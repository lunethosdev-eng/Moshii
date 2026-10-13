import 'package:flutter/material.dart';
import 'package:pokke/widgets/liquid_glass.dart';
import 'package:pokke/services/supabase_service.dart';

/// Settings: privacy, Liquid Glass, transparency, animations, fonts.
class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  bool liquidGlassEnabled = true;
  double glassOpacity = 0.12;
  double glassBlur = 24;
  bool reduceAnimations = false;
  bool showMiniPlayer = true;
  String playerFont = 'System';

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF0C0C0E),
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        title: const Text('Settings', style: TextStyle(fontWeight: FontWeight.w700)),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back_ios_new, size: 20),
          onPressed: () => Navigator.pop(context),
        ),
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 40),
        children: [
          _section('Appearance'),
          LiquidGlass(
            blur: 18,
            opacity: 0.08,
            borderRadius: BorderRadius.circular(16),
            child: Column(
              children: [
                SwitchListTile(
                  title: const Text('Liquid Glass', style: TextStyle(color: Colors.white)),
                  subtitle: Text('Frosted glass on bars & cards',
                      style: TextStyle(color: Colors.white.withOpacity(0.5), fontSize: 12)),
                  value: liquidGlassEnabled,
                  activeColor: const Color(0xfffc3c44),
                  onChanged: (v) => setState(() => liquidGlassEnabled = v),
                ),
                if (liquidGlassEnabled) ...[
                  ListTile(
                    title: Text('Transparency', style: TextStyle(color: Colors.white.withOpacity(0.9))),
                    subtitle: Slider(
                      value: glassOpacity,
                      min: 0.04,
                      max: 0.28,
                      activeColor: const Color(0xfffc3c44),
                      onChanged: (v) => setState(() => glassOpacity = v),
                    ),
                  ),
                  ListTile(
                    title: Text('Blur intensity', style: TextStyle(color: Colors.white.withOpacity(0.9))),
                    subtitle: Slider(
                      value: glassBlur,
                      min: 8,
                      max: 40,
                      activeColor: const Color(0xfffc3c44),
                      onChanged: (v) => setState(() => glassBlur = v),
                    ),
                  ),
                ],
                SwitchListTile(
                  title: const Text('Reduce animations', style: TextStyle(color: Colors.white)),
                  value: reduceAnimations,
                  activeColor: const Color(0xfffc3c44),
                  onChanged: (v) => setState(() => reduceAnimations = v),
                ),
                SwitchListTile(
                  title: const Text('Mini player', style: TextStyle(color: Colors.white)),
                  value: showMiniPlayer,
                  activeColor: const Color(0xfffc3c44),
                  onChanged: (v) => setState(() => showMiniPlayer = v),
                ),
              ],
            ),
          ),

          const SizedBox(height: 20),
          _section('Player'),
          LiquidGlass(
            blur: 18,
            opacity: 0.08,
            borderRadius: BorderRadius.circular(16),
            child: Column(
              children: [
                ListTile(
                  title: const Text('Lyrics font', style: TextStyle(color: Colors.white)),
                  subtitle: Text(playerFont, style: TextStyle(color: Colors.white54)),
                  trailing: const Icon(Icons.chevron_right, color: Colors.white38),
                  onTap: () async {
                    final fonts = ['System', 'Serif', 'Rounded', 'Mono'];
                    final pick = await showModalBottomSheet<String>(
                      context: context,
                      backgroundColor: const Color(0xFF1A1A1E),
                      builder: (ctx) => SafeArea(
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          children: fonts
                              .map((f) => ListTile(
                                    title: Text(f, style: const TextStyle(color: Colors.white)),
                                    onTap: () => Navigator.pop(ctx, f),
                                  ))
                              .toList(),
                        ),
                      ),
                    );
                    if (pick != null) setState(() => playerFont = pick);
                  },
                ),
                ListTile(
                  title: const Text('Custom background', style: TextStyle(color: Colors.white)),
                  subtitle: const Text('Use cover art blur (default)', style: TextStyle(color: Colors.white54, fontSize: 12)),
                  trailing: const Icon(Icons.chevron_right, color: Colors.white38),
                  onTap: () {},
                ),
              ],
            ),
          ),

          const SizedBox(height: 20),
          _section('Privacy'),
          LiquidGlass(
            blur: 18,
            opacity: 0.08,
            borderRadius: BorderRadius.circular(16),
            child: Column(
              children: [
                SwitchListTile(
                  title: const Text('Private profile', style: TextStyle(color: Colors.white)),
                  subtitle: Text('Only you can see your library',
                      style: TextStyle(color: Colors.white.withOpacity(0.5), fontSize: 12)),
                  value: true,
                  activeColor: const Color(0xfffc3c44),
                  onChanged: (_) {},
                ),
                SwitchListTile(
                  title: const Text('Listening activity', style: TextStyle(color: Colors.white)),
                  value: false,
                  activeColor: const Color(0xfffc3c44),
                  onChanged: (_) {},
                ),
              ],
            ),
          ),

          const SizedBox(height: 20),
          _section('Account'),
          LiquidGlass(
            blur: 18,
            opacity: 0.08,
            borderRadius: BorderRadius.circular(16),
            child: Column(
              children: [
                ListTile(
                  leading: const Icon(Icons.person_outline, color: Colors.white70),
                  title: const Text('Edit profile', style: TextStyle(color: Colors.white)),
                  trailing: const Icon(Icons.chevron_right, color: Colors.white38),
                  onTap: () => Navigator.pushNamed(context, '/profile_edit'),
                ),
                if (SupabaseService.isOwner)
                  ListTile(
                    leading: const Icon(Icons.campaign_outlined, color: Colors.white70),
                    title: const Text('Admin / Announcements', style: TextStyle(color: Colors.white)),
                    trailing: const Icon(Icons.chevron_right, color: Colors.white38),
                    onTap: () => Navigator.pushNamed(context, '/admin'),
                  ),
                ListTile(
                  leading: const Icon(Icons.logout, color: Color(0xfffc3c44)),
                  title: const Text('Sign out', style: TextStyle(color: Color(0xfffc3c44))),
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
