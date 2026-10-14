import 'dart:async';
import 'dart:math';
import 'package:flutter/material.dart';
import 'package:pokke/services/supabase_service.dart';
import 'package:pokke/services/listening_history.dart';
import 'package:pokke/services/follow_service.dart';
import 'package:pokke/services/ai_dj_service.dart';
import 'package:pokke/services/player_controller.dart';
import 'package:pokke/services/settings_service.dart';
import 'package:pokke/widgets/liquid_glass.dart';

/// Poki DJ — radio-style AI that builds a live queue from
/// followed artists + listening history + catalog discovery.
class PokiDjPage extends StatefulWidget {
  const PokiDjPage({super.key});

  @override
  State<PokiDjPage> createState() => _PokiDjPageState();
}

class _PokiDjPageState extends State<PokiDjPage> with SingleTickerProviderStateMixin {
  final List<Map<String, dynamic>> _queue = [];
  int _index = 0;
  bool _loading = true;
  bool _talking = false;
  String _phase = 'Poki DJ';
  late AnimationController _blob;

  String t(String es, String en) => SettingsService.instance.locale == 'en' ? en : es;

  @override
  void initState() {
    super.initState();
    _blob = AnimationController(vsync: this, duration: const Duration(seconds: 4))..repeat(reverse: true);
    _buildQueue();
  }

  @override
  void dispose() {
    _blob.dispose();
    super.dispose();
  }

  Future<void> _buildQueue() async {
    setState(() {
      _loading = true;
      _phase = t('Armando tu radio…', 'Building your radio…');
    });
    final rnd = Random();
    final out = <Map<String, dynamic>>[];

    // 1) Songs from followed artists
    try {
      final followed = await FollowService.list();
      for (final a in followed.take(8)) {
        final name = (a['name'] ?? a['artist'] ?? '').toString();
        if (name.isEmpty) continue;
        final songs = await SupabaseService.ukiyo
            .from('songs')
            .select()
            .ilike('artist', name)
            .limit(4);
        out.addAll(List<Map<String, dynamic>>.from(songs));
      }
    } catch (_) {}

    // 2) Similar to recent history
    try {
      final recent = ListeningHistory.instance.recent;
      final artists = recent.map((s) => s['artist']?.toString() ?? '').where((s) => s.isNotEmpty).toSet();
      for (final name in artists.take(6)) {
        final songs = await SupabaseService.ukiyo
            .from('songs')
            .select()
            .ilike('artist', name)
            .limit(3);
        out.addAll(List<Map<String, dynamic>>.from(songs));
      }
    } catch (_) {}

    // 3) Catalog discovery fill
    if (out.length < 20) {
      try {
        final more = await SupabaseService.getSongs(limit: 40);
        out.addAll(more);
      } catch (_) {}
    }

    // Dedup + shuffle
    final seen = <String>{};
    final unique = <Map<String, dynamic>>[];
    for (final s in out) {
      final k = '${s['id'] ?? s['title']}_${s['artist']}';
      if (seen.add(k)) unique.add(s);
    }
    unique.shuffle(rnd);

    setState(() {
      _queue
        ..clear()
        ..addAll(unique.take(40));
      _index = 0;
      _loading = false;
      _phase = t('Listo', 'Ready');
    });

    if (_queue.isNotEmpty) {
      await _speakIntro();
      _playCurrent();
    }
  }

  Future<void> _speakIntro() async {
    setState(() {
      _talking = true;
      _phase = t('Poki DJ al habla…', 'Poki DJ speaking…');
    });
    try {
      await AiDjService.instance.speak(
        SettingsService.instance.locale == 'en'
            ? 'Hey! Poki DJ here. I mixed tracks from artists you follow and what you have been listening to. Ready?'
            : '¡Oye! Aquí Poki DJ. Armé una mezcla con artistas que sigues y lo que has escuchado. ¿Preparado?',
      );
      await Future.delayed(const Duration(seconds: 6));
    } catch (_) {}
    if (mounted) setState(() => _talking = false);
  }

  void _playCurrent() {
    if (_index < 0 || _index >= _queue.length) return;
    final song = _queue[_index];
    PlayerController.instance.playSong(song, forceRestart: true);
    setState(() {
      _phase = song['title']?.toString() ?? 'Poki DJ';
    });
  }

  Future<void> _next() async {
    if (_index >= _queue.length - 1) {
      await _buildQueue();
      return;
    }
    // Occasional DJ talk between tracks
    if (Random().nextBool() && _index > 0) {
      setState(() {
        _talking = true;
        _phase = t('Descubriendo música nueva…', 'Discovering new music…');
      });
      final next = _queue[_index + 1];
      try {
        await AiDjService.instance.announceTrack(
          title: next['title']?.toString() ?? '',
          artist: next['artist']?.toString() ?? '',
        );
        await Future.delayed(const Duration(seconds: 5));
      } catch (_) {}
      if (mounted) setState(() => _talking = false);
    }
    setState(() => _index++);
    _playCurrent();
  }

