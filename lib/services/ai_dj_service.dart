import 'dart:convert';
import 'dart:io';
import 'package:http/http.dart' as http;
import 'package:audioplayers/audioplayers.dart';
import 'package:pokke/services/settings_service.dart';
import 'package:pokke/services/audio_cache_service.dart';
import 'package:pokke/services/player_controller.dart';

/// AI DJ: script via OpenRouter + voice via Fish Audio TTS.
/// Voices are cached locally (persistent) so offline still works when tokens run out.
/// Never talks over music: pauses music, speaks fully, then resumes / hands off.
class AiDjService {
  static final AiDjService instance = AiDjService._();
  AiDjService._();

  static const _openRouterKey =
      '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';
  static const _fishKey =
      'sk-fish-59jWPqt2VJa6z8vy5lSpCsfGnjCxPRRafcsfYJogZYM';
  static const _model = 'openai/gpt-4o-mini';

  final AudioPlayer _voicePlayer = AudioPlayer();
  bool _speaking = false;
  bool get isSpeaking => _speaking;

  String _hash(String text) {
    // lightweight stable hash without extra deps
    final bytes = utf8.encode(text.trim().toLowerCase());
    var h = 0xcbf29ce484222325;
    for (final b in bytes) {
      h ^= b;
      h = (h * 0x100000001b3) & 0xFFFFFFFFFFFFFFFF;
    }
    return h.toRadixString(16);
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

  Future<String?> _resolveVoiceFile(String text) async {
    final hash = _hash(text);
    final cached = await AudioCacheService.instance.localPathForVoice(hash);
    if (cached != null) return cached;

    try {
      final res = await http
          .post(
            Uri.parse('https://api.fish.audio/v1/tts'),
            headers: {
              'Authorization': 'Bearer $_fishKey',
              'Content-Type': 'application/json',
              'model': 'speech-1.6',
            },
            body: jsonEncode({
              'text': text,
              'format': 'mp3',
              'mp3_bitrate': 128,
            }),
          )
          .timeout(const Duration(seconds: 45));
      if (res.statusCode == 200 && res.bodyBytes.isNotEmpty) {
        return await AudioCacheService.instance.cacheVoiceBytes(hash, res.bodyBytes);
      }
    } catch (_) {}
    return cached; // may still be null
  }

  /// Speak [text]. Pauses music first, waits until voice fully ends, then returns.
  /// If TTS fails / offline with no cache, returns quickly without blocking forever.
  Future<void> speak(String text) async {
    if (text.trim().isEmpty) return;
    if (!SettingsService.instance.flag('exp_voice_dj', def: true) &&
        !SettingsService.instance.flag('exp_poki_dj', def: true)) {
      return;
    }

    final path = await _resolveVoiceFile(text);
    if (path == null || !await File(path).exists()) {
      // Fallback: short silence so UI can still breathe
      await Future.delayed(const Duration(milliseconds: 600));
      return;
    }

    _speaking = true;
    // Duck / pause music so voice is never under a track
    await PlayerController.instance.pauseForDj();

    try {
      await _voicePlayer.stop();
      await _voicePlayer.setReleaseMode(ReleaseMode.stop);
      await _voicePlayer.setSource(DeviceFileSource(path));
      await _voicePlayer.resume();

      // Wait until playback completes (or timeout safety)
      final done = _voicePlayer.onPlayerComplete.first;
      await done.timeout(const Duration(seconds: 45), onTimeout: () {});
    } catch (_) {
    } finally {
      try {
        await _voicePlayer.stop();
      } catch (_) {}
      _speaking = false;
    }
  }

  Future<void> announceTrack({required String title, required String artist}) async {
    final script = await scriptFor(title: title, artist: artist);
    await speak(script);
  }

  Future<void> stop() async {
    try {
      await _voicePlayer.stop();
    } catch (_) {}
    _speaking = false;
  }
}
