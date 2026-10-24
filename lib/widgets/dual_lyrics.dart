
import 'package:flutter/material.dart';

/// Spotify-style lyrics: original line + translation underneath.
class DualLyrics extends StatelessWidget {
  final String original;
  final String? translation;
  final Duration position;
  final bool showTranslation;
  final ScrollController? controller;

  const DualLyrics({
    super.key,
    required this.original,
    this.translation,
    required this.position,
    this.showTranslation = false,
    this.controller,
  });

  List<String> _lines(String raw) => raw
      .split('\n')
      .map((e) {
        // strip [mm:ss.xx] timestamps for display pairs
        return e.replaceFirst(RegExp(r'^\[\d{1,2}:\d{2}(?:\.\d{1,3})?\]\s*'), '').trim();
      })
      .where((e) => e.isNotEmpty && !e.startsWith('·'))
      .toList();

  /// Build pairs from translateLyrics format: original then "· translation"
  List<({String orig, String? tr})> _pairs() {
    final raw = showTranslation && translation != null && translation!.trim().isNotEmpty
        ? translation!
        : original;
    final lines = raw.split('\n').map((e) => e.trim()).where((e) => e.isNotEmpty).toList();
    final out = <({String orig, String? tr})>[];
    for (var i = 0; i < lines.length; i++) {
      var line = lines[i];
      // strip LRC time
      line = line.replaceFirst(RegExp(r'^\[\d{1,2}:\d{2}(?:\.\d{1,3})?\]\s*'), '');
      if (line.startsWith('·')) {
        if (out.isNotEmpty) {
          final prev = out.removeLast();
          out.add((orig: prev.orig, tr: line.replaceFirst(RegExp(r'^·\s*'), '')));
        }
        continue;
      }
      // next line may be · translation
      String? tr;
      if (i + 1 < lines.length && lines[i + 1].trim().startsWith('·')) {
        tr = lines[i + 1].trim().replaceFirst(RegExp(r'^·\s*'), '');
        i++;
      }
      if (line.isNotEmpty) out.add((orig: line, tr: tr));
    }
    // If translation is a full parallel block (same line count), zip with original
    if (showTranslation &&
        translation != null &&
        out.every((e) => e.tr == null)) {
      final o = _lines(original);
      final trs = _lines(translation!);
      if (o.isNotEmpty && trs.length >= (o.length * 0.6).floor()) {
        return [
          for (var i = 0; i < o.length; i++)
            (orig: o[i], tr: i < trs.length ? trs[i] : null)
        ];
      }
    }
    return out;
  }

  @override
  Widget build(BuildContext context) {
    final pairs = _pairs();
    if (pairs.isEmpty) {
      return Center(
        child: Text(
          'Sin letra',
          style: TextStyle(color: Colors.white.withOpacity(0.4)),
        ),
      );
    }
    // rough active index by position (~2.2s/line when no LRC)
    final approx = (position.inMilliseconds / 2200).floor().clamp(0, pairs.length - 1);

    return ListView.builder(
      controller: controller,
      padding: const EdgeInsets.fromLTRB(24, 12, 24, 120),
      itemCount: pairs.length,
      itemBuilder: (ctx, i) {
        final p = pairs[i];
        final active = i == approx;
        return Padding(
          padding: const EdgeInsets.only(bottom: 18),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              AnimatedDefaultTextStyle(
                duration: const Duration(milliseconds: 220),
                style: TextStyle(
                  color: active ? Colors.white : Colors.white.withOpacity(0.42),
                  fontSize: active ? 24 : 18,
                  fontWeight: active ? FontWeight.w700 : FontWeight.w500,
                  height: 1.25,
                ),
                child: Text(p.orig),
              ),
              if (showTranslation && p.tr != null && p.tr!.isNotEmpty)
                Padding(
                  padding: const EdgeInsets.only(top: 4),
                  child: Text(
                    p.tr!,
                    style: TextStyle(
                      color: active
                          ? Colors.white.withOpacity(0.72)
                          : Colors.white.withOpacity(0.32),
                      fontSize: active ? 15 : 13,
                      height: 1.3,
                      fontWeight: FontWeight.w400,
                    ),
                  ),
                ),
            ],
          ),
        );
      },
    );
  }
}
