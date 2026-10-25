import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:webview_flutter/webview_flutter.dart';
import 'package:pokke/services/spotify_web_import_service.dart';
import 'package:pokke/services/settings_service.dart';

/// In-app browser: user logs into Spotify, opens a playlist, we extract tracks.
class SpotifyWebImportScreen extends StatefulWidget {
  const SpotifyWebImportScreen({super.key});

  @override
  State<SpotifyWebImportScreen> createState() => _SpotifyWebImportScreenState();
}

class _SpotifyWebImportScreenState extends State<SpotifyWebImportScreen> {
  late final WebViewController _ctrl;
  bool _loading = true;
  String _url = 'https://accounts.spotify.com/login';
  String? _hint;
  bool _extracting = false;

  String t(String es, String en) =>
      SettingsService.instance.locale == 'en' ? en : es;

  /// JS that tries to read playlist name, cover and track rows from open.spotify.com DOM.
  static const _extractJs = r'''
(function() {
  try {
    var name = '';
    var cover = '';
    var h1 = document.querySelector('h1');
    if (h1) name = h1.innerText.trim();
    var img = document.querySelector('img[data-testid="entity-image"], img[alt*="cover"], img[alt*="Cover"], div[data-testid="playlist-image"] img');
    if (img) cover = img.src || '';
    var tracks = [];
    var rows = document.querySelectorAll('[data-testid="tracklist-row"], [role="row"]');
    rows.forEach(function(row) {
      var titleEl = row.querySelector('[data-testid="internal-track-link"], a[href*="/track/"]');
      var artistEl = row.querySelector('a[href*="/artist/"], span[class*="artist"]');
      var title = titleEl ? titleEl.innerText.trim() : '';
      var artist = artistEl ? artistEl.innerText.trim() : '';
      if (!title) {
        var spans = row.querySelectorAll('span, a');
        if (spans.length >= 1) title = spans[0].innerText.trim();
        if (spans.length >= 2 && !artist) artist = spans[1].innerText.trim();
      }
      if (title && title.length > 0 && title.length < 120) {
        tracks.push({ title: title, artist: artist || '' });
      }
    });
    // de-dupe
    var seen = {};
    tracks = tracks.filter(function(t) {
      var k = (t.title + '|' + t.artist).toLowerCase();
      if (seen[k]) return false;
      seen[k] = 1;
      return true;
    });
    return JSON.stringify({
      name: name || document.title || 'Playlist',
      cover: cover,
      url: location.href,
      tracks: tracks
    });
  } catch (e) {
    return JSON.stringify({ error: String(e), name: '', tracks: [] });
  }
})();
''';

  @override
  void initState() {
    super.initState();
    _ctrl = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..setBackgroundColor(const Color(0xFF121212))
      ..setNavigationDelegate(NavigationDelegate(
        onPageStarted: (u) => setState(() {
          _loading = true;
          _url = u;
        }),
        onPageFinished: (u) => setState(() {
          _loading = false;
          _url = u;
          if (u.contains('open.spotify.com/playlist')) {
            _hint = t(
              'Playlist abierta · toca “Extraer esta playlist”',
              'Playlist open · tap “Extract this playlist”',
            );
          } else if (u.contains('open.spotify.com')) {
            _hint = t(
              'Abre una playlist y luego “Extraer”',
              'Open a playlist then tap Extract',
            );
          } else if (u.contains('accounts.spotify.com')) {
            _hint = t('Inicia sesión en Spotify', 'Sign in to Spotify');
          }
        }),
      ))
      ..loadRequest(Uri.parse(_url));
  }

  Future<void> _goLibrary() async {
    await _ctrl.loadRequest(
        Uri.parse('https://open.spotify.com/collection/playlists'));
  }

