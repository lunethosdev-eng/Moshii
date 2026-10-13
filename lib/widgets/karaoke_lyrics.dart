import 'package:flutter/material.dart';

class _LrcLine {
  final Duration time;
  final String text;
  _LrcLine(this.time, this.text);
}

/// Karaoke lyrics: active line enlarged, words color letter-by-letter at high speed.
class KaraokeLyrics extends StatelessWidget {
  final String? lrcText;
  final Duration position;

  const KaraokeLyrics({super.key, required this.lrcText, required this.position});

  List<_LrcLine> _parse(String raw) {
    final lines = <_LrcLine>[];
    final re = RegExp(r'\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?\](.*)');
    for (final line in raw.split('\n')) {
      final m = re.firstMatch(line.trim());
      if (m == null) continue;
      final min = int.parse(m.group(1)!);
      final sec = int.parse(m.group(2)!);
      final msStr = m.group(3) ?? '0';
      final ms = int.parse(msStr.padRight(3, '0').substring(0, 3));
      final text = m.group(4)!.trim();
      if (text.isEmpty) continue;
      lines.add(_LrcLine(Duration(minutes: min, seconds: sec, milliseconds: ms), text));
    }
    lines.sort((a, b) => a.time.compareTo(b.time));
    return lines;
  }

  /// Plain text fallback: split into pseudo-timed lines (~2.2s each).
  List<_LrcLine> _fakeTiming(String raw) {
    final parts = raw
        .split(RegExp(r'[\n.]+'))
        .map((e) => e.trim())
        .where((e) => e.isNotEmpty)
        .toList();
    final out = <_LrcLine>[];
    for (var i = 0; i < parts.length; i++) {
      out.add(_LrcLine(Duration(milliseconds: (i * 2200)), parts[i]));
    }
    return out;
  }

  int _activeIndex(List<_LrcLine> lines) {
    if (lines.isEmpty) return 0;
    var idx = 0;
    for (var i = 0; i < lines.length; i++) {
      if (position >= lines[i].time) idx = i;
    }
    return idx;
  }

  /// Emotion-ish color: longer/exclamatory words get warmer tint.
  Color _wordColor(String word, bool filled) {
    if (!filled) return Colors.white.withOpacity(0.28);
    final lower = word.toLowerCase();
    if (lower.contains('love') || lower.contains('heart') || lower.endsWith('!')) {
      return const Color(0xFFFF6B8A);
    }
    if (lower.contains('cry') || lower.contains('sad') || lower.contains('tears')) {
      return const Color(0xFF7EB6FF);
    }
    if (lower.contains('fire') || lower.contains('hot') || lower.contains('burn')) {
      return const Color(0xFFFF9F43);
    }
    return Colors.white;
  }

  @override
  Widget build(BuildContext context) {
    final raw = (lrcText ?? '').trim();
    if (raw.isEmpty) {
      return const Center(
        child: Text('No lyrics', style: TextStyle(color: Colors.white38)),
      );
    }

    final hasLrc = raw.contains(RegExp(r'\[\d{1,2}:\d{2}'));
    final lines = hasLrc ? _parse(raw) : _fakeTiming(raw);
    if (lines.isEmpty) {
      return SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Text(raw, style: TextStyle(color: Colors.white.withOpacity(0.7), height: 1.6)),
      );
    }

    final active = _activeIndex(lines);
    final lineStart = lines[active].time;
    final lineEnd = active + 1 < lines.length
        ? lines[active + 1].time
        : lineStart + const Duration(seconds: 3);
    final lineDur = (lineEnd - lineStart).inMilliseconds.clamp(400, 8000);
    final elapsed = (position - lineStart).inMilliseconds.clamp(0, lineDur);
    // Slightly accelerated fill (1.35x) so it feels snappy / less delay
    final fillRatio = ((elapsed / lineDur) * 1.35).clamp(0.0, 1.0);

    return ListView.builder(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 20),
      itemCount: lines.length,
      itemBuilder: (ctx, i) {
        final line = lines[i];
        final isActive = i == active;
        final isPast = i < active;
        final words = line.text.split(RegExp(r'\s+'));
        final totalChars = line.text.replaceAll(' ', '').length.clamp(1, 9999);

        return AnimatedOpacity(
          duration: const Duration(milliseconds: 200),
          opacity: isActive ? 1 : (isPast ? 0.45 : 0.28),
          child: Padding(
            padding: const EdgeInsets.symmetric(vertical: 10),
            child: isActive
                ? _buildActiveLine(words, totalChars, fillRatio)
                : Text(
                    line.text,
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      fontSize: 18,
                      fontWeight: FontWeight.w500,
                      color: Colors.white.withOpacity(isPast ? 0.5 : 0.3),
                      height: 1.35,
                    ),
                  ),
          ),
        );
      },
    );
  }

  Widget _buildActiveLine(List<String> words, int totalChars, double fillRatio) {
    var seen = 0;
    return Wrap(
      alignment: WrapAlignment.center,
      spacing: 6,
      runSpacing: 4,
      children: words.map((w) {
        final start = seen;
        seen += w.length;
        final wordEndRatio = seen / totalChars;
        final wordStartRatio = start / totalChars;
        final filled = fillRatio >= wordEndRatio;
        final partial = !filled && fillRatio > wordStartRatio;
        final local = partial
            ? ((fillRatio - wordStartRatio) / (wordEndRatio - wordStartRatio + 0.0001)).clamp(0.0, 1.0)
            : (filled ? 1.0 : 0.0);

        if (partial && local > 0 && local < 1) {
          // Letter-by-letter
          final cut = (w.length * local).round().clamp(0, w.length);
          return RichText(
            text: TextSpan(
              children: [
                TextSpan(
                  text: w.substring(0, cut),
                  style: TextStyle(
                    fontSize: 24,
                    fontWeight: FontWeight.w800,
                    color: _wordColor(w, true),
                    height: 1.3,
                  ),
                ),
                TextSpan(
                  text: w.substring(cut),
                  style: TextStyle(
                    fontSize: 24,
                    fontWeight: FontWeight.w800,
                    color: Colors.white.withOpacity(0.28),
                    height: 1.3,
                  ),
                ),
              ],
            ),
          );
        }

        return Text(
          w,
          style: TextStyle(
            fontSize: 24,
            fontWeight: FontWeight.w800,
            color: _wordColor(w, filled),
            height: 1.3,
            fontStyle: w.length > 8 ? FontStyle.italic : FontStyle.normal,
          ),
        );
      }).toList(),
    );
  }
}
