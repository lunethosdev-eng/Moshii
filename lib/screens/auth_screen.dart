import 'package:flutter/material.dart';
import 'package:pokke/services/supabase_service.dart';
import 'package:pokke/services/settings_service.dart';

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
  bool _register = false;
  bool _loading = false;
  bool _obscure = true;
  String? _error;
  late AnimationController _fade;

  String t(String es, String en) =>
      SettingsService.instance.locale == 'en' ? en : es;

  @override
  void initState() {
    super.initState();
    _fade = AnimationController(
        vsync: this, duration: const Duration(milliseconds: 800))
      ..forward();
  }

  @override
  void dispose() {
    _email.dispose();
    _pass.dispose();
    _user.dispose();
    _nick.dispose();
    _fade.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      if (_register) {
        if (_user.text.trim().length < 3) {
          throw Exception(t('Usuario 3–4+ caracteres', 'Username 3+ chars'));
        }
        if (_pass.text.length < 8) {
          throw Exception(t('Contraseña mín. 8', 'Password min 8'));
        }
        await SupabaseService.signUp(
          email: _email.text.trim(),
          password: _pass.text,
          username: _user.text.trim(),
          nickname: _nick.text.trim().isEmpty ? _user.text.trim() : _nick.text.trim(),
        );
      } else {
        await SupabaseService.signIn(
          email: _email.text.trim(),
          password: _pass.text,
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
      body: Stack(
        fit: StackFit.expand,
        children: [
          Container(
            decoration: const BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
                colors: [
                  Color(0xFF1A0A0C),
                  Color(0xFF0A0A0C),
                  Color(0xFF050507),
                ],
              ),
            ),
          ),
          Positioned(
            top: -80,
            right: -60,
            child: Container(
              width: 240,
              height: 240,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: const Color(0xfffc3c44).withOpacity(0.12),
              ),
            ),
          ),
          Positioned(
            bottom: -40,
            left: -40,
            child: Container(
              width: 180,
              height: 180,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: const Color(0xfffc3c44).withOpacity(0.06),
              ),
            ),
          ),
          SafeArea(
            child: FadeTransition(
              opacity: _fade,
              child: SingleChildScrollView(
                padding: const EdgeInsets.fromLTRB(28, 40, 28, 28),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    const SizedBox(height: 24),
                    Center(
                      child: Container(
                        width: 72,
                        height: 72,
                        decoration: BoxDecoration(
                          borderRadius: BorderRadius.circular(20),
                          gradient: const LinearGradient(
                            colors: [Color(0xfffc3c44), Color(0xffc41e3a)],
                          ),
                          boxShadow: [
                            BoxShadow(
                              color: const Color(0xfffc3c44).withOpacity(0.35),
                              blurRadius: 24,
                              offset: const Offset(0, 8),
                            ),
                          ],
                        ),
                        child: const Icon(Icons.music_note_rounded,
                            color: Colors.white, size: 36),
                      ),
                    ),
                    const SizedBox(height: 20),
                    Text(
                      'pokke',
                      textAlign: TextAlign.center,
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 34,
                        fontWeight: FontWeight.w900,
                        letterSpacing: -1,
                      ),
                    ),
                    const SizedBox(height: 6),
                    Text(
                      _register
                          ? t('Crea tu cuenta', 'Create your account')
                          : t('Inicia sesión para continuar',
                              'Sign in to continue'),
                      textAlign: TextAlign.center,
                      style: TextStyle(
                          color: Colors.white.withOpacity(0.45), fontSize: 14),
                    ),
                    const SizedBox(height: 36),
                    if (_register) ...[
                      _field(_user, t('Usuario (3–4+)', 'Username (3+)'),
                          Icons.alternate_email),
                      const SizedBox(height: 12),
                      _field(_nick, t('Nickname (exactamente 3)', 'Nickname (exactly 3)'), Icons.badge_outlined),
                      const SizedBox(height: 12),
                    ],
                    _field(_email, t('Correo electrónico', 'Email'),
                        Icons.mail_outline),
                    const SizedBox(height: 12),
                    _field(
                      _pass,
                      t('Contraseña (mín. 8)', 'Password (min 8)'),
                      Icons.lock_outline,
                      obscure: _obscure,
                      suffix: IconButton(
                        onPressed: () => setState(() => _obscure = !_obscure),
                        icon: Icon(
                          _obscure
                              ? Icons.visibility_outlined
                              : Icons.visibility_off_outlined,
                          color: Colors.white38,
                        ),
                      ),
                    ),
                    if (_error != null) ...[
                      const SizedBox(height: 14),
                      Container(
                        padding: const EdgeInsets.all(12),
                        decoration: BoxDecoration(
                          color: const Color(0xfffc3c44).withOpacity(0.12),
                          borderRadius: BorderRadius.circular(12),
                        ),
                        child: Text(_error!,
                            style: const TextStyle(
                                color: Color(0xffff8a8a), fontSize: 13)),
                      ),
                    ],
                    const SizedBox(height: 24),
                    SizedBox(
                      height: 54,
                      child: ElevatedButton(
                        style: ElevatedButton.styleFrom(
                          backgroundColor: const Color(0xfffc3c44),
                          elevation: 0,
                          shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(16)),
                        ),
                        onPressed: _loading ? null : _submit,
                        child: _loading
                            ? const SizedBox(
                                width: 22,
                                height: 22,
                                child: CircularProgressIndicator(
                                    strokeWidth: 2, color: Colors.white),
                              )
                            : Text(
                                _register
                                    ? t('Crear cuenta', 'Create account')
                                    : t('Iniciar sesión', 'Sign in'),
                                style: const TextStyle(
                                    color: Colors.white,
                                    fontWeight: FontWeight.w800,
                                    fontSize: 16),
                              ),
                      ),
                    ),
                    const SizedBox(height: 18),
                    TextButton(
                      onPressed: () => setState(() {
                        _register = !_register;
                        _error = null;
                      }),
                      child: Text(
                        _register
                            ? t('¿Ya tienes cuenta? Inicia sesión',
                                'Already have an account? Sign in')
                            : t('¿No tienes cuenta? Regístrate',
                                'No account? Sign up'),
                        style: TextStyle(
                            color: Colors.white.withOpacity(0.55),
                            fontSize: 13),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _field(TextEditingController c, String hint, IconData icon,
      {bool obscure = false, Widget? suffix}) {
    return Container(
      decoration: BoxDecoration(
        color: Colors.white.withOpacity(0.06),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: Colors.white.withOpacity(0.08)),
      ),
      child: TextField(
        controller: c,
        obscureText: obscure,
        style: const TextStyle(color: Colors.white),
        cursorColor: const Color(0xfffc3c44),
        decoration: InputDecoration(
          hintText: hint,
          hintStyle: TextStyle(color: Colors.white.withOpacity(0.35)),
          prefixIcon: Icon(icon, color: Colors.white38, size: 20),
          suffixIcon: suffix,
          border: InputBorder.none,
          contentPadding:
              const EdgeInsets.symmetric(horizontal: 12, vertical: 16),
        ),
      ),
    );
  }
}
