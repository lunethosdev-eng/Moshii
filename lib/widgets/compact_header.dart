import 'package:flutter/material.dart';
import 'package:pokke/widgets/liquid_glass.dart';
import 'package:pokke/services/supabase_service.dart';

class CompactHeader extends StatelessWidget implements PreferredSizeWidget {
  final String title;
  final bool collapsed;
  final double expandedHeight;
  final double collapsedHeight;
  final VoidCallback? onSettings;

  const CompactHeader({
    super.key,
    required this.title,
    this.collapsed = false,
    this.expandedHeight = 56,
    this.collapsedHeight = 40,
    this.onSettings,
  });

  @override
  Size get preferredSize => Size.fromHeight((collapsed ? collapsedHeight : expandedHeight) + 8);

  @override
  Widget build(BuildContext context) {
    final h = collapsed ? collapsedHeight : expandedHeight;

    return SafeArea(
      bottom: false,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 280),
        curve: Curves.easeOutCubic,
        padding: EdgeInsets.fromLTRB(12, collapsed ? 2 : 4, 12, 0),
        child: LiquidGlass(
          blur: collapsed ? 18 : 22,
          opacity: collapsed ? 0.06 : 0.09,
          borderRadius: BorderRadius.circular(collapsed ? 14 : 18),
          borderWidth: collapsed ? 0.4 : 0.55,
          enableSpecular: !collapsed,
          child: AnimatedContainer(
            duration: const Duration(milliseconds: 280),
            curve: Curves.easeOutCubic,
            height: h,
            child: Row(
              children: [
                SizedBox(width: collapsed ? 12 : 14),
                Expanded(
                  child: AnimatedDefaultTextStyle(
                    duration: const Duration(milliseconds: 250),
                    style: TextStyle(
                      fontWeight: FontWeight.w700,
                      fontSize: collapsed ? 17 : 22,
                      color: Colors.white,
                      letterSpacing: -0.4,
                    ),
                    child: Text(title, maxLines: 1, overflow: TextOverflow.ellipsis),
                  ),
                ),
                // Settings gear
                if (!collapsed)
                  IconButton(
                    padding: EdgeInsets.zero,
                    constraints: const BoxConstraints(minWidth: 36, minHeight: 36),
                    icon: Icon(Icons.settings_outlined, size: 22, color: Colors.white.withOpacity(0.75)),
                    onPressed: onSettings ?? () => Navigator.pushNamed(context, '/settings'),
                  ),
                // Avatar
                AnimatedOpacity(
                  opacity: collapsed ? 0 : 1,
                  duration: const Duration(milliseconds: 200),
                  child: AnimatedScale(
                    scale: collapsed ? 0.6 : 1,
                    duration: const Duration(milliseconds: 250),
                    child: GestureDetector(
                      onTap: () => Navigator.pushNamed(context, '/profile_edit'),
                      onLongPress: () {
                        if (SupabaseService.isOwner) {
                          Navigator.pushNamed(context, '/admin');
                        } else {
                          Navigator.pushNamed(context, '/settings');
                        }
                      },
                      child: Padding(
                        padding: const EdgeInsets.only(right: 10),
                        child: FutureBuilder<Map<String, dynamic>?>(
                          future: SupabaseService.getProfile(),
                          builder: (context, snap) {
                            final avatar = snap.data?['avatar_url'] as String?;
                            return CircleAvatar(
                              radius: 16,
                              backgroundColor: Colors.white12,
                              backgroundImage: avatar != null && avatar.isNotEmpty
                                  ? NetworkImage(avatar)
                                  : null,
                              child: avatar == null || avatar.isEmpty
                                  ? const Icon(Icons.person, size: 16, color: Colors.white54)
                                  : null,
                            );
                          },
                        ),
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
