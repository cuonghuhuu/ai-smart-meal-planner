import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:smart_meal_planner/app/theme/app_theme.dart';
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
          appBar: AppBar(
            title: const Text(
              AppStrings.productName,
              style: TextStyle(fontWeight: FontWeight.w700),
            ),
            actions: [
              IconButton(
                tooltip: AppStrings.signOut,
                onPressed: sessionController.logout,
                icon: const Icon(Icons.logout_rounded),
              ),
            ],
          ),
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
              extended: true,
              minExtendedWidth: 216,
              labelType: NavigationRailLabelType.none,
              scrollable: true,
              onDestinationSelected: (index) {
                if (index == 0) context.go('/catalog/foods');
                if (index == 1) context.go('/catalog/ingredients');
                if (index == 2) context.go('/profile');
                if (index == 3) context.go('/preferences');
                if (index == 4) context.go('/measurements');
                if (index == 5) context.go('/meal-planning');
                if (index == 6) context.go('/pantry');
                if (index == 7) context.go('/recipes');
                if (sessionController.isAdmin && index == 8) {
                  context.go('/admin/users');
                }
              },
              leading: Padding(
                padding: const EdgeInsets.fromLTRB(16, 20, 16, 22),
                child: SizedBox(
                  width: 184,
                  child: Row(
                    children: [
                      Container(
                        width: 40,
                        height: 40,
                        decoration: BoxDecoration(
                          color: AppTheme.forest,
                          borderRadius: BorderRadius.circular(13),
                        ),
                        child: const Icon(
                          Icons.spa_rounded,
                          color: Colors.white,
                        ),
                      ),
                      const SizedBox(width: 10),
                      const Flexible(
                        child: Text(
                          AppStrings.productName,
                          maxLines: 2,
                          style: TextStyle(
                            fontWeight: FontWeight.w800,
                            color: AppTheme.ink,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
              trailing: Padding(
                padding: const EdgeInsets.all(16),
                child: Tooltip(
                  message: AppStrings.signOut,
                  child: TextButton.icon(
                    onPressed: sessionController.logout,
                    icon: const Icon(Icons.logout_rounded),
                    label: const Text(AppStrings.signOut),
                  ),
                ),
              ),
              destinations: [
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
                NavigationRailDestination(
                  icon: Icon(Icons.calendar_month),
                  label: Text(AppStrings.mealPlanning),
                ),
                NavigationRailDestination(
                  icon: Icon(
                    Icons.inventory_2,
                    key: ValueKey('pantry-nav-rail'),
                  ),
                  label: Text(AppStrings.pantry),
                ),
                const NavigationRailDestination(
                  icon: Icon(
                    Icons.menu_book,
                    key: ValueKey('recipes-nav-rail'),
                  ),
                  label: Text(AppStrings.recipes),
                ),
                if (sessionController.isAdmin)
                  const NavigationRailDestination(
                    icon: Icon(
                      Icons.manage_accounts,
                      key: ValueKey('admin-nav-users-rail'),
                    ),
                    label: Text(AppStrings.adminUsers),
                  ),
              ],
            ),
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
        DrawerHeader(
          decoration: const BoxDecoration(color: AppTheme.mint),
          child: Align(
            alignment: Alignment.bottomLeft,
            child: Text(
              AppStrings.productName,
              style: Theme.of(context).textTheme.headlineMedium,
            ),
          ),
        ),
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
        ListTile(
          selected: selectedIndex == 4,
          leading: const Icon(Icons.monitor_weight),
          title: const Text(AppStrings.measurements),
          onTap: () => context.go('/measurements'),
        ),
        ListTile(
          key: const ValueKey('meal-planning-nav-drawer'),
          selected: selectedIndex == 5,
          leading: const Icon(Icons.calendar_month),
          title: const Text(AppStrings.mealPlanning),
          onTap: () => context.go('/meal-planning'),
        ),
        ListTile(
          key: const ValueKey('pantry-nav-drawer'),
          selected: selectedIndex == 6,
          leading: const Icon(Icons.inventory_2),
          title: const Text(AppStrings.pantry),
          onTap: () => context.go('/pantry'),
        ),
        ListTile(
          key: const ValueKey('recipes-nav-drawer'),
          selected: selectedIndex == 7,
          leading: const Icon(Icons.menu_book),
          title: const Text(AppStrings.recipes),
          onTap: () => context.go('/recipes'),
        ),
        if (sessionController.isAdmin) ...[
          const Divider(),
          ListTile(
            key: const ValueKey('admin-nav-users-drawer'),
            selected: selectedIndex == 8,
            leading: const Icon(Icons.manage_accounts),
            title: const Text(AppStrings.adminUsers),
            onTap: () => context.go('/admin/users'),
          ),
        ],
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
