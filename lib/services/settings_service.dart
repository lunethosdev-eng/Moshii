import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

class SettingsService extends ChangeNotifier {
  static final SettingsService instance = SettingsService._();
  SettingsService._();

  bool liquidGlassEnabled = true;
  double glassOpacity = 0.12;
  double glassBlur = 24;
  bool reduceAnimations = false;
  bool showMiniPlayer = true;
  String locale = 'es';
  String playerFont = 'System';
  String profileTheme = 'default';

  /// 110+ feature flags (privacy, experimental, phone, playback, etc.)
  final Map<String, bool> flags = {};

  static const List<Map<String, String>> catalog = [
    // Privacy
    {'id': 'priv_analytics', 'cat': 'privacy', 'es': 'Análisis de uso', 'en': 'Usage analytics'},
    {'id': 'priv_crash', 'cat': 'privacy', 'es': 'Informes de fallos', 'en': 'Crash reports'},
    {'id': 'priv_personalize', 'cat': 'privacy', 'es': 'Personalización de anuncios', 'en': 'Ad personalization'},
    {'id': 'priv_history_cloud', 'cat': 'privacy', 'es': 'Historial en la nube', 'en': 'Cloud listening history'},
    {'id': 'priv_share_activity', 'cat': 'privacy', 'es': 'Compartir actividad', 'en': 'Share activity'},
    {'id': 'priv_profile_public', 'cat': 'privacy', 'es': 'Perfil público', 'en': 'Public profile'},
    {'id': 'priv_show_online', 'cat': 'privacy', 'es': 'Mostrar en línea', 'en': 'Show online status'},
    {'id': 'priv_allow_follows', 'cat': 'privacy', 'es': 'Permitir seguidores', 'en': 'Allow followers'},
    {'id': 'priv_dm', 'cat': 'privacy', 'es': 'Mensajes directos', 'en': 'Direct messages'},
    {'id': 'priv_search_engine', 'cat': 'privacy', 'es': 'Aparecer en búsqueda', 'en': 'Appear in search'},
    {'id': 'priv_download_wifi', 'cat': 'privacy', 'es': 'Descargas solo Wi‑Fi', 'en': 'Downloads on Wi‑Fi only'},
    {'id': 'priv_clear_on_logout', 'cat': 'privacy', 'es': 'Borrar caché al salir', 'en': 'Clear cache on logout'},
    {'id': 'priv_biometric_lock', 'cat': 'privacy', 'es': 'Bloqueo biométrico', 'en': 'Biometric lock'},
    {'id': 'priv_pin_lock', 'cat': 'privacy', 'es': 'Bloqueo con PIN', 'en': 'PIN lock'},
    {'id': 'priv_hide_nsfw', 'cat': 'privacy', 'es': 'Ocultar contenido explícito', 'en': 'Hide explicit content'},
    {'id': 'priv_incognito', 'cat': 'privacy', 'es': 'Modo incógnito', 'en': 'Incognito mode'},
    {'id': 'priv_location', 'cat': 'privacy', 'es': 'Usar ubicación', 'en': 'Use location'},
    {'id': 'priv_contacts', 'cat': 'privacy', 'es': 'Sincronizar contactos', 'en': 'Sync contacts'},
    {'id': 'priv_clipboard', 'cat': 'privacy', 'es': 'Detectar links en portapapeles', 'en': 'Detect clipboard links'},
    {'id': 'priv_screenshot_block', 'cat': 'privacy', 'es': 'Bloquear capturas en player', 'en': 'Block screenshots in player'},
    // Playback
    {'id': 'play_gapless', 'cat': 'playback', 'es': 'Reproducción sin pausas', 'en': 'Gapless playback'},
    {'id': 'play_crossfade', 'cat': 'playback', 'es': 'Crossfade entre pistas', 'en': 'Crossfade tracks'},
    {'id': 'play_normalize', 'cat': 'playback', 'es': 'Normalizar volumen', 'en': 'Normalize volume'},
    {'id': 'play_eq', 'cat': 'playback', 'es': 'Ecualizador', 'en': 'Equalizer'},
    {'id': 'play_bass_boost', 'cat': 'playback', 'es': 'Refuerzo de bajos', 'en': 'Bass boost'},
    {'id': 'play_spatial', 'cat': 'playback', 'es': 'Audio espacial', 'en': 'Spatial audio'},
    {'id': 'play_hires', 'cat': 'playback', 'es': 'Preferir alta calidad', 'en': 'Prefer high quality'},
    {'id': 'play_data_saver', 'cat': 'playback', 'es': 'Ahorro de datos', 'en': 'Data saver'},
    {'id': 'play_autoplay', 'cat': 'playback', 'es': 'Autoplay similares', 'en': 'Autoplay similar'},
    {'id': 'play_queue_clear', 'cat': 'playback', 'es': 'Limpiar cola al cerrar', 'en': 'Clear queue on close'},
    {'id': 'play_remember_pos', 'cat': 'playback', 'es': 'Recordar posición', 'en': 'Remember position'},
    {'id': 'play_lyrics_auto', 'cat': 'playback', 'es': 'Lyrics automáticas', 'en': 'Auto lyrics'},
    {'id': 'play_karaoke', 'cat': 'playback', 'es': 'Modo karaoke', 'en': 'Karaoke mode'},
    {'id': 'play_sleep_timer', 'cat': 'playback', 'es': 'Temporizador de sueño', 'en': 'Sleep timer'},
    {'id': 'play_shake_skip', 'cat': 'playback', 'es': 'Agitar para saltar', 'en': 'Shake to skip'},
    {'id': 'play_double_tap', 'cat': 'playback', 'es': 'Doble toque auriculares', 'en': 'Headphone double-tap'},
    {'id': 'play_lockscreen', 'cat': 'playback', 'es': 'Controles en pantalla bloqueada', 'en': 'Lock screen controls'},
    {'id': 'play_android_auto', 'cat': 'playback', 'es': 'Android Auto', 'en': 'Android Auto'},
    {'id': 'play_carplay', 'cat': 'playback', 'es': 'CarPlay (iOS)', 'en': 'CarPlay (iOS)'},
    {'id': 'play_cast', 'cat': 'playback', 'es': 'Google Cast', 'en': 'Google Cast'},
    // Phone / device
    {'id': 'phone_haptics', 'cat': 'phone', 'es': 'Haptics / vibración', 'en': 'Haptics'},
    {'id': 'phone_haptics_strong', 'cat': 'phone', 'es': 'Haptics fuertes', 'en': 'Strong haptics'},
    {'id': 'phone_bt_auto', 'cat': 'phone', 'es': 'Auto-play al conectar BT', 'en': 'Auto-play on BT connect'},
    {'id': 'phone_bt_banner', 'cat': 'phone', 'es': 'Banner de auriculares', 'en': 'Headphone banner'},
    {'id': 'phone_bt_name', 'cat': 'phone', 'es': 'Mostrar nombre del dispositivo BT', 'en': 'Show BT device name'},
    {'id': 'phone_wifi_only_hq', 'cat': 'phone', 'es': 'HQ solo en Wi‑Fi', 'en': 'HQ on Wi‑Fi only'},
    {'id': 'phone_cellular_warn', 'cat': 'phone', 'es': 'Avisar uso de datos', 'en': 'Warn on cellular data'},
    {'id': 'phone_battery_saver', 'cat': 'phone', 'es': 'Modo ahorro de batería', 'en': 'Battery saver mode'},
    {'id': 'phone_dim_oled', 'cat': 'phone', 'es': 'Atenuar OLED en player', 'en': 'Dim OLED in player'},
    {'id': 'phone_keep_awake', 'cat': 'phone', 'es': 'Mantener pantalla en lyrics', 'en': 'Keep screen on for lyrics'},
    {'id': 'phone_proximity', 'cat': 'phone', 'es': 'Sensor de proximidad', 'en': 'Proximity sensor'},
    {'id': 'phone_accel_gestures', 'cat': 'phone', 'es': 'Gestos con acelerómetro', 'en': 'Accelerometer gestures'},
    {'id': 'phone_volume_keys', 'cat': 'phone', 'es': 'Botones de volumen = pista', 'en': 'Volume keys = track'},
    {'id': 'phone_notif_media', 'cat': 'phone', 'es': 'Notificación multimedia', 'en': 'Media notification'},
    {'id': 'phone_notif_compact', 'cat': 'phone', 'es': 'Notificación compacta', 'en': 'Compact notification'},
    {'id': 'phone_widget', 'cat': 'phone', 'es': 'Widget de inicio', 'en': 'Home widget'},
    {'id': 'phone_quick_settings', 'cat': 'phone', 'es': 'Tile de ajustes rápidos', 'en': 'Quick settings tile'},
    {'id': 'phone_share_sheet', 'cat': 'phone', 'es': 'Sheet nativo de compartir', 'en': 'Native share sheet'},
    {'id': 'phone_file_picker', 'cat': 'phone', 'es': 'Importar audio local', 'en': 'Import local audio'},
    {'id': 'phone_scrobble', 'cat': 'phone', 'es': 'Scrobble (Last.fm style)', 'en': 'Scrobble (Last.fm style)'},
    // Appearance / customization
    {'id': 'ui_liquid_glass', 'cat': 'ui', 'es': 'Liquid Glass global', 'en': 'Global Liquid Glass'},
    {'id': 'ui_specular', 'cat': 'ui', 'es': 'Brillos especulares', 'en': 'Specular highlights'},
    {'id': 'ui_blur_heavy', 'cat': 'ui', 'es': 'Blur intenso', 'en': 'Heavy blur'},
    {'id': 'ui_compact_nav', 'cat': 'ui', 'es': 'Nav compacto al scroll', 'en': 'Compact nav on scroll'},
    {'id': 'ui_mini_player', 'cat': 'ui', 'es': 'Mini player', 'en': 'Mini player'},
    {'id': 'ui_mini_blur', 'cat': 'ui', 'es': 'Blur en mini player', 'en': 'Mini player blur'},
    {'id': 'ui_dynamic_color', 'cat': 'ui', 'es': 'Color dinámico del cover', 'en': 'Dynamic cover color'},
    {'id': 'ui_amoled', 'cat': 'ui', 'es': 'Tema AMOLED negro', 'en': 'AMOLED black theme'},
    {'id': 'ui_large_text', 'cat': 'ui', 'es': 'Texto grande', 'en': 'Large text'},
    {'id': 'ui_bold_text', 'cat': 'ui', 'es': 'Texto en negrita', 'en': 'Bold text'},
    {'id': 'ui_reduce_motion', 'cat': 'ui', 'es': 'Reducir movimiento', 'en': 'Reduce motion'},
    {'id': 'ui_bubble_nav', 'cat': 'ui', 'es': 'Burbuja liquid en nav', 'en': 'Liquid bubble nav'},
    {'id': 'ui_swipe_player', 'cat': 'ui', 'es': 'Swipe para cerrar player', 'en': 'Swipe to dismiss player'},
    {'id': 'ui_grid_home', 'cat': 'ui', 'es': 'Home en cuadrícula', 'en': 'Grid home'},
    {'id': 'ui_show_lyrics_btn', 'cat': 'ui', 'es': 'Botón lyrics visible', 'en': 'Show lyrics button'},
    {'id': 'ui_show_queue', 'cat': 'ui', 'es': 'Mostrar cola', 'en': 'Show queue'},
    {'id': 'ui_cover_spin', 'cat': 'ui', 'es': 'Cover giratorio', 'en': 'Spinning cover'},
    {'id': 'ui_wave_visualizer', 'cat': 'ui', 'es': 'Visualizador de onda', 'en': 'Wave visualizer'},
    {'id': 'ui_custom_bg', 'cat': 'ui', 'es': 'Fondos personalizados', 'en': 'Custom backgrounds'},
    {'id': 'ui_profile_gradient', 'cat': 'ui', 'es': 'Gradiente de perfil', 'en': 'Profile gradient'},
    // Experimental
    {'id': 'exp_poki_dj', 'cat': 'exp', 'es': 'Poki DJ (radio IA)', 'en': 'Poki DJ (AI radio)'},
    {'id': 'exp_ai_lyrics', 'cat': 'exp', 'es': 'Lyrics con IA', 'en': 'AI lyrics'},
    {'id': 'exp_ai_recommend', 'cat': 'exp', 'es': 'Recomendaciones IA', 'en': 'AI recommendations'},
    {'id': 'exp_voice_search', 'cat': 'exp', 'es': 'Búsqueda por voz', 'en': 'Voice search'},
    {'id': 'exp_voice_dj', 'cat': 'exp', 'es': 'Voz de DJ (Fish/TTS)', 'en': 'DJ voice (Fish/TTS)'},
    {'id': 'exp_stem_split', 'cat': 'exp', 'es': 'Separación de stems', 'en': 'Stem separation'},
    {'id': 'exp_live_translate', 'cat': 'exp', 'es': 'Traducir lyrics en vivo', 'en': 'Live lyrics translate'},
    {'id': 'exp_offline_pack', 'cat': 'exp', 'es': 'Pack offline inteligente', 'en': 'Smart offline pack'},
    {'id': 'exp_collaborative', 'cat': 'exp', 'es': 'Playlists colaborativas', 'en': 'Collaborative playlists'},
    {'id': 'exp_social_listen', 'cat': 'exp', 'es': 'Escucha social', 'en': 'Social listening'},
    {'id': 'exp_vr_player', 'cat': 'exp', 'es': 'Player VR (experimental)', 'en': 'VR player (experimental)'},
    {'id': 'exp_watch', 'cat': 'exp', 'es': 'Wear OS / Watch', 'en': 'Wear OS / Watch'},
    {'id': 'exp_beta_cast', 'cat': 'exp', 'es': 'Cast beta nativo', 'en': 'Native cast beta'},
    {'id': 'exp_sse_progress', 'cat': 'exp', 'es': 'Progreso SSE de descarga', 'en': 'SSE download progress'},
    {'id': 'exp_dual_player', 'cat': 'exp', 'es': 'Player dual (crossfade)', 'en': 'Dual player (crossfade)'},
    {'id': 'exp_debug_overlay', 'cat': 'exp', 'es': 'Overlay de depuración', 'en': 'Debug overlay'},
    {'id': 'exp_mock_network', 'cat': 'exp', 'es': 'Simular red lenta', 'en': 'Simulate slow network'},
    {'id': 'exp_force_hq', 'cat': 'exp', 'es': 'Forzar HQ siempre', 'en': 'Always force HQ'},
    {'id': 'exp_beta_search', 'cat': 'exp', 'es': 'Motor de búsqueda beta', 'en': 'Beta search engine'},
    {'id': 'exp_artist_graph', 'cat': 'exp', 'es': 'Grafo de artistas', 'en': 'Artist graph'},
    // Notifications
    {'id': 'notif_new_music', 'cat': 'notif', 'es': 'Nueva música de seguidos', 'en': 'New music from follows'},
    {'id': 'notif_playlist', 'cat': 'notif', 'es': 'Actualizaciones de playlist', 'en': 'Playlist updates'},
    {'id': 'notif_poki', 'cat': 'notif', 'es': 'Sugerencias Poki DJ', 'en': 'Poki DJ suggestions'},
    {'id': 'notif_live', 'cat': 'notif', 'es': 'Eventos en vivo', 'en': 'Live events'},
    {'id': 'notif_marketing', 'cat': 'notif', 'es': 'Novedades de pokke', 'en': 'pokke news'},
    {'id': 'notif_quiet', 'cat': 'notif', 'es': 'Horas silenciosas', 'en': 'Quiet hours'},
    {'id': 'notif_led', 'cat': 'notif', 'es': 'LED de notificación', 'en': 'Notification LED'},
    {'id': 'notif_heads_up', 'cat': 'notif', 'es': 'Heads-up', 'en': 'Heads-up'},
    // Library
    {'id': 'lib_auto_download', 'cat': 'lib', 'es': 'Descarga automática likes', 'en': 'Auto-download likes'},
    {'id': 'lib_smart_mix', 'cat': 'lib', 'es': 'Mezclas inteligentes', 'en': 'Smart mixes'},
    {'id': 'lib_duplicates', 'cat': 'lib', 'es': 'Detectar duplicados', 'en': 'Detect duplicates'},
    {'id': 'lib_sort_recent', 'cat': 'lib', 'es': 'Ordenar por recientes', 'en': 'Sort by recent'},
    {'id': 'lib_show_duration', 'cat': 'lib', 'es': 'Mostrar duración', 'en': 'Show duration'},
    {'id': 'lib_grid_covers', 'cat': 'lib', 'es': 'Cuadrícula de covers', 'en': 'Cover grid'},
    {'id': 'lib_hidden_folders', 'cat': 'lib', 'es': 'Carpetas ocultas', 'en': 'Hidden folders'},
    {'id': 'lib_import_m3u', 'cat': 'lib', 'es': 'Importar M3U', 'en': 'Import M3U'},
    {'id': 'lib_export', 'cat': 'lib', 'es': 'Exportar biblioteca', 'en': 'Export library'},
    {'id': 'lib_stats', 'cat': 'lib', 'es': 'Estadísticas de escucha', 'en': 'Listening stats'},
    {'id': 'lib_wrap', 'cat': 'lib', 'es': 'Wrap semanal/mensual', 'en': 'Weekly/monthly wrap'},
    {'id': 'lib_pin_playlists', 'cat': 'lib', 'es': 'Fijar playlists', 'en': 'Pin playlists'},
  ];

