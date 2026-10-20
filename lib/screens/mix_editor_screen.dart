import 'dart:async';
import 'package:audioplayers/audioplayers.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:pokke/services/settings_service.dart';
import 'package:pokke/services/supabase_service.dart';
import 'package:pokke/widgets/liquid_glass.dart';

/// Editor profesional de mezcla A → B con timeline, handles y preview.
class MixEditorScreen extends StatefulWidget {
  final Map<String, dynamic>? initialA;
  final Map<String, dynamic>? initialB;

  const MixEditorScreen({super.key, this.initialA, this.initialB});

  @override
  State<MixEditorScreen> createState() => _MixEditorScreenState();
}

class _MixEditorScreenState extends State<MixEditorScreen> {
  Map<String, dynamic>? trackA;
  Map<String, dynamic>? trackB;
  final AudioPlayer _playerA = AudioPlayer();
  final AudioPlayer _playerB = AudioPlayer();
  Duration _durA = const Duration(seconds: 180);
  Duration _durB = const Duration(seconds: 180);
  Duration _pos = Duration.zero;
  bool _playing = false;
  double _blendStartA = 40; // seconds
  double _crossfadeSec = 8;
  bool _bStarted = false;
  StreamSubscription? _posSub;

  String t(String es, String en) =>
      SettingsService.instance.locale == 'en' ? en : es;

  @override
  void initState() {
    super.initState();
    trackA = widget.initialA;
    trackB = widget.initialB;
    _playerA.onDurationChanged.listen((d) {
      if (mounted) setState(() => _durA = d);
    });
    _playerB.onDurationChanged.listen((d) {
      if (mounted) setState(() => _durB = d);
    });
    _posSub = _playerA.onPositionChanged.listen((p) {
      if (!mounted) return;
      setState(() => _pos = p);
      _tickCrossfade(p);
    });
    if (trackA != null) _prep(trackA!, true);
    if (trackB != null) _prep(trackB!, false);
  }

  Future<void> _prep(Map<String, dynamic> song, bool isA) async {
    final url = (song['audio_url'] ?? song['stream_url'] ?? '').toString();
    if (url.isEmpty) return;
    try {
      final p = isA ? _playerA : _playerB;
      await p.setSourceUrl(url);
      final d = await p.getDuration();
      if (mounted && d != null) {
        setState(() {
          if (isA) {
            _durA = d;
            _blendStartA =
                (d.inSeconds - _crossfadeSec).clamp(5, d.inSeconds.toDouble());
          } else {
            _durB = d;
          }
        });
      }
    } catch (_) {}
  }

  void _tickCrossfade(Duration posA) {
    if (!_playing || trackB == null) return;
    final startMs = (_blendStartA * 1000).round();
    final fadeMs = (_crossfadeSec * 1000).round().clamp(500, 30000);
    if (posA.inMilliseconds < startMs) {
      _playerA.setVolume(1);
      _playerB.setVolume(0);
      return;
    }
    final elapsed = posA.inMilliseconds - startMs;
    final tNorm = (elapsed / fadeMs).clamp(0.0, 1.0);
    _playerA.setVolume(1.0 - tNorm);
    _playerB.setVolume(tNorm);
    if (!_bStarted) {
      _bStarted = true;
      final url =
          (trackB!['audio_url'] ?? trackB!['stream_url'] ?? '').toString();
      if (url.isNotEmpty) _playerB.play(UrlSource(url));
    }
  }

