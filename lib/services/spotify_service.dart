import 'dart:async';
import 'dart:convert';
import 'dart:math';
import 'package:app_links/app_links.dart';
import 'package:crypto/crypto.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_web_auth_2/flutter_web_auth_2.dart';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:pokke/services/supabase_service.dart';
import 'package:pokke/services/music_api_service.dart';

/// Spotify OAuth PKCE.
/// Redirect URI en Dashboard (exacto, sin slash final):
///   pokke://spotify-callback
class SpotifyService {
  static final SpotifyService instance = SpotifyService._();
  SpotifyService._();

  static const defaultClientId = 'b4a45e2d1a13437fa4a668334607a0fe';
  static const mobileRedirect = 'pokke://spotify-callback';
  static const webRedirect =
      'https://lunethosdev-eng.github.io/Moshii/spotify-callback.html';
  static const scopes =
      'playlist-read-private playlist-read-collaborative user-library-read';

  String clientId = defaultClientId;
  String? accessToken;
  String? refreshToken;
  DateTime? expiresAt;
  String? lastError;
  String? displayName;
  String? profileImage;

  bool get isLinked =>
      (accessToken != null && accessToken!.isNotEmpty) ||
      (refreshToken != null && refreshToken!.isNotEmpty);

  String get redirectUri => kIsWeb ? webRedirect : mobileRedirect;

  Future<void> load() async {
    final p = await SharedPreferences.getInstance();
    clientId = p.getString('spotify_client_id') ?? defaultClientId;
    accessToken = p.getString('spotify_access');
    refreshToken = p.getString('spotify_refresh');
    final exp = p.getInt('spotify_expires');
    if (exp != null) {
      expiresAt = DateTime.fromMillisecondsSinceEpoch(exp);
    }
    displayName = p.getString('spotify_name');
    profileImage = p.getString('spotify_image');
  }

  Future<void> _persist() async {
    final p = await SharedPreferences.getInstance();
    await p.setString('spotify_client_id', clientId);
    if (accessToken != null) await p.setString('spotify_access', accessToken!);
    if (refreshToken != null) {
      await p.setString('spotify_refresh', refreshToken!);
    }
    if (expiresAt != null) {
      await p.setInt('spotify_expires', expiresAt!.millisecondsSinceEpoch);
    }
  }

  Future<void> unlink() async {
    final p = await SharedPreferences.getInstance();
    accessToken = null;
    refreshToken = null;
    expiresAt = null;
    lastError = null;
    displayName = null;
    profileImage = null;
    await p.remove('spotify_access');
    await p.remove('spotify_refresh');
    await p.remove('spotify_expires');
    await p.remove('spotify_name');
    await p.remove('spotify_image');
  }

  String _randomString(int length) {
    const chars =
        'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~';
    final rnd = Random.secure();
    return List.generate(length, (_) => chars[rnd.nextInt(chars.length)]).join();
  }

  String _codeChallenge(String verifier) {
    final digest = sha256.convert(utf8.encode(verifier));
    return base64Url.encode(digest.bytes).replaceAll('=', '');
  }

  /// Primary: open browser/Custom Tabs + wait for deep link.
  /// Fallback: flutter_web_auth_2.
  Future<bool> connect() async {
    lastError = null;
    await load();
    final verifier = _randomString(64);
    final challenge = _codeChallenge(verifier);
    final state = _randomString(16);

    final authUri = Uri.https('accounts.spotify.com', '/authorize', {
      'client_id': clientId,
      'response_type': 'code',
      'redirect_uri': redirectUri,
      'scope': scopes,
      'code_challenge_method': 'S256',
      'code_challenge': challenge,
      'state': state,
      'show_dialog': 'true',
    });

    // Persist verifier so we can exchange even if process is killed
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('spotify_pkce_verifier', verifier);
    await prefs.setString('spotify_pkce_state', state);

    if (kIsWeb) {
      return _connectWebAuth(authUri, verifier);
    }

    // Mobile: deep link first
    final deepOk = await _connectDeepLink(authUri, verifier, state);
    if (deepOk) return true;

    // Fallback flutter_web_auth_2
    debugPrint('Spotify deep-link failed ($lastError), trying web_auth…');
    return _connectWebAuth(authUri, verifier);
  }

