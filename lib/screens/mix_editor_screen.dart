import 'dart:async';
import 'package:audioplayers/audioplayers.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:pokke/services/settings_service.dart';
import 'package:pokke/services/supabase_service.dart';
import 'package:pokke/widgets/liquid_glass.dart';

/// Editor de mezcla estilo crossfade (A → B) con punto de transición y duración.
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
  Duration _durA = Duration.zero;
  Duration _durB = Duration.zero;
  Duration _pos = Duration.zero;
  bool _playing = false;
  /// 0..1 position of transition within the overlap window
  double _transitionNorm = 0.5;
  /// Crossfade length in seconds
  double _crossfadeSec = 8;
  /// Start of blend on track A (seconds from start)
  double _blendStartA = 20;
  StreamSubscription? _posSub;
  Timer? _previewTimer;

  String t(String es, String en) =>
      SettingsService.instance.locale == 'en' ? en : es;

  @override
  void initState() {
    super.initState();
    trackA = widget.initialA;
    trackB = widget.initialB;
    _wirePlayers();
    if (trackA != null) _loadDuration(trackA!, true);
    if (trackB != null) _loadDuration(trackB!, false);
  }

  void _wirePlayers() {
    _playerA.onDurationChanged.listen((d) {
      if (mounted) setState(() => _durA = d);
    });
    _playerB.onDurationChanged.listen((d) {
      if (mounted) setState(() => _durB = d);
    });
    _posSub = _playerA.onPositionChanged.listen((p) {
      if (mounted) setState(() => _pos = p);
      _maybeStartB(p);
    });
  }

  Future<void> _loadDuration(Map<String, dynamic> song, bool isA) async {
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
            _blendStartA = (d.inSeconds - _crossfadeSec).clamp(0, d.inSeconds.toDouble());
          } else {
            _durB = d;
          }
        });
      }
    } catch (_) {}
  }

  Future<void> _pickTrack(bool isA) async {
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
                    final all = await SupabaseService.getSongs(limit: 30);
                    setModal(() => results = all);
                    return;
                  }
                  final res = await SupabaseService.ukiyo
                      .from('songs')
                      .select()
                      .or('title.ilike.%$q%,artist.ilike.%$q%')
                      .limit(40);
                  setModal(() => results = List<Map<String, dynamic>>.from(res));
                } catch (_) {
                  setModal(() => results = []);
                }
              }

              search('');
              return SizedBox(
                height: MediaQuery.of(ctx).size.height * 0.7,
                child: Column(
                  children: [
                    const SizedBox(height: 12),
                    Text(t('Elegir canción ${isA ? "A" : "B"}', 'Pick track ${isA ? "A" : "B"}'),
                        style: const TextStyle(
                            color: Colors.white, fontWeight: FontWeight.w700, fontSize: 17)),
                    Padding(
                      padding: const EdgeInsets.all(12),
                      child: TextField(
                        controller: qCtrl,
                        style: const TextStyle(color: Colors.white),
                        onChanged: search,
                        decoration: InputDecoration(
                          hintText: t('Buscar…', 'Search…'),
                          hintStyle: TextStyle(color: Colors.white.withOpacity(0.35)),
                          prefixIcon: const Icon(Icons.search, color: Colors.white38),
                          filled: true,
                          fillColor: Colors.white.withOpacity(0.06),
                          border: OutlineInputBorder(
                              borderRadius: BorderRadius.circular(12), borderSide: BorderSide.none),
                        ),
                      ),
                    ),
                    Expanded(
                      child: ListView.builder(
                        itemCount: results.length,
                        itemBuilder: (_, i) {
                          final s = results[i];
                          return ListTile(
                            leading: _cover(s['cover_url']?.toString(), 44),
                            title: Text((s['title'] ?? '').toString(),
                                style: const TextStyle(color: Colors.white)),
                            subtitle: Text((s['artist'] ?? '').toString(),
                                style: TextStyle(color: Colors.white.withOpacity(0.5))),
                            onTap: () async {
                              Navigator.pop(ctx);
                              setState(() {
                                if (isA) {
                                  trackA = s;
                                } else {
                                  trackB = s;
                                }
                              });
                              await _loadDuration(s, isA);
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

  void _maybeStartB(Duration posA) {
    if (trackB == null || !_playing) return;
    final startMs = (_blendStartA * 1000).round();
    if (posA.inMilliseconds >= startMs) {
      final elapsedInFade = (posA.inMilliseconds - startMs) / 1000.0;
      final tNorm = (elapsedInFade / _crossfadeSec).clamp(0.0, 1.0);
      _playerA.setVolume(1.0 - tNorm);
      _playerB.setVolume(tNorm);
      // Start B once when we enter the window
      if (elapsedInFade < 0.15) {
        final url = (trackB!['audio_url'] ?? trackB!['stream_url'] ?? '').toString();
        if (url.isNotEmpty) {
          _playerB.play(UrlSource(url));
        }
      }
    }
  }

  Future<void> _togglePlay() async {
    if (trackA == null) return;
    HapticFeedback.lightImpact();
    if (_playing) {
      await _playerA.pause();
      await _playerB.pause();
      setState(() => _playing = false);
      return;
    }
    final urlA = (trackA!['audio_url'] ?? trackA!['stream_url'] ?? '').toString();
    if (urlA.isEmpty) return;
    // Seek near blend for quick preview
    final seekTo = Duration(milliseconds: ((_blendStartA - 2).clamp(0, 9999) * 1000).round());
    await _playerA.play(UrlSource(urlA));
    await _playerA.seek(seekTo);
    await _playerA.setVolume(1);
    await _playerB.setVolume(0);
    setState(() => _playing = true);
  }

  Future<void> _saveMix() async {
    if (trackA == null || trackB == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(t('Elige las dos canciones', 'Pick both tracks'))),
      );
      return;
    }
    final uid = SupabaseService.currentUser?.id;
    if (uid == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(t('Inicia sesión para guardar', 'Sign in to save'))),
      );
      return;
    }
    try {
      // Store as a special playlist of 2 songs with mix metadata in description
      final name =
          '${trackA!['title']} → ${trackB!['title']}';
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
          SnackBar(content: Text(t('Mezcla guardada en Playlists', 'Mix saved to Playlists'))),
        );
        Navigator.pop(context, true);
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('$e')));
      }
    }
  }

  @override
  void dispose() {
    _posSub?.cancel();
    _previewTimer?.cancel();
    _playerA.dispose();
    _playerB.dispose();
    super.dispose();
  }

  Widget _cover(String? url, double s) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(8),
      child: url != null && url.isNotEmpty
          ? Image.network(url, width: s, height: s, fit: BoxFit.cover,
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

  Widget _trackCard(bool isA) {
    final song = isA ? trackA : trackB;
    final label = isA ? 'A' : 'B';
    final time = isA
        ? _fmt(Duration(seconds: _blendStartA.round()))
        : '00:00';
    return Material(
      color: const Color(0xFF1A1A1E),
      borderRadius: BorderRadius.circular(14),
      child: InkWell(
        borderRadius: BorderRadius.circular(14),
        onTap: () => _pickTrack(isA),
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Row(
            children: [
              _cover(song?['cover_url']?.toString(), 52),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      song?['title']?.toString() ?? t('Toca para elegir', 'Tap to pick'),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                          color: Colors.white, fontWeight: FontWeight.w700, fontSize: 15),
                    ),
                    Text(
                      song?['artist']?.toString() ?? '—',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(color: Colors.white.withOpacity(0.45), fontSize: 12),
                    ),
                  ],
                ),
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                decoration: BoxDecoration(
                  color: Colors.white.withOpacity(0.08),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Text(time,
                    style: const TextStyle(color: Colors.white70, fontFeatures: [FontFeature.tabularFigures()])),
              ),
              const SizedBox(width: 8),
              Container(
                width: 28,
                height: 28,
                alignment: Alignment.center,
                decoration: BoxDecoration(
                  color: isA ? const Color(0xFF2EE6A6).withOpacity(0.2) : const Color(0xfffc3c44).withOpacity(0.2),
                  shape: BoxShape.circle,
                ),
                child: Text(label,
                    style: TextStyle(
                      color: isA ? const Color(0xFF2EE6A6) : const Color(0xfffc3c44),
                      fontWeight: FontWeight.w800,
                    )),
              ),
            ],
          ),
        ),
      ),
    );
  }

  String _fmt(Duration d) {
    final m = d.inMinutes.remainder(60).toString().padLeft(2, '0');
    final s = d.inSeconds.remainder(60).toString().padLeft(2, '0');
    return '$m:$s';
  }

  @override
  Widget build(BuildContext context) {
    final maxStart = _durA.inSeconds > 0 ? _durA.inSeconds.toDouble() : 180.0;
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        backgroundColor: Colors.black,
        title: Text(t('Editor de Mezcla', 'Mix Editor'),
            style: const TextStyle(fontWeight: FontWeight.w700)),
        actions: [
          TextButton(
            onPressed: _saveMix,
            child: Text(t('Guardar', 'Save'),
                style: const TextStyle(color: Color(0xfffc3c44), fontWeight: FontWeight.w700)),
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 40),
        children: [
          _trackCard(true),
          const SizedBox(height: 10),
          _trackCard(false),
          const SizedBox(height: 20),
          // Waveform / transition visual
          Container(
            padding: const EdgeInsets.all(14),
            decoration: BoxDecoration(
              color: const Color(0xFF121214),
              borderRadius: BorderRadius.circular(16),
              border: Border.all(color: Colors.white.withOpacity(0.06)),
            ),
            child: Column(
              children: [
                Row(
                  children: [
                    const Icon(Icons.graphic_eq, color: Color(0xFF2EE6A6), size: 18),
                    const SizedBox(width: 6),
                    Text(t('Transición', 'Transition'),
                        style: const TextStyle(
                            color: Colors.white70, fontWeight: FontWeight.w600, fontSize: 13)),
                    const Spacer(),
                    IconButton(
                      onPressed: _togglePlay,
                      icon: Icon(_playing ? Icons.pause_circle_filled : Icons.play_circle_filled,
                          color: const Color(0xFF2EE6A6), size: 36),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                SizedBox(
                  height: 90,
                  child: CustomPaint(
                    painter: _WavePainter(
                      progress: _durA.inMilliseconds > 0
                          ? _pos.inMilliseconds / _durA.inMilliseconds
                          : 0,
                      blendStart: maxStart > 0 ? _blendStartA / maxStart : 0.5,
                      xfade: maxStart > 0 ? _crossfadeSec / maxStart : 0.1,
                    ),
                    size: Size.infinite,
                  ),
                ),
                if (trackA != null && trackB != null)
                  Padding(
                    padding: const EdgeInsets.only(top: 8),
                    child: Text(
                      '${trackA!['title']} → ${trackB!['title']}',
                      style: TextStyle(color: Colors.white.withOpacity(0.5), fontSize: 12),
                      textAlign: TextAlign.center,
                    ),
                  ),
              ],
            ),
          ),
          const SizedBox(height: 20),
          Text(t('Inicio de mezcla en A', 'Blend start on A'),
              style: const TextStyle(color: Colors.white70, fontSize: 13)),
          Row(
            children: [
              Expanded(
                child: Slider(
                  value: _blendStartA.clamp(0, maxStart),
                  min: 0,
                  max: maxStart,
                  activeColor: const Color(0xFF2EE6A6),
                  onChanged: (v) => setState(() => _blendStartA = v),
                ),
              ),
              SizedBox(
                width: 48,
                child: Text(_fmt(Duration(seconds: _blendStartA.round())),
                    style: const TextStyle(color: Colors.white70, fontSize: 12)),
              ),
            ],
          ),
          Text(t('Duración del Crossfade', 'Crossfade duration'),
              style: const TextStyle(color: Colors.white70, fontSize: 13)),
          Row(
            children: [
              Expanded(
                child: Slider(
                  value: _crossfadeSec.clamp(2, 20),
                  min: 2,
                  max: 20,
                  divisions: 36,
                  activeColor: const Color(0xfffc3c44),
                  onChanged: (v) => setState(() => _crossfadeSec = v),
                ),
              ),
              SizedBox(
                width: 48,
                child: Text('${_crossfadeSec.toStringAsFixed(1)}s',
                    style: const TextStyle(color: Color(0xfffc3c44), fontSize: 12)),
              ),
            ],
          ),
          const SizedBox(height: 12),
          Text(
            t(
              'Elige dos canciones, marca dónde termina A y cuánto dura el fade hacia B. Guarda como playlist especial.',
              'Pick two tracks, set where A ends and how long the fade into B lasts. Saves as a special playlist.',
            ),
            style: TextStyle(color: Colors.white.withOpacity(0.4), fontSize: 12, height: 1.35),
          ),
        ],
      ),
    );
  }
}

class _WavePainter extends CustomPainter {
  final double progress;
  final double blendStart;
  final double xfade;

  _WavePainter({required this.progress, required this.blendStart, required this.xfade});

  @override
  void paint(Canvas canvas, Size size) {
    final mid = size.height / 2;
    final paintA = Paint()..color = const Color(0xFF2EE6A6).withOpacity(0.55)..strokeWidth = 2;
    final paintB = Paint()..color = const Color(0xfffc3c44).withOpacity(0.45)..strokeWidth = 2;
    final grid = Paint()..color = Colors.white.withOpacity(0.06)..strokeWidth = 1;

    // fake waveform bars
    final n = 48;
    for (var i = 0; i < n; i++) {
      final x = i * size.width / n;
      final h = (8 + (i * 17 % 23)).toDouble();
      final isB = i / n > blendStart;
      canvas.drawLine(Offset(x, mid - h / 2), Offset(x, mid + h / 2), isB ? paintB : paintA);
    }

    // transition marker
    final tx = blendStart * size.width;
    canvas.drawLine(Offset(tx, 0), Offset(tx, size.height),
        Paint()..color = Colors.white.withOpacity(0.85)..strokeWidth = 2);

    // playhead
    final px = progress.clamp(0.0, 1.0) * size.width;
    canvas.drawLine(Offset(px, 0), Offset(px, size.height),
        Paint()..color = const Color(0xFF2EE6A6)..strokeWidth = 2);

    // xfade region
    final xf = xfade.clamp(0.02, 0.5) * size.width;
    canvas.drawRect(
      Rect.fromLTWH(tx, 0, xf, size.height),
      Paint()..color = Colors.white.withOpacity(0.06),
    );
  }

  @override
  bool shouldRepaint(covariant _WavePainter old) =>
      old.progress != progress || old.blendStart != blendStart || old.xfade != xfade;
}
