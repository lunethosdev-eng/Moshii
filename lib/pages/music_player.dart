import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:pokke/widgets/liquid_glass.dart';
import 'package:pokke/widgets/karaoke_lyrics.dart';
import 'package:pokke/services/player_controller.dart';
import 'package:pokke/services/supabase_service.dart';

/// Full-screen player inspired by Apple Music (big cover / immersive lyrics mode).
class MusicPlayer extends StatefulWidget {
  MusicPlayer({super.key});

  @override
  State<MusicPlayer> createState() => _MusicPlayerState();
}

class _MusicPlayerState extends State<MusicPlayer> {
  bool _showLyrics = false;
  bool _liked = false;
  bool _following = false;
  Map<String, dynamic>? _song;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final args = ModalRoute.of(context)?.settings.arguments;
    if (args is Map<String, dynamic> && _song == null) {
      _song = args;
      PlayerController.instance.playSong(args);
    }
  }

  String _fmt(Duration d) {
    final m = d.inMinutes.remainder(60).toString().padLeft(1, '0');
    final s = d.inSeconds.remainder(60).toString().padLeft(2, '0');
    return '$m:$s';
  }

  @override
  Widget build(BuildContext context) {
    final pc = PlayerController.instance;
    final title = _song?['title'] ?? pc.current?['title'] ?? 'Unknown';
    final artist = _song?['artist'] ?? pc.current?['artist'] ?? 'Unknown';
    final cover = _song?['cover_url'] ?? _song?['animated_cover_url'] ?? pc.current?['cover_url'];
    final lyrics = _song?['lyrics_text']?.toString() ?? pc.current?['lyrics_text']?.toString() ?? '';

    return AnimatedBuilder(
      animation: pc,
      builder: (context, _) {
        final pos = pc.position;
        final dur = pc.duration.inMilliseconds > 0 ? pc.duration : const Duration(seconds: 1);
        final progress = (pos.inMilliseconds / dur.inMilliseconds).clamp(0.0, 1.0);

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
                // Blurred cover background
                if (cover != null)
                  Image.network(
                    cover.toString(),
                    fit: BoxFit.cover,
                    errorBuilder: (_, __, ___) => const SizedBox(),
                  ),
                Container(
                  decoration: BoxDecoration(
                    gradient: LinearGradient(
                      begin: Alignment.topCenter,
                      end: Alignment.bottomCenter,
                      colors: [
                        Colors.black.withOpacity(0.35),
                        Colors.black.withOpacity(0.75),
                        const Color(0xFF0A0A0C),
                      ],
                    ),
                  ),
                ),

                SafeArea(
                  child: Column(
                    children: [
                      // Handle + close
                      const SizedBox(height: 6),
                      Container(
                        width: 36,
                        height: 4,
                        decoration: BoxDecoration(
                          color: Colors.white38,
                          borderRadius: BorderRadius.circular(2),
                        ),
                      ),
                      Padding(
                        padding: const EdgeInsets.fromLTRB(8, 8, 8, 0),
                        child: Row(
                          children: [
                            IconButton(
                              onPressed: () => Navigator.maybePop(context),
                              icon: const Icon(Icons.keyboard_arrow_down_rounded, color: Colors.white, size: 32),
                            ),
                            const Spacer(),
                            IconButton(
                              onPressed: () {},
                              icon: const Icon(Icons.more_horiz, color: Colors.white70),
                            ),
                          ],
                        ),
                      ),

                      // Cover or Lyrics
                      Expanded(
                        flex: _showLyrics ? 4 : 5,
                        child: AnimatedSwitcher(
                          duration: const Duration(milliseconds: 320),
                          child: _showLyrics
                              ? Padding(
                                  key: const ValueKey('lyrics'),
                                  padding: const EdgeInsets.symmetric(horizontal: 20),
                                  child: LiquidGlass(
                                    blur: 24,
                                    opacity: 0.08,
                                    borderRadius: BorderRadius.circular(22),
                                    child: KaraokeLyrics(
                                      lrcText: lyrics.isNotEmpty
                                          ? lyrics
                                          : 'No lyrics available\nfor this track yet.',
                                      position: pos,
                                    ),
                                  ),
                                )
                              : Padding(
                                  key: const ValueKey('cover'),
                                  padding: const EdgeInsets.symmetric(horizontal: 36, vertical: 8),
                                  child: AspectRatio(
                                    aspectRatio: 1,
                                    child: ClipRRect(
                                      borderRadius: BorderRadius.circular(16),
                                      child: cover != null
                                          ? Image.network(
                                              cover.toString(),
                                              fit: BoxFit.cover,
                                              errorBuilder: (_, __, ___) => Container(
                                                color: Colors.white12,
                                                child: const Icon(Icons.music_note, size: 72, color: Colors.white24),
                                              ),
                                            )
                                          : Container(
                                              color: Colors.white12,
                                              child: const Icon(Icons.music_note, size: 72, color: Colors.white24),
                                            ),
                                    ),
                                  ),
                                ),
                        ),
                      ),

                      // Title row
                      Padding(
                        padding: const EdgeInsets.fromLTRB(28, 12, 28, 0),
                        child: Row(
                          children: [
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    title,
                                    maxLines: 1,
                                    overflow: TextOverflow.ellipsis,
                                    style: const TextStyle(
                                      fontSize: 20,
                                      fontWeight: FontWeight.w700,
                                      color: Colors.white,
                                    ),
                                  ),
                                  const SizedBox(height: 3),
                                  Text(
                                    artist,
                                    style: TextStyle(fontSize: 15, color: Colors.white.withOpacity(0.65)),
                                  ),
                                ],
                              ),
                            ),
                            IconButton(
                              onPressed: () => setState(() => _liked = !_liked),
                              icon: Icon(
                                _liked ? Icons.favorite : Icons.favorite_border,
                                color: _liked ? const Color(0xfffc3c44) : Colors.white70,
                              ),
                            ),
                          ],
                        ),
                      ),

                      // Liquid Glass slider
                      Padding(
                        padding: const EdgeInsets.fromLTRB(22, 14, 22, 0),
                        child: Column(
                          children: [
                            LiquidGlass(
                              blur: 12,
                              opacity: 0.06,
                              borderRadius: BorderRadius.circular(20),
                              borderWidth: 0.4,
                              child: Padding(
                                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                child: SliderTheme(
                                  data: SliderTheme.of(context).copyWith(
                                    trackHeight: 4,
                                    thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 7),
                                    overlayShape: const RoundSliderOverlayShape(overlayRadius: 14),
                                    activeTrackColor: Colors.white,
                                    inactiveTrackColor: Colors.white24,
                                    thumbColor: Colors.white,
                                    overlayColor: Colors.white24,
                                  ),
                                  child: Slider(
                                    value: progress,
                                    onChanged: (v) {
                                      pc.seek(Duration(milliseconds: (v * dur.inMilliseconds).round()));
                                    },
                                  ),
                                ),
                              ),
                            ),
                            Padding(
                              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                              child: Row(
                                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                children: [
                                  Text(_fmt(pos), style: TextStyle(fontSize: 12, color: Colors.white.withOpacity(0.5))),
                                  Text(_fmt(dur), style: TextStyle(fontSize: 12, color: Colors.white.withOpacity(0.5))),
                                ],
                              ),
                            ),
                          ],
                        ),
                      ),

                      // Controls
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
                              onTap: () => pc.togglePlay(),
                              child: Container(
                                width: 70,
                                height: 70,
                                decoration: const BoxDecoration(
                                  color: Colors.white,
                                  shape: BoxShape.circle,
                                ),
                                child: Icon(
                                  pc.isPlaying ? Icons.pause_rounded : Icons.play_arrow_rounded,
                                  size: 42,
                                  color: Colors.black,
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
                                  child: cover == null
                                      ? const Icon(Icons.person, color: Colors.white38)
                                      : null,
                                ),
                                const SizedBox(width: 12),
                                Expanded(
                                  child: Column(
                                    crossAxisAlignment: CrossAxisAlignment.start,
                                    children: [
                                      Text(artist,
                                          style: const TextStyle(
                                              color: Colors.white, fontWeight: FontWeight.w600)),
                                      Text('Artist',
                                          style: TextStyle(
                                              color: Colors.white.withOpacity(0.45), fontSize: 12)),
                                    ],
                                  ),
                                ),
                                TextButton(
                                  onPressed: () => setState(() => _following = !_following),
                                  style: TextButton.styleFrom(
                                    backgroundColor:
                                        _following ? Colors.white12 : const Color(0xfffc3c44),
                                    foregroundColor: Colors.white,
                                    padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 8),
                                    shape: RoundedRectangleBorder(
                                        borderRadius: BorderRadius.circular(20)),
                                  ),
                                  child: Text(
                                    _following ? 'Following' : 'Follow',
                                    style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 13),
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ),
                      ),

                      // Bottom actions: lyrics toggle
                      Padding(
                        padding: const EdgeInsets.fromLTRB(28, 12, 28, 16),
                        child: Row(
                          mainAxisAlignment: MainAxisAlignment.spaceAround,
                          children: [
                            _ActionBtn(
                              icon: Icons.lyrics_outlined,
                              active: _showLyrics,
                              onTap: () => setState(() => _showLyrics = !_showLyrics),
                            ),
                            _ActionBtn(icon: Icons.airplay_rounded, onTap: () {}),
                            _ActionBtn(icon: Icons.queue_music_rounded, onTap: () {}),
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

class _ActionBtn extends StatelessWidget {
  final IconData icon;
  final bool active;
  final VoidCallback onTap;
  const _ActionBtn({required this.icon, this.active = false, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Icon(icon, color: active ? const Color(0xfffc3c44) : Colors.white70, size: 26),
    );
  }
}
