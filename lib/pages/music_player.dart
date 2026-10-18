import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:share_plus/share_plus.dart';
import 'package:pokke/widgets/liquid_glass.dart';
import 'package:pokke/widgets/liquid_sheet.dart';
import 'package:pokke/widgets/karaoke_lyrics.dart';
import 'package:pokke/services/player_controller.dart';
import 'package:pokke/services/lyrics_service.dart';
import 'package:pokke/services/download_service.dart';
import 'package:pokke/services/settings_service.dart';
import 'package:pokke/services/supabase_service.dart';
import 'package:pokke/services/listening_history.dart';
import 'package:pokke/services/follow_service.dart';
import 'package:pokke/services/cast_service.dart';
import 'package:pokke/screens/playlist_detail_screen.dart';
import 'package:pokke/screens/mix_editor_screen.dart';

class MusicPlayer extends StatefulWidget {
  MusicPlayer({super.key});

  @override
  State<MusicPlayer> createState() => _MusicPlayerState();
}

class _MusicPlayerState extends State<MusicPlayer> with TickerProviderStateMixin {
  bool _lyricsMode = false;
  bool _liked = false;
  bool _following = false;
  Map<String, dynamic>? _song;
  String? _lyrics;
  bool _loadingLyrics = false;
  double _dragDy = 0;
  List<Map<String, dynamic>> _queue = [];

  String t(String es, String en) => SettingsService.instance.locale == 'en' ? en : es;

  @override
  void initState() {
    super.initState();
    _loadQueue();
  }

  Future<void> _loadQueue() async {
    final recent = await ListeningHistory.instance.recent(limit: 30);
    if (recent.isEmpty) {
      try {
        _queue = await SupabaseService.getSongs(limit: 30);
      } catch (_) {}
    } else {
      _queue = recent;
    }
    if (mounted) setState(() {});
  }

  @override
  void dispose() {
    super.dispose();
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final args = ModalRoute.of(context)?.settings.arguments;
    if (args is Map<String, dynamic> && _song == null) {
      _song = args;
      final pc = PlayerController.instance;
      final same = pc.current != null &&
          (pc.current!['id'] ?? pc.current!['title']) == (args['id'] ?? args['title']);
      if (same) {
        pc.attachSong(args);
      } else {
        pc.playSong(args);
      }
      _loadLyrics();
      final artistName = args['artist']?.toString() ?? '';
      FollowService.isFollowing(artistName).then((v) {
        if (mounted) setState(() => _following = v);
      });
    }
  }

  Future<void> _loadLyrics() async {
    if (_song == null) return;
    setState(() => _loadingLyrics = true);
    final text = await LyricsService.resolve(
      localText: _song!['lyrics_text']?.toString(),
      title: _song!['title']?.toString() ?? '',
      artist: _song!['artist']?.toString() ?? '',
      album: _song!['album']?.toString(),
      durationSeconds: _song!['duration_seconds'] as int?,
    );
    if (mounted) {
      setState(() {
        _lyrics = text;
        _loadingLyrics = false;
      });
    }
  }

  String _fmt(Duration d) {
    final m = d.inMinutes.remainder(60).toString();
    final s = d.inSeconds.remainder(60).toString().padLeft(2, '0');
    return '$m:$s';
  }

  void _share() {
    _openLiquidShare();
  }

