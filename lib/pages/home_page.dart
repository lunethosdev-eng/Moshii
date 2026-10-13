import 'package:flutter/material.dart';
import 'package:pokke/widgets/compact_header.dart';
import 'package:pokke/services/supabase_service.dart';

class HomePage extends StatefulWidget {
  HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  final _scrollCtrl = ScrollController();
  bool _collapsed = false;
  List<Map<String, dynamic>> _songs = [];
  List<Map<String, dynamic>> _artists = [];
  bool _loading = true;

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
      final results = await Future.wait([
        SupabaseService.getSongs(limit: 40),
        SupabaseService.getArtists(limit: 20),
      ]);
      setState(() {
        _songs = results[0];
        _artists = results[1];
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
                          Container(height: size, width: size, color: Colors.white12),
                    )
                  : Container(height: size, width: size, color: Colors.white12),
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
              style: TextStyle(color: Colors.white.withOpacity(0.55), fontSize: 12),
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
      appBar: CompactHeader(title: 'Home', collapsed: _collapsed),
      body: _loading
          ? const Center(child: CircularProgressIndicator(color: Color(0xfffc3c44)))
          : RefreshIndicator(
              color: const Color(0xfffc3c44),
              onRefresh: _load,
              child: SingleChildScrollView(
                controller: _scrollCtrl,
                physics: const AlwaysScrollableScrollPhysics(),
                padding: const EdgeInsets.only(bottom: 120),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const SizedBox(height: 12),

                    // From Ukiyo - all real songs
                    const Padding(
                      padding: EdgeInsets.only(left: 16, bottom: 10),
                      child: Text('From Ukiyo',
                          style: TextStyle(fontSize: 22, fontWeight: FontWeight.bold, color: Colors.white)),
                    ),
                    if (_songs.isEmpty)
                      const Padding(
                        padding: EdgeInsets.all(24),
                        child: Text('No songs yet', style: TextStyle(color: Colors.white54)),
                      )
                    else
                      SizedBox(
                        height: 190,
                        child: ListView.separated(
                          scrollDirection: Axis.horizontal,
                          padding: const EdgeInsets.symmetric(horizontal: 14),
                          itemCount: _songs.length.clamp(0, 15),
                          separatorBuilder: (_, __) => const SizedBox(width: 12),
                          itemBuilder: (_, i) => _songCard(_songs[i]),
                        ),
                      ),

                    const SizedBox(height: 22),

                    // More tracks grid
                    const Padding(
                      padding: EdgeInsets.only(left: 16, bottom: 10),
                      child: Text('Recommended',
                          style: TextStyle(fontSize: 22, fontWeight: FontWeight.bold, color: Colors.white)),
                    ),
                    Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 14),
                      child: GridView.builder(
                        shrinkWrap: true,
                        physics: const NeverScrollableScrollPhysics(),
                        itemCount: _songs.length > 15 ? _songs.length - 15 : _songs.length,
                        gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                          crossAxisCount: 2,
                          mainAxisSpacing: 14,
                          crossAxisSpacing: 12,
                          childAspectRatio: 0.78,
                        ),
                        itemBuilder: (_, i) {
                          final idx = _songs.length > 15 ? i + 15 : i;
                          if (idx >= _songs.length) return const SizedBox.shrink();
                          return _songCard(_songs[idx], size: double.infinity);
                        },
                      ),
                    ),

                    if (_artists.isNotEmpty) ...[
                      const SizedBox(height: 22),
                      const Padding(
                        padding: EdgeInsets.only(left: 16, bottom: 10),
                        child: Text('Artists',
                            style: TextStyle(fontSize: 22, fontWeight: FontWeight.bold, color: Colors.white)),
                      ),
                      SizedBox(
                        height: 120,
                        child: ListView.separated(
                          scrollDirection: Axis.horizontal,
                          padding: const EdgeInsets.symmetric(horizontal: 14),
                          itemCount: _artists.length,
                          separatorBuilder: (_, __) => const SizedBox(width: 14),
                          itemBuilder: (_, i) {
                            final a = _artists[i];
                            return Column(
                              children: [
                                CircleAvatar(
                                  radius: 40,
                                  backgroundColor: Colors.white12,
                                  backgroundImage: a['image_url'] != null
                                      ? NetworkImage(a['image_url'])
                                      : null,
                                  child: a['image_url'] == null
                                      ? const Icon(Icons.person, color: Colors.white38)
                                      : null,
                                ),
                                const SizedBox(height: 6),
                                SizedBox(
                                  width: 80,
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
