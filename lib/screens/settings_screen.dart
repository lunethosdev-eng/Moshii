import 'package:flutter/material.dart';
import 'package:pokke/services/settings_service.dart';
import 'package:pokke/services/audio_cache_service.dart';
import 'package:pokke/services/spotify_service.dart';
import 'package:pokke/widgets/liquid_glass.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  String t(String es, String en) => SettingsService.instance.locale == 'en' ? en : es;

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
              _section(t('Idioma', 'Language')),
              LiquidGlass(
                borderRadius: BorderRadius.circular(14),
                child: Column(
                  children: [
                    RadioListTile<String>(
                      value: 'es',
                      groupValue: s.locale,
                      activeColor: const Color(0xfffc3c44),
                      title: const Text('Español', style: TextStyle(color: Colors.white)),
                      onChanged: (v) async {
                        s.locale = v!;
                        await s.save();
                      },
                    ),
                    RadioListTile<String>(
                      value: 'en',
                      groupValue: s.locale,
                      activeColor: const Color(0xfffc3c44),
                      title: const Text('English', style: TextStyle(color: Colors.white)),
                      onChanged: (v) async {
                        s.locale = v!;
                        await s.save();
                      },
                    ),
                  ],
                ),
              ),
              _section(t('Apariencia', 'Appearance')),
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
              _section(t('Reproducción', 'Playback')),
              LiquidGlass(
                borderRadius: BorderRadius.circular(14),
                child: Column(
                  children: [
                    SwitchListTile(
                      activeColor: const Color(0xfffc3c44),
                      title: Text(t('Crossfade', 'Crossfade'),
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
                          value: s.crossfadeSeconds.clamp(2, 8),
                          min: 2,
                          max: 8,
                          divisions: 6,
                          activeColor: const Color(0xfffc3c44),
                          onChanged: (v) {
                            s.crossfadeSeconds = v;
                            s.save();
                          },
                        ),
                      ),
                  ],
                ),
              ),
              _section(t('Privacidad', 'Privacy')),
              LiquidGlass(
                borderRadius: BorderRadius.circular(14),
                child: Column(
                  children: [
                    SwitchListTile(
                      activeColor: const Color(0xfffc3c44),
                      title: Text(t('Historial local', 'Local history'),
                          style: const TextStyle(color: Colors.white)),
                      subtitle: Text(
                          t('Guardar lo que escuchas en el dispositivo',
                              'Save what you play on device'),
                          style: TextStyle(color: Colors.white.withOpacity(0.45), fontSize: 12)),
                      value: s.historyLocal,
                      onChanged: (v) => s.setFlag('priv_history_local', v),
                    ),
                    SwitchListTile(
                      activeColor: const Color(0xfffc3c44),
                      title: Text(t('Perfil público', 'Public profile'),
                          style: const TextStyle(color: Colors.white)),
                      value: s.profilePublic,
                      onChanged: (v) => s.setFlag('priv_profile_public', v),
                    ),
                    SwitchListTile(
                      activeColor: const Color(0xfffc3c44),
                      title: Text(t('Descargas solo Wi‑Fi', 'Downloads on Wi‑Fi only'),
                          style: const TextStyle(color: Colors.white)),
                      value: s.downloadsWifiOnly,
                      onChanged: (v) => s.setFlag('priv_download_wifi', v),
                    ),
                  ],
                ),
              ),
              _section(t('Almacenamiento', 'Storage')),
              LiquidGlass(
                borderRadius: BorderRadius.circular(14),
                child: ListTile(
                  leading: const Icon(Icons.delete_outline, color: Colors.white70),
                  title: Text(t('Caché de audio', 'Audio cache'),
                      style: const TextStyle(color: Colors.white)),
                  subtitle: FutureBuilder<int>(
                    future: AudioCacheService.instance.cachedSongCount(),
                    builder: (_, snap) {
                      final n = snap.data ?? 0;
                      return Text(
                        t('$n canciones en caché', '$n songs cached'),
                        style: TextStyle(color: Colors.white.withOpacity(0.45), fontSize: 12),
                      );
                    },
                  ),
                  trailing: Text(t('OK', 'OK'),
                      style: TextStyle(color: Colors.white.withOpacity(0.35))),
                ),
              ),
              _section(t('Cuentas vinculadas', 'Linked accounts')),
              LiquidGlass(
                borderRadius: BorderRadius.circular(14),
                child: ListTile(
                  leading: const Icon(Icons.library_music, color: Color(0xFF1DB954)),
                  title: Text(t('Spotify', 'Spotify'),
                      style: const TextStyle(color: Colors.white)),
                  subtitle: Text(
                    SpotifyService.instance.isLinked
                        ? t('Conectado', 'Connected')
                        : t('Importar playlists', 'Import playlists'),
                    style: TextStyle(color: Colors.white.withOpacity(0.45), fontSize: 12),
                  ),
                  trailing: TextButton(
                    onPressed: () async {
                      await SpotifyService.instance.load();
                      if (SpotifyService.instance.isLinked) {
                        await SpotifyService.instance.unlink();
                        if (context.mounted) {
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(content: Text(t('Spotify desconectado', 'Spotify disconnected'))),
                          );
                          setState(() {});
                        }
                        return;
                      }
                      final ok = await SpotifyService.instance.connect();
                      if (context.mounted) {
                        ScaffoldMessenger.of(context).showSnackBar(
                          SnackBar(
                            content: Text(ok
                                ? t('Spotify conectado', 'Spotify connected')
                                : _spotifyErrorText()),
                          ),
                        );
                        setState(() {});
                      }
                    },
                    child: Text(
                      SpotifyService.instance.isLinked
                          ? t('Salir', 'Unlink')
                          : t('Conectar', 'Connect'),
                      style: const TextStyle(color: Color(0xFF1DB954), fontWeight: FontWeight.w700),
                    ),
                  ),
                ),
              ),
              _section(t('Cuenta', 'Account')),
              LiquidGlass(
                borderRadius: BorderRadius.circular(14),
                child: Column(
                  children: [
                    ListTile(
                      leading: const Icon(Icons.person_outline, color: Colors.white70),
                      title: Text(t('Editar perfil', 'Edit profile'),
                          style: const TextStyle(color: Colors.white)),
                      trailing: const Icon(Icons.chevron_right, color: Colors.white38),
                      onTap: () => Navigator.pushNamed(context, '/profile_edit'),
                    ),
                    ListTile(
                      leading: const Icon(Icons.admin_panel_settings_outlined, color: Colors.white70),
                      title: Text(t('Admin', 'Admin'), style: const TextStyle(color: Colors.white)),
                      trailing: const Icon(Icons.chevron_right, color: Colors.white38),
                      onTap: () => Navigator.pushNamed(context, '/admin'),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 24),
              Center(
                child: Text('pokke · 1.0.0',
                    style: TextStyle(color: Colors.white.withOpacity(0.25), fontSize: 12)),
              ),
            ],
          ),
        );
      },
    );
  }


  String _spotifyErrorText() {
    final err = SpotifyService.instance.lastError;
    if (err == null || err.isEmpty) {
      return t(
        'No se pudo conectar. Revisa Client ID y redirect pokke://spotify-callback en Spotify Dashboard',
        'Could not connect. Check Client ID and pokke://spotify-callback in Spotify Dashboard',
      );
    }
    return err;
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
