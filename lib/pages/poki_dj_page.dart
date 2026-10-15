import 'dart:async';
import 'dart:math';
import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:pokke/services/supabase_service.dart';
import 'package:pokke/services/listening_history.dart';
import 'package:pokke/services/follow_service.dart';
import 'package:pokke/services/ai_dj_service.dart';
import 'package:pokke/services/player_controller.dart';
import 'package:pokke/services/settings_service.dart';
import 'package:pokke/services/audio_cache_service.dart';

/// Poki DJ — full radio player with animated orb, voice-before-track sequencing,
/// and local cache so DJ voice + songs work offline.
class PokiDjPage extends StatefulWidget {
  const PokiDjPage({super.key});

  @override
  State<PokiDjPage> createState() => _PokiDjPageState();
}

class _PokiDjPageState extends State<PokiDjPage> with TickerProviderStateMixin {
  final List<Map<String, dynamic>> _queue = [];
  int _index = 0;
  bool _loading = true;
  bool _talking = false;
  bool _preparingVoice = false;
  String _phase = 'Poki DJ';
  String _subtitle = '';

  late AnimationController _blob;
  late AnimationController _pulse;
  late AnimationController _spin;
  late AnimationController _wave;

  String t(String es, String en) => SettingsService.instance.locale == 'en' ? en : es;

  @override
  void initState() {
    super.initState();
    _blob = AnimationController(vsync: this, duration: const Duration(milliseconds: 3200))
      ..repeat(reverse: true);
    _pulse = AnimationController(vsync: this, duration: const Duration(milliseconds: 1400))
      ..repeat(reverse: true);
    _spin = AnimationController(vsync: this, duration: const Duration(seconds: 18))..repeat();
    _wave = AnimationController(vsync: this, duration: const Duration(milliseconds: 900))
      ..repeat(reverse: true);
    _buildQueue();
  }

  @override
  void dispose() {
    _blob.dispose();
    _pulse.dispose();
    _spin.dispose();
    _wave.dispose();
    super.dispose();
  }

  Future<void> _buildQueue() async {
    setState(() {
      _loading = true;
      _phase = t('Armando tu radio…', 'Building your radio…');
      _subtitle = t('Mezclando gustos y seguidos', 'Mixing follows & history');
    });
    final rnd = Random();
    final out = <Map<String, dynamic>>[];

    try {
      final followed = await FollowService.list();
      for (final a in followed.take(8)) {
        final name = (a['name'] ?? a['artist'] ?? a['artist_name'] ?? '').toString();
        if (name.isEmpty) continue;
        final songs = await SupabaseService.ukiyo
            .from('songs')
            .select()
            .ilike('artist', name)
            .limit(4);
        out.addAll(List<Map<String, dynamic>>.from(songs));
      }
    } catch (_) {}

    try {
      final recent = await ListeningHistory.instance.recent(limit: 40);
      final artists =
          recent.map((s) => s['artist']?.toString() ?? '').where((s) => s.isNotEmpty).toSet();
      for (final name in artists.take(6)) {
        final songs = await SupabaseService.ukiyo
            .from('songs')
            .select()
            .ilike('artist', name)
            .limit(3);
        out.addAll(List<Map<String, dynamic>>.from(songs));
      }
    } catch (_) {}

    if (out.length < 20) {
      try {
        final more = await SupabaseService.getSongs(limit: 40);
        out.addAll(more);
      } catch (_) {}
    }

    final seen = <String>{};
    final unique = <Map<String, dynamic>>[];
    for (final s in out) {
      final k = '${s['id'] ?? s['title']}_${s['artist']}';
      if (seen.add(k)) unique.add(s);
    }
    unique.shuffle(rnd);

    // Prefetch first few tracks into persistent cache
    for (final s in unique.take(5)) {
      final url = (s['audio_url'] ?? s['stream_url'] ?? '').toString();
      final id = (s['id'] ?? '').toString();
      if (url.isNotEmpty && id.isNotEmpty) {
        // ignore: unawaited_futures
        AudioCacheService.instance.ensureSongCached(
          id: id,
          title: (s['title'] ?? '').toString(),
          artist: (s['artist'] ?? '').toString(),
          url: url,
        );
      }
    }

    if (!mounted) return;
    setState(() {
      _queue
        ..clear()
        ..addAll(unique.take(40));
      _index = 0;
      _loading = false;
      _phase = t('Listo', 'Ready');
      _subtitle = t('${_queue.length} canciones en cola', '${_queue.length} tracks queued');
    });

    if (_queue.isNotEmpty) {
      await _playWithDj(_index, isIntro: true);
    }
  }

