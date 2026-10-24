import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';
import 'package:pokke/services/music_api_service.dart';
import 'package:pokke/services/supabase_service.dart';

/// Import Spotify playlists **without** the official Spotify Web API.
/// Flow: user logs in inside an in-app WebView → we extract playlist/track
/// metadata from the open.spotify.com page (DOM / embed / pasted URL) →
/// resolve each track against Pokke catalog (MusicApiService) → save playlist.
class SpotifyWebImportService extends ChangeNotifier {
  static final SpotifyWebImportService instance = SpotifyWebImportService._();
  SpotifyWebImportService._();

  static const _prefsKey = 'pokke_imported_spotify_playlists_v1';

  bool importing = false;
  String status = '';
  double progress = 0;
  String? lastError;

  /// Playlists already imported into Pokke (local cache + optional supabase).
  List<Map<String, dynamic>> imported = [];

  Future<void> load() async {
    try {
      final p = await SharedPreferences.getInstance();
      final raw = p.getString(_prefsKey);
      if (raw != null) {
        final list = jsonDecode(raw) as List;
        imported =
            list.map((e) => Map<String, dynamic>.from(e as Map)).toList();
      }
      notifyListeners();
    } catch (_) {}
  }

  Future<void> _saveImported() async {
    final p = await SharedPreferences.getInstance();
    await p.setString(_prefsKey, jsonEncode(imported));
    notifyListeners();
  }

  /// oEmbed: title + thumbnail for a public playlist URL (no track list).
  Future<Map<String, String>?> oEmbed(String playlistUrl) async {
    try {
      final uri = Uri.parse('https://open.spotify.com/oembed')
          .replace(queryParameters: {'url': playlistUrl});
      final res = await http.get(uri, headers: {
        'User-Agent': 'Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36',
      });
      if (res.statusCode != 200) return null;
      final j = jsonDecode(res.body) as Map<String, dynamic>;
      return {
        'title': (j['title'] ?? 'Playlist').toString(),
        'thumbnail': (j['thumbnail_url'] ?? '').toString(),
        'provider': (j['provider_name'] ?? 'Spotify').toString(),
      };
    } catch (e) {
      debugPrint('oEmbed: $e');
      return null;
    }
  }

  /// Parse track list from JS extraction payload.
  /// Expected shape from WebView JS:
  /// { name, cover, tracks: [{ title, artist }] }
  Map<String, dynamic>? parseExtracted(dynamic raw) {
    try {
      final map = raw is String
          ? jsonDecode(raw) as Map<String, dynamic>
          : Map<String, dynamic>.from(raw as Map);
      final tracks = <Map<String, String>>[];
      final list = map['tracks'] as List? ?? [];
      for (final t in list) {
        if (t is Map) {
          final title = (t['title'] ?? t['name'] ?? '').toString().trim();
          final artist =
              (t['artist'] ?? t['artists'] ?? t['subtitle'] ?? '').toString().trim();
          if (title.isEmpty) continue;
          tracks.add({'title': title, 'artist': artist});
        } else if (t is String && t.contains(' — ')) {
          final parts = t.split(' — ');
          tracks.add({
            'title': parts.first.trim(),
            'artist': parts.skip(1).join(' — ').trim(),
          });
        }
      }
      if (tracks.isEmpty && (map['name'] == null || map['name'].toString().isEmpty)) {
        return null;
      }
      return {
        'name': (map['name'] ?? map['title'] ?? 'Spotify playlist').toString(),
        'cover': (map['cover'] ?? map['image'] ?? map['thumbnail'] ?? '').toString(),
        'tracks': tracks,
        'source_url': (map['url'] ?? '').toString(),
      };
    } catch (e) {
      debugPrint('parseExtracted: $e');
      return null;
    }
  }

  /// Resolve one track against Pokke catalog / on-demand download server.
  Future<Map<String, dynamic>?> resolveTrack({
    required String title,
    required String artist,
  }) async {
    final q = artist.isEmpty ? title : '$title $artist';
    try {
      // 1) Local catalog first
      final local = await SupabaseService.searchSongs(q, limit: 8);
      Map<String, dynamic>? best = _bestMatch(local, title, artist);
      if (best != null) return best;

      // 2) Music API (search + auto download if missing)
      final remote = await MusicApiService.search(q, limit: 10);
      best = _bestMatch(remote, title, artist);
      if (best != null) return best;

      // If API returned anything close, take first
      if (remote.isNotEmpty) return remote.first;
    } catch (e) {
      debugPrint('resolveTrack $title: $e');
    }
    return null;
  }