  Future<void> _toggle() async {
    if (trackA == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(t('Elige la pista A', 'Pick track A'))),
      );
      return;
    }
    HapticFeedback.lightImpact();
    if (_playing) {
      await _playerA.pause();
      await _playerB.pause();
      setState(() => _playing = false);
      return;
    }
    final url = (trackA!['audio_url'] ?? trackA!['stream_url'] ?? '').toString();
    if (url.isEmpty) return;
    _bStarted = false;
    final seekSec = (_blendStartA - 3).clamp(0, 9999.0);
    await _playerA.setVolume(1);
    await _playerB.setVolume(0);
    await _playerA.play(UrlSource(url));
    await _playerA.seek(Duration(milliseconds: (seekSec * 1000).round()));
    setState(() => _playing = true);
  }

  Future<void> _pick(bool isA) async {
    final qCtrl = TextEditingController();
    List<Map<String, dynamic>> results = [];
    await showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) {
        return LiquidGlass(
          blur: 28,
          opacity: 0.14,
          borderRadius: const BorderRadius.vertical(top: Radius.circular(22)),
          child: StatefulBuilder(
            builder: (ctx, setModal) {
              Future<void> search(String q) async {
                try {
                  if (q.trim().isEmpty) {
                    final all = await SupabaseService.getSongs(limit: 40);
                    setModal(() => results = all);
                    return;
                  }
                  final res = await SupabaseService.ukiyo
                      .from('songs')
                      .select()
                      .or('title.ilike.%$q%,artist.ilike.%$q%')
                      .limit(40);
                  setModal(
                      () => results = List<Map<String, dynamic>>.from(res));
                } catch (_) {
                  setModal(() => results = []);
                }
              }

              search('');
              return SizedBox(
                height: MediaQuery.of(ctx).size.height * 0.72,
                child: Column(
                  children: [
                    const SizedBox(height: 10),
                    Container(
                      width: 40,
                      height: 4,
                      decoration: BoxDecoration(
                          color: Colors.white24,
                          borderRadius: BorderRadius.circular(2)),
                    ),
                    Padding(
                      padding: const EdgeInsets.all(14),
                      child: Text(
                        t('Elegir pista ${isA ? "A" : "B"}',
                            'Pick track ${isA ? "A" : "B"}'),
                        style: const TextStyle(
                            color: Colors.white,
                            fontWeight: FontWeight.w800,
                            fontSize: 17),
                      ),
                    ),
                    Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 14),
                      child: TextField(
                        controller: qCtrl,
                        style: const TextStyle(color: Colors.white),
                        onChanged: search,
                        decoration: InputDecoration(
                          hintText: t('Buscar canción o artista…',
                              'Search song or artist…'),
                          hintStyle:
                              TextStyle(color: Colors.white.withOpacity(0.35)),
                          prefixIcon:
                              const Icon(Icons.search, color: Colors.white38),
                          filled: true,
                          fillColor: Colors.white.withOpacity(0.06),
                          border: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(12),
                            borderSide: BorderSide.none,
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(height: 8),
                    Expanded(
                      child: ListView.builder(
                        itemCount: results.length,
                        itemBuilder: (_, i) {
                          final s = results[i];
                          return ListTile(
                            leading: _cover(s['cover_url']?.toString(), 46),
                            title: Text((s['title'] ?? '').toString(),
                                style: const TextStyle(color: Colors.white)),
                            subtitle: Text((s['artist'] ?? '').toString(),
                                style: TextStyle(
                                    color: Colors.white.withOpacity(0.45))),
                            onTap: () async {
                              Navigator.pop(ctx);
                              setState(() {
                                if (isA) {
                                  trackA = s;
                                } else {
                                  trackB = s;
                                }
                              });
                              await _prep(s, isA);
                            },
                          );
                        },
                      ),
                    ),
                  ],
                ),
              );
            },
          ),
        );
      },
    );
  }

  Future<void> _save() async {
    if (trackA == null || trackB == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
            content: Text(t('Elige las dos pistas', 'Pick both tracks'))),
      );
      return;
    }
    final uid = SupabaseService.currentUser?.id;
    if (uid == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
            content: Text(t('Inicia sesión para guardar', 'Sign in to save'))),
      );
      return;
    }
    try {
      final name = '${trackA!['title']} → ${trackB!['title']}';
      final pl = await SupabaseService.kokoro.from('playlists').insert({
        'user_id': uid,
        'name': name,
        'description':
            'mix|startA=${_blendStartA.toStringAsFixed(1)}|xfade=${_crossfadeSec.toStringAsFixed(1)}',
        'cover_url': trackA!['cover_url'],
        'is_public': false,
      }).select().single();
      final plId = pl['id'];
      await SupabaseService.kokoro.from('playlist_songs').insert([
        {
          'playlist_id': plId,
          'song_id': trackA!['id']?.toString(),
          'title': trackA!['title']?.toString() ?? '',
          'artist': trackA!['artist']?.toString() ?? '',
          'cover_url': trackA!['cover_url'],
          'stream_url': trackA!['audio_url'] ?? trackA!['stream_url'],
          'position': 0,
        },
        {
          'playlist_id': plId,
          'song_id': trackB!['id']?.toString(),
          'title': trackB!['title']?.toString() ?? '',
          'artist': trackB!['artist']?.toString() ?? '',
          'cover_url': trackB!['cover_url'],
          'stream_url': trackB!['audio_url'] ?? trackB!['stream_url'],
          'position': 1,
        },
      ]);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
              content: Text(
                  t('Mezcla guardada en Playlists', 'Mix saved to Playlists'))),
        );
        Navigator.pop(context, true);
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text('$e')));
      }
    }
  }

  @override
  void dispose() {
    _posSub?.cancel();
    _playerA.dispose();
    _playerB.dispose();
    super.dispose();
  }

  String _fmt(double sec) {
    final s = sec.round().clamp(0, 35999);
    final m = s ~/ 60;
    final r = s % 60;
    return '${m.toString().padLeft(2, '0')}:${r.toString().padLeft(2, '0')}';
  }

  Widget _cover(String? url, double s) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(8),
      child: url != null && url.isNotEmpty
          ? Image.network(url,
              width: s,
              height: s,
              fit: BoxFit.cover,
              errorBuilder: (_, __, ___) => _ph(s))
          : _ph(s),
    );
  }

  Widget _ph(double s) => Container(
        width: s,
        height: s,
        color: Colors.white12,
        child: const Icon(Icons.music_note, color: Colors.white24, size: 18),
      );

  Widget _trackLane({
    required bool isA,
    required Color accent,
  }) {
    final song = isA ? trackA : trackB;
    final dur = isA ? _durA : _durB;
    return GestureDetector(
      onTap: () => _pick(isA),
      child: Container(
        margin: const EdgeInsets.only(bottom: 10),
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: const Color(0xFF121216),
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: accent.withOpacity(0.35)),
        ),
        child: Row(
          children: [
            Container(
              width: 28,
              height: 28,
              alignment: Alignment.center,
              decoration: BoxDecoration(
                color: accent.withOpacity(0.2),
                shape: BoxShape.circle,
              ),
              child: Text(isA ? 'A' : 'B',
                  style: TextStyle(
                      color: accent, fontWeight: FontWeight.w900, fontSize: 13)),
            ),
            const SizedBox(width: 10),
            _cover(song?['cover_url']?.toString(), 48),
            const SizedBox(width: 10),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    song?['title']?.toString() ??
                        t('Toca para elegir pista', 'Tap to pick track'),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                        color: Colors.white,
                        fontWeight: FontWeight.w700,
                        fontSize: 14),
                  ),
                  Text(
                    song?['artist']?.toString() ??
                        t('Vacío', 'Empty'),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                        color: Colors.white.withOpacity(0.4), fontSize: 12),
                  ),
                ],
              ),
            ),
            Text(_fmt(dur.inSeconds.toDouble()),
                style: TextStyle(
                    color: Colors.white.withOpacity(0.5),
                    fontFeatures: const [FontFeature.tabularFigures()],
                    fontSize: 12)),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final maxStart =
        _durA.inSeconds > 10 ? _durA.inSeconds.toDouble() : 180.0;
    final blendFrac = (_blendStartA / maxStart).clamp(0.0, 1.0);
    final xfadeFrac = (_crossfadeSec / maxStart).clamp(0.02, 0.5);
    final playFrac = _durA.inMilliseconds > 0
        ? (_pos.inMilliseconds / _durA.inMilliseconds).clamp(0.0, 1.0)
        : 0.0;

    return Scaffold(
      backgroundColor: const Color(0xFF070709),
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        title: Text(t('Editor de Mezcla', 'Mix Editor'),
            style: const TextStyle(fontWeight: FontWeight.w800)),
        actions: [
          IconButton(
            onPressed: _toggle,
            icon: Icon(_playing ? Icons.pause_circle_filled : Icons.play_circle_filled,
                color: const Color(0xFF2EE6A6), size: 32),
          ),
          TextButton(
            onPressed: _save,
            child: Text(t('Guardar', 'Save'),
                style: const TextStyle(
                    color: Color(0xfffc3c44), fontWeight: FontWeight.w800)),
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(16, 4, 16, 40),
        children: [
          Text(
            t(
              'Elige dos pistas reales. En la timeline: arrastra el bloque A/B (ondas) para mover el punto de corte. El handle blanco también mueve la transición. Preview desde 3s antes del corte.',
              'Pick two real tracks. On the timeline: drag block A/B (waves) to move the cut point. The white handle also moves the transition.',
            ),
            style: TextStyle(color: Colors.white.withOpacity(0.45), fontSize: 12, height: 1.35),
          ),
          const SizedBox(height: 14),
          _trackLane(isA: true, accent: const Color(0xFF2EE6A6)),
          _trackLane(isA: false, accent: const Color(0xfffc3c44)),
          const SizedBox(height: 8),
          // Timeline canvas
          Container(
            height: 140,
            decoration: BoxDecoration(
              color: const Color(0xFF101014),
              borderRadius: BorderRadius.circular(16),
              border: Border.all(color: Colors.white.withOpacity(0.06)),
            ),
            child: LayoutBuilder(
              builder: (ctx, box) {
                final w = box.maxWidth;
                return GestureDetector(
                  onHorizontalDragUpdate: (d) {
                    final frac = (d.localPosition.dx / w).clamp(0.0, 1.0);
                    setState(() {
                      _blendStartA = frac * maxStart;
                    });
                  },
                  child: Stack(
                    children: [
                      // Lane A waveform
                      Positioned(
                        left: 12,
                        right: 12,
                        top: 18,
                        height: 36,
                        child: CustomPaint(
                          painter: _BarsPainter(
                            color: const Color(0xFF2EE6A6).withOpacity(0.55),
                            seed: 7,
                          ),
                        ),
                      ),
                      // Lane B
                      Positioned(
                        left: 12,
                        right: 12,
                        top: 70,
                        height: 36,
                        child: CustomPaint(
                          painter: _BarsPainter(
                            color: const Color(0xfffc3c44).withOpacity(0.45),
                            seed: 13,
                          ),
                        ),
                      ),
                      // Crossfade region
                      Positioned(
                        left: 12 + blendFrac * (w - 24),
                        width: xfadeFrac * (w - 24),
                        top: 12,
                        bottom: 12,
                        child: Container(
                          decoration: BoxDecoration(
                            color: Colors.white.withOpacity(0.06),
                            borderRadius: BorderRadius.circular(6),
                            border: Border.all(
                                color: Colors.white.withOpacity(0.12)),
                          ),
                        ),
                      ),
                      // Transition handle
                      Positioned(
                        left: 12 + blendFrac * (w - 24) - 10,
                        top: 0,
                        bottom: 0,
                        child: Column(
                          children: [
                            Container(
                              width: 20,
                              height: 20,
                              decoration: const BoxDecoration(
                                color: Colors.white,
                                shape: BoxShape.circle,
                              ),
                              child: const Icon(Icons.drag_indicator,
                                  size: 14, color: Colors.black87),
                            ),
                            Expanded(
                              child: Container(width: 2, color: Colors.white70),
                            ),
                          ],
                        ),
                      ),
                      // Playhead
                      if (_playing)
                        Positioned(
                          left: 12 + playFrac * (w - 24),
                          top: 8,
                          bottom: 8,
                          child: Container(
                            width: 2,
                            color: const Color(0xFF2EE6A6),
                          ),
                        ),
                      Positioned(
                        left: 12,
                        bottom: 6,
                        child: Text('A',
                            style: TextStyle(
                                color: const Color(0xFF2EE6A6).withOpacity(0.8),
                                fontSize: 10,
                                fontWeight: FontWeight.w800)),
                      ),
                      Positioned(
                        left: 12,
                        top: 56,
                        child: Text('B',
                            style: TextStyle(
                                color: const Color(0xfffc3c44).withOpacity(0.8),
                                fontSize: 10,
                                fontWeight: FontWeight.w800)),
                      ),
                    ],
                  ),
                );
              },
            ),
          ),
          const SizedBox(height: 18),
          Row(
            children: [
              Expanded(
                child: Text(
                  t('Corte en A', 'Cut on A'),
                  style: const TextStyle(color: Colors.white70, fontSize: 13),
                ),
              ),
              Text(_fmt(_blendStartA),
                  style: const TextStyle(
                      color: Color(0xFF2EE6A6),
                      fontWeight: FontWeight.w800,
                      fontFeatures: [FontFeature.tabularFigures()])),
            ],
          ),
          Slider(
            value: _blendStartA.clamp(0, maxStart),
            min: 0,
            max: maxStart,
            activeColor: const Color(0xFF2EE6A6),
            onChanged: (v) => setState(() => _blendStartA = v),
          ),
          Row(
            children: [
              Expanded(
                child: Text(
                  t('Duración crossfade', 'Crossfade length'),
                  style: const TextStyle(color: Colors.white70, fontSize: 13),
                ),
              ),
              Text('${_crossfadeSec.toStringAsFixed(1)}s',
                  style: const TextStyle(
                      color: Color(0xfffc3c44), fontWeight: FontWeight.w800)),
            ],
          ),
          Slider(
            value: _crossfadeSec.clamp(2, 20),
            min: 2,
            max: 20,
            divisions: 36,
            activeColor: const Color(0xfffc3c44),
            onChanged: (v) => setState(() => _crossfadeSec = v),
          ),
          const SizedBox(height: 12),
          SizedBox(
            width: double.infinity,
            height: 48,
            child: ElevatedButton.icon(
              style: ElevatedButton.styleFrom(
                backgroundColor: const Color(0xfffc3c44),
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(14)),
              ),
              onPressed: _save,
              icon: const Icon(Icons.save_rounded, color: Colors.white),
              label: Text(t('Guardar mezcla', 'Save mix'),
                  style: const TextStyle(
                      color: Colors.white, fontWeight: FontWeight.w800)),
            ),
          ),
        ],
      ),
    );
  }
}

class _BarsPainter extends CustomPainter {
  final Color color;
  final int seed;
  _BarsPainter({required this.color, required this.seed});

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()..color = color;
    const n = 56;
    final mid = size.height / 2;
    for (var i = 0; i < n; i++) {
      final x = i * size.width / n;
      final h = 6.0 + ((i * seed * 13) % 17);
      canvas.drawRRect(
        RRect.fromRectAndRadius(
          Rect.fromCenter(center: Offset(x, mid), width: 2.2, height: h),
          const Radius.circular(1),
        ),
        paint,
      );
    }
  }

  @override
  bool shouldRepaint(covariant _BarsPainter old) =>
      old.color != color || old.seed != seed;
}
