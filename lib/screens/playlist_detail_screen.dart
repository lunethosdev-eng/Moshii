import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:share_plus/share_plus.dart';
import 'package:pokke/services/settings_service.dart';
import 'package:pokke/services/supabase_service.dart';
import 'package:pokke/services/player_controller.dart';
import 'package:pokke/widgets/liquid_glass.dart';

class PlaylistDetailScreen extends StatefulWidget {
  final Map<String, dynamic> playlist;
  const PlaylistDetailScreen({super.key, required this.playlist});

  @override
  State<PlaylistDetailScreen> createState() => _PlaylistDetailScreenState();
}

class _PlaylistDetailScreenState extends State<PlaylistDetailScreen>
    with SingleTickerProviderStateMixin {
  List<Map<String, dynamic>> _songs = [];
  bool _loading = true;
  late AnimationController _entry;

  String t(String es, String en) =>
      SettingsService.instance.locale == 'en' ? en : es;
  String get _playlistId => (widget.playlist['id'] ?? '').toString();
  String get _name => (widget.playlist['name'] ?? 'Playlist').toString();
  String? get _cover => widget.playlist['cover_url']?.toString();
  bool get _isPublic => widget.playlist['is_public'] == true;

  @override
  void initState() {
    super.initState();
    _entry = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 420),
    )..forward();
    _load();
  }

  @override
  void dispose() {
    _entry.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    try {
      final rows = await SupabaseService.kokoro
          .from('playlist_songs')
          .select()
          .eq('playlist_id', _playlistId)
          .order('position', ascending: true);
      final list = List<Map<String, dynamic>>.from(rows);
      final out = <Map<String, dynamic>>[];
      for (final r in list) {
        if ((r['title'] ?? '').toString().isNotEmpty) {
          out.add(Map<String, dynamic>.from(r));
          continue;
        }
        final sid = (r['song_id'] ?? '').toString();
        if (sid.isEmpty) {
          out.add(Map<String, dynamic>.from(r));
          continue;
        }
        try {
          final song = await SupabaseService.ukiyo
              .from('songs')
              .select()
              .eq('id', sid)
              .maybeSingle();
          if (song != null) {
            out.add({
              ...r,
              ...Map<String, dynamic>.from(song),
              'audio_url': song['audio_url'] ?? song['stream_url'] ?? r['stream_url'],
            });
          } else {
            out.add(Map<String, dynamic>.from(r));
          }
        } catch (_) {
          out.add(Map<String, dynamic>.from(r));
        }
      }
      if (mounted) setState(() => _songs = out);
    } catch (_) {
      if (mounted) setState(() => _songs = []);
    }
    if (mounted) setState(() => _loading = false);
  }

  Future<void> _persistOrder() async {
    for (var i = 0; i < _songs.length; i++) {
      final row = _songs[i];
      final id = row['id'];
      if (id == null) continue;
      try {
        await SupabaseService.kokoro
            .from('playlist_songs')
            .update({'position': i}).eq('id', id);
      } catch (_) {}
    }
  }

  Future<void> _onReorder(int oldIndex, int newIndex) async {
    setState(() {
      if (newIndex > oldIndex) newIndex -= 1;
      final item = _songs.removeAt(oldIndex);
      _songs.insert(newIndex, item);
    });
    HapticFeedback.selectionClick();
    await _persistOrder();
  }

  Future<void> _play({required bool shuffle}) async {
    if (_songs.isEmpty) return;
    HapticFeedback.mediumImpact();
    await PlayerController.instance.playPlaylist(_songs, shuffle: shuffle);
    if (!mounted) return;
    Navigator.pushNamed(context, '/player', arguments: _songs.first);
  }

  Future<void> _removeSong(Map<String, dynamic> row) async {
    try {
      final id = row['id'];
      if (id != null) {
        await SupabaseService.kokoro.from('playlist_songs').delete().eq('id', id);
      } else {
        await SupabaseService.kokoro
            .from('playlist_songs')
            .delete()
            .eq('playlist_id', _playlistId)
            .eq('song_id', row['song_id']);
      }
      await _load();
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text('$e')));
      }
    }
  }

  Future<void> _togglePublic() async {
    final next = !_isPublic;
    try {
      await SupabaseService.kokoro
          .from('playlists')
          .update({'is_public': next}).eq('id', _playlistId);
      setState(() => widget.playlist['is_public'] = next);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text(next
              ? t('Playlist pública: cualquiera en pokke puede escucharla',
                  'Public playlist: anyone on pokke can listen')
              : t('Playlist privada', 'Private playlist')),
        ));
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text(t(
              'No se pudo cambiar (¿columna is_public?)',
              'Could not update (is_public column?)')),
        ));
      }
    }
  }

  void _shareMenu() {
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      builder: (ctx) {
        return LiquidGlass(
          blur: 28,
          opacity: 0.16,
          borderRadius: const BorderRadius.vertical(top: Radius.circular(22)),
          child: SafeArea(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const SizedBox(height: 10),
                Container(
                  width: 40,
                  height: 4,
                  decoration: BoxDecoration(
                    color: Colors.white24,
                    borderRadius: BorderRadius.circular(2),
                  ),
                ),
                ListTile(
                  leading: Icon(
                    _isPublic ? Icons.public : Icons.public_off,
                    color: Colors.white70,
                  ),
                  title: Text(
                    _isPublic
                        ? t('Hacer privada', 'Make private')
                        : t('Publicar en pokke', 'Publish on pokke'),
                    style: const TextStyle(color: Colors.white),
                  ),
                  subtitle: Text(
                    t('Otros usuarios pueden encontrar y reproducir',
                        'Other users can find and play'),
                    style: TextStyle(color: Colors.white.withOpacity(0.45), fontSize: 12),
                  ),
                  onTap: () {
                    Navigator.pop(ctx);
                    _togglePublic();
                  },
                ),
                ListTile(
                  leading: const Icon(Icons.ios_share, color: Colors.white70),
                  title: Text(t('Compartir enlace', 'Share link'),
                      style: const TextStyle(color: Colors.white)),
                  onTap: () {
                    Navigator.pop(ctx);
                    Share.share(t(
                      'Escucha mi playlist "$_name" en pokke\npokke://playlist/$_playlistId',
                      'Listen to my playlist "$_name" on pokke\npokke://playlist/$_playlistId',
                    ));
                  },
                ),
                ListTile(
                  leading: const Icon(Icons.copy, color: Colors.white70),
                  title: Text(t('Copiar enlace', 'Copy link'),
                      style: const TextStyle(color: Colors.white)),
                  onTap: () {
                    Clipboard.setData(
                        ClipboardData(text: 'pokke://playlist/$_playlistId'));
                    Navigator.pop(ctx);
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(content: Text(t('Copiado', 'Copied'))),
                    );
                  },
                ),
                const SizedBox(height: 12),
              ],
            ),
          ),
        );
      },
    );
  }

  Future<void> _addFromCatalog() async {
    final qCtrl = TextEditingController();
    List<Map<String, dynamic>> results = [];
    await showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) {
        return Padding(
          padding: EdgeInsets.only(bottom: MediaQuery.of(ctx).viewInsets.bottom),
          child: LiquidGlass(
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
                    setModal(() =>
                        results = List<Map<String, dynamic>>.from(res));
                  } catch (_) {
                    setModal(() => results = []);
                  }
                }

                if (results.isEmpty) search('');

                return SizedBox(
                  height: MediaQuery.of(ctx).size.height * 0.72,
                  child: Column(
                    children: [
                      const SizedBox(height: 12),
                      Text(t('Añadir canciones', 'Add songs'),
                          style: const TextStyle(
                              color: Colors.white,
                              fontSize: 17,
                              fontWeight: FontWeight.w700)),
                      Padding(
                        padding: const EdgeInsets.all(12),
                        child: TextField(
                          controller: qCtrl,
                          style: const TextStyle(color: Colors.white),
                          onChanged: search,
                          decoration: InputDecoration(
                            hintText: t('Buscar canción…', 'Search song…'),
                            hintStyle:
                                TextStyle(color: Colors.white.withOpacity(0.35)),
                            prefixIcon:
                                const Icon(Icons.search, color: Colors.white38),
                            filled: true,
                            fillColor: Colors.white.withOpacity(0.06),
                            border: OutlineInputBorder(
                              borderRadius: BorderRadius.circular(12),
                              borderSide: BorderSide.none,
                            ),
                          ),
                        ),
                      ),
                      Expanded(
                        child: ListView.builder(
                          itemCount: results.length,
                          itemBuilder: (_, i) {
                            final s = results[i];
                            final title = (s['title'] ?? '').toString();
                            final artist = (s['artist'] ?? '').toString();
                            final cover = s['cover_url']?.toString();
                            return ListTile(
                              leading: ClipRRect(
                                borderRadius: BorderRadius.circular(6),
                                child: cover != null
                                    ? Image.network(cover,
                                        width: 44,
                                        height: 44,
                                        fit: BoxFit.cover,
                                        errorBuilder: (_, __, ___) =>
                                            _ph(44))
                                    : _ph(44),
                              ),
                              title: Text(title,
                                  style: const TextStyle(color: Colors.white)),
                              subtitle: Text(artist,
                                  style: TextStyle(
                                      color: Colors.white.withOpacity(0.5))),
                              trailing: const Icon(Icons.add_circle_outline,
                                  color: Color(0xfffc3c44)),
                              onTap: () async {
                                final ok = await PlaylistActions.addSong(
                                  playlistId: _playlistId,
                                  song: s,
                                );
                                if (ctx.mounted) {
                                  ScaffoldMessenger.of(ctx).showSnackBar(SnackBar(
                                    content: Text(ok
                                        ? t('Añadida', 'Added')
                                        : t('Error', 'Error')),
                                  ));
                                }
                                await _load();
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
          ),
        );
      },
    );
  }

  Widget _ph(double s) => Container(
        width: s,
        height: s,
        color: Colors.white12,
        child: const Icon(Icons.music_note, color: Colors.white24, size: 18),
      );

  @override
  Widget build(BuildContext context) {
    final bg = SettingsService.instance.amoled ? Colors.black : const Color(0xFF0C0C0E);
    return Scaffold(
      backgroundColor: bg,
      body: FadeTransition(
        opacity: CurvedAnimation(parent: _entry, curve: Curves.easeOut),
        child: SlideTransition(
          position: Tween<Offset>(
            begin: const Offset(0, 0.04),
            end: Offset.zero,
          ).animate(CurvedAnimation(parent: _entry, curve: Curves.easeOutCubic)),
          child: CustomScrollView(
            physics: const BouncingScrollPhysics(),
            slivers: [
              SliverAppBar(
                expandedHeight: 280,
                pinned: true,
                backgroundColor: bg,
                leading: IconButton(
                  icon: const Icon(Icons.arrow_back_ios_new, size: 18),
                  onPressed: () => Navigator.maybePop(context),
                ),
                actions: [
                  IconButton(
                    icon: const Icon(Icons.ios_share),
                    onPressed: _shareMenu,
                  ),
                  IconButton(
                    icon: const Icon(Icons.add),
                    onPressed: _addFromCatalog,
                    tooltip: t('Añadir', 'Add'),
                  ),
                ],
                flexibleSpace: FlexibleSpaceBar(
                  background: Stack(
                    fit: StackFit.expand,
                    children: [
                      if (_cover != null)
                        Image.network(
                          _cover!,
                          fit: BoxFit.cover,
                          errorBuilder: (_, __, ___) => Container(color: Colors.white10),
                        )
                      else
                        Container(
                          color: Colors.white10,
                          child: const Icon(Icons.queue_music,
                              size: 72, color: Colors.white24),
                        ),
                      Container(
                        decoration: BoxDecoration(
                          gradient: LinearGradient(
                            begin: Alignment.topCenter,
                            end: Alignment.bottomCenter,
                            colors: [
                              Colors.black.withOpacity(0.15),
                              bg.withOpacity(0.95),
                            ],
                          ),
                        ),
                      ),
                      Positioned(
                        left: 20,
                        right: 20,
                        bottom: 16,
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              _name,
                              style: const TextStyle(
                                color: Colors.white,
                                fontSize: 28,
                                fontWeight: FontWeight.w800,
                                letterSpacing: -0.5,
                              ),
                            ),
                            const SizedBox(height: 4),
                            Text(
                              '${_songs.length} ${t('canciones', 'songs')}${_isPublic ? ' · ${t('Pública', 'Public')}' : ''}',
                              style: TextStyle(
                                color: Colors.white.withOpacity(0.55),
                                fontSize: 13,
                              ),
                            ),
                            const SizedBox(height: 14),
                            Row(
                              children: [
                                _roundBtn(
                                  icon: Icons.play_arrow_rounded,
                                  filled: true,
                                  onTap: () => _play(shuffle: false),
                                  label: t('Reproducir', 'Play'),
                                ),
                                const SizedBox(width: 10),
                                _roundBtn(
                                  icon: Icons.shuffle_rounded,
                                  filled: false,
                                  onTap: () => _play(shuffle: true),
                                  label: t('Aleatorio', 'Shuffle'),
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
              if (_loading)
                const SliverFillRemaining(
                  child: Center(
                      child: CircularProgressIndicator(color: Color(0xfffc3c44))),
                )
              else if (_songs.isEmpty)
                SliverFillRemaining(
                  child: Center(
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(Icons.queue_music,
                            size: 48, color: Colors.white.withOpacity(0.25)),
                        const SizedBox(height: 12),
                        Text(t('Playlist vacía', 'Empty playlist'),
                            style: TextStyle(color: Colors.white.withOpacity(0.45))),
                        const SizedBox(height: 16),
                        ElevatedButton.icon(
                          style: ElevatedButton.styleFrom(
                            backgroundColor: const Color(0xfffc3c44),
                          ),
                          onPressed: _addFromCatalog,
                          icon: const Icon(Icons.add, color: Colors.white),
                          label: Text(t('Añadir canciones', 'Add songs'),
                              style: const TextStyle(color: Colors.white)),
                        ),
                      ],
                    ),
                  ),
                )
              else
                SliverPadding(
                  padding: const EdgeInsets.fromLTRB(8, 8, 8, 100),
                  sliver: SliverReorderableList(
                    itemCount: _songs.length,
                    onReorder: _onReorder,
                    itemBuilder: (context, i) {
                      final s = _songs[i];
                      final title = (s['title'] ?? '').toString();
                      final artist = (s['artist'] ?? '').toString();
                      final cover = s['cover_url']?.toString();
                      return ReorderableDelayedDragStartListener(
                        key: ValueKey(s['id'] ?? '$i-$title'),
                        index: i,
                        child: ListTile(
                          contentPadding:
                              const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                          leading: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              Text('${i + 1}',
                                  style: TextStyle(
                                      color: Colors.white.withOpacity(0.35),
                                      fontSize: 13)),
                              const SizedBox(width: 10),
                              ClipRRect(
                                borderRadius: BorderRadius.circular(6),
                                child: cover != null
                                    ? Image.network(cover,
                                        width: 48,
                                        height: 48,
                                        fit: BoxFit.cover,
                                        errorBuilder: (_, __, ___) => _ph(48))
                                    : _ph(48),
                              ),
                            ],
                          ),
                          title: Text(title,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(
                                  color: Colors.white, fontWeight: FontWeight.w600)),
                          subtitle: Text(artist,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: TextStyle(
                                  color: Colors.white.withOpacity(0.45), fontSize: 12)),
                          trailing: const Icon(Icons.drag_handle, color: Colors.white24),
                          onTap: () async {
                            await PlayerController.instance
                                .playPlaylist(_songs, startIndex: i);
                            if (context.mounted) {
                              Navigator.pushNamed(context, '/player', arguments: s);
                            }
                          },
                          onLongPress: () => _songMenu(s),
                        ),
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

  Widget _roundBtn({
    required IconData icon,
    required bool filled,
    required VoidCallback onTap,
    required String label,
  }) {
    return Material(
      color: filled ? const Color(0xfffc3c44) : Colors.white.withOpacity(0.12),
      borderRadius: BorderRadius.circular(28),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(28),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(icon, color: Colors.white, size: 22),
              const SizedBox(width: 6),
              Text(label,
                  style: const TextStyle(
                      color: Colors.white, fontWeight: FontWeight.w700, fontSize: 14)),
            ],
          ),
        ),
      ),
    );
  }

  void _songMenu(Map<String, dynamic> s) {
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      builder: (ctx) => LiquidGlass(
        blur: 24,
        opacity: 0.14,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(20)),
        child: SafeArea(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              ListTile(
                leading: const Icon(Icons.play_arrow, color: Colors.white70),
                title: Text(t('Reproducir', 'Play'),
                    style: const TextStyle(color: Colors.white)),
                onTap: () async {
                  Navigator.pop(ctx);
                  final i = _songs.indexOf(s);
                  await PlayerController.instance
                      .playPlaylist(_songs, startIndex: i < 0 ? 0 : i);
                  if (mounted) {
                    Navigator.pushNamed(context, '/player', arguments: s);
                  }
                },
              ),
              ListTile(
                leading: const Icon(Icons.delete_outline, color: Colors.redAccent),
                title: Text(t('Quitar de la playlist', 'Remove from playlist'),
                    style: const TextStyle(color: Colors.redAccent)),
                onTap: () {
                  Navigator.pop(ctx);
                  _removeSong(s);
                },
              ),
              const SizedBox(height: 8),
            ],
          ),
        ),
      ),
    );
  }
}

/// Shared helper used by player "Add to playlist" menu.
class PlaylistActions {
  static String t(String es, String en) =>
      SettingsService.instance.locale == 'en' ? en : es;

  static Future<bool> addSong({
    required String playlistId,
    required Map<String, dynamic> song,
  }) async {
    try {
      int pos = 0;
      try {
        final rows = await SupabaseService.kokoro
            .from('playlist_songs')
            .select('position')
            .eq('playlist_id', playlistId)
            .order('position', ascending: false)
            .limit(1);
        if (rows is List && rows.isNotEmpty) {
          pos = ((rows.first['position'] as int?) ?? 0) + 1;
        }
      } catch (_) {}

      await SupabaseService.kokoro.from('playlist_songs').insert({
        'playlist_id': playlistId,
        'song_id': song['id']?.toString(),
        'title': song['title']?.toString() ?? '',
        'artist': song['artist']?.toString() ?? '',
        'cover_url': song['cover_url']?.toString(),
        'stream_url': song['audio_url'] ?? song['stream_url'],
        'position': pos,
      });
      return true;
    } catch (_) {
      return false;
    }
  }

  static Future<void> showAddToPlaylistSheet(
    BuildContext context, {
    required Map<String, dynamic> song,
  }) async {
    final uid = SupabaseService.currentUser?.id;
    if (uid == null) return;
    List<Map<String, dynamic>> lists = [];
    try {
      final res = await SupabaseService.kokoro
          .from('playlists')
          .select()
          .eq('user_id', uid)
          .order('created_at', ascending: false);
      lists = List<Map<String, dynamic>>.from(res);
    } catch (_) {}

    if (!context.mounted) return;
    await showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      builder: (ctx) {
        return LiquidGlass(
          blur: 28,
          opacity: 0.14,
          borderRadius: const BorderRadius.vertical(top: Radius.circular(22)),
          child: lists.isEmpty
              ? Padding(
                  padding: const EdgeInsets.all(24),
                  child: Text(
                      t('No tienes playlists. Crea una en Biblioteca.',
                          'No playlists yet. Create one in Library.'),
                      style: const TextStyle(color: Colors.white70)),
                )
              : ListView(
                  shrinkWrap: true,
                  children: [
                    Padding(
                      padding: const EdgeInsets.fromLTRB(20, 16, 20, 8),
                      child: Text(t('Añadir a playlist', 'Add to playlist'),
                          style: const TextStyle(
                              color: Colors.white,
                              fontSize: 17,
                              fontWeight: FontWeight.w700)),
                    ),
                    ...lists.map((pl) {
                      return ListTile(
                        leading:
                            const Icon(Icons.queue_music, color: Colors.white70),
                        title: Text(pl['name'] ?? '',
                            style: const TextStyle(color: Colors.white)),
                        onTap: () async {
                          final ok = await addSong(
                            playlistId: (pl['id'] ?? '').toString(),
                            song: song,
                          );
                          if (ctx.mounted) Navigator.pop(ctx);
                          if (context.mounted) {
                            ScaffoldMessenger.of(context).showSnackBar(
                              SnackBar(
                                content: Text(ok
                                    ? t('Añadida', 'Added')
                                    : t('Error al añadir (¿tabla playlist_songs?)',
                                        'Add failed (playlist_songs table?)')),
                              ),
                            );
                          }
                        },
                      );
                    }),
                    const SizedBox(height: 16),
                  ],
                ),
        );
      },
    );
  }
}
