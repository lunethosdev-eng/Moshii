import 'package:flutter/material.dart';
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

class _PlaylistDetailScreenState extends State<PlaylistDetailScreen> {
  List<Map<String, dynamic>> _songs = [];
  bool _loading = true;

  String t(String es, String en) => SettingsService.instance.locale == 'en' ? en : es;
  String get _playlistId => (widget.playlist['id'] ?? '').toString();

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    try {
      // playlist_songs: playlist_id, song_id, title, artist, cover_url, audio_url, position
      final rows = await SupabaseService.kokoro
          .from('playlist_songs')
          .select()
          .eq('playlist_id', _playlistId)
          .order('position', ascending: true);
      final list = List<Map<String, dynamic>>.from(rows);

      // Enrich from ukiyo when we only have song_id
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
            out.add({...r, ...Map<String, dynamic>.from(song)});
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
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('${t('Error', 'Error')}: $e')),
        );
      }
    }
  }

  Future<void> _addFromCatalog() async {
    // Simple picker: recent / search catalog from ukiyo
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
                    setModal(() => results = List<Map<String, dynamic>>.from(res));
                  } catch (_) {
                    setModal(() => results = []);
                  }
                }

                // initial load
                if (results.isEmpty) {
                  search('');
                }

                return SizedBox(
                  height: MediaQuery.of(ctx).size.height * 0.7,
                  child: Column(
                    children: [
                      const SizedBox(height: 12),
                      Container(
                        width: 40,
                        height: 4,
                        decoration: BoxDecoration(
                          color: Colors.white24,
                          borderRadius: BorderRadius.circular(2),
                        ),
                      ),
                      Padding(
                        padding: const EdgeInsets.all(16),
                        child: TextField(
                          controller: qCtrl,
                          style: const TextStyle(color: Colors.white),
                          decoration: InputDecoration(
                            hintText: t('Buscar canción…', 'Search song…'),
                            hintStyle: TextStyle(color: Colors.white.withOpacity(0.4)),
                            filled: true,
                            fillColor: Colors.white.withOpacity(0.06),
                            border: OutlineInputBorder(
                              borderRadius: BorderRadius.circular(12),
                              borderSide: BorderSide.none,
                            ),
                            prefixIcon: const Icon(Icons.search, color: Colors.white54),
                          ),
                          onSubmitted: search,
                          onChanged: (v) {
                            if (v.length >= 2 || v.isEmpty) search(v);
                          },
                        ),
                      ),
                      Expanded(
                        child: ListView.builder(
                          itemCount: results.length,
                          itemBuilder: (_, i) {
                            final s = results[i];
                            return ListTile(
                              leading: ClipRRect(
                                borderRadius: BorderRadius.circular(6),
                                child: s['cover_url'] != null
                                    ? Image.network(s['cover_url'].toString(),
                                        width: 44, height: 44, fit: BoxFit.cover,
                                        errorBuilder: (_, __, ___) =>
                                            Container(width: 44, height: 44, color: Colors.white12))
                                    : Container(width: 44, height: 44, color: Colors.white12),
                              ),
                              title: Text(s['title'] ?? '',
                                  style: const TextStyle(color: Colors.white)),
                              subtitle: Text(s['artist'] ?? '',
                                  style: TextStyle(color: Colors.white.withOpacity(0.5))),
                              trailing: const Icon(Icons.add_circle_outline, color: Color(0xfffc3c44)),
                              onTap: () async {
                                final ok = await PlaylistActions.addSong(
                                  playlistId: _playlistId,
                                  song: s,
                                );
                                if (ctx.mounted) {
                                  Navigator.pop(ctx);
                                  ScaffoldMessenger.of(context).showSnackBar(
                                    SnackBar(
                                      content: Text(ok
                                          ? t('Añadida a la playlist', 'Added to playlist')
                                          : t('No se pudo añadir', 'Could not add')),
                                    ),
                                  );
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

  @override
  Widget build(BuildContext context) {
    final pl = widget.playlist;
    final cover = pl['cover_url'];
    final name = pl['name'] ?? t('Playlist', 'Playlist');

    return Scaffold(
      backgroundColor: SettingsService.instance.amoled ? Colors.black : const Color(0xFF0C0C0E),
      body: CustomScrollView(
        slivers: [
          SliverAppBar(
            expandedHeight: 220,
            pinned: true,
            backgroundColor: const Color(0xFF0C0C0E),
            flexibleSpace: FlexibleSpaceBar(
              title: Text(name.toString(),
                  style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700)),
              background: Stack(
                fit: StackFit.expand,
                children: [
                  if (cover != null)
                    Image.network(cover.toString(), fit: BoxFit.cover,
                        errorBuilder: (_, __, ___) => Container(color: const Color(0xFF1A1A2E)))
                  else
                    Container(
                      decoration: const BoxDecoration(
                        gradient: LinearGradient(
                          colors: [Color(0xFF2A1A3E), Color(0xFF0C0C0E)],
                          begin: Alignment.topCenter,
                          end: Alignment.bottomCenter,
                        ),
                      ),
                    ),
                  Container(
                    decoration: BoxDecoration(
                      gradient: LinearGradient(
                        begin: Alignment.topCenter,
                        end: Alignment.bottomCenter,
                        colors: [
                          Colors.black.withOpacity(0.15),
                          Colors.black.withOpacity(0.85),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
            ),
            actions: [
              IconButton(
                onPressed: _addFromCatalog,
                icon: const Icon(Icons.add),
                tooltip: t('Añadir canción', 'Add song'),
              ),
            ],
          ),
          if (_loading)
            const SliverFillRemaining(
              child: Center(child: CircularProgressIndicator(color: Color(0xfffc3c44))),
            )
          else if (_songs.isEmpty)
            SliverFillRemaining(
              child: Center(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(Icons.queue_music, size: 48, color: Colors.white.withOpacity(0.25)),
                    const SizedBox(height: 12),
                    Text(t('Playlist vacía', 'Empty playlist'),
                        style: TextStyle(color: Colors.white.withOpacity(0.5))),
                    const SizedBox(height: 16),
                    FilledButton.icon(
                      style: FilledButton.styleFrom(backgroundColor: const Color(0xfffc3c44)),
                      onPressed: _addFromCatalog,
                      icon: const Icon(Icons.add),
                      label: Text(t('Añadir canciones', 'Add songs')),
                    ),
                  ],
                ),
              ),
            )
          else
            SliverList(
              delegate: SliverChildBuilderDelegate(
                (_, i) {
                  final s = _songs[i];
                  final c = s['cover_url'];
                  return ListTile(
                    leading: ClipRRect(
                      borderRadius: BorderRadius.circular(6),
                      child: c != null
                          ? Image.network(c.toString(), width: 48, height: 48, fit: BoxFit.cover,
                              errorBuilder: (_, __, ___) =>
                                  Container(width: 48, height: 48, color: Colors.white12))
                          : Container(width: 48, height: 48, color: Colors.white12),
                    ),
                    title: Text(s['title'] ?? '',
                        style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w600)),
                    subtitle: Text(s['artist'] ?? '',
                        style: TextStyle(color: Colors.white.withOpacity(0.5))),
                    trailing: IconButton(
                      icon: const Icon(Icons.more_vert, color: Colors.white38),
                      onPressed: () {
                        showModalBottomSheet(
                          context: context,
                          backgroundColor: const Color(0xFF1A1A1E),
                          builder: (_) => SafeArea(
                            child: Column(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                ListTile(
                                  leading: const Icon(Icons.play_arrow, color: Colors.white70),
                                  title: Text(t('Reproducir', 'Play'),
                                      style: const TextStyle(color: Colors.white)),
                                  onTap: () {
                                    Navigator.pop(context);
                                    PlayerController.instance.playSong(s, forceRestart: true);
                                    Navigator.pushNamed(context, '/player', arguments: s);
                                  },
                                ),
                                ListTile(
                                  leading: const Icon(Icons.delete_outline, color: Colors.redAccent),
                                  title: Text(t('Quitar de la playlist', 'Remove from playlist'),
                                      style: const TextStyle(color: Colors.white)),
                                  onTap: () {
                                    Navigator.pop(context);
                                    _removeSong(s);
                                  },
                                ),
                              ],
                            ),
                          ),
                        );
                      },
                    ),
                    onTap: () {
                      PlayerController.instance.playSong(s, forceRestart: true);
                      Navigator.pushNamed(context, '/player', arguments: s);
                    },
                  );
                },
                childCount: _songs.length,
              ),
            ),
          const SliverToBoxAdapter(child: SizedBox(height: 100)),
        ],
      ),
    );
  }
}

/// Shared helper used by player "Add to playlist" menu.
class PlaylistActions {
  static Future<bool> addSong({
    required String playlistId,
    required Map<String, dynamic> song,
  }) async {
    try {
      // next position
      int pos = 0;
      try {
        final existing = await SupabaseService.kokoro
            .from('playlist_songs')
            .select('position')
            .eq('playlist_id', playlistId)
            .order('position', ascending: false)
            .limit(1);
        if (existing is List && existing.isNotEmpty) {
          pos = ((existing.first['position'] as num?)?.toInt() ?? 0) + 1;
        }
      } catch (_) {}

      await SupabaseService.kokoro.from('playlist_songs').insert({
        'playlist_id': playlistId,
        'song_id': song['id'],
        'title': song['title'],
        'artist': song['artist'],
        'cover_url': song['cover_url'],
        'audio_url': song['audio_url'] ?? song['stream_url'],
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
    final t = (String es, String en) =>
        SettingsService.instance.locale == 'en' ? en : es;
    final uid = SupabaseService.currentUser?.id;
    if (uid == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(t('Inicia sesión primero', 'Sign in first'))),
      );
      return;
    }
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
                  child: Text(t('No tienes playlists. Crea una en Biblioteca.',
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
                              color: Colors.white, fontSize: 17, fontWeight: FontWeight.w700)),
                    ),
                    ...lists.map((pl) {
                      return ListTile(
                        leading: const Icon(Icons.queue_music, color: Colors.white70),
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
