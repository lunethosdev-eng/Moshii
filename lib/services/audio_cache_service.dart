import 'dart:io';
import 'package:dio/dio.dart';
import 'package:path_provider/path_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Persistent audio cache outside app sandbox when possible
/// (public Music/pokke on Android) so files survive "clear app data".
class AudioCacheService {
  static final AudioCacheService instance = AudioCacheService._();
  AudioCacheService._();

  static const _metaKey = 'pokke_audio_cache_meta_v1';
  Directory? _root;

  Future<Directory> rootDir() async {
    if (_root != null && await _root!.exists()) return _root!;
    Directory? dir;
    if (Platform.isAndroid) {
      // Public Music survives clear-data better than app dirs
      dir = Directory('/storage/emulated/0/Music/pokke_cache');
      try {
        if (!await dir.exists()) await dir.create(recursive: true);
        // probe write
        final probe = File('${dir.path}/.probe');
        await probe.writeAsString('ok');
        await probe.delete();
      } catch (_) {
        dir = await getExternalStorageDirectory();
        dir = Directory('${dir!.path}/pokke_cache');
        if (!await dir.exists()) await dir.create(recursive: true);
      }
    } else {
      final docs = await getApplicationDocumentsDirectory();
      dir = Directory('${docs.path}/pokke_cache');
      if (!await dir.exists()) await dir.create(recursive: true);
    }
    _root = dir;
    return dir!;
  }

  String _safe(String s) =>
      s.replaceAll(RegExp(r'[^\w\s\-\.]'), '').replaceAll(' ', '_').toLowerCase();

  Future<String?> localPathForSongId(String id) async {
    final meta = await _loadMeta();
    final path = meta['song_$id'] as String?;
    if (path != null && await File(path).exists()) return path;
    return null;
  }

  Future<String?> localPathForVoice(String hash) async {
    final meta = await _loadMeta();
    final path = meta['voice_$hash'] as String?;
    if (path != null && await File(path).exists()) return path;
    return null;
  }

  Future<Map<String, dynamic>> _loadMeta() async {
    final p = await SharedPreferences.getInstance();
    final raw = p.getString(_metaKey);
    if (raw == null) return {};
    try {
      // simple key=value lines to avoid json dependency issues
      final map = <String, dynamic>{};
      for (final line in raw.split('\n')) {
        final i = line.indexOf('=');
        if (i > 0) map[line.substring(0, i)] = line.substring(i + 1);
      }
      return map;
    } catch (_) {
      return {};
    }
  }

  Future<void> _saveMeta(Map<String, dynamic> map) async {
    final p = await SharedPreferences.getInstance();
    final buf = StringBuffer();
    map.forEach((k, v) => buf.writeln('$k=$v'));
    await p.setString(_metaKey, buf.toString());
    // also mirror meta file on disk so we can recover after clear-data
    try {
      final root = await rootDir();
      await File('${root.path}/_meta.txt').writeAsString(buf.toString());
    } catch (_) {}
  }

  Future<void> _recoverMetaFromDisk() async {
    try {
      final root = await rootDir();
      final f = File('${root.path}/_meta.txt');
      if (!await f.exists()) return;
      final raw = await f.readAsString();
      final p = await SharedPreferences.getInstance();
      if ((p.getString(_metaKey) ?? '').isEmpty) {
        await p.setString(_metaKey, raw);
      }
    } catch (_) {}
  }

  Future<String?> ensureSongCached({
    required String id,
    required String title,
    required String artist,
    required String url,
    void Function(double)? onProgress,
  }) async {
    await _recoverMetaFromDisk();
    final existing = await localPathForSongId(id);
    if (existing != null) return existing;
    if (url.isEmpty) return null;
    final root = await rootDir();
    final songsDir = Directory('${root.path}/songs');
    if (!await songsDir.exists()) await songsDir.create(recursive: true);
    final path = '${songsDir.path}/${_safe(id)}_${_safe(artist)}_${_safe(title)}.mp3';
    try {
      await Dio().download(url, path, onReceiveProgress: (r, t) {
        if (t > 0 && onProgress != null) onProgress(r / t);
      });
      final meta = await _loadMeta();
      meta['song_$id'] = path;
      await _saveMeta(meta);
      return path;
    } catch (_) {
      return null;
    }
  }

  Future<String> cacheVoiceBytes(String hash, List<int> bytes) async {
    final root = await rootDir();
    final voiceDir = Directory('${root.path}/voice');
    if (!await voiceDir.exists()) await voiceDir.create(recursive: true);
    final path = '${voiceDir.path}/$hash.mp3';
    await File(path).writeAsBytes(bytes, flush: true);
    final meta = await _loadMeta();
    meta['voice_$hash'] = path;
    await _saveMeta(meta);
    return path;
  }

  Future<int> cachedSongCount() async {
    await _recoverMetaFromDisk();
    final meta = await _loadMeta();
    return meta.keys.where((k) => k.startsWith('song_')).length;
  }
}
