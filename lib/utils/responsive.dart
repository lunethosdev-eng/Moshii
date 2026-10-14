import 'package:flutter/material.dart';

/// Adaptive layout breakpoints (phone / tablet / TV).
class R {
  static double width(BuildContext c) => MediaQuery.sizeOf(c).width;
  static bool isPhone(BuildContext c) => width(c) < 600;
  static bool isTablet(BuildContext c) => width(c) >= 600 && width(c) < 1100;
  static bool isTv(BuildContext c) => width(c) >= 1100;

  static double pagePadding(BuildContext c) {
    if (isTv(c)) return 48;
    if (isTablet(c)) return 28;
    return 16;
  }

  static int gridCols(BuildContext c) {
    if (isTv(c)) return 6;
    if (isTablet(c)) return 4;
    return 2;
  }

  static double coverSize(BuildContext c) {
    if (isTv(c)) return 360;
    if (isTablet(c)) return 280;
    return 280;
  }

  static double titleSize(BuildContext c) {
    if (isTv(c)) return 28;
    if (isTablet(c)) return 22;
    return 20;
  }
}
