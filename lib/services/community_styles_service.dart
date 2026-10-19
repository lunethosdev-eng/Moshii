import 'package:pokke/services/supabase_service.dart';
import 'package:pokke/services/settings_service.dart';

/// Publish / browse community UI styles (player layouts, etc).
class CommunityStylesService {
  static final CommunityStylesService instance = CommunityStylesService._();
  CommunityStylesService._();

  /// target: player | home | library | profile
  Future<List<Map<String, dynamic>>> list({String? target, int limit = 40}) async {
    try {
      var q = SupabaseService.kokoro
          .from('community_styles')
          .select()
          .eq('is_public', true)
          .order('created_at', ascending: false)
          .limit(limit);
      if (target != null && target.isNotEmpty) {
        q = SupabaseService.kokoro
            .from('community_styles')
            .select()
            .eq('is_public', true)
            .eq('target', target)
            .order('created_at', ascending: false)
            .limit(limit);
      }
      final res = await q;
      return List<Map<String, dynamic>>.from(res);
    } catch (_) {
      return [];
    }
  }

  Future<bool> publish({
    required String name,
    required String target,
    required Map<String, dynamic> payload,
    String? description,
  }) async {
    final uid = SupabaseService.currentUser?.id;
    if (uid == null) return false;
    try {
      await SupabaseService.kokoro.from('community_styles').insert({
        'user_id': uid,
        'name': name,
        'target': target,
        'description': description ?? '',
        'payload': payload,
        'is_public': true,
      });
      return true;
    } catch (_) {
      return false;
    }
  }

  /// Apply a player style payload into SettingsService.
  Future<void> applyPlayerPayload(Map<String, dynamic> payload) async {
    final s = SettingsService.instance;
    if (payload['slider_style'] is String) {
      await s.setString('slider_style', payload['slider_style'] as String);
    }
    if (payload['slider_thumb'] is String) {
      await s.setString('slider_thumb', payload['slider_thumb'] as String);
    }
    if (payload['time_display'] is String) {
      await s.setString('time_display', payload['time_display'] as String);
    }
    if (payload['cover_style'] is String) {
      await s.setString('cover_style', payload['cover_style'] as String);
    }
    if (payload['player_bg'] is String) {
      await s.setString('player_bg', payload['player_bg'] as String);
    }
    if (payload['layout'] is List) {
      await s.setPlayerLayout(
          (payload['layout'] as List).map((e) => e.toString()).toList());
    }
  }

  Map<String, dynamic> currentPlayerPayload() {
    final s = SettingsService.instance;
    return {
      'slider_style': s.sliderStyle,
      'slider_thumb': s.sliderThumb,
      'time_display': s.timeDisplay,
      'cover_style': s.coverStyle,
      'player_bg': s.playerBackground,
      'layout': s.playerLayout,
    };
  }
}
