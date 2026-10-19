import 'dart:async';
import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:pokke/services/supabase_service.dart';
import 'package:pokke/services/settings_service.dart';

/// Motion-graphics style welcome: real covers float while copy animates in.
class WelcomeScreen extends StatefulWidget {
  const WelcomeScreen({super.key});

  @override
  State<WelcomeScreen> createState() => _WelcomeScreenState();
}

class _WelcomeScreenState extends State<WelcomeScreen>
    with TickerProviderStateMixin {
  late final AnimationController _master;
  late final AnimationController _float;
  List<String> _covers = [];
  int _phase = 0; // 0 title, 1 subtitle, 2 feature, 3 CTA
  Timer? _phaseTimer;

  String t(String es, String en) =>
      SettingsService.instance.locale == 'en' ? en : es;

  @override
  void initState() {
    super.initState();
    _master = AnimationController(
        vsync: this, duration: const Duration(milliseconds: 1200))
      ..forward();
    _float = AnimationController(
        vsync: this, duration: const Duration(seconds: 12))
      ..repeat();
    _loadCovers();
    _phaseTimer = Timer.periodic(const Duration(milliseconds: 2200), (_) {
      if (!mounted) return;
      setState(() => _phase = (_phase + 1).clamp(0, 3));
      if (_phase >= 3) _phaseTimer?.cancel();
    });
    // Safety: always enable CTA after 6s
    Future.delayed(const Duration(seconds: 6), () {
      if (mounted && _phase < 3) setState(() => _phase = 3);
    });
  }

  Future<void> _loadCovers() async {
    try {
      final songs = await SupabaseService.getSongs(limit: 24);
      final urls = songs
          .map((s) => (s['cover_url'] ?? s['animated_cover_url'] ?? '').toString())
          .where((u) => u.startsWith('http'))
          .toList();
      if (mounted) setState(() => _covers = urls);
    } catch (_) {}
  }

  Future<void> _finish() async {
    final p = await SharedPreferences.getInstance();
    await p.setBool('welcome_seen', true);
    if (!mounted) return;
    final logged = SupabaseService.isLoggedIn;
    Navigator.of(context).pushReplacementNamed(logged ? '/firstpage' : '/auth');
  }

  @override
  void dispose() {
    _phaseTimer?.cancel();
    _master.dispose();
    _float.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final size = MediaQuery.of(context).size;
    return Scaffold(
      backgroundColor: const Color(0xFF050507),
      body: Stack(
        fit: StackFit.expand,
        children: [
          // Floating real covers (motion graphics layer)
          AnimatedBuilder(
            animation: _float,
            builder: (_, __) {
              if (_covers.isEmpty) {
                return CustomPaint(
                  painter: _GlowPainter(_float.value),
                  size: size,
                );
              }
              return Stack(
                children: List.generate(
                  math.min(_covers.length, 12),
                  (i) => _floatingCover(i, size),
                ),
              );
            },
          ),
          // Dark veil for readability
          Container(
            decoration: BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.topCenter,
                end: Alignment.bottomCenter,
                colors: [
                  Colors.black.withOpacity(0.35),
                  Colors.black.withOpacity(0.75),
                  const Color(0xFF050507),
                ],
              ),
            ),
          ),
          // Copy + CTA
          SafeArea(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(28, 24, 28, 28),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Spacer(flex: 2),
                  FadeTransition(
                    opacity: CurvedAnimation(
                        parent: _master, curve: Curves.easeOut),
                    child: SlideTransition(
                      position: Tween<Offset>(
                        begin: const Offset(0, 0.25),
                        end: Offset.zero,
                      ).animate(CurvedAnimation(
                          parent: _master, curve: Curves.easeOutCubic)),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Container(
                            padding: const EdgeInsets.symmetric(
                                horizontal: 10, vertical: 4),
                            decoration: BoxDecoration(
                              color: const Color(0xfffc3c44).withOpacity(0.2),
                              borderRadius: BorderRadius.circular(20),
                            ),
                            child: Text(
                              'pokke',
                              style: TextStyle(
                                color: const Color(0xfffc3c44).withOpacity(0.95),
                                fontWeight: FontWeight.w800,
                                letterSpacing: 1.2,
                                fontSize: 12,
                              ),
                            ),
                          ),
                          const SizedBox(height: 16),
                          AnimatedOpacity(
                            duration: const Duration(milliseconds: 500),
                            opacity: _phase >= 0 ? 1 : 0,
                            child: Text(
                              t('Bienvenido a pokke', 'Welcome to pokke'),
                              style: const TextStyle(
                                color: Colors.white,
                                fontSize: 34,
                                fontWeight: FontWeight.w900,
                                height: 1.1,
                                letterSpacing: -0.8,
                              ),
                            ),
                          ),
                          const SizedBox(height: 14),
                          AnimatedOpacity(
                            duration: const Duration(milliseconds: 500),
                            opacity: _phase >= 1 ? 1 : 0,
                            child: Text(
                              t(
                                'Tu música, tu estilo. Escucha, mezcla y personaliza cada detalle.',
                                'Your music, your style. Listen, mix and personalize every detail.',
                              ),
                              style: TextStyle(
                                color: Colors.white.withOpacity(0.72),
                                fontSize: 16,
                                height: 1.4,
                              ),
                            ),
                          ),
                          const SizedBox(height: 18),
                          AnimatedOpacity(
                            duration: const Duration(milliseconds: 500),
                            opacity: _phase >= 2 ? 1 : 0,
                            child: Text(
                              t(
                                'Crea playlists, comparte estilos y haz que el player se sienta tuyo.',
                                'Create playlists, share styles and make the player feel yours.',
                              ),
                              style: TextStyle(
                                color: Colors.white.withOpacity(0.5),
                                fontSize: 14,
                                height: 1.35,
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                  const Spacer(flex: 3),
                  // Feature chips
                  AnimatedOpacity(
                    duration: const Duration(milliseconds: 500),
                    opacity: _phase >= 2 ? 1 : 0,
                    child: Wrap(
                      spacing: 8,
                      runSpacing: 8,
                      children: [
                        _chip(Icons.graphic_eq, t('Mezclas', 'Mixes')),
                        _chip(Icons.palette_outlined, t('Estilos', 'Styles')),
                        _chip(Icons.queue_music, t('Playlists', 'Playlists')),
                        _chip(Icons.lyrics_outlined, t('Karaoke', 'Karaoke')),
                      ],
                    ),
                  ),
                  const SizedBox(height: 28),
                  AnimatedOpacity(
                    duration: const Duration(milliseconds: 400),
                    opacity: _phase >= 3 ? 1 : 0.35,
                    child: SizedBox(
                      width: double.infinity,
                      height: 54,
                      child: ElevatedButton(
                        style: ElevatedButton.styleFrom(
                          backgroundColor: const Color(0xfffc3c44),
                          shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(16)),
                          elevation: 0,
                        ),
                        onPressed: _finish,
                        child: Text(
                          t('Empezar', 'Get started'),
                          style: const TextStyle(
                            color: Colors.white,
                            fontWeight: FontWeight.w800,
                            fontSize: 17,
                          ),
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(height: 10),
                  Center(
                    child: TextButton(
                      onPressed: _finish,
                      child: Text(
                        t('Saltar', 'Skip'),
                        style: TextStyle(
                            color: Colors.white.withOpacity(0.4), fontSize: 13),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _chip(IconData icon, String label) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 7),
      decoration: BoxDecoration(
        color: Colors.white.withOpacity(0.07),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: Colors.white.withOpacity(0.08)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 14, color: Colors.white70),
          const SizedBox(width: 6),
          Text(label,
              style: const TextStyle(
                  color: Colors.white70,
                  fontSize: 12,
                  fontWeight: FontWeight.w600)),
        ],
      ),
    );
  }

  Widget _floatingCover(int i, Size size) {
    final t = (_float.value + i * 0.08) % 1.0;
    final rnd = math.Random(i * 17 + 3);
    final baseX = rnd.nextDouble() * size.width;
    final baseY = rnd.nextDouble() * size.height;
    final ampX = 18 + rnd.nextDouble() * 28;
    final ampY = 22 + rnd.nextDouble() * 36;
    final x = baseX + math.sin(t * math.pi * 2) * ampX;
    final y = baseY + math.cos(t * math.pi * 2 + i) * ampY;
    final scale = 0.7 + rnd.nextDouble() * 0.55;
    final s = 64.0 * scale;
    final rot = math.sin(t * math.pi * 2 + i) * 0.08;

    return Positioned(
      left: x - s / 2,
      top: y - s / 2,
      child: Transform.rotate(
        angle: rot,
        child: Opacity(
          opacity: 0.22 + rnd.nextDouble() * 0.25,
          child: Container(
            width: s,
            height: s,
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(14),
              boxShadow: [
                BoxShadow(
                  color: Colors.black.withOpacity(0.4),
                  blurRadius: 16,
                ),
              ],
            ),
            child: ClipRRect(
              borderRadius: BorderRadius.circular(14),
              child: Image.network(
                _covers[i],
                fit: BoxFit.cover,
                errorBuilder: (_, __, ___) => Container(color: Colors.white12),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _GlowPainter extends CustomPainter {
  final double t;
  _GlowPainter(this.t);
  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..shader = RadialGradient(
        colors: [
          const Color(0xfffc3c44).withOpacity(0.25),
          Colors.transparent,
        ],
      ).createShader(Rect.fromCircle(
        center: Offset(size.width * (0.3 + 0.2 * math.sin(t * math.pi * 2)),
            size.height * 0.35),
        radius: size.width * 0.6,
      ));
    canvas.drawRect(Offset.zero & size, paint);
  }

  @override
  bool shouldRepaint(covariant _GlowPainter old) => old.t != t;
}
