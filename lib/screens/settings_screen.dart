import 'package:flutter/material.dart';
import 'package:pokke/widgets/liquid_glass.dart';
import 'package:pokke/services/supabase_service.dart';
import 'package:pokke/services/settings_service.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  final s = SettingsService.instance;

  @override
  void initState() {
    super.initState();
    s.load().then((_) {
      if (mounted) setState(() {});
    });
  }

  Future<void> _persist() async {
    await s.save();
    setState(() {});
  }

  String t(String es, String en) => s.locale == 'en' ? en : es;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF0C0C0E),
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        title: Text(t('Ajustes', 'Settings'), style: const TextStyle(fontWeight: FontWeight.w700)),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back_ios_new, size: 20),
          onPressed: () => Navigator.pop(context),
        ),
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 40),
        children: [
          _section(t('Idioma', 'Language')),
          LiquidGlass(
            blur: 18,
            opacity: 0.08,
            borderRadius: BorderRadius.circular(16),
            child: Column(
              children: [
                RadioListTile<String>(
                  title: const Text('Español', style: TextStyle(color: Colors.white)),
                  value: 'es',
                  groupValue: s.locale,
                  activeColor: const Color(0xfffc3c44),
                  onChanged: (v) {
                    s.locale = v!;
                    _persist();
                  },
                ),
                RadioListTile<String>(
                  title: const Text('English', style: TextStyle(color: Colors.white)),
                  value: 'en',
                  groupValue: s.locale,
                  activeColor: const Color(0xfffc3c44),
                  onChanged: (v) {
                    s.locale = v!;
                    _persist();
                  },
                ),
              ],
            ),
          ),
          const SizedBox(height: 20),
          _section(t('Apariencia', 'Appearance')),
          LiquidGlass(
            blur: 18,
            opacity: 0.08,
            borderRadius: BorderRadius.circular(16),
            child: Column(
              children: [
                SwitchListTile(
                  title: Text(t('Liquid Glass', 'Liquid Glass'), style: const TextStyle(color: Colors.white)),
                  subtitle: Text(t('Efecto cristal en barras', 'Frosted glass on bars'),
                      style: TextStyle(color: Colors.white.withOpacity(0.5), fontSize: 12)),
                  value: s.liquidGlassEnabled,
                  activeColor: const Color(0xfffc3c44),
                  onChanged: (v) {
                    s.liquidGlassEnabled = v;
                    _persist();
                  },
                ),
                if (s.liquidGlassEnabled) ...[
                  ListTile(
                    title: Text(t('Transparencia', 'Transparency'),
                        style: TextStyle(color: Colors.white.withOpacity(0.9))),
                    subtitle: Slider(
                      value: s.glassOpacity,
                      min: 0.04,
                      max: 0.28,
                      activeColor: const Color(0xfffc3c44),
                      onChanged: (v) {
                        s.glassOpacity = v;
                        setState(() {});
                      },
                      onChangeEnd: (_) => _persist(),
                    ),
                  ),
                  ListTile(
                    title: Text(t('Intensidad del blur', 'Blur intensity'),
                        style: TextStyle(color: Colors.white.withOpacity(0.9))),
                    subtitle: Slider(
                      value: s.glassBlur,
                      min: 8,
                      max: 40,
                      activeColor: const Color(0xfffc3c44),
                      onChanged: (v) {
                        s.glassBlur = v;
                        setState(() {});
                      },
                      onChangeEnd: (_) => _persist(),
                    ),
                  ),
                ],
                SwitchListTile(
                  title: Text(t('Reducir animaciones', 'Reduce animations'),
                      style: const TextStyle(color: Colors.white)),
                  value: s.reduceAnimations,
                  activeColor: const Color(0xfffc3c44),
                  onChanged: (v) {
                    s.reduceAnimations = v;
                    _persist();
                  },
                ),
                SwitchListTile(
                  title: Text(t('Mini player', 'Mini player'), style: const TextStyle(color: Colors.white)),
                  value: s.showMiniPlayer,
                  activeColor: const Color(0xfffc3c44),
                  onChanged: (v) {
                    s.showMiniPlayer = v;
                    _persist();
                  },
                ),
              ],
            ),
          ),
          const SizedBox(height: 20),
          _section(t('Cuenta', 'Account')),
          LiquidGlass(
            blur: 18,
            opacity: 0.08,
            borderRadius: BorderRadius.circular(16),
            child: Column(
              children: [
                ListTile(
                  leading: const Icon(Icons.person_outline, color: Colors.white70),
                  title: Text(t('Editar perfil', 'Edit profile'), style: const TextStyle(color: Colors.white)),
                  trailing: const Icon(Icons.chevron_right, color: Colors.white38),
                  onTap: () => Navigator.pushNamed(context, '/profile_edit'),
                ),
                ListTile(
                  leading: const Icon(Icons.auto_awesome, color: Colors.white70),
                  title: Text(t('Wrap semanal / mensual', 'Weekly / Monthly wrap'),
                      style: const TextStyle(color: Colors.white)),
                  trailing: const Icon(Icons.chevron_right, color: Colors.white38),
                  onTap: () => Navigator.pushNamed(context, '/wrap'),
                ),
                if (SupabaseService.isOwner)
                  ListTile(
                    leading: const Icon(Icons.campaign_outlined, color: Colors.white70),
                    title: const Text('Admin', style: TextStyle(color: Colors.white)),
                    trailing: const Icon(Icons.chevron_right, color: Colors.white38),
                    onTap: () => Navigator.pushNamed(context, '/admin'),
                  ),
                ListTile(
                  leading: const Icon(Icons.logout, color: Color(0xfffc3c44)),
                  title: Text(t('Cerrar sesión', 'Sign out'),
                      style: const TextStyle(color: Color(0xfffc3c44))),
                  onTap: () async {
                    await SupabaseService.signOut();
                    if (context.mounted) {
                      Navigator.pushNamedAndRemoveUntil(context, '/auth', (_) => false);
                    }
                  },
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _section(String t) => Padding(
        padding: const EdgeInsets.only(left: 4, bottom: 8),
        child: Text(t,
            style: TextStyle(
                color: Colors.white.withOpacity(0.5),
                fontWeight: FontWeight.w600,
                fontSize: 13,
                letterSpacing: 0.3)),
      );
}
