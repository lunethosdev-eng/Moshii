import 'package:flutter/services.dart';

/// Opens Tori's BitChord-powered native player and feature workspace.
/// Moshi remains the Flutter application shell and retains its existing routes.
class ToriNativeBridge {
  ToriNativeBridge._();
  static const MethodChannel _channel = MethodChannel('pokke/tori');

  static Future<bool> open() async {
    try {
      return await _channel.invokeMethod<bool>('open') ?? false;
    } on PlatformException {
      return false;
    }
  }
}
