import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:pokke/services/settings_service.dart';
import 'package:pokke/widgets/liquid_glass.dart';

/// Editor visual del full player: preview + bloques arrastrables.
class PlayerLayoutEditorScreen extends StatefulWidget {
  const PlayerLayoutEditorScreen({super.key});

  @override
  State<PlayerLayoutEditorScreen> createState() =>
      _PlayerLayoutEditorScreenState();
}

class _PlayerLayoutEditorScreenState extends State<PlayerLayoutEditorScreen> {
  late List<String> _blocks;
  String _sliderStyle = 'liquid';
  String _thumb = 'circle';
  String _time = 'sides';
  String _cover = 'large';
  String _artist = 'below';
  String _bg = 'blur';
  bool _dirty = false;

  static const _labels = {
    'cover': ('Cover', Icons.album_rounded),
    'title': ('Título', Icons.title_rounded),
    'slider': ('Slider', Icons.linear_scale_rounded),
    'controls': ('Controles', Icons.play_circle_rounded),
    'artist': ('Artista', Icons.person_rounded),
    'lyrics': ('Lyrics', Icons.lyrics_rounded),
  };

  String t(String es, String en) =>
      SettingsService.instance.locale == 'en' ? en : es;

  @override
  void initState() {
    super.initState();
    final s = SettingsService.instance;
    _blocks = List<String>.from(s.playerLayout);
    _sliderStyle = s.sliderStyle;
    _thumb = s.sliderThumb;
    _time = s.timeDisplay;
    _cover = s.coverStyle;
    _artist = s.artistStyle;
    _bg = s.playerBackground;
  }

