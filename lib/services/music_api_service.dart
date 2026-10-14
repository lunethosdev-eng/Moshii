import 'dart:async';
import 'dart:convert';
import 'package:http/http.dart' as http;
import 'package:pokke/services/supabase_service.dart';

class DownloadProgress {
  final String status;
  final double? progress; // 0..1
  final String? youtubeId;
  final String? title;
  final String? artist;
  final String? coverUrl;
  const DownloadProgress({
    required this.status,
    this.progress,
    this.youtubeId,
    this.title,
    this.artist,
    this.coverUrl,
  });
}

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
    if (v is num) {
      final n = v.toDouble();
      return (n > 1.5 ? n / 100.0 : n).clamp(0.0, 1.0);
    }
    final s = v.toString().replaceAll('%', '').trim();
    final n = double.tryParse(s);
    if (n == null) return null;
    return (n > 1.5 ? n / 100.0 : n).clamp(0.0, 1.0);
  }

  static DownloadProgress _fromJson(Map j) {
    return DownloadProgress(
      status: j['status']?.toString() ?? 'downloading',
      progress: _parsePercent(j['percent'] ?? j['progress'] ?? j['percentage']),
      youtubeId: j['youtubeId']?.toString() ?? j['youtube_id']?.toString(),
      title: j['title']?.toString() ?? j['track']?.toString() ?? j['name']?.toString(),
      artist: j['artist']?.toString() ?? j['channel']?.toString(),
      coverUrl: j['cover_url']?.toString() ??
          j['cover']?.toString() ??
          j['thumbnail']?.toString() ??
          j['thumb']?.toString(),
    );
  }

  /// SSE progress stream. Returns cancel callback.
  static Future<void Function()> listenProgressStream({
    required void Function(DownloadProgress p) onEvent,
  }) async {
    final base = await baseUrl;
    final uri = Uri.parse('$base/api/progress-stream');
    final client = http.Client();
    StreamSubscription? sub;
    try {
      final req = http.Request('GET', uri);
      req.headers['Accept'] = 'text/event-stream';
      req.headers['Cache-Control'] = 'no-cache';
      final streamed = await client.send(req).timeout(const Duration(seconds: 12));
      final buffer = StringBuffer();
      sub = streamed.stream.transform(utf8.decoder).listen(
        (chunk) {
          buffer.write(chunk);
          var raw = buffer.toString();
          // Normalize SSE frames
          raw = raw.replaceAll('\r\n', '\n');
          final frames = raw.split('\n\n');
          buffer.clear();
          if (!raw.endsWith('\n\n') && frames.isNotEmpty) {
            buffer.write(frames.removeLast());
          }
          for (final frame in frames) {
            for (final line in frame.split('\n')) {
              final t = line.trim();
              if (t.isEmpty || t.startsWith(':')) continue;
              String data = t;
              if (t.toLowerCase().startsWith('data:')) {
                data = t.substring(5).trim();
              }
              try {
                final j = jsonDecode(data);
                if (j is Map) onEvent(_fromJson(j));
              } catch (_) {
                // sometimes percent alone
                final pct = _parsePercent(data);
                if (pct != null) {
                  onEvent(DownloadProgress(status: 'downloading', progress: pct));
                }
              }
            }
          }
        },
        onError: (_) {},
        cancelOnError: true,
      );
    } catch (_) {
      client.close();
      return () {};
    }
    return () {
      sub?.cancel();
      client.close();
    };
  }

  static Future<DownloadProgress?> pollProgress(String id) async {
    try {
      final base = await baseUrl;
      final uri = Uri.parse('$base/api/progress').replace(queryParameters: {'id': id});
      final res = await http.get(uri).timeout(const Duration(seconds: 8));
      if (res.statusCode == 200) {
        final j = jsonDecode(res.body);
        if (j is Map) return _fromJson(j);
      }
    } catch (_) {}
    return null;
  }

  /// Search + download. [onProgress] receives real SSE % when available.
  static Future<List<Map<String, dynamic>>> search(
    String query, {
    void Function(DownloadProgress p)? onProgress,
    void Function(String status, double? progress)? onStatus, // legacy
  }) async {
    final q = query.trim();
    if (q.isEmpty) return [];

    List<Map<String, dynamic>> local = [];
    try {
      local = await SupabaseService.searchSongs(q, limit: 30);
    } catch (_) {}

    void emit(DownloadProgress p) {
      onProgress?.call(p);
      onStatus?.call(
        p.status == 'downloading' && p.progress != null
            ? 'Descargando ${(p.progress! * 100).toStringAsFixed(0)}%'
            : p.status,
        p.progress,
      );
    }

    emit(const DownloadProgress(status: 'Conectando…', progress: 0.04));

    void Function()? cancelSse;
    String? lastYid;
    Timer? pollTimer;

    try {
      cancelSse = await listenProgressStream(
        onEvent: (p) {
          if (p.youtubeId != null) lastYid = p.youtubeId;
          emit(p);
        },
      );
    } catch (_) {}

    // Fallback polling every 800ms if we know an id
    pollTimer = Timer.periodic(const Duration(milliseconds: 800), (_) async {
      final id = lastYid;
      if (id == null) return;
      final p = await pollProgress(id);
      if (p != null) emit(p);
    });

    try {
      final base = await baseUrl;
      final uri = Uri.parse('$base/api/search').replace(queryParameters: {'q': q});
      final res = await http.get(uri).timeout(const Duration(seconds: 150));
      pollTimer.cancel();
      cancelSse?.call();

      if (res.statusCode == 200) {
        final body = jsonDecode(res.body);
        List results = [];
        String? source;
        if (body is Map) {
          source = body['source']?.toString();
          if (body['results'] is List) results = body['results'] as List;
          // Some servers include progress meta on the response
          if (body['progress'] != null) {
            emit(_fromJson(Map<String, dynamic>.from(body as Map)));
          }
        } else if (body is List) {
          results = body;
        }

        final doneStatus = (source == 'downloaded_on_demand' || source == 'download')
            ? 'Descarga completa ✓'
            : (source == 'database' ? 'En catálogo' : 'Listo');
        emit(DownloadProgress(status: doneStatus, progress: 1.0));

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
        emit(DownloadProgress(status: 'Servidor ${res.statusCode}'));
      }
    } catch (e) {
      pollTimer.cancel();
      cancelSse?.call();
      emit(const DownloadProgress(status: 'API lenta o túnel caído. Reintenta.'));
    }
    return local;
  }
}
