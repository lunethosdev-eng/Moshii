import 'package:flutter/material.dart';
import 'package:pokke/services/supabase_service.dart';
import 'package:pokke/services/artist_image_service.dart';
import 'package:pokke/widgets/compact_header.dart';

class ArtistList extends StatefulWidget {
  ArtistList({super.key});

  @override
  State<ArtistList> createState() => _ArtistListState();
}

class _ArtistListState extends State<ArtistList> {
  final _scroll = ScrollController();
  List<Map<String, dynamic>> _artists = [];
  bool _loading = true;
  bool _loadingMore = false;
  bool _hasMore = true;
  int _page = 0;
  static const _pageSize = 40;

  @override
  void initState() {
    super.initState();
    _load(reset: true);
    _scroll.addListener(() {
      if (_scroll.position.pixels > _scroll.position.maxScrollExtent - 200) {
        _load();
      }
    });
  }

  @override
  void dispose() {
    _scroll.dispose();
    super.dispose();
  }

  Future<void> _load({bool reset = false}) async {
    if (_loadingMore) return;
    if (!reset && !_hasMore) return;
    if (reset) {
      setState(() {
        _loading = true;
        _page = 0;
        _hasMore = true;
        _artists = [];
      });
    } else {
      setState(() => _loadingMore = true);
    }
    try {
      // Unique artists from songs with pagination via range
      final from = _page * _pageSize;
      final songs = await SupabaseService.ukiyo
          .from('songs')
          .select('artist, cover_url')
          .order('artist')
          .range(from, from + _pageSize * 3 - 1);
      final map = <String, Map<String, dynamic>>{};
      for (final a in _artists) {
        map[a['name']] = a;
      }
      for (final s in songs) {
        final name = (s['artist'] ?? '').toString().trim();
        if (name.isEmpty || map.containsKey(name)) continue;
        map[name] = {'name': name, 'image_url': s['cover_url']};
      }
      // Enrich missing images (limit API calls)
      var enriched = 0;
      for (final e in map.values) {
        if ((e['image_url'] == null || e['image_url'].toString().isEmpty) && enriched < 8) {
          final img = await ArtistImageService.imageFor(e['name'] as String);
          if (img != null) e['image_url'] = img;
          enriched++;
        }
      }
      final list = map.values.toList()..sort((a, b) => (a['name'] as String).compareTo(b['name'] as String));
      setState(() {
        _artists = list;
        _page++;
        _hasMore = songs.length >= _pageSize;
      });
    } catch (_) {}
    setState(() {
      _loading = false;
      _loadingMore = false;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF0C0C0E),
      appBar: const CompactHeader(title: 'Artists'),
      body: _loading
          ? const Center(child: CircularProgressIndicator(color: Color(0xfffc3c44)))
          : RefreshIndicator(
              color: const Color(0xfffc3c44),
              onRefresh: () => _load(reset: true),
              child: ListView.separated(
                controller: _scroll,
                padding: const EdgeInsets.fromLTRB(12, 8, 12, 100),
                itemCount: _artists.length + (_loadingMore ? 1 : 0),
                separatorBuilder: (_, __) => Divider(color: Colors.white.withOpacity(0.08), height: 1),
                itemBuilder: (_, i) {
                  if (i >= _artists.length) {
                    return const Padding(
                      padding: EdgeInsets.all(16),
                      child: Center(child: CircularProgressIndicator(color: Color(0xfffc3c44))),
                    );
                  }
                  final a = _artists[i];
                  final img = a['image_url'];
                  return ListTile(
                    leading: CircleAvatar(
                      radius: 28,
                      backgroundColor: Colors.white12,
                      backgroundImage: img != null ? NetworkImage(img.toString()) : null,
                      child: img == null ? const Icon(Icons.person, color: Colors.white38) : null,
                    ),
                    title: Text(a['name'] ?? '', style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w600)),
                    trailing: const Icon(Icons.chevron_right, color: Colors.white38),
                    onTap: () => Navigator.pushNamed(context, '/artist', arguments: a),
                  );
                },
              ),
            ),
    );
  }
}
