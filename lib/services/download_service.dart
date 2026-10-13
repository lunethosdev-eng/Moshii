import 'dart:io';
import 'package:dio/dio.dart';
import 'package:path_provider/path_provider.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Offline downloads: in-app storage OR device Music folder.
class DownloadService {
  static final DownloadService instance = DownloadService._();
  DownloadService._();

  static const _metaKey = 'pokke_offline_tracks';

  Future<Directory> get inAppDir async {
    final base = await getApplicationDocumentsDirectory();
    final dir = Directory('${base.path}/offline_music');
    if (!await dir.exists()) await dir.create(recursive: true);
    return dir;
  }

  Future<List<Map<String, dynamic>>> listOffline() async {
    final p = await SharedPreferences.getInstance();
    final raw = p.getStringList(_metaKey) ?? [];
    return raw.map((e) {
      final parts = e.split('|||');
      return {
        'id': parts.isNotEmpty ? parts[0] : '',
        'title': parts.length > 1 ? parts[1] : '',
        'artist': parts.length > 2 ? parts[2] : '',
        'path': parts.length > 3 ? parts[3] : '',
      };
    }).toList();
  }

  Future<void> _saveMeta(String id, String title, String artist, String path) async {
    final p = await SharedPreferences.getInstance();
    final list = p.getStringList(_metaKey) ?? [];
    list.removeWhere((e) => e.startsWith('$id|||'));
    list.add('$id|||$title|||$artist|||$path');
    await p.setStringList(_metaKey, list);
  }

  Future<String?> downloadInApp({
    required String id,
    required String title,
    required String artist,
    required String url,
    void Function(double)? onProgress,
  }) async {
    final dir = await inAppDir;
    final safe = title.replaceAll(RegExp(r'[^\w\s-]'), '').replaceAll(' ', '_');
    final path = '${dir.path}/$id\_$safe.mp3';
    await Dio().download(
      url,
      path,
      onReceiveProgress: (r, t) {
        if (t > 0 && onProgress != null) onProgress(r / t);
      },
    );
    await _saveMeta(id, title, artist, path);
    return path;
  }

  Future<String?> downloadToDeviceMusic({
    required String title,
    required String artist,
    required String url,
    void Function(double)? onProgress,
  }) async {
    // Android 13+ media permission
    if (Platform.isAndroid) {
      final status = await Permission.storage.request();
      await Permission.audio.request();
      if (!status.isGranted && !await Permission.manageExternalStorage.request().isGranted) {
        // still try public downloads
      }
    }
    Directory? dir;
    if (Platform.isAndroid) {
      dir = Directory('/storage/emulated/0/Music/pokke');
      if (!await dir.exists()) {
        try {
          await dir.create(recursive: true);
        } catch (_) {
          dir = await getExternalStorageDirectory();
        }
      }
    } else {
      dir = await getApplicationDocumentsDirectory();
    }
    if (dir == null) return null;
    final safe = '${artist}_$title'.replaceAll(RegExp(r'[^\w\s-]'), '').replaceAll(' ', '_');
    final path = '${dir.path}/$safe.mp3';
    await Dio().download(url, path, onReceiveProgress: (r, t) {
      if (t > 0 && onProgress != null) onProgress(r / t);
    });
    return path;
  }
}