  Future<void> _save() async {
    final s = SettingsService.instance;
    await s.setPlayerLayout(_blocks);
    await s.setString('slider_style', _sliderStyle);
    await s.setString('slider_thumb', _thumb);
    await s.setString('time_display', _time);
    await s.setString('cover_style', _cover);
    await s.setString('artist_style', _artist);
    await s.setString('player_bg', _bg);
    setState(() => _dirty = false);
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(t('Layout guardado', 'Layout saved'))),
      );
    }
  }

  void _mark() => setState(() => _dirty = true);

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF070709),
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        title: Text(t('Editor del Player', 'Player Editor'),
            style: const TextStyle(fontWeight: FontWeight.w700)),
        actions: [
          if (_dirty)
            TextButton(
              onPressed: _save,
              child: Text(t('Guardar', 'Save'),
                  style: const TextStyle(
                      color: Color(0xfffc3c44), fontWeight: FontWeight.w800)),
            ),
        ],
      ),
      body: Column(
        children: [
          // Hint
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
            child: Text(
              t(
                'Arrastra los bloques para cambiar el orden. La vista previa se actualiza al instante.',
                'Drag blocks to reorder. Preview updates live.',
              ),
              style: TextStyle(color: Colors.white.withOpacity(0.45), fontSize: 12),
            ),
          ),
          // Preview + blocks side by side on wide, stacked on phone
          Expanded(
            child: ListView(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 32),
              children: [
                _previewPhone(),
                const SizedBox(height: 20),
                Text(t('Bloques (mantén y arrastra)', 'Blocks (long-press & drag)'),
                    style: const TextStyle(
                        color: Colors.white,
                        fontWeight: FontWeight.w700,
                        fontSize: 15)),
                const SizedBox(height: 10),
                ReorderableListView.builder(
                  shrinkWrap: true,
                  physics: const NeverScrollableScrollPhysics(),
                  itemCount: _blocks.length,
                  onReorder: (oldI, newI) {
                    HapticFeedback.selectionClick();
                    setState(() {
                      if (newI > oldI) newI -= 1;
                      final item = _blocks.removeAt(oldI);
                      _blocks.insert(newI, item);
                      _dirty = true;
                    });
                  },
                  itemBuilder: (ctx, i) {
                    final id = _blocks[i];
                    final meta = _labels[id] ?? (id, Icons.widgets);
                    return Container(
                      key: ValueKey(id),
                      margin: const EdgeInsets.only(bottom: 8),
                      decoration: BoxDecoration(
                        color: const Color(0xFF141418),
                        borderRadius: BorderRadius.circular(14),
                        border: Border.all(color: Colors.white.withOpacity(0.06)),
                      ),
                      child: ListTile(
                        leading: Container(
                          width: 36,
                          height: 36,
                          decoration: BoxDecoration(
                            color: const Color(0xfffc3c44).withOpacity(0.15),
                            borderRadius: BorderRadius.circular(10),
                          ),
                          child: Icon(meta.$2, color: const Color(0xfffc3c44), size: 20),
                        ),
                        title: Text(meta.$1,
                            style: const TextStyle(
                                color: Colors.white, fontWeight: FontWeight.w600)),
                        subtitle: Text(
                          t('Posición ${i + 1}', 'Position ${i + 1}'),
                          style: TextStyle(
                              color: Colors.white.withOpacity(0.4), fontSize: 11),
                        ),
                        trailing: const Icon(Icons.drag_handle, color: Colors.white38),
                      ),
                    );
                  },
                ),
                const SizedBox(height: 18),
                Text(t('Estilo del slider', 'Slider style'),
                    style: const TextStyle(
                        color: Colors.white, fontWeight: FontWeight.w700, fontSize: 15)),
                const SizedBox(height: 8),
                _styleGrid(
                  options: const [
                    ('minimal', 'Minimal', 'Barra fina'),
                    ('liquid', 'Liquid', 'Glow suave'),
                    ('bold', 'Bold', 'Gruesa'),
                    ('timeline', 'Timeline', 'Marcas 30s'),
                    ('hidden', 'Hidden', 'Casi invisible'),
                  ],
                  value: _sliderStyle,
                  onPick: (v) {
                    setState(() {
                      _sliderStyle = v;
                      _dirty = true;
                    });
                  },
                ),
                const SizedBox(height: 14),
                Text(t('Thumb', 'Thumb'),
                    style: const TextStyle(
                        color: Colors.white, fontWeight: FontWeight.w700, fontSize: 15)),
                const SizedBox(height: 8),
                _styleGrid(
                  options: const [
                    ('circle', 'Círculo', ''),
                    ('line', 'Línea', ''),
                    ('hidden', 'Oculto', ''),
                  ],
                  value: _thumb,
                  onPick: (v) {
                    setState(() {
                      _thumb = v;
                      _dirty = true;
                    });
                  },
                ),
                const SizedBox(height: 14),
                Text(t('Tiempo', 'Time labels'),
                    style: const TextStyle(
                        color: Colors.white, fontWeight: FontWeight.w700, fontSize: 15)),
                const SizedBox(height: 8),
                _styleGrid(
                  options: const [
                    ('sides', 'Izq · Der', ''),
                    ('center', 'Centro', ''),
                    ('hidden', 'Oculto', ''),
                  ],
                  value: _time,
                  onPick: (v) {
                    setState(() {
                      _time = v;
                      _dirty = true;
                    });
                  },
                ),
                const SizedBox(height: 14),
                Text(t('Cover', 'Cover'),
                    style: const TextStyle(
                        color: Colors.white, fontWeight: FontWeight.w700, fontSize: 15)),
                const SizedBox(height: 8),
                _styleGrid(
                  options: const [
                    ('large', 'Grande', ''),
                    ('medium', 'Mediano', ''),
                    ('rotating', 'Disco', ''),
                    ('blur_bg', 'Blur fondo', ''),
                  ],
                  value: _cover,
                  onPick: (v) {
                    setState(() {
                      _cover = v;
                      _dirty = true;
                    });
                  },
                ),
                const SizedBox(height: 14),
                Text(t('Fondo del player', 'Player background'),
                    style: const TextStyle(
                        color: Colors.white, fontWeight: FontWeight.w700, fontSize: 15)),
                const SizedBox(height: 8),
                _styleGrid(
                  options: const [
                    ('blur', 'Blur', ''),
                    ('solid', 'Sólido', ''),
                    ('gradient', 'Gradiente', ''),
                  ],
                  value: _bg,
                  onPick: (v) {
                    setState(() {
                      _bg = v;
                      _dirty = true;
                    });
                  },
                ),
                const SizedBox(height: 24),
                SizedBox(
                  width: double.infinity,
                  height: 50,
                  child: ElevatedButton(
                    style: ElevatedButton.styleFrom(
                      backgroundColor: const Color(0xfffc3c44),
                      shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(14)),
                    ),
                    onPressed: _save,
                    child: Text(t('Guardar layout', 'Save layout'),
                        style: const TextStyle(
                            color: Colors.white,
                            fontWeight: FontWeight.w800,
                            fontSize: 16)),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _styleGrid({
    required List<(String, String, String)> options,
    required String value,
    required ValueChanged<String> onPick,
  }) {
    return Wrap(
      spacing: 8,
      runSpacing: 8,
      children: options.map((o) {
        final sel = o.$1 == value;
        return GestureDetector(
          onTap: () {
            HapticFeedback.lightImpact();
            onPick(o.$1);
          },
          child: AnimatedContainer(
            duration: const Duration(milliseconds: 180),
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
            decoration: BoxDecoration(
              color: sel
                  ? const Color(0xfffc3c44)
                  : Colors.white.withOpacity(0.06),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(
                color: sel
                    ? const Color(0xfffc3c44)
                    : Colors.white.withOpacity(0.08),
              ),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(o.$2,
                    style: TextStyle(
                      color: Colors.white,
                      fontWeight: sel ? FontWeight.w800 : FontWeight.w600,
                      fontSize: 13,
                    )),
                if (o.$3.isNotEmpty)
                  Text(o.$3,
                      style: TextStyle(
                          color: Colors.white.withOpacity(sel ? 0.85 : 0.4),
                          fontSize: 10)),
              ],
            ),
          ),
        );
      }).toList(),
    );
  }

  Widget _previewPhone() {
    final coverH = _cover == 'large'
        ? 160.0
        : _cover == 'medium'
            ? 110.0
            : 90.0;
    return Center(
      child: Container(
        width: 220,
        height: 400,
        decoration: BoxDecoration(
          color: Colors.black,
          borderRadius: BorderRadius.circular(28),
          border: Border.all(color: Colors.white.withOpacity(0.12), width: 2),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withOpacity(0.5),
              blurRadius: 30,
              offset: const Offset(0, 12),
            ),
          ],
        ),
        child: ClipRRect(
          borderRadius: BorderRadius.circular(26),
          child: Stack(
            fit: StackFit.expand,
            children: [
              if (_bg == 'blur' || _bg == 'gradient')
                Container(
                  decoration: BoxDecoration(
                    gradient: LinearGradient(
                      begin: Alignment.topCenter,
                      end: Alignment.bottomCenter,
                      colors: [
                        const Color(0xfffc3c44).withOpacity(0.35),
                        Colors.black,
                      ],
                    ),
                  ),
                )
              else
                const ColoredBox(color: Color(0xFF0A0A0C)),
              Padding(
                padding: const EdgeInsets.fromLTRB(14, 28, 14, 16),
                child: Column(
                  children: [
                    for (final id in _blocks) ...[
                      if (id == 'cover')
                        Container(
                          height: coverH,
                          width: coverH,
                          margin: const EdgeInsets.only(bottom: 10),
                          decoration: BoxDecoration(
                            borderRadius: BorderRadius.circular(
                                _cover == 'rotating' ? 999 : 12),
                            gradient: const LinearGradient(
                              colors: [Color(0xFF3A3A40), Color(0xFF1A1A1E)],
                            ),
                          ),
                          child: const Icon(Icons.music_note,
                              color: Colors.white24, size: 36),
                        )
                      else if (id == 'title')
                        const Padding(
                          padding: EdgeInsets.only(bottom: 6),
                          child: Text('Canción de ejemplo',
                              style: TextStyle(
                                  color: Colors.white,
                                  fontWeight: FontWeight.w800,
                                  fontSize: 14)),
                        )
                      else if (id == 'artist')
                        Padding(
                          padding: const EdgeInsets.only(bottom: 8),
                          child: _artist == 'chip'
                              ? Container(
                                  padding: const EdgeInsets.symmetric(
                                      horizontal: 8, vertical: 3),
                                  decoration: BoxDecoration(
                                    color: Colors.white12,
                                    borderRadius: BorderRadius.circular(20),
                                  ),
                                  child: const Text('Artista',
                                      style: TextStyle(
                                          color: Colors.white70, fontSize: 10)),
                                )
                              : Text('Artista',
                                  style: TextStyle(
                                      color: Colors.white.withOpacity(0.5),
                                      fontSize: 11)),
                        )
                      else if (id == 'slider')
                        Padding(
                          padding: const EdgeInsets.symmetric(vertical: 6),
                          child: _fakeSlider(),
                        )
                      else if (id == 'controls')
                        const Padding(
                          padding: EdgeInsets.symmetric(vertical: 6),
                          child: Row(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              Icon(Icons.skip_previous, color: Colors.white54, size: 22),
                              SizedBox(width: 16),
                              Icon(Icons.play_circle_filled,
                                  color: Colors.white, size: 36),
                              SizedBox(width: 16),
                              Icon(Icons.skip_next, color: Colors.white54, size: 22),
                            ],
                          ),
                        )
                      else if (id == 'lyrics')
                        Padding(
                          padding: const EdgeInsets.only(top: 4),
                          child: Text('♪ lyrics preview…',
                              style: TextStyle(
                                  color: Colors.white.withOpacity(0.35),
                                  fontSize: 10,
                                  fontStyle: FontStyle.italic)),
                        ),
                    ],
                  ],
                ),
              ),
              // notch
              Positioned(
                top: 8,
                left: 0,
                right: 0,
                child: Center(
                  child: Container(
                    width: 48,
                    height: 5,
                    decoration: BoxDecoration(
                      color: Colors.white24,
                      borderRadius: BorderRadius.circular(3),
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _fakeSlider() {
    double h = 3;
    double thumb = 6;
    switch (_sliderStyle) {
      case 'minimal':
        h = 2;
        thumb = 4;
        break;
      case 'bold':
        h = 6;
        thumb = 9;
        break;
      case 'hidden':
        h = 1.5;
        thumb = 0;
        break;
      default:
        h = 3.5;
        thumb = 6;
    }
    return Column(
      children: [
        Stack(
          alignment: Alignment.centerLeft,
          children: [
            Container(
              height: h,
              decoration: BoxDecoration(
                color: Colors.white24,
                borderRadius: BorderRadius.circular(99),
              ),
            ),
            FractionallySizedBox(
              widthFactor: 0.35,
              child: Container(
                height: h,
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(99),
                  boxShadow: _sliderStyle == 'liquid'
                      ? [
                          BoxShadow(
                            color: Colors.white.withOpacity(0.35),
                            blurRadius: 6,
                          )
                        ]
                      : null,
                ),
              ),
            ),
            if (_thumb != 'hidden' && _sliderStyle != 'hidden')
              Positioned(
                left: 70,
                child: Container(
                  width: _thumb == 'line' ? 2 : thumb,
                  height: _thumb == 'line' ? 12 : thumb,
                  decoration: BoxDecoration(
                    color: Colors.white,
                    borderRadius: BorderRadius.circular(_thumb == 'line' ? 1 : 99),
                  ),
                ),
              ),
          ],
        ),
        if (_time != 'hidden') ...[
          const SizedBox(height: 4),
          if (_time == 'center')
            Text('1:12 / 3:08',
                style: TextStyle(
                    color: Colors.white.withOpacity(0.4), fontSize: 9))
          else
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text('1:12',
                    style: TextStyle(
                        color: Colors.white.withOpacity(0.4), fontSize: 9)),
                Text('3:08',
                    style: TextStyle(
                        color: Colors.white.withOpacity(0.4), fontSize: 9)),
              ],
            ),
        ],
      ],
    );
  }
}
