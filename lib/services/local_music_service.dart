import 'dart:convert';
import 'dart:io';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/foundation.dart';
import 'package:path_provider/path_provider.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Local device music: pick individual files or folders, persist selection.
class LocalMusicService extends ChangeNotifier {
  static final LocalMusicService instance = LocalMusicService._();
  LocalMusicService._();

  static const _prefsKey = 'local_library_json';
  static const _audioExt = {
    'mp3', 'm4a', 'aac', 'wav', 'flac', 'ogg', 'opus', 'wma', 'aiff', 'aif'
  };

  List<Map<String, dynamic>> tracks = [];

  Future<void> load() async {
    try {
      final p = await SharedPreferences.getInstance();
      final raw = p.getString(_prefsKey);
      if (raw == null || raw.isEmpty) {
        tracks = [];
        return;
      }
      final list = jsonDecode(raw) as List;
      tracks = list
          .map((e) => Map<String, dynamic>.from(e as Map))
          .where((t) {
            final path = (t['path'] ?? '').toString();
            return path.isNotEmpty && File(path).existsSync();
          })
          .toList();
      notifyListeners();
    } catch (e) {
      debugPrint('LocalMusic load: $e');
      tracks = [];
    }
  }

  Future<void> _save() async {
    final p = await SharedPreferences.getInstance();
    await p.setString(_prefsKey, jsonEncode(tracks));
    notifyListeners();
  }

  Future<bool> ensurePermission() async {
    if (kIsWeb) return false;
    if (Platform.isAndroid) {
      // Android 13+
      final audio = await Permission.audio.request();
      if (audio.isGranted) return true;
      final storage = await Permission.storage.request();
      return storage.isGranted || audio.isGranted;
    }
    if (Platform.isIOS) {
      // file_picker uses document picker — no special permission
      return true;
    }
    return true;
  }

  Map<String, dynamic> _fromPath(String path, {String? folderTag}) {
    final name = path.split(Platform.pathSeparator).last;
    final title = name.contains('.')
        ? name.substring(0, name.lastIndexOf('.'))
        : name;
    final id = 'local_${path.hashCode.abs()}';
    return {
      'id': id,
      'title': title,
      'artist': folderTag ?? 'Local',
      'album': folderTag ?? 'Imported',
      'audio_url': path,
      'stream_url': path,
      'path': path,
      'cover_url': null,
      'is_local': true,
      'source': 'local',
    };
  }

  bool _isAudio(String path) {
    final ext = path.split('.').last.toLowerCase();
    return _audioExt.contains(ext);
  }

  /// Pick individual audio files (multi-select).
  Future<int> pickFiles() async {
    final ok = await ensurePermission();
    if (!ok && Platform.isAndroid) {
      // still try file_picker — SAF may work without broad storage
    }
    final result = await FilePicker.platform.pickFiles(
      type: FileType.custom,
      allowedExtensions: _audioExt.toList(),
      allowMultiple: true,
      withData: false,
    );
    if (result == null || result.files.isEmpty) return 0;
    var added = 0;
    final existing = tracks.map((t) => t['path'].toString()).toSet();
    for (final f in result.files) {
      final path = f.path;
      if (path == null || path.isEmpty) continue;
      if (!_isAudio(path)) continue;
      if (existing.contains(path)) continue;
      tracks.add(_fromPath(path));
      existing.add(path);
      added++;
    }
    if (added > 0) await _save();
    return added;
  }

  /// Pick a folder and import all audio files inside (recursive, shallow-safe).
  Future<int> pickFolder() async {
    final dir = await FilePicker.platform.getDirectoryPath();
    if (dir == null || dir.isEmpty) return 0;
    return importFolder(dir);
  }

  Future<int> importFolder(String dirPath, {int maxFiles = 500}) async {
    final dir = Directory(dirPath);
    if (!await dir.exists()) return 0;
    var added = 0;
    final existing = tracks.map((t) => t['path'].toString()).toSet();
    final folderName = dirPath.split(Platform.pathSeparator).last;
    try {
      await for (final entity in dir.list(recursive: true, followLinks: false)) {
        if (added >= maxFiles) break;
        if (entity is! File) continue;
        final path = entity.path;
        if (!_isAudio(path)) continue;
        if (existing.contains(path)) continue;
        tracks.add(_fromPath(path, folderTag: folderName));
        existing.add(path);
        added++;
      }
    } catch (e) {
      debugPrint('importFolder: $e');
    }
    if (added > 0) await _save();
    return added;
  }

  Future<void> addPaths(List<String> paths, {String? folderTag}) async {
    final existing = tracks.map((t) => t['path'].toString()).toSet();
    var n = 0;
    for (final path in paths) {
      if (!_isAudio(path) || existing.contains(path)) continue;
      if (!File(path).existsSync()) continue;
      tracks.add(_fromPath(path, folderTag: folderTag));
      existing.add(path);
      n++;
    }
    if (n > 0) await _save();
  }

  Future<void> removeByIds(Iterable<String> ids) async {
    final set = ids.toSet();
    tracks.removeWhere((t) => set.contains((t['id'] ?? '').toString()));
    await _save();
  }

  Future<void> removeByPaths(Iterable<String> paths) async {
    final set = paths.toSet();
    tracks.removeWhere((t) => set.contains((t['path'] ?? '').toString()));
    await _save();
  }

  Future<void> clearAll() async {
    tracks = [];
    await _save();
  }

  /// Copy selected tracks into app documents (optional offline mirror).
  Future<int> copySelectedToAppStorage(List<String> ids) async {
    final dir = await getApplicationDocumentsDirectory();
    final out = Directory('${dir.path}/local_music');
    if (!await out.exists()) await out.create(recursive: true);
    var n = 0;
    for (final t in tracks.where((e) => ids.contains(e['id']?.toString()))) {
      final src = File((t['path'] ?? '').toString());
      if (!await src.exists()) continue;
      final name = src.uri.pathSegments.last;
      final dest = File('${out.path}/$name');
      try {
        await src.copy(dest.path);
        t['path'] = dest.path;
        t['audio_url'] = dest.path;
        n++;
      } catch (_) {}
    }
    if (n > 0) await _save();
    return n;
  }
}
