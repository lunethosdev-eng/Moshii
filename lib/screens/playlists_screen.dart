import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:pokke/widgets/liquid_glass.dart';
import 'package:pokke/services/supabase_service.dart';
import 'package:pokke/services/settings_service.dart';
import 'dart:io';

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
        setState(() { _playlists = []; _loading = false; });
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
    setState(() => _loading = false);
  }

  Future<void> _create() async {
    final nameCtrl = TextEditingController();
    File? coverFile;
    await showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) {
        return Padding(
          padding: EdgeInsets.only(bottom: MediaQuery.of(ctx).viewInsets.bottom, left: 12, right: 12, top: 12),
          child: LiquidGlass(
            blur: 32,
            opacity: 0.16,
            borderRadius: BorderRadius.circular(22),
            enableSpecular: true,
            child: StatefulBuilder(
              builder: (ctx, setModal) {
                return Padding(
                  padding: const EdgeInsets.all(20),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Text(t('Nueva playlist', 'New playlist'),
                          style: const TextStyle(color: Colors.white, fontSize: 18, fontWeight: FontWeight.bold)),
                      const SizedBox(height: 16),
                      GestureDetector(
                        onTap: () async {
                          final x = await ImagePicker().pickImage(source: ImageSource.gallery);
                          if (x != null) setModal(() => coverFile = File(x.path));
                        },
                        child: Container(
                          height: 96, width: 96,
                          decoration: BoxDecoration(color: Colors.white12, borderRadius: BorderRadius.circular(12)),
                          child: coverFile != null
                              ? ClipRRect(
                                  borderRadius: BorderRadius.circular(12),
                                  child: Image.file(coverFile!, fit: BoxFit.cover))
                              : const Icon(Icons.add_photo_alternate, color: Colors.white38, size: 36),
                        ),
                      ),
                      const SizedBox(height: 16),
                      TextField(
                        controller: nameCtrl,
                        style: const TextStyle(color: Colors.white),
                        decoration: InputDecoration(
                          hintText: t('Nombre', 'Name'),
                          hintStyle: TextStyle(color: Colors.white.withOpacity(0.35)),
                          filled: true,
                          fillColor: Colors.white10,
                          border: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: BorderSide.none),
                        ),
                      ),
                      const SizedBox(height: 16),
                      SizedBox(
                        width: double.infinity,
                        child: ElevatedButton(
                          style: ElevatedButton.styleFrom(
                            backgroundColor: const Color(0xfffc3c44),
                            foregroundColor: Colors.white,
                            padding: const EdgeInsets.symmetric(vertical: 14),
                            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                          ),
                          onPressed: () async {
                            final name = nameCtrl.text.trim();
                            if (name.isEmpty) return;
                            final uid = SupabaseService.currentUser?.id;
                            if (uid == null) return;
                            String? coverUrl;
                            if (coverFile != null) {
                              try {
                                final path = 'playlist_covers/$uid/${DateTime.now().millisecondsSinceEpoch}.jpg';
                                await SupabaseService.kokoro.storage.from('avatars').upload(path, coverFile!);
                                coverUrl = SupabaseService.kokoro.storage.from('avatars').getPublicUrl(path);
                              } catch (_) {}
                            }
                            await SupabaseService.kokoro.from('playlists').insert({
                              'user_id': uid,
                              'name': name,
                              'cover_url': coverUrl,
                            });
                            if (ctx.mounted) Navigator.pop(ctx);
                            _load();
                          },
                          child: Text(t('Crear', 'Create')),
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
    return Scaffold(
      backgroundColor: const Color(0xFF0C0C0E),
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        title: Text(t('Playlists', 'Playlists'), style: const TextStyle(fontWeight: FontWeight.w700)),
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
                      Icon(Icons.queue_music, size: 64, color: Colors.white.withOpacity(0.2)),
                      const SizedBox(height: 12),
                      Text(t('Crea tu primera playlist', 'Create your first playlist'),
                          style: TextStyle(color: Colors.white.withOpacity(0.5))),
                      const SizedBox(height: 16),
                      ElevatedButton(
                        style: ElevatedButton.styleFrom(backgroundColor: const Color(0xfffc3c44)),
                        onPressed: _create,
                        child: Text(t('Crear playlist', 'Create playlist'),
                            style: const TextStyle(color: Colors.white)),
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
                          leading: ClipRRect(
                            borderRadius: BorderRadius.circular(8),
                            child: cover != null
                                ? Image.network(cover.toString(), width: 52, height: 52, fit: BoxFit.cover,
                                    errorBuilder: (_, __, ___) => Container(
                                        width: 52, height: 52, color: Colors.white12,
                                        child: const Icon(Icons.queue_music, color: Colors.white38)))
                                : Container(
                                    width: 52, height: 52, color: Colors.white12,
                                    child: const Icon(Icons.queue_music, color: Colors.white38)),
                          ),
                          title: Text(pl['name'] ?? '',
                              style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w600)),
                          trailing: const Icon(Icons.chevron_right, color: Colors.white38),
                        ),
                      ),
                    );
                  },
                ),
    );
  }
}
