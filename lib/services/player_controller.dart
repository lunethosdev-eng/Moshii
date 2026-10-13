import 'package:audioplayers/audioplayers.dart';
import 'package:flutter/foundation.dart';

/// Global player so mini player and full player share state.
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

  Future<void> playSong(Map<String, dynamic> song) async {
    current = song;
    notifyListeners();
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
