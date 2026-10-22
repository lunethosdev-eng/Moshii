import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'dart:math' as math;
import 'package:pokke/pages/browse_page.dart';
import 'package:pokke/pages/home_page.dart';
import 'package:pokke/pages/library_page.dart';
import 'package:pokke/pages/radio.dart';
import 'package:pokke/pages/search_page.dart';
import 'package:pokke/widgets/liquid_glass.dart';
import 'package:pokke/widgets/mini_player.dart';
import 'package:pokke/widgets/headphone_banner.dart';
import 'package:pokke/services/headphone_service.dart';

class FirstPage extends StatefulWidget {
  FirstPage({super.key});

  @override
  State<FirstPage> createState() => _FirstPageState();
}

class _FirstPageState extends State<FirstPage> with TickerProviderStateMixin {
  int _selectedIndex = 0;
  bool _isDragging = false;
  int? _hoverIndex;
  double _dragX = 0;
  double _velocity = 0; // >0 = moving right, <0 = left
  double _prevX = 0;
  String? _headphoneDevice;

  late AnimationController _bubbleCtrl;
  late Animation<double> _bubbleScale;

  final List<Widget> _pages = [
    HomePage(),
    BrowsePage(),
    RadioPage(),
    LibraryPage(),
    SearchPage(),
  ];

  final List<IconData> _icons = [
    Icons.home_filled,
    Icons.grid_view_rounded,
    Icons.sensors,
    Icons.subscriptions_rounded,
    Icons.search,
  ];

  final List<String> _labels = ['Home', 'Browse', 'Radio', 'Library', 'Search'];

  @override
  void initState() {
    super.initState();
    _bubbleCtrl = AnimationController(vsync: this, duration: const Duration(milliseconds: 220));
    _bubbleScale = CurvedAnimation(parent: _bubbleCtrl, curve: Curves.easeOutBack);
    HeadphoneService.instance.start();
    HeadphoneService.instance.onConnected.listen((name) {
      if (mounted) setState(() => _headphoneDevice = name);
    });
  }

  @override
  void dispose() {
    _bubbleCtrl.dispose();
    super.dispose();
  }

  void _onLongPressStart(LongPressStartDetails d, BoxConstraints c) {
    HapticFeedback.mediumImpact();
    setState(() {
      _isDragging = true;
      _dragX = d.localPosition.dx;
      _prevX = d.localPosition.dx;
      _velocity = 0;
      _hoverIndex = _indexFromX(d.localPosition.dx, c.maxWidth);
    });
    _bubbleCtrl.forward(from: 0);
  }

  void _onLongPressMove(LongPressMoveUpdateDetails d, BoxConstraints c) {
    final x = d.localPosition.dx.clamp(0.0, c.maxWidth);
    final idx = _indexFromX(x, c.maxWidth);
    if (idx != _hoverIndex) HapticFeedback.selectionClick();
    setState(() {
      _velocity = (x - _prevX).clamp(-22.0, 22.0);
      _prevX = x;
      _dragX = x;
      _hoverIndex = idx;
    });
  }

  void _onLongPressEnd(LongPressEndDetails d) {
    if (_hoverIndex != null && _hoverIndex != _selectedIndex) {
      setState(() => _selectedIndex = _hoverIndex!);
    }
    _bubbleCtrl.reverse().then((_) {
      if (mounted) {
        setState(() {
          _isDragging = false;
          _hoverIndex = null;
          _velocity = 0;
        });
      }
    });
  }

  int _indexFromX(double x, double width) {
    final itemW = width / 5;
    return (x / itemW).floor().clamp(0, 4);
  }

