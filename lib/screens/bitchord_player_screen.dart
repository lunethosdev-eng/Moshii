import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:pokke/services/player_controller.dart';
import 'package:pokke/services/tori_native_bridge.dart';

/// Hosts Tori's original BitChord Player Host as an Android Compose PlatformView.
/// Its visual tree is not reimplemented in Flutter, so the original design remains intact.
class BitChordPlayerScreen extends StatefulWidget {
  const BitChordPlayerScreen({super.key, required this.song});
  final Map<String, dynamic> song;

  @override
  State<BitChordPlayerScreen> createState() => _BitChordPlayerScreenState();
}

class _BitChordPlayerScreenState extends State<BitChordPlayerScreen> {
  static const MethodChannel _channel = MethodChannel('pokke/bitchord');
  List<Map<String, dynamic>>? _queue;
  int _startIndex = 0;
  bool _failed = false;

  String _songKey(Map<String, dynamic>? song) =>
      (song?['id'] ?? song?['audio_url'] ?? song?['title'] ?? '').toString();

  @override
  void initState() {
    super.initState();
    _channel.setMethodCallHandler(_onNativeCall);
    unawaited(_preparePlayer());
  }

  Future<void> _preparePlayer() async {
    final controller = PlayerController.instance;
    try {
      final requestedKey = _songKey(widget.song);
      if (!controller.isNativePlaybackActive) {
        if (_songKey(controller.current) != requestedKey) {
          final audio = (widget.song['audio_url'] ?? widget.song['stream_url'] ?? '').toString();
          if (audio.isNotEmpty) {
            await controller.playSong(widget.song, forceRestart: true);
          }
        }
        // Transfer playback ownership to Media3; never leave both engines active.
        await controller.pause();
      }

      var songs = controller.queue
          .map((item) => Map<String, dynamic>.from(item))
          .toList(growable: false);
      var index = songs.indexWhere((item) => _songKey(item) == requestedKey);
      if (songs.isEmpty || index < 0) {
        songs = [Map<String, dynamic>.from(widget.song)];
        index = 0;
      }
      if (!mounted) return;
      setState(() {
        _queue = songs;
        _startIndex = index;
      });
    } catch (error) {
      debugPrint('BitChord player bridge: $error');
      if (mounted) setState(() => _failed = true);
    }
  }

  Future<void> _onNativeCall(MethodCall call) async {
    switch (call.method) {
      case 'close':
        await _closePlayer();
        break;
      case 'requireLogin':
        if (mounted) Navigator.of(context).pushNamed('/auth');
        break;
      case 'openArtist':
        final artist = call.arguments?.toString() ?? '';
        if (mounted && artist.isNotEmpty) {
          Navigator.of(context).pushNamed('/artist', arguments: {'artist': artist});
        }
        break;
      case 'openQueue':
        await _showQueue();
        break;
      case 'openLyrics':
        // Lyrics are available inside the original BitChord surface.
        break;
    }
  }

  Future<void> _showQueue() async {
    final state = await ToriNativeBridge.playerState();
    final raw = state?['queue'];
    final queue = raw is List
        ? raw.whereType<Map>().map((item) => Map<String, dynamic>.from(item)).toList()
        : <Map<String, dynamic>>[];
    if (!mounted || queue.isEmpty) return;
    await showModalBottomSheet<void>(
      context: context,
      backgroundColor: const Color(0xFF17171B),
      isScrollControlled: true,
      builder: (sheetContext) => SafeArea(
        child: SizedBox(
          height: MediaQuery.of(sheetContext).size.height * .65,
          child: Column(
            children: [
              const Padding(
                padding: EdgeInsets.all(18),
                child: Text('Cola de reproducción', style: TextStyle(color: Colors.white, fontSize: 18, fontWeight: FontWeight.w600)),
              ),
              Expanded(
                child: ListView.builder(
                  itemCount: queue.length,
                  itemBuilder: (context, index) {
                    final item = queue[index];
                    return ListTile(
                      title: Text(item['title']?.toString() ?? 'Sin título', maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(color: Colors.white)),
                      subtitle: Text(item['artist']?.toString() ?? '', maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(color: Colors.white54)),
                      onTap: () async {
                        await ToriNativeBridge.playQueueItem(index);
                        if (sheetContext.mounted) Navigator.pop(sheetContext);
                      },
                    );
                  },
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _syncNativeState() async {
    final state = await ToriNativeBridge.playerState();
    final song = state?['song'];
    if (song is! Map) return;
    final queueValue = state?['queue'];
    final queue = queueValue is List
        ? queueValue.whereType<Map>().map((item) => Map<String, dynamic>.from(item)).toList()
        : <Map<String, dynamic>>[];
    PlayerController.instance.adoptNativeState(
      song: Map<String, dynamic>.from(song),
      queue: queue,
      queueIndex: (state?['queueIndex'] as num?)?.toInt() ?? 0,
      isPlaying: state?['isPlaying'] == true,
      position: Duration(milliseconds: (state?['positionMs'] as num?)?.toInt() ?? 0),
      duration: Duration(milliseconds: (state?['durationMs'] as num?)?.toInt() ?? 0),
      nativeRepeatMode: (state?['repeatMode'] as num?)?.toInt(),
      nativeShuffleEnabled: state?['shuffleEnabled'] as bool?,
    );
  }

  Future<void> _closePlayer() async {
    await _syncNativeState();
    if (mounted) Navigator.of(context).maybePop();
  }

  Future<bool> _onWillPop() async {
    await _syncNativeState();
    return true;
  }

  @override
  void dispose() {
    _channel.setMethodCallHandler(null);
    // Playback remains active in Media3 after this view is removed.
    unawaited(_syncNativeState());
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (_failed) {
      return Scaffold(
        backgroundColor: const Color(0xFF08080A),
        body: Center(
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            const Icon(Icons.error_outline, color: Colors.white70, size: 34),
            const SizedBox(height: 12),
            const Text('No se pudo abrir BitChord Player Host', style: TextStyle(color: Colors.white)),
            TextButton(onPressed: () => setState(() { _failed = false; _queue = null; }) , child: const Text('Reintentar')),
          ]),
        ),
      );
    }
    final queue = _queue;
    return WillPopScope(
      onWillPop: _onWillPop,
      child: Scaffold(
        backgroundColor: const Color(0xFF08080A),
        body: queue == null
            ? const Center(child: CircularProgressIndicator(color: Color(0xFFFC3C44)))
            : AndroidView(
                viewType: 'pokke/bit_chord_player',
                creationParams: {'queue': queue, 'startIndex': _startIndex},
                creationParamsCodec: const StandardMessageCodec(),
              ),
      ),
    );
  }
}
