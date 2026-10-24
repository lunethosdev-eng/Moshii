import 'dart:convert';
import 'package:http/http.dart' as http;
import 'package:pokke/services/settings_service.dart';

/// AI helpers using Groq (fast) and OpenRouter (quality / multimodal).
/// Keys live in SettingsService beta fields — never hardcode secrets in source
/// for production; user provided them for this beta session.
class AiService {
  static final AiService instance = AiService._();
  AiService._();

  // Session keys (user-supplied for beta). Prefer Settings overrides if set.
  static const _defaultGroq =
      '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';
  static const _defaultOpenRouter =
      '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';

  String get _groqKey =>
      SettingsService.instance.betaGroqKey.isNotEmpty
          ? SettingsService.instance.betaGroqKey
          : _defaultGroq;

  String get _orKey =>
      SettingsService.instance.betaOpenRouterKey.isNotEmpty
          ? SettingsService.instance.betaOpenRouterKey
          : _defaultOpenRouter;

  bool get enabled => SettingsService.instance.flag('beta_ai', def: true);

  /// Translate lyrics to target language. Uses Groq for speed.
  Future<String?> translateLyrics(String lyrics, {String targetLang = 'es'}) async {
    if (!enabled || lyrics.trim().isEmpty) return null;
    try {
      final res = await http.post(
        Uri.parse('https://api.groq.com/openai/v1/chat/completions'),
        headers: {
          'Authorization': 'Bearer $_groqKey',
          'Content-Type': 'application/json',
        },
        body: jsonEncode({
          'model': 'llama-3.3-70b-versatile',
          'messages': [
            {
              'role': 'system',
              'content':
                  'Translate lyrics to the user language. For each line: original, then a line starting with "· " for translation. Keep [mm:ss.xx] on originals. No commentary.',
            },
            {'role': 'user', 'content': lyrics},
          ],
          'temperature': 0.2,
          'max_tokens': 4096,
        }),
      );
      if (res.statusCode != 200) return null;
      final data = jsonDecode(res.body) as Map<String, dynamic>;
      final content =
          data['choices']?[0]?['message']?['content']?.toString();
      return content?.trim();
    } catch (_) {
      return null;
    }
  }

  /// Estimate a karaoke fill multiplier (0.7–1.6) from tempo / line density
  /// so letter colouring feels locked to the song, not delayed or rushed.
  Future<double?> karaokeSpeedHint({
    required String title,
    required String artist,
    String? sampleLyrics,
    int? durationSeconds,
  }) async {
    if (!enabled) return null;
    try {
      final prompt = '''
Song: "$title" by $artist.
${durationSeconds != null ? 'Duration ~${durationSeconds}s.' : ''}
${sampleLyrics != null && sampleLyrics.isNotEmpty ? 'Sample lyrics:\n${sampleLyrics.substring(0, sampleLyrics.length.clamp(0, 400))}' : ''}
Return ONLY a JSON object: {"bpm_guess": number, "karaoke_fill_multiplier": number between 0.75 and 1.55}.
Higher multiplier = faster letter fill (for upbeat / dense lines). Lower = slower (ballads).
''';
      final res = await http.post(
        Uri.parse('https://api.groq.com/openai/v1/chat/completions'),
        headers: {
          'Authorization': 'Bearer $_groqKey',
          'Content-Type': 'application/json',
        },
        body: jsonEncode({
          'model': 'llama-3.1-8b-instant',
          'messages': [
            {
              'role': 'system',
              'content':
                  'You estimate song tempo for karaoke UI. Reply with pure JSON only.',
            },
            {'role': 'user', 'content': prompt},
          ],
          'temperature': 0.1,
          'max_tokens': 120,
        }),
      );
      if (res.statusCode != 200) return null;
      final data = jsonDecode(res.body) as Map<String, dynamic>;
      var content =
          data['choices']?[0]?['message']?['content']?.toString() ?? '';
      content = content.replaceAll(RegExp(r'```json|```'), '').trim();
      final m = jsonDecode(content) as Map<String, dynamic>;
      final mult = (m['karaoke_fill_multiplier'] as num?)?.toDouble();
      if (mult == null) return null;
      return mult.clamp(0.75, 1.55);
    } catch (_) {
      return null;
    }
  }

  /// OpenRouter path for higher-quality creative tasks (beta).
  Future<String?> openRouterChat(String system, String user,
      {String model = 'openai/gpt-4o-mini'}) async {
    if (!enabled) return null;
    try {
      final res = await http.post(
        Uri.parse('https://openrouter.ai/api/v1/chat/completions'),
        headers: {
          'Authorization': 'Bearer $_orKey',
          'Content-Type': 'application/json',
          'HTTP-Referer': 'https://pokke.app',
          'X-Title': 'Pokke',
        },
        body: jsonEncode({
          'model': model,
          'messages': [
            {'role': 'system', 'content': system},
            {'role': 'user', 'content': user},
          ],
          'temperature': 0.4,
          'max_tokens': 2048,
        }),
      );
      if (res.statusCode != 200) return null;
      final data = jsonDecode(res.body) as Map<String, dynamic>;
      return data['choices']?[0]?['message']?['content']?.toString()?.trim();
    } catch (_) {
      return null;
    }
  }
}
