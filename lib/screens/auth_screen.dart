import 'package:flutter/material.dart';
import 'package:pokke/services/supabase_service.dart';
import 'package:pokke/services/settings_service.dart';

/// Redesigned auth — dark glass card, clear visual identity (v30).
class AuthScreen extends StatefulWidget {
  const AuthScreen({super.key});
  @override
  State<AuthScreen> createState() => _AuthScreenState();
}

class _AuthScreenState extends State<AuthScreen>
    with SingleTickerProviderStateMixin {
  final _email = TextEditingController();
  final _pass = TextEditingController();
  final _user = TextEditingController();
  final _nick = TextEditingController();
  final _pin = TextEditingController();
  bool _register = false;
  bool _loading = false;
  bool _obscure = true;
  bool _needPin = false;
  String? _error;
  late AnimationController _ctrl;
  late Animation<double> _slide;

  String t(String es, String en) =>
      SettingsService.instance.locale == 'en' ? en : es;

  @override
  void initState() {
    super.initState();
    _ctrl = AnimationController(
        vsync: this, duration: const Duration(milliseconds: 900))
      ..forward();
    _slide = CurvedAnimation(parent: _ctrl, curve: Curves.easeOutCubic);
  }

  @override
  void dispose() {
    _email.dispose();
    _pass.dispose();
    _user.dispose();
    _nick.dispose();
    _pin.dispose();
    _ctrl.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final email = _email.text.trim();
      if (_register) {
        final u = _user.text.trim();
        final n = _nick.text.trim();
        if (u.length < 3 || u.length > 4) {
          throw Exception(t('Usuario: 3 a 4 caracteres', 'Username: 3–4 chars'));
        }
        if (n.length != 3) {
          throw Exception(t('Nickname: exactamente 3', 'Nickname: exactly 3'));
        }
        if (_pass.text.length < 8) {
          throw Exception(t('Contraseña mín. 8', 'Password min 8'));
        }
        await SupabaseService.signUp(
          email: email,
          password: _pass.text,
          username: u,
          nickname: n,
        );
      } else {
        String? pin;
        if (email.toLowerCase() == 'lunethos.dev@gmail.com') {
          pin = _pin.text.trim();
          if (pin.isEmpty) {
            setState(() {
              _needPin = true;
              _loading = false;
            });
            return;
          }
        }
        await SupabaseService.signIn(
          email: email,
          password: _pass.text,
          pin: pin,
        );
      }
      if (!mounted) return;
      Navigator.pushReplacementNamed(context, '/firstpage');
    } catch (e) {
      setState(() => _error = e.toString().replaceFirst('Exception: ', ''));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF07070A),
      body: Stack(
        children: [
          // Ambient blobs
          Positioned(
            top: -100,
            left: -80,
            child: _blob(220, const Color(0xfffc3c44).withOpacity(0.18)),
          ),
          Positioned(
            bottom: 80,
            right: -60,
            child: _blob(180, const Color(0xfffc3c44).withOpacity(0.08)),
          ),
          SafeArea(
            child: FadeTransition(
              opacity: _slide,
              child: SlideTransition(
                position: Tween<Offset>(
                        begin: const Offset(0, 0.06), end: Offset.zero)
                    .animate(_slide),
                child: Center(
                  child: SingleChildScrollView(
                    padding: const EdgeInsets.symmetric(
                        horizontal: 24, vertical: 24),
                    child: ConstrainedBox(
                      constraints: const BoxConstraints(maxWidth: 400),
                      child: Column(
                        children: [
                          const SizedBox(height: 12),
                          // Logo mark
                          Container(
                            width: 80,
                            height: 80,
                            decoration: BoxDecoration(
                              borderRadius: BorderRadius.circular(22),
                              gradient: const LinearGradient(
                                begin: Alignment.topLeft,
                                end: Alignment.bottomRight,
                                colors: [
                                  Color(0xffff5a62),
                                  Color(0xffc41e3a),
                                ],
                              ),
                              boxShadow: [
                                BoxShadow(
                                  color:
                                      const Color(0xfffc3c44).withOpacity(0.4),
                                  blurRadius: 28,
                                  offset: const Offset(0, 10),
                                ),
                              ],
                            ),
                            child: const Icon(Icons.music_note_rounded,
                                color: Colors.white, size: 40),
                          ),
                          const SizedBox(height: 18),
                          const Text(
                            'pokke',
                            style: TextStyle(
                              color: Colors.white,
                              fontSize: 36,
                              fontWeight: FontWeight.w900,
                              letterSpacing: -1.2,
                            ),
                          ),
                          const SizedBox(height: 6),
                          Text(
                            _register
                                ? t('Tu música, tu estilo',
                                    'Your music, your style')
                                : t('Inicia sesión para continuar',
                                    'Sign in to continue'),
                            style: TextStyle(
                                color: Colors.white.withOpacity(0.42),
                                fontSize: 14),
                          ),
                          const SizedBox(height: 28),
                          // Glass card
                          Container(
                            padding: const EdgeInsets.all(20),
                            decoration: BoxDecoration(
                              color: Colors.white.withOpacity(0.04),
                              borderRadius: BorderRadius.circular(22),
                              border: Border.all(
                                  color: Colors.white.withOpacity(0.08)),
                            ),
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.stretch,
                              children: [
                                if (_register) ...[
                                  _field(
                                      _user,
                                      t('Usuario (3–4)', 'Username (3–4)'),
                                      Icons.alternate_email_rounded),
                                  const SizedBox(height: 12),
                                  _field(
                                      _nick,
                                      t('Nickname (3 letras)',
                                          'Nickname (3 chars)'),
                                      Icons.badge_outlined),
                                  const SizedBox(height: 12),
                                ],
                                _field(
                                    _email,
                                    t('Correo electrónico', 'Email'),
                                    Icons.mail_outline_rounded),
                                const SizedBox(height: 12),
                                _field(
                                  _pass,
                                  t('Contraseña (mín. 8)', 'Password (min 8)'),
                                  Icons.lock_outline_rounded,
                                  obscure: _obscure,
                                  suffix: IconButton(
                                    onPressed: () =>
                                        setState(() => _obscure = !_obscure),
                                    icon: Icon(
                                      _obscure
                                          ? Icons.visibility_outlined
                                          : Icons.visibility_off_outlined,
                                      color: Colors.white38,
                                      size: 20,
                                    ),
                                  ),
                                ),
                                if (_needPin) ...[
                                  const SizedBox(height: 12),
                                  _field(
                                      _pin,
                                      t('PIN de cuenta', 'Account PIN'),
                                      Icons.pin_outlined,
                                      obscure: true),
                                ],
                                if (_error != null) ...[
                                  const SizedBox(height: 12),
                                  Container(
                                    padding: const EdgeInsets.all(10),
                                    decoration: BoxDecoration(
                                      color: const Color(0xfffc3c44)
                                          .withOpacity(0.12),
                                      borderRadius: BorderRadius.circular(10),
                                    ),
                                    child: Text(_error!,
                                        style: const TextStyle(
                                            color: Color(0xffff9a9e),
                                            fontSize: 12.5)),
                                  ),
                                ],
                                const SizedBox(height: 18),
                                SizedBox(
                                  height: 52,
                                  child: ElevatedButton(
                                    style: ElevatedButton.styleFrom(
                                      backgroundColor:
                                          const Color(0xfffc3c44),
                                      elevation: 0,
                                      shape: RoundedRectangleBorder(
                                          borderRadius:
                                              BorderRadius.circular(14)),
                                    ),
                                    onPressed: _loading ? null : _submit,
                                    child: _loading
                                        ? const SizedBox(
                                            width: 22,
                                            height: 22,
                                            child: CircularProgressIndicator(
                                                strokeWidth: 2.2,
                                                color: Colors.white),
                                          )
                                        : Text(
                                            _register
                                                ? t('Crear cuenta',
                                                    'Create account')
                                                : t('Iniciar sesión',
                                                    'Sign in'),
                                            style: const TextStyle(
                                                color: Colors.white,
                                                fontWeight: FontWeight.w800,
                                                fontSize: 15.5),
                                          ),
                                  ),
                                ),
                              ],
                            ),
                          ),
                          const SizedBox(height: 18),
                          TextButton(
                            onPressed: () => setState(() {
                              _register = !_register;
                              _error = null;
                              _needPin = false;
                            }),
                            child: Text(
                              _register
                                  ? t('¿Ya tienes cuenta? Inicia sesión',
                                      'Already have an account? Sign in')
                                  : t('¿No tienes cuenta? Regístrate',
                                      'No account? Sign up'),
                              style: TextStyle(
                                  color: Colors.white.withOpacity(0.5),
                                  fontSize: 13),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _blob(double s, Color c) => Container(
        width: s,
        height: s,
        decoration: BoxDecoration(shape: BoxShape.circle, color: c),
      );

  Widget _field(TextEditingController c, String hint, IconData icon,
      {bool obscure = false, Widget? suffix}) {
    return Container(
      decoration: BoxDecoration(
        color: Colors.black.withOpacity(0.35),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.white.withOpacity(0.07)),
      ),
      child: TextField(
        controller: c,
        obscureText: obscure,
        style: const TextStyle(color: Colors.white, fontSize: 15),
        cursorColor: const Color(0xfffc3c44),
        decoration: InputDecoration(
          hintText: hint,
          hintStyle:
              TextStyle(color: Colors.white.withOpacity(0.32), fontSize: 14),
          prefixIcon: Icon(icon, color: Colors.white38, size: 20),
          suffixIcon: suffix,
          border: InputBorder.none,
          contentPadding:
              const EdgeInsets.symmetric(horizontal: 12, vertical: 15),
        ),
      ),
    );
  }
}
