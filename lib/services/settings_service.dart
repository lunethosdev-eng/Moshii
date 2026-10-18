import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Settings that are actually wired to UI / playback / privacy.
class SettingsService extends ChangeNotifier {
  static final SettingsService instance = SettingsService._();
  SettingsService._();

  // Appearance
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

  // Privacy / playback
  bool historyLocal = true;
  bool profilePublic = true;
  bool downloadsWifiOnly = true;
  bool crossfade = true;
  double crossfadeSeconds = 4;

  // ── Player customization ──────────────────────────────────────────
  /// minimal | liquid | bold | timeline | hidden
  String sliderStyle = 'liquid';
  /// circle | line | hidden
  String sliderThumb = 'circle';
  /// sides | center | hidden
  String timeDisplay = 'sides';
  /// large | medium | rotating | blur_bg
  String coverStyle = 'large';
  /// below | beside | chip
  String artistStyle = 'below';
  bool karaokeOn = true;
  /// blur | solid | gradient
  String playerBackground = 'blur';
  /// Ordered blocks for full player layout
  List<String> playerLayout = const [
    'cover',
    'title',
    'slider',
    'controls',
    'artist',
    'lyrics',
  ];

  // ── Playlist defaults ─────────────────────────────────────────────
  /// compact | comfortable | large
  String playlistListLayout = 'comfortable';
  bool playlistShowNumbers = true;
  bool playlistDragHandles = true;
  /// manual | recent | az | most_played
  String playlistDefaultSort = 'manual';
  /// private | link | public
  String playlistDefaultPrivacy = 'private';

  // ── Home / Library / Nav ──────────────────────────────────────────
  bool homeSectionsEnabled = true;
  bool libraryGrid = false;
  bool libraryRecentsFirst = true;
  bool showRadioTab = true;
  bool searchHistory = true;
  bool compactHeaderOnScroll = true;
  bool navLabels = true;
  bool navBubble = true;
  /// compact | normal | dense
  String homeCardDensity = 'normal';

  static const catalog = <Map<String, String>>[
    {'id': 'ui_liquid_glass', 'cat': 'appearance', 'es': 'Liquid Glass', 'en': 'Liquid Glass'},
    {'id': 'ui_amoled', 'cat': 'appearance', 'es': 'Tema AMOLED', 'en': 'AMOLED theme'},
    {'id': 'ui_reduce_anim', 'cat': 'appearance', 'es': 'Reducir animaciones', 'en': 'Reduce animations'},
    {'id': 'ui_mini_player', 'cat': 'appearance', 'es': 'Mini player', 'en': 'Mini player'},
    {'id': 'play_crossfade', 'cat': 'playback', 'es': 'Crossfade', 'en': 'Crossfade'},
    {'id': 'priv_history_local', 'cat': 'privacy', 'es': 'Historial local', 'en': 'Local history'},
    {'id': 'priv_profile_public', 'cat': 'privacy', 'es': 'Perfil público', 'en': 'Public profile'},
    {'id': 'priv_download_wifi', 'cat': 'privacy', 'es': 'Descargas solo Wi‑Fi', 'en': 'Downloads on Wi‑Fi only'},
    {'id': 'nav_labels', 'cat': 'nav', 'es': 'Etiquetas del nav', 'en': 'Nav labels'},
    {'id': 'nav_bubble', 'cat': 'nav', 'es': 'Burbuja Liquid', 'en': 'Liquid bubble'},
    {'id': 'ui_compact_header', 'cat': 'nav', 'es': 'Header compacto al scroll', 'en': 'Compact header on scroll'},
    {'id': 'ui_show_radio', 'cat': 'nav', 'es': 'Mostrar Radio', 'en': 'Show Radio'},
    {'id': 'ui_search_history', 'cat': 'nav', 'es': 'Historial de búsqueda', 'en': 'Search history'},
    {'id': 'lib_grid', 'cat': 'library', 'es': 'Library en cuadrícula', 'en': 'Library grid'},
    {'id': 'lib_recents_first', 'cat': 'library', 'es': 'Recientes primero', 'en': 'Recents first'},
    {'id': 'pl_numbers', 'cat': 'playlist', 'es': 'Numeración en playlists', 'en': 'Playlist numbers'},
    {'id': 'pl_drag', 'cat': 'playlist', 'es': 'Asas de arrastre', 'en': 'Drag handles'},
    {'id': 'play_karaoke', 'cat': 'playback', 'es': 'Karaoke', 'en': 'Karaoke'},
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

    sliderStyle = p.getString('slider_style') ?? 'liquid';
    sliderThumb = p.getString('slider_thumb') ?? 'circle';
    timeDisplay = p.getString('time_display') ?? 'sides';
    coverStyle = p.getString('cover_style') ?? 'large';
    artistStyle = p.getString('artist_style') ?? 'below';
    karaokeOn = p.getBool('karaoke_on') ?? true;
    playerBackground = p.getString('player_bg') ?? 'blur';
    final layout = p.getStringList('player_layout');
    if (layout != null && layout.isNotEmpty) playerLayout = layout;

    playlistListLayout = p.getString('pl_list_layout') ?? 'comfortable';
    playlistShowNumbers = p.getBool('pl_numbers') ?? true;
    playlistDragHandles = p.getBool('pl_drag') ?? true;
    playlistDefaultSort = p.getString('pl_sort') ?? 'manual';
    playlistDefaultPrivacy = p.getString('pl_privacy') ?? 'private';

    homeSectionsEnabled = p.getBool('home_sections') ?? true;
    libraryGrid = p.getBool('lib_grid') ?? false;
    libraryRecentsFirst = p.getBool('lib_recents') ?? true;
    showRadioTab = p.getBool('show_radio') ?? true;
    searchHistory = p.getBool('search_history') ?? true;
    compactHeaderOnScroll = p.getBool('compact_header') ?? true;
    navLabels = p.getBool('nav_labels') ?? true;
    navBubble = p.getBool('nav_bubble') ?? true;
    homeCardDensity = p.getString('home_density') ?? 'normal';

    for (final item in catalog) {
      final id = item['id']!;
      flags[id] = p.getBool('flag_$id') ?? _defaultFor(id);
    }
    flags['ui_liquid_glass'] = liquidGlassEnabled;
    flags['ui_amoled'] = amoled;
    flags['ui_reduce_anim'] = reduceAnimations;
    flags['ui_mini_player'] = showMiniPlayer;
    flags['play_crossfade'] = crossfade;
    flags['priv_history_local'] = historyLocal;
    flags['priv_profile_public'] = profilePublic;
    flags['priv_download_wifi'] = downloadsWifiOnly;
    flags['nav_labels'] = navLabels;
    flags['nav_bubble'] = navBubble;
    flags['ui_compact_header'] = compactHeaderOnScroll;
    flags['ui_show_radio'] = showRadioTab;
    flags['ui_search_history'] = searchHistory;
    flags['lib_grid'] = libraryGrid;
    flags['lib_recents_first'] = libraryRecentsFirst;
    flags['pl_numbers'] = playlistShowNumbers;
    flags['pl_drag'] = playlistDragHandles;
    flags['play_karaoke'] = karaokeOn;
  }

