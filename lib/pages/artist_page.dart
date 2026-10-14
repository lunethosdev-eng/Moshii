import 'package:flutter/material.dart';
import 'package:pokke/services/supabase_service.dart';
import 'package:pokke/services/artist_image_service.dart';
import 'package:pokke/services/follow_service.dart';
import 'package:pokke/services/settings_service.dart';
import 'package:pokke/widgets/liquid_glass.dart';
import 'package:pokke/widgets/context_menu.dart';

class ArtistPage extends StatefulWidget {
  ArtistPage({super.key});

  @override
  State<ArtistPage> createState() => _ArtistPageState();
}

class _ArtistPageState extends State<ArtistPage> {
  String _name = '';
  String? _imageUrl;
  List<Map<String, dynamic>> _songs = [];
  bool _loading = true;
  bool _following = false;

  String t(String es, String en) => SettingsService.instance.locale == 'en' ? en : es;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_name.isNotEmpty) return;
    final args = ModalRoute.of(context)?.settings.arguments;
    if (args is Map) {
      _name = (args['name'] ?? args['artist'] ?? '').toString();
      _imageUrl = args['image_url']?.toString() ?? args['artist_img']?.toString();
      _load();
    } else if (args is String) {
      _name = args;
      _load();
    }
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    try {
      final songs = await SupabaseService.ukiyo
          .from('songs')
          .select()
          .ilike('artist', _name)
          .order('created_at', ascending: false)
          .limit(80);
      _songs = List<Map<String, dynamic>>.from(songs);
      // Fallback: contains match if exact ilike empty
      if (_songs.isEmpty && _name.isNotEmpty) {
        final all = await SupabaseService.ukiyo
            .from('songs')
            .select()
            .order('created_at', ascending: false)
            .limit(400);
        final q = _name.toLowerCase();
        _songs = List<Map<String, dynamic>>.from(all)
            .where((s) => (s['artist'] ?? '').toString().toLowerCase().contains(q))
            .toList();
      }
      if ((_imageUrl == null || _imageUrl!.isEmpty) && _songs.isNotEmpty) {
        _imageUrl = _songs.first['cover_url']?.toString();
      }
      if (_imageUrl == null || _imageUrl!.isEmpty) {
        _imageUrl = await ArtistImageService.imageFor(_name);
      }
      _following = await FollowService.isFollowing(_name);
    } catch (_) {}
    if (mounted) setState(() => _loading = false);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF0C0C0E),
      body: _loading
          ? const Center(child: CircularProgressIndicator(color: Color(0xfffc3c44)))
          : CustomScrollView(
              slivers: [
                SliverAppBar(
                  expandedHeight: 280,
                  pinned: true,
                  backgroundColor: const Color(0xFF0C0C0E),
                  leading: IconButton(
                    icon: const Icon(Icons.arrow_back, color: Colors.white),
                    onPressed: () => Navigator.pop(context),
                  ),
                  actions: [
                    IconButton(
                      icon: const Icon(Icons.more_horiz, color: Colors.white),
                      onPressed: () {
                        showPokkeContextMenu(
                          context: context,
                          items: [
                            ContextMenuItem(
                              icon: Icons.share,
                              label: t('Compartir artista', 'Share artist'),
                              onTap: () {},
                            ),
                            ContextMenuItem(
                              icon: _following ? Icons.person_remove : Icons.person_add,
                              label: _following ? t('Dejar de seguir', 'Unfollow') : t('Seguir', 'Follow'),
                              onTap: () async {
                                final v = await FollowService.toggle(_name, imageUrl: _imageUrl);
                                if (mounted) setState(() => _following = v);
                              },
                            ),
                          ],
                        );
                      },
                    ),
                  ],
                  flexibleSpace: FlexibleSpaceBar(
                    background: Stack(
                      fit: StackFit.expand,
                      children: [
                        if (_imageUrl != null)
                          Image.network(_imageUrl!, fit: BoxFit.cover,
                              errorBuilder: (_, __, ___) => Container(color: Colors.white10))
                        else
                          Container(color: Colors.white10),
                        Container(
                          decoration: BoxDecoration(
                            gradient: LinearGradient(
                              begin: Alignment.topCenter,
                              end: Alignment.bottomCenter,
                              colors: [
                                Colors.black.withOpacity(0.2),
                                const Color(0xFF0C0C0E),
                              ],
                            ),
                          ),
                        ),
                        Positioned(
                          left: 20,
                          right: 20,
                          bottom: 24,
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                _name,
                                style: const TextStyle(
                                  color: Colors.white,
                                  fontSize: 32,
                                  fontWeight: FontWeight.w800,
                                ),
                              ),
                              const SizedBox(height: 8),
                              Row(
                                children: [
                                  ElevatedButton(
                                    style: ElevatedButton.styleFrom(
                                      backgroundColor: _following
                                          ? Colors.white24
                                          : const Color(0xfffc3c44),
                                      foregroundColor: Colors.white,
                                      shape: RoundedRectangleBorder(
                                          borderRadius: BorderRadius.circular(20)),
                                    ),
                                    onPressed: () async {
                                      final v = await FollowService.toggle(_name, imageUrl: _imageUrl);
                                      if (mounted) setState(() => _following = v);
                                    },
                                    child: Text(_following ? t('Siguiendo', 'Following') : t('Seguir', 'Follow')),
                                  ),
                                  const SizedBox(width: 12),
                                  Text(
                                    '${_songs.length} ${t('canciones', 'songs')}',
                                    style: TextStyle(color: Colors.white.withOpacity(0.6)),
                                  ),
                                ],
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
                if (_songs.isEmpty)
                  const SliverFillRemaining(
                    child: Center(
                      child: Text('Sin canciones de este artista',
                          style: TextStyle(color: Colors.white54)),
                    ),
                  )
                else
                  SliverList(
                    delegate: SliverChildBuilderDelegate(
                      (ctx, i) {
                        final s = _songs[i];
                        return ListTile(
                          leading: ClipRRect(
                            borderRadius: BorderRadius.circular(6),
                            child: s['cover_url'] != null
                                ? Image.network(s['cover_url'].toString(),
                                    width: 48, height: 48, fit: BoxFit.cover,
                                    errorBuilder: (_, __, ___) =>
                                        Container(width: 48, height: 48, color: Colors.white12))
                                : Container(width: 48, height: 48, color: Colors.white12),
                          ),
                          title: Text(s['title'] ?? '',
                              style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w600)),
                          subtitle: Text(s['album'] ?? s['artist'] ?? '',
                              style: TextStyle(color: Colors.white.withOpacity(0.5))),
                          trailing: IconButton(
                            icon: const Icon(Icons.more_horiz, color: Colors.white38),
                            onPressed: () {
                              showPokkeContextMenu(
                                context: context,
                                items: [
                                  ContextMenuItem(
                                    icon: Icons.play_arrow,
                                    label: t('Reproducir', 'Play'),
                                    onTap: () => Navigator.pushNamed(context, '/player', arguments: s),
                                  ),
                                  ContextMenuItem(
                                    icon: Icons.share,
                                    label: t('Compartir', 'Share'),
                                    onTap: () {},
                                  ),
                                  ContextMenuItem(
                                    icon: Icons.playlist_add,
                                    label: t('Añadir a playlist', 'Add to playlist'),
                                    onTap: () {},
                                  ),
                                ],
                              );
                            },
                          ),
                          onTap: () => Navigator.pushNamed(context, '/player', arguments: s),
                        );
                      },
                      childCount: _songs.length,
                    ),
                  ),
              ],
            ),
    );
  }
}
