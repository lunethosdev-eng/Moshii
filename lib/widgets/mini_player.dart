import 'package:flutter/material.dart';
import 'package:pokke/widgets/liquid_glass.dart';
import 'package:pokke/services/player_controller.dart';

/// Thin floating mini player above the bottom nav.
class MiniPlayer extends StatelessWidget {
  const MiniPlayer({super.key});

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: PlayerController.instance,
      builder: (context, _) {
        final pc = PlayerController.instance;
        if (pc.current == null) return const SizedBox.shrink();

        final s = pc.current!;
        final cover = s['cover_url'];

        return Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12),
          child: GestureDetector(
            onTap: () => Navigator.pushNamed(context, '/player', arguments: s),
            onVerticalDragUpdate: (d) {
              if (d.primaryDelta != null && d.primaryDelta! < -8) {
                Navigator.pushNamed(context, '/player', arguments: s);
              }
            },
            child: LiquidGlass(
              blur: 28,
              opacity: 0.14,
              borderRadius: BorderRadius.circular(14),
              borderWidth: 0.6,
              enableSpecular: true,
              child: SizedBox(
                height: 58,
                child: Row(
                  children: [
                    const SizedBox(width: 8),
                    ClipRRect(
                      borderRadius: BorderRadius.circular(8),
                      child: cover != null
                          ? Image.network(cover.toString(), width: 44, height: 44, fit: BoxFit.cover,
                              errorBuilder: (_, __, ___) => Container(width: 44, height: 44, color: Colors.white12))
                          : Container(width: 44, height: 44, color: Colors.white12),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(s['title'] ?? '', maxLines: 1, overflow: TextOverflow.ellipsis,
                            style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w600, fontSize: 14)),
                          Text(s['artist'] ?? '', maxLines: 1, overflow: TextOverflow.ellipsis,
                            style: TextStyle(color: Colors.white.withOpacity(0.55), fontSize: 12)),
                        ],
                      ),
                    ),
                    IconButton(
                      icon: Icon(pc.isPlaying ? Icons.pause_rounded : Icons.play_arrow_rounded,
                        color: Colors.white, size: 28),
                      onPressed: pc.toggle,
                    ),
                    IconButton(
                      icon: const Icon(Icons.skip_next_rounded, color: Colors.white70, size: 26),
                      onPressed: () {},
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
