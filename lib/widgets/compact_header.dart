
import 'package:flutter/material.dart';
import 'package:pokke/widgets/liquid_glass.dart';
import 'package:pokke/services/supabase_service.dart';

class CompactHeader extends StatelessWidget implements PreferredSizeWidget {
  final String title;
  final double height;

  const CompactHeader({super.key, required this.title, this.height = 56});

  @override
  Size get preferredSize => Size.fromHeight(height + 8);

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      bottom: false,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(12, 4, 12, 0),
        child: LiquidGlass(
          blur: 22,
          opacity: 0.08,
          borderRadius: BorderRadius.circular(18),
          borderWidth: 0.55,
          enableSpecular: true,
          child: SizedBox(
            height: height,
            child: Row(
              children: [
                const SizedBox(width: 14),
                Text(
                  title,
                  style: const TextStyle(
                    fontWeight: FontWeight.w700,
                    fontSize: 22,
                    color: Colors.white,
                    letterSpacing: -0.4,
                  ),
                ),
                const Spacer(),
                // User avatar (or logo fallback)
                FutureBuilder<Map<String, dynamic>?>(
                  future: SupabaseService.getProfile(),
                  builder: (context, snap) {
                    final avatar = snap.data?['avatar_url'] as String?;
                    return Padding(
                      padding: const EdgeInsets.only(right: 10),
                      child: CircleAvatar(
                        radius: 16,
                        backgroundColor: Colors.white12,
                        backgroundImage: avatar != null && avatar.isNotEmpty
                            ? NetworkImage(avatar)
                            : null,
                        child: avatar == null || avatar.isEmpty
                            ? Image.asset('assets/logo/pokke_logo_transparent.png', height: 18)
                            : null,
                      ),
                    );
                  },
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
