import 'package:flutter/services.dart';

/// Google Cast / media route bridge.
/// Uses Android MediaRouter when the method channel is wired in MainActivity.
class CastService {
  static const _methods = MethodChannel('pokke/cast');

  static Future<List<Map<String, String>>> devices() async {
    try {
      final res = await _methods.invokeMethod('listDevices');
      if (res is List) {
        return res
            .map((e) => Map<String, String>.from(
                (e as Map).map((k, v) => MapEntry(k.toString(), v.toString()))))
            .toList();
      }
    } catch (_) {}
    return [];
  }

  static Future<bool> castUrl({
    required String url,
    required String title,
    String? artist,
    String? imageUrl,
  }) async {
    try {
      final ok = await _methods.invokeMethod('castMedia', {
        'url': url,
        'title': title,
        'artist': artist,
        'imageUrl': imageUrl,
      });
      return ok == true;
    } catch (_) {
      return false;
    }
  }

  static Future<void> openSystemCastPicker() async {
    try {
      await _methods.invokeMethod('showCastPicker');
    } catch (_) {}
  }
}
