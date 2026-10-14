import 'dart:convert';
import 'dart:io';
import 'package:http/http.dart' as http;
import 'package:audioplayers/audioplayers.dart';
import 'package:path_provider/path_provider.dart';
import 'package:pokke/services/settings_service.dart';

/// AI DJ: script via OpenRouter + voice via Fish Audio TTS (free tier model).
class AiDjService {
  static final AiDjService instance = AiDjService._();
  AiDjService._();

  static const _openRouterKey =
      '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';
  static const _fishKey =
      'sk-fish-59jWPqt2VJa6z8vy5lSpCsfGnjCxPRRafcsfYJogZYM';
  static const _model = 'openai/gpt-4o-mini';

  final AudioPlayer _voicePlayer = AudioPlayer();

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

  /// Fish Audio TTS → temp mp3 → play.
  Future<void> speak(String text) async {
    try {
      final res = await http
          .post(
            Uri.parse('https://api.fish.audio/v1/tts'),
            headers: {
              'Authorization': 'Bearer $_fishKey',
              'Content-Type': 'application/json',
              'model': 's2.1-pro-free',
            },
            body: jsonEncode({
              'text': text,
              'format': 'mp3',
              'mp3_bitrate': 128,
              'normalize': true,
              'latency': 'balanced',
              'prosody': {'speed': 1.0, 'volume': 0},
            }),
          )
          .timeout(const Duration(seconds: 45));
      if (res.statusCode != 200) {
        throw Exception('Fish TTS ${res.statusCode}: ${res.body}');
      }
      final dir = await getTemporaryDirectory();
      final file = File('${dir.path}/pokke_dj_${DateTime.now().millisecondsSinceEpoch}.mp3');
      await file.writeAsBytes(res.bodyBytes);
      await _voicePlayer.stop();
      await _voicePlayer.play(DeviceFileSource(file.path));
    } catch (e) {
      // Silent fail — DJ is optional
      // ignore: avoid_print
      print('Fish TTS error: $e');
    }
  }

  Future<void> announceTrack({required String title, required String artist}) async {
    final script = await scriptFor(title: title, artist: artist);
    await speak(script);
  }

  Future<void> stop() => _voicePlayer.stop();
}
