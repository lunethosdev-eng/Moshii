import 'dart:async';
import 'package:flutter/material.dart';
import 'package:pokke/widgets/liquid_glass.dart';
import 'package:pokke/widgets/compact_header.dart';
import 'package:pokke/services/music_api_service.dart';
import 'package:pokke/services/settings_service.dart';

class SearchPage extends StatefulWidget {
  SearchPage({super.key});

  @override
  State<SearchPage> createState() => _SearchPageState();
}

class _SearchPageState extends State<SearchPage> {
  final _searchCtrl = TextEditingController();
  final _scrollCtrl = ScrollController();
  final _focus = FocusNode();
  List<Map<String, dynamic>> _results = [];
  bool _searching = false;
  bool _collapsed = false;
  DownloadProgress? _dl;
  Timer? _debounce;

  String t(String es, String en) => SettingsService.instance.locale == 'en' ? en : es;

  @override
  void initState() {
    super.initState();
    _scrollCtrl.addListener(() {
      final c = _scrollCtrl.offset > 40;
      if (c != _collapsed) setState(() => _collapsed = c);
    });
  }

  @override
  void dispose() {
    _debounce?.cancel();
    _searchCtrl.dispose();
    _scrollCtrl.dispose();
    _focus.dispose();
    super.dispose();
  }

  void _onChanged(String q) {
    _debounce?.cancel();
    if (q.trim().length < 3) {
      setState(() {
        _results = [];
        _dl = null;
      });
      return;
    }
    _debounce = Timer(const Duration(milliseconds: 800), () => _doSearch(q));
  }

  Future<void> _doSearch(String q) async {
    if (q.trim().isEmpty) return;
    setState(() {
      _searching = true;
      _dl = DownloadProgress(
        status: t('Buscando…', 'Searching…'),
        progress: 0.05,
        title: q,
      );
      _results = [];
    });
    try {
      final res = await MusicApiService.search(
        q,
        onProgress: (p) {
          if (!mounted) return;
          setState(() {
            // Keep query as title fallback until server sends one
            _dl = DownloadProgress(
              status: p.status,
              progress: p.progress,
              youtubeId: p.youtubeId,
              title: p.title ?? _dl?.title ?? q,
              artist: p.artist ?? _dl?.artist,
              coverUrl: p.coverUrl ?? _dl?.coverUrl,
            );
          });
        },
      );
      if (mounted) {
        setState(() {
          _results = res;
          if (res.isEmpty) {
            _dl = DownloadProgress(
              status: t('Sin resultados. Revisa el túnel en Admin.', 'No results. Check tunnel in Admin.'),
            );
          } else {
            _dl = null; // hide card when we have results
          }
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _results = [];
          _dl = DownloadProgress(status: t('Error: $e', 'Error: $e'));
        });
      }
    }
    if (mounted) setState(() => _searching = false);
  }

