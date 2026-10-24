import 'package:shared_preferences/shared_preferences.dart';
import 'package:flutter/material.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:pokke/services/supabase_service.dart';
import 'package:pokke/services/settings_service.dart';
import 'package:pokke/services/listening_history.dart';
import 'package:pokke/services/spotify_service.dart';
import 'package:pokke/services/player_controller.dart';
import 'package:pokke/services/local_music_service.dart';
import 'package:pokke/services/notification_service.dart';
import 'package:pokke/services/media_audio_handler.dart';
import 'package:app_links/app_links.dart';
import 'package:pokke/screens/auth_screen.dart';
import 'package:pokke/screens/welcome_screen.dart';
import 'package:pokke/Bottom%20NavBar/bottom_nav.dart';
import 'package:pokke/pages/list.dart';
import 'package:pokke/pages/album_page.dart';
import 'package:pokke/pages/artist_list.dart';
import 'package:pokke/pages/artist_page.dart';
import 'package:pokke/pages/browse_page.dart';
import 'package:pokke/pages/home_page.dart';
import 'package:pokke/pages/music_list.dart';
import 'package:pokke/pages/music_player.dart';
import 'package:pokke/pages/radio.dart';
import 'package:pokke/pages/search_page.dart';
import 'package:pokke/pages/library_page.dart';
import 'package:pokke/screens/profile_edit_screen.dart';
import 'package:pokke/screens/admin_screen.dart';
import 'package:pokke/screens/settings_screen.dart';
import 'package:pokke/screens/mix_editor_screen.dart';
import 'package:pokke/screens/player_layout_editor_screen.dart';
import 'package:pokke/screens/playlists_screen.dart';
import 'package:pokke/screens/wrap_screen.dart';
import 'package:pokke/screens/local_import_screen.dart';
import 'package:pokke/screens/notifications_screen.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await SupabaseService.init();
  await SettingsService.instance.load();
  await ListeningHistory.instance.load();
  await SpotifyService.instance.load();
  await PlayerController.instance.restoreSession();
  await LocalMusicService.instance.load();
  await NotificationService.instance.load();
  // background check for followed-artist releases
  NotificationService.instance.refreshFromFollows();
  await initMediaAudioService();
  SystemChrome.setSystemUIOverlayStyle(const SystemUiOverlayStyle(
    statusBarColor: Colors.transparent,
    statusBarIconBrightness: Brightness.light,
  ));
  runApp(const PokkeApp());
}

class PokkeApp extends StatefulWidget {
  const PokkeApp({super.key});

  @override
  State<PokkeApp> createState() => _PokkeAppState();
}

class _PokkeAppState extends State<PokkeApp> {
  @override
  void initState() {
    super.initState();
    _listenSpotifyLinks();
  }

