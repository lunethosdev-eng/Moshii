import 'package:audioplayers/audioplayers.dart';
import 'package:flutter/material.dart';
import 'package:pokke/widgets/liquid_glass.dart';
import 'package:pokke/services/supabase_service.dart';

class MusicPlayer extends StatefulWidget {
  MusicPlayer({super.key});

  @override
  State<MusicPlayer> createState() => _MusicPlayerState();
}

class _MusicPlayerState extends State<MusicPlayer> {
  final AudioPlayer _player = AudioPlayer();
  bool _isPlaying = false;
  bool _liked = false;
  bool _following = false;
  Duration _duration = Duration.zero;
  Duration _position = Duration.zero;
  Map<String, dynamic>? _song;
  String? _lyrics;

  @override
  void initState() {
    super.initState();
    _player.onPlayerStateChanged.listen((s) {
      if (mounted) setState(() => _isPlaying = s == PlayerState.playing);
    });
    _player.onDurationChanged.listen((d) {
      if (mounted) setState(() => _duration = d);
    });
    _player.onPositionChanged.listen((p) {
      if (mounted) setState(() => _position = p);
    });
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final args = ModalRoute.of(context)?.settings.arguments;
    if (args is Map<String, dynamic> && _song == null) {
      _song = args;
      _loadAndPlay();
    }
  }

  Future<void> _loadAndPlay() async {
    final url = _song?['audio_url'] ?? _song?['stream_url'];
    if (url != null && url.toString().isNotEmpty) {
      try {
        await _player.play(UrlSource(url.toString()));
      } catch (_) {}
    }
    // lyrics if available
    _lyrics = _song?['lyrics_text'] ?? _song?['lyrics']?.toString();
  }

  Future<void> _togglePlay() async {
    if (_isPlaying) {
      await _player.pause();
    } else {
      await _player.resume();
    }
  }

  String _fmt(Duration d) {
    final m = d.inMinutes.remainder(60).toString().padLeft(2, '0');
    final s = d.inSeconds.remainder(60).toString().padLeft(2, '0');
    return '$m:$s';
  }

  @override
  void dispose() {
    _player.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final title = _song?['title'] ?? 'Unknown';
    final artist = _song?['artist'] ?? 'Unknown Artist';
    final cover = _song?['cover_url'] ?? _song?['image'];

    return GestureDetector(
      onVerticalDragUpdate: (d) {
        if (d.primaryDelta != null && d.primaryDelta! > 14) {
          Navigator.of(context).maybePop();
        }
      },
      child: Scaffold(
        backgroundColor: const Color(0xFF121214),
        body: SafeArea(
          child: Column(
            children: [
              // Drag handle
              const SizedBox(height: 8),
              Container(
                width: 40, height: 4,
                decoration: BoxDecoration(color: Colors.white24, borderRadius: BorderRadius.circular(2)),
              ),
              const SizedBox(height: 20),

              // Cover
              Expanded(
                flex: 5,
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 32),
                  child: AspectRatio(
                    aspectRatio: 1,
                    child: ClipRRect(
                      borderRadius: BorderRadius.circular(16),
                      child: cover != null
                          ? Image.network(cover.toString(), fit: BoxFit.cover,
                              errorBuilder: (_, __, ___) => Container(color: Colors.white12, child: const Icon(Icons.music_note, size: 64, color: Colors.white24)))
                          : Container(color: Colors.white12, child: const Icon(Icons.music_note, size: 64, color: Colors.white24)),
                    ),
                  ),
                ),
              ),

              // Title + actions
              Padding(
                padding: const EdgeInsets.fromLTRB(28, 20, 28, 0),
                child: Row(
                  children: [
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(title, maxLines: 1, overflow: TextOverflow.ellipsis,
                            style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w700, color: Colors.white)),
                          const SizedBox(height: 4),
                          Text(artist, style: TextStyle(fontSize: 15, color: Colors.white.withOpacity(0.6))),
                        ],
                      ),
                    ),
                    IconButton(
                      onPressed: () => setState(() => _liked = !_liked),
                      icon: Icon(_liked ? Icons.favorite : Icons.favorite_border,
                        color: _liked ? const Color(0xfffc3c44) : Colors.white70),
                    ),
                    IconButton(
                      onPressed: () {},
                      icon: const Icon(Icons.more_horiz, color: Colors.white70),
                    ),
                  ],
                ),
              ),

