import 'package:supabase_flutter/supabase_flutter.dart';

class SupabaseService {
  // Kokoro Supabase (auth + profiles + announcements)
  static const String kokoroUrl = 'https://ajbmpgnzkgtcmulocftd.supabase.co';
  static const String kokoroAnonKey = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImFqYm1wZ256a2d0Y211bG9jZnRkIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTAzNDU2MjksImV4cCI6MjEwNTkyMTYyOX0.twsBnT9WBBILyXFuoQh7viI578yoNbeQztKHsr8DB8k';

  // Ukiyo server (real music tracks)
  static const String ukiyoUrl = 'https://esjoifsjljvymttinyhj.supabase.co';
  static const String ukiyoAnonKey = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImVzam9pZnNqbGp2eW10dGlueWhqIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTEwMzYyMTAsImV4cCI6MjEwNjYxMjIxMH0.GKA3KyoMElVwLUQlL2ad82IPjRmwKmMJEGPTUNwbuVQ';

  static late final SupabaseClient kokoro;
  static late final SupabaseClient ukiyo;

  static const String reservedEmail = 'lunethos.dev@gmail.com';
  static const String ownerPin = '4049959'; // 4 0 4 9 9 5 9

  static Future<void> init() async {
    await Supabase.initialize(
      url: kokoroUrl,
      anonKey: kokoroAnonKey,
    );
    kokoro = Supabase.instance.client;
    ukiyo = SupabaseClient(ukiyoUrl, ukiyoAnonKey);
  }

  static User? get currentUser => kokoro.auth.currentUser;
  static bool get isLoggedIn => currentUser != null;
  static bool get isOwner => currentUser?.email?.toLowerCase() == reservedEmail;

  // Check if username is available (3-4 chars)
  static Future<bool> isUsernameAvailable(String username) async {
    final res = await kokoro
        .from('profiles')
        .select('username')
        .eq('username', username.toLowerCase())
        .maybeSingle();
    return res == null;
  }

  static Future<AuthResponse> signUp({
    required String email,
    required String password,
    required String username,
    required String nickname,
  }) async {
    // Validation
    if (username.length < 3 || username.length > 4) {
      throw Exception('Username must be 3-4 characters');
    }
    if (nickname.length != 3) {
      throw Exception('Nickname must be exactly 3 characters');
    }
    if (password.length < 8) {
      throw Exception('Password must be at least 8 characters');
    }
    if (!await isUsernameAvailable(username)) {
      throw Exception('Username already taken');
    }

    final res = await kokoro.auth.signUp(
      email: email,
      password: password,
      data: {
        'username': username.toLowerCase(),
        'nickname': nickname,
      },
    );

    if (res.user != null) {
      try {
        await kokoro.from('profiles').upsert({
          'id': res.user!.id,
          'username': username.toLowerCase(),
          'display_name': nickname,
          'created_at': DateTime.now().toIso8601String(),
          'updated_at': DateTime.now().toIso8601String(),
        });
      } catch (e) {
        // RLS may block until email confirmed - profile can be created on first login
        print('Profile create deferred: $e');
      }
    }
    return res;
  }

  static Future<AuthResponse> signIn({
    required String email,
    required String password,
    String? pin,
  }) async {
    // Special owner protection
    if (email.toLowerCase() == reservedEmail) {
      if (pin != ownerPin) {
        throw Exception('PIN required for this account');
      }
    }

    return await kokoro.auth.signInWithPassword(
      email: email,
      password: password,
    );
  }

  static Future<void> signOut() async {
    await kokoro.auth.signOut();
  }

  static Future<Map<String, dynamic>?> getProfile() async {
    if (currentUser == null) return null;
    return await kokoro
        .from('profiles')
        .select()
        .eq('id', currentUser!.id)
        .maybeSingle();
  }

  static Future<void> updateProfile({
    String? displayName,
    String? bio,
    String? avatarUrl,
    String? bannerUrl,
  }) async {
    if (currentUser == null) return;
    final data = <String, dynamic>{
      'updated_at': DateTime.now().toIso8601String(),
    };
    if (displayName != null) data['display_name'] = displayName;
    if (bio != null) data['bio'] = bio;
    if (avatarUrl != null) data['avatar_url'] = avatarUrl;
    if (bannerUrl != null) data['banner_url'] = bannerUrl;
    await kokoro.from('profiles').update(data).eq('id', currentUser!.id);
  }

  // Real music from Ukiyo
  
  static Future<List<Map<String, dynamic>>> searchSongs(String query, {int limit = 40}) async {
    if (query.trim().isEmpty) return [];
    final q = query.trim();
    try {
      final res = await ukiyo
          .from('songs')
          .select()
          .or('title.ilike.%$q%,artist.ilike.%$q%,album.ilike.%$q%')
          .order('created_at', ascending: false)
          .limit(limit);
      final list = List<Map<String, dynamic>>.from(res);
      if (list.isNotEmpty) return list;
    } catch (_) {}

    // Fallback local filter for accents / multi-word
    final all = await ukiyo
        .from('songs')
        .select()
        .order('created_at', ascending: false)
        .limit(400);
    final words = q.toLowerCase().split(RegExp(r'\s+')).where((w) => w.isNotEmpty).toList();
    return List<Map<String, dynamic>>.from(all).where((s) {
      final hay = '${s['title'] ?? ''} ${s['artist'] ?? ''} ${s['album'] ?? ''}'.toLowerCase();
      return words.every((w) => hay.contains(w));
    }).take(limit).toList();
  }

  static Future<List<Map<String, dynamic>>> getArtists({int limit = 30}) async {
    final res = await ukiyo.from('artists').select().order('name').limit(limit);
    return List<Map<String, dynamic>>.from(res);
  }

  static Future<Map<String, dynamic>?> getSongById(String id) async {
    return await ukiyo.from('songs').select().eq('id', id).maybeSingle();
  }

  static Future<List<Map<String, dynamic>>> getSongs({int limit = 50}) async {
    final res = await ukiyo
        .from('songs')
        .select()
        .order('created_at', ascending: false)
        .limit(limit);
    return List<Map<String, dynamic>>.from(res);
  }

  static Future<List<Map<String, dynamic>>> getAnnouncements() async {
    final res = await kokoro
        .from('announcements')
        .select()
        .eq('active', true)
        .order('created_at', ascending: false);
    return List<Map<String, dynamic>>.from(res);
  }

  static Future<void> createAnnouncement({
    required String title,
    String? body,
    String? imageUrl,
  }) async {
    if (!isOwner) throw Exception('Only owner can create announcements');
    await kokoro.from('announcements').insert({
      'title': title,
      'body': body,
      'image_url': imageUrl,
      'active': true,
    });
  }
}
