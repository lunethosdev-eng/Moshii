import 'dart:convert';
import 'package:http/http.dart' as http;
import 'package:flutter_tts/flutter_tts.dart';
import 'package:pokke/services/settings_service.dart';

/// AI DJ: commentary via OpenRouter + on-device TTS (free voice).
/// User accepted keys in client.
class AiDjService {
  static final AiDjService instance = AiDjService._();
  AiDjService._();

  // OpenRouter (user-provided). Prefer env/backend later.
  static const _openRouterKey =
      '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';
  static const _model = 'openai/gpt-4o-mini';

  final FlutterTts _tts = FlutterTts();
  bool _ready = false;

  Future<void> init() async {
    if (_ready) return;
    final lang = SettingsService.instance.locale == 'en' ? 'en-US' : 'es-ES';
    await _tts.setLanguage(lang);
    await _tts.setSpeechRate(0.48);
    await _tts.setPitch(1.05);
    await _tts.setVolume(1.0);
    _ready = true;
  }

  Future<String> scriptFor({
    required String title,
    required String artist,
  }) async {
    final es = SettingsService.instance.locale != 'en';
    final prompt = es
        ? 'Eres un DJ de radio cálido y breve (máx 2 frases). Presenta la canción "$title" de $artist. Tono amable, sin emojis.'
        : 'You are a warm, brief radio DJ (max 2 sentences). Introduce the song "$title" by $artist. Friendly tone, no emojis.';
    try {
      final res = await http
          .post(
            Uri.parse('https://openrouter.ai/api/v1/chat/completions'),
            headers: {
              'Authorization': 'Bearer $_openRouterKey',
              'Content-Type': 'application/json',
              'HTTP-Referer': 'https://github.com/lunethosdev-eng/Moshii',
              'X-Title': 'pokke',
            },
            body: jsonEncode({
              'model': _model,
              'messages': [
                {'role': 'user', 'content': prompt},
              ],
              'max_tokens': 120,
            }),
          )
          .timeout(const Duration(seconds: 20));
      if (res.statusCode == 200) {
        final body = jsonDecode(res.body);
        final text = body['choices']?[0]?['message']?['content']?.toString();
        if (text != null && text.trim().isNotEmpty) return text.trim();
      }
    } catch (_) {}
    return es
        ? 'Ahora suena $title, de $artist. Disfruta.'
        : 'Now playing $title, by $artist. Enjoy.';
  }

  Future<void> speak(String text) async {
    await init();
    await _tts.stop();
    await _tts.speak(text);
  }

  Future<void> announceTrack({required String title, required String artist}) async {
    final script = await scriptFor(title: title, artist: artist);
    await speak(script);
  }

  Future<void> stop() => _tts.stop();
}
