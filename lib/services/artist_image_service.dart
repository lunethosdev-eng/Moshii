import 'dart:convert';
import 'package:http/http.dart' as http;

/// Free artist image lookup via Deezer public API (no key).
class ArtistImageService {
  static final Map<String, String?> _cache = {};

  static Future<String?> imageFor(String artistName) async {
    final key = artistName.trim().toLowerCase();
    if (key.isEmpty) return null;
    if (_cache.containsKey(key)) return _cache[key];

    try {
      final q = Uri.encodeComponent(artistName);
      final uri = Uri.parse('https://api.deezer.com/search/artist?q=$q&limit=1');
      final res = await http.get(uri);
      if (res.statusCode == 200) {
        final data = jsonDecode(res.body) as Map<String, dynamic>;
        final list = data['data'] as List?;
        if (list != null && list.isNotEmpty) {
          final img = list.first['picture_medium']?.toString() ??
              list.first['picture']?.toString();
          _cache[key] = img;
          return img;
        }
      }
    } catch (_) {}
    _cache[key] = null;
    return null;
  }
}