  Future<bool> _connectDeepLink(
      Uri authUri, String verifier, String state) async {
    final completer = Completer<Uri?>();
    StreamSubscription? sub;
    Timer? timer;

    try {
      final appLinks = AppLinks();

      // Pending link (cold start)
      try {
        final initial = await appLinks.getInitialLink();
        if (initial != null && initial.scheme == 'pokke') {
          completer.complete(initial);
        }
      } catch (_) {}

      sub = appLinks.uriLinkStream.listen((uri) {
        debugPrint('Spotify deep link: $uri');
        if (!completer.isCompleted &&
            (uri.scheme == 'pokke' ||
                uri.toString().contains('spotify-callback'))) {
          completer.complete(uri);
        }
      }, onError: (e) {
        debugPrint('app_links error: $e');
      });

      final launched = await launchUrl(
        authUri,
        mode: LaunchMode.externalApplication,
      );
      if (!launched) {
        lastError = 'no_se_pudo_abrir_navegador';
        return false;
      }

      timer = Timer(const Duration(seconds: 120), () {
        if (!completer.isCompleted) completer.complete(null);
      });

      final result = await completer.future;
      if (result == null) {
        lastError =
            'timeout_sin_callback · ¿Redirect en Dashboard es exactamente pokke://spotify-callback ?';
        return false;
      }
      return _handleCallbackUri(result, verifier, state);
    } catch (e) {
      lastError = 'deeplink: $e';
      return false;
    } finally {
      await sub?.cancel();
      timer?.cancel();
    }
  }

  Future<bool> _connectWebAuth(Uri authUri, String verifier) async {
    try {
      final result = await FlutterWebAuth2.authenticate(
        url: authUri.toString(),
        callbackUrlScheme: kIsWeb ? 'https' : 'pokke',
      );
      debugPrint('web_auth result: $result');
      final uri = Uri.parse(result);
      return _handleCallbackUri(uri, verifier, null);
    } catch (e) {
      final msg = e.toString();
      debugPrint('web_auth error: $e');
      if (msg.contains('CANCELED') ||
          msg.contains('canceled') ||
          msg.contains('cancelled')) {
        lastError =
            'auth_cancel_o_sin_callback · Redirect Dashboard: pokke://spotify-callback';
      } else {
        lastError = msg.length > 160 ? msg.substring(0, 160) : msg;
      }
      return false;
    }
  }

  Future<bool> _handleCallbackUri(
      Uri uri, String verifier, String? expectedState) async {
    final params = Map<String, String>.from(uri.queryParameters);
    if (uri.fragment.isNotEmpty) {
      params.addAll(Uri.splitQueryString(uri.fragment));
    }
    final code = params['code'];
    final err = params['error'];
    final st = params['state'];
    if (err != null) {
      lastError = 'spotify_$err';
      return false;
    }
    if (expectedState != null && st != null && st != expectedState) {
      lastError = 'state_mismatch';
      return false;
    }
    if (code == null || code.isEmpty) {
      lastError = 'callback_sin_code · $uri';
      return false;
    }
    final ok = await _exchangeCode(code, verifier);
    if (ok) await _fetchMe();
    return ok;
  }

  /// Call from main.dart when app receives pokke:// link while running.
  Future<bool> handleIncomingLink(Uri uri) async {
    if (uri.scheme != 'pokke') return false;
    final prefs = await SharedPreferences.getInstance();
    final verifier = prefs.getString('spotify_pkce_verifier');
    final state = prefs.getString('spotify_pkce_state');
    if (verifier == null) {
      lastError = 'sin_verifier_pkce';
      return false;
    }
    final ok = await _handleCallbackUri(uri, verifier, state);
    if (ok) {
      await prefs.remove('spotify_pkce_verifier');
      await prefs.remove('spotify_pkce_state');
    }
    return ok;
  }