  void _prev() {
    if (_index > 0) {
      setState(() => _index--);
      _playCurrent();
    }
  }

  @override
  Widget build(BuildContext context) {
    final song = _queue.isNotEmpty && _index < _queue.length ? _queue[_index] : null;
    final cover = song?['cover_url']?.toString();

    return Scaffold(
      body: Container(
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [Color(0xFF0A1628), Color(0xFF1A0A2E), Color(0xFF0C0C0E)],
          ),
        ),
        child: SafeArea(
          child: Column(
            children: [
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 8),
                child: Row(
                  children: [
                    IconButton(
                      onPressed: () => Navigator.pop(context),
                      icon: const Icon(Icons.expand_more, color: Colors.white, size: 32),
                    ),
                    const Spacer(),
                    Text('POKI DJ',
                        style: TextStyle(
                          color: Colors.white.withOpacity(0.7),
                          letterSpacing: 2,
                          fontWeight: FontWeight.w700,
                          fontSize: 12,
                        )),
                    const Spacer(),
                    IconButton(
                      onPressed: _buildQueue,
                      icon: const Icon(Icons.refresh, color: Colors.white70),
                    ),
                  ],
                ),
              ),
              Expanded(
                child: Center(
                  child: AnimatedBuilder(
                    animation: _blob,
                    builder: (_, __) {
                      final t = _blob.value;
                      if (_talking || song == null) {
                        return Container(
                          width: 220 + t * 24,
                          height: 220 + (1 - t) * 18,
                          decoration: BoxDecoration(
                            shape: BoxShape.circle,
                            gradient: RadialGradient(
                              colors: [
                                Color.lerp(const Color(0xFF2EE6A6), const Color(0xFF4FC3F7), t)!,
                                const Color(0xFF0A1628),
                              ],
                            ),
                            boxShadow: [
                              BoxShadow(
                                color: const Color(0xFF2EE6A6).withOpacity(0.35),
                                blurRadius: 40 + t * 20,
                              ),
                            ],
                          ),
                        );
                      }
                      return Container(
                        width: 280,
                        height: 280,
                        decoration: BoxDecoration(
                          borderRadius: BorderRadius.circular(24),
                          boxShadow: [
                            BoxShadow(
                              color: const Color(0xFF4FC3F7).withOpacity(0.35),
                              blurRadius: 40,
                            ),
                          ],
                        ),
                        child: ClipRRect(
                          borderRadius: BorderRadius.circular(24),
                          child: cover != null
                              ? Image.network(cover, fit: BoxFit.cover,
                                  errorBuilder: (_, __, ___) => Container(color: Colors.white12))
                              : Container(color: Colors.white12),
                        ),
                      );
                    },
                  ),
                ),
              ),
              Padding(
                padding: const EdgeInsets.fromLTRB(24, 0, 24, 8),
                child: Column(
                  children: [
                    Text(
                      _loading
                          ? t('Armando tu radio…', 'Building your radio…')
                          : (song?['title']?.toString() ?? _phase),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(color: Colors.white, fontSize: 22, fontWeight: FontWeight.w700),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      song?['artist']?.toString() ?? 'Poki DJ',
                      style: TextStyle(color: Colors.white.withOpacity(0.55), fontSize: 15),
                    ),
                    const SizedBox(height: 20),
                    Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        IconButton(
                          iconSize: 36,
                          onPressed: _prev,
                          icon: const Icon(Icons.skip_previous_rounded, color: Colors.white),
                        ),
                        const SizedBox(width: 16),
                        ListenableBuilder(
                          listenable: PlayerController.instance,
                          builder: (_, __) {
                            final playing = PlayerController.instance.isPlaying;
                            return IconButton(
                              iconSize: 56,
                              onPressed: () => PlayerController.instance.toggle(),
                              icon: Icon(
                                playing ? Icons.pause_circle_filled : Icons.play_circle_filled,
                                color: Colors.white,
                              ),
                            );
                          },
                        ),
                        const SizedBox(width: 16),
                        IconButton(
                          iconSize: 36,
                          onPressed: _next,
                          icon: const Icon(Icons.skip_next_rounded, color: Colors.white),
                        ),
                      ],
                    ),
                    const SizedBox(height: 8),
                    Text(
                      t('${_queue.length} canciones en cola', '${_queue.length} tracks in queue'),
                      style: TextStyle(color: Colors.white.withOpacity(0.35), fontSize: 12),
                    ),
                    const SizedBox(height: 16),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
