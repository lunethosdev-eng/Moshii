import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Only flags that are actually wired to UI / playback / privacy.
class SettingsService extends ChangeNotifier {
  static final SettingsService instance = SettingsService._();
  SettingsService._();

  bool liquidGlassEnabled = true;
  double glassOpacity = 0.14;
  double glassBlur = 24;
  bool reduceAnimations = false;
  bool showMiniPlayer = true;
  bool amoled = false;
  String locale = 'es';
  String playerFont = 'default';
  String profileTheme = 'default';
  String profileEffect = 'glass'; // none | glass | parallax | glow
  bool historyLocal = true;
  bool profilePublic = true;
  bool downloadsWifiOnly = true;
  bool crossfade = true;
  double crossfadeSeconds = 4;

  /// Slim catalog — every id is read somewhere in the app.
  static const catalog = <Map<String, String>>[
    // appearance handled by dedicated controls
    {'id': 'ui_liquid_glass', 'cat': 'appearance', 'es': 'Liquid Glass', 'en': 'Liquid Glass'},
    {'id': 'ui_amoled', 'cat': 'appearance', 'es': 'Tema AMOLED', 'en': 'AMOLED theme'},
    {'id': 'ui_reduce_anim', 'cat': 'appearance', 'es': 'Reducir animaciones', 'en': 'Reduce animations'},
    {'id': 'ui_mini_player', 'cat': 'appearance', 'es': 'Mini player', 'en': 'Mini player'},
    // playback
    {'id': 'play_crossfade', 'cat': 'playback', 'es': 'Crossfade', 'en': 'Crossfade'},
    // privacy
    {'id': 'priv_history_local', 'cat': 'privacy', 'es': 'Historial local', 'en': 'Local history'},
    {'id': 'priv_profile_public', 'cat': 'privacy', 'es': 'Perfil público', 'en': 'Public profile'},
    {'id': 'priv_download_wifi', 'cat': 'privacy', 'es': 'Descargas solo Wi‑Fi', 'en': 'Downloads on Wi‑Fi only'},
  ];

  final Map<String, bool> flags = {};

  bool flag(String id, {bool def = false}) => flags[id] ?? def;

  Future<void> load() async {
    final p = await SharedPreferences.getInstance();
    liquidGlassEnabled = p.getBool('lg_enabled') ?? true;
    glassOpacity = p.getDouble('lg_opacity') ?? 0.14;
    glassBlur = p.getDouble('lg_blur') ?? 24;
    reduceAnimations = p.getBool('reduce_anim') ?? false;
    showMiniPlayer = p.getBool('mini_player') ?? true;
    amoled = p.getBool('amoled') ?? false;
    locale = p.getString('locale') ?? 'es';
    playerFont = p.getString('player_font') ?? 'default';
    profileTheme = p.getString('profile_theme') ?? 'default';
    profileEffect = p.getString('profile_effect') ?? 'glass';
    historyLocal = p.getBool('history_local') ?? true;
    profilePublic = p.getBool('profile_public') ?? true;
    downloadsWifiOnly = p.getBool('dl_wifi') ?? true;
    crossfade = p.getBool('crossfade') ?? true;
    crossfadeSeconds = p.getDouble('crossfade_sec') ?? 4;
    for (final item in catalog) {
      final id = item['id']!;
      flags[id] = p.getBool('flag_$id') ?? _defaultFor(id);
    }
    // mirror convenience fields into flags
    flags['ui_liquid_glass'] = liquidGlassEnabled;
    flags['ui_amoled'] = amoled;
    flags['ui_reduce_anim'] = reduceAnimations;
    flags['ui_mini_player'] = showMiniPlayer;
    flags['play_crossfade'] = crossfade;
    flags['priv_history_local'] = historyLocal;
    flags['priv_profile_public'] = profilePublic;
    flags['priv_download_wifi'] = downloadsWifiOnly;
  }

  bool _defaultFor(String id) {
    const on = {
      'ui_liquid_glass',
      'ui_mini_player',
      'play_crossfade',
      'priv_history_local',
      'priv_profile_public',
      'priv_download_wifi',
    };
    return on.contains(id);
  }

  Future<void> setFlag(String id, bool v) async {
    flags[id] = v;
    switch (id) {
      case 'ui_liquid_glass':
        liquidGlassEnabled = v;
        break;
      case 'ui_amoled':
        amoled = v;
        break;
      case 'ui_reduce_anim':
        reduceAnimations = v;
        break;
      case 'ui_mini_player':
        showMiniPlayer = v;
        break;
      case 'play_crossfade':
        crossfade = v;
        break;
      case 'priv_history_local':
        historyLocal = v;
        break;
      case 'priv_profile_public':
        profilePublic = v;
        break;
      case 'priv_download_wifi':
        downloadsWifiOnly = v;
        break;
    }
    await save();
  }

  Future<void> save() async {
    final p = await SharedPreferences.getInstance();
    await p.setBool('lg_enabled', liquidGlassEnabled);
    await p.setDouble('lg_opacity', glassOpacity);
    await p.setDouble('lg_blur', glassBlur);
    await p.setBool('reduce_anim', reduceAnimations);
    await p.setBool('mini_player', showMiniPlayer);
    await p.setBool('amoled', amoled);
    await p.setString('locale', locale);
    await p.setString('player_font', playerFont);
    await p.setString('profile_theme', profileTheme);
    await p.setString('profile_effect', profileEffect);
    await p.setBool('history_local', historyLocal);
    await p.setBool('profile_public', profilePublic);
    await p.setBool('dl_wifi', downloadsWifiOnly);
    await p.setBool('crossfade', crossfade);
    await p.setDouble('crossfade_sec', crossfadeSeconds);
    for (final e in flags.entries) {
      await p.setBool('flag_${e.key}', e.value);
    }
    notifyListeners();
  }
}
