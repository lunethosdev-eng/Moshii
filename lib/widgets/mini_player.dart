import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:pokke/services/settings_service.dart';
import 'package:pokke/widgets/liquid_glass.dart';
import 'package:pokke/services/player_controller.dart';
import 'package:pokke/pages/music_player.dart';

/// Thin floating mini player above the bottom nav.
class MiniPlayer extends StatefulWidget {
  final bool compact;

  const MiniPlayer({super.key, this.compact = false});

  @override
  State<MiniPlayer> createState() => _MiniPlayerState();
}

class _MiniPlayerState extends State<MiniPlayer> {
  double _dragDx = 0;

  void _openFull(BuildContext context, Map<String, dynamic> song) {
    Navigator.of(context).push(
      PageRouteBuilder<void>(
        opaque: false,
        barrierColor: Colors.transparent,
        transitionDuration: const Duration(milliseconds: 380),
        reverseTransitionDuration: const Duration(milliseconds: 320),
        pageBuilder: (_, __, ___) => MusicPlayer(),
        transitionsBuilder: (_, animation, __, child) {
          final curved = CurvedAnimation(
            parent: animation,
            curve: Curves.easeOutCubic,
            reverseCurve: Curves.easeInCubic,
          );
          return SlideTransition(
            position: Tween<Offset>(
              begin: const Offset(0, 1),
              end: Offset.zero,
            ).animate(curved),
            child: FadeTransition(
              opacity: Tween<double>(begin: 0.0, end: 1.0).animate(
                CurvedAnimation(
                  parent: animation,
                  curve: const Interval(0.0, 0.65),
                ),
              ),
              child: child,
            ),
          );
        },
        settings: RouteSettings(name: '/player', arguments: song),
      ),
    );
  }

  void _onHorizontalEnd(DragEndDetails d) {
    final v = d.primaryVelocity ?? 0;
    final pc = PlayerController.instance;
    // Swipe left (negative) → next; swipe right (positive) → previous
    if (v < -400 || _dragDx < -48) {
      HapticFeedback.selectionClick();
      pc.playNextInQueue();
    } else if (v > 400 || _dragDx > 48) {
      HapticFeedback.selectionClick();
      pc.playPreviousInQueue();
    }
    setState(() => _dragDx = 0);
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: PlayerController.instance,
      builder: (context, _) {
        final pc = PlayerController.instance;
        if (pc.current == null) return const SizedBox.shrink();
        final settings = SettingsService.instance;
        if (!settings.showMiniPlayer ||
            !settings.flag('ui_mini_player', def: true)) {
          return const SizedBox.shrink();
        }

        final s = pc.current!;
        final cover = s['cover_url'];
        final compact = widget.compact;
        final h = compact ? 50.0 : 58.0;
        final coverSize = compact ? 38.0 : 44.0;

        return AnimatedContainer(
          duration: const Duration(milliseconds: 280),
          curve: Curves.easeOutCubic,
          padding: EdgeInsets.symmetric(horizontal: compact ? 20 : 14),
          child: GestureDetector(
            onTap: () => _openFull(context, s),
            onVerticalDragUpdate: (d) {
              if (d.primaryDelta != null && d.primaryDelta! < -10) {
                _openFull(context, s);
              }
            },
            onHorizontalDragUpdate: (d) {
              setState(() {
                _dragDx = (_dragDx + (d.primaryDelta ?? 0)).clamp(-80.0, 80.0);
              });
            },
            onHorizontalDragEnd: _onHorizontalEnd,
            onHorizontalDragCancel: () => setState(() => _dragDx = 0),
            child: Transform.translate(
              offset: Offset(_dragDx * 0.35, 0),
              child: Opacity(
                opacity: (1.0 - _dragDx.abs() / 160).clamp(0.55, 1.0),
                child: LiquidGlass(
                  blur: 28,
                  opacity: 0.14,
                  borderRadius: BorderRadius.circular(compact ? 14 : 16),
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
                                    color: Colors.white12,
                                  ),
                                )
                              : Container(
                                  width: coverSize,
                                  height: coverSize,
                                  color: Colors.white12,
                                ),
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
            ),
          ),
        );
      },
    );
  }
}
