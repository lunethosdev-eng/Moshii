import 'package:flutter/material.dart';
import 'package:pokke/services/settings_service.dart';
import 'package:pokke/widgets/liquid_glass.dart';
import 'package:pokke/services/player_controller.dart';
import 'package:pokke/pages/music_player.dart';

/// Thin floating mini player above the bottom nav.
class MiniPlayer extends StatelessWidget {
  final bool compact;

  const MiniPlayer({super.key, this.compact = false});

  void _openFull(BuildContext context, Map<String, dynamic> song) {
    Navigator.of(context).push(MusicPlayer.route(song));
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: PlayerController.instance,
      builder: (context, _) {
        final pc = PlayerController.instance;
        if (pc.current == null) return const SizedBox.shrink();
        final settings = SettingsService.instance;
        if (!settings.showMiniPlayer || !settings.flag('ui_mini_player', def: true)) {
          return const SizedBox.shrink();
        }

        final s = pc.current!;
        final cover = s['cover_url'];
        final h = compact ? 48.0 : 58.0;
        final coverSize = compact ? 36.0 : 44.0;

        return AnimatedContainer(
          duration: const Duration(milliseconds: 280),
          curve: Curves.easeOutCubic,
          padding: EdgeInsets.symmetric(horizontal: compact ? 16 : 12),
          child: GestureDetector(
            onTap: () => _openFull(context, s),
            onVerticalDragUpdate: (d) {
              if (d.primaryDelta != null && d.primaryDelta! < -8) {
                _openFull(context, s);
              }
            },
            child: LiquidGlass(
              blur: 28,
              opacity: 0.14,
              borderRadius: BorderRadius.circular(compact ? 12 : 14),
              borderWidth: 0.6,
              enableSpecular: true,
              child: SizedBox(
                height: h,
                child: Row(
                  children: [
                    const SizedBox(width: 8),
                    ClipRRect(
                      borderRadius: BorderRadius.circular(8),
                      child: cover != null
                          ? Image.network(
                              cover.toString(),
                              width: coverSize,
                              height: coverSize,
                              fit: BoxFit.cover,
                              errorBuilder: (_, __, ___) => Container(
                                  width: coverSize,
                                  height: coverSize,
                                  color: Colors.white12),
                            )
                          : Container(
                              width: coverSize,
                              height: coverSize,
                              color: Colors.white12),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            s['title'] ?? '',
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: TextStyle(
                              color: Colors.white,
                              fontWeight: FontWeight.w600,
                              fontSize: compact ? 13 : 14,
                            ),
                          ),
                          Text(
                            s['artist'] ?? '',
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: TextStyle(
                              color: Colors.white.withOpacity(0.55),
                              fontSize: compact ? 11 : 12,
                            ),
                          ),
                        ],
                      ),
                    ),
                    IconButton(
                      icon: Icon(
                        pc.isPlaying
                            ? Icons.pause_rounded
                            : Icons.play_arrow_rounded,
                        color: Colors.white,
                        size: compact ? 24 : 28,
                      ),
                      onPressed: pc.toggle,
                    ),
                    IconButton(
                      icon: Icon(
                        Icons.skip_next_rounded,
                        color: Colors.white70,
                        size: compact ? 22 : 26,
                      ),
                      onPressed: () => pc.playNextInQueue(),
                    ),
                    const SizedBox(width: 4),
                  ],
                ),
              ),
            ),
          ),
        );
      },
    );
  }
}
