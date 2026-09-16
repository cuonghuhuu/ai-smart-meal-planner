import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:smart_meal_planner/features/auth/application/session_controller.dart';

class AuthenticatedShell extends StatelessWidget {
  const AuthenticatedShell({super.key, required this.sessionController});
  final SessionController sessionController;

  @override
  Widget build(BuildContext context) => LayoutBuilder(
    builder: (context, constraints) {
      final wide = constraints.maxWidth >= 800;
      final content = const _FoodsPlaceholder();
      if (!wide) {
        return Scaffold(
          appBar: AppBar(title: const Text('AI Smart Meal Planner')),
          drawer: _NavigationDrawer(sessionController: sessionController),
          body: content,
        );
      }
      return Scaffold(
        body: Row(
          children: [
            NavigationRail(
              selectedIndex: 0,
              labelType: NavigationRailLabelType.all,
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
            const Expanded(child: _FoodsPlaceholder()),
          ],
        ),
      );
    },
  );
}

class _NavigationDrawer extends StatelessWidget {
  const _NavigationDrawer({required this.sessionController});
  final SessionController sessionController;
  @override
  Widget build(BuildContext context) => Drawer(
    child: ListView(
      children: [
        const DrawerHeader(child: Text('Smart Meal Planner')),
        ListTile(
          selected: true,
          leading: const Icon(Icons.restaurant),
          title: const Text('Foods'),
          onTap: () => context.go('/catalog/foods'),
        ),
        const ListTile(
          leading: Icon(Icons.kitchen),
          title: Text('Ingredients (coming soon)'),
        ),
        const ListTile(
          leading: Icon(Icons.person),
          title: Text('Profile (coming soon)'),
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
