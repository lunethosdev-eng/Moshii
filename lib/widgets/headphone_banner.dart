import 'package:flutter/material.dart';
import 'package:pokke/widgets/liquid_glass.dart';

/// Overlay when headphones / bluetooth audio connect.
class HeadphoneBanner extends StatefulWidget {
  final String deviceName;
  final VoidCallback onDone;
  const HeadphoneBanner({super.key, required this.deviceName, required this.onDone});

  @override
  State<HeadphoneBanner> createState() => _HeadphoneBannerState();
}

class _HeadphoneBannerState extends State<HeadphoneBanner> with SingleTickerProviderStateMixin {
  late AnimationController _c;

  @override
  void initState() {
    super.initState();
    _c = AnimationController(vsync: this, duration: const Duration(milliseconds: 2200))..forward();
    _c.addStatusListener((s) {
      if (s == AnimationStatus.completed) widget.onDone();
    });
  }

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _c,
      builder: (_, __) {
        final t = _c.value;
        final opacity = t < 0.15 ? t / 0.15 : (t > 0.85 ? (1 - t) / 0.15 : 1.0);
        final dy = (1 - Curves.easeOutBack.transform(t.clamp(0.0, 0.4) / 0.4)) * -40;
        return IgnorePointer(
          child: Opacity(
            opacity: opacity.clamp(0.0, 1.0),
            child: Transform.translate(
              offset: Offset(0, dy),
              child: Align(
                alignment: Alignment.topCenter,
                child: SafeArea(
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: LiquidGlass(
                      blur: 28,
                      opacity: 0.18,
                      borderRadius: BorderRadius.circular(18),
                      enableSpecular: true,
                      child: Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 14),
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            const Icon(Icons.headphones, color: Colors.white, size: 28),
                            const SizedBox(width: 12),
                            Column(
                              mainAxisSize: MainAxisSize.min,
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                const Text('Audífonos conectados',
                                    style: TextStyle(color: Colors.white, fontWeight: FontWeight.w700)),
                                Text(widget.deviceName,
                                    style: TextStyle(color: Colors.white.withOpacity(0.55), fontSize: 12)),
                              ],
                            ),
                          ],
                        ),
                      ),
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
