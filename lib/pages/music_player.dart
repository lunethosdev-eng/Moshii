import 'package:flutter/material.dart';
import 'package:share_plus/share_plus.dart';
import 'package:pokke/widgets/liquid_glass.dart';
import 'package:pokke/widgets/karaoke_lyrics.dart';
import 'package:pokke/services/player_controller.dart';
import 'package:pokke/services/lyrics_service.dart';

class MusicPlayer extends StatefulWidget {
  MusicPlayer({super.key});

  @override
  State<MusicPlayer> createState() => _MusicPlayerState();
}

class _MusicPlayerState extends State<MusicPlayer> {
  bool _lyricsMode = false;
  bool _liked = false;
  bool _following = false;
  Map<String, dynamic>? _song;
  String? _lyrics;
  bool _loadingLyrics = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final args = ModalRoute.of(context)?.settings.arguments;
    if (args is Map<String, dynamic>) {
      _song = args;
      final pc = PlayerController.instance;
      final same = pc.current != null &&
          (pc.current!['id'] ?? pc.current!['title']) == (args['id'] ?? args['title']);
      if (same) {
        // Don't restart — just attach
        pc.attachSong(args);
      } else {
        pc.playSong(args);
      }
      _loadLyrics();
    }
  }

  Future<void> _loadLyrics() async {
    if (_song == null) return;
    final local = _song!['lyrics_text']?.toString();
    if (local != null && local.isNotEmpty) {
      setState(() => _lyrics = local);
      return;
    }
    setState(() => _loadingLyrics = true);
    final text = await LyricsService.fetch(
      title: _song!['title']?.toString() ?? '',
      artist: _song!['artist']?.toString() ?? '',
      album: _song!['album']?.toString(),
      durationSeconds: _song!['duration_seconds'] as int?,
    );
    if (mounted) {
      setState(() {
        _lyrics = text;
        _loadingLyrics = false;
      });
    }
  }

  String _fmt(Duration d) {
    final m = d.inMinutes.remainder(60).toString();
    final s = d.inSeconds.remainder(60).toString().padLeft(2, '0');
    return '$m:$s';
  }

  void _share() {
    final title = _song?['title'] ?? '';
    final artist = _song?['artist'] ?? '';
    final id = _song?['id'] ?? '';
    // App deep link — NOT supabase storage URL
    Share.share('Escucha "$title" de $artist en pokke\nhttps://pokke.app/song/$id');
  }

  Widget _liquidSlider(PlayerController pc, Duration dur, double progress) {
    return SliderTheme(
      data: SliderTheme.of(context).copyWith(
        trackHeight: 5,
        thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 8),
        overlayShape: const RoundSliderOverlayShape(overlayRadius: 16),
        activeTrackColor: Colors.white,
        inactiveTrackColor: Colors.white24,
        thumbColor: Colors.white,
        overlayColor: Colors.white24,
        // Track looks glassy via parent blur bg
      ),
      child: Slider(
        value: progress,
        onChanged: (v) {
          pc.seek(Duration(milliseconds: (v * dur.inMilliseconds).round()));
        },
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final pc = PlayerController.instance;
    final title = _song?['title'] ?? pc.current?['title'] ?? 'Unknown';
    final artist = _song?['artist'] ?? pc.current?['artist'] ?? 'Unknown';
    final cover = _song?['cover_url'] ?? _song?['animated_cover_url'] ?? pc.current?['cover_url'];

    return AnimatedBuilder(
      animation: pc,
      builder: (context, _) {
        final pos = pc.position;
        final dur = pc.duration.inMilliseconds > 0 ? pc.duration : const Duration(seconds: 1);
        final progress = (pos.inMilliseconds / dur.inMilliseconds).clamp(0.0, 1.0);

        // ========== FULLSCREEN LYRICS MODE ==========
        if (_lyricsMode) {
          return Scaffold(
            backgroundColor: Colors.black,
            body: Stack(
              fit: StackFit.expand,
              children: [
                if (cover != null)
                  Image.network(cover.toString(), fit: BoxFit.cover,
                      errorBuilder: (_, __, ___) => const SizedBox()),
                Container(color: Colors.black.withOpacity(0.72)),
                SafeArea(
                  child: Column(
                    children: [
                      Padding(
                        padding: const EdgeInsets.fromLTRB(8, 4, 8, 0),
                        child: Row(
                          children: [
                            IconButton(
                              onPressed: () => setState(() => _lyricsMode = false),
                              icon: const Icon(Icons.close, color: Colors.white, size: 28),
                            ),
                            Expanded(
                              child: Column(
                                children: [
                                  Text(title, maxLines: 1, overflow: TextOverflow.ellipsis,
                                      style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w700)),
                                  Text(artist, style: TextStyle(color: Colors.white.withOpacity(0.55), fontSize: 13)),
                                ],
                              ),
                            ),
                            const SizedBox(width: 48),
                          ],
                        ),
                      ),
                      Expanded(
                        child: _loadingLyrics
                            ? const Center(child: CircularProgressIndicator(color: Colors.white54))
                            : KaraokeLyrics(
                                lrcText: _lyrics ?? 'No lyrics found',
                                position: pos,
                              ),
                      ),
                      Padding(
                        padding: const EdgeInsets.fromLTRB(20, 0, 20, 4),
                        child: _liquidSlider(pc, dur, progress),
                      ),
                      Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 24),
                        child: Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Text(_fmt(pos), style: TextStyle(fontSize: 12, color: Colors.white.withOpacity(0.5))),
                            Text(_fmt(dur), style: TextStyle(fontSize: 12, color: Colors.white.withOpacity(0.5))),
                          ],
                        ),
                      ),
                      Padding(
                        padding: const EdgeInsets.only(bottom: 20, top: 4),
                        child: Row(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            IconButton(
                              iconSize: 36,
                              onPressed: () => pc.seek(Duration.zero),
                              icon: const Icon(Icons.skip_previous_rounded, color: Colors.white),
                            ),
                            const SizedBox(width: 16),
                            GestureDetector(
                              onTap: pc.toggle,
                              child: Container(
                                width: 64, height: 64,
                                decoration: const BoxDecoration(color: Colors.white, shape: BoxShape.circle),
                                child: Icon(
                                  pc.isPlaying ? Icons.pause_rounded : Icons.play_arrow_rounded,
                                  size: 38, color: Colors.black,
                                ),
                              ),
                            ),
                            const SizedBox(width: 16),
                            IconButton(
                              iconSize: 36,
                              onPressed: () {},
                              icon: const Icon(Icons.skip_next_rounded, color: Colors.white),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          );
        }

        // ========== NORMAL FULL PLAYER ==========
        return GestureDetector(
          onVerticalDragUpdate: (d) {
            if (d.primaryDelta != null && d.primaryDelta! > 12) {
              Navigator.of(context).maybePop();
            }
          },
          child: Scaffold(
            backgroundColor: const Color(0xFF0A0A0C),
            body: Stack(
              fit: StackFit.expand,
              children: [
                if (cover != null)
                  Image.network(cover.toString(), fit: BoxFit.cover,
                      errorBuilder: (_, __, ___) => const SizedBox()),
                Container(
                  decoration: BoxDecoration(
                    gradient: LinearGradient(
                      begin: Alignment.topCenter,
                      end: Alignment.bottomCenter,
                      colors: [
                        Colors.black.withOpacity(0.35),
                        Colors.black.withOpacity(0.8),
                        const Color(0xFF0A0A0C),
                      ],
                    ),
                  ),
                ),
                SafeArea(
                  child: Column(
                    children: [
                      const SizedBox(height: 6),
                      Container(
                        width: 36, height: 4,
                        decoration: BoxDecoration(color: Colors.white38, borderRadius: BorderRadius.circular(2)),
                      ),
                      // Top bar: close + 3 action buttons (share, etc.)
                      Padding(
                        padding: const EdgeInsets.fromLTRB(4, 4, 4, 0),
                        child: Row(
                          children: [
                            IconButton(
                              onPressed: () => Navigator.maybePop(context),
                              icon: const Icon(Icons.keyboard_arrow_down_rounded, color: Colors.white, size: 32),
                            ),
                            const Spacer(),
                            IconButton(
                              onPressed: _share,
                              icon: const Icon(Icons.ios_share_rounded, color: Colors.white70),
                              tooltip: 'Share',
                            ),
                            IconButton(
                              onPressed: () {},
                              icon: const Icon(Icons.cast, color: Colors.white70),
                            ),
                            IconButton(
                              onPressed: () {},
                              icon: const Icon(Icons.more_horiz, color: Colors.white70),
                            ),
                          ],
                        ),
                      ),
                      Expanded(
                        child: Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 36, vertical: 8),
                          child: AspectRatio(
                            aspectRatio: 1,
                            child: ClipRRect(
                              borderRadius: BorderRadius.circular(16),
                              child: cover != null
                                  ? Image.network(cover.toString(), fit: BoxFit.cover,
                                      errorBuilder: (_, __, ___) => Container(
                                          color: Colors.white12,
                                          child: const Icon(Icons.music_note, size: 72, color: Colors.white24)))
                                  : Container(
                                      color: Colors.white12,
                                      child: const Icon(Icons.music_note, size: 72, color: Colors.white24)),
                            ),
                          ),
                        ),
                      ),
                      Padding(
                        padding: const EdgeInsets.fromLTRB(28, 8, 28, 0),
                        child: Row(
                          children: [
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(title, maxLines: 1, overflow: TextOverflow.ellipsis,
                                      style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w700, color: Colors.white)),
                                  const SizedBox(height: 3),
                                  Text(artist, style: TextStyle(fontSize: 15, color: Colors.white.withOpacity(0.65))),
                                ],
                              ),
                            ),
                            IconButton(
                              onPressed: () => setState(() => _liked = !_liked),
                              icon: Icon(_liked ? Icons.favorite : Icons.favorite_border,
                                  color: _liked ? const Color(0xfffc3c44) : Colors.white70),
                            ),
                          ],
                        ),
                      ),
                      // Progress — Liquid Glass directly on the bar area
                      Padding(
                        padding: const EdgeInsets.fromLTRB(20, 12, 20, 0),
                        child: LiquidGlass(
                          blur: 14,
                          opacity: 0.07,
                          borderRadius: BorderRadius.circular(24),
                          borderWidth: 0.5,
                          child: Padding(
                            padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 0),
                            child: _liquidSlider(pc, dur, progress),
                          ),
                        ),
                      ),
                      Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 28, vertical: 2),
                        child: Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Text(_fmt(pos), style: TextStyle(fontSize: 12, color: Colors.white.withOpacity(0.5))),
                            Text(_fmt(dur), style: TextStyle(fontSize: 12, color: Colors.white.withOpacity(0.5))),
                          ],
                        ),
                      ),
                      Padding(
                        padding: const EdgeInsets.symmetric(vertical: 6),
                        child: Row(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            IconButton(
                              iconSize: 38,
                              onPressed: () => pc.seek(Duration.zero),
                              icon: const Icon(Icons.skip_previous_rounded, color: Colors.white),
                            ),
                            const SizedBox(width: 18),
                            GestureDetector(
                              onTap: pc.toggle,
                              child: Container(
                                width: 70, height: 70,
                                decoration: const BoxDecoration(color: Colors.white, shape: BoxShape.circle),
                                child: Icon(
                                  pc.isPlaying ? Icons.pause_rounded : Icons.play_arrow_rounded,
                                  size: 42, color: Colors.black,
                                ),
                              ),
                            ),
                            const SizedBox(width: 18),
                            IconButton(
                              iconSize: 38,
                              onPressed: () {},
                              icon: const Icon(Icons.skip_next_rounded, color: Colors.white),
                            ),
                          ],
                        ),
                      ),
                      // Artist + Follow
                      Padding(
                        padding: const EdgeInsets.fromLTRB(24, 4, 24, 0),
                        child: LiquidGlass(
                          blur: 16,
                          opacity: 0.08,
                          borderRadius: BorderRadius.circular(16),
                          child: Padding(
                            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
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
                      Padding(
                        padding: const EdgeInsets.fromLTRB(28, 14, 28, 16),
                        child: Row(
                          mainAxisAlignment: MainAxisAlignment.spaceAround,
                          children: [
                            IconButton(
                              onPressed: () => setState(() => _lyricsMode = true),
                              icon: const Icon(Icons.lyrics_outlined, color: Colors.white70, size: 26),
                            ),
                            IconButton(
                              onPressed: () {},
                              icon: const Icon(Icons.airplay_rounded, color: Colors.white70, size: 26),
                            ),
                            IconButton(
                              onPressed: () {},
                              icon: const Icon(Icons.queue_music_rounded, color: Colors.white70, size: 26),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        );
      },
    );
  }
}
