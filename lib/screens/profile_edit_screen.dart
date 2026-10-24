import 'dart:io';
import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:share_plus/share_plus.dart';
import 'package:pokke/services/supabase_service.dart';
import 'package:pokke/services/settings_service.dart';
// profile theme via SettingsService
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
  String _theme = 'default';
  final _picker = ImagePicker();

  final _themes = <String, List<Color>>{
    'default': [const Color(0xFF0C0C0E), const Color(0xFF1A1A1E)],
    'sunset': [const Color(0xFF2D1B0E), const Color(0xFFfc3c44)],
    'ocean': [const Color(0xFF0A1628), const Color(0xFF1B6CA8)],
    'violet': [const Color(0xFF1A0A2E), const Color(0xFF9B59B6)],
    'mint': [const Color(0xFF0A1F1A), const Color(0xFF2ECC71)],
    'gold': [const Color(0xFF1A1508), const Color(0xFFD4AF37)],
    'rose': [const Color(0xFF1A0A12), const Color(0xFFE91E63)],
    'cyber': [const Color(0xFF050510), const Color(0xFF00E5FF)],
    'ember': [const Color(0xFF1A0C05), const Color(0xFFFF6D00)],
  };
  String _effect = 'glass';
  final _effects = const ['none', 'glass', 'parallax', 'glow'];

  String t(String es, String en) => SettingsService.instance.locale == 'en' ? en : es;

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
      _theme = profile['theme']?.toString()
          ?? SettingsService.instance.profileTheme
          ?? 'default';
      _effect = profile['effect']?.toString()
          ?? SettingsService.instance.profileEffect
          ?? 'glass';
    }
    setState(() => _loading = false);
  }

  Future<void> _pickMedia({required bool isAvatar}) async {
    final xfile = await _picker.pickImage(source: ImageSource.gallery, imageQuality: 85);
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
    await SupabaseService.kokoro.storage.from('avatars').upload(
          path,
          file,
          fileOptions: const FileOptions(upsert: true),
        );
    return SupabaseService.kokoro.storage.from('avatars').getPublicUrl(path);
  }

  Future<void> _save() async {
    setState(() => _saving = true);
    try {
      String? avatar = _avatarUrl;
      String? banner = _bannerUrl;
      if (_newAvatar != null) avatar = await _uploadFile(_newAvatar!, 'avatars');
      if (_newBanner != null) banner = await _uploadFile(_newBanner!, 'banners');
      await SupabaseService.updateProfile(
        displayName: _displayNameCtrl.text.trim(),
        bio: _bioCtrl.text.trim(),
        avatarUrl: avatar,
        bannerUrl: banner,
      );
      // theme column best-effort
      SettingsService.instance.profileTheme = _theme;
      SettingsService.instance.profileEffect = _effect;
      await SettingsService.instance.save();
      try {
        await SupabaseService.kokoro.from('profiles').update({
          'theme': _theme,
          'effect': _effect,
        }).eq('id', SupabaseService.currentUser!.id);
      } catch (_) {}
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(t('Perfil guardado', 'Profile saved')), backgroundColor: const Color(0xfffc3c44)),
        );
        Navigator.pop(context);
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('$e'), backgroundColor: Colors.red));
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  void _shareProfile() {
    final name = _displayNameCtrl.text.trim().isEmpty ? 'pokke' : _displayNameCtrl.text.trim();
    final id = SupabaseService.currentUser?.id ?? '';
    Share.share(t(
      'Mira mi perfil en pokke: @$name\npokke://user/$id',
      'Check my pokke profile: @$name\npokke://user/$id',
    ));
  }

  @override
  Widget build(BuildContext context) {
    final colors = _themes[_theme] ?? _themes['default']!;
    if (_loading) {
      return const Scaffold(
        backgroundColor: Color(0xFF0C0C0E),
        body: Center(child: CircularProgressIndicator(color: Color(0xfffc3c44))),
      );
    }
    return Scaffold(
      body: Container(
        decoration: BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: [colors[1].withOpacity(0.55), colors[0], const Color(0xFF0C0C0E)],
          ),
        ),
        child: SafeArea(
          child: ListView(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 32),
            children: [
              Row(
                children: [
                  IconButton(
                    onPressed: () => Navigator.pop(context),
                    icon: const Icon(Icons.arrow_back, color: Colors.white),
                  ),
                  Expanded(
                    child: Text(t('Editar perfil', 'Edit profile'),
                        style: const TextStyle(color: Colors.white, fontSize: 18, fontWeight: FontWeight.w700)),
                  ),
                  TextButton(
                    onPressed: _shareProfile,
                    child: Text(t('Compartir', 'Share'), style: const TextStyle(color: Colors.white70)),
                  ),
                  TextButton(
                    onPressed: _saving ? null : _save,
                    child: Text(t('Guardar', 'Save'),
                        style: const TextStyle(color: Color(0xfffc3c44), fontWeight: FontWeight.w700)),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              // Banner
              GestureDetector(
                onTap: () => _pickMedia(isAvatar: false),
                child: Stack(
                  children: [
                    ClipRRect(
                      borderRadius: BorderRadius.circular(16),
                      child: SizedBox(
                        height: 140,
                        width: double.infinity,
                        child: _newBanner != null
                            ? Image.file(_newBanner!, fit: BoxFit.cover)
                            : (_bannerUrl != null
                                ? Image.network(_bannerUrl!, fit: BoxFit.cover,
                                    errorBuilder: (_, __, ___) => Container(color: colors[1].withOpacity(0.4)))
                                : Container(
                                    decoration: BoxDecoration(
                                      gradient: LinearGradient(colors: colors),
                                    ),
                                  )),
                      ),
                    ),
                    Positioned(
                      right: 10,
                      bottom: 10,
                      child: LiquidGlass(
                        blur: 12,
                        opacity: 0.2,
                        borderRadius: BorderRadius.circular(20),
                        child: Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                          child: Text(t('Cambiar banner', 'Change banner'),
                              style: const TextStyle(color: Colors.white, fontSize: 12)),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 12),
              Center(
                child: GestureDetector(
                  onTap: () => _pickMedia(isAvatar: true),
                  child: Stack(
                    children: [
                      CircleAvatar(
                        radius: 48,
                        backgroundColor: Colors.white12,
                        backgroundImage: _newAvatar != null
                            ? FileImage(_newAvatar!)
                            : (_avatarUrl != null ? NetworkImage(_avatarUrl!) as ImageProvider : null),
                        child: (_newAvatar == null && _avatarUrl == null)
                            ? const Icon(Icons.person, size: 40, color: Colors.white38)
                            : null,
                      ),
                      Positioned(
                        right: 0,
                        bottom: 0,
                        child: Container(
                          padding: const EdgeInsets.all(6),
                          decoration: const BoxDecoration(color: Color(0xfffc3c44), shape: BoxShape.circle),
                          child: const Icon(Icons.camera_alt, size: 16, color: Colors.white),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 6),
              Center(
                child: Text(t('Toca para cambiar (JPG, PNG, GIF)', 'Tap to change (JPG, PNG, GIF)'),
                    style: TextStyle(color: Colors.white.withOpacity(0.4), fontSize: 12)),
              ),
              const SizedBox(height: 20),
              Text(t('Tema del perfil', 'Profile theme'),
                  style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w600)),
              const SizedBox(height: 10),
              SizedBox(
                height: 44,
                child: ListView(
                  scrollDirection: Axis.horizontal,
                  children: _themes.entries.map((e) {
                    final selected = _theme == e.key;
                    return GestureDetector(
                      onTap: () => setState(() => _theme = e.key),
                      child: Container(
                        width: 44,
                        height: 44,
                        margin: const EdgeInsets.only(right: 10),
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          gradient: LinearGradient(colors: e.value),
                          border: Border.all(
                            color: selected ? Colors.white : Colors.transparent,
                            width: 2.5,
                          ),
                        ),
                      ),
                    );
                  }).toList(),
                ),
              ),
              const SizedBox(height: 18),
              Text(t('Efecto del perfil', 'Profile effect'),
                  style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w600)),
              const SizedBox(height: 10),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: _effects.map((e) {
                  final selected = _effect == e;
                  final labels = {
                    'none': t('Ninguno', 'None'),
                    'glass': 'Glass',
                    'parallax': 'Parallax',
                    'glow': 'Glow',
                  };
                  return ChoiceChip(
                    label: Text(labels[e] ?? e),
                    selected: selected,
                    selectedColor: const Color(0xfffc3c44),
                    labelStyle: TextStyle(
                        color: selected ? Colors.white : Colors.white70,
                        fontWeight: FontWeight.w600),
                    backgroundColor: Colors.white10,
                    onSelected: (_) => setState(() => _effect = e),
                  );
                }).toList(),
              ),
              const SizedBox(height: 16),
              LiquidGlass(
                blur: 14,
                opacity: 0.08,
                borderRadius: BorderRadius.circular(14),
                child: TextField(
                  controller: _displayNameCtrl,
                  style: const TextStyle(color: Colors.white),
                  decoration: InputDecoration(
                    labelText: t('Nombre / Nickname', 'Name / Nickname'),
                    labelStyle: TextStyle(color: Colors.white.withOpacity(0.45)),
                    border: InputBorder.none,
                    contentPadding: const EdgeInsets.all(14),
                  ),
                ),
              ),
              const SizedBox(height: 12),
              LiquidGlass(
                blur: 14,
                opacity: 0.08,
                borderRadius: BorderRadius.circular(14),
                child: TextField(
                  controller: _bioCtrl,
                  maxLines: 3,
                  style: const TextStyle(color: Colors.white),
                  decoration: InputDecoration(
                    labelText: 'Bio',
                    labelStyle: TextStyle(color: Colors.white.withOpacity(0.45)),
                    border: InputBorder.none,
                    contentPadding: const EdgeInsets.all(14),
                  ),
                ),
              ),
              const SizedBox(height: 20),
              LiquidGlass(
                blur: 14,
                opacity: 0.08,
                borderRadius: BorderRadius.circular(14),
                child: ListTile(
                  leading: const Icon(Icons.playlist_play, color: Colors.white70),
                  title: Text(t('Mis playlists', 'My playlists'), style: const TextStyle(color: Colors.white)),
                  subtitle: Text(t('Crear y compartir', 'Create and share'),
                      style: TextStyle(color: Colors.white.withOpacity(0.45), fontSize: 12)),
                  trailing: const Icon(Icons.chevron_right, color: Colors.white38),
                  onTap: () => Navigator.pushNamed(context, '/playlists'),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