  Widget _downloadCard() {
    final p = _dl;
    if (p == null) return const SizedBox.shrink();
    final pct = p.progress;
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 4, 16, 8),
      child: LiquidGlass(
        blur: 20,
        opacity: 0.12,
        borderRadius: BorderRadius.circular(16),
        enableSpecular: true,
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Row(
            children: [
              ClipRRect(
                borderRadius: BorderRadius.circular(10),
                child: SizedBox(
                  width: 56,
                  height: 56,
                  child: p.coverUrl != null
                      ? Image.network(
                          p.coverUrl!,
                          fit: BoxFit.cover,
                          errorBuilder: (_, __, ___) => Container(
                            color: Colors.white12,
                            child: const Icon(Icons.music_note, color: Colors.white38),
                          ),
                        )
                      : Container(
                          color: Colors.white12,
                          child: const Icon(Icons.download_rounded, color: Colors.white38),
                        ),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      p.title ?? t('Descargando…', 'Downloading…'),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w700, fontSize: 14),
                    ),
                    if (p.artist != null)
                      Text(
                        p.artist!,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(color: Colors.white.withOpacity(0.5), fontSize: 12),
                      ),
                    const SizedBox(height: 6),
                    ClipRRect(
                      borderRadius: BorderRadius.circular(4),
                      child: LinearProgressIndicator(
                        value: pct?.clamp(0.02, 1.0),
                        backgroundColor: Colors.white12,
                        color: const Color(0xfffc3c44),
                        minHeight: 5,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      pct != null
                          ? '${(pct * 100).toStringAsFixed(1)}% · ${p.status}'
                          : p.status,
                      style: TextStyle(color: Colors.white.withOpacity(0.45), fontSize: 11),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF0C0C0E),
      appBar: CompactHeader(title: t('Buscar', 'Search'), collapsed: _collapsed),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 6, 16, 8),
            child: LiquidGlass(
              blur: 22,
              opacity: 0.10,
              borderRadius: BorderRadius.circular(16),
              borderWidth: 0.7,
              enableSpecular: true,
              child: TextField(
                controller: _searchCtrl,
                focusNode: _focus,
                style: const TextStyle(color: Colors.white, fontSize: 16),
                cursorColor: const Color(0xfffc3c44),
                textInputAction: TextInputAction.search,
                onChanged: (v) {
                  setState(() {});
                  _onChanged(v);
                },
                onSubmitted: _doSearch,
                decoration: InputDecoration(
                  hintText: t('Canción, artista o link de YouTube…', 'Song, artist or YouTube link…'),
                  hintStyle: TextStyle(color: Colors.white.withOpacity(0.35)),
                  prefixIcon: Icon(Icons.search, color: Colors.white.withOpacity(0.5)),
                  suffixIcon: _searchCtrl.text.isNotEmpty
                      ? IconButton(
                          icon: const Icon(Icons.clear, color: Colors.white54, size: 20),
                          onPressed: () {
                            _searchCtrl.clear();
                            setState(() {
                              _results = [];
                              _dl = null;
                            });
                          },
                        )
                      : null,
                  border: InputBorder.none,
                  contentPadding: const EdgeInsets.symmetric(horizontal: 8, vertical: 14),
                ),
              ),
            ),
          ),
          if (_dl != null) _downloadCard(),
          Expanded(
            child: _searching && _results.isEmpty
                ? Center(
                    child: Text(
                      t('Esperando al servidor…', 'Waiting for server…'),
                      style: TextStyle(color: Colors.white.withOpacity(0.4)),
                    ),
                  )
                : _results.isEmpty
                    ? Center(
                        child: Padding(
                          padding: const EdgeInsets.all(24),
                          child: Text(
                            t(
                              'Escribe y pulsa buscar.\nSi no está en el catálogo se descarga con % real.',
                              'Type and press search.\nIf missing from catalog it downloads with real %.',
                            ),
                            style: TextStyle(color: Colors.white.withOpacity(0.4), height: 1.4),
                            textAlign: TextAlign.center,
                          ),
                        ),
                      )
                    : ListView.builder(
                        controller: _scrollCtrl,
                        padding: const EdgeInsets.fromLTRB(12, 4, 12, 120),
                        itemCount: _results.length,
                        itemBuilder: (ctx, i) {
                          final s = _results[i];
                          final cover = s['cover_url'];
                          final justDl = s['_just_downloaded'] == true;
                          return ListTile(
                            leading: ClipRRect(
                              borderRadius: BorderRadius.circular(8),
                              child: cover != null
                                  ? Image.network(cover.toString(), width: 50, height: 50, fit: BoxFit.cover,
                                      errorBuilder: (_, __, ___) =>
                                          Container(width: 50, height: 50, color: Colors.white12))
                                  : Container(width: 50, height: 50, color: Colors.white12,
                                      child: const Icon(Icons.music_note, color: Colors.white24)),
                            ),
                            title: Row(
                              children: [
                                Expanded(
                                  child: Text(s['title'] ?? '',
                                      style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w600)),
                                ),
                                if (justDl)
                                  Container(
                                    margin: const EdgeInsets.only(left: 6),
                                    padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                    decoration: BoxDecoration(
                                      color: const Color(0xfffc3c44).withOpacity(0.2),
                                      borderRadius: BorderRadius.circular(6),
                                    ),
                                    child: Text(t('NUEVA', 'NEW'),
                                        style: const TextStyle(
                                            color: Color(0xfffc3c44), fontSize: 10, fontWeight: FontWeight.bold)),
                                  ),
                              ],
                            ),
                            subtitle: Text(s['artist'] ?? '',
                                style: TextStyle(color: Colors.white.withOpacity(0.55))),
                            onTap: () => Navigator.pushNamed(context, '/player', arguments: s),
                          );
                        },
                      ),
          ),
        ],
      ),
    );
  }
}