              // Progress
              Padding(
                padding: const EdgeInsets.fromLTRB(28, 16, 28, 0),
                child: Column(
                  children: [
                    SliderTheme(
                      data: SliderTheme.of(context).copyWith(
                        trackHeight: 3,
                        thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 6),
                        activeTrackColor: Colors.white,
                        inactiveTrackColor: Colors.white24,
                        thumbColor: Colors.white,
                      ),
                      child: Slider(
                        value: _duration.inMilliseconds > 0
                            ? _position.inMilliseconds.clamp(0, _duration.inMilliseconds).toDouble()
                            : 0,
                        max: _duration.inMilliseconds > 0 ? _duration.inMilliseconds.toDouble() : 1,
                        onChanged: (v) => _player.seek(Duration(milliseconds: v.toInt())),
                      ),
                    ),
                    Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 4),
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Text(_fmt(_position), style: TextStyle(fontSize: 12, color: Colors.white.withOpacity(0.5))),
                          Text(_fmt(_duration), style: TextStyle(fontSize: 12, color: Colors.white.withOpacity(0.5))),
                        ],
                      ),
                    ),
                  ],
                ),
              ),

              // Controls
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 12),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    IconButton(
                      iconSize: 36,
                      onPressed: () => _player.seek(Duration.zero),
                      icon: const Icon(Icons.skip_previous_rounded, color: Colors.white),
                    ),
                    const SizedBox(width: 20),
                    GestureDetector(
                      onTap: _togglePlay,
                      child: Container(
                        width: 68, height: 68,
                        decoration: const BoxDecoration(color: Colors.white, shape: BoxShape.circle),
                        child: Icon(
                          _isPlaying ? Icons.pause_rounded : Icons.play_arrow_rounded,
                          size: 40, color: Colors.black,
                        ),
                      ),
                    ),
                    const SizedBox(width: 20),
                    IconButton(
                      iconSize: 36,
                      onPressed: () {},
                      icon: const Icon(Icons.skip_next_rounded, color: Colors.white),
                    ),
                  ],
                ),
              ),

              // Artist row + Follow
              Padding(
                padding: const EdgeInsets.fromLTRB(24, 4, 24, 0),
                child: LiquidGlass(
                  blur: 16,
                  opacity: 0.08,
                  borderRadius: BorderRadius.circular(16),
                  child: Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                    child: Row(
                      children: [
                        CircleAvatar(
                          radius: 22,
                          backgroundColor: Colors.white12,
                          backgroundImage: cover != null ? NetworkImage(cover.toString()) : null,
                          child: cover == null ? const Icon(Icons.person, color: Colors.white38) : null,
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(artist, style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w600)),
                              Text('Artist', style: TextStyle(color: Colors.white.withOpacity(0.45), fontSize: 12)),
                            ],
                          ),
                        ),
                        TextButton(
                          onPressed: () => setState(() => _following = !_following),
                          style: TextButton.styleFrom(
                            backgroundColor: _following ? Colors.white12 : const Color(0xfffc3c44),
                            foregroundColor: Colors.white,
                            padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 8),
                            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
                          ),
                          child: Text(_following ? 'Following' : 'Follow',
                            style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 13)),
                        ),
                      ],
                    ),
                  ),
                ),
              ),

              // Lyrics
              Expanded(
                flex: 3,
                child: Container(
                  width: double.infinity,
                  margin: const EdgeInsets.fromLTRB(24, 16, 24, 16),
                  child: LiquidGlass(
                    blur: 18,
                    opacity: 0.07,
                    borderRadius: BorderRadius.circular(18),
                    child: Padding(
                      padding: const EdgeInsets.all(18),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const Text('Lyrics', style: TextStyle(color: Colors.white, fontWeight: FontWeight.w700, fontSize: 16)),
                          const SizedBox(height: 10),
                          Expanded(
                            child: SingleChildScrollView(
                              child: Text(
                                (_lyrics != null && _lyrics!.isNotEmpty)
                                    ? _lyrics!
                                    : 'No lyrics available for this track.',
                                style: TextStyle(
                                  color: Colors.white.withOpacity(0.7),
                                  fontSize: 15,
                                  height: 1.6,
                                ),
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
