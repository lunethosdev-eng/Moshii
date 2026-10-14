import 'dart:convert';
import 'package:http/http.dart' as http;
import 'package:pokke/services/supabase_service.dart';

/// Remote music search / on-demand download API (URL editable from Admin).
class MusicApiService {
  static String? _cachedBase;

  static Future<String> get baseUrl async {
    if (_cachedBase != null && _cachedBase!.isNotEmpty) return _cachedBase!;
    try {
      final row = await SupabaseService.kokoro
          .from('app_config')
          .select('value')
          .eq('key', 'music_api_base')
          .maybeSingle();
      _cachedBase = row?['value']?.toString() ??
          'https://creatures-band-wearing-partners.trycloudflare.com';
    } catch (_) {
      _cachedBase = 'https://creatures-band-wearing-partners.trycloudflare.com';
    }
    return _cachedBase!;
  }

  static void clearCache() => _cachedBase = null;

  static Future<void> setBaseUrl(String url) async {
    final clean = url.trim().replaceAll(RegExp(r'/$'), '');
    await SupabaseService.kokoro.from('app_config').upsert({
      'key': 'music_api_base',
      'value': clean,
      'updated_at': DateTime.now().toIso8601String(),
    });
    _cachedBase = clean;
  }

  /// Search local catalog + remote API (triggers download if missing).
  static Future<List<Map<String, dynamic>>> search(
    String query, {
    void Function(String status, double? progress)? onStatus,
  }) async {
    final q = query.trim();
    if (q.isEmpty) return [];

    List<Map<String, dynamic>> local = [];
    try {
      local = await SupabaseService.searchSongs(q, limit: 30);
    } catch (_) {}

    // Always hit remote so missing tracks get downloaded
    onStatus?.call('Buscando en catálogo remoto…', 0.12);
    try {
      final base = await baseUrl;
      final uri = Uri.parse('$base/api/search').replace(queryParameters: {'q': q});
      // Animate progress while the server downloads (API is blocking until done)
      var fake = 0.12;
      final ticker = Stream.periodic(const Duration(milliseconds: 450), (i) => i).listen((_) {
        fake = (fake + 0.03).clamp(0.12, 0.92);
        onStatus?.call(
          fake < 0.4
              ? 'Buscando…'
              : (fake < 0.75 ? 'Descargando audio…' : 'Procesando en servidor…'),
          fake,
        );
      });
      late final http.Response res;
      try {
        res = await http.get(uri).timeout(const Duration(seconds: 120));
      } finally {
        await ticker.cancel();
      }
      if (res.statusCode == 200) {
        final body = jsonDecode(res.body);
        List results = [];
        String? source;
        if (body is Map) {
          source = body['source']?.toString();
          if (body['results'] is List) results = body['results'] as List;
        } else if (body is List) {
          results = body;
        }

        if (source == 'downloaded_on_demand' ||
            source == 'download' ||
            source == 'downloading') {
          onStatus?.call('Descargada en el servidor ✓', 1.0);
        } else if (source == 'database') {
          onStatus?.call('En catálogo', 1.0);
        } else {
          onStatus?.call('Listo', 1.0);
        }

        final remote = results.map((e) {
          final m = Map<String, dynamic>.from(e as Map);
          if (source == 'downloaded_on_demand') {
            m['_just_downloaded'] = true;
          }
          return m;
        }).toList();

        // Prefer remote (has fresh audio_url)
        final merged = <String, Map<String, dynamic>>{};
        for (final s in local) {
          final k = '${s['title']}_${s['artist']}'.toLowerCase();
          merged[k] = s;
        }
        for (final s in remote) {
          final k = '${s['title']}_${s['artist']}'.toLowerCase();
          merged[k] = s;
        }
        // If remote returned something local didn't, put remote first
        final list = merged.values.toList();
        list.sort((a, b) {
          final ad = a['_just_downloaded'] == true ? 0 : 1;
          final bd = b['_just_downloaded'] == true ? 0 : 1;
          return ad.compareTo(bd);
        });
        return list;
      } else {
        onStatus?.call('Servidor ${res.statusCode}', null);
      }
    } catch (e) {
      onStatus?.call('API lenta o túnel caído. Reintenta.', null);
    }
    return local;
  }
}