  @override
  Widget build(BuildContext context) {
    return Stack(
      children: [
        Scaffold(
      extendBody: true,
      body: Stack(
        children: [
          _pages[_selectedIndex],
          // Mini player above nav
          const Positioned(
            left: 0,
            right: 0,
            bottom: 90,
            child: MiniPlayer(),
          ),
        ],
      ),
      bottomNavigationBar: Padding(
        padding: const EdgeInsets.fromLTRB(12, 0, 12, 16),
        child: LayoutBuilder(
          builder: (context, constraints) {
            final barH = 64.0;
            return GestureDetector(
              onLongPressStart: (d) => _onLongPressStart(d, constraints),
              onLongPressMoveUpdate: (d) => _onLongPressMove(d, constraints),
              onLongPressEnd: _onLongPressEnd,
              child: SizedBox(
                height: barH,
                child: Stack(
                  clipBehavior: Clip.none,
                  children: [
                    // Glass bar
                    LiquidGlass(
                      blur: 34,
                      opacity: 0.14,
                      borderRadius: BorderRadius.circular(32),
                      borderWidth: 0.85,
                      enableSpecular: true,
                      elevation: 10,
                      child: SizedBox(
                        height: barH,
                        child: Row(
                          children: List.generate(5, (i) {
                            final selected = i == _selectedIndex;
                            final hovering = _isDragging && i == _hoverIndex;
                            final active = selected || hovering;
                            return Expanded(
                              child: GestureDetector(
                                onTap: () {
                                  HapticFeedback.lightImpact();
                                  setState(() => _selectedIndex = i);
                                },
                                behavior: HitTestBehavior.opaque,
                                child: Column(
                                  mainAxisAlignment: MainAxisAlignment.center,
                                  children: [
                                    Icon(
                                      _icons[i],
                                      size: hovering ? 26 : 24,
                                      color: active
                                          ? const Color(0xfffc3c44)
                                          : Colors.white.withOpacity(0.88),
                                    ),
                                    const SizedBox(height: 2),
                                    Text(
                                      _labels[i],
                                      style: TextStyle(
                                        fontSize: 10,
                                        fontWeight: active ? FontWeight.w700 : FontWeight.w500,
                                        color: active
                                            ? const Color(0xfffc3c44)
                                            : Colors.white.withOpacity(0.72),
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                            );
                          }),
                        ),
                      ),
                    ),

                    // Liquid bubble INSIDE the bar (not above)
                    if (_isDragging && _hoverIndex != null)
                      Positioned(
                        left: (_dragX - 30).clamp(4.0, constraints.maxWidth - 64),
                        top: 6,
                        child: ScaleTransition(
                          scale: _bubbleScale,
                          child: CustomPaint(
                            size: const Size(60, 52),
                            painter: _LiquidBubblePainter(
                              velocity: _velocity,
                            ),
                            child: SizedBox(
                              width: 60,
                              height: 52,
                              child: Center(
                                child: Icon(
                                  _icons[_hoverIndex!],
                                  size: 26,
                                  color: const Color(0xfffc3c44),
                                ),
                              ),
                            ),
                          ),
                        ),
                      ),
                  ],
                ),
              ),
            );
          },
        ),
      ),
        ),
        if (_headphoneDevice != null)
          HeadphoneBanner(
            deviceName: _headphoneDevice!,
            onDone: () {
              if (mounted) setState(() => _headphoneDevice = null);
            },
          ),
      ],
    );
  }
}

/// Bubble that stretches in the direction of movement (liquid inertia).
/// velocity > 0 (moving right) → mass lags left, bulge on left
/// velocity < 0 (moving left)  → mass lags right, bulge on right
class _LiquidBubblePainter extends CustomPainter {
  final double velocity;

  _LiquidBubblePainter({required this.velocity});

  @override
  void paint(Canvas canvas, Size size) {
    final cx = size.width / 2;
    final cy = size.height / 2;
    // More oval / egg-like (less perfectly round)
    final baseRx = size.width * 0.38;
    final baseRy = size.height * 0.40;

    // Deform even on slow moves — floor stretch so micro velocity still shapes
    final speed = velocity.abs();
    final stretch = (0.14 + (speed / 18.0) * 0.86).clamp(0.14, 1.0);
    final dir = velocity >= 0 ? -1.0 : 1.0;

    final path = Path();
    const steps = 56;
    for (int i = 0; i <= steps; i++) {
      final t = i / steps * math.pi * 2;
      // Slight egg: narrower near top
      final egg = 1.0 + 0.09 * math.sin(t - math.pi / 2);
      final rx = baseRx * egg * (1 + stretch * 0.42);
      final ry = baseRy * egg * (1 - stretch * 0.22);
      final lag = math.cos(t) * dir * stretch * baseRx * 0.38;
      final wave = math.sin(t * 2.2) * stretch * 1.6;
      final x = cx + rx * math.cos(t) + lag;
      final y = cy + ry * math.sin(t) + wave * 0.35;
      if (i == 0) {
        path.moveTo(x, y);
      } else {
        path.lineTo(x, y);
      }
    }
    path.close();

    final rect = Offset.zero & size;

    canvas.drawPath(
      path.shift(const Offset(0, 2)),
      Paint()
        ..color = Colors.black.withOpacity(0.22)
        ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 5),
    );

    // Cleaner fill — softer gradient, no red tint
    final fill = Paint()
      ..shader = LinearGradient(
        begin: Alignment.topCenter,
        end: Alignment.bottomCenter,
        colors: [
          Colors.white.withOpacity(0.28),
          Colors.white.withOpacity(0.14),
        ],
      ).createShader(rect);
    canvas.drawPath(path, fill);

    canvas.drawPath(
      path,
      Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = 1.0
        ..color = Colors.white.withOpacity(0.48),
    );

    canvas.save();
    canvas.clipPath(path);
    canvas.drawRect(
      Rect.fromLTWH(0, 0, size.width, size.height * 0.38),
      Paint()
        ..shader = LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [
            Colors.white.withOpacity(0.42),
            Colors.white.withOpacity(0.0),
          ],
        ).createShader(rect),
    );
    canvas.restore();
  }

  @override
  bool shouldRepaint(covariant _LiquidBubblePainter old) => old.velocity != velocity;
}
