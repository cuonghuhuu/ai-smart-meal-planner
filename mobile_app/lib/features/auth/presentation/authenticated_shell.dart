import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:smart_meal_planner/features/auth/application/session_controller.dart';
import 'package:smart_meal_planner/l10n/app_strings.dart';

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
          appBar: AppBar(title: const Text(AppStrings.productName)),
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
                if (index == 3) context.go('/preferences');
              },
              leading: IconButton(
                tooltip: AppStrings.signOut,
                onPressed: sessionController.logout,
                icon: const Icon(Icons.logout),
              ),
              destinations: const [
                NavigationRailDestination(
                  icon: Icon(Icons.restaurant),
                  label: Text(AppStrings.foods),
                ),
                NavigationRailDestination(
                  icon: Icon(Icons.kitchen),
                  label: Text(AppStrings.ingredients),
                ),
                NavigationRailDestination(
                  icon: Icon(Icons.person),
                  label: Text(AppStrings.profile),
                ),
                NavigationRailDestination(
                  icon: Icon(Icons.tune),
                  label: Text(AppStrings.preferences),
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
        const DrawerHeader(child: Text(AppStrings.productName)),
        ListTile(
          selected: selectedIndex == 0,
          leading: const Icon(Icons.restaurant),
          title: const Text(AppStrings.foods),
          onTap: () => context.go('/catalog/foods'),
        ),
        const ListTile(
          leading: Icon(Icons.kitchen),
          title: Text(AppStrings.ingredientsComingSoon),
        ),
        ListTile(
          selected: selectedIndex == 2,
          leading: const Icon(Icons.person),
          title: const Text(AppStrings.profile),
          onTap: () => context.go('/profile'),
        ),
        ListTile(
          selected: selectedIndex == 3,
          leading: const Icon(Icons.tune),
          title: const Text(AppStrings.preferences),
          onTap: () => context.go('/preferences'),
        ),
        const Divider(),
        ListTile(
          leading: const Icon(Icons.logout),
          title: const Text(AppStrings.signOut),
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
        child: Text(AppStrings.foodsPlaceholder, textAlign: TextAlign.center),
      ),
    ),
  );
}
