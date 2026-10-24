import 'package:flutter/material.dart';
import 'package:pokke/widgets/compact_header.dart';
import 'package:pokke/services/listening_history.dart';
import 'package:pokke/services/settings_service.dart';
import 'package:pokke/services/local_music_service.dart';
import 'package:pokke/services/notification_service.dart';
import 'package:pokke/services/player_controller.dart';

class LibraryPage extends StatefulWidget {
  LibraryPage({super.key});

  @override
  State<LibraryPage> createState() => _LibraryPageState();
}

class _LibraryPageState extends State<LibraryPage> {
  final _scrollCtrl = ScrollController();
  bool _collapsed = false;
  List<Map<String, dynamic>> _recent = [];

  String t(String es, String en) =>
      SettingsService.instance.locale == 'en' ? en : es;

  @override
  void initState() {
    super.initState();
    _scrollCtrl.addListener(() {
      final c = _scrollCtrl.offset > 40;
      if (c != _collapsed) setState(() => _collapsed = c);
    });
    _load();
    LocalMusicService.instance.load();
    NotificationService.instance.load();
  }

  Future<void> _load() async {
    if (!SettingsService.instance.flag('priv_history_local', def: true)) {
      if (mounted) setState(() => _recent = []);
      return;
    }
    final r = await ListeningHistory.instance.recent(limit: 50);
    if (mounted) setState(() => _recent = r);
  }

  @override
  void dispose() {
    _scrollCtrl.dispose();
    super.dispose();
  }

  Widget _menuRow(IconData icon, String label, VoidCallback onTap,
      {Widget? trailing}) {
    return InkWell(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 12),
        child: Row(
          children: [
            Icon(icon, color: const Color(0xfffc3c44), size: 28),
            const SizedBox(width: 14),
            Expanded(
                child: Text(label,
                    style:
                        const TextStyle(color: Colors.white, fontSize: 18))),
            if (trailing != null) trailing,
            const Icon(Icons.chevron_right, color: Colors.white38, size: 20),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: SettingsService.instance.amoled
          ? Colors.black
          : const Color(0xFF0C0C0E),
      appBar:
          CompactHeader(title: t('Biblioteca', 'Library'), collapsed: _collapsed),
      body: RefreshIndicator(
        color: const Color(0xfffc3c44),
        onRefresh: _load,
        child: AnimatedBuilder(
          animation: Listenable.merge([
            LocalMusicService.instance,
            NotificationService.instance,
          ]),
          builder: (context, _) {
            final localCount = LocalMusicService.instance.tracks.length;
            final unread = NotificationService.instance.unread;
            return SingleChildScrollView(
              controller: _scrollCtrl,
              physics: const AlwaysScrollableScrollPhysics(),
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 140),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  _menuRow(Icons.playlist_play, 'Playlists',
                      () => Navigator.pushNamed(context, '/playlists')),
                  _menuRow(Icons.person_outline, t('Artistas', 'Artists'),
                      () => Navigator.pushNamed(context, '/artistlist')),
                  _menuRow(
                    Icons.library_music_outlined,
                    t('Música local', 'Local music'),
                    () => Navigator.pushNamed(context, '/local_import'),
                    trailing: localCount > 0
                        ? Padding(
                            padding: const EdgeInsets.only(right: 8),
                            child: Text('$localCount',
                                style: TextStyle(
                                    color: Colors.white.withOpacity(0.45))),
                          )
                        : null,
                  ),
                  _menuRow(
                    Icons.notifications_outlined,
                    t('Notificaciones', 'Notifications'),
                    () => Navigator.pushNamed(context, '/notifications'),
                    trailing: unread > 0
                        ? Container(
                            margin: const EdgeInsets.only(right: 8),
                            padding: const EdgeInsets.symmetric(
                                horizontal: 8, vertical: 2),
                            decoration: BoxDecoration(
                              color: const Color(0xfffc3c44),
                              borderRadius: BorderRadius.circular(10),
                            ),
                            child: Text('$unread',
                                style: const TextStyle(
                                    color: Colors.white,
                                    fontSize: 12,
                                    fontWeight: FontWeight.w700)),
                          )
                        : null,
                  ),
                  _menuRow(Icons.download_outlined, t('Descargas', 'Downloads'),
                      () {
                    ScaffoldMessenger.of(context).showSnackBar(SnackBar(
                      content: Text(t(
                          'Las descargas están en el player (botón descargar).',
                          'Downloads live in the player (download button).')),
                    ));
                  }),
                  const SizedBox(height: 20),
                  Text(t('Recientes', 'Recents'),
                      style: const TextStyle(
                          color: Colors.white,
                          fontSize: 20,
                          fontWeight: FontWeight.bold)),
                  const SizedBox(height: 10),
                  if (_recent.isEmpty)
                    Text(t('Sin historial aún', 'No history yet'),
                        style: TextStyle(color: Colors.white.withOpacity(0.4)))
                  else
                    ..._recent.take(30).map((s) {
                      final cover = s['cover_url'];
                      return ListTile(
                        contentPadding: EdgeInsets.zero,
                        leading: ClipRRect(
                          borderRadius: BorderRadius.circular(6),
                          child: cover != null
                              ? Image.network(cover.toString(),
                                  width: 48, height: 48, fit: BoxFit.cover,
                                  errorBuilder: (_, __, ___) => Container(
                                      width: 48,
                                      height: 48,
                                      color: Colors.white12))
                              : Container(
                                  width: 48,
                                  height: 48,
                                  color: Colors.white12,
                                  child: const Icon(Icons.music_note,
                                      color: Colors.white38)),
                        ),
                        title: Text(s['title'] ?? '',
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(color: Colors.white)),
                        subtitle: Text(s['artist'] ?? '',
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: TextStyle(
                                color: Colors.white.withOpacity(0.5))),
                        onTap: () =>
                            PlayerController.instance.playSong(s),
                      );
                    }),
                ],
              ),
            );
          },
        ),
      ),
    );
  }
}
