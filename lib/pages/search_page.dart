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
  String? _status;
  double? _progress;
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
    // Debounce short queries; full query on submit is more reliable for downloads
    if (q.trim().length < 3) {
      setState(() {
        _results = [];
        _status = null;
        _progress = null;
      });
      return;
    }
    _debounce = Timer(const Duration(milliseconds: 700), () => _doSearch(q));
  }

  Future<void> _doSearch(String q) async {
    if (q.trim().isEmpty) return;
    setState(() {
      _searching = true;
      _status = t('Buscando… puede tardar si descarga', 'Searching… may take time if downloading');
      _progress = 0.1;
      _results = [];
    });
    try {
      final res = await MusicApiService.search(
        q,
        onStatus: (s, p) {
          if (mounted) setState(() { _status = s; _progress = p; });
        },
      );
      if (mounted) {
        setState(() {
          _results = res;
          if (res.isEmpty) {
            _status = t('Sin resultados. Revisa el túnel en Admin.', 'No results. Check tunnel in Admin.');
          }
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _results = [];
          _status = t('Error: $e', 'Error: $e');
        });
      }
    }
    if (mounted) setState(() => _searching = false);
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
                  setState(() {}); // refresh clear btn
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
                              _status = null;
                              _progress = null;
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
          if (_status != null)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 4),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(_status!, style: TextStyle(color: Colors.white.withOpacity(0.55), fontSize: 12)),
                  if (_progress != null) ...[
                    const SizedBox(height: 6),
                    ClipRRect(
                      borderRadius: BorderRadius.circular(4),
                      child: LinearProgressIndicator(
                        value: _progress!.clamp(0.05, 1.0),
                        backgroundColor: Colors.white12,
                        color: const Color(0xfffc3c44),
                        minHeight: 4,
                      ),
                    ),
                    Text('${((_progress ?? 0) * 100).round()}%',
                        style: TextStyle(color: Colors.white.withOpacity(0.4), fontSize: 11)),
                  ],
                ],
              ),
            ),
          Expanded(
            child: _searching && _results.isEmpty
                ? Center(
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const CircularProgressIndicator(color: Color(0xfffc3c44)),
                        const SizedBox(height: 16),
                        Text(
                          t('Si no está en el catálogo, el servidor la descarga…\nPuede tardar 30–90 s',
                              'If not in catalog, server downloads it…\nCan take 30–90 s'),
                          textAlign: TextAlign.center,
                          style: TextStyle(color: Colors.white.withOpacity(0.45), fontSize: 13),
                        ),
                      ],
                    ),
                  )
                : _results.isEmpty
                    ? Center(
                        child: Padding(
                          padding: const EdgeInsets.all(24),
                          child: Text(
                            t(
                              'Escribe el nombre y pulsa buscar en el teclado.\nEj: Contigo los Panchos\nO pega un link de YouTube.',
                              'Type a name and press search on the keyboard.\nE.g. Contigo los Panchos\nOr paste a YouTube link.',
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
                                        style: const TextStyle(color: Color(0xfffc3c44), fontSize: 10, fontWeight: FontWeight.bold)),
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
