import 'package:flutter/material.dart';
import 'package:pokke/services/notification_service.dart';
import 'package:pokke/services/player_controller.dart';
import 'package:pokke/services/settings_service.dart';
import 'package:pokke/widgets/liquid_glass.dart';

class NotificationsScreen extends StatefulWidget {
  const NotificationsScreen({super.key});

  @override
  State<NotificationsScreen> createState() => _NotificationsScreenState();
}

class _NotificationsScreenState extends State<NotificationsScreen> {
  bool _refreshing = false;

  String t(String es, String en) =>
      SettingsService.instance.locale == 'en' ? en : es;

  @override
  void initState() {
    super.initState();
    _boot();
  }

  Future<void> _boot() async {
    await NotificationService.instance.load();
    await _refresh();
  }

  Future<void> _refresh() async {
    setState(() => _refreshing = true);
    await NotificationService.instance.refreshFromFollows();
    if (mounted) setState(() => _refreshing = false);
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: NotificationService.instance,
      builder: (context, _) {
        final items = NotificationService.instance.items;
        return Scaffold(
          backgroundColor: const Color(0xFF0C0C0E),
          appBar: AppBar(
            backgroundColor: Colors.transparent,
            title: Text(t('Notificaciones', 'Notifications')),
            actions: [
              if (items.isNotEmpty)
                TextButton(
                  onPressed: () => NotificationService.instance.markAllRead(),
                  child: Text(t('Leer todo', 'Read all'),
                      style: const TextStyle(color: Colors.white70)),
                ),
              IconButton(
                onPressed: _refreshing ? null : _refresh,
                icon: _refreshing
                    ? const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(
                            strokeWidth: 2, color: Colors.white54),
                      )
                    : const Icon(Icons.refresh),
              ),
            ],
          ),
          body: items.isEmpty
              ? Center(
                  child: Padding(
                    padding: const EdgeInsets.all(32),
                    child: Text(
                      t(
                        'Cuando un artista que sigues tenga canciones nuevas en el catálogo, aparecerán aquí.',
                        'When an artist you follow has new tracks in the catalog, they show up here.',
                      ),
                      textAlign: TextAlign.center,
                      style: TextStyle(
                          color: Colors.white.withOpacity(0.45), height: 1.4),
                    ),
                  ),
                )
              : RefreshIndicator(
                  color: const Color(0xfffc3c44),
                  onRefresh: _refresh,
                  child: ListView.builder(
                    padding: const EdgeInsets.fromLTRB(12, 8, 12, 40),
                    itemCount: items.length,
                    itemBuilder: (ctx, i) {
                      final n = items[i];
                      final read = n['read'] == true;
                      final song = n['song'];
                      return Padding(
                        padding: const EdgeInsets.only(bottom: 8),
                        child: LiquidGlass(
                          blur: 16,
                          opacity: read ? 0.06 : 0.12,
                          borderRadius: BorderRadius.circular(14),
                          child: ListTile(
                            leading: Icon(
                              n['type'] == 'new_release'
                                  ? Icons.album_outlined
                                  : Icons.notifications_outlined,
                              color: read
                                  ? Colors.white38
                                  : const Color(0xfffc3c44),
                            ),
                            title: Text(
                              n['title'] ?? '',
                              style: TextStyle(
                                color: Colors.white,
                                fontWeight:
                                    read ? FontWeight.w500 : FontWeight.w700,
                              ),
                            ),
                            subtitle: Text(
                              n['body'] ?? '',
                              style: TextStyle(
                                  color: Colors.white.withOpacity(0.55)),
                            ),
                            trailing: read
                                ? null
                                : Container(
                                    width: 8,
                                    height: 8,
                                    decoration: const BoxDecoration(
                                      color: Color(0xfffc3c44),
                                      shape: BoxShape.circle,
                                    ),
                                  ),
                            onTap: () async {
                              await NotificationService.instance
                                  .markRead(n['id']?.toString() ?? '');
                              if (song is Map<String, dynamic>) {
                                await PlayerController.instance.playSong(song);
                              }
                            },
                          ),
                        ),
                      );
                    },
                  ),
                ),
        );
      },
    );
  }
}
