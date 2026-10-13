import 'package:flutter/material.dart';
import 'package:pokke/widgets/compact_header.dart';
import 'package:pokke/services/listening_history.dart';
import 'package:pokke/services/settings_service.dart';

class LibraryPage extends StatefulWidget {
  LibraryPage({super.key});

  @override
  State<LibraryPage> createState() => _LibraryPageState();
}

class _LibraryPageState extends State<LibraryPage> {
  final _scrollCtrl = ScrollController();
  bool _collapsed = false;
  List<Map<String, dynamic>> _recent = [];

  String t(String es, String en) => SettingsService.instance.locale == 'en' ? en : es;

  @override
  void initState() {
    super.initState();
    _scrollCtrl.addListener(() {
      final c = _scrollCtrl.offset > 40;
      if (c != _collapsed) setState(() => _collapsed = c);
    });
    _load();
  }

  Future<void> _load() async {
    final r = await ListeningHistory.instance.recent(limit: 50);
    if (mounted) setState(() => _recent = r);
  }

  @override
  void dispose() {
    _scrollCtrl.dispose();
    super.dispose();
  }

  Widget _menuRow(IconData icon, String label, VoidCallback onTap) {
    return InkWell(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 12),
        child: Row(
          children: [
            Icon(icon, color: const Color(0xfffc3c44), size: 28),
            const SizedBox(width: 14),
            Expanded(child: Text(label, style: const TextStyle(color: Colors.white, fontSize: 18))),
            const Icon(Icons.chevron_right, color: Colors.white38, size: 20),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF0C0C0E),
      appBar: CompactHeader(title: t('Biblioteca', 'Library'), collapsed: _collapsed),
      body: RefreshIndicator(
        color: const Color(0xfffc3c44),
        onRefresh: _load,
        child: SingleChildScrollView(
          controller: _scrollCtrl,
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 140),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              _menuRow(Icons.queue_music_rounded, t('Playlists', 'Playlists'),
                  () => Navigator.pushNamed(context, '/playlists')),
              Divider(color: Colors.white.withOpacity(0.1)),
              _menuRow(Icons.mic_external_on, t('Artistas', 'Artists'),
                  () => Navigator.pushNamed(context, '/artistlist')),
              Divider(color: Colors.white.withOpacity(0.1)),
              _menuRow(Icons.album_rounded, t('Álbumes', 'Albums'),
                  () => Navigator.pushNamed(context, '/musiclist')),
              Divider(color: Colors.white.withOpacity(0.1)),
              _menuRow(Icons.music_note_rounded, t('Canciones', 'Songs'),
                  () => Navigator.pushNamed(context, '/musiclist')),
              const SizedBox(height: 24),
              Text(
                t('Escuchado recientemente', 'Recently Played'),
                style: const TextStyle(color: Colors.white, fontSize: 20, fontWeight: FontWeight.bold),
              ),
              const SizedBox(height: 12),
              if (_recent.isEmpty)
                Text(t('Aún no has escuchado nada', 'Nothing played yet'),
                    style: TextStyle(color: Colors.white.withOpacity(0.45)))
              else
                ..._recent.map((s) {
                  final cover = s['cover_url'];
                  return ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: ClipRRect(
                      borderRadius: BorderRadius.circular(8),
                      child: cover != null
                          ? Image.network(cover.toString(), width: 48, height: 48, fit: BoxFit.cover,
                              errorBuilder: (_, __, ___) => Container(width: 48, height: 48, color: Colors.white12))
                          : Container(width: 48, height: 48, color: Colors.white12),
                    ),
                    title: Text(s['title'] ?? '', style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w600)),
                    subtitle: Text(s['artist'] ?? '', style: TextStyle(color: Colors.white.withOpacity(0.5))),
                    onTap: () => Navigator.pushNamed(context, '/player', arguments: s),
                  );
                }),
            ],
          ),
        ),
      ),
    );
  }
}
