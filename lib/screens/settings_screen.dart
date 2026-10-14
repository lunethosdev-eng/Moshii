import 'package:flutter/material.dart';
import 'package:pokke/widgets/liquid_glass.dart';
import 'package:pokke/services/settings_service.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  final s = SettingsService.instance;
  String _filter = '';

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

  Widget _section(String title) => Padding(
        padding: const EdgeInsets.fromLTRB(4, 18, 4, 8),
        child: Text(title,
            style: TextStyle(
              color: Colors.white.withOpacity(0.5),
              fontWeight: FontWeight.w700,
              fontSize: 13,
              letterSpacing: 0.6,
            )),
      );

  Widget _flagTile(Map<String, String> item) {
    final id = item['id']!;
    final label = s.locale == 'en' ? item['en']! : item['es']!;
    return SwitchListTile(
      title: Text(label, style: const TextStyle(color: Colors.white, fontSize: 14)),
      value: s.flag(id),
      activeColor: const Color(0xfffc3c44),
      onChanged: (v) {
        s.setFlag(id, v);
        // Keep legacy mirrors in sync
        if (id == 'ui_liquid_glass') s.liquidGlassEnabled = v;
        if (id == 'ui_reduce_motion') s.reduceAnimations = v;
        if (id == 'ui_mini_player') s.showMiniPlayer = v;
        _persist();
      },
    );
  }

  List<Map<String, String>> _byCat(String cat) {
    final q = _filter.trim().toLowerCase();
    return SettingsService.catalog.where((e) {
      if (e['cat'] != cat) return false;
      if (q.isEmpty) return true;
      return (e['es']!.toLowerCase().contains(q) || e['en']!.toLowerCase().contains(q));
    }).toList();
  }

  Widget _group(String title, String cat) {
    final items = _byCat(cat);
    if (items.isEmpty) return const SizedBox.shrink();
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _section(title),
        LiquidGlass(
          blur: 16,
          opacity: 0.08,
          borderRadius: BorderRadius.circular(16),
          child: Column(children: items.map(_flagTile).toList()),
        ),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    final total = SettingsService.catalog.length;
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
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 48),
        children: [
          LiquidGlass(
            blur: 18,
            opacity: 0.1,
            borderRadius: BorderRadius.circular(14),
            child: TextField(
              style: const TextStyle(color: Colors.white),
              decoration: InputDecoration(
                hintText: t('Buscar en $total ajustes…', 'Search $total settings…'),
                hintStyle: TextStyle(color: Colors.white.withOpacity(0.35)),
                prefixIcon: Icon(Icons.search, color: Colors.white.withOpacity(0.45)),
                border: InputBorder.none,
                contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 14),
              ),
              onChanged: (v) => setState(() => _filter = v),
            ),
          ),
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
          _section(t('Liquid Glass', 'Liquid Glass')),
          LiquidGlass(
            blur: 18,
            opacity: 0.08,
            borderRadius: BorderRadius.circular(16),
            child: Column(
              children: [
                SwitchListTile(
                  title: Text(t('Activar Liquid Glass', 'Enable Liquid Glass'),
                      style: const TextStyle(color: Colors.white)),
                  value: s.liquidGlassEnabled,
                  activeColor: const Color(0xfffc3c44),
                  onChanged: (v) {
                    s.liquidGlassEnabled = v;
                    s.setFlag('ui_liquid_glass', v);
                    _persist();
                  },
                ),
                ListTile(
                  title: Text(t('Opacidad', 'Opacity'), style: const TextStyle(color: Colors.white70)),
                  subtitle: Slider(
                    value: s.glassOpacity.clamp(0.04, 0.4),
                    min: 0.04,
                    max: 0.4,
                    activeColor: const Color(0xfffc3c44),
                    onChanged: (v) {
                      s.glassOpacity = v;
                      setState(() {});
                    },
                    onChangeEnd: (_) => _persist(),
                  ),
                ),
                ListTile(
                  title: Text(t('Blur', 'Blur'), style: const TextStyle(color: Colors.white70)),
                  subtitle: Slider(
                    value: s.glassBlur.clamp(8, 40),
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
            ),
          ),
          _group(t('Privacidad', 'Privacy'), 'privacy'),
          _group(t('Reproducción', 'Playback'), 'playback'),
          _group(t('Teléfono y sistema', 'Phone & system'), 'phone'),
          _group(t('Personalización', 'Customization'), 'ui'),
          _group(t('Experimentales', 'Experimental'), 'exp'),
          _group(t('Notificaciones', 'Notifications'), 'notif'),
          _group(t('Biblioteca', 'Library'), 'lib'),
          const SizedBox(height: 16),
          Text(
            t('$total funciones configurables', '$total configurable features'),
            textAlign: TextAlign.center,
            style: TextStyle(color: Colors.white.withOpacity(0.3), fontSize: 12),
          ),
        ],
      ),
    );
  }
}
