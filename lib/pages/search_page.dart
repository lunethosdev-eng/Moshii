import 'dart:async';
import 'package:flutter/material.dart';
import 'package:pokke/lists_constants.dart';
import 'package:pokke/widgets/liquid_glass.dart';
import 'package:pokke/widgets/compact_header.dart';
import 'package:pokke/services/supabase_service.dart';

class SearchPage extends StatefulWidget {
  SearchPage({super.key});

  @override
  State<SearchPage> createState() => _SearchPageState();
}

class _SearchPageState extends State<SearchPage> {
  final _searchCtrl = TextEditingController();
  final _scrollCtrl = ScrollController();
  List<Map<String, dynamic>> _results = [];
  bool _searching = false;
  bool _collapsed = false;
  Timer? _debounce;

  @override
  void initState() {
    super.initState();
    _scrollCtrl.addListener(() {
      final c = _scrollCtrl.offset > 40;
      if (c != _collapsed) setState(() => _collapsed = c);
    });
  }

  @override
  void dispose() {
    _debounce?.cancel();
    _searchCtrl.dispose();
    _scrollCtrl.dispose();
    super.dispose();
  }

  void _onChanged(String q) {
    _debounce?.cancel();
    _debounce = Timer(const Duration(milliseconds: 280), () => _doSearch(q));
  }

  Future<void> _doSearch(String q) async {
    if (q.trim().isEmpty) {
      setState(() {
        _results = [];
        _searching = false;
      });
      return;
    }
    setState(() => _searching = true);
    try {
      final res = await SupabaseService.searchSongs(q, limit: 50);
      if (mounted) setState(() => _results = res);
    } catch (_) {
      if (mounted) setState(() => _results = []);
    }
    if (mounted) setState(() => _searching = false);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF0C0C0E),
      appBar: CompactHeader(title: 'Search', collapsed: _collapsed),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 6, 16, 10),
            child: LiquidGlass(
              blur: 22,
              opacity: 0.10,
              borderRadius: BorderRadius.circular(16),
              borderWidth: 0.7,
              enableSpecular: true,
              child: TextField(
                controller: _searchCtrl,
                style: const TextStyle(color: Colors.white, fontSize: 16),
                cursorColor: const Color(0xfffc3c44),
                onChanged: _onChanged,
                decoration: InputDecoration(
                  hintText: 'Artists, Songs, Albums...',
                  hintStyle: TextStyle(color: Colors.white.withOpacity(0.35)),
                  prefixIcon: Icon(Icons.search, color: Colors.white.withOpacity(0.5)),
                  suffixIcon: _searchCtrl.text.isNotEmpty
                      ? IconButton(
                          icon: const Icon(Icons.clear, color: Colors.white54, size: 20),
                          onPressed: () {
                            _searchCtrl.clear();
                            setState(() => _results = []);
                          },
                        )
                      : null,
                  border: InputBorder.none,
                  contentPadding: const EdgeInsets.symmetric(horizontal: 8, vertical: 14),
                ),
              ),
            ),
          ),
          Expanded(
            child: _searching
                ? const Center(child: CircularProgressIndicator(color: Color(0xfffc3c44)))
                : _results.isNotEmpty
                    ? ListView.builder(
                        controller: _scrollCtrl,
                        padding: const EdgeInsets.fromLTRB(12, 0, 12, 100),
                        itemCount: _results.length,
                        itemBuilder: (ctx, i) {
                          final s = _results[i];
                          return ListTile(
                            contentPadding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                            leading: ClipRRect(
                              borderRadius: BorderRadius.circular(8),
                              child: s['cover_url'] != null
                                  ? Image.network(
                                      s['cover_url'],
                                      width: 52,
                                      height: 52,
                                      fit: BoxFit.cover,
                                      errorBuilder: (_, __, ___) =>
                                          Container(width: 52, height: 52, color: Colors.white12),
                                    )
                                  : Container(width: 52, height: 52, color: Colors.white12),
                            ),
                            title: Text(
                              s['title'] ?? '',
                              style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w600),
                            ),
                            subtitle: Text(
                              s['artist'] ?? '',
                              style: TextStyle(color: Colors.white.withOpacity(0.55)),
                            ),
                            onTap: () => Navigator.pushNamed(context, '/player', arguments: s),
                          );
                        },
                      )
                    : GridView.builder(
                        controller: _scrollCtrl,
                        padding: const EdgeInsets.fromLTRB(16, 0, 16, 100),
                        gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                          crossAxisCount: 2,
                          mainAxisSpacing: 12,
                          crossAxisSpacing: 12,
                          childAspectRatio: 1.35,
                        ),
                        itemCount: constants.BrowseCat.length,
                        itemBuilder: (ctx, i) {
                          final cat = constants.BrowseCat[i];
                          return ClipRRect(
                            borderRadius: BorderRadius.circular(14),
                            child: Stack(
                              fit: StackFit.expand,
                              children: [
                                Image.network(
                                  cat['image'],
                                  fit: BoxFit.cover,
                                  errorBuilder: (_, __, ___) => Container(color: Colors.white10),
                                ),
                                Container(
                                  decoration: BoxDecoration(
                                    gradient: LinearGradient(
                                      begin: Alignment.topCenter,
                                      end: Alignment.bottomCenter,
                                      colors: [Colors.transparent, Colors.black.withOpacity(0.7)],
                                    ),
                                  ),
                                ),
                                Positioned(
                                  left: 12,
                                  bottom: 12,
                                  child: Text(
                                    cat['title'],
                                    style: const TextStyle(
                                      color: Colors.white,
                                      fontWeight: FontWeight.bold,
                                      fontSize: 18,
                                    ),
                                  ),
                                ),
                              ],
                            ),
                          );
                        },
                      ),
          ),
        ],
      ),
    );
  }
}
