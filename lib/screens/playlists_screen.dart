import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:pokke/widgets/liquid_glass.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'dart:convert';

class PlaylistsScreen extends StatefulWidget {
  const PlaylistsScreen({super.key});

  @override
  State<PlaylistsScreen> createState() => _PlaylistsScreenState();
}

class _PlaylistsScreenState extends State<PlaylistsScreen> {
  List<Map<String, dynamic>> _playlists = [];

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final p = await SharedPreferences.getInstance();
    final raw = p.getString('pokke_playlists');
    if (raw != null) {
      try {
        final list = jsonDecode(raw) as List;
        setState(() => _playlists = list.map((e) => Map<String, dynamic>.from(e)).toList());
      } catch (_) {}
    }
  }

  Future<void> _save() async {
    final p = await SharedPreferences.getInstance();
    await p.setString('pokke_playlists', jsonEncode(_playlists));
  }

  Future<void> _create() async {
    final nameCtrl = TextEditingController();
    String? coverPath;
    await showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: const Color(0xFF1A1A1E),
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(20))),
      builder: (ctx) {
        return Padding(
          padding: EdgeInsets.only(bottom: MediaQuery.of(ctx).viewInsets.bottom, left: 20, right: 20, top: 20),
          child: StatefulBuilder(
            builder: (ctx, setModal) {
              return Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Text('Nueva playlist', style: TextStyle(color: Colors.white, fontSize: 18, fontWeight: FontWeight.bold)),
                  const SizedBox(height: 16),
                  GestureDetector(
                    onTap: () async {
                      final x = await ImagePicker().pickImage(source: ImageSource.gallery);
                      if (x != null) setModal(() => coverPath = x.path);
                    },
                    child: Container(
                      height: 100,
                      width: 100,
                      decoration: BoxDecoration(
                        color: Colors.white12,
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: coverPath != null
                          ? ClipRRect(
                              borderRadius: BorderRadius.circular(12),
                              child: Image.asset(coverPath!, fit: BoxFit.cover, errorBuilder: (_, __, ___) =>
                                  const Icon(Icons.image, color: Colors.white38, size: 36)))
                          : const Icon(Icons.add_photo_alternate, color: Colors.white38, size: 36),
                    ),
                  ),
                  const SizedBox(height: 8),
                  const Text('Toca para cover (JPG/PNG/GIF)', style: TextStyle(color: Colors.white38, fontSize: 12)),
                  const SizedBox(height: 16),
                  TextField(
                    controller: nameCtrl,
                    style: const TextStyle(color: Colors.white),
                    decoration: InputDecoration(
                      hintText: 'Nombre de la playlist',
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
                      onPressed: () {
                        if (nameCtrl.text.trim().isEmpty) return;
                        setState(() {
                          _playlists.insert(0, {
                            'id': DateTime.now().millisecondsSinceEpoch.toString(),
                            'name': nameCtrl.text.trim(),
                            'cover': coverPath,
                            'songs': <String>[],
                          });
                        });
                        _save();
                        Navigator.pop(ctx);
                      },
                      child: const Text('Crear'),
                    ),
                  ),
                  const SizedBox(height: 20),
                ],
              );
            },
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
        title: const Text('Playlists', style: TextStyle(fontWeight: FontWeight.w700)),
        actions: [
          IconButton(onPressed: _create, icon: const Icon(Icons.add, color: Colors.white)),
        ],
      ),
      body: _playlists.isEmpty
          ? Center(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(Icons.queue_music, size: 64, color: Colors.white.withOpacity(0.2)),
                  const SizedBox(height: 12),
                  Text('Crea tu primera playlist', style: TextStyle(color: Colors.white.withOpacity(0.5))),
                  const SizedBox(height: 16),
                  ElevatedButton(
                    style: ElevatedButton.styleFrom(backgroundColor: const Color(0xfffc3c44)),
                    onPressed: _create,
                    child: const Text('Crear playlist', style: TextStyle(color: Colors.white)),
                  ),
                ],
              ),
            )
          : ListView.builder(
              padding: const EdgeInsets.all(16),
              itemCount: _playlists.length,
              itemBuilder: (_, i) {
                final pl = _playlists[i];
                return LiquidGlass(
                  blur: 14,
                  opacity: 0.07,
                  borderRadius: BorderRadius.circular(14),
                  child: ListTile(
                    leading: Container(
                      width: 52, height: 52,
                      decoration: BoxDecoration(color: Colors.white12, borderRadius: BorderRadius.circular(8)),
                      child: const Icon(Icons.queue_music, color: Colors.white38),
                    ),
                    title: Text(pl['name'] ?? '', style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w600)),
                    subtitle: Text('${(pl['songs'] as List?)?.length ?? 0} canciones',
                        style: TextStyle(color: Colors.white.withOpacity(0.45))),
                    trailing: const Icon(Icons.chevron_right, color: Colors.white38),
                  ),
                );
              },
            ),
    );
  }
}
