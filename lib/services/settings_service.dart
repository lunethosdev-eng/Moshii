import 'package:shared_preferences/shared_preferences.dart';

class SettingsService {
  static final SettingsService instance = SettingsService._();
  SettingsService._();

  bool liquidGlassEnabled = true;
  double glassOpacity = 0.12;
  double glassBlur = 24;
  bool reduceAnimations = false;
  bool showMiniPlayer = true;
  String locale = 'es'; // 'es' | 'en'
  String playerFont = 'System';

  Future<void> load() async {
    final p = await SharedPreferences.getInstance();
    liquidGlassEnabled = p.getBool('lg_enabled') ?? true;
    glassOpacity = p.getDouble('lg_opacity') ?? 0.12;
    glassBlur = p.getDouble('lg_blur') ?? 24;
    reduceAnimations = p.getBool('reduce_anim') ?? false;
    showMiniPlayer = p.getBool('mini_player') ?? true;
    locale = p.getString('locale') ?? 'es';
    playerFont = p.getString('player_font') ?? 'System';
  }

  Future<void> save() async {
    final p = await SharedPreferences.getInstance();
    await p.setBool('lg_enabled', liquidGlassEnabled);
    await p.setDouble('lg_opacity', glassOpacity);
    await p.setDouble('lg_blur', glassBlur);
    await p.setBool('reduce_anim', reduceAnimations);
    await p.setBool('mini_player', showMiniPlayer);
    await p.setString('locale', locale);
    await p.setString('player_font', playerFont);
  }
}
