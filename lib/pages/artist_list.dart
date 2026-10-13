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
  List<Map<String, dynamic>> _artists = [];
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    try {
      var artists = await SupabaseService.getArtists(limit: 50);
      // Enrich missing images from Deezer
      for (var i = 0; i < artists.length; i++) {
        if (artists[i]['image_url'] == null || artists[i]['image_url'].toString().isEmpty) {
          final img = await ArtistImageService.imageFor(artists[i]['name']?.toString() ?? '');
          if (img != null) artists[i] = {...artists[i], 'image_url': img};
        }
      }
      // Also derive unique artists from songs if table is sparse
      if (artists.length < 5) {
        final songs = await SupabaseService.getSongs(limit: 100);
        final map = <String, Map<String, dynamic>>{};
        for (final s in songs) {
          final name = (s['artist'] ?? '').toString();
          if (name.isEmpty || map.containsKey(name)) continue;
          final img = await ArtistImageService.imageFor(name);
          map[name] = {'name': name, 'image_url': img ?? s['cover_url']};
        }
        artists = map.values.toList();
      }
      setState(() => _artists = artists);
    } catch (_) {}
    setState(() => _loading = false);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF0C0C0E),
      appBar: const CompactHeader(title: 'Artists'),
      body: _loading
          ? const Center(child: CircularProgressIndicator(color: Color(0xfffc3c44)))
          : ListView.separated(
              padding: const EdgeInsets.fromLTRB(12, 8, 12, 100),
              itemCount: _artists.length,
              separatorBuilder: (_, __) => Divider(color: Colors.white.withOpacity(0.08), height: 1),
              itemBuilder: (_, i) {
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
    );
  }
}
