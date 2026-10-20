import 'dart:async';
import 'dart:math' as math;
import 'package:audioplayers/audioplayers.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:pokke/services/settings_service.dart';
import 'package:pokke/services/supabase_service.dart';

/// Editor de mezcla A→B con timeline real (sin sliders).
/// Colores neutros: blanco / gris / acento pokke.
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
  double _cutSec = 40; // cut point on A
  double _xfadeSec = 6;
  bool _bStarted = false;
  StreamSubscription? _posSub;
  // Horizontal offset of track blocks (visual only + cut)
  double _offsetA = 0; // 0..1 of timeline
  double _offsetB = 0.25;

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

  @override
  void dispose() {
    _posSub?.cancel();
    _playerA.dispose();
    _playerB.dispose();
    super.dispose();
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
            _cutSec = (d.inSeconds * 0.45).clamp(5, d.inSeconds - 5.0);
          } else {
            _durB = d;
          }
        });
      }
    } catch (_) {}
  }

  void _tickCrossfade(Duration posA) {
    if (!_playing || trackB == null) return;
    final startMs = (_cutSec * 1000).round();
    final fadeMs = (_xfadeSec * 1000).round().clamp(300, 30000);
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
      if (url.isNotEmpty) {
        _playerB.play(UrlSource(url));
      }
    }
  }

  Future<void> _toggle() async {
    if (trackA == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(t('Elige la pista A', 'Pick track A'))),
      );
      return;
    }
    if (_playing) {
      await _playerA.pause();
      await _playerB.pause();
      setState(() => _playing = false);
      return;
    }
    _bStarted = false;
    final start = Duration(
        milliseconds: math.max(0, ((_cutSec - 3) * 1000).round()));
    final url = (trackA!['audio_url'] ?? trackA!['stream_url'] ?? '').toString();
    if (url.isEmpty) return;
    await _playerA.play(UrlSource(url));
    await _playerA.seek(start);
    await _playerA.setVolume(1);
    await _playerB.setVolume(0);
    setState(() => _playing = true);
  }

  Future<void> _pick(bool isA) async {
    try {
      final songs = await SupabaseService.searchSongs('', limit: 40);
      if (!mounted) return;
      final picked = await showModalBottomSheet<Map<String, dynamic>>(
        context: context,
        backgroundColor: const Color(0xFF121214),
        isScrollControlled: true,
        shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(18)),
        ),
        builder: (ctx) {
          return DraggableScrollableSheet(
            expand: false,
            initialChildSize: 0.7,
            maxChildSize: 0.92,
            builder: (_, sc) {
              return Column(
                children: [
                  const SizedBox(height: 10),
                  Container(
                    width: 36,
                    height: 4,
                    decoration: BoxDecoration(
                      color: Colors.white24,
                      borderRadius: BorderRadius.circular(2),
                    ),
                  ),
                  Padding(
                    padding: const EdgeInsets.all(14),
                    child: Text(
                      t('Elegir pista ${isA ? "A" : "B"}',
                          'Pick track ${isA ? "A" : "B"}'),
                      style: const TextStyle(
                          color: Colors.white,
                          fontWeight: FontWeight.w700,
                          fontSize: 16),
                    ),
                  ),
                  Expanded(
                    child: ListView.builder(
                      controller: sc,
                      itemCount: songs.length,
                      itemBuilder: (_, i) {
                        final s = songs[i];
                        final cover = s['cover_url']?.toString();
                        return ListTile(
                          leading: ClipRRect(
                            borderRadius: BorderRadius.circular(6),
                            child: cover != null
                                ? Image.network(cover,
                                    width: 44, height: 44, fit: BoxFit.cover,
                                    errorBuilder: (_, __, ___) =>
                                        const Icon(Icons.music_note,
                                            color: Colors.white38))
                                : const Icon(Icons.music_note,
                                    color: Colors.white38),
                          ),
                          title: Text(
                            (s['title'] ?? '').toString(),
                            style: const TextStyle(color: Colors.white),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                          subtitle: Text(
                            (s['artist'] ?? '').toString(),
                            style: const TextStyle(
                                color: Colors.white54, fontSize: 12),
                          ),
                          onTap: () => Navigator.pop(ctx, s),
                        );
                      },
                    ),
                  ),
                ],
              );
            },
          );
        },
      );
      if (picked == null) return;
      setState(() {
        if (isA) {
          trackA = picked;
        } else {
          trackB = picked;
        }
      });
      await _prep(picked, isA);
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('$e')),
        );
      }
    }
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
    if (uid == null) return;
    try {
      final name = '${trackA!['title']} → ${trackB!['title']}';
      final row = await SupabaseService.kokoro.from('playlists').insert({
        'user_id': uid,
        'name': name,
        'cover_url': trackA!['cover_url'],
        'is_public': false,
        'source': 'mix',
        'mix_cut_sec': _cutSec,
        'mix_xfade_sec': _xfadeSec,
      }).select().single();
      final pid = row['id'];
      await SupabaseService.kokoro.from('playlist_songs').insert({
        'playlist_id': pid,
        'song_id': trackA!['id']?.toString(),
        'title': trackA!['title']?.toString() ?? '',
        'artist': trackA!['artist']?.toString() ?? '',
        'cover_url': trackA!['cover_url'],
        'stream_url': trackA!['audio_url'] ?? trackA!['stream_url'],
        'position': 0,
      });
      await SupabaseService.kokoro.from('playlist_songs').insert({
        'playlist_id': pid,
        'song_id': trackB!['id']?.toString(),
        'title': trackB!['title']?.toString() ?? '',
        'artist': trackB!['artist']?.toString() ?? '',
        'cover_url': trackB!['cover_url'],
        'stream_url': trackB!['audio_url'] ?? trackB!['stream_url'],
        'position': 1,
      });
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(t('Mezcla guardada', 'Mix saved'))),
        );
        Navigator.pop(context);
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('$e')),
        );
      }
    }
  }

  String _fmt(double sec) {
    final s = sec.floor();
    final m = s ~/ 60;
    final r = s % 60;
    return '${m.toString().padLeft(1, '0')}:${r.toString().padLeft(2, '0')}';
  }

  Widget _trackChip(bool isA) {
    final song = isA ? trackA : trackB;
    final label = isA ? 'A' : 'B';
    final accent = isA ? Colors.white : const Color(0xfffc3c44);
    return GestureDetector(
      onTap: () => _pick(isA),
      child: Container(
        margin: const EdgeInsets.only(bottom: 10),
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: const Color(0xFF141416),
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: Colors.white.withOpacity(0.08)),
        ),
        child: Row(
          children: [
            Container(
              width: 28,
              height: 28,
              alignment: Alignment.center,
              decoration: BoxDecoration(
                color: accent.withOpacity(0.15),
                shape: BoxShape.circle,
                border: Border.all(color: accent.withOpacity(0.5)),
              ),
              child: Text(label,
                  style: TextStyle(
                      color: accent,
                      fontWeight: FontWeight.w800,
                      fontSize: 12)),
            ),
            const SizedBox(width: 10),
            ClipRRect(
              borderRadius: BorderRadius.circular(6),
              child: song?['cover_url'] != null
                  ? Image.network(song!['cover_url'].toString(),
                      width: 42, height: 42, fit: BoxFit.cover,
                      errorBuilder: (_, __, ___) => _ph())
                  : _ph(),
            ),
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
                        color: Colors.white, fontWeight: FontWeight.w600),
                  ),
                  Text(
                    song?['artist']?.toString() ?? t('Vacío', 'Empty'),
                    style: TextStyle(
                        color: Colors.white.withOpacity(0.45), fontSize: 12),
                  ),
                ],
              ),
            ),
            Text(
              _fmt((isA ? _durA : _durB).inSeconds.toDouble()),
              style: TextStyle(
                  color: Colors.white.withOpacity(0.4),
                  fontFeatures: const [FontFeature.tabularFigures()]),
            ),
          ],
        ),
      ),
    );
  }

  Widget _ph() => Container(
        width: 42,
        height: 42,
        color: Colors.white10,
        child: const Icon(Icons.music_note, color: Colors.white24, size: 20),
      );

  Widget _waveLane({
    required Color color,
    required double leftFrac,
    required double widthFrac,
    required bool isA,
  }) {
    return LayoutBuilder(builder: (ctx, box) {
      final w = box.maxWidth;
      final left = leftFrac * w;
      final bw = (widthFrac * w).clamp(40.0, w);
      return Positioned(
        left: left,
        width: bw,
        top: isA ? 54 : 12,
        height: 40,
        child: GestureDetector(
          onHorizontalDragUpdate: (d) {
            setState(() {
              final delta = d.delta.dx / w;
              if (isA) {
                _offsetA = (_offsetA + delta).clamp(0.0, 0.6);
                // moving A also shifts cut relative to visual
                final maxS = _durA.inSeconds > 10
                    ? _durA.inSeconds.toDouble()
                    : 180.0;
                _cutSec = ((_offsetA + 0.35) * maxS).clamp(3.0, maxS - 2);
              } else {
                _offsetB = (_offsetB + delta).clamp(0.0, 0.7);
              }
            });
          },
          child: CustomPaint(
            painter: _WavePainter(color: color),
            child: Container(
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(6),
                border: Border.all(color: color.withOpacity(0.35)),
                color: color.withOpacity(0.06),
              ),
              alignment: Alignment.centerLeft,
              padding: const EdgeInsets.only(left: 8),
              child: Text(isA ? 'A' : 'B',
                  style: TextStyle(
                      color: color.withOpacity(0.9),
                      fontSize: 10,
                      fontWeight: FontWeight.w800)),
            ),
          ),
        ),
      );
    });
  }

  @override
  Widget build(BuildContext context) {
    final maxStart =
        _durA.inSeconds > 10 ? _durA.inSeconds.toDouble() : 180.0;
    final cutFrac = (_cutSec / maxStart).clamp(0.0, 1.0);
    final xfadeFrac = (_xfadeSec / maxStart).clamp(0.02, 0.4);
    final playFrac = _durA.inMilliseconds > 0
        ? (_pos.inMilliseconds / _durA.inMilliseconds).clamp(0.0, 1.0)
        : 0.0;

    return Scaffold(
      backgroundColor: const Color(0xFF0A0A0C),
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        title: Text(t('Editor de Mezcla', 'Mix Editor'),
            style: const TextStyle(fontWeight: FontWeight.w800)),
        actions: [
          IconButton(
            onPressed: _toggle,
            icon: Icon(
              _playing ? Icons.pause_circle_filled : Icons.play_circle_filled,
              color: Colors.white,
              size: 30,
            ),
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
              'Elige dos pistas. Arrastra los bloques A/B en la timeline. El handle blanco es el corte; el ancho del velo es el crossfade.',
              'Pick two tracks. Drag A/B blocks on the timeline. White handle = cut; veil width = crossfade.',
            ),
            style: TextStyle(
                color: Colors.white.withOpacity(0.45),
                fontSize: 12,
                height: 1.35),
          ),
          const SizedBox(height: 14),
          _trackChip(true),
          _trackChip(false),
          const SizedBox(height: 8),
          // Timeline
          Container(
            height: 150,
            decoration: BoxDecoration(
              color: const Color(0xFF121214),
              borderRadius: BorderRadius.circular(16),
              border: Border.all(color: Colors.white.withOpacity(0.06)),
            ),
            child: LayoutBuilder(
              builder: (ctx, box) {
                final w = box.maxWidth;
                return GestureDetector(
                  onHorizontalDragUpdate: (d) {
                    setState(() {
                      final f = ((d.localPosition.dx) / w).clamp(0.0, 1.0);
                      _cutSec = (f * maxStart).clamp(2.0, maxStart - 1);
                    });
                  },
                  onScaleUpdate: (s) {
                    // pinch-ish: scale changes crossfade
                    setState(() {
                      _xfadeSec =
                          (_xfadeSec * s.scale).clamp(1.0, 20.0);
                    });
                  },
                  child: Stack(
                    children: [
                      // grid
                      ...List.generate(8, (i) {
                        return Positioned(
                          left: (i / 8) * w,
                          top: 0,
                          bottom: 0,
                          child: Container(
                              width: 1,
                              color: Colors.white.withOpacity(0.04)),
                        );
                      }),
                      // Track B block (top)
                      _waveLane(
                        color: const Color(0xfffc3c44),
                        leftFrac: _offsetB,
                        widthFrac: 0.55,
                        isA: false,
                      ),
                      // Track A block (bottom)
                      _waveLane(
                        color: Colors.white70,
                        leftFrac: _offsetA,
                        widthFrac: 0.6,
                        isA: true,
                      ),
                      // Crossfade veil
                      Positioned(
                        left: cutFrac * w,
                        width: xfadeFrac * w,
                        top: 8,
                        bottom: 8,
                        child: Container(
                          decoration: BoxDecoration(
                            gradient: LinearGradient(
                              colors: [
                                Colors.white.withOpacity(0.14),
                                Colors.white.withOpacity(0.02),
                              ],
                            ),
                            borderRadius: BorderRadius.circular(6),
                          ),
                        ),
                      ),
                      // Cut handle
                      Positioned(
                        left: cutFrac * w - 12,
                        top: 0,
                        bottom: 0,
                        child: Column(
                          children: [
                            Container(
                              width: 24,
                              height: 24,
                              decoration: const BoxDecoration(
                                color: Colors.white,
                                shape: BoxShape.circle,
                              ),
                              child: const Icon(Icons.drag_indicator,
                                  size: 14, color: Colors.black87),
                            ),
                            Expanded(
                              child: Container(
                                  width: 2, color: Colors.white70),
                            ),
                          ],
                        ),
                      ),
                      // Playhead
                      if (_playing)
                        Positioned(
                          left: playFrac * w,
                          top: 8,
                          bottom: 8,
                          child: Container(
                            width: 2,
                            color: const Color(0xfffc3c44),
                          ),
                        ),
                      // labels
                      Positioned(
                        left: 10,
                        bottom: 6,
                        child: Text(
                          '${t('Corte', 'Cut')} ${_fmt(_cutSec)}  ·  xfade ${_xfadeSec.toStringAsFixed(1)}s',
                          style: TextStyle(
                            color: Colors.white.withOpacity(0.5),
                            fontSize: 11,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ),
                    ],
                  ),
                );
              },
            ),
          ),
          const SizedBox(height: 20),
          SizedBox(
            height: 50,
            child: ElevatedButton.icon(
              onPressed: _save,
              style: ElevatedButton.styleFrom(
                backgroundColor: const Color(0xfffc3c44),
                foregroundColor: Colors.white,
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(14)),
              ),
              icon: const Icon(Icons.save_outlined),
              label: Text(t('Guardar mezcla', 'Save mix'),
                  style: const TextStyle(fontWeight: FontWeight.w800)),
            ),
          ),
        ],
      ),
    );
  }
}

class _WavePainter extends CustomPainter {
  final Color color;
  _WavePainter({required this.color});

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = color.withOpacity(0.55)
      ..strokeWidth = 2
      ..strokeCap = StrokeCap.round;
    final rnd = math.Random(7);
    final mid = size.height / 2;
    for (double x = 4; x < size.width - 4; x += 5) {
      final h = 4 + rnd.nextDouble() * (size.height * 0.35);
      canvas.drawLine(Offset(x, mid - h), Offset(x, mid + h), paint);
    }
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}
