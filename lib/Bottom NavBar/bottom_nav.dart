import 'package:flutter/material.dart';
import 'dart:ui';
import 'package:pokke/pages/browse_page.dart';
import 'package:pokke/pages/home_page.dart';
import 'package:pokke/pages/library_page.dart';
import 'package:pokke/pages/radio.dart';
import 'package:pokke/pages/search_page.dart';
import 'package:pokke/widgets/liquid_glass.dart';

class FirstPage extends StatefulWidget {
  FirstPage({super.key});

  @override
  State<FirstPage> createState() => _FirstPageState();
}

class _FirstPageState extends State<FirstPage> {
  int _selected_index = 0;

  void _navigateBottomBar(int index) {
    setState(() {
      _selected_index = index;
    });
  }

  final List _pages = [
    HomePage(),
    BrowsePage(),
    RadioPage(),
    LibraryPage(),
    SearchPage(),
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      extendBody: true, // Important for glass effect over content
      body: _pages[_selected_index],
      bottomNavigationBar: Padding(
        padding: const EdgeInsets.fromLTRB(12, 0, 12, 16),
        child: LiquidGlass(
          blur: 32,
          opacity: 0.10,
          borderRadius: BorderRadius.circular(28),
          borderWidth: 0.9,
          enableSpecular: true,
          elevation: 8,
          child: BottomNavigationBar(
            type: BottomNavigationBarType.fixed,
            currentIndex: _selected_index,
            onTap: _navigateBottomBar,
            items: const [
              BottomNavigationBarItem(
                icon: Icon(Icons.home_filled, size: 28),
                activeIcon: Icon(Icons.home_filled, size: 28),
                label: 'Home',
              ),
              BottomNavigationBarItem(
                icon: Icon(Icons.grid_view_rounded, size: 28),
                activeIcon: Icon(Icons.grid_view_rounded, size: 28),
                label: 'Browse',
              ),
              BottomNavigationBarItem(
                icon: Icon(Icons.sensors, size: 28),
                activeIcon: Icon(Icons.sensors, size: 28),
                label: 'Radio',
              ),
              BottomNavigationBarItem(
                icon: Icon(Icons.subscriptions_rounded, size: 28),
                activeIcon: Icon(Icons.subscriptions_rounded, size: 28),
                label: 'Library',
              ),
              BottomNavigationBarItem(
                icon: Icon(Icons.search, size: 28),
                activeIcon: Icon(Icons.search, size: 28),
                label: 'Search',
              ),
            ],
            elevation: 0,
            selectedItemColor: const Color(0xfffc3c44),
            unselectedItemColor: Colors.white70,
            showUnselectedLabels: false,
            backgroundColor: Colors.transparent,
            selectedFontSize: 12.0,
            unselectedFontSize: 12.0,
            selectedLabelStyle: const TextStyle(fontWeight: FontWeight.w600),
          ),
        ),
      ),
    );
  }
}
