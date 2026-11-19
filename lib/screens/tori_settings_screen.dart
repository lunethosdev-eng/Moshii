import 'package:flutter/material.dart';
import 'package:pokke/services/tori_native_bridge.dart';

/// Moshi-styled controls for Tori's persisted Android DataStore preferences.
class ToriSettingsScreen extends StatefulWidget {
  const ToriSettingsScreen({super.key});

  @override
  State<ToriSettingsScreen> createState() => _ToriSettingsScreenState();
}

class _ToriSettingsScreenState extends State<ToriSettingsScreen> {
  Map<String, dynamic> _values = {};
  bool _loading = true;
  String? _error;

  static const _sections = <String, List<String>>{
    'Apariencia': [
      'liquidGlassEnabled', 'liquidSliderEnabled', 'liquidPerformanceMode',
      'dynamicColors', 'accentColor', 'appAccentTheme', 'appTheme', 'vendoredBackdropGlass',
    ],
    'Movimiento': [
      'motionEnabled', 'motionProfile', 'motionIntensity', 'motionSpeed', 'motionDepth',
      'motionReflections', 'motionParallax', 'motionHaptics', 'motionReduce',
    ],
    'Reproducción y Canvas': [
      'blurPlayerBackground', 'gaplessPlayback', 'crossfadeSeconds', 'playerTheme',
      'youtubeCatalogEnabled', 'spotifyCanvasEnabled', 'appleCanvasEnabled',
      'pokkeAnimatedCoversEnabled', 'externalCanvasEnabled', 'canvasRepeatEnabled', 'playerLastScreen',
    ],
    'Letras': ['lyricsTargetLang', 'lyricsColorMode', 'lyricsAnimIntensity'],
    'Inicio': [
      'homeCustomBgUrl', 'homeShowRecent', 'homeShowNowPlaying', 'homeShowQuickAccess',
      'homeShowRecommendations', 'homeShowLibraryShortcuts', 'homeSectionOrder', 'homeLayout', 'homeDensity',
    ],
    'Cuentas y privacidad': [
      'externalAccountsEnabled', 'spotifyLoginEnabled', 'discordLoginEnabled',
      'discordPolicLinked', 'ytMusicLoginEnabled', 'showListeningOnProfile', 'showLinkEmbeds',
    ],
    'Funciones experimentales': ['experimentalChatsEnabled', 'experimentalMapsEnabled', 'autoAnimatedCovers'],
  };

