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
                if (index == 1) context.go('/catalog/ingredients');
                if (index == 2) context.go('/recipes');
                if (index == 3) context.go('/pantry');
                if (index == 4) context.go('/meal-plans');
                if (index == 5) context.go('/profile');
                if (index == 6) context.go('/preferences');
                if (index == 7) context.go('/measurements');
              },
              leading: IconButton(
                tooltip: AppStrings.signOut,
                onPressed: sessionController.logout,
                icon: const Icon(Icons.logout),
              ),
              destinations: const [
                NavigationRailDestination(
                  icon: Icon(
                    Icons.restaurant,
                    key: ValueKey('catalog-nav-foods-rail'),
                  ),
                  label: Text(AppStrings.foods),
                ),
                NavigationRailDestination(
                  icon: Icon(
                    Icons.kitchen,
                    key: ValueKey('catalog-nav-ingredients-rail'),
                  ),
                  label: Text(AppStrings.ingredients),
                ),
                NavigationRailDestination(
                  icon: Icon(
                    Icons.menu_book,
                    key: ValueKey('recipes-nav-rail'),
                  ),
                  label: Text(AppStrings.recipes),
                ),
                NavigationRailDestination(
                  icon: Icon(
                    Icons.kitchen_outlined,
                    key: ValueKey('pantry-nav-rail'),
                  ),
                  label: Text(AppStrings.pantry),
                ),
                NavigationRailDestination(
                  icon: Icon(
                    Icons.calendar_month,
                    key: ValueKey('meal-plans-nav-rail'),
                  ),
                  label: Text(AppStrings.mealPlans),
                ),
                NavigationRailDestination(
                  icon: Icon(Icons.person),
                  label: Text(AppStrings.profile),
                ),
                NavigationRailDestination(
                  icon: Icon(Icons.tune),
                  label: Text(AppStrings.preferences),
                ),
                NavigationRailDestination(
                  icon: Icon(Icons.monitor_weight),
                  label: Text(AppStrings.measurements),
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
          key: const ValueKey('catalog-nav-foods-drawer'),
          selected: selectedIndex == 0,
          leading: const Icon(Icons.restaurant),
          title: const Text(AppStrings.foods),
          onTap: () => context.go('/catalog/foods'),
        ),
        ListTile(
          key: const ValueKey('catalog-nav-ingredients-drawer'),
          selected: selectedIndex == 1,
          leading: const Icon(Icons.kitchen),
          title: const Text(AppStrings.ingredients),
          onTap: () => context.go('/catalog/ingredients'),
        ),
        ListTile(
          key: const ValueKey('recipes-nav-drawer'),
          selected: selectedIndex == 2,
          leading: const Icon(Icons.menu_book),
          title: const Text(AppStrings.recipes),
          onTap: () => context.go('/recipes'),
        ),
        ListTile(
          key: const ValueKey('pantry-nav-drawer'),
          selected: selectedIndex == 3,
          leading: const Icon(Icons.kitchen_outlined),
          title: const Text(AppStrings.pantry),
          onTap: () => context.go('/pantry'),
        ),
        ListTile(
          key: const ValueKey('meal-plans-nav-drawer'),
          selected: selectedIndex == 4,
          leading: const Icon(Icons.calendar_month),
          title: const Text(AppStrings.mealPlans),
          onTap: () => context.go('/meal-plans'),
        ),
        ListTile(
          selected: selectedIndex == 5,
          leading: const Icon(Icons.person),
          title: const Text(AppStrings.profile),
          onTap: () => context.go('/profile'),
        ),
        ListTile(
          selected: selectedIndex == 6,
          leading: const Icon(Icons.tune),
          title: const Text(AppStrings.preferences),
          onTap: () => context.go('/preferences'),
        ),
        ListTile(
          selected: selectedIndex == 7,
          leading: const Icon(Icons.monitor_weight),
          title: const Text(AppStrings.measurements),
          onTap: () => context.go('/measurements'),
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
