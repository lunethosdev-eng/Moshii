  import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

/// Live audio-output label (Bluetooth headphones name, speaker, etc.).
class HeadphoneService extends ChangeNotifier {
  static final HeadphoneService instance = HeadphoneService._();
  HeadphoneService._();

  static const _channel = MethodChannel('com.pokke.app/audio_output');

  String? _deviceName;
  bool _wired = false;
  bool _bluetooth = false;
  Timer? _poll;

  final _connectedCtrl = StreamController<String>.broadcast();
  final _deviceCtrl = StreamController<String?>.broadcast();

  /// Emits when a new external device name is detected.
  Stream<String> get onConnected => _connectedCtrl.stream;

  /// Emits on every refresh (name or null).
  Stream<String?> get onDeviceChanged => _deviceCtrl.stream;

  String? get deviceName => _deviceName;
  String? get currentDevice => _deviceName;

  bool get isExternal =>
      _wired ||
      _bluetooth ||
      (_deviceName != null &&
          _deviceName!.isNotEmpty &&
          !_deviceName!.toLowerCase().contains('speaker') &&
          !_deviceName!.toLowerCase().contains('altavoz') &&
          !_deviceName!.toLowerCase().contains('phone') &&
          !_deviceName!.toLowerCase().contains('teléfono') &&
          !_deviceName!.toLowerCase().contains('este teléfono'));

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
      String? name;
      String type = '';
      if (res is Map) {
        name = (res['name'] ?? res['deviceName'] ?? '').toString();
        type = (res['type'] ?? '').toString().toLowerCase();
      } else if (res is String) {
        name = res;
      }
      if (name != null && name.isEmpty) name = null;

      _wired = type.contains('wired') || type.contains('headset');
      _bluetooth = type.contains('bluetooth') ||
          type.contains('a2dp') ||
          type.contains('ble');

      final changed = name != _deviceName;
      _deviceName = name;
      if (!_deviceCtrl.isClosed) _deviceCtrl.add(name);
      if (changed) {
        notifyListeners();
        if (name != null && isExternal && !_connectedCtrl.isClosed) {
          _connectedCtrl.add(name);
        }
      }
    } catch (e) {
      if (kDebugMode) debugPrint('HeadphoneService: $e');
    }
  }

  void simulate(String deviceName) {
    _deviceName = deviceName;
    _bluetooth = true;
    if (!_deviceCtrl.isClosed) _deviceCtrl.add(deviceName);
    if (!_connectedCtrl.isClosed) _connectedCtrl.add(deviceName);
    notifyListeners();
  }
}
