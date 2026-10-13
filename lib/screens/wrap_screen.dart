import 'package:flutter/material.dart';
import 'package:pokke/services/listening_history.dart';
import 'package:pokke/widgets/liquid_glass.dart';

/// Weekly (simple) + Monthly (fancy) listening wrap.
class WrapScreen extends StatefulWidget {
  const WrapScreen({super.key});

  @override
  State<WrapScreen> createState() => _WrapScreenState();
}

class _WrapScreenState extends State<WrapScreen> with SingleTickerProviderStateMixin {
  late TabController _tabs;
  List<Map<String, dynamic>> _recent = [];
  Map<String, int> _artists = {};
  int _plays = 0;

  @override
  void initState() {
    super.initState();
    _tabs = TabController(length: 2, vsync: this);
    _load();
  }

  Future<void> _load() async {
    final r = await ListeningHistory.instance.recent(limit: 100);
    final a = await ListeningHistory.instance.artistCounts();
    setState(() {
      _recent = r;
      _artists = a;
      _plays = r.length;
    });
  }

  @override
  void dispose() {
    _tabs.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final topArtist = _artists.entries.toList()
      ..sort((a, b) => b.value.compareTo(a.value));
    final topName = topArtist.isNotEmpty ? topArtist.first.key : '—';
    final topCount = topArtist.isNotEmpty ? topArtist.first.value : 0;

    return Scaffold(
      backgroundColor: const Color(0xFF0C0C0E),
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        title: const Text('Your Wrap', style: TextStyle(fontWeight: FontWeight.w700)),
        bottom: TabBar(
          controller: _tabs,
          indicatorColor: const Color(0xfffc3c44),
          labelColor: Colors.white,
          unselectedLabelColor: Colors.white54,
          tabs: const [Tab(text: 'Semanal'), Tab(text: 'Mensual')],
        ),
      ),
      body: TabBarView(
        controller: _tabs,
        children: [
          // Weekly — simple
          ListView(
            padding: const EdgeInsets.all(20),
            children: [
              LiquidGlass(
                blur: 20,
                opacity: 0.1,
                borderRadius: BorderRadius.circular(20),
                child: Padding(
                  padding: const EdgeInsets.all(24),
                  child: Column(
                    children: [
                      const Text('Esta semana', style: TextStyle(color: Colors.white54)),
                      const SizedBox(height: 8),
                      Text('$_plays', style: const TextStyle(color: Colors.white, fontSize: 48, fontWeight: FontWeight.w800)),
                      const Text('reproducciones', style: TextStyle(color: Colors.white70)),
                      const SizedBox(height: 20),
                      Text('Artista top: $topName', style: const TextStyle(color: Colors.white, fontSize: 18, fontWeight: FontWeight.w600)),
                      Text('$topCount plays', style: TextStyle(color: Colors.white.withOpacity(0.5))),
                    ],
                  ),
                ),
              ),
            ],
          ),
          // Monthly — fancier
          ListView(
            padding: const EdgeInsets.all(20),
            children: [
              TweenAnimationBuilder<double>(
                tween: Tween(begin: 0, end: 1),
                duration: const Duration(milliseconds: 900),
                curve: Curves.easeOutBack,
                builder: (ctx, v, child) => Transform.scale(scale: v, child: child),
                child: LiquidGlass(
                  blur: 28,
                  opacity: 0.12,
                  borderRadius: BorderRadius.circular(24),
                  enableSpecular: true,
                  child: Container(
                    width: double.infinity,
                    padding: const EdgeInsets.all(28),
                    child: Column(
                      children: [
                        const Text('🎁 Regalo del mes', style: TextStyle(fontSize: 16, color: Colors.white70)),
                        const SizedBox(height: 12),
                        const Text('pokke Badge', style: TextStyle(fontSize: 28, fontWeight: FontWeight.w800, color: Colors.white)),
                        const SizedBox(height: 8),
                        Text(
                          _plays >= 50 ? 'Nivel: Melómano' : _plays >= 15 ? 'Nivel: Explorer' : 'Nivel: Newbie',
                          style: const TextStyle(color: Color(0xfffc3c44), fontWeight: FontWeight.w700, fontSize: 16),
                        ),
                        const SizedBox(height: 20),
                        Text('$_plays tracks', style: const TextStyle(color: Colors.white, fontSize: 40, fontWeight: FontWeight.w800)),
                        Text('Artista #1 · $topName', style: const TextStyle(color: Colors.white70, fontSize: 16)),
                        const SizedBox(height: 16),
                        Text(
                          'Sigue escuchando para subir de nivel y desbloquear covers animados.',
                          textAlign: TextAlign.center,
                          style: TextStyle(color: Colors.white.withOpacity(0.45), fontSize: 13),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}
