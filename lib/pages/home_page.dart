import 'package:flutter/material.dart';
import 'package:pokke/widgets/compact_header.dart';
import 'package:pokke/services/supabase_service.dart';
import 'package:pokke/services/listening_history.dart';
import 'package:pokke/services/settings_service.dart';

class HomePage extends StatefulWidget {
  HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  final _scrollCtrl = ScrollController();
  bool _collapsed = false;
  List<Map<String, dynamic>> _forYou = [];
  List<Map<String, dynamic>> _trending = [];
  List<Map<String, dynamic>> _artists = [];
  bool _loading = true;

  String get _tForYou => SettingsService.instance.locale == 'en' ? 'For You' : 'Para ti';
  String get _tTrending => SettingsService.instance.locale == 'en' ? 'Trending on pokke' : 'Tendencias en pokke';
  String get _tArtists => SettingsService.instance.locale == 'en' ? 'Artists' : 'Artistas';

  @override
  void initState() {
    super.initState();
    _scrollCtrl.addListener(() {
      final c = _scrollCtrl.offset > 48;
      if (c != _collapsed) setState(() => _collapsed = c);
    });
    _load();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    try {
      final all = await SupabaseService.getSongs(limit: 80);
      final history = await ListeningHistory.instance.recent(limit: 20);
      final topArtists = await ListeningHistory.instance.topArtistNames(limit: 8);

      // For You: same artist as recent listens, else mix of catalog
      List<Map<String, dynamic>> forYou = [];
      if (topArtists.isNotEmpty) {
        for (final a in topArtists) {
          forYou.addAll(all.where((s) =>
              (s['artist'] ?? '').toString().toLowerCase().contains(a.toLowerCase())));
        }
      }
      if (forYou.isEmpty) forYou = List.from(all);
      // Dedup
      final seen = <String>{};
      forYou = forYou.where((s) {
        final id = (s['id'] ?? s['title']).toString();
        if (seen.contains(id)) return false;
        seen.add(id);
        return true;
      }).toList();

      // Trending: newest / catalog head (proxy until we have global plays table)
      final trending = List<Map<String, dynamic>>.from(all);

      final artists = await SupabaseService.getArtists(limit: 20);

      setState(() {
        _forYou = forYou;
        _trending = trending;
        _artists = artists;
      });
    } catch (_) {}
    setState(() => _loading = false);
  }

  @override
  void dispose() {
    _scrollCtrl.dispose();
    super.dispose();
  }

  void _openPlayer(Map<String, dynamic> song) {
    Navigator.pushNamed(context, '/player', arguments: song);
  }

  Widget _songCard(Map<String, dynamic> s, {double size = 140}) {
    final cover = s['cover_url'] ?? s['animated_cover_url'];
    return GestureDetector(
      onTap: () => _openPlayer(s),
      child: SizedBox(
        width: size,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            ClipRRect(
              borderRadius: BorderRadius.circular(12),
              child: cover != null
                  ? Image.network(
                      cover.toString(),
                      height: size,
                      width: size,
                      fit: BoxFit.cover,
                      errorBuilder: (_, __, ___) =>
                          Container(height: size, width: size, color: Colors.white12,
                              child: const Icon(Icons.music_note, color: Colors.white24)),
                    )
                  : Container(
                      height: size,
                      width: size,
                      color: Colors.white12,
                      child: const Icon(Icons.music_note, color: Colors.white24),
                    ),
            ),
            const SizedBox(height: 6),
            Text(
              s['title'] ?? '',
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 13, color: Colors.white),
            ),
            Text(
              s['artist'] ?? '',
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(fontSize: 12, color: Colors.white.withOpacity(0.55)),
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF0C0C0E),
      appBar: CompactHeader(
        title: 'Home',
        collapsed: _collapsed,
        onSettings: () => Navigator.pushNamed(context, '/settings'),
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator(color: Color(0xfffc3c44)))
          : RefreshIndicator(
              color: const Color(0xfffc3c44),
              onRefresh: _load,
              child: SingleChildScrollView(
                controller: _scrollCtrl,
                physics: const AlwaysScrollableScrollPhysics(),
                padding: const EdgeInsets.only(bottom: 140),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const SizedBox(height: 12),

                    // For You (based on listening history)
                    Padding(
                      padding: const EdgeInsets.only(left: 16, bottom: 10),
                      child: Text(_tForYou,
                          style: const TextStyle(fontSize: 22, fontWeight: FontWeight.bold, color: Colors.white)),
                    ),
                    if (_forYou.isEmpty)
                      const Padding(
                        padding: EdgeInsets.all(24),
                        child: Text('Play something to get recommendations',
                            style: TextStyle(color: Colors.white54)),
                      )
                    else
                      SizedBox(
                        height: 190,
                        child: ListView.separated(
                          scrollDirection: Axis.horizontal,
                          padding: const EdgeInsets.symmetric(horizontal: 14),
                          itemCount: _forYou.length.clamp(0, 20),
                          separatorBuilder: (_, __) => const SizedBox(width: 12),
                          itemBuilder: (_, i) => _songCard(_forYou[i]),
                        ),
                      ),

                    const SizedBox(height: 22),

                    // Trending on pokke
                    Padding(
                      padding: const EdgeInsets.only(left: 16, bottom: 10),
                      child: Text(_tTrending,
                          style: const TextStyle(fontSize: 22, fontWeight: FontWeight.bold, color: Colors.white)),
                    ),
                    SizedBox(
                      height: 190,
                      child: ListView.separated(
                        scrollDirection: Axis.horizontal,
                        padding: const EdgeInsets.symmetric(horizontal: 14),
                        itemCount: _trending.length.clamp(0, 15),
                        separatorBuilder: (_, __) => const SizedBox(width: 12),
                        itemBuilder: (_, i) => _songCard(_trending[i]),
                      ),
                    ),

                    const SizedBox(height: 22),

                    // Artists from Ukiyo
                    if (_artists.isNotEmpty) ...[
                      Padding(
                        padding: const EdgeInsets.only(left: 16, bottom: 10),
                        child: Text(_tArtists,
                            style: const TextStyle(fontSize: 22, fontWeight: FontWeight.bold, color: Colors.white)),
                      ),
                      SizedBox(
                        height: 110,
                        child: ListView.separated(
                          scrollDirection: Axis.horizontal,
                          padding: const EdgeInsets.symmetric(horizontal: 14),
                          itemCount: _artists.length,
                          separatorBuilder: (_, __) => const SizedBox(width: 14),
                          itemBuilder: (_, i) {
                            final a = _artists[i];
                            final img = a['image_url'];
                            return Column(
                              children: [
                                CircleAvatar(
                                  radius: 36,
                                  backgroundColor: Colors.white12,
                                  backgroundImage: img != null ? NetworkImage(img.toString()) : null,
                                  child: img == null
                                      ? const Icon(Icons.person, color: Colors.white38)
                                      : null,
                                ),
                                const SizedBox(height: 6),
                                SizedBox(
                                  width: 72,
                                  child: Text(
                                    a['name'] ?? '',
                                    maxLines: 1,
                                    overflow: TextOverflow.ellipsis,
                                    textAlign: TextAlign.center,
                                    style: const TextStyle(color: Colors.white, fontSize: 12),
                                  ),
                                ),
                              ],
                            );
                          },
                        ),
                      ),
                    ],
                  ],
                ),
              ),
            ),
    );
  }
}
