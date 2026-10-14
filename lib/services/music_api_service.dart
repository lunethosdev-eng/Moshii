import 'dart:async';
import 'dart:convert';
import 'package:http/http.dart' as http;
import 'package:pokke/services/supabase_service.dart';

/// Remote music search / on-demand download + real progress (SSE / polling).
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

  static double? _parsePercent(dynamic v) {
    if (v == null) return null;
    if (v is num) return (v.toDouble() / (v > 1.5 ? 100.0 : 1.0)).clamp(0.0, 1.0);
    final s = v.toString().replaceAll('%', '').trim();
    final n = double.tryParse(s);
    if (n == null) return null;
    return (n > 1.5 ? n / 100.0 : n).clamp(0.0, 1.0);
  }

  /// Listen to SSE progress stream. Returns cancel function.
  static Future<void Function()> listenProgressStream({
    required void Function(String status, double? progress, String? youtubeId) onEvent,
  }) async {
    final base = await baseUrl;
    final uri = Uri.parse('$base/api/progress-stream');
    final client = http.Client();
    final req = http.Request('GET', uri);
    req.headers['Accept'] = 'text/event-stream';
    late StreamSubscription sub;
    try {
      final streamed = await client.send(req).timeout(const Duration(seconds: 10));
      final buffer = StringBuffer();
      sub = streamed.stream.transform(utf8.decoder).listen(
        (chunk) {
          buffer.write(chunk);
          final raw = buffer.toString();
          final parts = raw.split('\n');
          // keep incomplete last line
          buffer.clear();
          if (!raw.endsWith('\n')) {
            buffer.write(parts.isNotEmpty ? parts.removeLast() : '');
          }
          for (final line in parts) {
            final t = line.trim();
            if (t.isEmpty || t.startsWith(':')) continue;
            String data = t;
            if (t.startsWith('data:')) data = t.substring(5).trim();
            try {
              final j = jsonDecode(data);
              if (j is Map) {
                final pct = _parsePercent(j['percent']);
                final status = j['status']?.toString() ?? 'downloading';
                final yid = j['youtubeId']?.toString() ?? j['youtube_id']?.toString();
                onEvent(status, pct, yid);
              }
            } catch (_) {}
          }
        },
        onError: (_) {},
        onDone: () {},
        cancelOnError: true,
      );
    } catch (_) {
      client.close();
      return () {};
    }
    return () {
      sub.cancel();
      client.close();
    };
  }

  /// Poll progress for a youtube id.
  static Future<Map<String, dynamic>?> pollProgress(String id) async {
    try {
      final base = await baseUrl;
      final uri = Uri.parse('$base/api/progress').replace(queryParameters: {'id': id});
      final res = await http.get(uri).timeout(const Duration(seconds: 8));
      if (res.statusCode == 200) {
        final j = jsonDecode(res.body);
        if (j is Map) return Map<String, dynamic>.from(j);
      }
    } catch (_) {}
    return null;
  }

  /// Search local catalog + remote API (triggers download if missing).
  /// Uses SSE for real % when the server exposes /api/progress-stream.
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

    onStatus?.call('Conectando…', 0.05);

    void Function()? cancelSse;
    try {
      cancelSse = await listenProgressStream(
        onEvent: (status, progress, yid) {
          final label = status == 'downloading'
              ? 'Descargando${progress != null ? ' ${(progress * 100).toStringAsFixed(0)}%' : '…'}'
              : status;
          onStatus?.call(label, progress);
        },
      );
    } catch (_) {}

    try {
      final base = await baseUrl;
      final uri = Uri.parse('$base/api/search').replace(queryParameters: {'q': q});
      final res = await http.get(uri).timeout(const Duration(seconds: 120));
      cancelSse?.call();

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
          onStatus?.call('Descarga completa ✓', 1.0);
        } else if (source == 'database') {
          onStatus?.call('En catálogo', 1.0);
        } else {
          onStatus?.call('Listo', 1.0);
        }

        final remote = results.map((e) {
          final m = Map<String, dynamic>.from(e as Map);
          if (source == 'downloaded_on_demand') m['_just_downloaded'] = true;
          return m;
        }).toList();

        final merged = <String, Map<String, dynamic>>{};
        for (final s in local) {
          merged['${s['title']}_${s['artist']}'.toLowerCase()] = s;
        }
        for (final s in remote) {
          merged['${s['title']}_${s['artist']}'.toLowerCase()] = s;
        }
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
      cancelSse?.call();
      onStatus?.call('API lenta o túnel caído. Reintenta.', null);
    }
    return local;
  }
}
