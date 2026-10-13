import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:pokke/services/supabase_service.dart';
import 'package:pokke/widgets/liquid_glass.dart';

class AuthScreen extends StatefulWidget {
  const AuthScreen({super.key});

  @override
  State<AuthScreen> createState() => _AuthScreenState();
}

class _AuthScreenState extends State<AuthScreen> with SingleTickerProviderStateMixin {
  bool isLogin = true;
  bool isLoading = false;
  bool showPinDialog = false;
  String? errorMessage;

  final emailCtrl = TextEditingController();
  final passCtrl = TextEditingController();
  final userCtrl = TextEditingController();
  final nickCtrl = TextEditingController();
  final pinCtrl = TextEditingController();

  late AnimationController _animCtrl;
  late Animation<double> _fadeAnim;

  @override
  void initState() {
    super.initState();
    _animCtrl = AnimationController(vsync: this, duration: const Duration(milliseconds: 600));
    _fadeAnim = CurvedAnimation(parent: _animCtrl, curve: Curves.easeOutCubic);
    _animCtrl.forward();
  }

  @override
  void dispose() {
    emailCtrl.dispose();
    passCtrl.dispose();
    userCtrl.dispose();
    nickCtrl.dispose();
    pinCtrl.dispose();
    _animCtrl.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    setState(() {
      isLoading = true;
      errorMessage = null;
    });

    try {
      if (isLogin) {
        final email = emailCtrl.text.trim().toLowerCase();
        if (email == SupabaseService.reservedEmail) {
          // Ask for PIN
          final pin = await _askPin();
          if (pin == null) {
            setState(() => isLoading = false);
            return;
          }
          await SupabaseService.signIn(
            email: email,
            password: passCtrl.text,
            pin: pin,
          );
        } else {
          await SupabaseService.signIn(
            email: email,
            password: passCtrl.text,
          );
        }
      } else {
        await SupabaseService.signUp(
          email: emailCtrl.text.trim(),
          password: passCtrl.text,
          username: userCtrl.text.trim(),
          nickname: nickCtrl.text.trim(),
        );
      }
      if (mounted) {
        Navigator.of(context).pushReplacementNamed('/firstpage');
      }
    } catch (e) {
      setState(() {
        errorMessage = e.toString().replaceAll('Exception: ', '');
      });
    } finally {
      if (mounted) setState(() => isLoading = false);
    }
  }

