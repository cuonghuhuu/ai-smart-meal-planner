import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:smart_meal_planner/core/ui/responsive_content.dart';
import 'package:smart_meal_planner/features/auth/application/session_controller.dart';
import 'package:smart_meal_planner/features/auth/presentation/authenticated_shell.dart';
import 'package:smart_meal_planner/features/meal_plan/application/meal_plan_controller.dart';
import 'package:smart_meal_planner/features/meal_plan/data/meal_plan_models.dart';
import 'package:smart_meal_planner/features/meal_plan/presentation/meal_plan_localizations.dart';
import 'package:smart_meal_planner/l10n/app_strings.dart';

class MealPlanDetailPage extends StatefulWidget {
  const MealPlanDetailPage({
    super.key,
    required this.sessionController,
    required this.controller,
    required this.publicId,
  });

  final SessionController sessionController;
  final MealPlanController controller;
  final String publicId;

  @override
  State<MealPlanDetailPage> createState() => _MealPlanDetailPageState();
}

class _MealPlanDetailPageState extends State<MealPlanDetailPage> {
  MealPlanController get _controller => widget.controller;

  @override
  void initState() {
    super.initState();
    _controller.addListener(_onChanged);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _controller.loadDetail(widget.publicId);
    });
  }

  @override
  void didUpdateWidget(covariant MealPlanDetailPage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.publicId != widget.publicId) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) _controller.loadDetail(widget.publicId);
      });
    }
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
    content: SafeArea(
      child: ResponsiveContent(
        child: ListView(
          key: ValueKey('meal-plan-detail-${widget.publicId}'),
          children: [
            Align(
              alignment: Alignment.centerLeft,
              child: TextButton.icon(
                onPressed: () => context.canPop()
                    ? context.pop()
                    : context.go('/meal-plans'),
                icon: const Icon(Icons.arrow_back),
                label: const Text(AppStrings.mealPlanBack),
              ),
            ),
            ..._body(context),
          ],
        ),
      ),
    ),
  );

  List<Widget> _body(BuildContext context) {
    final state = _controller.detailState;
    if (state.status == MealPlanDetailStatus.loading) {
      return const [
        Padding(
          padding: EdgeInsets.all(32),
          child: Center(child: CircularProgressIndicator()),
        ),
      ];
    }
    if (state.status == MealPlanDetailStatus.error) {
      return [
        _MealPlanErrorPanel(
          message: state.errorMessage ?? AppStrings.mealPlanLoadFailed,
          onRetry: _controller.reloadDetail,
        ),
      ];
    }
    final plan = state.item;
    if (plan == null) {
      return const [Center(child: Text(AppStrings.mealPlanLoadFailed))];
    }
    final grouped = <String, List<MealPlanEntry>>{};
    for (final entry in plan.entries) {
      grouped.putIfAbsent(entry.planDate, () => <MealPlanEntry>[]).add(entry);
    }
    return [
      Text(plan.title, style: Theme.of(context).textTheme.headlineMedium),
      const SizedBox(height: 8),
      Text(
        '${plan.startDate} → ${plan.endDate} · '
        '${MealPlanLocalizations.statusName(plan.status)}',
      ),
      if (plan.status == 'DRAFT') ...[
        const SizedBox(height: 16),
        FilledButton.icon(
          key: const ValueKey('meal-plan-accept'),
          onPressed: _controller.accepting
              ? null
              : () => _controller.accept(plan.publicId),
          icon: _controller.accepting
              ? const SizedBox.square(
                  dimension: 18,
                  child: CircularProgressIndicator(strokeWidth: 2),
                )
              : const Icon(Icons.check),
          label: const Text(AppStrings.mealPlanAccept),
        ),
      ],
      if (_controller.actionErrorMessage != null) ...[
        const SizedBox(height: 12),
        Text(
          _controller.actionErrorMessage!,
          style: TextStyle(color: Theme.of(context).colorScheme.error),
        ),
      ],
      if (grouped.isEmpty)
        const Padding(
          padding: EdgeInsets.only(top: 24),
          child: Text(AppStrings.mealPlanNoEntries),
        )
      else
        for (final group in grouped.entries) ...[
          const SizedBox(height: 20),
          Text(group.key, style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: 8),
          for (final entry in group.value) _entryCard(context, entry),
        ],
    ];
  }

  Widget _entryCard(BuildContext context, MealPlanEntry entry) => Card(
    key: ValueKey(
      'meal-plan-entry-${entry.planDate}-${entry.mealSlotCode}-${entry.position}',
    ),
    margin: const EdgeInsets.only(bottom: 8),
    child: InkWell(
      onTap: () => context.go('/recipes/${entry.recipePublicId}'),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(entry.mealSlotDisplayName),
            const SizedBox(height: 4),
            Text(entry.recipeTitle, style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 4),
            Text('${entry.servings} ${AppStrings.recipeServings} · ${MealPlanLocalizations.consumptionName(entry.consumptionStatus)}'),
            if (entry.recommendationTotalScore != null)
              Text('${AppStrings.mealPlanScore}: ${entry.recommendationTotalScore!.toStringAsFixed(2)}'),
            if (entry.recommendationExplanation != null &&
                entry.recommendationExplanation!.isNotEmpty)
              Text('${AppStrings.mealPlanReason}: ${entry.recommendationExplanation}'),
          ],
        ),
      ),
    ),
  );
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
        key: const ValueKey('meal-plan-detail-retry'),
        onPressed: onRetry,
        child: const Text(AppStrings.retry),
      ),
    ],
  );
}
