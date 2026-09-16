import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:smart_meal_planner/features/auth/application/session_controller.dart';

class AuthenticatedShell extends StatelessWidget {
  const AuthenticatedShell({
    super.key,
    required this.sessionController,
    this.content,
    this.selectedIndex = 0,
  });
  final SessionController sessionController;
  final Widget? content;
  final int selectedIndex;

  @override
  Widget build(BuildContext context) => LayoutBuilder(
    builder: (context, constraints) {
      final wide = constraints.maxWidth >= 800;
      final pageContent = content ?? const _FoodsPlaceholder();
      if (!wide) {
        return Scaffold(
          appBar: AppBar(title: const Text('AI Smart Meal Planner')),
          drawer: _NavigationDrawer(
            sessionController: sessionController,
            selectedIndex: selectedIndex,
          ),
          body: pageContent,
        );
      }
      return Scaffold(
        body: Row(
          children: [
            NavigationRail(
              selectedIndex: selectedIndex,
              labelType: NavigationRailLabelType.all,
              onDestinationSelected: (index) {
                if (index == 0) context.go('/catalog/foods');
                if (index == 2) context.go('/profile');
              },
              leading: IconButton(
                tooltip: 'Sign out',
                onPressed: sessionController.logout,
                icon: const Icon(Icons.logout),
              ),
              destinations: const [
                NavigationRailDestination(
                  icon: Icon(Icons.restaurant),
                  label: Text('Foods'),
                ),
                NavigationRailDestination(
                  icon: Icon(Icons.kitchen),
                  label: Text('Ingredients'),
                ),
                NavigationRailDestination(
                  icon: Icon(Icons.person),
                  label: Text('Profile'),
                ),
              ],
            ),
            const VerticalDivider(width: 1),
            Expanded(child: pageContent),
          ],
        ),
      );
    },
  );
}

class _NavigationDrawer extends StatelessWidget {
  const _NavigationDrawer({
    required this.sessionController,
    required this.selectedIndex,
  });
  final SessionController sessionController;
  final int selectedIndex;
  @override
  Widget build(BuildContext context) => Drawer(
    child: ListView(
      children: [
        const DrawerHeader(child: Text('Smart Meal Planner')),
        ListTile(
          selected: selectedIndex == 0,
          leading: const Icon(Icons.restaurant),
          title: const Text('Foods'),
          onTap: () => context.go('/catalog/foods'),
        ),
        const ListTile(
          leading: Icon(Icons.kitchen),
          title: Text('Ingredients (coming soon)'),
        ),
        ListTile(
          selected: selectedIndex == 2,
          leading: const Icon(Icons.person),
          title: const Text('Profile'),
          onTap: () => context.go('/profile'),
        ),
        const Divider(),
        ListTile(
          leading: const Icon(Icons.logout),
          title: const Text('Sign out'),
          onTap: sessionController.logout,
        ),
      ],
    ),
  );
}

class _FoodsPlaceholder extends StatelessWidget {
  const _FoodsPlaceholder();
  @override
  Widget build(BuildContext context) => Center(
    child: ConstrainedBox(
      constraints: const BoxConstraints(maxWidth: 640),
      child: const Padding(
        padding: EdgeInsets.all(24),
        child: Text(
          'Food Catalog is coming in P8.7.',
          textAlign: TextAlign.center,
        ),
      ),
    ),
  );
}
