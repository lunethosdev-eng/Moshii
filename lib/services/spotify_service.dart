import 'dart:convert';
import 'dart:math';
import 'package:crypto/crypto.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_web_auth_2/flutter_web_auth_2.dart';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';

/// Spotify OAuth (PKCE) + playlist import.
/// Register redirect URI in Spotify Dashboard:
///   Mobile: pokke://spotify-callback
///   Web:    https://lunethosdev-eng.github.io/Moshii/spotify-callback.html
class SpotifyService {
  static final SpotifyService instance = SpotifyService._();
  SpotifyService._();

  /// Default public client id (replace in Settings if needed).
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

  bool get isLinked => accessToken != null && accessToken!.isNotEmpty;

  String get redirectUri => kIsWeb ? webRedirect : mobileRedirect;

  Future<void> load() async {
    final p = await SharedPreferences.getInstance();
    clientId = p.getString('spotify_client_id') ?? defaultClientId;
    accessToken = p.getString('spotify_access');
    refreshToken = p.getString('spotify_refresh');
    final exp = p.getInt('spotify_expires');
    if (exp != null) expiresAt = DateTime.fromMillisecondsSinceEpoch(exp);
  }

  Future<void> setClientId(String id) async {
    clientId = id.trim().isEmpty ? defaultClientId : id.trim();
    final p = await SharedPreferences.getInstance();
    await p.setString('spotify_client_id', clientId);
  }

  Future<void> _save() async {
    final p = await SharedPreferences.getInstance();
    if (accessToken != null) await p.setString('spotify_access', accessToken!);
    if (refreshToken != null) await p.setString('spotify_refresh', refreshToken!);
    if (expiresAt != null) {
      await p.setInt('spotify_expires', expiresAt!.millisecondsSinceEpoch);
    }
  }

  Future<void> unlink() async {
    accessToken = null;
    refreshToken = null;
    expiresAt = null;
    lastError = null;
    final p = await SharedPreferences.getInstance();
    await p.remove('spotify_access');
    await p.remove('spotify_refresh');
    await p.remove('spotify_expires');
  }

  String _randomString(int len) {
    const chars =
        'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
    final r = Random.secure();
    return List.generate(len, (_) => chars[r.nextInt(chars.length)]).join();
  }

  String _codeChallenge(String verifier) {
    final bytes = utf8.encode(verifier);
    final digest = sha256.convert(bytes);
    return base64Url.encode(digest.bytes).replaceAll('=', '');
  }

  Future<bool> connect() async {
    lastError = null;
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

    try {
      final result = await FlutterWebAuth2.authenticate(
        url: authUri.toString(),
        callbackUrlScheme: kIsWeb ? 'https' : 'pokke',
      );
      final uri = Uri.parse(result);
      final code = uri.queryParameters['code'];
      final err = uri.queryParameters['error'];
      if (err != null) {
        lastError = err;
        return false;
      }
      if (code == null || code.isEmpty) {
        lastError = 'no_code';
        return false;
      }
      return await _exchangeCode(code, verifier);
    } catch (e) {
      final msg = e.toString();
      // User closed the browser / canceled OAuth — not a hard error.
      if (msg.contains('CANCELED') ||
          msg.contains('canceled') ||
          msg.contains('cancelled') ||
          msg.contains('User canceled')) {
        lastError = null;
        debugPrint('Spotify connect canceled by user');
        return false;
      }
      lastError = msg;
      debugPrint('Spotify connect error: $e');
      return false;
    }
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
      if (res.statusCode != 200) {
        lastError = 'token_${res.statusCode}: ${res.body}';
        debugPrint(lastError);
        return false;
      }
      final data = jsonDecode(res.body) as Map<String, dynamic>;
      accessToken = data['access_token'] as String?;
      refreshToken = data['refresh_token'] as String? ?? refreshToken;
      final expiresIn = data['expires_in'] as int? ?? 3600;
      expiresAt = DateTime.now().add(Duration(seconds: expiresIn));
      await _save();
      return accessToken != null;
    } catch (e) {
      lastError = e.toString();
      return false;
    }
  }

  Future<bool> _refresh() async {
    if (refreshToken == null) return false;
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
      if (res.statusCode != 200) return false;
      final data = jsonDecode(res.body) as Map<String, dynamic>;
      accessToken = data['access_token'] as String?;
      final expiresIn = data['expires_in'] as int? ?? 3600;
      expiresAt = DateTime.now().add(Duration(seconds: expiresIn));
      if (data['refresh_token'] != null) {
        refreshToken = data['refresh_token'] as String;
      }
      await _save();
      return true;
    } catch (_) {
      return false;
    }
  }

  Future<String?> _token() async {
    if (accessToken == null) return null;
    if (expiresAt != null && DateTime.now().isAfter(expiresAt!)) {
      final ok = await _refresh();
      if (!ok) return null;
    }
    return accessToken;
  }

  Future<List<Map<String, dynamic>>> fetchPlaylists() async {
    final token = await _token();
    if (token == null) return [];
    final out = <Map<String, dynamic>>[];
    var url = 'https://api.spotify.com/v1/me/playlists?limit=50';
    while (url.isNotEmpty) {
      final res = await http.get(Uri.parse(url), headers: {
        'Authorization': 'Bearer $token',
      });
      if (res.statusCode != 200) break;
      final data = jsonDecode(res.body) as Map<String, dynamic>;
      final items = data['items'] as List? ?? [];
      for (final it in items) {
        out.add(Map<String, dynamic>.from(it as Map));
      }
      url = (data['next'] as String?) ?? '';
    }
    return out;
  }

  Future<List<Map<String, dynamic>>> fetchPlaylistTracks(String playlistId) async {
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
}

  /// Liked songs as a synthetic playlist.
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

  /// Import Spotify playlists (+ Liked Songs) into pokke (Supabase).
  /// [onStatus] reports human-readable progress for UI.
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
    // Liked songs first
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
    // Create playlist row
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

      // Try local catalog first
      Map<String, dynamic>? song;
      try {
        final found = await SupabaseService.searchSongs('$title $artist', limit: 5);
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
        // On-demand download from music API
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
            streamUrl = res['audio_url']?.toString() ?? res['url']?.toString() ?? res['stream_url']?.toString();
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
      } catch (e) {
        // Minimal columns fallback
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