  static const _labels = <String, String>{
    'liquidGlassEnabled': 'Liquid Glass',
    'liquidSliderEnabled': 'Slider líquido',
    'liquidPerformanceMode': 'Modo rendimiento',
    'dynamicColors': 'Colores dinámicos',
    'accentColor': 'Color de acento',
    'appAccentTheme': 'Tema de acento',
    'appTheme': 'Tema de la aplicación',
    'vendoredBackdropGlass': 'Cristal de fondo alternativo',
    'motionEnabled': 'Activar Motion Engine',
    'motionProfile': 'Perfil de movimiento',
    'motionIntensity': 'Intensidad del movimiento',
    'motionSpeed': 'Velocidad del movimiento',
    'motionDepth': 'Profundidad',
    'motionReflections': 'Reflejos',
    'motionParallax': 'Parallax',
    'motionHaptics': 'Respuesta háptica',
    'motionReduce': 'Reducir movimiento',
    'blurPlayerBackground': 'Desenfoque del reproductor',
    'gaplessPlayback': 'Reproducción sin pausas',
    'crossfadeSeconds': 'Fundido entre canciones (segundos)',
    'playerTheme': 'Tema del reproductor',
    'youtubeCatalogEnabled': 'Catálogo YouTube Music / InnerTube',
    'spotifyCanvasEnabled': 'Spotify Canvas',
    'appleCanvasEnabled': 'Apple Music Canvas',
    'pokkeAnimatedCoversEnabled': 'Canvas local de POKKE',
    'externalCanvasEnabled': 'Canvas externos',
    'canvasRepeatEnabled': 'Repetir Canvas',
    'playerLastScreen': 'Última pantalla del reproductor',
    'lyricsTargetLang': 'Idioma de traducción',
    'lyricsColorMode': 'Estilo de color de letras',
    'lyricsAnimIntensity': 'Intensidad de animación de letras',
    'homeCustomBgUrl': 'URL de fondo de inicio',
    'homeShowRecent': 'Mostrar escuchado recientemente',
    'homeShowNowPlaying': 'Mostrar reproducción actual',
    'homeShowQuickAccess': 'Mostrar accesos rápidos',
    'homeShowRecommendations': 'Mostrar recomendaciones',
    'homeShowLibraryShortcuts': 'Mostrar accesos a biblioteca',
    'homeSectionOrder': 'Orden de secciones',
    'homeLayout': 'Distribución de inicio',
    'homeDensity': 'Densidad de inicio',
    'externalAccountsEnabled': 'Activar cuentas externas',
    'spotifyLoginEnabled': 'Inicio de sesión Spotify',
    'discordLoginEnabled': 'Conexión Discord',
    'discordPolicLinked': 'Discord vinculado',
    'ytMusicLoginEnabled': 'Inicio de sesión YouTube Music',
    'showListeningOnProfile': 'Mostrar lo que escuchas en tu perfil',
    'showLinkEmbeds': 'Previsualizaciones de enlaces',
    'experimentalChatsEnabled': 'Chats experimentales',
    'experimentalMapsEnabled': 'Mapas de amigos experimentales',
    'autoAnimatedCovers': 'Portadas animadas automáticas',
  };

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    if (mounted) setState(() { _loading = true; _error = null; });
    final values = await ToriNativeBridge.toriSettings();
    if (!mounted) return;
    setState(() {
      _loading = false;
      _values = values ?? {};
      if (values == null) _error = 'No se pudo leer la configuración nativa. Comprueba que estás usando Android.';
    });
  }

  Future<void> _save(String key, Object value) async {
    final ok = await ToriNativeBridge.setToriSetting(key, value);
    if (!mounted) return;
    if (ok) {
      setState(() => _values[key] = value);
    } else {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('No se pudo guardar este ajuste.')),
      );
    }
  }

  double _numberMin(String key) => switch (key) {
        'crossfadeSeconds' => 0,
        'lyricsAnimIntensity' => 0.3,
        'motionSpeed' => 0.5,
        _ => 0,
      };

  double _numberMax(String key) => switch (key) {
        'crossfadeSeconds' => 12,
        'lyricsAnimIntensity' => 1.8,
        'motionIntensity' => 1.8,
        'motionSpeed' => 1.8,
        _ => 1,
      };

  List<String>? _options(String key) => switch (key) {
        'appTheme' => ['system', 'dark', 'light'],
        'accentColor' => ['white', 'violet', 'blue', 'pink', 'green'],
        'appAccentTheme' => ['white', 'violet', 'blue', 'pink', 'green'],
        'motionProfile' => ['full', 'balanced', 'light'],
        'playerTheme' => ['minimal', 'sakura', 'midnight', 'aurora'],
        'homeLayout' => ['compact', 'comfortable', 'dense'],
        'homeDensity' => ['small', 'medium', 'large'],
        'lyricsTargetLang' => ['es', 'en', 'pt', 'fr', 'de', 'it', 'ja', 'ko', 'zh', 'ru', 'ar', 'hi'],
        'lyricsColorMode' => ['classic', 'emotion', 'mono'],
        _ => null,
      };

  Widget _setting(String key) {
    final value = _values[key];
    final label = _labels[key] ?? key;
    if (value is bool) {
      return SwitchListTile(
        value: value,
        activeColor: const Color(0xfffc3c44),
        title: Text(label, style: const TextStyle(color: Colors.white)),
        onChanged: key == 'discordPolicLinked' ? null : (next) => _save(key, next),
      );
    }
    if (value is num) {
      final min = _numberMin(key);
      final max = _numberMax(key);
      final current = value.toDouble().clamp(min, max).toDouble();
      return Padding(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 12),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(children: [
            Expanded(child: Text(label, style: const TextStyle(color: Colors.white))),
            Text(current.toStringAsFixed(1), style: const TextStyle(color: Colors.white60, fontSize: 12)),
          ]),
          Slider(
            value: current,
            min: min,
            max: max,
            activeColor: const Color(0xfffc3c44),
            onChanged: (next) => setState(() => _values[key] = next),
            onChangeEnd: (next) => _save(key, next),
          ),
        ]),
      );
    }
    if (value is String) {
      final options = _options(key);
      if (options != null) {
        return ListTile(
          title: Text(label, style: const TextStyle(color: Colors.white)),
          trailing: DropdownButton<String>(
            value: options.contains(value) ? value : null,
            dropdownColor: const Color(0xFF202024),
            style: const TextStyle(color: Colors.white),
            items: options.map((item) => DropdownMenuItem(value: item, child: Text(item))).toList(),
            onChanged: (next) { if (next != null) _save(key, next); },
          ),
        );
      }
      return ListTile(
        title: Text(label, style: const TextStyle(color: Colors.white)),
        subtitle: Text(value.isEmpty ? 'Sin configurar' : value, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(color: Colors.white54, fontSize: 12)),
        trailing: const Icon(Icons.edit_outlined, color: Colors.white54, size: 18),
        onTap: () => _editText(key, label, value),
      );
    }
    return const SizedBox.shrink();
  }

  Future<void> _editText(String key, String label, String initial) async {
    final controller = TextEditingController(text: initial);
    final value = await showDialog<String>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        backgroundColor: const Color(0xFF1B1B20),
        title: Text(label, style: const TextStyle(color: Colors.white)),
        content: TextField(
          controller: controller,
          style: const TextStyle(color: Colors.white),
          decoration: const InputDecoration(enabledBorder: UnderlineInputBorder(borderSide: BorderSide(color: Colors.white24))),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(dialogContext), child: const Text('Cancelar')),
          TextButton(onPressed: () => Navigator.pop(dialogContext, controller.text.trim()), child: const Text('Guardar')),
        ],
      ),
    );
    if (value != null) await _save(key, value);
    controller.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF0C0C0E),
      appBar: AppBar(
        title: const Text('Ajustes avanzados'),
        backgroundColor: Colors.transparent,
        actions: [IconButton(onPressed: _load, icon: const Icon(Icons.refresh))],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator(color: Color(0xfffc3c44)))
          : _error != null
              ? Center(child: Padding(padding: const EdgeInsets.all(24), child: Text(_error!, style: const TextStyle(color: Colors.white70), textAlign: TextAlign.center)))
              : ListView(
                  padding: const EdgeInsets.fromLTRB(12, 8, 12, 36),
                  children: [
                    const Padding(
                      padding: EdgeInsets.fromLTRB(8, 4, 8, 16),
                      child: Text('Estos controles guardan directamente las preferencias nativas de Tori dentro de POKKE.', style: TextStyle(color: Colors.white54, fontSize: 12)),
                    ),
                    for (final entry in _sections.entries) ...[
                      Padding(
                        padding: const EdgeInsets.fromLTRB(8, 18, 8, 8),
                        child: Text(entry.key.toUpperCase(), style: const TextStyle(color: Colors.white54, fontWeight: FontWeight.w700, letterSpacing: .8, fontSize: 11)),
                      ),
                      Container(
                        decoration: BoxDecoration(color: const Color(0xFF151519), borderRadius: BorderRadius.circular(16), border: Border.all(color: Colors.white.withOpacity(.06))),
                        child: Column(children: [
                          for (final key in entry.value.where(_values.containsKey))
                            _setting(key),
                        ]),
                      ),
                    ],
                  ],
                ),
    );
  }
}
