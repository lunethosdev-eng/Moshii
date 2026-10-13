import 'dart:convert';
import 'package:http/http.dart' as http;

/// Free lyrics via LRCLIB (no API key).
class LyricsService {
  static const _base = 'https://lrclib.net';
  static const _ua = 'Pokke/1.0 (https://github.com/lunethosdev-eng/Moshii)';

  /// Returns synced LRC text or plain lyrics, or null.
  static Future<String?> fetch({
    required String title,
    required String artist,
    String? album,
    int? durationSeconds,
  }) async {
    try {
      // 1) Search
      final q = Uri.encodeComponent('$title $artist');
      final searchUri = Uri.parse('$_base/api/search?q=$q');
      final searchRes = await http.get(searchUri, headers: {'User-Agent': _ua});
      if (searchRes.statusCode != 200) return null;
      final list = jsonDecode(searchRes.body);
      if (list is! List || list.isEmpty) return null;

      Map<String, dynamic>? best = Map<String, dynamic>.from(list.first as Map);
      // Prefer entry with synced lyrics
      for (final item in list) {
        final m = Map<String, dynamic>.from(item as Map);
        if (m['syncedLyrics'] != null && m['syncedLyrics'].toString().isNotEmpty) {
          best = m;
          break;
        }
      }

      final synced = best?['syncedLyrics']?.toString();
      if (synced != null && synced.isNotEmpty) return synced;
      final plain = best?['plainLyrics']?.toString();
      if (plain != null && plain.isNotEmpty) return plain;

      // 2) Exact get if we have duration
      if (durationSeconds != null && durationSeconds > 0) {
        final params = {
          'track_name': title,
          'artist_name': artist,
          if (album != null) 'album_name': album,
          'duration': '$durationSeconds',
        };
        final getUri = Uri.parse('$_base/api/get').replace(queryParameters: params);
        final getRes = await http.get(getUri, headers: {'User-Agent': _ua});
        if (getRes.statusCode == 200) {
          final m = jsonDecode(getRes.body) as Map<String, dynamic>;
          return m['syncedLyrics']?.toString() ?? m['plainLyrics']?.toString();
        }
      }
    } catch (e) {
      // ignore
    }
    return null;
  }
}
