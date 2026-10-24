import 'dart:async';
import 'package:audio_service/audio_service.dart';
import 'package:flutter/foundation.dart';
import 'package:pokke/services/player_controller.dart';

/// Bridges PlayerController → system media notification (lockscreen / shade).
class PokkeAudioHandler extends BaseAudioHandler with SeekHandler {
  PokkeAudioHandler() {
    final pc = PlayerController.instance;
    pc.addListener(_syncFromPlayer);
    _syncFromPlayer();
  }

  void _syncFromPlayer() {
    final pc = PlayerController.instance;
    final song = pc.current;
    if (song != null) {
      final id = (song['id'] ?? song['title'] ?? 'unknown').toString();
      final title = (song['title'] ?? 'Unknown').toString();
      final artist = (song['artist'] ?? '').toString();
      final art = (song['cover_url'] ?? song['animated_cover_url'])?.toString();
      final dur = pc.duration;
      mediaItem.add(
        MediaItem(
          id: id,
          title: title,
          artist: artist,
          artUri: (art != null && art.startsWith('http')) ? Uri.parse(art) : null,
          duration: dur.inMilliseconds > 0 ? dur : null,
        ),
      );
    }

    playbackState.add(
      PlaybackState(
        controls: [
          MediaControl.skipToPrevious,
          pc.isPlaying ? MediaControl.pause : MediaControl.play,
          MediaControl.skipToNext,
          MediaControl.stop,
        ],
        systemActions: const {
          MediaAction.seek,
          MediaAction.seekForward,
          MediaAction.seekBackward,
        },
        androidCompactActionIndices: const [0, 1, 2],
        processingState: AudioProcessingState.ready,
        playing: pc.isPlaying,
        updatePosition: pc.position,
        bufferedPosition: pc.position,
        speed: 1.0,
        queueIndex: pc.queueIndex,
      ),
    );
  }

  @override
  Future<void> play() async {
    final pc = PlayerController.instance;
    if (!pc.isPlaying) await pc.toggle();
    _syncFromPlayer();
  }

  @override
  Future<void> pause() async {
    final pc = PlayerController.instance;
    if (pc.isPlaying) await pc.toggle();
    _syncFromPlayer();
  }

  @override
  Future<void> stop() async {
    final pc = PlayerController.instance;
    if (pc.isPlaying) await pc.toggle();
    await super.stop();
    _syncFromPlayer();
  }

  @override
  Future<void> skipToNext() async {
    await PlayerController.instance.playNextInQueue();
    _syncFromPlayer();
  }

  @override
  Future<void> skipToPrevious() async {
    await PlayerController.instance.playPreviousInQueue();
    _syncFromPlayer();
  }

  @override
  Future<void> seek(Duration position) async {
    try {
      // PlayerController may not expose seek publicly — try via active player path
      await PlayerController.instance.seek(position);
    } catch (e) {
      debugPrint('media seek: $e');
    }
    _syncFromPlayer();
  }
}

PokkeAudioHandler? _handler;

Future<void> initMediaAudioService() async {
  try {
    _handler = await AudioService.init(
      builder: () => PokkeAudioHandler(),
      config: const AudioServiceConfig(
        androidNotificationChannelId: 'com.pokke.audio',
        androidNotificationChannelName: 'Pokke Music',
        androidNotificationOngoing: true,
        androidStopForegroundOnPause: true,
        androidNotificationIcon: 'mipmap/ic_launcher',
        fastForwardInterval: Duration(seconds: 10),
        rewindInterval: Duration(seconds: 10),
      ),
    );
  } catch (e) {
    debugPrint('initMediaAudioService: $e');
  }
}

PokkeAudioHandler? get mediaHandler => _handler;
