import 'dart:io';
import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:pokke/services/supabase_service.dart';
import 'package:pokke/widgets/liquid_glass.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

class ProfileEditScreen extends StatefulWidget {
  const ProfileEditScreen({super.key});

  @override
  State<ProfileEditScreen> createState() => _ProfileEditScreenState();
}

class _ProfileEditScreenState extends State<ProfileEditScreen> {
  final _displayNameCtrl = TextEditingController();
  final _bioCtrl = TextEditingController();
  String? _avatarUrl;
  String? _bannerUrl;
  File? _newAvatar;
  File? _newBanner;
  bool _loading = true;
  bool _saving = false;

  final _picker = ImagePicker();

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final profile = await SupabaseService.getProfile();
    if (profile != null) {
      _displayNameCtrl.text = profile['display_name'] ?? '';
      _bioCtrl.text = profile['bio'] ?? '';
      _avatarUrl = profile['avatar_url'];
      _bannerUrl = profile['banner_url'];
    }
    setState(() => _loading = false);
  }

  Future<void> _pickMedia({required bool isAvatar}) async {
    // Supports image + GIF. Video can be added later with file_picker
    final xfile = await _picker.pickImage(
      source: ImageSource.gallery,
      imageQuality: 85,
    );
    if (xfile == null) return;
    setState(() {
      if (isAvatar) {
        _newAvatar = File(xfile.path);
      } else {
        _newBanner = File(xfile.path);
      }
    });
  }

  Future<String?> _uploadFile(File file, String folder) async {
    final userId = SupabaseService.currentUser?.id;
    if (userId == null) return null;
    final ext = file.path.split('.').last.toLowerCase();
    final path = '$folder/$userId/${DateTime.now().millisecondsSinceEpoch}.$ext';
    await SupabaseService.kokoro.storage.from('profiles').upload(
          path,
          file,
          fileOptions: const FileOptions(upsert: true),
        );
    return SupabaseService.kokoro.storage.from('profiles').getPublicUrl(path);
  }

  Future<void> _save() async {
    setState(() => _saving = true);
    try {
      String? avatar = _avatarUrl;
      String? banner = _bannerUrl;

      if (_newAvatar != null) {
        avatar = await _uploadFile(_newAvatar!, 'avatars');
      }
      if (_newBanner != null) {
        banner = await _uploadFile(_newBanner!, 'banners');
      }

      await SupabaseService.updateProfile(
        displayName: _displayNameCtrl.text.trim(),
        bio: _bioCtrl.text.trim(),
        avatarUrl: avatar,
        bannerUrl: banner,
      );

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Perfil actualizado'), backgroundColor: Color(0xfffc3c44)),
        );
        Navigator.pop(context, true);
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Error: $e'), backgroundColor: Colors.red),
        );
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return const Scaffold(
        backgroundColor: Color(0xFF0A0A0C),
        body: Center(child: CircularProgressIndicator(color: Color(0xfffc3c44))),
      );
    }

    return Scaffold(
      backgroundColor: const Color(0xFF0A0A0C),
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        title: const Text('Editar perfil', style: TextStyle(fontWeight: FontWeight.w600)),
        actions: [
          TextButton(
            onPressed: _saving ? null : _save,
            child: _saving
                ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2, color: Color(0xfffc3c44)))
                : const Text('Guardar', style: TextStyle(color: Color(0xfffc3c44), fontWeight: FontWeight.w600)),
          ),
        ],
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: Column(
          children: [
            // Banner
            GestureDetector(
              onTap: () => _pickMedia(isAvatar: false),
              child: Stack(
                children: [
                  Container(
                    height: 140,
                    width: double.infinity,
                    decoration: BoxDecoration(
                      borderRadius: BorderRadius.circular(18),
                      color: Colors.white10,
                      image: _newBanner != null
                          ? DecorationImage(image: FileImage(_newBanner!), fit: BoxFit.cover)
                          : (_bannerUrl != null
                              ? DecorationImage(image: NetworkImage(_bannerUrl!), fit: BoxFit.cover)
                              : null),
                    ),
                    child: (_newBanner == null && _bannerUrl == null)
                        ? const Center(child: Icon(Icons.add_photo_alternate_outlined, color: Colors.white38, size: 36))
                        : null,
                  ),
                  Positioned(
                    bottom: 8,
                    right: 8,
                    child: LiquidGlass(
                      blur: 12,
                      opacity: 0.2,
                      borderRadius: BorderRadius.circular(10),
                      child: const Padding(
                        padding: EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                        child: Text('Cambiar banner', style: TextStyle(fontSize: 12, color: Colors.white)),
                      ),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 20),

            // Avatar
            GestureDetector(
              onTap: () => _pickMedia(isAvatar: true),
              child: Stack(
                alignment: Alignment.bottomRight,
                children: [
                  CircleAvatar(
                    radius: 52,
                    backgroundColor: Colors.white12,
                    backgroundImage: _newAvatar != null
                        ? FileImage(_newAvatar!)
                        : (_avatarUrl != null ? NetworkImage(_avatarUrl!) as ImageProvider : null),
                    child: (_newAvatar == null && _avatarUrl == null)
                        ? const Icon(Icons.person, size: 48, color: Colors.white38)
                        : null,
                  ),
                  Container(
                    padding: const EdgeInsets.all(6),
                    decoration: const BoxDecoration(
                      color: Color(0xfffc3c44),
                      shape: BoxShape.circle,
                    ),
                    child: const Icon(Icons.camera_alt, size: 16, color: Colors.white),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 8),
            Text('Toca para cambiar (JPG, PNG, GIF)', style: TextStyle(color: Colors.white.withOpacity(0.4), fontSize: 12)),
            const SizedBox(height: 28),

            LiquidGlass(
              blur: 16,
              opacity: 0.07,
              borderRadius: BorderRadius.circular(16),
              child: TextField(
                controller: _displayNameCtrl,
                style: const TextStyle(color: Colors.white),
                decoration: const InputDecoration(
                  labelText: 'Nombre / Nickname',
                  labelStyle: TextStyle(color: Colors.white54),
                  border: InputBorder.none,
                  contentPadding: EdgeInsets.all(16),
                ),
              ),
            ),
            const SizedBox(height: 14),
            LiquidGlass(
              blur: 16,
              opacity: 0.07,
              borderRadius: BorderRadius.circular(16),
              child: TextField(
                controller: _bioCtrl,
                maxLines: 3,
                style: const TextStyle(color: Colors.white),
                decoration: const InputDecoration(
                  labelText: 'Bio',
                  labelStyle: TextStyle(color: Colors.white54),
                  border: InputBorder.none,
                  contentPadding: EdgeInsets.all(16),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
