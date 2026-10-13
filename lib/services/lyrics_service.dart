import 'dart:convert';
import 'package:http/http.dart' as http;

/// Free lyrics via LRCLIB only. Never surfaces Termux / junk server text.
class LyricsService {
  static const _base = 'https://lrclib.net';
  static const _ua = 'Pokke/1.0 (https://github.com/lunethosdev-eng/Moshii)';

  static bool isJunk(String? text) {
    if (text == null || text.trim().isEmpty) return true;
    final lower = text.toLowerCase();
    const bad = [
      'termux',
      'seki',
      'procesado por',
      'processed by',
      'sticky termos',
      'server error',
      'no lyrics available on server',
    ];
    for (final b in bad) {
      if (lower.contains(b)) return true;
    }
    // Too short to be real lyrics
    if (text.trim().length < 12) return true;
    return false;
  }

  /// Prefer DB text only if it's real lyrics; otherwise LRCLIB.
  static Future<String?> resolve({
    String? localText,
    required String title,
    required String artist,
    String? album,
    int? durationSeconds,
  }) async {
    if (!isJunk(localText)) return localText;
    return fetch(
      title: title,
      artist: artist,
      album: album,
      durationSeconds: durationSeconds,
    );
  }

  static Future<String?> fetch({
    required String title,
    required String artist,
    String? album,
    int? durationSeconds,
  }) async {
    try {
      final q = Uri.encodeComponent('$title $artist');
      final searchUri = Uri.parse('$_base/api/search?q=$q');
      final searchRes = await http.get(searchUri, headers: {'User-Agent': _ua});
      if (searchRes.statusCode != 200) return null;
      final list = jsonDecode(searchRes.body);
      if (list is! List || list.isEmpty) return null;

      Map<String, dynamic>? best;
      for (final item in list) {
        final m = Map<String, dynamic>.from(item as Map);
        final synced = m['syncedLyrics']?.toString();
        if (synced != null && synced.isNotEmpty && !isJunk(synced)) {
          best = m;
          break;
        }
        best ??= m;
      }
      if (best == null) return null;

      final synced = best['syncedLyrics']?.toString();
      if (synced != null && !isJunk(synced)) return synced;
      final plain = best['plainLyrics']?.toString();
      if (plain != null && !isJunk(plain)) return plain;

      if (durationSeconds != null && durationSeconds > 0) {
        final params = {
          'track_name': title,
          'artist_name': artist,
          if (album != null && album.isNotEmpty) 'album_name': album,
          'duration': '$durationSeconds',
        };
        final getUri = Uri.parse('$_base/api/get').replace(queryParameters: params);
        final getRes = await http.get(getUri, headers: {'User-Agent': _ua});
        if (getRes.statusCode == 200) {
          final m = jsonDecode(getRes.body) as Map<String, dynamic>;
          final s = m['syncedLyrics']?.toString();
          if (s != null && !isJunk(s)) return s;
          final p = m['plainLyrics']?.toString();
          if (p != null && !isJunk(p)) return p;
        }
      }
    } catch (_) {}
    return null;
  }
}
