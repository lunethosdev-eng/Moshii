import 'dart:convert';
import 'package:shared_preferences/shared_preferences.dart';

/// Local listening history for recommendations, library "Recently", wraps.
class ListeningHistory {
  static final ListeningHistory instance = ListeningHistory._();
  ListeningHistory._();

  static const _key = 'pokke_listening_history_v1';
  List<Map<String, dynamic>> _items = [];
  bool _loaded = false;

  Future<void> load() async {
    if (_loaded) return;
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getString(_key);
    if (raw != null) {
      try {
        final list = jsonDecode(raw) as List;
        _items = list.map((e) => Map<String, dynamic>.from(e as Map)).toList();
      } catch (_) {}
    }
    _loaded = true;
  }

  Future<void> _save() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_key, jsonEncode(_items.take(200).toList()));
  }

  Future<void> recordPlay(Map<String, dynamic> song) async {
    await load();
    final id = (song['id'] ?? song['title']).toString();
    _items.removeWhere((e) => (e['id'] ?? e['title']).toString() == id);
    _items.insert(0, {
      'id': song['id'],
      'title': song['title'],
      'artist': song['artist'],
      'cover_url': song['cover_url'],
      'audio_url': song['audio_url'],
      'album': song['album'],
      'played_at': DateTime.now().toIso8601String(),
    });
    await _save();
  }

  Future<List<Map<String, dynamic>>> recent({int limit = 40}) async {
    await load();
    return _items.take(limit).toList();
  }

  Future<Map<String, int>> artistCounts() async {
    await load();
    final map = <String, int>{};
    for (final s in _items) {
      final a = (s['artist'] ?? 'Unknown').toString();
      map[a] = (map[a] ?? 0) + 1;
    }
    return map;
  }

  Future<List<String>> topArtistNames({int limit = 5}) async {
    final counts = await artistCounts();
    final sorted = counts.entries.toList()
      ..sort((a, b) => b.value.compareTo(a.value));
    return sorted.take(limit).map((e) => e.key).toList();
  }
}
