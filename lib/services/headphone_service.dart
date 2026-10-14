import 'dart:async';
import 'package:flutter/services.dart';

/// Listens for wired/BT audio route changes via platform events when available.
/// Falls back to a no-op on unsupported platforms.
class HeadphoneService {
  static final HeadphoneService instance = HeadphoneService._();
  HeadphoneService._();

  static const _channel = EventChannel('pokke/audio_route');
  final _controller = StreamController<String>.broadcast();
  StreamSubscription? _sub;
  bool _started = false;

  Stream<String> get onConnected => _controller.stream;

  void start() {
    if (_started) return;
    _started = true;
    try {
      _sub = _channel.receiveBroadcastStream().listen((event) {
        final name = event?.toString() ?? 'Auriculares';
        if (name.isNotEmpty) _controller.add(name);
      }, onError: (_) {});
    } catch (_) {
      // Channel not registered — optional native code
    }
  }

  /// Call from UI when you detect audio focus / want to demo the banner.
  void simulate(String deviceName) => _controller.add(deviceName);

  void dispose() {
    _sub?.cancel();
    _controller.close();
  }
}