  Map<String, dynamic>? _bestMatch(
    List<Map<String, dynamic>> list,
    String title,
    String artist,
  ) {
    if (list.isEmpty) return null;
    final tl = title.toLowerCase();
    final al = artist.toLowerCase();
    Map<String, dynamic>? exact;
    Map<String, dynamic>? titleOnly;
    for (final s in list) {
      final st = (s['title'] ?? '').toString().toLowerCase();
      final sa = (s['artist'] ?? '').toString().toLowerCase();
      if (st == tl && (al.isEmpty || sa.contains(al) || al.contains(sa))) {
        exact = s;
        break;
      }
      if (st.contains(tl) || tl.contains(st)) {
        titleOnly ??= s;
      }
    }
    return exact ?? titleOnly ?? list.first;
  }

  /// Import a fully extracted playlist into Pokke.
  Future<Map<String, dynamic>?> importPlaylist(
    Map<String, dynamic> extracted, {
    void Function(String msg, double prog)? onProgress,
  }) async {
    importing = true;
    lastError = null;
    status = 'Resolviendo canciones…';
    progress = 0;
    notifyListeners();

    final name = (extracted['name'] ?? 'Spotify').toString();
    final cover = (extracted['cover'] ?? '').toString();
    final tracks = (extracted['tracks'] as List?)
            ?.map((e) => Map<String, String>.from(
                (e as Map).map((k, v) => MapEntry(k.toString(), v.toString()))))
            .toList() ??
        <Map<String, String>>[];

    final resolved = <Map<String, dynamic>>[];
    for (var i = 0; i < tracks.length; i++) {
      final t = tracks[i];
      final title = t['title'] ?? '';
      final artist = t['artist'] ?? '';
      status = 'Buscando: $title';
      progress = tracks.isEmpty ? 1.0 : (i + 1) / tracks.length;
      onProgress?.call(status, progress);
      notifyListeners();

      final song = await resolveTrack(title: title, artist: artist);
      if (song != null) {
        resolved.add(song);
      } else {
        // Placeholder so user sees what failed
        resolved.add({
          'title': title,
          'artist': artist,
          'missing': true,
        });
      }
    }

    final playlist = {
      'id': 'sp_imp_${DateTime.now().millisecondsSinceEpoch}',
      'name': name,
      'cover_url': cover.isNotEmpty ? cover : null,
      'source': 'spotify_web',
      'source_url': extracted['source_url'],
      'tracks': resolved,
      'track_count': resolved.length,
      'found_count': resolved.where((e) => e['missing'] != true).length,
      'created_at': DateTime.now().toIso8601String(),
    };

    // Persist locally
    imported.insert(0, playlist);
    await _saveImported();

    // Best-effort: also mirror into Supabase playlists if user is logged in
    try {
      final uid = SupabaseService.client.auth.currentUser?.id;
      if (uid != null) {
        final row = await SupabaseService.kokoro.from('playlists').insert({
          'user_id': uid,
          'name': name,
          'cover_url': cover.isNotEmpty ? cover : null,
          'source': 'spotify_web',
        }).select().maybeSingle();
        final pid = row?['id'];
        if (pid != null) {
          for (var i = 0; i < resolved.length; i++) {
            final s = resolved[i];
            if (s['missing'] == true) continue;
            await SupabaseService.kokoro.from('playlist_songs').insert({
              'playlist_id': pid,
              'song_id': s['id']?.toString(),
              'title': s['title']?.toString() ?? '',
              'artist': s['artist']?.toString() ?? '',
              'cover_url': s['cover_url'],
              'stream_url': s['audio_url'] ?? s['stream_url'],
              'position': i,
            });
          }
          playlist['supabase_id'] = pid;
          await _saveImported();
        }
      }
    } catch (e) {
      debugPrint('supabase playlist mirror: $e');
    }

    importing = false;
    status = 'Listo · ${playlist['found_count']}/${playlist['track_count']} canciones';
    progress = 1;
    notifyListeners();
    return playlist;
  }
}
