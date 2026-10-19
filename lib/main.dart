import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:pokke/services/supabase_service.dart';
import 'package:pokke/services/settings_service.dart';
import 'package:pokke/services/listening_history.dart';
import 'package:pokke/services/spotify_service.dart';
import 'package:pokke/screens/auth_screen.dart';
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

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await SupabaseService.init();
  await SettingsService.instance.load();
  await ListeningHistory.instance.load();
  await SpotifyService.instance.load();
  SystemChrome.setSystemUIOverlayStyle(const SystemUiOverlayStyle(
    statusBarColor: Colors.transparent,
    statusBarIconBrightness: Brightness.light,
  ));
  runApp(const PokkeApp());
}

class PokkeApp extends StatelessWidget {
  const PokkeApp({super.key});

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
      initialRoute: SupabaseService.isLoggedIn ? '/firstpage' : '/auth',
      routes: {
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
        '/player': (context) => MusicPlayer(),
        '/profile_edit': (context) => const ProfileEditScreen(),
        '/admin': (context) => const AdminScreen(),
        '/settings': (context) => const SettingsScreen(),
        '/mix': (context) => const MixEditorScreen(),
        '/player_editor': (context) => const PlayerLayoutEditorScreen(),
        '/playlists': (context) => const PlaylistsScreen(),
        '/wrap': (context) => const WrapScreen(),
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

