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
    'message': 'assets/Sounds/tone_message.mp3',
    'joyous': 'assets/Sounds/tone_joyous.mp3',
    'bubble': 'assets/Sounds/tone_bubble.mp3',
    'twinkle': 'assets/Sounds/tone_twinkle.mp3',
    'marimba': 'assets/Sounds/tone_marimba.mp3',
    'chime': 'assets/Sounds/tone_chime.mp3',
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


  void _dedupeBySong() {
    final seen = <String>{};
    final out = <Map<String, dynamic>>[];
    for (final e in items) {
      final sid = e['song_id']?.toString() ?? e['id']?.toString() ?? '';
      if (sid.isEmpty) {
        out.add(e);
        continue;
      }
      if (seen.contains(sid)) continue;
      seen.add(sid);
      out.add(e);
    }
    items = out;
  }
  Future<void> load() async {
    try {
      final p = await SharedPreferences.getInstance();
      final raw = p.getString(_prefsKey);
      if (raw != null && raw.isNotEmpty) {
        final list = jsonDecode(raw) as List;
        items = list.map((e) {
          final m = Map<String, dynamic>.from(e as Map);
          // normalize read flag (JSON may restore as bool or string)
          m['read'] = m['read'] == true || m['read'] == 'true' || m['read'] == 1;
          return m;
        }).toList();
      }
      _dedupeBySong();
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
    // Files live under assets/Sounds/ (capital S) in the repo.
    final candidates = <String>[
      soundAssets[id]!.replaceFirst('assets/', ''), // Sounds/tone_xxx.mp3
      'Sounds/tone_$id.mp3',
      'sounds/tone_$id.mp3',
    ];
    await _sfx.stop();
    // Low-latency mode so tones aren't mixed into media session
    try {
      await _sfx.setPlayerMode(PlayerMode.lowLatency);
      await _sfx.setReleaseMode(ReleaseMode.stop);
    } catch (_) {}
    Object? lastErr;
    for (final path in candidates) {
      try {
        await _sfx.play(AssetSource(path));
        return;
      } catch (e) {
        lastErr = e;
      }
    }
    debugPrint('previewSound failed for $id: $lastErr');
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
    final seenIds = <String>{};
    for (final e in items) {
      if (e['id'] == id) {
        e['read'] = true;
        final sid = e['song_id']?.toString();
        if (sid != null && sid.isNotEmpty) seenIds.add(sid);
      }
    }
    if (seenIds.isNotEmpty) await _markSeen(seenIds);
    await _save();
  }

  Future<void> markAllRead() async {
    final seenIds = <String>{};
    for (final e in items) {
      e['read'] = true;
      final sid = e['song_id']?.toString();
      if (sid != null && sid.isNotEmpty) seenIds.add(sid);
    }
    if (seenIds.isNotEmpty) await _markSeen(seenIds);
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