  Future<String?> _askPin() async {
    pinCtrl.clear();
    return showDialog<String>(
      context: context,
      barrierDismissible: false,
      builder: (ctx) {
        return AlertDialog(
          backgroundColor: const Color(0xFF1C1C1E),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
          title: const Text('PIN requerido', style: TextStyle(color: Colors.white)),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Text(
                'Esta cuenta está protegida. Ingresa el PIN de 7 dígitos.',
                style: TextStyle(color: Colors.white70, fontSize: 14),
              ),
              const SizedBox(height: 16),
              TextField(
                controller: pinCtrl,
                keyboardType: TextInputType.number,
                maxLength: 7,
                obscureText: true,
                style: const TextStyle(color: Colors.white, letterSpacing: 8, fontSize: 22),
                textAlign: TextAlign.center,
                decoration: InputDecoration(
                  counterText: '',
                  filled: true,
                  fillColor: Colors.white.withOpacity(0.08),
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(12),
                    borderSide: BorderSide.none,
                  ),
                ),
                inputFormatters: [FilteringTextInputFormatter.digitsOnly],
              ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(ctx),
              child: const Text('Cancelar', style: TextStyle(color: Colors.white54)),
            ),
            TextButton(
              onPressed: () => Navigator.pop(ctx, pinCtrl.text),
              child: const Text('Continuar', style: TextStyle(color: Color(0xfffc3c44))),
            ),
          ],
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF0A0A0C),
      body: SafeArea(
        child: FadeTransition(
          opacity: _fadeAnim,
          child: SingleChildScrollView(
            padding: const EdgeInsets.symmetric(horizontal: 28, vertical: 24),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.center,
              children: [
                const SizedBox(height: 40),
                Image.asset('assets/logo/pokke_logo.png', height: 90),
                const SizedBox(height: 16),
                const Text(
                  'pokke',
                  style: TextStyle(
                    fontSize: 36,
                    fontWeight: FontWeight.w700,
                    color: Colors.white,
                    letterSpacing: -1,
                  ),
                ),
                const SizedBox(height: 8),
                Text(
                  isLogin ? 'Inicia sesión para continuar' : 'Crea tu cuenta',
                  style: TextStyle(color: Colors.white.withOpacity(0.55), fontSize: 15),
                ),
                const SizedBox(height: 40),

                if (!isLogin) ...[
                  _field(
                    controller: userCtrl,
                    hint: 'Usuario (3-4 caracteres)',
                    icon: Icons.alternate_email,
                    maxLength: 4,
                  ),
                  const SizedBox(height: 14),
                  _field(
                    controller: nickCtrl,
                    hint: 'Nickname (exactamente 3)',
                    icon: Icons.badge_outlined,
                    maxLength: 3,
                  ),
                  const SizedBox(height: 14),
                ],

                _field(
                  controller: emailCtrl,
                  hint: 'Correo electrónico',
                  icon: Icons.email_outlined,
                  keyboard: TextInputType.emailAddress,
                ),
                const SizedBox(height: 14),
                _field(
                  controller: passCtrl,
                  hint: 'Contraseña (mín. 8)',
                  icon: Icons.lock_outline,
                  obscure: true,
                ),

                if (errorMessage != null) ...[
                  const SizedBox(height: 16),
                  Container(
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: Colors.red.withOpacity(0.15),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Text(errorMessage!, style: const TextStyle(color: Colors.redAccent, fontSize: 13)),
                  ),
                ],

                const SizedBox(height: 28),
                SizedBox(
                  width: double.infinity,
                  height: 54,
                  child: ElevatedButton(
                    onPressed: isLoading ? null : _submit,
                    style: ElevatedButton.styleFrom(
                      backgroundColor: const Color(0xfffc3c44),
                      foregroundColor: Colors.white,
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                      elevation: 0,
                    ),
                    child: isLoading
                        ? const SizedBox(
                            width: 22,
                            height: 22,
                            child: CircularProgressIndicator(strokeWidth: 2.2, color: Colors.white),
                          )
                        : Text(
                            isLogin ? 'Iniciar sesión' : 'Crear cuenta',
                            style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600),
                          ),
                  ),
                ),
                const SizedBox(height: 20),
                TextButton(
                  onPressed: () => setState(() {
                    isLogin = !isLogin;
                    errorMessage = null;
                  }),
                  child: Text(
                    isLogin ? '¿No tienes cuenta? Regístrate' : '¿Ya tienes cuenta? Inicia sesión',
                    style: TextStyle(color: Colors.white.withOpacity(0.6)),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _field({
    required TextEditingController controller,
    required String hint,
    required IconData icon,
    bool obscure = false,
    TextInputType keyboard = TextInputType.text,
    int? maxLength,
  }) {
    return LiquidGlass(
      blur: 18,
      opacity: 0.07,
      borderRadius: BorderRadius.circular(16),
      borderWidth: 0.6,
      child: TextField(
        controller: controller,
        obscureText: obscure,
        keyboardType: keyboard,
        maxLength: maxLength,
        style: const TextStyle(color: Colors.white),
        decoration: InputDecoration(
          counterText: '',
          prefixIcon: Icon(icon, color: Colors.white54, size: 20),
          hintText: hint,
          hintStyle: TextStyle(color: Colors.white.withOpacity(0.35)),
          border: InputBorder.none,
          contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
        ),
      ),
    );
  }
}
