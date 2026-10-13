import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
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
    _bubbleCtrl = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 220),
    );
    _bubbleScale = CurvedAnimation(parent: _bubbleCtrl, curve: Curves.easeOutBack);
  }

  @override
  void dispose() {
    _bubbleCtrl.dispose();
    super.dispose();
  }

  void _onLongPressStart(LongPressStartDetails details, BoxConstraints constraints) {
    HapticFeedback.mediumImpact();
    setState(() {
      _isDragging = true;
      _bubblePos = details.localPosition;
      _hoverIndex = _indexFromX(details.localPosition.dx, constraints.maxWidth);
    });
    _bubbleCtrl.forward(from: 0);
  }

  void _onLongPressMove(LongPressMoveUpdateDetails details, BoxConstraints constraints) {
    final idx = _indexFromX(details.localPosition.dx, constraints.maxWidth);
    if (idx != _hoverIndex) {
      HapticFeedback.selectionClick();
    }
    setState(() {
      _bubblePos = details.localPosition;
      _hoverIndex = idx;
    });
  }

  void _onLongPressEnd(LongPressEndDetails details) {
    if (_hoverIndex != null && _hoverIndex != _selectedIndex) {
      setState(() => _selectedIndex = _hoverIndex!);
    }
    _bubbleCtrl.reverse().then((_) {
      if (mounted) {
        setState(() {
          _isDragging = false;
          _hoverIndex = null;
        });
      }
    });
  }

  int _indexFromX(double x, double width) {
    final itemWidth = width / 5;
    final idx = (x / itemWidth).floor().clamp(0, 4);
    return idx;
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
                  // Main Liquid Glass bar
                  LiquidGlass(
                    blur: 32,
                    opacity: 0.11,
                    borderRadius: BorderRadius.circular(30),
                    borderWidth: 0.9,
                    enableSpecular: true,
                    elevation: 10,
                    child: SizedBox(
                      height: 64,
                      child: Row(
                        children: List.generate(5, (i) {
                          final selected = i == _selectedIndex;
                          final hovering = _isDragging && i == _hoverIndex;
                          return Expanded(
                            child: GestureDetector(
                              onTap: () {
                                HapticFeedback.lightImpact();
                                setState(() => _selectedIndex = i);
                              },
                              behavior: HitTestBehavior.opaque,
                              child: AnimatedContainer(
                                duration: const Duration(milliseconds: 200),
                                curve: Curves.easeOutCubic,
                                child: Column(
                                  mainAxisAlignment: MainAxisAlignment.center,
                                  children: [
                                    Icon(
                                      _icons[i],
                                      size: hovering ? 28 : 26,
                                      color: selected || hovering
                                          ? const Color(0xfffc3c44)
                                          : Colors.white.withOpacity(0.75),
                                    ),
                                    const SizedBox(height: 3),
                                    AnimatedOpacity(
                                      opacity: selected || hovering ? 1 : 0,
                                      duration: const Duration(milliseconds: 180),
                                      child: Text(
                                        _labels[i],
                                        style: TextStyle(
                                          fontSize: 10,
                                          fontWeight: FontWeight.w600,
                                          color: selected || hovering
                                              ? const Color(0xfffc3c44)
                                              : Colors.white70,
                                        ),
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                            ),
                          );
                        }),
                      ),
                    ),
                  ),

                  // Floating Liquid Bubble that follows the finger
                  if (_isDragging && _hoverIndex != null)
                    Positioned(
                      left: (_bubblePos.dx - 32).clamp(0, constraints.maxWidth - 64),
                      top: -18,
                      child: ScaleTransition(
                        scale: _bubbleScale,
                        child: LiquidGlass(
                          blur: 20,
                          opacity: 0.22,
                          borderRadius: BorderRadius.circular(20),
                          borderWidth: 1.1,
                          enableSpecular: true,
                          elevation: 12,
                          child: Container(
                            width: 64,
                            height: 64,
                            alignment: Alignment.center,
                            child: Icon(
                              _icons[_hoverIndex!],
                              size: 30,
                              color: const Color(0xfffc3c44),
                            ),
                          ),
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