  Future<bool> _exchangeCode(String code, String verifier) async {
    try {
      final res = await http.post(
        Uri.parse('https://accounts.spotify.com/api/token'),
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: {
          'client_id': clientId,
          'grant_type': 'authorization_code',
          'code': code,
          'redirect_uri': redirectUri,
          'code_verifier': verifier,
        },
      );
      debugPrint('token ${res.statusCode}: ${res.body}');
      if (res.statusCode != 200) {
        lastError = 'token_${res.statusCode}: ${res.body}';
        return false;
      }
      final data = jsonDecode(res.body) as Map<String, dynamic>;
      accessToken = data['access_token'] as String?;
      refreshToken = data['refresh_token'] as String? ?? refreshToken;
      final expiresIn = data['expires_in'] as int? ?? 3600;
      expiresAt = DateTime.now().add(Duration(seconds: expiresIn - 30));
      await _persist();
      return accessToken != null && accessToken!.isNotEmpty;
    } catch (e) {
      lastError = e.toString();
      return false;
    }
  }

  Future<void> _fetchMe() async {
    try {
      final token = await _token();
      if (token == null) return;
      final res = await http.get(
        Uri.parse('https://api.spotify.com/v1/me'),
        headers: {'Authorization': 'Bearer $token'},
      );
      if (res.statusCode != 200) return;
      final data = jsonDecode(res.body) as Map<String, dynamic>;
      displayName = data['display_name']?.toString() ?? data['id']?.toString();
      final images = data['images'] as List?;
      if (images != null && images.isNotEmpty) {
        profileImage = images.first['url']?.toString();
      }
      final p = await SharedPreferences.getInstance();
      if (displayName != null) await p.setString('spotify_name', displayName!);
      if (profileImage != null) {
        await p.setString('spotify_image', profileImage!);
      }
    } catch (e) {
      debugPrint('/me $e');
    }
  }

  Future<String?> _token() async {
    if (accessToken != null &&
        expiresAt != null &&
        expiresAt!.isAfter(DateTime.now())) {
      return accessToken;
    }
    if (refreshToken == null) return accessToken;
    try {
      final res = await http.post(
        Uri.parse('https://accounts.spotify.com/api/token'),
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: {
          'client_id': clientId,
          'grant_type': 'refresh_token',
          'refresh_token': refreshToken!,
        },
      );
      if (res.statusCode != 200) return accessToken;
      final data = jsonDecode(res.body) as Map<String, dynamic>;
      accessToken = data['access_token'] as String?;
      if (data['refresh_token'] != null) {
        refreshToken = data['refresh_token'] as String?;
      }
      final expiresIn = data['expires_in'] as int? ?? 3600;
      expiresAt = DateTime.now().add(Duration(seconds: expiresIn - 30));
      await _persist();
      return accessToken;
    } catch (_) {
      return accessToken;
    }
  }

  Future<List<Map<String, dynamic>>> fetchPlaylists() async {
    final token = await _token();
    if (token == null) {
      lastError = 'sin_token';
      return [];
    }
    final out = <Map<String, dynamic>>[];
    var url = 'https://api.spotify.com/v1/me/playlists?limit=50';
    while (url.isNotEmpty) {
      final res = await http.get(Uri.parse(url), headers: {
        'Authorization': 'Bearer $token',
      });
      debugPrint('spotify playlists ${res.statusCode}: ${res.body.length > 200 ? res.body.substring(0, 200) : res.body}');
      if (res.statusCode == 401 || res.statusCode == 403) {
        lastError =
            'playlists_${res.statusCode}: vuelve a conectar Spotify (permisos)';
        // Clear tokens so next connect re-requests playlist scopes
        accessToken = null;
        refreshToken = null;
        try {
          final p = await SharedPreferences.getInstance();
          await p.remove('spotify_access');
          await p.remove('spotify_refresh');
          await p.remove('spotify_expires');
        } catch (_) {}
        break;
      }
      if (res.statusCode != 200) {
        lastError = 'playlists_${res.statusCode}: ${res.body}';
        break;
      }
      final data = jsonDecode(res.body) as Map<String, dynamic>;
      final items = data['items'] as List? ?? [];
      for (final it in items) {
        // Spotify puede devolver null en items
        if (it is Map<String, dynamic> && it['id'] != null) {
          out.add(it);
        }
      }
      url = (data['next'] as String?) ?? '';
    }
    if (out.isEmpty && lastError == null) {
      lastError = 'cero_playlists_en_cuenta';
    }
    return out;
  }

