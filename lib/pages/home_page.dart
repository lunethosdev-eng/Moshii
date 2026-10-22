import 'package:flutter/material.dart';
import 'package:pokke/widgets/compact_header.dart';
import 'package:pokke/services/supabase_service.dart';
import 'package:pokke/services/listening_history.dart';
import 'package:pokke/services/settings_service.dart';
import 'package:pokke/services/follow_service.dart';
import 'package:pokke/services/player_controller.dart';

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
  List<Map<String, dynamic>> _recent = [];
  List<Map<String, dynamic>> _popular = [];
  List<Map<String, dynamic>> _followedArtists = [];
  List<Map<String, dynamic>> _newReleases = [];
  bool _loading = true;

  String t(String es, String en) =>
      SettingsService.instance.locale == 'en' ? en : es;

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
      final all = await SupabaseService.getSongs(limit: 100);
      final history = await ListeningHistory.instance.recent(limit: 24);
      final topArtists =
          await ListeningHistory.instance.topArtistNames(limit: 10);
      final followed = await FollowService.list();

      // For You from listening taste
      List<Map<String, dynamic>> forYou = [];
      if (topArtists.isNotEmpty) {
        for (final a in topArtists) {
          forYou.addAll(all.where((s) => (s['artist'] ?? '')
              .toString()
              .toLowerCase()
              .contains(a.toLowerCase())));
        }
      }
      if (forYou.isEmpty) forYou = List.from(all);
      forYou = _dedup(forYou);

      // Popular today: first half shuffled as proxy for plays
      final popular = List<Map<String, dynamic>>.from(all)..shuffle();
      popular.shuffle();

      // New releases: take tail / alternate slice
      final neu = List<Map<String, dynamic>>.from(all.reversed);

      setState(() {
        _forYou = forYou;
        _trending = List.from(all);
        _recent = history;
        _popular = popular.take(30).toList();
        _newReleases = neu.take(24).toList();
        _followedArtists = followed;
      });
    } catch (_) {}
    if (mounted) setState(() => _loading = false);
  }

  List<Map<String, dynamic>> _dedup(List<Map<String, dynamic>> list) {
    final seen = <String>{};
    return list.where((s) {
      final id = (s['id'] ?? s['title']).toString();
      if (seen.contains(id)) return false;
      seen.add(id);
      return true;
    }).toList();
  }

  void _play(Map<String, dynamic> song, List<Map<String, dynamic>> contextList) {
    final idx = contextList.indexWhere(
        (s) => (s['id'] ?? s['title']) == (song['id'] ?? song['title']));
    PlayerController.instance.playPlaylist(
      contextList.isEmpty ? [song] : contextList,
      startIndex: idx >= 0 ? idx : 0,
    );
  }

  Widget _sectionTitle(String title) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 12),
      child: Text(title,
          style: const TextStyle(
              fontSize: 22, fontWeight: FontWeight.bold, color: Colors.white)),
    );
  }

  Widget _songCard(Map<String, dynamic> s, List<Map<String, dynamic>> list) {
    final cover = s['cover_url'];
    return GestureDetector(
      onTap: () => _play(s, list),
      child: SizedBox(
        width: 140,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            ClipRRect(
              borderRadius: BorderRadius.circular(12),
              child: cover != null
                  ? Image.network(cover.toString(),
                      width: 140, height: 140, fit: BoxFit.cover,
                      errorBuilder: (_, __, ___) => Container(
                          width: 140, height: 140, color: Colors.white12))
                  : Container(width: 140, height: 140, color: Colors.white12),
            ),
            const SizedBox(height: 8),
            Text(s['title'] ?? '',
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                    color: Colors.white, fontWeight: FontWeight.w600)),
            Text(s['artist'] ?? '',
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(
                    color: Colors.white.withOpacity(0.5), fontSize: 12)),
          ],
        ),
      ),
    );
  }

  Widget _hList(List<Map<String, dynamic>> list) {
    if (list.isEmpty) return const SizedBox.shrink();
    return SizedBox(
      height: 200,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: 14),
        itemCount: list.length.clamp(0, 24),
        separatorBuilder: (_, __) => const SizedBox(width: 12),
        itemBuilder: (_, i) => _songCard(list[i], list),
      ),
    );
  }

  @override
  void dispose() {
    _scrollCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF0C0C0E),
      body: _loading
          ? const Center(
              child: CircularProgressIndicator(color: Color(0xfffc3c44)))
          : RefreshIndicator(
              color: const Color(0xfffc3c44),
              onRefresh: _load,
              child: CustomScrollView(
                controller: _scrollCtrl,
                physics: const AlwaysScrollableScrollPhysics(),
                slivers: [
                  SliverToBoxAdapter(
                    child: CompactHeader(
                      collapsed: _collapsed,
                      title: t('Inicio', 'Home'),
                    ),
                  ),
                  SliverToBoxAdapter(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        if (_recent.isNotEmpty) ...[
                          _sectionTitle(t('Escuchado recientemente', 'Recently played')),
                          _hList(_recent),
                          const SizedBox(height: 18),
                        ],
                        _sectionTitle(t('Para ti', 'For You')),
                        _hList(_forYou),
                        const SizedBox(height: 18),
                        _sectionTitle(t('Popular hoy', 'Popular today')),
                        _hList(_popular),
                        const SizedBox(height: 18),
                        _sectionTitle(t('Tendencias en pokke', 'Trending on pokke')),
                        _hList(_trending),
                        const SizedBox(height: 18),
                        _sectionTitle(t('Novedades', 'New releases')),
                        _hList(_newReleases),
                        const SizedBox(height: 18),
                        // Followed artists only
                        _sectionTitle(t('Artistas que sigues', 'Artists you follow')),
                        if (_followedArtists.isEmpty)
                          Padding(
                            padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
                            child: Text(
                              t(
                                'Sigue artistas desde el player para verlos aquí.',
                                'Follow artists from the player to see them here.',
                              ),
                              style: TextStyle(
                                  color: Colors.white.withOpacity(0.45),
                                  fontSize: 13),
                            ),
                          )
                        else
                          SizedBox(
                            height: 110,
                            child: ListView.separated(
                              scrollDirection: Axis.horizontal,
                              padding:
                                  const EdgeInsets.symmetric(horizontal: 14),
                              itemCount: _followedArtists.length,
                              separatorBuilder: (_, __) =>
                                  const SizedBox(width: 14),
                              itemBuilder: (_, i) {
                                final a = _followedArtists[i];
                                final img = a['artist_image'] ?? a['image_url'];
                                final name =
                                    (a['artist_name'] ?? a['name'] ?? '')
                                        .toString();
                                return Column(
                                  children: [
                                    CircleAvatar(
                                      radius: 36,
                                      backgroundColor: Colors.white12,
                                      backgroundImage: img != null
                                          ? NetworkImage(img.toString())
                                          : null,
                                      child: img == null
                                          ? const Icon(Icons.person,
                                              color: Colors.white38)
                                          : null,
                                    ),
                                    const SizedBox(height: 6),
                                    SizedBox(
                                      width: 72,
                                      child: Text(
                                        name,
                                        maxLines: 1,
                                        overflow: TextOverflow.ellipsis,
                                        textAlign: TextAlign.center,
                                        style: const TextStyle(
                                            color: Colors.white, fontSize: 12),
                                      ),
                                    ),
                                  ],
                                );
                              },
                            ),
                          ),
                        // Bottom padding for mini player + nav
                        const SizedBox(height: 140),
                      ],
                    ),
                  ),
                ],
              ),
            ),
    );
  }
}
