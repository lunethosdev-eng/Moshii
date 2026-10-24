import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

/// Live audio-output label (Bluetooth headphones name, speaker, etc.).
/// Mirrors Spotify’s “CMF Buds 2a” style row under the player controls.
class HeadphoneService extends ChangeNotifier {
  static final HeadphoneService instance = HeadphoneService._();
  HeadphoneService._();

  static const _channel = MethodChannel('com.pokke.app/audio_output');

  String? _deviceName;
  bool _wired = false;
  bool _bluetooth = false;
  Timer? _poll;

  String? get deviceName => _deviceName;
  bool get isExternal => _wired || _bluetooth ||
      (_deviceName != null &&
          _deviceName!.isNotEmpty &&
          !_deviceName!.toLowerCase().contains('speaker') &&
          !_deviceName!.toLowerCase().contains('altavoz') &&
          !_deviceName!.toLowerCase().contains('phone'));

  String get displayLabel {
    if (_deviceName != null && _deviceName!.trim().isNotEmpty) {
      return _deviceName!.trim();
    }
    return 'Este teléfono';
  }

  Future<void> start() async {
    await refresh();
    _poll?.cancel();
    _poll = Timer.periodic(const Duration(seconds: 4), (_) => refresh());
  }

  void stop() {
    _poll?.cancel();
    _poll = null;
  }

  Future<void> refresh() async {
    try {
      final res = await _channel.invokeMethod<dynamic>('getAudioOutput');
      if (res is Map) {
        final name = (res['name'] ?? res['deviceName'] ?? '').toString();
        final type = (res['type'] ?? '').toString().toLowerCase();
        _wired = type.contains('wired') || type.contains('headset');
        _bluetooth = type.contains('bluetooth') || type.contains('a2dp') ||
            type.contains('ble');
        if (name.isNotEmpty && name != _deviceName) {
          _deviceName = name;
          notifyListeners();
        } else if (name.isNotEmpty) {
          _deviceName = name;
        }
      } else if (res is String && res.isNotEmpty) {
        if (res != _deviceName) {
          _deviceName = res;
          notifyListeners();
        }
      }
    } catch (e) {
      // Platform not ready / desktop — keep last known or null
      if (kDebugMode) debugPrint('HeadphoneService: $e');
    }
  }

  /// Dev helper
  void simulate(String deviceName) {
    _deviceName = deviceName;
    _bluetooth = true;
    notifyListeners();
  }
}
