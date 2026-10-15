import 'package:audioplayers/audioplayers.dart';
import 'package:flutter/foundation.dart';
import 'package:pokke/services/audio_cache_service.dart';
import 'package:pokke/services/listening_history.dart';
import 'package:pokke/services/settings_service.dart';
import 'package:pokke/services/supabase_service.dart';

class PlayerController extends ChangeNotifier {
  static final PlayerController instance = PlayerController._();
  PlayerController._() {
    void wire(AudioPlayer p, bool isB) {
      p.onPlayerComplete.listen((_) {
        if (_usingB == isB) notifyListeners();
      });
      p.onPositionChanged.listen((pos) {
        if (_usingB == isB) {
          position = pos;
          notifyListeners();
        }
      });
      p.onDurationChanged.listen((d) {
        if (_usingB == isB) {
          duration = d;
          notifyListeners();
        }
      });
    }
    wire(_playerA, false);
    wire(_playerB, true);
  }

  final AudioPlayer _playerA = AudioPlayer();
  final AudioPlayer _playerB = AudioPlayer();
  bool _usingB = false;
  bool _pausedForDj = false;

  Map<String, dynamic>? currentSong;
  Map<String, dynamic>? get current => currentSong;
  bool isPlaying = false;
  Duration position = Duration.zero;
  Duration duration = Duration.zero;

  AudioPlayer get _active => _usingB ? _playerB : _playerA;

  String _songKey(Map<String, dynamic> s) =>
      (s['id'] ?? s['audio_url'] ?? s['title']).toString();

  Future<void> playSong(Map<String, dynamic> song, {bool forceRestart = false}) async {
    final key = _songKey(song);
    if (!forceRestart && currentSong != null && _songKey(currentSong!) == key && isPlaying) {
      return;
    }
    currentSong = song;
    notifyListeners();

    final id = (song['id'] ?? key).toString();
    final title = (song['title'] ?? '').toString();
    final artist = (song['artist'] ?? '').toString();
    final url = (song['audio_url'] ?? song['stream_url'] ?? '').toString();

    // Prefer persistent local cache
    String? sourcePath = await AudioCacheService.instance.localPathForSongId(id);
    if (sourcePath == null && url.isNotEmpty) {
      // Fire-and-forget cache for next time; stream now if needed
      AudioCacheService.instance.ensureSongCached(
        id: id,
        title: title,
        artist: artist,
        url: url,
      );
    }

    try {
      await _active.stop();
      if (sourcePath != null) {
        await _active.play(DeviceFileSource(sourcePath));
      } else if (url.isNotEmpty) {
        await _active.play(UrlSource(url));
        // also start caching while streaming
        if (url.isNotEmpty) {
          AudioCacheService.instance.ensureSongCached(
            id: id,
            title: title,
            artist: artist,
            url: url,
          );
        }
      } else {
        debugPrint('No audio_url for song: $title');
        return;
      }
      isPlaying = true;
      _pausedForDj = false;
      notifyListeners();
      try {
        await ListeningHistory.instance.recordPlay(song);
      } catch (_) {}
      try {
        await SupabaseService.kokoro.from('song_plays').insert({
          'song_id': id,
          'title': title,
          'artist': artist,
        });
      } catch (_) {}
    } catch (e) {
      debugPrint('playSong error: $e');
    }
  }

  
  /// Bind UI to a song without restarting playback if already current.
  void attachSong(Map<String, dynamic> song) {
    final key = _songKey(song);
    if (currentSong != null && _songKey(currentSong!) == key) {
      notifyListeners();
      return;
    }
    // Soft attach: set metadata; playback may already be running
    currentSong = song;
    notifyListeners();
  }

  Future<void> seek(Duration position) async {
    try {
      await _active.seek(position);
      this.position = position;
      notifyListeners();
    } catch (_) {}
  }

  Future<void> pauseForDj() async {
    if (!isPlaying) {
      _pausedForDj = false;
      return;
    }
    _pausedForDj = true;
    try {
      await _active.pause();
    } catch (_) {}
    isPlaying = false;
    notifyListeners();
  }

  Future<void> resumeAfterDj() async {
    if (!_pausedForDj && currentSong == null) return;
    try {
      await _active.resume();
      isPlaying = true;
      _pausedForDj = false;
      notifyListeners();
    } catch (_) {
      if (currentSong != null) {
        await playSong(currentSong!, forceRestart: true);
      }
    }
  }

  Future<void> toggle() async {
    if (isPlaying) {
      await _active.pause();
      isPlaying = false;
    } else {
      await _active.resume();
      isPlaying = true;
    }
    notifyListeners();
  }

  Future<void> stop() async {
    await _active.stop();
    isPlaying = false;
    notifyListeners();
  }

  Future<void> crossfadeTo(Map<String, dynamic> next, {Duration fade = const Duration(seconds: 4)}) async {
    final s = SettingsService.instance;
    if (!s.flag('play_crossfade', def: true) && !s.flag('exp_dual_player', def: true)) {
      await playSong(next, forceRestart: true);
      return;
    }
    final url = next['audio_url'] ?? next['stream_url'];
    if (url == null) {
      await playSong(next, forceRestart: true);
      return;
    }
    final incoming = _usingB ? _playerA : _playerB;
    final outgoing = _active;
    try {
      await incoming.setVolume(0);
      final id = (next['id'] ?? '').toString();
      final local = id.isEmpty ? null : await AudioCacheService.instance.localPathForSongId(id);
      if (local != null) {
        await incoming.play(DeviceFileSource(local));
      } else {
        await incoming.play(UrlSource(url.toString()));
      }
      const steps = 20;
      final stepMs = fade.inMilliseconds ~/ steps;
      for (var i = 1; i <= steps; i++) {
        await Future.delayed(Duration(milliseconds: stepMs));
        final t = i / steps;
        await incoming.setVolume(t);
        await outgoing.setVolume(1 - t);
      }
      await outgoing.stop();
      await outgoing.setVolume(1);
      _usingB = !_usingB;
      currentSong = next;
      isPlaying = true;
      notifyListeners();
    } catch (_) {
      await playSong(next, forceRestart: true);
    }
  }
}
