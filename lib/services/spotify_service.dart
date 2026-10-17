import 'dart:convert';
import 'dart:math';
import 'package:crypto/crypto.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_web_auth_2/flutter_web_auth_2.dart';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';

/// Spotify OAuth (PKCE) + playlist import helpers.
/// Client ID is public by design; no client secret in the app.
class SpotifyService {
  static final SpotifyService instance = SpotifyService._();
  SpotifyService._();

  static const clientId = 'b4a45e2d1a13437fa4a668334607a0fe';
  static const redirectUri = 'pokke://spotify-callback';
  static const scopes = 'playlist-read-private playlist-read-collaborative user-library-read';

  String? accessToken;
  String? refreshToken;
  DateTime? expiresAt;

  bool get isLinked => accessToken != null && accessToken!.isNotEmpty;

  Future<void> load() async {
    final p = await SharedPreferences.getInstance();
    accessToken = p.getString('spotify_access');
    refreshToken = p.getString('spotify_refresh');
    final exp = p.getInt('spotify_expires');
    if (exp != null) expiresAt = DateTime.fromMillisecondsSinceEpoch(exp);
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
    final p = await SharedPreferences.getInstance();
    await p.remove('spotify_access');
    await p.remove('spotify_refresh');
    await p.remove('spotify_expires');
  }

  String _randomString(int len) {
    const chars = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
    final r = Random.secure();
    return List.generate(len, (_) => chars[r.nextInt(chars.length)]).join();
  }

  String _codeChallenge(String verifier) {
    final bytes = utf8.encode(verifier);
    final digest = sha256.convert(bytes);
    return base64Url.encode(digest.bytes).replaceAll('=', '');
  }

  Future<bool> connect() async {
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
    });

    try {
      final result = await FlutterWebAuth2.authenticate(
        url: authUri.toString(),
        callbackUrlScheme: 'pokke',
      );
      final code = Uri.parse(result).queryParameters['code'];
      if (code == null || code.isEmpty) return false;
      return await _exchangeCode(code, verifier);
    } catch (e) {
      debugPrint('Spotify connect error: $e');
      return false;
    }
  }

  Future<bool> _exchangeCode(String code, String verifier) async {
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
      debugPrint('token exchange ${res.statusCode} ${res.body}');
      return false;
    }
    final body = jsonDecode(res.body) as Map<String, dynamic>;
    accessToken = body['access_token'] as String?;
    refreshToken = body['refresh_token'] as String? ?? refreshToken;
    final expiresIn = body['expires_in'] as int? ?? 3600;
    expiresAt = DateTime.now().add(Duration(seconds: expiresIn - 30));
    await _save();
    return accessToken != null;
  }

  Future<bool> _ensureToken() async {
    await load();
    if (accessToken == null) return false;
    if (expiresAt != null && DateTime.now().isBefore(expiresAt!)) return true;
    if (refreshToken == null) return false;
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
    final body = jsonDecode(res.body) as Map<String, dynamic>;
    accessToken = body['access_token'] as String?;
    final expiresIn = body['expires_in'] as int? ?? 3600;
    expiresAt = DateTime.now().add(Duration(seconds: expiresIn - 30));
    await _save();
    return accessToken != null;
  }

  Future<List<Map<String, dynamic>>> fetchPlaylists() async {
    if (!await _ensureToken()) return [];
    final out = <Map<String, dynamic>>[];
    var url = 'https://api.spotify.com/v1/me/playlists?limit=50';
    while (url.isNotEmpty) {
      final res = await http.get(
        Uri.parse(url),
        headers: {'Authorization': 'Bearer $accessToken'},
      );
      if (res.statusCode != 200) break;
      final body = jsonDecode(res.body) as Map<String, dynamic>;
      final items = List<Map<String, dynamic>>.from(body['items'] ?? []);
      out.addAll(items);
      url = (body['next'] as String?) ?? '';
    }
    return out;
  }

  Future<List<Map<String, dynamic>>> fetchPlaylistTracks(String playlistId) async {
    if (!await _ensureToken()) return [];
    final out = <Map<String, dynamic>>[];
    var url = 'https://api.spotify.com/v1/playlists/$playlistId/tracks?limit=100';
    while (url.isNotEmpty) {
      final res = await http.get(
        Uri.parse(url),
        headers: {'Authorization': 'Bearer $accessToken'},
      );
      if (res.statusCode != 200) break;
      final body = jsonDecode(res.body) as Map<String, dynamic>;
      final items = List<Map<String, dynamic>>.from(body['items'] ?? []);
      for (final it in items) {
        final track = it['track'];
        if (track is Map<String, dynamic> && track['id'] != null) {
          final artists = (track['artists'] as List?)
                  ?.map((a) => a['name']?.toString() ?? '')
                  .where((s) => s.isNotEmpty)
                  .join(', ') ??
              '';
          final images = track['album']?['images'] as List?;
          out.add({
            'spotify_id': track['id'],
            'title': track['name'],
            'artist': artists,
            'cover_url': images != null && images.isNotEmpty ? images[0]['url'] : null,
            'duration_ms': track['duration_ms'],
            'isrc': track['external_ids']?['isrc'],
          });
        }
      }
      url = (body['next'] as String?) ?? '';
    }
    return out;
  }
}
