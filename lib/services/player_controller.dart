import 'package:audioplayers/audioplayers.dart';
import 'package:flutter/foundation.dart';
import 'package:pokke/services/listening_history.dart';

/// Global player so mini player and full player share state.
/// Opening full player for the SAME song does NOT restart playback.
class PlayerController extends ChangeNotifier {
  static final PlayerController instance = PlayerController._();
  PlayerController._() {
    _player.onPlayerStateChanged.listen((s) {
      isPlaying = s == PlayerState.playing;
      notifyListeners();
    });
    _player.onDurationChanged.listen((d) {
      duration = d;
      notifyListeners();
    });
    _player.onPositionChanged.listen((p) {
      position = p;
      notifyListeners();
    });
  }

  final AudioPlayer _player = AudioPlayer();
  Map<String, dynamic>? current;
  bool isPlaying = false;
  Duration duration = Duration.zero;
  Duration position = Duration.zero;

  Map<String, dynamic>? get currentSong => current;

  String? _songKey(Map<String, dynamic>? s) {
    if (s == null) return null;
    return (s['id'] ?? s['audio_url'] ?? s['title']).toString();
  }

  /// Play a song. If it's already the current track, just resume / keep position.
  Future<void> playSong(Map<String, dynamic> song, {bool forceRestart = false}) async {
    final newKey = _songKey(song);
    final same = !forceRestart && newKey != null && newKey == _songKey(current);

    if (same) {
      // Same song: do not stop/seek — just ensure UI has data
      current = song;
      notifyListeners();
      if (!isPlaying) {
        try {
          await _player.resume();
        } catch (_) {}
      }
      return;
    }

    current = song;
    notifyListeners();
    ListeningHistory.instance.recordPlay(song);

    final url = song['audio_url'] ?? song['stream_url'];
    if (url != null && url.toString().isNotEmpty) {
      try {
        await _player.stop();
        await _player.play(UrlSource(url.toString()));
      } catch (e) {
        debugPrint('Play error: $e');
      }
    } else {
      debugPrint('No audio_url for song: ${song['title']}');
    }
  }

  /// Open full player without touching playback (used when expanding mini player).
  void attachSong(Map<String, dynamic> song) {
    if (_songKey(song) != _songKey(current)) {
      current = song;
      notifyListeners();
    }
  }

  Future<void> toggle() async {
    if (isPlaying) {
      await _player.pause();
    } else {
      await _player.resume();
    }
  }

  Future<void> togglePlay() => toggle();

  Future<void> seek(Duration d) => _player.seek(d);

  Future<void> stop() async {
    await _player.stop();
    current = null;
    notifyListeners();
  }
}
