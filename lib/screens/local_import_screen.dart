import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:pokke/services/local_music_service.dart';
import 'package:pokke/services/player_controller.dart';
import 'package:pokke/services/settings_service.dart';
import 'package:pokke/widgets/liquid_glass.dart';
import 'package:pokke/widgets/context_menu.dart';

class LocalImportScreen extends StatefulWidget {
  const LocalImportScreen({super.key});

  @override
  State<LocalImportScreen> createState() => _LocalImportScreenState();
}

class _LocalImportScreenState extends State<LocalImportScreen> {
  final _selected = <String>{};
  bool _selectMode = false;
  bool _busy = false;

  String t(String es, String en) =>
      SettingsService.instance.locale == 'en' ? en : es;

  @override
  void initState() {
    super.initState();
    LocalMusicService.instance.load().then((_) {
      if (mounted) setState(() {});
    });
  }

  Future<void> _pickFiles() async {
    setState(() => _busy = true);
    final n = await LocalMusicService.instance.pickFiles();
    if (!mounted) return;
    setState(() => _busy = false);
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(t('Importadas: $n', 'Imported: $n')),
        backgroundColor: const Color(0xFF1A1A1E),
      ),
    );
  }

  Future<void> _pickFolder() async {
    setState(() => _busy = true);
    final n = await LocalMusicService.instance.pickFolder();
    if (!mounted) return;
    setState(() => _busy = false);
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(t('Desde carpeta: $n', 'From folder: $n')),
        backgroundColor: const Color(0xFF1A1A1E),
      ),
    );
  }

  void _toggleSelect(String id) {
    setState(() {
      if (_selected.contains(id)) {
        _selected.remove(id);
      } else {
        _selected.add(id);
      }
    });
  }

  Future<void> _deleteSelected() async {
    if (_selected.isEmpty) return;
    await LocalMusicService.instance.removeByIds(_selected);
    setState(() {
      _selected.clear();
      _selectMode = false;
    });
  }

  Future<void> _playSelected() async {
    final tracks = LocalMusicService.instance.tracks
        .where((t) => _selected.contains(t['id']?.toString()))
        .toList();
    if (tracks.isEmpty) return;
    await PlayerController.instance.playPlaylist(tracks);
  }

  void _showItemMenu(Map<String, dynamic> track) {
    final id = (track['id'] ?? '').toString();
    showPokkeContextMenu(
      context: context,
      items: [
        ContextMenuItem(
          icon: Icons.play_arrow_rounded,
          label: t('Reproducir', 'Play'),
          onTap: () => PlayerController.instance.playSong(track),
        ),
        ContextMenuItem(
          icon: Icons.playlist_add,
          label: t('Reproducir siguientes', 'Play next in queue'),
          onTap: () {
            final all = LocalMusicService.instance.tracks;
            final idx = all.indexWhere((e) => e['id'] == id);
            PlayerController.instance.playPlaylist(
              all,
              startIndex: idx >= 0 ? idx : 0,
            );
          },
        ),
        ContextMenuItem(
          icon: Icons.check_circle_outline,
          label: t('Seleccionar', 'Select'),
          onTap: () {
            setState(() {
              _selectMode = true;
              _selected.add(id);
            });
          },
        ),
        ContextMenuItem(
          icon: Icons.delete_outline,
          label: t('Quitar de biblioteca local', 'Remove from local library'),
          destructive: true,
          onTap: () async {
            await LocalMusicService.instance.removeByIds([id]);
            if (mounted) setState(() {});
          },
        ),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: LocalMusicService.instance,
      builder: (context, _) {
        final tracks = LocalMusicService.instance.tracks;
        return Scaffold(
          backgroundColor: const Color(0xFF0C0C0E),
          appBar: AppBar(
            backgroundColor: Colors.transparent,
            title: Text(t('Música local', 'Local music')),
            actions: [
              if (_selectMode) ...[
                IconButton(
                  tooltip: t('Reproducir', 'Play'),
                  onPressed: _selected.isEmpty ? null : _playSelected,
                  icon: const Icon(Icons.play_arrow_rounded),
                ),
                IconButton(
                  tooltip: t('Eliminar', 'Delete'),
                  onPressed: _selected.isEmpty ? null : _deleteSelected,
                  icon: const Icon(Icons.delete_outline, color: Color(0xfffc3c44)),
                ),
                TextButton(
                  onPressed: () => setState(() {
                    _selectMode = false;
                    _selected.clear();
                  }),
                  child: Text(t('Listo', 'Done'),
                      style: const TextStyle(color: Colors.white70)),
                ),
              ] else
                IconButton(
                  tooltip: t('Seleccionar', 'Select'),
                  onPressed: () => setState(() => _selectMode = true),
                  icon: const Icon(Icons.checklist_rtl),
                ),
            ],
          ),
          body: Column(
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 4, 16, 12),
                child: Row(
                  children: [
                    Expanded(
                      child: _actionChip(
                        Icons.audio_file_outlined,
                        t('Elegir canciones', 'Pick songs'),
                        _busy ? null : _pickFiles,
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: _actionChip(
                        Icons.folder_open_outlined,
                        t('Elegir carpeta', 'Pick folder'),
                        _busy ? null : _pickFolder,
                      ),
                    ),
                  ],
                ),
              ),
              if (_busy)
                const LinearProgressIndicator(
                  color: Color(0xfffc3c44),
                  backgroundColor: Colors.white12,
                ),
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
                child: Row(
                  children: [
                    Text(
                      t('${tracks.length} canciones', '${tracks.length} tracks'),
                      style: TextStyle(
                          color: Colors.white.withOpacity(0.5), fontSize: 13),
                    ),
                    const Spacer(),
                    if (tracks.isNotEmpty)
                      TextButton(
                        onPressed: () async {
                          final ok = await showDialog<bool>(
                            context: context,
                            builder: (ctx) => AlertDialog(
                              backgroundColor: const Color(0xFF1A1A1E),
                              title: Text(t('Vaciar local', 'Clear local'),
                                  style: const TextStyle(color: Colors.white)),
                              content: Text(
                                t('¿Quitar todas las canciones importadas?',
                                    'Remove all imported tracks?'),
                                style: const TextStyle(color: Colors.white70),
                              ),
                              actions: [
                                TextButton(
                                    onPressed: () => Navigator.pop(ctx, false),
                                    child: Text(t('Cancelar', 'Cancel'))),
                                TextButton(
                                    onPressed: () => Navigator.pop(ctx, true),
                                    child: Text(t('Vaciar', 'Clear'),
                                        style: const TextStyle(
                                            color: Color(0xfffc3c44)))),
                              ],
                            ),
                          );
                          if (ok == true) {
                            await LocalMusicService.instance.clearAll();
                          }
                        },
                        child: Text(t('Vaciar', 'Clear'),
                            style: const TextStyle(color: Colors.white38)),
                      ),
                  ],
                ),
              ),
              Expanded(
                child: tracks.isEmpty
                    ? Center(
                        child: Padding(
                          padding: const EdgeInsets.all(32),
                          child: Text(
                            t(
                              'Elige canciones o carpetas del almacenamiento. No se importan todas a la fuerza: tú decides cuáles.',
                              'Pick songs or folders from storage. Nothing is bulk-forced — you choose what to import.',
                            ),
                            textAlign: TextAlign.center,
                            style: TextStyle(
                                color: Colors.white.withOpacity(0.45),
                                height: 1.4),
                          ),
                        ),
                      )
                    : ListView.builder(
                        padding: const EdgeInsets.fromLTRB(12, 0, 12, 40),
                        itemCount: tracks.length,
                        itemBuilder: (ctx, i) {
                          final track = tracks[i];
                          final id = (track['id'] ?? '').toString();
                          final selected = _selected.contains(id);
                          return Padding(
                            padding: const EdgeInsets.only(bottom: 6),
                            child: LiquidGlass(
                              blur: 16,
                              opacity: 0.08,
                              borderRadius: BorderRadius.circular(12),
                              child: ListTile(
                                onTap: () {
                                  if (_selectMode) {
                                    _toggleSelect(id);
                                  } else {
                                    PlayerController.instance.playSong(track);
                                  }
                                },
                                onLongPress: () {
                                  HapticFeedback.mediumImpact();
                                  if (_selectMode) {
                                    _toggleSelect(id);
                                  } else {
                                    _showItemMenu(track);
                                  }
                                },
                                leading: _selectMode
                                    ? Icon(
                                        selected
                                            ? Icons.check_circle
                                            : Icons.circle_outlined,
                                        color: selected
                                            ? const Color(0xfffc3c44)
                                            : Colors.white38,
                                      )
                                    : const Icon(Icons.music_note,
                                        color: Colors.white54),
                                title: Text(
                                  track['title'] ?? '',
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                  style: const TextStyle(color: Colors.white),
                                ),
                                subtitle: Text(
                                  track['path'] ?? track['artist'] ?? '',
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                  style: TextStyle(
                                      color: Colors.white.withOpacity(0.4),
                                      fontSize: 11),
                                ),
                                trailing: _selectMode
                                    ? null
                                    : IconButton(
                                        icon: const Icon(Icons.more_vert,
                                            color: Colors.white38),
                                        onPressed: () => _showItemMenu(track),
                                      ),
                              ),
                            ),
                          );
                        },
                      ),
              ),
            ],
          ),
        );
      },
    );
  }

  Widget _actionChip(IconData icon, String label, VoidCallback? onTap) {
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(14),
        child: LiquidGlass(
          blur: 18,
          opacity: 0.1,
          borderRadius: BorderRadius.circular(14),
          child: Padding(
            padding: const EdgeInsets.symmetric(vertical: 14, horizontal: 12),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Icon(icon, color: const Color(0xfffc3c44), size: 22),
                const SizedBox(width: 8),
                Flexible(
                  child: Text(label,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                          color: Colors.white, fontWeight: FontWeight.w600)),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
