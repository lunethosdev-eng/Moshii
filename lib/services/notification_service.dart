import 'dart:convert';
import 'package:audioplayers/audioplayers.dart';
import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:pokke/services/follow_service.dart';
import 'package:pokke/services/settings_service.dart';
import 'package:pokke/services/supabase_service.dart';

/// In-app notifications (followed artists new tracks, etc.).
class NotificationService extends ChangeNotifier {
  static final NotificationService instance = NotificationService._();
  NotificationService._();

  static const _prefsKey = 'pokke_notifications_v1';
  static const _seenKey = 'pokke_seen_song_ids';

  /// id -> asset path
  static const soundAssets = <String, String>{
    'message': 'assets/sounds/tone_message.mp3',
    'joyous': 'assets/sounds/tone_joyous.mp3',
    'bubble': 'assets/sounds/tone_bubble.mp3',
    'twinkle': 'assets/sounds/tone_twinkle.mp3',
    'marimba': 'assets/sounds/tone_marimba.mp3',
    'chime': 'assets/sounds/tone_chime.mp3',
  };

  static const soundLabels = <String, Map<String, String>>{
    'none': {'es': 'Silencio', 'en': 'Silent'},
    'message': {'es': 'Mensaje', 'en': 'Message'},
    'joyous': {'es': 'Campanilla alegre', 'en': 'Joyous chime'},
    'bubble': {'es': 'Burbuja', 'en': 'Bubble pop'},
    'twinkle': {'es': 'Destello', 'en': 'Twinkle'},
    'marimba': {'es': 'Marimba', 'en': 'Marimba'},
    'chime': {'es': 'Campana', 'en': 'Chime'},
  };

  final AudioPlayer _sfx = AudioPlayer();
  List<Map<String, dynamic>> items = [];
  int unread = 0;

  Future<void> load() async {
    try {
      final p = await SharedPreferences.getInstance();
      final raw = p.getString(_prefsKey);
      if (raw != null && raw.isNotEmpty) {
        final list = jsonDecode(raw) as List;
        items = list.map((e) => Map<String, dynamic>.from(e as Map)).toList();
      }
      unread = items.where((e) => e['read'] != true).length;
      notifyListeners();
    } catch (_) {}
  }

  Future<void> _save() async {
    final p = await SharedPreferences.getInstance();
    await p.setString(_prefsKey, jsonEncode(items));
    unread = items.where((e) => e['read'] != true).length;
    notifyListeners();
  }

  Future<Set<String>> _seenSongIds() async {
    final p = await SharedPreferences.getInstance();
    return (p.getStringList(_seenKey) ?? []).toSet();
  }

  Future<void> _markSeen(Set<String> ids) async {
    final p = await SharedPreferences.getInstance();
    final all = await _seenSongIds()..addAll(ids);
    final list = all.toList();
    if (list.length > 800) {
      await p.setStringList(_seenKey, list.sublist(list.length - 800));
    } else {
      await p.setStringList(_seenKey, list);
    }
  }

  /// Preview a tone from Settings.
  Future<void> previewSound(String id) async {
    if (id == 'none' || !soundAssets.containsKey(id)) return;
    try {
      await _sfx.stop();
      await _sfx.play(AssetSource(soundAssets[id]!.replaceFirst('assets/', '')));
    } catch (e) {
      // AssetSource path is relative to assets/ folder root declared in pubspec
      try {
        await _sfx.play(AssetSource('sounds/tone_$id.mp3'));
      } catch (e2) {
        debugPrint('previewSound: $e2');
      }
    }
  }

  Future<void> _playNotifSound() async {
    final s = SettingsService.instance;
    if (!s.notificationSoundEnabled) return;
    final id = s.notificationSound;
    if (id == 'none' || id.isEmpty) return;
    await previewSound(id);
  }

  /// Poll catalog for songs by followed artists that user hasn't "seen".
  Future<int> refreshFromFollows() async {
    try {
      final follows = await FollowService.list();
      if (follows.isEmpty) return 0;
      final names = follows
          .map((f) => (f['artist_name'] ?? f['name'] ?? '').toString().trim())
          .where((n) => n.isNotEmpty)
          .toList();
      if (names.isEmpty) return 0;

      final songs = await SupabaseService.getSongs(limit: 120);
      final seen = await _seenSongIds();
      final newIds = <String>{};
      var added = 0;

      for (final s in songs) {
        final artist = (s['artist'] ?? '').toString();
        final id = (s['id'] ?? s['title'] ?? '').toString();
        if (id.isEmpty || seen.contains(id)) continue;
        final match = names.any(
            (n) => artist.toLowerCase().contains(n.toLowerCase()));
        if (!match) continue;

        if (items.any((e) => e['song_id']?.toString() == id)) {
          newIds.add(id);
          continue;
        }

        items.insert(0, {
          'id': 'n_${DateTime.now().millisecondsSinceEpoch}_$id',
          'type': 'new_release',
          'title': artist,
          'body': (s['title'] ?? 'New track').toString(),
          'song_id': id,
          'song': s,
          'read': false,
          'created_at': DateTime.now().toIso8601String(),
        });
        newIds.add(id);
        added++;
      }

      if (newIds.isNotEmpty) await _markSeen(newIds);
      if (items.length > 100) items = items.take(100).toList();
      if (added > 0) {
        await _save();
        await _playNotifSound();
      }
      return added;
    } catch (e) {
      debugPrint('NotificationService.refresh: $e');
      return 0;
    }
  }

  Future<void> markRead(String id) async {
    for (final e in items) {
      if (e['id'] == id) e['read'] = true;
    }
    await _save();
  }

  Future<void> markAllRead() async {
    for (final e in items) {
      e['read'] = true;
    }
    await _save();
  }

  Future<void> clear() async {
    items = [];
    await _save();
  }

  void pushLocal({
    required String title,
    required String body,
    String type = 'info',
    Map<String, dynamic>? extra,
    bool playSound = true,
  }) {
    items.insert(0, {
      'id': 'n_${DateTime.now().millisecondsSinceEpoch}',
      'type': type,
      'title': title,
      'body': body,
      'read': false,
      'created_at': DateTime.now().toIso8601String(),
      ...?extra,
    });
    _save();
    if (playSound) _playNotifSound();
  }
}
