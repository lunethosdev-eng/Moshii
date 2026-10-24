import 'package:flutter/material.dart';
import 'package:pokke/widgets/liquid_glass.dart';
import 'package:pokke/services/supabase_service.dart';
import 'package:pokke/services/notification_service.dart';
import 'package:pokke/services/settings_service.dart';

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
  Size get preferredSize =>
      Size.fromHeight((collapsed ? collapsedHeight : expandedHeight) + 8);

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
            height: h,
            padding: const EdgeInsets.symmetric(horizontal: 4),
            child: Row(
              children: [
                const SizedBox(width: 10),
                Expanded(
                  child: Text(
                    title,
                    style: TextStyle(
                      color: Colors.white,
                      fontWeight: FontWeight.w800,
                      fontSize: collapsed ? 16 : 20,
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
                if (!collapsed) ...[
                  // Notifications in header
                  AnimatedBuilder(
                    animation: NotificationService.instance,
                    builder: (context, _) {
                      final u = NotificationService.instance.unread;
                      return IconButton(
                        padding: EdgeInsets.zero,
                        constraints:
                            const BoxConstraints(minWidth: 36, minHeight: 36),
                        tooltip: 'Notifications',
                        onPressed: () =>
                            Navigator.pushNamed(context, '/notifications'),
                        icon: Badge(
                          isLabelVisible: u > 0,
                          label: Text('$u',
                              style: const TextStyle(fontSize: 10)),
                          backgroundColor: const Color(0xfffc3c44),
                          child: Icon(Icons.notifications_outlined,
                              size: 22,
                              color: Colors.white.withOpacity(0.75)),
                        ),
                      );
                    },
                  ),
                  // Settings gear
                  IconButton(
                    padding: EdgeInsets.zero,
                    constraints:
                        const BoxConstraints(minWidth: 36, minHeight: 36),
                    icon: Icon(Icons.settings_outlined,
                        size: 22, color: Colors.white.withOpacity(0.75)),
                    onPressed: onSettings ??
                        () => Navigator.pushNamed(context, '/settings'),
                  ),
                ],
                // Avatar
                AnimatedOpacity(
                  opacity: collapsed ? 0 : 1,
                  duration: const Duration(milliseconds: 200),
                  child: AnimatedScale(
                    scale: collapsed ? 0.6 : 1,
                    duration: const Duration(milliseconds: 250),
                    child: GestureDetector(
                      onTap: () =>
                          Navigator.pushNamed(context, '/profile_edit'),
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
                            final avatar =
                                snap.data?['avatar_url'] as String?;
                            final effect =
                                SettingsService.instance.profileEffect;
                            Widget av = CircleAvatar(
                              radius: 16,
                              backgroundColor: Colors.white12,
                              backgroundImage: avatar != null &&
                                      avatar.isNotEmpty
                                  ? NetworkImage(avatar)
                                  : null,
                              child: avatar == null || avatar.isEmpty
                                  ? const Icon(Icons.person,
                                      size: 16, color: Colors.white54)
                                  : null,
                            );
                            if (effect == 'glow') {
                              av = Container(
                                decoration: BoxDecoration(
                                  shape: BoxShape.circle,
                                  boxShadow: [
                                    BoxShadow(
                                      color: const Color(0xfffc3c44)
                                          .withOpacity(0.45),
                                      blurRadius: 10,
                                      spreadRadius: 1,
                                    ),
                                  ],
                                ),
                                child: av,
                              );
                            } else if (effect == 'glass') {
                              av = Container(
                                decoration: BoxDecoration(
                                  shape: BoxShape.circle,
                                  border: Border.all(
                                      color: Colors.white.withOpacity(0.35)),
                                ),
                                child: av,
                              );
                            }
                            return av;
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