  bool flag(String id, {bool def = false}) => flags[id] ?? def;

  void setFlag(String id, bool v) => flags[id] = v;

  Future<void> load() async {
    final p = await SharedPreferences.getInstance();
    liquidGlassEnabled = p.getBool('lg_enabled') ?? true;
    glassOpacity = p.getDouble('lg_opacity') ?? 0.12;
    glassBlur = p.getDouble('lg_blur') ?? 24;
    reduceAnimations = p.getBool('reduce_anim') ?? false;
    showMiniPlayer = p.getBool('mini_player') ?? true;
    locale = p.getString('locale') ?? 'es';
    playerFont = p.getString('player_font') ?? 'System';
    profileTheme = p.getString('profile_theme') ?? 'default';
    flags.clear();
    for (final item in catalog) {
      final id = item['id']!;
      flags[id] = p.getBool('flag_$id') ?? _defaultFor(id);
    }
  }

  bool _defaultFor(String id) {
    const on = {
      'ui_liquid_glass',
      'ui_specular',
      'ui_compact_nav',
      'ui_mini_player',
      'ui_bubble_nav',
      'ui_swipe_player',
      'ui_profile_gradient',
      'play_lyrics_auto',
      'play_karaoke',
      'play_lockscreen',
      'play_cast',
      'phone_haptics',
      'phone_bt_banner',
      'phone_notif_media',
      'phone_share_sheet',
      'exp_poki_dj',
      'exp_voice_dj',
      'exp_sse_progress',
      'exp_dual_player',
      'lib_stats',
      'lib_wrap',
      'priv_hide_nsfw',
    };
    return on.contains(id);
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
    await p.setString('profile_theme', profileTheme);
    for (final e in flags.entries) {
      await p.setBool('flag_${e.key}', e.value);
    }
    notifyListeners();
  }
}
