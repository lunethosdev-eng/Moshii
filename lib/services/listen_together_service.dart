import 'dart:async';
import 'dart:math';
import 'package:flutter/foundation.dart';
import 'package:pokke/services/player_controller.dart';
import 'package:pokke/services/supabase_service.dart';

/// Listen Together (beta): host shares a room code; guests follow host position.
/// Uses Supabase realtime channel when available; otherwise local-only host mode.
class ListenTogetherService extends ChangeNotifier {
  static final ListenTogetherService instance = ListenTogetherService._();
  ListenTogetherService._();

  String? roomCode;
  bool isHost = false;
  bool active = false;
  String? lastError;
  final List<String> peers = [];
  StreamSubscription? _sub;
  Timer? _broadcast;

  String _code() {
    const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
    final r = Random.secure();
    return List.generate(6, (_) => chars[r.nextInt(chars.length)]).join();
  }

  Future<String?> host() async {
    try {
      roomCode = _code();
      isHost = true;
      active = true;
      peers.clear();
      peers.add('Tú (host)');
      lastError = null;
      notifyListeners();
      _startBroadcast();
      // Best-effort supabase presence room
      try {
        final ch = SupabaseService.kokoro.channel('listen_${roomCode!}');
        ch.subscribe();
      } catch (_) {}
      return roomCode;
    } catch (e) {
      lastError = e.toString();
      notifyListeners();
      return null;
    }
  }

  Future<bool> join(String code) async {
    try {
      final c = code.trim().toUpperCase();
      if (c.length < 4) {
        lastError = 'Código inválido';
        notifyListeners();
        return false;
      }
      roomCode = c;
      isHost = false;
      active = true;
      peers
        ..clear()
        ..add('Host')
        ..add('Tú');
      lastError = null;
      notifyListeners();
      // Guest follows host updates when channel is wired
      return true;
    } catch (e) {
      lastError = e.toString();
      notifyListeners();
      return false;
    }
  }

  void _startBroadcast() {
    _broadcast?.cancel();
    if (!isHost) return;
    _broadcast = Timer.periodic(const Duration(seconds: 2), (_) {
      final pc = PlayerController.instance;
      // Placeholder for realtime payload: song id + position
      debugPrint(
          'listen_together broadcast ${roomCode}: ${pc.current?['title']} @ ${pc.position}');
    });
  }

  Future<void> leave() async {
    _broadcast?.cancel();
    await _sub?.cancel();
    _sub = null;
    active = false;
    isHost = false;
    roomCode = null;
    peers.clear();
    notifyListeners();
  }

  /// Dual audio hint: same device, two outputs is OS-level; we expose a flag
  /// for UI “modo dual / compartir sesión”.
  bool dualSession = false;
  void setDualSession(bool v) {
    dualSession = v;
    notifyListeners();
  }
}