  /// Voice first (wait until finished), then start the track. Never overlap.
  Future<void> _playWithDj(int index, {bool isIntro = false}) async {
    if (index < 0 || index >= _queue.length) return;
    final song = _queue[index];
    final title = song['title']?.toString() ?? '';
    final artist = song['artist']?.toString() ?? '';

    // Stop any current music before talking
    await PlayerController.instance.pauseForDj();

    setState(() {
      _index = index;
      _talking = true;
      _preparingVoice = true;
      _phase = isIntro
          ? t('Poki DJ al habla…', 'Poki DJ speaking…')
          : t('Presentando…', 'Introducing…');
      _subtitle = '$title · $artist';
    });

    // Give TTS a moment to resolve/cache before UI feels rushed
    await Future.delayed(const Duration(milliseconds: 400));
    if (!mounted) return;
    setState(() => _preparingVoice = false);

    try {
      if (isIntro) {
        await AiDjService.instance.speak(
          SettingsService.instance.locale == 'en'
              ? 'Hey! Poki DJ here. I mixed tracks from artists you follow and what you have been listening to. Ready?'
              : '¡Oye! Aquí Poki DJ. Armé una mezcla con artistas que sigues y lo que has escuchado. ¿Preparado?',
        );
        if (!mounted) return;
        // Brief gap after intro before announcing first track
        await Future.delayed(const Duration(milliseconds: 350));
        await AiDjService.instance.announceTrack(title: title, artist: artist);
      } else {
        await AiDjService.instance.announceTrack(title: title, artist: artist);
      }
    } catch (_) {}

    // Only after voice fully ends → start music
    if (!mounted) return;
    setState(() {
      _talking = false;
      _phase = title;
      _subtitle = artist;
    });

    // Prefetch next
    if (index + 1 < _queue.length) {
      final n = _queue[index + 1];
      final url = (n['audio_url'] ?? n['stream_url'] ?? '').toString();
      final id = (n['id'] ?? '').toString();
      if (url.isNotEmpty && id.isNotEmpty) {
        // ignore: unawaited_futures
        AudioCacheService.instance.ensureSongCached(
          id: id,
          title: (n['title'] ?? '').toString(),
          artist: (n['artist'] ?? '').toString(),
          url: url,
        );
      }
    }

    await PlayerController.instance.playSong(song, forceRestart: true);
  }

  Future<void> _next() async {
    if (_talking || _preparingVoice) return; // block skip while talking
    if (_index >= _queue.length - 1) {
      await _buildQueue();
      return;
    }
    await _playWithDj(_index + 1);
  }

  Future<void> _prev() async {
    if (_talking || _preparingVoice) return;
    if (_index > 0) await _playWithDj(_index - 1);
  }

