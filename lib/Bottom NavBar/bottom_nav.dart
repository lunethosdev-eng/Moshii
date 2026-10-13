import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'dart:math' as math;
import 'package:pokke/pages/browse_page.dart';
import 'package:pokke/pages/home_page.dart';
import 'package:pokke/pages/library_page.dart';
import 'package:pokke/pages/radio.dart';
import 'package:pokke/pages/search_page.dart';
import 'package:pokke/widgets/liquid_glass.dart';

class FirstPage extends StatefulWidget {
  FirstPage({super.key});

  @override
  State<FirstPage> createState() => _FirstPageState();
}

class _FirstPageState extends State<FirstPage> with TickerProviderStateMixin {
  int _selectedIndex = 0;
  bool _isDragging = false;
  int? _hoverIndex;
  Offset _bubblePos = Offset.zero;
  double _stretch = 0;

  late AnimationController _bubbleCtrl;
  late AnimationController _morphCtrl;
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
    _bubbleCtrl = AnimationController(vsync: this, duration: const Duration(milliseconds: 280));
    _morphCtrl = AnimationController(vsync: this, duration: const Duration(milliseconds: 400));
    _bubbleScale = CurvedAnimation(parent: _bubbleCtrl, curve: Curves.elasticOut);
  }

  @override
  void dispose() {
    _bubbleCtrl.dispose();
    _morphCtrl.dispose();
    super.dispose();
  }

  void _onLongPressStart(LongPressStartDetails d, BoxConstraints c) {
    HapticFeedback.mediumImpact();
    setState(() {
      _isDragging = true;
      _bubblePos = d.localPosition;
      _hoverIndex = _indexFromX(d.localPosition.dx, c.maxWidth);
      _stretch = 0;
    });
    _bubbleCtrl.forward(from: 0);
    _morphCtrl.repeat(reverse: true);
  }

  void _onLongPressMove(LongPressMoveUpdateDetails d, BoxConstraints c) {
    final idx = _indexFromX(d.localPosition.dx, c.maxWidth);
    if (idx != _hoverIndex) HapticFeedback.selectionClick();
    // Calculate stretch based on movement speed
    final dx = (d.localPosition.dx - _bubblePos.dx).abs();
    setState(() {
      _bubblePos = d.localPosition;
      _hoverIndex = idx;
      _stretch = (dx / 40).clamp(0.0, 0.35);
    });
  }

  void _onLongPressEnd(LongPressEndDetails d) {
    if (_hoverIndex != null && _hoverIndex != _selectedIndex) {
      setState(() => _selectedIndex = _hoverIndex!);
    }
    _morphCtrl.stop();
    _bubbleCtrl.reverse().then((_) {
      if (mounted) {
        setState(() {
          _isDragging = false;
          _hoverIndex = null;
          _stretch = 0;
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
    return Scaffold(
      extendBody: true,
      body: _pages[_selectedIndex],
      bottomNavigationBar: Padding(
        padding: const EdgeInsets.fromLTRB(14, 0, 14, 18),
        child: LayoutBuilder(
          builder: (context, constraints) {
            return GestureDetector(
              onLongPressStart: (d) => _onLongPressStart(d, constraints),
              onLongPressMoveUpdate: (d) => _onLongPressMove(d, constraints),
              onLongPressEnd: _onLongPressEnd,
              child: Stack(
                clipBehavior: Clip.none,
                children: [
                  // Main bar
                  LiquidGlass(
                    blur: 34,
                    opacity: 0.13,
                    borderRadius: BorderRadius.circular(32),
                    borderWidth: 0.85,
                    enableSpecular: true,
                    elevation: 12,
                    child: SizedBox(
                      height: 66,
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
                                    size: hovering ? 28 : 25,
                                    color: active
                                        ? const Color(0xfffc3c44)
                                        : Colors.white.withOpacity(0.88),
                                  ),
                                  const SizedBox(height: 3),
                                  Text(
                                    _labels[i],
                                    style: TextStyle(
                                      fontSize: 10.5,
                                      fontWeight: active ? FontWeight.w700 : FontWeight.w500,
                                      // FIX: always light color so labels are visible
                                      color: active
                                          ? const Color(0xfffc3c44)
                                          : Colors.white.withOpacity(0.75),
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

                  // Morphing Liquid Bubble
                  if (_isDragging && _hoverIndex != null)
                    Positioned(
                      left: (_bubblePos.dx - 36).clamp(0.0, constraints.maxWidth - 72),
                      top: -22 - (_stretch * 8),
                      child: ScaleTransition(
                        scale: _bubbleScale,
                        child: AnimatedBuilder(
                          animation: _morphCtrl,
                          builder: (context, child) {
                            final wobble = math.sin(_morphCtrl.value * math.pi * 2) * 3;
                            final width = 72 + _stretch * 28;
                            final height = 72 - _stretch * 14;
                            return Transform.translate(
                              offset: Offset(wobble * 0.4, 0),
                              child: CustomPaint(
                                painter: _LiquidBubblePainter(
                                  stretch: _stretch,
                                  pulse: _morphCtrl.value,
                                ),
                                child: Container(
                                  width: width,
                                  height: height,
                                  alignment: Alignment.center,
                                  child: Icon(
                                    _icons[_hoverIndex!],
                                    size: 30,
                                    color: const Color(0xfffc3c44),
                                  ),
                                ),
                              ),
                            );
                          },
                        ),
                      ),
                    ),
                ],
              ),
            );
          },
        ),
      ),
    );
  }
}

/// Custom painter for a more organic, deformable liquid bubble
class _LiquidBubblePainter extends CustomPainter {
  final double stretch;
  final double pulse;

  _LiquidBubblePainter({required this.stretch, required this.pulse});

  @override
  void paint(Canvas canvas, Size size) {
    final rect = Offset.zero & size;
    final r = size.shortestSide / 2;

    // Organic path with slight deformation
    final path = Path();
    final cx = size.width / 2;
    final cy = size.height / 2;
    final rx = r * (1 + stretch * 0.25);
    final ry = r * (1 - stretch * 0.18);

    for (int i = 0; i <= 60; i++) {
      final t = i / 60 * math.pi * 2;
      final wave = math.sin(t * 3 + pulse * math.pi * 2) * (1.8 + stretch * 3);
      final x = cx + (rx + wave) * math.cos(t);
      final y = cy + (ry + wave * 0.6) * math.sin(t);
      if (i == 0) {
        path.moveTo(x, y);
      } else {
        path.lineTo(x, y);
      }
    }
    path.close();

    // Glass fill
    final fill = Paint()
      ..shader = LinearGradient(
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
        colors: [
          Colors.white.withOpacity(0.28),
          Colors.white.withOpacity(0.10),
          const Color(0xfffc3c44).withOpacity(0.08),
        ],
      ).createShader(rect);
    canvas.drawPath(path, fill);

    // Soft border
    final border = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.2
      ..color = Colors.white.withOpacity(0.45);
    canvas.drawPath(path, border);

    // Specular highlight
    final highlight = Paint()
      ..shader = LinearGradient(
        begin: Alignment.topCenter,
        end: Alignment.center,
        colors: [
          Colors.white.withOpacity(0.55),
          Colors.white.withOpacity(0.0),
        ],
      ).createShader(Rect.fromLTWH(0, 0, size.width, size.height * 0.45));
    canvas.save();
    canvas.clipPath(path);
    canvas.drawRect(Rect.fromLTWH(0, 0, size.width, size.height * 0.4), highlight);
    canvas.restore();
  }

  @override
  bool shouldRepaint(covariant _LiquidBubblePainter old) =>
      old.stretch != stretch || old.pulse != pulse;
}
