import 'package:pokke/services/supabase_service.dart';

class FollowService {
  static Future<bool> isFollowing(String artistName) async {
    final uid = SupabaseService.currentUser?.id;
    if (uid == null || artistName.trim().isEmpty) return false;
    try {
      final row = await SupabaseService.kokoro
          .from('artist_follows')
          .select('id')
          .eq('user_id', uid)
          .eq('artist_name', artistName.trim())
          .maybeSingle();
      return row != null;
    } catch (_) {
      return false;
    }
  }

  static Future<bool> toggle(String artistName, {String? imageUrl}) async {
    final uid = SupabaseService.currentUser?.id;
    if (uid == null) return false;
    final name = artistName.trim();
    final following = await isFollowing(name);
    if (following) {
      await SupabaseService.kokoro
          .from('artist_follows')
          .delete()
          .eq('user_id', uid)
          .eq('artist_name', name);
      return false;
    } else {
      await SupabaseService.kokoro.from('artist_follows').upsert({
        'user_id': uid,
        'artist_name': name,
        'artist_image': imageUrl,
      });
      return true;
    }
  }

  static Future<List<Map<String, dynamic>>> list() async {
    final uid = SupabaseService.currentUser?.id;
    if (uid == null) return [];
    final res = await SupabaseService.kokoro
        .from('artist_follows')
        .select()
        .eq('user_id', uid)
        .order('created_at', ascending: false);
    return List<Map<String, dynamic>>.from(res);
  }
}