  Widget _buildOrb() {
    return AnimatedBuilder(
      animation: Listenable.merge([_blob, _pulse, _spin, _wave]),
      builder: (_, __) {
        final b = _blob.value;
        final p = _pulse.value;
        final w = _wave.value;
        final talking = _talking || _preparingVoice;
        final size = talking ? 210.0 + p * 36 : 260.0 + b * 12;

        return SizedBox(
          width: 320,
          height: 320,
          child: Stack(
            alignment: Alignment.center,
            children: [
              // Outer glow rings
              for (final scale in [1.35, 1.2, 1.08])
                Transform.rotate(
                  angle: _spin.value * 6.28 * (scale > 1.2 ? 1 : -1),
                  child: Container(
                    width: size * scale,
                    height: size * scale * (0.92 + w * 0.08),
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      border: Border.all(
                        color: (talking ? const Color(0xFF2EE6A6) : const Color(0xFF4FC3F7))
                            .withOpacity(0.12 + p * 0.1),
                        width: 1.5,
                      ),
                    ),
                  ),
                ),
              // Soft blur halo
              Container(
                width: size * 1.15,
                height: size * 1.15,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  boxShadow: [
                    BoxShadow(
                      color: (talking ? const Color(0xFF2EE6A6) : const Color(0xFF7C4DFF))
                          .withOpacity(0.35 + p * 0.2),
                      blurRadius: 50 + p * 30,
                      spreadRadius: 4,
                    ),
                  ],
                ),
              ),
              // Morphing liquid blob via multiple layered circles
              Transform.rotate(
                angle: _spin.value * 6.28 * 0.3,
                child: ClipOval(
                  child: BackdropFilter(
                    filter: ImageFilter.blur(sigmaX: 2, sigmaY: 2),
                    child: Container(
                      width: size,
                      height: size * (0.94 + b * 0.08),
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        gradient: SweepGradient(
                          colors: talking
                              ? [
                                  const Color(0xFF2EE6A6),
                                  const Color(0xFF4FC3F7),
                                  const Color(0xFF7C4DFF),
                                  const Color(0xFF2EE6A6),
                                ]
                              : [
                                  const Color(0xFF4FC3F7),
                                  const Color(0xFF7C4DFF),
                                  const Color(0xFFFF6BCB),
                                  const Color(0xFF4FC3F7),
                                ],
                          transform: GradientRotation(_spin.value * 6.28),
                        ),
                      ),
                      child: Stack(
                        children: [
                          // Specular highlight
                          Positioned(
                            top: size * 0.12,
                            left: size * 0.18,
                            child: Container(
                              width: size * 0.35,
                              height: size * 0.2,
                              decoration: BoxDecoration(
                                borderRadius: BorderRadius.circular(100),
                                gradient: LinearGradient(
                                  colors: [
                                    Colors.white.withOpacity(0.45),
                                    Colors.white.withOpacity(0.0),
                                  ],
                                ),
                              ),
                            ),
                          ),
                          // Cover when not talking
                          if (!_talking &&
                              !_preparingVoice &&
                              _queue.isNotEmpty &&
                              _index < _queue.length &&
                              _queue[_index]['cover_url'] != null)
                            Positioned.fill(
                              child: Opacity(
                                opacity: 0.85,
                                child: Image.network(
                                  _queue[_index]['cover_url'].toString(),
                                  fit: BoxFit.cover,
                                  errorBuilder: (_, __, ___) => const SizedBox.shrink(),
                                ),
                              ),
                            ),
                          // Wave bars when talking
                          if (talking)
                            Center(
                              child: Row(
                                mainAxisSize: MainAxisSize.min,
                                children: List.generate(5, (i) {
                                  final h = 12.0 +
                                      (20 + i * 6) * (0.4 + p * 0.6) *
                                          (i == 2 ? 1.3 : 0.8 + w * 0.4);
                                  return Container(
                                    margin: const EdgeInsets.symmetric(horizontal: 3),
                                    width: 5,
                                    height: h,
                                    decoration: BoxDecoration(
                                      color: Colors.white.withOpacity(0.9),
                                      borderRadius: BorderRadius.circular(3),
                                    ),
                                  );
                                }),
                              ),
                            ),
                        ],
                      ),
                    ),
                  ),
                ),
              ),
            ],
          ),
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    final song = _queue.isNotEmpty && _index < _queue.length ? _queue[_index] : null;

    return Scaffold(
      body: Container(
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [Color(0xFF070B16), Color(0xFF12081F), Color(0xFF0C0C0E)],
          ),
        ),
        child: SafeArea(
          child: Column(
            children: [
              // Top bar
              Padding(
                padding: const EdgeInsets.fromLTRB(4, 4, 8, 0),
                child: Row(
                  children: [
                    IconButton(
                      onPressed: () => Navigator.pop(context),
                      icon: const Icon(Icons.keyboard_arrow_down_rounded,
                          color: Colors.white, size: 34),
                    ),
                    const Spacer(),
                    Column(
                      children: [
                        Text(
                          'POKI DJ',
                          style: TextStyle(
                            color: Colors.white.withOpacity(0.75),
                            letterSpacing: 3,
                            fontWeight: FontWeight.w800,
                            fontSize: 11,
                          ),
                        ),
                        const SizedBox(height: 2),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                          decoration: BoxDecoration(
                            color: const Color(0xFF2EE6A6).withOpacity(0.15),
                            borderRadius: BorderRadius.circular(8),
                          ),
                          child: Text(
                            _talking
                                ? t('EN VIVO', 'LIVE')
                                : t('RADIO', 'RADIO'),
                            style: const TextStyle(
                              color: Color(0xFF2EE6A6),
                              fontSize: 9,
                              fontWeight: FontWeight.w700,
                              letterSpacing: 1.2,
                            ),
                          ),
                        ),
                      ],
                    ),
                    const Spacer(),
                    IconButton(
                      onPressed: _loading ? null : _buildQueue,
                      icon: const Icon(Icons.casino_outlined, color: Colors.white70),
                      tooltip: t('Nueva mezcla', 'New mix'),
                    ),
                  ],
                ),
              ),

              const SizedBox(height: 12),
              Expanded(child: Center(child: _buildOrb())),
              const SizedBox(height: 8),

              // Track info
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 28),
                child: Column(
                  children: [
                    Text(
                      _loading ? t('Armando tu radio…', 'Building your radio…') : _phase,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      textAlign: TextAlign.center,
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 22,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    const SizedBox(height: 6),
                    Text(
                      _subtitle,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        color: Colors.white.withOpacity(0.55),
                        fontSize: 15,
                      ),
                    ),
                    if (_preparingVoice) ...[
                      const SizedBox(height: 10),
                      Text(
                        t('Cargando voz del DJ…', 'Loading DJ voice…'),
                        style: TextStyle(
                          color: const Color(0xFF2EE6A6).withOpacity(0.8),
                          fontSize: 12,
                        ),
                      ),
                    ],
                  ],
                ),
              ),

              const SizedBox(height: 22),

              // Controls row — real player style
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 24),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    IconButton(
                      iconSize: 28,
                      onPressed: (_talking || _preparingVoice) ? null : _prev,
                      icon: Icon(Icons.skip_previous_rounded,
                          color: (_talking || _preparingVoice)
                              ? Colors.white24
                              : Colors.white),
                    ),
                    const SizedBox(width: 18),
                    ListenableBuilder(
                      listenable: PlayerController.instance,
                      builder: (_, __) {
                        final playing =
                            PlayerController.instance.isPlaying && !_talking;
                        return GestureDetector(
                          onTap: (_talking || _preparingVoice)
                              ? null
                              : () => PlayerController.instance.toggle(),
                          child: Container(
                            width: 72,
                            height: 72,
                            decoration: BoxDecoration(
                              shape: BoxShape.circle,
                              gradient: const LinearGradient(
                                colors: [Color(0xFF2EE6A6), Color(0xFF4FC3F7)],
                              ),
                              boxShadow: [
                                BoxShadow(
                                  color: const Color(0xFF2EE6A6).withOpacity(0.35),
                                  blurRadius: 18,
                                ),
                              ],
                            ),
                            child: Icon(
                              playing
                                  ? Icons.pause_rounded
                                  : Icons.play_arrow_rounded,
                              color: Colors.black,
                              size: 40,
                            ),
                          ),
                        );
                      },
                    ),
                    const SizedBox(width: 18),
                    IconButton(
                      iconSize: 28,
                      onPressed: (_talking || _preparingVoice) ? null : _next,
                      icon: Icon(Icons.skip_next_rounded,
                          color: (_talking || _preparingVoice)
                              ? Colors.white24
                              : Colors.white),
                    ),
                  ],
                ),
              ),

              const SizedBox(height: 16),
              // Queue strip
              SizedBox(
                height: 64,
                child: ListView.builder(
                  scrollDirection: Axis.horizontal,
                  padding: const EdgeInsets.symmetric(horizontal: 16),
                  itemCount: _queue.length,
                  itemBuilder: (_, i) {
                    final s = _queue[i];
                    final active = i == _index;
                    return GestureDetector(
                      onTap: (_talking || _preparingVoice) ? null : () => _playWithDj(i),
                      child: Container(
                        width: 48,
                        margin: const EdgeInsets.only(right: 8),
                        decoration: BoxDecoration(
                          borderRadius: BorderRadius.circular(8),
                          border: active
                              ? Border.all(color: const Color(0xFF2EE6A6), width: 2)
                              : null,
                        ),
                        child: ClipRRect(
                          borderRadius: BorderRadius.circular(6),
                          child: s['cover_url'] != null
                              ? Image.network(
                                  s['cover_url'].toString(),
                                  fit: BoxFit.cover,
                                  errorBuilder: (_, __, ___) =>
                                      Container(color: Colors.white12),
                                )
                              : Container(color: Colors.white12),
                        ),
                      ),
                    );
                  },
                ),
              ),
              const SizedBox(height: 18),
            ],
          ),
        ),
      ),
    );
  }
}
