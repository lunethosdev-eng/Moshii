import 'package:flutter/material.dart';
import 'package:pokke/widgets/liquid_glass.dart';

/// Shows a Liquid Glass modal bottom sheet (no plain dark box).
Future<T?> showLiquidSheet<T>({
  required BuildContext context,
  required Widget child,
  double maxHeightFactor = 0.55,
}) {
  return showModalBottomSheet<T>(
    context: context,
    backgroundColor: Colors.transparent,
    barrierColor: Colors.black54,
    isScrollControlled: true,
    builder: (ctx) {
      return Padding(
        padding: EdgeInsets.only(
          left: 12,
          right: 12,
          bottom: MediaQuery.of(ctx).viewInsets.bottom + 16,
        ),
        child: LiquidGlass(
          blur: 36,
          opacity: 0.16,
          borderRadius: BorderRadius.circular(22),
          borderWidth: 0.8,
          enableSpecular: true,
          elevation: 16,
          child: ConstrainedBox(
            constraints: BoxConstraints(
              maxHeight: MediaQuery.of(ctx).size.height * maxHeightFactor,
            ),
            child: Material(
              color: Colors.transparent,
              child: child,
            ),
          ),
        ),
      );
    },
  );
}
