import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:smart_meal_planner/core/ui/responsive_content.dart';
import 'package:smart_meal_planner/features/auth/application/session_controller.dart';
import 'package:smart_meal_planner/features/auth/presentation/authenticated_shell.dart';
import 'package:smart_meal_planner/features/meal_plan/application/meal_plan_controller.dart';
import 'package:smart_meal_planner/features/meal_plan/presentation/meal_plan_localizations.dart';
import 'package:smart_meal_planner/l10n/app_strings.dart';

class MealPlanPage extends StatefulWidget {
  const MealPlanPage({
    super.key,
    required this.sessionController,
    required this.controller,
  });

  final SessionController sessionController;
  final MealPlanController controller;

  @override
  State<MealPlanPage> createState() => _MealPlanPageState();
}

class _MealPlanPageState extends State<MealPlanPage> {
  MealPlanController get _controller => widget.controller;

  @override
  void initState() {
    super.initState();
    _controller.addListener(_onChanged);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _controller.loadInitial();
    });
  }

  @override
  void dispose() {
    _controller.removeListener(_onChanged);
    super.dispose();
  }

  void _onChanged() {
    if (mounted) setState(() {});
  }

  @override
  Widget build(BuildContext context) => AuthenticatedShell(
    sessionController: widget.sessionController,
    selectedIndex: 4,
    content: _content(context),
  );

  Widget _content(BuildContext context) {
    final state = _controller.state;
    return SafeArea(
      child: ResponsiveContent(
        child: ListView(
          key: const ValueKey('meal-plan-list'),
          children: [
            Text(
              AppStrings.mealPlans,
              style: Theme.of(context).textTheme.headlineMedium,
            ),
            const SizedBox(height: 8),
            const Text(AppStrings.mealPlansSubtitle),
            const SizedBox(height: 20),
            FilledButton.icon(
              key: const ValueKey('meal-plan-generate'),
              onPressed: _controller.generating ? null : _generate,
              icon: _controller.generating
                  ? const SizedBox.square(
                      dimension: 18,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Icon(Icons.auto_awesome),
              label: const Text(AppStrings.generateMealPlan),
            ),
            if (_controller.actionErrorMessage != null) ...[
              const SizedBox(height: 12),
              Text(
                _controller.actionErrorMessage!,
                style: TextStyle(color: Theme.of(context).colorScheme.error),
              ),
            ],
            const SizedBox(height: 20),
            ..._body(context, state),
          ],
        ),
      ),
    );
  }

  List<Widget> _body(BuildContext context, MealPlanListState state) {
    if (state.isLoading && state.items.isEmpty) {
      return const [
        Padding(
          padding: EdgeInsets.all(32),
          child: Center(child: CircularProgressIndicator()),
        ),
      ];
    }
    if (state.status == MealPlanListStatus.error && state.items.isEmpty) {
      return [
        _MealPlanErrorPanel(
          message: state.errorMessage ?? AppStrings.mealPlanLoadFailed,
          onRetry: _controller.reload,
        ),
      ];
    }
    if (state.items.isEmpty) {
      return const [
        Padding(
          padding: EdgeInsets.symmetric(vertical: 32),
          child: Center(child: Text(AppStrings.mealPlanEmpty)),
        ),
      ];
    }
    return [
      for (final plan in state.items)
        Card(
          key: ValueKey('meal-plan-item-${plan.publicId}'),
          margin: const EdgeInsets.only(bottom: 12),
          child: ListTile(
            title: Text(plan.title),
            subtitle: Text(
              '${plan.startDate} → ${plan.endDate} · '
              '${MealPlanLocalizations.statusName(plan.status)}',
            ),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => context.go('/meal-plans/${plan.publicId}'),
          ),
        ),
    ];
  }

  Future<void> _generate() async {
    final plan = await _controller.generateDefault();
    if (!mounted || plan == null) return;
    context.go('/meal-plans/${plan.publicId}');
  }
}

final class _MealPlanErrorPanel extends StatelessWidget {
  const _MealPlanErrorPanel({required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) => Column(
    children: [
      Text(message),
      const SizedBox(height: 12),
      OutlinedButton(
        key: const ValueKey('meal-plan-retry'),
        onPressed: onRetry,
        child: const Text(AppStrings.retry),
      ),
    ],
  );
}
