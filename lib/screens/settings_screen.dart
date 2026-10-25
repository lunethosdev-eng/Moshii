import 'package:flutter/material.dart';
import 'package:pokke/services/settings_service.dart';
import 'package:pokke/services/notification_service.dart';
import 'package:pokke/services/audio_cache_service.dart';
import 'package:pokke/services/spotify_service.dart';
import 'package:pokke/screens/spotify_web_import_screen.dart';
import 'package:pokke/screens/listen_together_screen.dart';
import 'package:pokke/widgets/liquid_glass.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  @override
  void initState() {
    super.initState();
    SpotifyService.instance.load().then((_) {
      if (mounted) setState(() {});
    });
  }

  String t(String es, String en) =>
      SettingsService.instance.locale == 'en' ? en : es;

  String _spotifyErrorText() {
    final err = SpotifyService.instance.friendlyError.isNotEmpty ? SpotifyService.instance.friendlyError : (SpotifyService.instance.lastError ?? "");
    if (err == null || err.isEmpty) {
      return t(
        'No se pudo conectar. Revisa Client ID y redirect pokke://spotify-callback en Spotify Dashboard',
        'Could not connect. Check Client ID and pokke://spotify-callback in Spotify Dashboard',
      );
    }
    return err;
  }


  Future<void> _importSpotifyWithPicker(BuildContext context) async {
    String t(String es, String en) =>
        SettingsService.instance.locale == 'en' ? en : es;
    final lists = await SpotifyService.instance.fetchPlaylists();
    if (!mounted) return;
    if (lists.isEmpty) {
      final err = SpotifyService.instance.friendlyError.isNotEmpty ? SpotifyService.instance.friendlyError : (SpotifyService.instance.lastError ?? "");
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            err != null && err.isNotEmpty
                ? err
                : t(
                    'No hay playlists (¿permisos? vuelve a Conectar)',
                    'No playlists (permissions? reconnect)',
                  ),
          ),
          backgroundColor: const Color(0xFF3A1A1A),
        ),
      );
      return;
    }
    final selected = <String>{};
    var includeLiked = true;
    final ok = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      backgroundColor: const Color(0xFF121214),
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(18)),
      ),
      builder: (ctx) {
        return StatefulBuilder(
          builder: (ctx, setSt) {
            return SafeArea(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(16, 12, 16, 16),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Container(
                      width: 40,
                      height: 4,
                      decoration: BoxDecoration(
                        color: Colors.white24,
                        borderRadius: BorderRadius.circular(2),
                      ),
                    ),
                    const SizedBox(height: 12),
                    Text(
                      t('Elige playlists a importar', 'Pick playlists to import'),
                      style: const TextStyle(
                        color: Colors.white,
                        fontWeight: FontWeight.w800,
                        fontSize: 16,
                      ),
                    ),
                    const SizedBox(height: 8),
                    CheckboxListTile(
                      value: includeLiked,
                      activeColor: const Color(0xFF1DB954),
                      title: Text(t('Liked Songs', 'Liked Songs'),
                          style: const TextStyle(color: Colors.white)),
                      onChanged: (v) => setSt(() => includeLiked = v ?? true),
                    ),
                    SizedBox(
                      height: MediaQuery.of(ctx).size.height * 0.45,
                      child: ListView.builder(
                        itemCount: lists.length,
                        itemBuilder: (_, i) {
                          final pl = lists[i];
                          final id = (pl['id'] ?? '').toString();
                          final name = (pl['name'] ?? 'Playlist').toString();
                          final total = pl['tracks']?['total'];
                          final checked = selected.contains(id);
                          return CheckboxListTile(
                            value: checked,
                            activeColor: const Color(0xFF1DB954),
                            title: Text(name,
                                style: const TextStyle(color: Colors.white)),
                            subtitle: total != null
                                ? Text(
                                    '$total tracks',
                                    style: TextStyle(
                                        color: Colors.white.withOpacity(0.45),
                                        fontSize: 12),
                                  )
                                : null,
                            onChanged: (v) {
                              setSt(() {
                                if (v == true) {
                                  selected.add(id);
                                } else {
                                  selected.remove(id);
                                }
                              });
                            },
                          );
                        },
                      ),
                    ),
                    const SizedBox(height: 8),
                    SizedBox(
                      width: double.infinity,
                      height: 48,
                      child: ElevatedButton(
                        style: ElevatedButton.styleFrom(
                          backgroundColor: const Color(0xFF1DB954),
                          foregroundColor: Colors.black,
                        ),
                        onPressed: () => Navigator.pop(ctx, true),
                        child: Text(
                          t('Importar seleccionadas', 'Import selected'),
                          style: const TextStyle(fontWeight: FontWeight.w800),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            );
          },
        );
      },
    );
    if (ok != true || !mounted) return;
    if (selected.isEmpty && !includeLiked) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(t('No elegiste ninguna', 'None selected'))),
      );
      return;
    }
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (_) => AlertDialog(
        backgroundColor: const Color(0xFF1A1A1E),
        content: Row(
          children: [
            const CircularProgressIndicator(color: Color(0xFF1DB954)),
            const SizedBox(width: 16),
            Expanded(
              child: Text(
                t('Importando Spotify…', 'Importing Spotify…'),
                style: const TextStyle(color: Colors.white),
              ),
            ),
          ],
        ),
      ),
    );
    try {
      final n = await SpotifyService.instance.importSelectedPlaylists(
        selected.toList(),
        includeLiked: includeLiked,
      );
      if (context.mounted) {
        Navigator.pop(context);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              t('Importadas: $n playlists', 'Imported: $n playlists'),
            ),
          ),
        );
      }
    } catch (e) {
      if (context.mounted) {
        Navigator.pop(context);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('$e')),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final s = SettingsService.instance;
    return AnimatedBuilder(
      animation: s,
      builder: (context, _) {
        return Scaffold(
          backgroundColor: s.amoled ? Colors.black : const Color(0xFF0C0C0E),
          appBar: AppBar(
            title: Text(t('Ajustes', 'Settings')),
            backgroundColor: Colors.transparent,
          ),
          body: ListView(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 40),
            children: [
              // ── IDIOMA ────────────────────────────────────────
              _section(t('Idioma', 'Language')),
              LiquidGlass(
                borderRadius: BorderRadius.circular(14),
                child: Column(
                  children: [
                    RadioListTile<String>(
                      value: 'es',
                      groupValue: s.locale,
                      activeColor: const Color(0xfffc3c44),
                      title: const Text('Español',
                          style: TextStyle(color: Colors.white)),
                      onChanged: (v) async {
                        s.locale = v!;
                        await s.save();
                      },
                    ),
                    RadioListTile<String>(
                      value: 'en',
                      groupValue: s.locale,
                      activeColor: const Color(0xfffc3c44),
                      title: const Text('English',
                          style: TextStyle(color: Colors.white)),
                      onChanged: (v) async {
                        s.locale = v!;
                        await s.save();
                      },
                    ),
                  ],
                ),
              ),

              // ── APARIENCIA RÁPIDA ─────────────────────────────
              _section(t('Apariencia rápida', 'Quick appearance')),
              LiquidGlass(
                borderRadius: BorderRadius.circular(14),
                child: Column(
                  children: [
                    SwitchListTile(
                      activeColor: const Color(0xfffc3c44),
                      title: Text(t('Liquid Glass', 'Liquid Glass'),
                          style: const TextStyle(color: Colors.white)),
                      value: s.liquidGlassEnabled,
                      onChanged: (v) => s.setFlag('ui_liquid_glass', v),
                    ),
                    if (s.liquidGlassEnabled) ...[
                      ListTile(
                        title: Text(t('Opacidad', 'Opacity'),
                            style: const TextStyle(color: Colors.white70)),
                        subtitle: Slider(
                          value: s.glassOpacity.clamp(0.04, 0.4),
                          min: 0.04,
                          max: 0.4,
                          activeColor: const Color(0xfffc3c44),
                          onChanged: (v) {
                            s.glassOpacity = v;
                            s.save();
                          },
                        ),
                      ),
                      ListTile(
                        title: Text(t('Blur', 'Blur'),
                            style: const TextStyle(color: Colors.white70)),
                        subtitle: Slider(
                          value: s.glassBlur.clamp(8, 40),
                          min: 8,
                          max: 40,
                          activeColor: const Color(0xfffc3c44),
                          onChanged: (v) {
                            s.glassBlur = v;
                            s.save();
                          },
                        ),
                      ),
                    ],
                    SwitchListTile(
                      activeColor: const Color(0xfffc3c44),
                      title: Text(t('Tema AMOLED', 'AMOLED theme'),
                          style: const TextStyle(color: Colors.white)),
                      value: s.amoled,
                      onChanged: (v) => s.setFlag('ui_amoled', v),
                    ),
                    SwitchListTile(
                      activeColor: const Color(0xfffc3c44),
                      title: Text(t('Reducir animaciones', 'Reduce animations'),
                          style: const TextStyle(color: Colors.white)),
                      value: s.reduceAnimations,
                      onChanged: (v) => s.setFlag('ui_reduce_anim', v),
                    ),
                    SwitchListTile(
                      activeColor: const Color(0xfffc3c44),
                      title: Text(t('Mini player', 'Mini player'),
                          style: const TextStyle(color: Colors.white)),
                      value: s.showMiniPlayer,
                      onChanged: (v) => s.setFlag('ui_mini_player', v),
                    ),
                  ],
                ),
              ),

              // ── PLAYBACK ──────────────────────────────────────
              _section(t('Reproducción', 'Playback')),
              LiquidGlass(
                borderRadius: BorderRadius.circular(14),
                child: Column(
                  children: [
                    SwitchListTile(
                      activeColor: const Color(0xfffc3c44),
                      title: Text(t('Crossfade entre canciones', 'Crossfade between songs'),
                          style: const TextStyle(color: Colors.white)),
                      value: s.crossfade,
                      onChanged: (v) => s.setFlag('play_crossfade', v),
                    ),
                    if (s.crossfade)
                      ListTile(
                        title: Text(
                            t('Duración (${s.crossfadeSeconds.round()}s)',
                                'Duration (${s.crossfadeSeconds.round()}s)'),
                            style: const TextStyle(color: Colors.white70)),
                        subtitle: Slider(
                          value: s.crossfadeSeconds.clamp(2, 12),
                          min: 2,
                          max: 12,
                          activeColor: const Color(0xfffc3c44),
                          onChanged: (v) {
                            s.crossfadeSeconds = v;
                            s.save();
                          },
                        ),
                      ),
                    SwitchListTile(
                      activeColor: const Color(0xfffc3c44),
                      title: Text(t('Karaoke', 'Karaoke'),
                          style: const TextStyle(color: Colors.white)),
                      value: s.karaokeOn,
                      onChanged: (v) => s.setFlag('play_karaoke', v),
                    ),
                  ],
                ),
              ),

              // ── CACHE ─────────────────────────────────────────
              _section(t('Almacenamiento', 'Storage')),
              LiquidGlass(
                borderRadius: BorderRadius.circular(14),
                child: ListTile(
                  leading: const Icon(Icons.sd_storage_outlined, color: Colors.white70),
                  title: Text(t('Caché de audio', 'Audio cache'),
                      style: const TextStyle(color: Colors.white)),
                  subtitle: FutureBuilder<int>(
                    future: AudioCacheService.instance.cachedSongCount(),
                    builder: (_, snap) {
                      final n = snap.data ?? 0;
                      return Text(
                        t('$n canciones en caché', '$n songs cached'),
                        style: TextStyle(
                            color: Colors.white.withOpacity(0.45), fontSize: 12),
                      );
                    },
                  ),
                ),
              ),

              // ── SPOTIFY ───────────────────────────────────────
                                          _section(t('Cuentas vinculadas', 'Linked accounts')),
              LiquidGlass(
                borderRadius: BorderRadius.circular(14),
                child: ListTile(
                  leading: const Icon(Icons.library_music, color: Color(0xFF1DB954)),
                  title: Text(t('Spotify (Web)', 'Spotify (Web)'),
                      style: const TextStyle(color: Colors.white)),
                  subtitle: Text(
                    t(
                      'Sin API · inicia sesión en un navegador in-app, extrae playlists y las busca en Pokke.',
                      'No API · sign in via in-app browser, extract playlists, match in Pokke.',
                    ),
                    style: TextStyle(color: Colors.white.withOpacity(0.45), fontSize: 12),
                  ),
                  trailing: const Icon(Icons.chevron_right, color: Colors.white38),
                  onTap: () {
                    Navigator.push(
                      context,
                      MaterialPageRoute(
                          builder: (_) => const SpotifyWebImportScreen()),
                    );
                  },
                ),
              ),
              const SizedBox(height: 10),
              LiquidGlass(
                borderRadius: BorderRadius.circular(14),
                child: ListTile(
                  leading: const Icon(Icons.headphones, color: Color(0xfffc3c44)),
                  title: Text(t('Escuchar juntos', 'Listen together'),
                      style: const TextStyle(color: Colors.white)),
                  subtitle: Text(
                    t('Beta · salas con código y sesión dual',
                        'Beta · room codes and dual session'),
                    style: TextStyle(color: Colors.white.withOpacity(0.45), fontSize: 12),
                  ),
                  trailing: const Icon(Icons.chevron_right, color: Colors.white38),
                  onTap: () {
                    Navigator.push(
                      context,
                      MaterialPageRoute(
                          builder: (_) => const ListenTogetherScreen()),
                    );
                  },
                ),
              ),

              // ── EDITORES ──────────────────────────────────────
              _section(t('Estudio', 'Studio')),
              Text(
                t(
                  'Cada editor abre una pantalla completa para diseñar esa parte de la app. No son interruptores sueltos.',
                  'Each editor opens a full screen to design that part of the app. Not loose toggles.',
                ),
                style: TextStyle(
                    color: Colors.white.withOpacity(0.42), fontSize: 12, height: 1.35),
              ),
              const SizedBox(height: 12),
              _editorCard(
                icon: Icons.graphic_eq_rounded,
                color: const Color(0xFF2EE6A6),
                title: t('Editor de Mezcla', 'Mix Editor'),
                subtitle: t(
                  'Combina dos canciones con timeline, punto de corte y crossfade real.',
                  'Blend two tracks with timeline, cut point and real crossfade.',
                ),
                where: t(
                  'También desde el menú ⋮ del player → Combinar.',
                  'Also from player ⋮ menu → Blend.',
                ),
                onTap: () => Navigator.pushNamed(context, '/mix'),
              ),
              _editorCard(
                icon: Icons.queue_music_rounded,
                color: const Color(0xFF6C8CFF),
                title: t('Playlists · defaults', 'Playlists · defaults'),
                subtitle: t(
                  'Densidad de lista, numeración, asas, orden y privacidad por defecto.',
                  'List density, numbers, handles, sort and default privacy.',
                ),
                where: t(
                  'Afecta todas las playlists nuevas y la vista de detalle.',
                  'Affects new playlists and the detail view.',
                ),
                onTap: () => _openPlaylistDefaults(),
              ),

              // ── ACCOUNT ───────────────────────────────────────
              _section(t('Cuenta', 'Account')),
              LiquidGlass(
                borderRadius: BorderRadius.circular(14),
                child: Column(
                  children: [
                    ListTile(
                      leading:
                          const Icon(Icons.person_outline, color: Colors.white70),
                      title: Text(t('Editar perfil', 'Edit profile'),
                          style: const TextStyle(color: Colors.white)),
                      trailing:
                          const Icon(Icons.chevron_right, color: Colors.white38),
                      onTap: () => Navigator.pushNamed(context, '/profile_edit'),
                    ),
                    ListTile(
                      leading: const Icon(Icons.admin_panel_settings_outlined,
                          color: Colors.white70),
                      title: Text(t('Admin', 'Admin'),
                          style: const TextStyle(color: Colors.white)),
                      trailing:
                          const Icon(Icons.chevron_right, color: Colors.white38),
                      onTap: () => Navigator.pushNamed(context, '/admin'),
                    ),
                  ],
                ),
              ),
              _section(t('Notificaciones', 'Notifications')),
              LiquidGlass(
                borderRadius: BorderRadius.circular(14),
                child: Column(
                  children: [
                    SwitchListTile(
                      activeColor: const Color(0xfffc3c44),
                      title: Text(t('Sonido de notificación', 'Notification sound'),
                          style: const TextStyle(color: Colors.white)),
                      value: s.notificationSoundEnabled,
                      onChanged: (v) async {
                        s.notificationSoundEnabled = v;
                        await s.save();
                      },
                    ),
                    ...['none', 'message', 'joyous', 'bubble', 'twinkle', 'marimba', 'chime'].map((id) {
                      final labels = NotificationService.soundLabels[id] ?? {'es': id, 'en': id};
                      final label = s.locale == 'en' ? labels['en']! : labels['es']!;
                      return RadioListTile<String>(
                        value: id,
                        groupValue: s.notificationSound,
                        activeColor: const Color(0xfffc3c44),
                        title: Text(label, style: const TextStyle(color: Colors.white)),
                        secondary: id == 'none'
                            ? null
                            : IconButton(
                                icon: const Icon(Icons.play_arrow, color: Colors.white54),
                                onPressed: () => NotificationService.instance.previewSound(id),
                              ),
                        onChanged: (v) async {
                          if (v == null) return;
                          s.notificationSound = v;
                          await s.save();
                          if (v != 'none') {
                            await NotificationService.instance.previewSound(v);
                          }
                        },
                      );
                    }),
                  ],
                ),
              ),

              // ── BETA ─────────────────────────────────────────
              _section(t('Beta / experimental', 'Beta / experimental')),
              LiquidGlass(
                borderRadius: BorderRadius.circular(14),
                child: Column(
                  children: [
                    SwitchListTile(
                      activeColor: const Color(0xfffc3c44),
                      title: Text(t('IA (traducir / karaoke)', 'AI (translate / karaoke)'),
                          style: const TextStyle(color: Colors.white)),
                      subtitle: Text(
                        t('Traducción de letras y sync de karaoke',
                            'Lyrics translation & karaoke sync'),
                        style: TextStyle(color: Colors.white.withOpacity(0.45), fontSize: 12),
                      ),
                      value: s.flag('beta_ai', def: true),
                      onChanged: (v) => s.setFlag('beta_ai', v),
                    ),
                    SwitchListTile(
                      activeColor: const Color(0xfffc3c44),
                      title: Text(t('Nav colapsa al scroll', 'Collapse nav on scroll'),
                          style: const TextStyle(color: Colors.white)),
                      subtitle: Text(
                        t('Home+Library cortos y Search se separa',
                            'Short Home+Library, Search peels off'),
                        style: TextStyle(color: Colors.white.withOpacity(0.45), fontSize: 12),
                      ),
                      value: s.flag('beta_nav_collapse', def: true),
                      onChanged: (v) => s.setFlag('beta_nav_collapse', v),
                    ),
                    SwitchListTile(
                      activeColor: const Color(0xfffc3c44),
                      title: Text(t('Dismiss con blur', 'Blur dismiss'),
                          style: const TextStyle(color: Colors.white)),
                      subtitle: Text(
                        t('Al bajar el full player se ve el home',
                            'Pulling down full player reveals home'),
                        style: TextStyle(color: Colors.white.withOpacity(0.45), fontSize: 12),
                      ),
                      value: s.flag('beta_player_blur', def: true),
                      onChanged: (v) => s.setFlag('beta_player_blur', v),
                    ),
                    SwitchListTile(
                      activeColor: const Color(0xfffc3c44),
                      title: Text(t('Cola inteligente', 'Smart queue'),
                          style: const TextStyle(color: Colors.white)),
                      value: s.flag('beta_smart_queue', def: true),
                      onChanged: (v) => s.setFlag('beta_smart_queue', v),
                    ),
                    SwitchListTile(
                      activeColor: const Color(0xfffc3c44),
                      title: Text(t('Etiquetas del nav', 'Nav labels'),
                          style: const TextStyle(color: Colors.white)),
                      value: s.flag('nav_labels', def: true),
                      onChanged: (v) => s.setFlag('nav_labels', v),
                    ),
                  ],
                ),
              ),

              const SizedBox(height: 24),
              Center(
                child: Text('pokke · 1.0.0',
                    style: TextStyle(
                        color: Colors.white.withOpacity(0.25), fontSize: 12)),
              ),
            ],
          ),
        );
      },
    );
  }

  void _openPlaylistDefaults() {
    final s = SettingsService.instance;
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      isScrollControlled: true,
      builder: (ctx) {
        return LiquidGlass(
          blur: 28,
          opacity: 0.14,
          borderRadius: const BorderRadius.vertical(top: Radius.circular(22)),
          child: StatefulBuilder(
            builder: (ctx, setModal) {
              return Padding(
                padding: const EdgeInsets.fromLTRB(16, 12, 16, 28),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Center(
                      child: Container(
                        width: 40,
                        height: 4,
                        decoration: BoxDecoration(
                            color: Colors.white24,
                            borderRadius: BorderRadius.circular(2)),
                      ),
                    ),
                    const SizedBox(height: 14),
                    Text(t('Defaults de playlist', 'Playlist defaults'),
                        style: const TextStyle(
                            color: Colors.white,
                            fontWeight: FontWeight.w800,
                            fontSize: 18)),
                    const SizedBox(height: 12),
                    Text(t('Layout', 'Layout'),
                        style: TextStyle(color: Colors.white.withOpacity(0.6))),
                    Wrap(
                      spacing: 8,
                      children: [
                        for (final o in [
                          ('compact', 'Compacta'),
                          ('comfortable', 'Cómoda'),
                          ('large', 'Grande'),
                        ])
                          ChoiceChip(
                            label: Text(o.$2),
                            selected: s.playlistListLayout == o.$1,
                            selectedColor: const Color(0xfffc3c44),
                            labelStyle: const TextStyle(color: Colors.white),
                            backgroundColor: Colors.white12,
                            onSelected: (_) async {
                              await s.setString('pl_list_layout', o.$1);
                              setModal(() {});
                            },
                          ),
                      ],
                    ),
                    SwitchListTile(
                      contentPadding: EdgeInsets.zero,
                      activeColor: const Color(0xfffc3c44),
                      title: Text(t('Numeración', 'Numbers'),
                          style: const TextStyle(color: Colors.white)),
                      value: s.playlistShowNumbers,
                      onChanged: (v) async {
                        await s.setFlag('pl_numbers', v);
                        setModal(() {});
                      },
                    ),
                    SwitchListTile(
                      contentPadding: EdgeInsets.zero,
                      activeColor: const Color(0xfffc3c44),
                      title: Text(t('Asas de arrastre', 'Drag handles'),
                          style: const TextStyle(color: Colors.white)),
                      value: s.playlistDragHandles,
                      onChanged: (v) async {
                        await s.setFlag('pl_drag', v);
                        setModal(() {});
                      },
                    ),
                  ],
                ),
              );
            },
          ),
        );
      },
    );
  }

  Widget _editorCard({
    required IconData icon,
    required Color color,
    required String title,
    required String subtitle,
    required String where,
    required VoidCallback onTap,
  }) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: Material(
        color: const Color(0xFF141418),
        borderRadius: BorderRadius.circular(16),
        child: InkWell(
          borderRadius: BorderRadius.circular(16),
          onTap: onTap,
          child: Container(
            padding: const EdgeInsets.all(14),
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(16),
              border: Border.all(color: Colors.white.withOpacity(0.08)),
            ),
            child: Row(
              children: [
                Container(
                  width: 48,
                  height: 48,
                  decoration: BoxDecoration(
                    color: Colors.white.withOpacity(0.06),
                    borderRadius: BorderRadius.circular(14),
                    border: Border.all(color: Colors.white.withOpacity(0.08)),
                  ),
                  child: Icon(icon, color: Colors.white70, size: 24),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(title,
                          style: const TextStyle(
                              color: Colors.white,
                              fontWeight: FontWeight.w800,
                              fontSize: 15)),
                      const SizedBox(height: 3),
                      Text(subtitle,
                          style: TextStyle(
                              color: Colors.white.withOpacity(0.55),
                              fontSize: 12,
                              height: 1.3)),
                      const SizedBox(height: 4),
                      Text(where,
                          style: TextStyle(
                              color: Colors.white.withOpacity(0.35),
                              fontSize: 11,
                              fontWeight: FontWeight.w500)),
                    ],
                  ),
                ),
                Icon(Icons.arrow_forward_ios_rounded,
                    size: 16, color: Colors.white.withOpacity(0.35)),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _section(String title) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(4, 22, 4, 10),
      child: Text(title,
          style: TextStyle(
            color: Colors.white.withOpacity(0.5),
            fontSize: 13,
            fontWeight: FontWeight.w700,
            letterSpacing: 0.6,
          )),
    );
  }
}
