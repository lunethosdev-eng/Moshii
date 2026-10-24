import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:pokke/services/listen_together_service.dart';
import 'package:pokke/services/settings_service.dart';

class ListenTogetherScreen extends StatefulWidget {
  const ListenTogetherScreen({super.key});

  @override
  State<ListenTogetherScreen> createState() => _ListenTogetherScreenState();
}

class _ListenTogetherScreenState extends State<ListenTogetherScreen> {
  final _joinCtrl = TextEditingController();
  String t(String es, String en) =>
      SettingsService.instance.locale == 'en' ? en : es;

  @override
  void dispose() {
    _joinCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final svc = ListenTogetherService.instance;
    return AnimatedBuilder(
      animation: svc,
      builder: (context, _) {
        return Scaffold(
          backgroundColor: const Color(0xFF0C0C0E),
          appBar: AppBar(
            backgroundColor: Colors.transparent,
            title: Text(t('Escuchar juntos', 'Listen together'),
                style: const TextStyle(fontWeight: FontWeight.w800)),
          ),
          body: ListView(
            padding: const EdgeInsets.fromLTRB(20, 8, 20, 40),
            children: [
              Text(
                t(
                  'Beta · Crea una sala y comparte el código. Tus amigos siguen la misma canción y posición (se ampliará con audio dual / audífonos).',
                  'Beta · Create a room and share the code. Friends follow the same track and position (dual audio / headphones coming next).',
                ),
                style: TextStyle(color: Colors.white.withOpacity(0.55), height: 1.4),
              ),
              const SizedBox(height: 24),
              if (!svc.active) ...[
                ElevatedButton.icon(
                  onPressed: () async {
                    final code = await svc.host();
                    if (code != null && context.mounted) {
                      ScaffoldMessenger.of(context).showSnackBar(
                        SnackBar(content: Text(t('Sala $code', 'Room $code'))),
                      );
                    }
                  },
                  style: ElevatedButton.styleFrom(
                    backgroundColor: const Color(0xfffc3c44),
                    foregroundColor: Colors.white,
                    padding: const EdgeInsets.symmetric(vertical: 14),
                  ),
                  icon: const Icon(Icons.meeting_room_outlined),
                  label: Text(t('Crear sala (host)', 'Create room (host)'),
                      style: const TextStyle(fontWeight: FontWeight.w800)),
                ),
                const SizedBox(height: 16),
                Text(t('O únete con código', 'Or join with code'),
                    style: const TextStyle(color: Colors.white70)),
                const SizedBox(height: 8),
                Row(
                  children: [
                    Expanded(
                      child: TextField(
                        controller: _joinCtrl,
                        textCapitalization: TextCapitalization.characters,
                        style: const TextStyle(
                            color: Colors.white,
                            letterSpacing: 3,
                            fontWeight: FontWeight.w700),
                        decoration: InputDecoration(
                          hintText: 'ABC123',
                          hintStyle:
                              TextStyle(color: Colors.white.withOpacity(0.3)),
                          filled: true,
                          fillColor: const Color(0xFF141416),
                          border: OutlineInputBorder(
                              borderRadius: BorderRadius.circular(12),
                              borderSide: BorderSide.none),
                        ),
                      ),
                    ),
                    const SizedBox(width: 10),
                    ElevatedButton(
                      onPressed: () => svc.join(_joinCtrl.text),
                      style: ElevatedButton.styleFrom(
                        backgroundColor: Colors.white12,
                        foregroundColor: Colors.white,
                      ),
                      child: Text(t('Entrar', 'Join')),
                    ),
                  ],
                ),
              ] else ...[
                Container(
                  padding: const EdgeInsets.all(20),
                  decoration: BoxDecoration(
                    color: const Color(0xFF141416),
                    borderRadius: BorderRadius.circular(16),
                    border: Border.all(color: Colors.white12),
                  ),
                  child: Column(
                    children: [
                      Text(t('Código de sala', 'Room code'),
                          style: TextStyle(color: Colors.white.withOpacity(0.5))),
                      const SizedBox(height: 8),
                      Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Text(
                            svc.roomCode ?? '——',
                            style: const TextStyle(
                              color: Colors.white,
                              fontSize: 32,
                              fontWeight: FontWeight.w900,
                              letterSpacing: 6,
                            ),
                          ),
                          IconButton(
                            onPressed: () {
                              Clipboard.setData(
                                  ClipboardData(text: svc.roomCode ?? ''));
                              ScaffoldMessenger.of(context).showSnackBar(
                                SnackBar(
                                    content: Text(t('Copiado', 'Copied'))),
                              );
                            },
                            icon: const Icon(Icons.copy, color: Colors.white54),
                          ),
                        ],
                      ),
                      const SizedBox(height: 8),
                      Text(
                        svc.isHost
                            ? t('Eres el host', 'You are the host')
                            : t('Eres invitado', 'You are a guest'),
                        style: const TextStyle(color: Color(0xfffc3c44)),
                      ),
                      const SizedBox(height: 16),
                      ...svc.peers.map((p) => ListTile(
                            dense: true,
                            leading: const Icon(Icons.headphones,
                                color: Colors.white54, size: 20),
                            title: Text(p,
                                style: const TextStyle(color: Colors.white)),
                          )),
                      SwitchListTile(
                        value: svc.dualSession,
                        onChanged: svc.setDualSession,
                        activeColor: const Color(0xfffc3c44),
                        title: Text(
                            t('Sesión dual (beta)', 'Dual session (beta)'),
                            style: const TextStyle(color: Colors.white)),
                        subtitle: Text(
                          t(
                            'Marca la sesión para dos pares de audífonos / amigos en el mismo dispositivo.',
                            'Mark session for two headsets / friends on the same device.',
                          ),
                          style: TextStyle(
                              color: Colors.white.withOpacity(0.45),
                              fontSize: 12),
                        ),
                      ),
                      const SizedBox(height: 12),
                      OutlinedButton(
                        onPressed: svc.leave,
                        style: OutlinedButton.styleFrom(
                          foregroundColor: Colors.white70,
                        ),
                        child: Text(t('Salir de la sala', 'Leave room')),
                      ),
                    ],
                  ),
                ),
              ],
              if (svc.lastError != null) ...[
                const SizedBox(height: 12),
                Text(svc.lastError!,
                    style: const TextStyle(color: Colors.redAccent)),
              ],
            ],
          ),
        );
      },
    );
  }
}