  void _listenSpotifyLinks() {
    final appLinks = AppLinks();
    appLinks.uriLinkStream.listen((uri) async {
      if (uri.scheme == 'pokke') {
        final ok = await SpotifyService.instance.handleIncomingLink(uri);
        debugPrint('Spotify incoming link handled: $ok err=${SpotifyService.instance.lastError}');
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'pokke',
      theme: ThemeData(
        colorScheme: const ColorScheme.dark(),
        scaffoldBackgroundColor: const Color.fromARGB(255, 12, 12, 14),
        appBarTheme: const AppBarTheme(
          backgroundColor: Colors.transparent,
          elevation: 0,
        ),
      ),
      debugShowCheckedModeBanner: false,
      initialRoute: '/boot',
      routes: {
        '/boot': (context) => const _BootGate(),
        '/welcome': (context) => const WelcomeScreen(),
        '/auth': (context) => const AuthScreen(),
        '/list_test': (context) => ListTest(),
        '/firstpage': (context) => FirstPage(),
        '/browse': (context) => BrowsePage(),
        '/radio': (context) => RadioPage(),
        '/library': (context) => LibraryPage(),
        '/search': (context) => SearchPage(),
        '/home': (context) => HomePage(),
        '/album': (context) => AlbumPage(),
        '/artist': (context) => ArtistPage(),
        '/artistlist': (context) => ArtistList(),
        '/musiclist': (context) => MusicList(),
        '/profile_edit': (context) => const ProfileEditScreen(),
        '/admin': (context) => const AdminScreen(),
        '/settings': (context) => const SettingsScreen(),
        '/mix': (context) => const MixEditorScreen(),
        '/player_editor': (context) => const PlayerLayoutEditorScreen(),
        '/playlists': (context) => const PlaylistsScreen(),
        '/wrap': (context) => const WrapScreen(),
        '/local_import': (context) => const LocalImportScreen(),
        '/notifications': (context) => const NotificationsScreen(),
      },
      // Full player always uses slide-up + fade (never default Material popup)
      onGenerateRoute: (settings) {
        if (settings.name == '/player') {
          final args = settings.arguments;
          final song = args is Map<String, dynamic> ? args : <String, dynamic>{};
          return MusicPlayer.route(song);
        }
        return null;
      },
    );
  }
}


class SlideUpPageRoute<T> extends PageRouteBuilder<T> {
  final Widget page;
  SlideUpPageRoute({required this.page})
      : super(
          pageBuilder: (_, __, ___) => page,
          transitionsBuilder: (_, anim, __, child) {
            final curved = CurvedAnimation(parent: anim, curve: Curves.easeOutCubic);
            return SlideTransition(
              position: Tween<Offset>(begin: const Offset(0, 1), end: Offset.zero).animate(curved),
              child: FadeTransition(opacity: curved, child: child),
            );
          },
          transitionDuration: const Duration(milliseconds: 380),
          reverseTransitionDuration: const Duration(milliseconds: 320),
        );
}



class _BootGate extends StatefulWidget {
  const _BootGate();
  @override
  State<_BootGate> createState() => _BootGateState();
}

class _BootGateState extends State<_BootGate> {
  String? _error;

  @override
  void initState() {
    super.initState();
    // Wait one frame so Navigator is ready
    WidgetsBinding.instance.addPostFrameCallback((_) => _go());
  }

  Future<void> _go() async {
    try {
      final p = await SharedPreferences.getInstance()
          .timeout(const Duration(seconds: 3));
      final seen = p.getBool('welcome_seen') ?? false;
      if (!mounted) return;
      if (!seen) {
        Navigator.pushReplacementNamed(context, '/welcome');
        return;
      }
      Navigator.pushReplacementNamed(
        context,
        SupabaseService.isLoggedIn ? '/firstpage' : '/auth',
      );
    } catch (e) {
      debugPrint('BootGate error: $e');
      if (!mounted) return;
      // Never stay black: fall through to auth/home
      try {
        Navigator.pushReplacementNamed(
          context,
          SupabaseService.isLoggedIn ? '/firstpage' : '/auth',
        );
      } catch (e2) {
        setState(() => _error = e2.toString());
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF050507),
      body: Center(
        child: _error == null
            ? const CircularProgressIndicator(color: Color(0xfffc3c44))
            : Padding(
                padding: const EdgeInsets.all(24),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const Text('Error al iniciar',
                        style: TextStyle(color: Colors.white, fontSize: 16)),
                    const SizedBox(height: 8),
                    Text(_error!,
                        style: TextStyle(color: Colors.white54, fontSize: 12),
                        textAlign: TextAlign.center),
                    const SizedBox(height: 16),
                    ElevatedButton(
                      style: ElevatedButton.styleFrom(
                          backgroundColor: const Color(0xfffc3c44)),
                      onPressed: () {
                        Navigator.pushReplacementNamed(context, '/auth');
                      },
                      child: const Text('Ir a login',
                          style: TextStyle(color: Colors.white)),
                    ),
                  ],
                ),
              ),
      ),
    );
  }
}
