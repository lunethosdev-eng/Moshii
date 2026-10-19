import 'package:flutter/material.dart';
import 'package:pokke/services/community_styles_service.dart';
import 'package:pokke/services/settings_service.dart';
import 'package:pokke/widgets/liquid_glass.dart';

class CommunityStylesScreen extends StatefulWidget {
  const CommunityStylesScreen({super.key});

  @override
  State<CommunityStylesScreen> createState() => _CommunityStylesScreenState();
}

class _CommunityStylesScreenState extends State<CommunityStylesScreen>
    with SingleTickerProviderStateMixin {
  late TabController _tabs;
  final _targets = const ['player', 'home', 'library', 'profile'];
  List<Map<String, dynamic>> _items = [];
  bool _loading = true;

  String t(String es, String en) =>
      SettingsService.instance.locale == 'en' ? en : es;

  @override
  void initState() {
    super.initState();
    _tabs = TabController(length: _targets.length, vsync: this);
    _tabs.addListener(() {
      if (!_tabs.indexIsChanging) _load();
    });
    _load();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    final list =
        await CommunityStylesService.instance.list(target: _targets[_tabs.index]);
    if (mounted) setState(() {
      _items = list;
      _loading = false;
    });
  }

  Future<void> _publishCurrent() async {
    final nameCtrl = TextEditingController();
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: const Color(0xFF1A1A1E),
        title: Text(t('Publicar estilo del Player', 'Publish Player style'),
            style: const TextStyle(color: Colors.white)),
        content: TextField(
          controller: nameCtrl,
          style: const TextStyle(color: Colors.white),
          decoration: InputDecoration(
            hintText: t('Nombre del estilo', 'Style name'),
            hintStyle: TextStyle(color: Colors.white.withOpacity(0.4)),
          ),
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: Text(t('Cancelar', 'Cancel'))),
          TextButton(
              onPressed: () => Navigator.pop(ctx, true),
              child: Text(t('Publicar', 'Publish'),
                  style: const TextStyle(color: Color(0xfffc3c44)))),
        ],
      ),
    );
    if (ok != true || nameCtrl.text.trim().isEmpty) return;
    final success = await CommunityStylesService.instance.publish(
      name: nameCtrl.text.trim(),
      target: 'player',
      payload: CommunityStylesService.instance.currentPlayerPayload(),
      description: t('Estilo de player compartido', 'Shared player style'),
    );
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(
      content: Text(success
          ? t('Publicado en Comunidad', 'Published to Community')
          : t('No se pudo publicar (¿tabla community_styles?)',
              'Could not publish (community_styles table?)')),
    ));
    if (success) _load();
  }

  Future<void> _apply(Map<String, dynamic> item) async {
    final payload = Map<String, dynamic>.from(item['payload'] as Map? ?? {});
    final target = (item['target'] ?? 'player').toString();
    if (target == 'player') {
      await CommunityStylesService.instance.applyPlayerPayload(payload);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(t('Estilo aplicado al Player', 'Style applied to Player'))),
        );
      }
    } else {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
              content: Text(t(
                  'Este target aún se está expandiendo',
                  'This target is still being expanded'))),
        );
      }
    }
  }

  @override
  void dispose() {
    _tabs.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF0C0C0E),
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        title: Text(t('Comunidad de estilos', 'Style community')),
        actions: [
          TextButton(
            onPressed: _publishCurrent,
            child: Text(t('Publicar', 'Publish'),
                style: const TextStyle(
                    color: Color(0xfffc3c44), fontWeight: FontWeight.w800)),
          ),
        ],
        bottom: TabBar(
          controller: _tabs,
          isScrollable: true,
          indicatorColor: const Color(0xfffc3c44),
          labelColor: Colors.white,
          unselectedLabelColor: Colors.white54,
          tabs: [
            Tab(text: t('Player', 'Player')),
            Tab(text: t('Home', 'Home')),
            Tab(text: t('Library', 'Library')),
            Tab(text: t('Perfil', 'Profile')),
          ],
        ),
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator(color: Color(0xfffc3c44)))
          : _items.isEmpty
              ? Center(
                  child: Padding(
                    padding: const EdgeInsets.all(32),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(Icons.palette_outlined,
                            size: 48, color: Colors.white.withOpacity(0.25)),
                        const SizedBox(height: 12),
                        Text(
                          t(
                            'Aún no hay estilos públicos aquí.\nDiseña el Player y pulsa Publicar.',
                            'No public styles here yet.\nDesign the Player and tap Publish.',
                          ),
                          textAlign: TextAlign.center,
                          style: TextStyle(
                              color: Colors.white.withOpacity(0.45), height: 1.4),
                        ),
                      ],
                    ),
                  ),
                )
              : RefreshIndicator(
                  onRefresh: _load,
                  color: const Color(0xfffc3c44),
                  child: ListView.builder(
                    padding: const EdgeInsets.fromLTRB(16, 12, 16, 40),
                    itemCount: _items.length,
                    itemBuilder: (_, i) {
                      final item = _items[i];
                      final name = (item['name'] ?? 'Style').toString();
                      final desc = (item['description'] ?? '').toString();
                      final payload =
                          Map<String, dynamic>.from(item['payload'] as Map? ?? {});
                      return Padding(
                        padding: const EdgeInsets.only(bottom: 10),
                        child: LiquidGlass(
                          borderRadius: BorderRadius.circular(16),
                          child: ListTile(
                            contentPadding: const EdgeInsets.symmetric(
                                horizontal: 14, vertical: 8),
                            title: Text(name,
                                style: const TextStyle(
                                    color: Colors.white,
                                    fontWeight: FontWeight.w700)),
                            subtitle: Text(
                              desc.isEmpty
                                  ? [
                                      payload['slider_style'],
                                      payload['cover_style'],
                                      payload['player_bg'],
                                    ].whereType<String>().join(' · ')
                                  : desc,
                              style: TextStyle(
                                  color: Colors.white.withOpacity(0.45),
                                  fontSize: 12),
                            ),
                            trailing: TextButton(
                              onPressed: () => _apply(item),
                              child: Text(t('Aplicar', 'Apply'),
                                  style: const TextStyle(
                                      color: Color(0xfffc3c44),
                                      fontWeight: FontWeight.w800)),
                            ),
                          ),
                        ),
                      );
                    },
                  ),
                ),
    );
  }
}
