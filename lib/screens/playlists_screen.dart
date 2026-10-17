import 'dart:io';
import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:share_plus/share_plus.dart';
import 'package:pokke/widgets/liquid_glass.dart';
import 'package:pokke/services/supabase_service.dart';
import 'package:pokke/services/settings_service.dart';
import 'package:pokke/screens/playlist_detail_screen.dart';

class PlaylistsScreen extends StatefulWidget {
  const PlaylistsScreen({super.key});

  @override
  State<PlaylistsScreen> createState() => _PlaylistsScreenState();
}

class _PlaylistsScreenState extends State<PlaylistsScreen> {
  List<Map<String, dynamic>> _playlists = [];
  bool _loading = true;

  String t(String es, String en) => SettingsService.instance.locale == 'en' ? en : es;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    try {
      final uid = SupabaseService.currentUser?.id;
      if (uid == null) {
        setState(() {
          _playlists = [];
          _loading = false;
        });
        return;
      }
      final res = await SupabaseService.kokoro
          .from('playlists')
          .select()
          .eq('user_id', uid)
          .order('created_at', ascending: false);
      setState(() => _playlists = List<Map<String, dynamic>>.from(res));
    } catch (_) {
      setState(() => _playlists = []);
    }
    if (mounted) setState(() => _loading = false);
  }

  Future<void> _create() async {
    final nameCtrl = TextEditingController();
    XFile? cover;
    File? coverFile;
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
                return Padding(
                  padding: const EdgeInsets.all(20),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Text(t('Nueva playlist', 'New playlist'),
                          style: const TextStyle(
                              color: Colors.white, fontSize: 18, fontWeight: FontWeight.w700)),
                      const SizedBox(height: 14),
                      GestureDetector(
                        onTap: () async {
                          final f = await ImagePicker().pickImage(source: ImageSource.gallery);
                          if (f != null) {
                            setModal(() {
                              cover = f;
                              coverFile = File(f.path);
                            });
                          }
                        },
                        child: Container(
                          height: 96,
                          width: 96,
                          decoration: BoxDecoration(
                            color: Colors.white12,
                            borderRadius: BorderRadius.circular(12),
                            image: coverFile != null
                                ? DecorationImage(image: FileImage(coverFile!), fit: BoxFit.cover)
                                : null,
                          ),
                          child: coverFile == null
                              ? const Icon(Icons.add_photo_alternate, color: Colors.white38, size: 36)
                              : null,
                        ),
                      ),
                      const SizedBox(height: 12),
                      TextField(
                        controller: nameCtrl,
                        style: const TextStyle(color: Colors.white),
                        decoration: InputDecoration(
                          hintText: t('Nombre', 'Name'),
                          hintStyle: TextStyle(color: Colors.white.withOpacity(0.4)),
                          filled: true,
                          fillColor: Colors.white.withOpacity(0.06),
                          border: OutlineInputBorder(
                              borderRadius: BorderRadius.circular(12), borderSide: BorderSide.none),
                        ),
                      ),
                      const SizedBox(height: 14),
                      SizedBox(
                        width: double.infinity,
                        child: ElevatedButton(
                          style: ElevatedButton.styleFrom(
                            backgroundColor: const Color(0xfffc3c44),
                            padding: const EdgeInsets.symmetric(vertical: 14),
                          ),
                          onPressed: () async {
                            final name = nameCtrl.text.trim();
                            if (name.isEmpty) return;
                            final uid = SupabaseService.currentUser?.id;
                            if (uid == null) return;
                            String? coverUrl;
                            try {
                              if (coverFile != null) {
                                final path =
                                    'playlist_covers/$uid/${DateTime.now().millisecondsSinceEpoch}.jpg';
                                await SupabaseService.kokoro.storage.from('avatars').upload(path, coverFile!);
                                coverUrl =
                                    SupabaseService.kokoro.storage.from('avatars').getPublicUrl(path);
                              }
                              await SupabaseService.kokoro.from('playlists').insert({
                                'user_id': uid,
                                'name': name,
                                if (coverUrl != null) 'cover_url': coverUrl,
                              });
                              if (ctx.mounted) Navigator.pop(ctx);
                              await _load();
                            } catch (e) {
                              if (ctx.mounted) {
                                ScaffoldMessenger.of(ctx).showSnackBar(
                                  SnackBar(content: Text('$e')),
                                );
                              }
                            }
                          },
                          child: Text(t('Crear', 'Create'),
                              style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w700)),
                        ),
                      ),
                      const SizedBox(height: 8),
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
    return Scaffold(
      backgroundColor: SettingsService.instance.amoled ? Colors.black : const Color(0xFF0C0C0E),
      appBar: AppBar(
        title: Text(t('Playlists', 'Playlists')),
        backgroundColor: Colors.transparent,
        actions: [
          IconButton(onPressed: _create, icon: const Icon(Icons.add, color: Colors.white)),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator(color: Color(0xfffc3c44)))
          : _playlists.isEmpty
              ? Center(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Text(t('Crea tu primera playlist', 'Create your first playlist'),
                          style: TextStyle(color: Colors.white.withOpacity(0.5))),
                      const SizedBox(height: 12),
                      ElevatedButton(
                        style: ElevatedButton.styleFrom(backgroundColor: const Color(0xfffc3c44)),
                        onPressed: _create,
                        child: Text(t('Crear playlist', 'Create playlist')),
                      ),
                    ],
                  ),
                )
              : ListView.builder(
                  padding: const EdgeInsets.all(16),
                  itemCount: _playlists.length,
                  itemBuilder: (_, i) {
                    final pl = _playlists[i];
                    final cover = pl['cover_url'];
                    return Padding(
                      padding: const EdgeInsets.only(bottom: 10),
                      child: LiquidGlass(
                        blur: 14,
                        opacity: 0.07,
                        borderRadius: BorderRadius.circular(14),
                        child: ListTile(
                          onTap: () {
                            Navigator.push(
                              context,
                              MaterialPageRoute(
                                builder: (_) => PlaylistDetailScreen(playlist: pl),
                              ),
                            ).then((_) => _load());
                          },
                          leading: ClipRRect(
                            borderRadius: BorderRadius.circular(8),
                            child: cover != null
                                ? Image.network(
                                    cover.toString(),
                                    width: 52,
                                    height: 52,
                                    fit: BoxFit.cover,
                                    errorBuilder: (_, __, ___) => Container(
                                      width: 52,
                                      height: 52,
                                      color: Colors.white12,
                                      child: const Icon(Icons.queue_music, color: Colors.white38),
                                    ),
                                  )
                                : Container(
                                    width: 52,
                                    height: 52,
                                    color: Colors.white12,
                                    child: const Icon(Icons.queue_music, color: Colors.white38),
                                  ),
                          ),
                          title: Text(pl['name'] ?? '',
                              style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w600)),
                          trailing: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              IconButton(
                                icon: const Icon(Icons.ios_share, color: Colors.white54, size: 20),
                                onPressed: () {
                                  final id = pl['id'] ?? '';
                                  final name = pl['name'] ?? 'playlist';
                                  Share.share(t(
                                    'Escucha mi playlist "$name" en pokke\npokke://playlist/$id',
                                    'Listen to my playlist "$name" on pokke\npokke://playlist/$id',
                                  ));
                                },
                              ),
                              const Icon(Icons.chevron_right, color: Colors.white38),
                            ],
                          ),
                        ),
                      ),
                    );
                  },
                ),
    );
  }
}
