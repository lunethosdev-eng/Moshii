import 'package:flutter/material.dart';
import 'package:pokke/widgets/liquid_glass.dart';

class ContextMenuItem {
  final IconData icon;
  final String label;
  final VoidCallback onTap;
  final bool destructive;
  const ContextMenuItem({
    required this.icon,
    required this.label,
    required this.onTap,
    this.destructive = false,
  });
}

Future<void> showPokkeContextMenu({
  required BuildContext context,
  required List<ContextMenuItem> items,
}) {
  return showModalBottomSheet(
    context: context,
    backgroundColor: Colors.transparent,
    builder: (ctx) {
      return Padding(
        padding: const EdgeInsets.fromLTRB(16, 0, 16, 24),
        child: LiquidGlass(
          blur: 28,
          opacity: 0.16,
          borderRadius: BorderRadius.circular(20),
          enableSpecular: true,
          child: SafeArea(
            top: false,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const SizedBox(height: 10),
                Container(
                  width: 36,
                  height: 4,
                  decoration: BoxDecoration(
                    color: Colors.white24,
                    borderRadius: BorderRadius.circular(2),
                  ),
                ),
                const SizedBox(height: 8),
                ...items.map((item) => ListTile(
                      leading: Icon(
                        item.icon,
                        color: item.destructive ? const Color(0xfffc3c44) : Colors.white70,
                      ),
                      title: Text(
                        item.label,
                        style: TextStyle(
                          color: item.destructive ? const Color(0xfffc3c44) : Colors.white,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                      onTap: () {
                        Navigator.pop(ctx);
                        item.onTap();
                      },
                    )),
                const SizedBox(height: 8),
              ],
            ),
          ),
        ),
      );
    },
  );
}