  Future<void> _extract() async {
    setState(() {
      _extracting = true;
      _hint = t('Extrayendo…', 'Extracting…');
    });
    try {
      final raw = await _ctrl.runJavaScriptReturningResult(_extractJs);
      var text = raw.toString();
      // Android may wrap result in quotes
      if (text.startsWith('"') && text.endsWith('"')) {
        text = text.substring(1, text.length - 1)
            .replaceAll(r'\"', '"')
            .replaceAll(r'\\', '\\');
      }
      final parsed = SpotifyWebImportService.instance.parseExtracted(text);
      if (parsed == null ||
          ((parsed['tracks'] as List?)?.isEmpty ?? true)) {
        if (!mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text(t(
            'No se vieron canciones. Abre una playlist, haz scroll y reintenta. O pega el link.',
            'No tracks found. Open a playlist, scroll, retry — or paste the link.',
          )),
          duration: const Duration(seconds: 5),
        ));
        setState(() => _extracting = false);
        return;
      }

      if (!mounted) return;
      final ok = await showDialog<bool>(
        context: context,
        builder: (ctx) {
          final n = (parsed['tracks'] as List).length;
          return AlertDialog(
            backgroundColor: const Color(0xFF1A1A1E),
            title: Text(parsed['name']?.toString() ?? 'Playlist',
                style: const TextStyle(color: Colors.white)),
            content: Text(
              t(
                'Se encontraron $n canciones.\n¿Importar a Pokke? Cada tema se buscará en el catálogo (y se descargará si falta).',
                'Found $n tracks.\nImport into Pokke? Each song is matched in the catalog (downloaded if missing).',
              ),
              style: const TextStyle(color: Colors.white70),
            ),
            actions: [
              TextButton(
                  onPressed: () => Navigator.pop(ctx, false),
                  child: Text(t('Cancelar', 'Cancel'),
                      style: const TextStyle(color: Colors.white54))),
              TextButton(
                  onPressed: () => Navigator.pop(ctx, true),
                  child: Text(t('Importar', 'Import'),
                      style: const TextStyle(
                          color: Color(0xFF1DB954),
                          fontWeight: FontWeight.w700))),
            ],
          );
        },
      );
      if (ok != true) {
        setState(() => _extracting = false);
        return;
      }

      final result = await SpotifyWebImportService.instance
          .importPlaylist(parsed, onProgress: (m, p) {
        if (mounted) {
          setState(() => _hint = '$m · ${(p * 100).toStringAsFixed(0)}%');
        }
      });
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: Text(result == null
            ? t('Error al importar', 'Import failed')
            : t(
                'Importada · ${result['found_count']}/${result['track_count']} encontradas',
                'Imported · ${result['found_count']}/${result['track_count']} found',
              )),
        backgroundColor: const Color(0xFF1A3A1A),
      ));
      if (result != null) Navigator.pop(context, result);
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('$e')));
      }
    } finally {
      if (mounted) setState(() => _extracting = false);
    }
  }

  Future<void> _pasteUrl() async {
    final c = TextEditingController();
    final url = await showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: const Color(0xFF1A1A1E),
        title: Text(t('Link de playlist', 'Playlist link'),
            style: const TextStyle(color: Colors.white)),
        content: TextField(
          controller: c,
          style: const TextStyle(color: Colors.white),
          decoration: InputDecoration(
            hintText: 'https://open.spotify.com/playlist/...',
            hintStyle: TextStyle(color: Colors.white.withOpacity(0.35)),
          ),
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx),
              child: Text(t('Cancelar', 'Cancel'),
                  style: const TextStyle(color: Colors.white54))),
          TextButton(
              onPressed: () => Navigator.pop(ctx, c.text.trim()),
              child: Text(t('Abrir', 'Open'),
                  style: const TextStyle(color: Color(0xFF1DB954)))),
        ],
      ),
    );
    if (url == null || url.isEmpty) return;
    // oEmbed for title/cover while page loads
    final meta = await SpotifyWebImportService.instance.oEmbed(url);
    if (meta != null && mounted) {
      setState(() =>
          _hint = '${meta['title']} · ${t('cargando…', 'loading…')}');
    }
    await _ctrl.loadRequest(Uri.parse(url));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF0C0C0E),
      appBar: AppBar(
        backgroundColor: const Color(0xFF0C0C0E),
        title: Text(t('Importar Spotify', 'Import Spotify'),
            style: const TextStyle(fontWeight: FontWeight.w800)),
        actions: [
          IconButton(
            tooltip: t('Mis playlists', 'My playlists'),
            onPressed: _goLibrary,
            icon: const Icon(Icons.library_music_outlined),
          ),
          IconButton(
            tooltip: t('Pegar link', 'Paste link'),
            onPressed: _pasteUrl,
            icon: const Icon(Icons.link),
          ),
        ],
      ),
      body: Column(
        children: [
          if (_loading) const LinearProgressIndicator(color: Color(0xFF1DB954)),
          if (_hint != null)
            Container(
              width: double.infinity,
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
              color: const Color(0xFF151518),
              child: Text(_hint!,
                  style: TextStyle(
                      color: Colors.white.withOpacity(0.7), fontSize: 12)),
            ),
          Expanded(child: WebViewWidget(controller: _ctrl)),
          SafeArea(
            top: false,
            child: Padding(
              padding: const EdgeInsets.fromLTRB(12, 8, 12, 12),
              child: Row(
                children: [
                  Expanded(
                    child: OutlinedButton(
                      onPressed: () => _ctrl.loadRequest(
                          Uri.parse('https://accounts.spotify.com/login')),
                      style: OutlinedButton.styleFrom(
                        foregroundColor: Colors.white70,
                        side: BorderSide(color: Colors.white.withOpacity(0.2)),
                      ),
                      child: Text(t('Login', 'Login')),
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    flex: 2,
                    child: ElevatedButton.icon(
                      onPressed: _extracting ? null : _extract,
                      style: ElevatedButton.styleFrom(
                        backgroundColor: const Color(0xFF1DB954),
                        foregroundColor: Colors.black,
                        padding: const EdgeInsets.symmetric(vertical: 14),
                      ),
                      icon: _extracting
                          ? const SizedBox(
                              width: 18,
                              height: 18,
                              child: CircularProgressIndicator(
                                  strokeWidth: 2, color: Colors.black54),
                            )
                          : const Icon(Icons.download_rounded),
                      label: Text(
                        t('Extraer esta playlist', 'Extract this playlist'),
                        style: const TextStyle(fontWeight: FontWeight.w800),
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
}
