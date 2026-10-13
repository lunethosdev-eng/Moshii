import 'dart:convert';
import 'package:http/http.dart' as http;
import 'package:pokke/services/supabase_service.dart';

/// Remote music search / on-demand download API (Seki tunnel, URL editable from Admin).
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

  /// Search catalog + remote API. Returns list of song maps.
  /// If remote is downloading, [onStatus] receives progress messages.
  static Future<List<Map<String, dynamic>>> search(
    String query, {
    void Function(String status, double? progress)? onStatus,
  }) async {
    final q = query.trim();
    if (q.isEmpty) return [];

    // 1) Local Ukiyo catalog first
    List<Map<String, dynamic>> local = [];
    try {
      local = await SupabaseService.searchSongs(q, limit: 30);
    } catch (_) {}

    // 2) Remote API (may trigger download for missing tracks)
    onStatus?.call('Buscando en servidor…', null);
    try {
      final base = await baseUrl;
      final uri = Uri.parse('$base/api/search').replace(queryParameters: {'q': q});
      final res = await http.get(uri).timeout(const Duration(seconds: 90));
      if (res.statusCode == 200) {
        final body = jsonDecode(res.body);
        List results = [];
        if (body is Map && body['results'] is List) {
          results = body['results'] as List;
          final source = body['source']?.toString();
          if (source == 'download' || source == 'downloading') {
            onStatus?.call('Descargando…', body['progress'] is num ? (body['progress'] as num).toDouble() : 0.3);
          } else {
            onStatus?.call('Listo', 1);
          }
        } else if (body is List) {
          results = body;
        }
        final remote = results.map((e) => Map<String, dynamic>.from(e as Map)).toList();
        // Merge: prefer remote audio_url if same title
        final merged = <String, Map<String, dynamic>>{};
        for (final s in local) {
          final k = '${s['title']}_${s['artist']}'.toLowerCase();
          merged[k] = s;
        }
        for (final s in remote) {
          final k = '${s['title']}_${s['artist']}'.toLowerCase();
          merged[k] = s;
        }
        return merged.values.toList();
      } else {
        onStatus?.call('Servidor respondió ${res.statusCode}', null);
      }
    } catch (e) {
      onStatus?.call('API no disponible (túnel caído?)', null);
    }
    return local;
  }
}
