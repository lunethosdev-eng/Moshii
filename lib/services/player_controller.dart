import 'dart:async';
import 'dart:convert';
import 'package:audioplayers/audioplayers.dart';
import 'package:flutter/foundation.dart';
import 'package:pokke/services/audio_cache_service.dart';
import 'package:pokke/services/listening_history.dart';
import 'package:pokke/services/settings_service.dart';
import 'package:pokke/services/local_music_service.dart';
import 'package:pokke/services/supabase_service.dart';
import 'package:shared_preferences/shared_preferences.dart';

class PlayerController extends ChangeNotifier {
  static final PlayerController instance = PlayerController._();

  PlayerController._() {
    _wire(_playerA, false);
    _wire(_playerB, true);
    // Prefer media player mode so Android can surface media controls
    unawaited(_playerA.setPlayerMode(PlayerMode.mediaPlayer));
    unawaited(_playerB.setPlayerMode(PlayerMode.mediaPlayer));
  }

  void _wire(AudioPlayer p, bool isB) {
    p.onPlayerComplete.listen((_) {
      if (_usingB == isB && !_advancing) {
        unawaited(_advanceToNext());
      }
    });
    p.onPositionChanged.listen((pos) {
      if (_usingB == isB) {
        position = pos;
        // near end, ensure next is ready
        if (duration.inMilliseconds > 0 &&
            pos.inMilliseconds > duration.inMilliseconds - 4000) {
          unawaited(_preloadNext());
        }
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

  final AudioPlayer _playerA = AudioPlayer();
  final AudioPlayer _playerB = AudioPlayer();
  bool _usingB = false;
  bool _pausedForDj = false;
  bool _advancing = false;
  bool _nextReady = false;
  int _preloadedIndex = -1;

  Map<String, dynamic>? currentSong;
  Map<String, dynamic>? get current => currentSong;

  List<Map<String, dynamic>> queue = [];
  int queueIndex = 0;
  bool shuffleMode = false;

  Duration position = Duration.zero;
  Duration duration = Duration.zero;
  bool isPlaying = false;

  AudioPlayer get _active => _usingB ? _playerB : _playerA;
  AudioPlayer get _idle => _usingB ? _playerA : _playerB;

  String _songKey(Map<String, dynamic> s) =>
      (s['id'] ?? s['audio_url'] ?? s['title']).toString();

  Future<void> playPlaylist(
    List<Map<String, dynamic>> songs, {
    bool shuffle = false,
    int startIndex = 0,
  }) async {
    if (songs.isEmpty) return;
    queue = List<Map<String, dynamic>>.from(songs);
    shuffleMode = shuffle;
    if (shuffle) {
      queue.shuffle();
      queueIndex = 0;
    } else {
      queueIndex = startIndex.clamp(0, queue.length - 1);
    }
    _nextReady = false;
    _preloadedIndex = -1;
    await playSong(queue[queueIndex], forceRestart: true);
  }

  Future<void> _preloadNext() async {
    try {
      if (queue.length < 2) return;
      final nextIdx = (queueIndex + 1) % queue.length;
      if (_nextReady && _preloadedIndex == nextIdx) return;
      final next = queue[nextIdx];
      final url = (next['audio_url'] ?? next['stream_url'] ?? '').toString();
      final id = (next['id'] ?? _songKey(next)).toString();

      // Prefer local cache
      String? local = await AudioCacheService.instance.localPathForSongId(id);
      await _idle.stop();
      if (local != null) {
        await _idle.play(DeviceFileSource(local), volume: 0);
      } else if (url.isNotEmpty) {
        await _idle.play(UrlSource(url), volume: 0);
        unawaited(AudioCacheService.instance.ensureSongCached(
          id: id,
          title: (next['title'] ?? '').toString(),
          artist: (next['artist'] ?? '').toString(),
          url: url,
        ));
      } else {
        return;
      }
      await _idle.pause();
      await _idle.seek(Duration.zero);
      await _idle.setVolume(1);
      _nextReady = true;
      _preloadedIndex = nextIdx;
      debugPrint('preloaded queue[$nextIdx] ${(next['title'] ?? '')}');
    } catch (e) {
      _nextReady = false;
      debugPrint('preloadNext: $e');
    }
  }

  Future<void> _advanceToNext() async {
    if (_advancing || queue.isEmpty) return;
    _advancing = true;
    try {
      // Single-song queue → expand so auto-next has real next tracks
      if (queue.length < 2 && currentSong != null) {
        await _expandQueueAround(currentSong!);
      }
      if (queue.isEmpty) return;
      final nextIdx = (queueIndex + 1) % queue.length;
      queueIndex = nextIdx;
      final next = queue[nextIdx];
      currentSong = next;
      notifyListeners();

      if (_nextReady && _preloadedIndex == nextIdx) {
        // Instant switch: idle already has the source
        try {
          await _active.stop();
        } catch (_) {}
        _usingB = !_usingB;
        _nextReady = false;
        _preloadedIndex = -1;
        await _active.resume();
        // if resume fails (some platforms need play), fall through
        final st = await _active.state;
        if (st != PlayerState.playing) {
          await _active.seek(Duration.zero);
          await _active.resume();
        }
        isPlaying = true;
        position = Duration.zero;
        notifyListeners();
        unawaited(_recordPlay(next));
        unawaited(_preloadNext());
        return;
      }

      // Fallback: normal load
      await playSong(next, forceRestart: true);
    } catch (e) {
      debugPrint('advanceToNext: $e');
      try {
        await playSong(queue[queueIndex], forceRestart: true);
      } catch (_) {}
    } finally {
      _advancing = false;
    }
  }

  Future<void> playNextInQueue() async {
    if (queue.isEmpty) return;
    await _advanceToNext();
  }

  Future<void> playPreviousInQueue() async {
    if (queue.isEmpty) return;
    queueIndex = (queueIndex - 1) < 0 ? queue.length - 1 : queueIndex - 1;
    _nextReady = false;
    _preloadedIndex = -1;
    await playSong(queue[queueIndex], forceRestart: true);
  }

  Future<void> playSong(Map<String, dynamic> song,
      {bool forceRestart = false}) async {
    final key = _songKey(song);
    if (!forceRestart &&
        currentSong != null &&
        _songKey(currentSong!) == key &&
        isPlaying) {
      return;
    }
    currentSong = song;
    notifyListeners();

    final id = (song['id'] ?? key).toString();
    final title = (song['title'] ?? '').toString();
    final artist = (song['artist'] ?? '').toString();
    final url = (song['audio_url'] ?? song['stream_url'] ?? song['path'] ?? '').toString();
    final isLocal = song['is_local'] == true ||
        (url.isNotEmpty && !url.startsWith('http://') && !url.startsWith('https://'));

    String? sourcePath =
        await AudioCacheService.instance.localPathForSongId(id);
    // Local device files / imported paths
    if (sourcePath == null && isLocal && url.isNotEmpty) {
      sourcePath = url;
    }
    if (sourcePath == null && url.isNotEmpty && !isLocal) {
      unawaited(AudioCacheService.instance.ensureSongCached(
        id: id,
        title: title,
        artist: artist,
        url: url,
      ));
    }

    try {
      await _active.stop();
      if (sourcePath != null) {
        await _active.play(DeviceFileSource(sourcePath));
      } else if (url.isNotEmpty) {
        await _active.play(UrlSource(url));
      } else {
        debugPrint('No audio_url for song: $title');
        return;
      }
      isPlaying = true;
      _pausedForDj = false;
      _nextReady = false;
      // If queue empty (tap from Home), build a queue around this song
      if (queue.isEmpty) {
        queue = [song];
        queueIndex = 0;
        if (song['is_local'] == true) {
          final local = LocalMusicService.instance.tracks;
          if (local.length > 1) {
            queue = List<Map<String, dynamic>>.from(local);
            final idx = queue.indexWhere((s) => _songKey(s) == key);
            queueIndex = idx >= 0 ? idx : 0;
          }
        } else {
          unawaited(_expandQueueAround(song));
        }
      } else {
        final key = _songKey(song);
        final idx = queue.indexWhere((s) => _songKey(s) == key);
        if (idx >= 0) queueIndex = idx;
      }
      notifyListeners();
      unawaited(_recordPlay(song));
      unawaited(_persistSession());
      // Preload next ASAP
      unawaited(Future.delayed(const Duration(milliseconds: 400), _preloadNext));
    } catch (e) {
      debugPrint('playSong error: $e');
    }
  }

  Future<void> _recordPlay(Map<String, dynamic> song) async {
    try {
      if (SettingsService.instance.historyLocal) {
        await ListeningHistory.instance.recordPlay(song);
      }
    } catch (_) {}
    try {
      await SupabaseService.kokoro.from('song_plays').insert({
        'song_id': (song['id'] ?? '').toString(),
        'title': (song['title'] ?? '').toString(),
        'artist': (song['artist'] ?? '').toString(),
      });
    } catch (_) {}
  }

  void attachSong(Map<String, dynamic> song) {
    final key = _songKey(song);
    if (currentSong != null && _songKey(currentSong!) == key) {
      notifyListeners();
      return;
    }
    currentSong = song;
    notifyListeners();
  }

  Future<void> seek(Duration position) async {
    await _active.seek(position);
    this.position = position;
    notifyListeners();
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

  Future<void> pause() async {
    await _active.pause();
    isPlaying = false;
    notifyListeners();
  }

  Future<void> resume() async {
    await _active.resume();
    isPlaying = true;
    notifyListeners();
  }

  /// Crossfade to another song using dual players (settings crossfade).
  Future<void> crossfadeTo(Map<String, dynamic> song,
      {Duration fade = const Duration(seconds: 4)}) async {
    final url = (song['audio_url'] ?? song['stream_url'] ?? '').toString();
    if (url.isEmpty) {
      await playSong(song, forceRestart: true);
      return;
    }
    final incoming = _idle;
    try {
      final id = (song['id'] ?? _songKey(song)).toString();
      final local = await AudioCacheService.instance.localPathForSongId(id);
      await incoming.stop();
      if (local != null) {
        await incoming.play(DeviceFileSource(local));
      } else {
        await incoming.play(UrlSource(url));
      }
      await incoming.setVolume(0);
      final steps = 20;
      final ms = fade.inMilliseconds ~/ steps;
      for (var i = 1; i <= steps; i++) {
        final t = i / steps;
        await _active.setVolume(1.0 - t);
        await incoming.setVolume(t);
        await Future.delayed(Duration(milliseconds: ms));
      }
      await _active.stop();
      await _active.setVolume(1);
      await incoming.setVolume(1);
      _usingB = !_usingB;
      currentSong = song;
      isPlaying = true;
      _nextReady = false;
      notifyListeners();
      unawaited(_preloadNext());
    } catch (e) {
      debugPrint('crossfade: $e');
      await playSong(song, forceRestart: true);
    }
  }

  Future<void> _expandQueueAround(Map<String, dynamic> song) async {
    try {
      final more = await SupabaseService.getSongs(limit: 40);
      final key = _songKey(song);
      final rest = more.where((s) => _songKey(s) != key).toList();
      if (rest.isEmpty) return;
      queue = [song, ...rest];
      queueIndex = 0;
      notifyListeners();
      unawaited(_preloadNext());
    } catch (e) {
      debugPrint('expandQueue: $e');
    }
  }

  Future<void> _persistSession() async {
    try {
      final p = await SharedPreferences.getInstance();
      final s = currentSong;
      if (s == null) return;
      await p.setString('session_song_json', jsonEncode(s));
      await p.setInt('session_queue_index', queueIndex);
    } catch (_) {}
  }

  Future<void> restoreSession() async {
    try {
      final p = await SharedPreferences.getInstance();
      final raw = p.getString('session_song_json');
      if (raw == null || raw.isEmpty) return;
      final map = Map<String, dynamic>.from(jsonDecode(raw) as Map);
      currentSong = map;
      queue = [map];
      queueIndex = 0;
      isPlaying = false;
      notifyListeners();
      unawaited(_expandQueueAround(map));
    } catch (e) {
      debugPrint('restoreSession: $e');
    }
  }
}