  bool _defaultFor(String id) {
    const on = {
      'ui_liquid_glass',
      'ui_mini_player',
      'play_crossfade',
      'priv_history_local',
      'priv_profile_public',
      'priv_download_wifi',
      'nav_labels',
      'nav_bubble',
      'ui_compact_header',
      'ui_show_radio',
      'ui_search_history',
      'lib_recents_first',
      'pl_numbers',
      'pl_drag',
      'play_karaoke',
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
      case 'nav_labels':
        navLabels = v;
        break;
      case 'nav_bubble':
        navBubble = v;
        break;
      case 'ui_compact_header':
        compactHeaderOnScroll = v;
        break;
      case 'ui_show_radio':
        showRadioTab = v;
        break;
      case 'ui_search_history':
        searchHistory = v;
        break;
      case 'lib_grid':
        libraryGrid = v;
        break;
      case 'lib_recents_first':
        libraryRecentsFirst = v;
        break;
      case 'pl_numbers':
        playlistShowNumbers = v;
        break;
      case 'pl_drag':
        playlistDragHandles = v;
        break;
      case 'play_karaoke':
        karaokeOn = v;
        break;
    }
    await save();
  }

  Future<void> setString(String key, String value) async {
    switch (key) {
      case 'slider_style':
        sliderStyle = value;
        break;
      case 'slider_thumb':
        sliderThumb = value;
        break;
      case 'time_display':
        timeDisplay = value;
        break;
      case 'cover_style':
        coverStyle = value;
        break;
      case 'artist_style':
        artistStyle = value;
        break;
      case 'player_bg':
        playerBackground = value;
        break;
      case 'pl_list_layout':
        playlistListLayout = value;
        break;
      case 'pl_sort':
        playlistDefaultSort = value;
        break;
      case 'pl_privacy':
        playlistDefaultPrivacy = value;
        break;
      case 'home_density':
        homeCardDensity = value;
        break;
      case 'locale':
        locale = value;
        break;
      case 'profile_effect':
        profileEffect = value;
        break;
      case 'profile_theme':
        profileTheme = value;
        break;
      case 'player_font':
        playerFont = value;
        break;
      default:
        break;
    }
    await save();
  }

  Future<void> setPlayerLayout(List<String> layout) async {
    playerLayout = List<String>.from(layout);
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

    await p.setString('slider_style', sliderStyle);
    await p.setString('slider_thumb', sliderThumb);
    await p.setString('time_display', timeDisplay);
    await p.setString('cover_style', coverStyle);
    await p.setString('artist_style', artistStyle);
    await p.setBool('karaoke_on', karaokeOn);
    await p.setString('player_bg', playerBackground);
    await p.setStringList('player_layout', playerLayout);

    await p.setString('pl_list_layout', playlistListLayout);
    await p.setBool('pl_numbers', playlistShowNumbers);
    await p.setBool('pl_drag', playlistDragHandles);
    await p.setString('pl_sort', playlistDefaultSort);
    await p.setString('pl_privacy', playlistDefaultPrivacy);

    await p.setBool('home_sections', homeSectionsEnabled);
    await p.setBool('lib_grid', libraryGrid);
    await p.setBool('lib_recents', libraryRecentsFirst);
    await p.setBool('show_radio', showRadioTab);
    await p.setBool('search_history', searchHistory);
    await p.setBool('compact_header', compactHeaderOnScroll);
    await p.setBool('nav_labels', navLabels);
    await p.setBool('nav_bubble', navBubble);
    await p.setString('home_density', homeCardDensity);

    for (final e in flags.entries) {
      await p.setBool('flag_${e.key}', e.value);
    }
    notifyListeners();
  }
}