  void _cast() async {
    final url = _song?['audio_url']?.toString() ?? '';
    final title = _song?['title']?.toString() ?? '';
    final artist = _song?['artist']?.toString() ?? '';
    final cover = _song?['cover_url']?.toString();
    final devices = await CastService.devices();
    showLiquidSheet(
      context: context,
      child: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(width: 36, height: 4, decoration: BoxDecoration(color: Colors.white24, borderRadius: BorderRadius.circular(2))),
              const SizedBox(height: 16),
              Text(t('Transmitir', 'Cast'),
                  style: const TextStyle(color: Colors.white, fontSize: 18, fontWeight: FontWeight.bold)),
              const SizedBox(height: 12),
              if (devices.isEmpty)
                Text(
                  t(
                    'Busca Chromecast / Android TV en la red. Si no aparece, usa el selector del sistema.',
                    'Looking for Chromecast / Android TV. If none, use the system picker.',
                  ),
                  style: TextStyle(color: Colors.white.withOpacity(0.6), height: 1.4),
                  textAlign: TextAlign.center,
                )
              else
                ...devices.map((d) => ListTile(
                      leading: const Icon(Icons.tv, color: Colors.white70),
                      title: Text(d['name'] ?? 'Device', style: const TextStyle(color: Colors.white)),
                      onTap: () async {
                        Navigator.pop(context);
                        final ok = await CastService.castUrl(
                          url: url, title: title, artist: artist, imageUrl: cover,
                        );
                        if (!mounted) return;
                        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
                          content: Text(ok
                              ? t('Transmitiendo…', 'Casting…')
                              : t('No se pudo conectar', 'Could not connect')),
                        ));
                      },
                    )),
              ListTile(
                leading: const Icon(Icons.cast_connected, color: Colors.white70),
                title: Text(t('Selector del sistema', 'System cast picker'),
                    style: const TextStyle(color: Colors.white)),
                onTap: () {
                  Navigator.pop(context);
                  CastService.openSystemCastPicker();
                },
              ),
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _showDownloadSheet() async {
    final url = _song?['audio_url']?.toString();
    if (url == null || url.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(t('No hay audio para descargar', 'No audio to download'))),
      );
      return;
    }
    showLiquidSheet(
      context: context,
      child: SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const SizedBox(height: 12),
            Text(t('Descargar', 'Download'),
                style: const TextStyle(color: Colors.white, fontSize: 18, fontWeight: FontWeight.bold)),
            ListTile(
              leading: const Icon(Icons.phone_android, color: Colors.white70),
              title: Text(t('Dentro de la app (offline)', 'In-app (offline)'),
                  style: const TextStyle(color: Colors.white)),
              subtitle: Text(t('Solo en pokke', 'Only in pokke'),
                  style: TextStyle(color: Colors.white.withOpacity(0.45))),
              onTap: () async {
                Navigator.pop(context);
                await _doDownload(inApp: true);
              },
            ),
            ListTile(
              leading: const Icon(Icons.folder, color: Colors.white70),
              title: Text(t('Almacenamiento (Music/pokke)', 'Device storage (Music/pokke)'),
                  style: const TextStyle(color: Colors.white)),
              subtitle: Text(t('Carpeta Music del teléfono', 'Phone Music folder'),
                  style: TextStyle(color: Colors.white.withOpacity(0.45))),
              onTap: () async {
                Navigator.pop(context);
                await _doDownload(inApp: false);
              },
            ),
            const SizedBox(height: 12),
          ],
        ),
      ),
    );
  }

  Future<void> _doDownload({required bool inApp}) async {
    final url = _song!['audio_url'].toString();
    final id = (_song!['id'] ?? _song!['title']).toString();
    final title = _song!['title']?.toString() ?? 'track';
    final artist = _song!['artist']?.toString() ?? '';
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (_) => AlertDialog(
        backgroundColor: const Color(0xFF1A1A1E),
        content: Row(
          children: [
            const CircularProgressIndicator(color: Color(0xfffc3c44)),
            const SizedBox(width: 16),
            Expanded(child: Text(t('Descargando…', 'Downloading…'), style: const TextStyle(color: Colors.white))),
          ],
        ),
      ),
    );
    try {
      if (inApp) {
        await DownloadService.instance.downloadInApp(id: id, title: title, artist: artist, url: url);
      } else {
        await DownloadService.instance.downloadToDeviceMusic(title: title, artist: artist, url: url);
      }
      if (mounted) {
        Navigator.pop(context);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(t('Descarga lista', 'Download complete'))),
        );
      }
    } catch (e) {
      if (mounted) {
        Navigator.pop(context);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(t('Error al descargar', 'Download failed'))),
        );
      }
    }
  }

  void _showMoreMenu() {
    showLiquidSheet(
      context: context,
      child: SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              leading: Icon(_liked ? Icons.favorite : Icons.favorite_border, color: const Color(0xfffc3c44)),
              title: Text(t('Agregar a favoritos', 'Add to favorites'), style: const TextStyle(color: Colors.white)),
              onTap: () {
                setState(() => _liked = !_liked);
                Navigator.pop(context);
              },
            ),
            ListTile(
              leading: const Icon(Icons.playlist_add, color: Colors.white70),
              title: Text(t('Agregar a playlist', 'Add to playlist'), style: const TextStyle(color: Colors.white)),
              onTap: () {
                Navigator.pop(context);
                final song = _song ?? PlayerController.instance.current;
                if (song != null) {
                  PlaylistActions.showAddToPlaylistSheet(context, song: song);
                }
              },
            ),
            ListTile(
              leading: const Icon(Icons.download_rounded, color: Colors.white70),
              title: Text(t('Descargar', 'Download'), style: const TextStyle(color: Colors.white)),
              onTap: () {
                Navigator.pop(context);
                _showDownloadSheet();
              },
            ),
            ListTile(
              leading: const Icon(Icons.merge_type, color: Colors.white70),
              title: Text(t('Combinar con otra canción', 'Crossfade with another track'),
                  style: const TextStyle(color: Colors.white)),
              subtitle: Text(t('Elige el minuto de mezcla', 'Pick blend start time'),
                  style: TextStyle(color: Colors.white.withOpacity(0.45), fontSize: 12)),
              onTap: () {
                Navigator.pop(context);
                _showCrossfade();
              },
            ),
            ListTile(
              leading: const Icon(Icons.share, color: Colors.white70),
              title: Text(t('Compartir', 'Share'), style: const TextStyle(color: Colors.white)),
              onTap: () {
                Navigator.pop(context);
                _share();
              },
            ),
            const SizedBox(height: 8),
          ],
        ),
      ),
    );
  }

  void _showCrossfade() {
    final s = _song;
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => MixEditorScreen(
          initialA: s == null ? null : Map<String, dynamic>.from(s),
        ),
      ),
    );
  }

  void _showQueue() {
    showLiquidSheet(
      context: context,
      maxHeightFactor: 0.6,
      child: SafeArea(
        child: SizedBox(
          height: 360,
          child: Column(
            children: [
              Padding(
                padding: const EdgeInsets.all(16),
                child: Text(t('Cola', 'Queue'),
                    style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 18)),
              ),
              Expanded(
                child: ListView.builder(
                  itemCount: _queue.length,
                  itemBuilder: (_, i) {
                    final s = _queue[i];
                    return ListTile(
                      title: Text(s['title'] ?? '', style: const TextStyle(color: Colors.white)),
                      subtitle: Text(s['artist'] ?? '', style: TextStyle(color: Colors.white.withOpacity(0.5))),
                      onTap: () {
                        Navigator.pop(context);
                        PlayerController.instance.playSong(s, forceRestart: true);
                        setState(() => _song = s);
                        _loadLyrics();
                      },
                    );
                  },
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  void _prev() {
    if (_queue.isEmpty || _song == null) {
      PlayerController.instance.seek(Duration.zero);
      return;
    }
    final key = (_song!['id'] ?? _song!['title']).toString();
    final idx = _queue.indexWhere((s) => (s['id'] ?? s['title']).toString() == key);
    if (idx > 0) {
      final prev = _queue[idx - 1];
      PlayerController.instance.playSong(prev, forceRestart: true);
      setState(() => _song = prev);
      _loadLyrics();
    } else {
      PlayerController.instance.seek(Duration.zero);
    }
  }

  void _next() {
    if (_queue.isEmpty || _song == null) return;
    final key = (_song!['id'] ?? _song!['title']).toString();
    final idx = _queue.indexWhere((s) => (s['id'] ?? s['title']).toString() == key);
    if (idx >= 0 && idx < _queue.length - 1) {
      final next = _queue[idx + 1];
      PlayerController.instance.playSong(next, forceRestart: true);
      setState(() => _song = next);
      _loadLyrics();
      final artistName = next['artist']?.toString() ?? '';
      FollowService.isFollowing(artistName).then((v) {
        if (mounted) setState(() => _following = v);
      });
    }
  }

  void _onVerticalDragUpdate(DragUpdateDetails d) {
    final dy = d.primaryDelta ?? 0;
    // Only track downward drag. Upward while at top is ignored (no bounce).
    if (dy > 0 || _dragDy > 0) {
      setState(() => _dragDy = (_dragDy + dy).clamp(0.0, 480.0));
    }
  }

  Future<void> _onVerticalDragEnd(DragEndDetails d) async {
    final v = d.primaryVelocity ?? 0;
    final h = MediaQuery.of(context).size.height;
    final shouldClose = _dragDy > h * 0.18 || v > 900;
    if (shouldClose) {
      // Animate pure slide-down off screen, then pop (no spring / bounce).
      final start = _dragDy;
      final frames = SettingsService.instance.reduceAnimations ? 8 : 14;
      for (var i = 1; i <= frames; i++) {
        if (!mounted) return;
        setState(() => _dragDy = start + (h - start) * (i / frames));
        await Future.delayed(const Duration(milliseconds: 12));
      }
      if (mounted) Navigator.of(context).maybePop();
    } else {
      // Smooth ease back — not elastic
      final start = _dragDy;
      final frames = SettingsService.instance.reduceAnimations ? 6 : 12;
      for (var i = 1; i <= frames; i++) {
        if (!mounted) return;
        final t = i / frames;
        final eased = Curves.easeOutCubic.transform(t);
        setState(() => _dragDy = start * (1 - eased));
        await Future.delayed(const Duration(milliseconds: 12));
      }
      if (mounted) setState(() => _dragDy = 0);
    }
  }

  Widget _liquidSlider(PlayerController pc, Duration dur, double progress) {
    final s = SettingsService.instance;
    final style = s.sliderStyle; // minimal | liquid | bold | timeline | hidden
    final thumb = s.sliderThumb; // circle | line | hidden

    double trackH = 4;
    double thumbR = 7;
    Color active = Colors.white;
    Color inactive = Colors.white24;
    Color thumbC = Colors.white;

    switch (style) {
      case 'minimal':
        trackH = 2.5;
        thumbR = 5;
        break;
      case 'bold':
        trackH = 8;
        thumbR = 11;
        break;
      case 'liquid':
        trackH = 5;
        thumbR = 8;
        active = Colors.white.withOpacity(0.95);
        inactive = Colors.white.withOpacity(0.18);
        break;
      case 'timeline':
        trackH = 3.5;
        thumbR = 6;
        break;
      case 'hidden':
        trackH = 2;
        thumbR = thumb == 'hidden' ? 0 : 4;
        active = Colors.white.withOpacity(0.35);
        inactive = Colors.white.withOpacity(0.08);
        break;
    }

    SliderComponentShape thumbShape;
    if (thumb == 'hidden' || style == 'hidden') {
      thumbShape = const RoundSliderThumbShape(enabledThumbRadius: 0.01);
    } else if (thumb == 'line') {
      thumbShape = const RoundSliderThumbShape(enabledThumbRadius: 2, elevation: 0);
    } else {
      thumbShape = RoundSliderThumbShape(enabledThumbRadius: thumbR);
    }

    Widget slider = SliderTheme(
      data: SliderTheme.of(context).copyWith(
        trackHeight: trackH,
        thumbShape: thumbShape,
        overlayShape: RoundSliderOverlayShape(overlayRadius: thumbR + 8),
        activeTrackColor: active,
        inactiveTrackColor: inactive,
        thumbColor: thumbC,
        overlayColor: Colors.white24,
      ),
      child: Slider(
        value: progress,
        onChanged: (v) {
          pc.seek(Duration(milliseconds: (v * dur.inMilliseconds).round()));
        },
      ),
    );

    // Liquid: soft glow under track
    if (style == 'liquid' && s.liquidGlassEnabled) {
      slider = Stack(
        alignment: Alignment.center,
        children: [
          Positioned.fill(
            child: IgnorePointer(
              child: Container(
                margin: const EdgeInsets.symmetric(horizontal: 12, vertical: 14),
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(99),
                  boxShadow: [
                    BoxShadow(
                      color: Colors.white.withOpacity(0.12),
                      blurRadius: 12,
                      spreadRadius: 0.5,
                    ),
                  ],
                ),
              ),
            ),
          ),
          slider,
        ],
      );
    }

    // Timeline marks every 30s
    if (style == 'timeline' && dur.inSeconds > 30) {
      final marks = <Widget>[];
      final total = dur.inSeconds;
      for (var sec = 30; sec < total; sec += 30) {
        final frac = sec / total;
        marks.add(Positioned(
          left: frac * (MediaQuery.of(context).size.width - 48),
          top: 0,
          bottom: 0,
          child: Container(width: 1, color: Colors.white24),
        ));
      }
      slider = SizedBox(
        height: 36,
        child: Stack(alignment: Alignment.center, children: [...marks, slider]),
      );
    }

    return slider;
  }

  Widget _timeRow(Duration pos, Duration dur) {
    final mode = SettingsService.instance.timeDisplay;
    if (mode == 'hidden') return const SizedBox.shrink();
    final left = _fmt(pos);
    final right = _fmt(dur);
    if (mode == 'center') {
      return Padding(
        padding: const EdgeInsets.only(top: 4),
        child: Center(
          child: Text('$left / $right',
              style: TextStyle(color: Colors.white.withOpacity(0.55), fontSize: 12)),
        ),
      );
    }
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 4),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(left, style: TextStyle(color: Colors.white.withOpacity(0.55), fontSize: 12)),
          Text(right, style: TextStyle(color: Colors.white.withOpacity(0.55), fontSize: 12)),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final pc = PlayerController.instance;
    final title = _song?['title'] ?? pc.current?['title'] ?? 'Unknown';
    final artist = _song?['artist'] ?? pc.current?['artist'] ?? 'Unknown';
    final cover = _song?['cover_url'] ?? _song?['animated_cover_url'] ?? pc.current?['cover_url'];

    return AnimatedBuilder(
      animation: pc,
      builder: (context, _) {
        final pos = pc.position;
        final dur = pc.duration.inMilliseconds > 0 ? pc.duration : const Duration(seconds: 1);
        final progress = (pos.inMilliseconds / dur.inMilliseconds).clamp(0.0, 1.0);

        if (_lyricsMode) {
          return Scaffold(
            backgroundColor: Colors.black,
            body: Stack(
              fit: StackFit.expand,
              children: [
                if (cover != null)
                  Image.network(cover.toString(), fit: BoxFit.cover,
                      errorBuilder: (_, __, ___) => const SizedBox()),
                Container(color: Colors.black.withOpacity(0.72)),
                SafeArea(
                  child: Column(
                    children: [
                      Padding(
                        padding: const EdgeInsets.fromLTRB(8, 4, 8, 0),
                        child: Row(
                          children: [
                            IconButton(
                              onPressed: () => setState(() => _lyricsMode = false),
                              icon: const Icon(Icons.close, color: Colors.white, size: 28),
                            ),
                            Expanded(
                              child: Column(
                                children: [
                                  Text(title, maxLines: 1, overflow: TextOverflow.ellipsis,
                                      style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w700)),
                                  Text(artist, style: TextStyle(color: Colors.white.withOpacity(0.55), fontSize: 13)),
                                ],
                              ),
                            ),
                            const SizedBox(width: 48),
                          ],
                        ),
                      ),
                      Expanded(
                        child: _loadingLyrics
                            ? const Center(child: CircularProgressIndicator(color: Colors.white54))
                            : KaraokeLyrics(
                                lrcText: _lyrics ?? t('No se encontraron letras', 'No lyrics found'),
                                position: pos,
                              ),
                      ),
                      Padding(
                        padding: const EdgeInsets.fromLTRB(20, 0, 20, 4),
                        child: _liquidSlider(pc, dur, progress),
                      ),
                      Padding(
                        padding: const EdgeInsets.only(bottom: 20, top: 4),
                        child: Row(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            IconButton(iconSize: 36, onPressed: _prev,
                                icon: const Icon(Icons.skip_previous_rounded, color: Colors.white)),
                            const SizedBox(width: 16),
                            GestureDetector(
                              onTap: pc.toggle,
                              child: Container(
                                width: 64, height: 64,
                                decoration: const BoxDecoration(color: Colors.white, shape: BoxShape.circle),
                                child: Icon(pc.isPlaying ? Icons.pause_rounded : Icons.play_arrow_rounded,
                                    size: 38, color: Colors.black),
                              ),
                            ),
                            const SizedBox(width: 16),
                            IconButton(iconSize: 36, onPressed: _next,
                                icon: const Icon(Icons.skip_next_rounded, color: Colors.white)),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          );
        }

        final h = MediaQuery.of(context).size.height;
        final fade = (1 - (_dragDy / (h * 0.55))).clamp(0.0, 1.0);
        return Opacity(
          opacity: fade,
          child: Transform.translate(
          offset: Offset(0, _dragDy),
          child: GestureDetector(
            onVerticalDragUpdate: _onVerticalDragUpdate,
            onVerticalDragEnd: _onVerticalDragEnd,
            child: Scaffold(
              backgroundColor: const Color(0xFF0A0A0C),
              body: Stack(
                fit: StackFit.expand,
                children: [
                  if (cover != null)
                    Image.network(cover.toString(), fit: BoxFit.cover,
                        errorBuilder: (_, __, ___) => const SizedBox()),
                  Container(
                    decoration: BoxDecoration(
                      gradient: LinearGradient(
                        begin: Alignment.topCenter,
                        end: Alignment.bottomCenter,
                        colors: [
                          Colors.black.withOpacity(0.35),
                          Colors.black.withOpacity(0.8),
                          const Color(0xFF0A0A0C),
                        ],
                      ),
                    ),
                  ),
                  SafeArea(
                    child: Column(
                      children: [
                        const SizedBox(height: 6),
                        // Blue handle area — drag down to dismiss
                        GestureDetector(
                          onVerticalDragUpdate: _onVerticalDragUpdate,
                          onVerticalDragEnd: _onVerticalDragEnd,
                          behavior: HitTestBehavior.opaque,
                          child: Container(
                            width: 40, height: 20,
                            alignment: Alignment.center,
                            child: Container(
                              width: 36, height: 4,
                              decoration: BoxDecoration(
                                color: Colors.white38,
                                borderRadius: BorderRadius.circular(2),
                              ),
                            ),
                          ),
                        ),
                        Padding(
                          padding: const EdgeInsets.fromLTRB(4, 0, 4, 0),
                          child: Row(
                            children: [
                              // White chevron — also dismiss
                              IconButton(
                                onPressed: () => Navigator.maybePop(context),
                                icon: const Icon(Icons.keyboard_arrow_down_rounded, color: Colors.white, size: 32),
                              ),
                              const Spacer(),
                              IconButton(onPressed: _share, icon: const Icon(Icons.ios_share_rounded, color: Colors.white70)),
                              IconButton(onPressed: _cast, icon: const Icon(Icons.cast, color: Colors.white70)),
                              IconButton(onPressed: _showMoreMenu, icon: const Icon(Icons.more_horiz, color: Colors.white70)),
                            ],
                          ),
                        ),
                        Expanded(
                          child: Padding(
                            padding: const EdgeInsets.symmetric(horizontal: 36, vertical: 8),
                            child: AspectRatio(
                              aspectRatio: 1,
                              child: ClipRRect(
                                borderRadius: BorderRadius.circular(16),
                                child: cover != null
                                    ? Image.network(cover.toString(), fit: BoxFit.cover,
                                        errorBuilder: (_, __, ___) => Container(
                                            color: Colors.white12,
                                            child: const Icon(Icons.music_note, size: 72, color: Colors.white24)))
                                    : Container(
                                        color: Colors.white12,
                                        child: const Icon(Icons.music_note, size: 72, color: Colors.white24)),
                              ),
                            ),
                          ),
                        ),
                        Padding(
                          padding: const EdgeInsets.fromLTRB(28, 8, 28, 0),
                          child: Row(
                            children: [
                              Expanded(
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    Text(title, maxLines: 1, overflow: TextOverflow.ellipsis,
                                        style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w700, color: Colors.white)),
                                    const SizedBox(height: 3),
                                    Text(artist, style: TextStyle(fontSize: 15, color: Colors.white.withOpacity(0.65))),
                                  ],
                                ),
                              ),
                              IconButton(
                                onPressed: () => setState(() => _liked = !_liked),
                                icon: Icon(_liked ? Icons.favorite : Icons.favorite_border,
                                    color: _liked ? const Color(0xfffc3c44) : Colors.white70),
                              ),
                            ],
                          ),
                        ),
                        Padding(
                          padding: const EdgeInsets.fromLTRB(20, 12, 20, 0),
                          child: LiquidGlass(
                            blur: 14,
                            opacity: 0.07,
                            borderRadius: BorderRadius.circular(24),
                            borderWidth: 0.5,
                            child: Padding(
                              padding: const EdgeInsets.symmetric(horizontal: 4),
                              child: _liquidSlider(pc, dur, progress),
                            ),
                          ),
                        ),
                        Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 28, vertical: 2),
                          child: _timeRow(pos, dur),
                        ),
                        Padding(
                          padding: const EdgeInsets.symmetric(vertical: 6),
                          child: Row(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              IconButton(iconSize: 38, onPressed: _prev,
                                  icon: const Icon(Icons.skip_previous_rounded, color: Colors.white)),
                              const SizedBox(width: 18),
                              GestureDetector(
                                onTap: pc.toggle,
                                child: Container(
                                  width: 70, height: 70,
                                  decoration: const BoxDecoration(color: Colors.white, shape: BoxShape.circle),
                                  child: Icon(pc.isPlaying ? Icons.pause_rounded : Icons.play_arrow_rounded,
                                      size: 42, color: Colors.black),
                                ),
                              ),
                              const SizedBox(width: 18),
                              IconButton(iconSize: 38, onPressed: _next,
                                  icon: const Icon(Icons.skip_next_rounded, color: Colors.white)),
                            ],
                          ),
                        ),
                        Padding(
                          padding: const EdgeInsets.fromLTRB(24, 4, 24, 0),
                          child: LiquidGlass(
                            blur: 16,
                            opacity: 0.08,
                            borderRadius: BorderRadius.circular(16),
                            child: Padding(
                              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                              child: Row(
                                children: [
                                  CircleAvatar(
                                    radius: 22,
                                    backgroundColor: Colors.white12,
                                    backgroundImage: cover != null ? NetworkImage(cover.toString()) : null,
                                    child: cover == null ? const Icon(Icons.person, color: Colors.white38) : null,
                                  ),
                                  const SizedBox(width: 12),
                                  Expanded(
                                    child: Column(
                                      crossAxisAlignment: CrossAxisAlignment.start,
                                      children: [
                                        Text(artist, style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w600)),
                                        Text(t('Artista', 'Artist'),
                                            style: TextStyle(color: Colors.white.withOpacity(0.45), fontSize: 12)),
                                      ],
                                    ),
                                  ),
                                  TextButton(
                                    onPressed: () async {
                                      final name = artist;
                                      final img = cover?.toString();
                                      final v = await FollowService.toggle(name, imageUrl: img);
                                      if (mounted) setState(() => _following = v);
                                    },
                                    style: TextButton.styleFrom(
                                      backgroundColor: _following ? Colors.white12 : const Color(0xfffc3c44),
                                      foregroundColor: Colors.white,
                                      padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 8),
                                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
                                    ),
                                    child: Text(_following ? t('Siguiendo', 'Following') : t('Seguir', 'Follow'),
                                        style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 13)),
                                  ),
                                ],
                              ),
                            ),
                          ),
                        ),
                        Padding(
                          padding: const EdgeInsets.fromLTRB(28, 14, 28, 16),
                          child: Row(
                            mainAxisAlignment: MainAxisAlignment.spaceAround,
                            children: [
                              IconButton(
                                onPressed: () => setState(() => _lyricsMode = true),
                                icon: const Icon(Icons.lyrics_outlined, color: Colors.white70, size: 26),
                              ),
                              IconButton(
                                onPressed: _cast,
                                icon: const Icon(Icons.cast_connected, color: Colors.white70, size: 26),
                              ),
                              IconButton(
                                onPressed: _showQueue,
                                icon: const Icon(Icons.queue_music_rounded, color: Colors.white70, size: 26),
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
          ),
        );
      },
    );
  }

  void _openLiquidShare() {
    final s = PlayerController.instance.currentSong;
    if (s == null) return;
    final title = (s['title'] ?? '').toString();
    final artist = (s['artist'] ?? '').toString();
    final id = (s['id'] ?? '').toString();
    final link = 'https://pokke.app/song/$id';
    final text = SettingsService.instance.locale == 'en'
        ? 'Listen to "$title" by $artist on pokke\n$link'
        : 'Escucha "$title" de $artist en pokke\n$link';
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      builder: (ctx) => LiquidGlass(
        blur: 28,
        opacity: 0.16,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(22)),
        child: SafeArea(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 20),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Center(
                  child: Container(
                    width: 40, height: 4,
                    decoration: BoxDecoration(color: Colors.white24, borderRadius: BorderRadius.circular(2)),
                  ),
                ),
                const SizedBox(height: 16),
                Text(SettingsService.instance.locale == 'en' ? 'Share' : 'Compartir',
                    style: const TextStyle(color: Colors.white, fontSize: 18, fontWeight: FontWeight.w700)),
                const SizedBox(height: 8),
                Text(text, style: TextStyle(color: Colors.white.withOpacity(0.7), fontSize: 13)),
                const SizedBox(height: 16),
                ListTile(
                  leading: const Icon(Icons.copy, color: Colors.white70),
                  title: Text(SettingsService.instance.locale == 'en' ? 'Copy link' : 'Copiar enlace',
                      style: const TextStyle(color: Colors.white)),
                  onTap: () {
                    Clipboard.setData(ClipboardData(text: link));
                    Navigator.pop(ctx);
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(content: Text(SettingsService.instance.locale == 'en' ? 'Copied' : 'Copiado')),
                    );
                  },
                ),
                ListTile(
                  leading: const Icon(Icons.ios_share, color: Colors.white70),
                  title: Text(SettingsService.instance.locale == 'en' ? 'More apps…' : 'Más apps…',
                      style: const TextStyle(color: Colors.white)),
                  onTap: () {
                    Navigator.pop(ctx);
                    Share.share(text);
                  },
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

}