  /// Import only selected playlist ids (+ optional liked songs).
  Future<int> importSelectedPlaylists(
    List<String> playlistIds, {
    bool includeLiked = false,
    void Function(String message, double? progress)? onStatus,
  }) async {
    final uid = SupabaseService.currentUser?.id;
    if (uid == null) {
      lastError = 'not_logged_in';
      return 0;
    }
    int created = 0;
    final all = await fetchPlaylists();
    final selected = all
        .where((p) => playlistIds.contains((p['id'] ?? '').toString()))
        .toList();

    if (includeLiked) {
      onStatus?.call('Liked Songs…', 0.05);
      final liked = await fetchLikedTracks();
      if (liked.isNotEmpty) {
        await _importOnePlaylist(
          name: 'Liked Songs',
          cover: liked.first['cover_url']?.toString(),
          tracks: liked,
          uid: uid,
          onStatus: onStatus,
        );
        created++;
      }
    }

    for (var i = 0; i < selected.length; i++) {
      final pl = selected[i];
      final name = (pl['name'] ?? 'Playlist').toString();
      final id = (pl['id'] ?? '').toString();
      if (id.isEmpty) continue;
      onStatus?.call(
        'Importando $name…',
        selected.isEmpty ? null : (i + 1) / selected.length,
      );
      final tracks = await fetchPlaylistTracks(id);
      final images = pl['images'] as List?;
      final cover = images != null && images.isNotEmpty
          ? images.first['url']?.toString()
          : null;
      await _importOnePlaylist(
        name: name,
        cover: cover,
        tracks: tracks,
        uid: uid,
        onStatus: onStatus,
      );
      created++;
    }
    onStatus?.call('Listo · $created playlists', 1);
    return created;
  }

  Future<List<Map<String, dynamic>>> fetchPlaylistTracks(
      String playlistId) async {
    final token = await _token();
    if (token == null) return [];
    final out = <Map<String, dynamic>>[];
    var url =
        'https://api.spotify.com/v1/playlists/$playlistId/tracks?limit=100';
    while (url.isNotEmpty) {
      final res = await http.get(Uri.parse(url), headers: {
        'Authorization': 'Bearer $token',
      });
      if (res.statusCode != 200) break;
      final data = jsonDecode(res.body) as Map<String, dynamic>;
      final items = data['items'] as List? ?? [];
      for (final it in items) {
        final track = (it as Map)['track'];
        if (track == null) continue;
        final artists = (track['artists'] as List?)
                ?.map((a) => a['name'])
                .whereType<String>()
                .join(', ') ??
            '';
        final images = track['album']?['images'] as List?;
        out.add({
          'spotify_id': track['id'],
          'title': track['name'],
          'artist': artists,
          'cover_url': images != null && images.isNotEmpty
              ? images.last['url']
              : null,
          'duration_ms': track['duration_ms'],
        });
      }
      url = (data['next'] as String?) ?? '';
    }
    return out;
  }

  Future<List<Map<String, dynamic>>> fetchLikedTracks() async {
    final token = await _token();
    if (token == null) return [];
    final out = <Map<String, dynamic>>[];
    var url = 'https://api.spotify.com/v1/me/tracks?limit=50';
    while (url.isNotEmpty) {
      final res = await http.get(Uri.parse(url), headers: {
        'Authorization': 'Bearer $token',
      });
      if (res.statusCode != 200) break;
      final data = jsonDecode(res.body) as Map<String, dynamic>;
      final items = data['items'] as List? ?? [];
      for (final it in items) {
        final track = (it as Map)['track'];
        if (track == null) continue;
        final artists = (track['artists'] as List?)
                ?.map((a) => a['name'])
                .whereType<String>()
                .join(', ') ??
            '';
        final images = track['album']?['images'] as List?;
        out.add({
          'spotify_id': track['id'],
          'title': track['name'],
          'artist': artists,
          'cover_url': images != null && images.isNotEmpty
              ? images.last['url']
              : null,
          'duration_ms': track['duration_ms'],
        });
      }
      url = (data['next'] as String?) ?? '';
    }
    return out;
  }

