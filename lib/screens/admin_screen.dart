import 'dart:io';
import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:pokke/services/supabase_service.dart';
import 'package:pokke/widgets/liquid_glass.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

class AdminScreen extends StatefulWidget {
  const AdminScreen({super.key});

  @override
  State<AdminScreen> createState() => _AdminScreenState();
}

class _AdminScreenState extends State<AdminScreen> {
  final _titleCtrl = TextEditingController();
  final _bodyCtrl = TextEditingController();
  File? _image;
  bool _saving = false;
  List<Map<String, dynamic>> _announcements = [];

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final list = await SupabaseService.getAnnouncements();
    setState(() => _announcements = list);
  }

  Future<void> _pickImage() async {
    final x = await ImagePicker().pickImage(source: ImageSource.gallery);
    if (x != null) setState(() => _image = File(x.path));
  }

  Future<void> _create() async {
    if (_titleCtrl.text.trim().isEmpty) return;
    setState(() => _saving = true);
    try {
      String? imageUrl;
      if (_image != null) {
        final path = 'announcements/${DateTime.now().millisecondsSinceEpoch}.jpg';
        await SupabaseService.kokoro.storage.from('avatars').upload(path, _image!);
        imageUrl = SupabaseService.kokoro.storage.from('avatars').getPublicUrl(path);
      }
      await SupabaseService.createAnnouncement(
        title: _titleCtrl.text.trim(),
        body: _bodyCtrl.text.trim().isEmpty ? null : _bodyCtrl.text.trim(),
        imageUrl: imageUrl,
      );
      _titleCtrl.clear();
      _bodyCtrl.clear();
      setState(() => _image = null);
      await _load();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Anuncio creado'), backgroundColor: Color(0xfffc3c44)),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('$e'), backgroundColor: Colors.red));
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    if (!SupabaseService.isOwner) {
      return Scaffold(
        backgroundColor: const Color(0xFF0A0A0C),
        appBar: AppBar(title: const Text('Admin')),
        body: const Center(child: Text('Solo el dueño puede acceder', style: TextStyle(color: Colors.white54))),
      );
    }

    return Scaffold(
      backgroundColor: const Color(0xFF0A0A0C),
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        title: const Text('Admin · Anuncios', style: TextStyle(fontWeight: FontWeight.w600)),
      ),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          const Text('Crear anuncio / mini-evento', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w600)),
          const SizedBox(height: 16),
          LiquidGlass(
            blur: 16,
            opacity: 0.07,
            borderRadius: BorderRadius.circular(14),
            child: TextField(
              controller: _titleCtrl,
              style: const TextStyle(color: Colors.white),
              decoration: const InputDecoration(
                hintText: 'Título',
                hintStyle: TextStyle(color: Colors.white38),
                border: InputBorder.none,
                contentPadding: EdgeInsets.all(14),
              ),
            ),
          ),
          const SizedBox(height: 12),
          LiquidGlass(
            blur: 16,
            opacity: 0.07,
            borderRadius: BorderRadius.circular(14),
            child: TextField(
              controller: _bodyCtrl,
              maxLines: 3,
              style: const TextStyle(color: Colors.white),
              decoration: const InputDecoration(
                hintText: 'Descripción (opcional)',
                hintStyle: TextStyle(color: Colors.white38),
                border: InputBorder.none,
                contentPadding: EdgeInsets.all(14),
              ),
            ),
          ),
          const SizedBox(height: 12),
          GestureDetector(
            onTap: _pickImage,
            child: LiquidGlass(
              blur: 16,
              opacity: 0.07,
              borderRadius: BorderRadius.circular(14),
              child: Container(
                height: 120,
                alignment: Alignment.center,
                child: _image != null
                    ? ClipRRect(
                        borderRadius: BorderRadius.circular(12),
                        child: Image.file(_image!, height: 110, fit: BoxFit.cover),
                      )
                    : const Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Icon(Icons.add_photo_alternate_outlined, color: Colors.white38, size: 32),
                          SizedBox(height: 6),
                          Text('Agregar imagen', style: TextStyle(color: Colors.white38, fontSize: 13)),
                        ],
                      ),
              ),
            ),
          ),
          const SizedBox(height: 16),
          SizedBox(
            height: 50,
            child: ElevatedButton(
              onPressed: _saving ? null : _create,
              style: ElevatedButton.styleFrom(
                backgroundColor: const Color(0xfffc3c44),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
              ),
              child: _saving
                  ? const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                  : const Text('Publicar', style: TextStyle(fontWeight: FontWeight.w600)),
            ),
          ),
          const SizedBox(height: 32),
          const Text('Anuncios activos', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600)),
          const SizedBox(height: 12),
          ..._announcements.map((a) => Container(
                margin: const EdgeInsets.only(bottom: 12),
                child: LiquidGlass(
                  blur: 14,
                  opacity: 0.08,
                  borderRadius: BorderRadius.circular(14),
                  child: ListTile(
                    title: Text(a['title'] ?? '', style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w600)),
                    subtitle: Text(a['body'] ?? '', style: TextStyle(color: Colors.white.withOpacity(0.6), fontSize: 13)),
                    trailing: a['image_url'] != null
                        ? ClipRRect(
                            borderRadius: BorderRadius.circular(8),
                            child: Image.network(a['image_url'], width: 48, height: 48, fit: BoxFit.cover),
                          )
                        : null,
                  ),
                ),
              )),
        ],
      ),
    );
  }
}