  Future<int> importAllPlaylists({
    void Function(String message, double? progress)? onStatus,
  }) async {
    final uid = SupabaseService.currentUser?.id;
    if (uid == null) {
      lastError = 'not_logged_in';
      return 0;
    }
    int created = 0;
    final lists = await fetchPlaylists();
    final liked = await fetchLikedTracks();
    if (liked.isNotEmpty) {
      onStatus?.call('Liked Songs…', 0.05);
      await _importOnePlaylist(
        name: 'Liked Songs',
        cover: liked.first['cover_url']?.toString(),
        tracks: liked,
        uid: uid,
        onStatus: onStatus,
      );
      created++;
    }
    for (var i = 0; i < lists.length; i++) {
      final pl = lists[i];
      final name = (pl['name'] ?? 'Playlist').toString();
      final id = (pl['id'] ?? '').toString();
      if (id.isEmpty) continue;
      onStatus?.call('Importando $name…', (i + 1) / (lists.length + 1));
      final tracks = await fetchPlaylistTracks(id);
      final images = pl['images'] as List?;
      final cover = images != null && images.isNotEmpty
          ? images.first['url']?.toString()
          : null;
      await _importOnePlaylist(
        name: name,
        cover: cover,
        tracks: tracks,
        uid: uid,
        onStatus: onStatus,
      );
      created++;
    }
    onStatus?.call('Listo · $created playlists', 1);
    return created;
  }

  Future<void> _importOnePlaylist({
    required String name,
    required String? cover,
    required List<Map<String, dynamic>> tracks,
    required String uid,
    void Function(String message, double? progress)? onStatus,
  }) async {
    final row = await SupabaseService.kokoro.from('playlists').insert({
      'user_id': uid,
      'name': name,
      'cover_url': cover,
      'is_public': false,
      'source': 'spotify',
    }).select().single();
    final playlistId = row['id'];

    for (var i = 0; i < tracks.length; i++) {
      final t = tracks[i];
      final title = (t['title'] ?? '').toString();
      final artist = (t['artist'] ?? '').toString();
      if (title.isEmpty) continue;
      onStatus?.call(
        '$name · $title',
        tracks.isEmpty ? null : (i + 1) / tracks.length,
      );

      Map<String, dynamic>? song;
      try {
        final found =
            await SupabaseService.searchSongs('$title $artist', limit: 5);
        for (final s in found) {
          final st = (s['title'] ?? s['name'] ?? '').toString().toLowerCase();
          if (st.contains(title.toLowerCase().split(' ').first)) {
            song = s;
            break;
          }
        }
        if (found.isNotEmpty && song == null) song = found.first;
      } catch (_) {}

      String? songId;
      String? streamUrl;
      if (song != null) {
        songId = (song['id'] ?? '').toString();
        streamUrl =
            (song['audio_url'] ?? song['stream_url'] ?? song['url'])?.toString();
      } else {
        try {
          final results = await MusicApiService.search(
            '$title $artist',
            onProgress: (p) {
              onStatus?.call(
                '$title · ${p.status}${p.progress != null ? ' ${(p.progress! * 100).toStringAsFixed(0)}%' : ''}',
                p.progress,
              );
            },
          );
          if (results.isNotEmpty) {
            final res = results.first;
            streamUrl = res['audio_url']?.toString() ??
                res['url']?.toString() ??
                res['stream_url']?.toString();
            songId = res['id']?.toString();
          }
        } catch (e) {
          debugPrint('download miss $title: $e');
        }
      }

      try {
        await SupabaseService.kokoro.from('playlist_songs').insert({
          'playlist_id': playlistId,
          'song_id': songId,
          'title': title,
          'artist': artist,
          'cover_url': t['cover_url'],
          'stream_url': streamUrl,
          'position': i,
          'spotify_id': t['spotify_id'],
        });
      } catch (_) {
        try {
          await SupabaseService.kokoro.from('playlist_songs').insert({
            'playlist_id': playlistId,
            'song_id': songId ?? title,
            'stream_url': streamUrl,
            'position': i,
          });
        } catch (e2) {
          debugPrint('playlist_songs insert fail: $e2');
        }
      }
    }
  }
}
