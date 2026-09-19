import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:smart_meal_planner/core/ui/responsive_content.dart';
import 'package:smart_meal_planner/features/auth/application/session_controller.dart';
import 'package:smart_meal_planner/features/auth/presentation/authenticated_shell.dart';
import 'package:smart_meal_planner/features/recipes/application/recipe_controller.dart';
import 'package:smart_meal_planner/features/recipes/data/recipe_models.dart';
import 'package:smart_meal_planner/features/recipes/presentation/recipe_localizations.dart';
import 'package:smart_meal_planner/l10n/app_strings.dart';

class RecipeDetailPage extends StatefulWidget {
  const RecipeDetailPage({
    super.key,
    required this.sessionController,
    required this.controller,
    required this.publicId,
  });

  final SessionController sessionController;
  final RecipeController controller;
  final String publicId;

  @override
  State<RecipeDetailPage> createState() => _RecipeDetailPageState();
}

class _RecipeDetailPageState extends State<RecipeDetailPage> {
  RecipeController get _controller => widget.controller;

  @override
  void initState() {
    super.initState();
    _controller.addListener(_onChanged);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _controller.loadDetail(widget.publicId);
    });
  }

  @override
  void didUpdateWidget(covariant RecipeDetailPage oldWidget) {
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
    selectedIndex: 2,
    content: SafeArea(
      child: ResponsiveContent(
        child: ListView(
          key: ValueKey('recipe-detail-${widget.publicId}'),
          children: [
            Align(
              alignment: Alignment.centerLeft,
              child: TextButton.icon(
                key: const ValueKey('recipe-detail-back'),
                onPressed: () => context.canPop()
                    ? context.pop()
                    : context.go('/recipes'),
                icon: const Icon(Icons.arrow_back),
                label: const Text(AppStrings.recipeBack),
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
    if (state.status == RecipeDetailStatus.loading) {
      return const [
        Padding(
          padding: EdgeInsets.all(32),
          child: Center(child: CircularProgressIndicator()),
        ),
      ];
    }
    if (state.status == RecipeDetailStatus.error) {
      return [
        _RecipeErrorPanel(
          message: state.errorMessage ?? AppStrings.recipeLoadFailed,
          onRetry: _controller.reloadDetail,
        ),
      ];
    }
    final recipe = state.item;
    if (recipe == null) {
      return const [Center(child: Text(AppStrings.recipeLoadFailed))];
    }
    return _detailChildren(context, recipe);
  }

  List<Widget> _detailChildren(BuildContext context, RecipeDetail recipe) => [
    Text(recipe.title, style: Theme.of(context).textTheme.headlineMedium),
    const SizedBox(height: 8),
    Text(
      [
        if (recipe.totalMinutes != null)
          '${recipe.totalMinutes} ${AppStrings.recipeMinutes}',
        '${recipe.servings} ${AppStrings.recipeServings}',
        RecipeLocalizations.difficultyName(recipe.difficulty),
      ].join(' · '),
    ),
    if (recipe.summary != null && recipe.summary!.isNotEmpty) ...[
      const SizedBox(height: 20),
      _section(context, AppStrings.recipeSummary, [Text(recipe.summary!)]),
    ],
    if (recipe.ingredients.isNotEmpty) ...[
      const SizedBox(height: 20),
      _section(
        context,
        AppStrings.recipeIngredients,
        [
          for (final ingredient in recipe.ingredients)
            ListTile(
              contentPadding: EdgeInsets.zero,
              title: Text(ingredient.ingredientDisplayName),
              subtitle: Text(_ingredientSubtitle(ingredient)),
            ),
        ],
      ),
    ] else
      const Padding(
        padding: EdgeInsets.only(top: 20),
        child: Text(AppStrings.recipeNoIngredients),
      ),
    if (recipe.steps.isNotEmpty) ...[
      const SizedBox(height: 20),
      _section(
        context,
        AppStrings.recipeSteps,
        [
          for (final step in recipe.steps)
            ListTile(
              contentPadding: EdgeInsets.zero,
              leading: CircleAvatar(child: Text('${step.stepNumber}')),
              title: Text(step.instruction),
              subtitle: step.durationMinutes == null
                  ? null
                  : Text('${step.durationMinutes} ${AppStrings.recipeMinutes}'),
            ),
        ],
      ),
    ] else
      const Padding(
        padding: EdgeInsets.only(top: 20),
        child: Text(AppStrings.recipeNoSteps),
      ),
    const SizedBox(height: 20),
    _nutritionSection(context, recipe.nutrition),
  ];

  Widget _section(BuildContext context, String title, List<Widget> children) =>
      Card(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(title, style: Theme.of(context).textTheme.titleMedium),
              const SizedBox(height: 8),
              ...children,
            ],
          ),
        ),
      );

  Widget _nutritionSection(
    BuildContext context,
    RecipeNutritionSnapshot? nutrition,
  ) {
    if (nutrition == null || nutrition.values.isEmpty) {
      return const Text(AppStrings.recipeNoNutrition);
    }
    return _section(
      context,
      AppStrings.recipeNutrition,
      [
        for (final value in nutrition.values)
          ListTile(
            contentPadding: EdgeInsets.zero,
            title: Text(value.nutrientDisplayName),
            trailing: Text('${_format(value.amountPerServing)} ${value.unitDisplayName}'),
          ),
        Text(
          '${(nutrition.completenessRatio * 100).toStringAsFixed(0)}% '
          '${AppStrings.recipeDataCompleteness}',
        ),
      ],
    );
  }

  String _ingredientSubtitle(RecipeIngredient ingredient) {
    final quantity = ingredient.quantity;
    final unit = ingredient.unitDisplayName ?? ingredient.unitCode;
    final amount = quantity == null || unit == null
        ? AppStrings.recipeToTaste
        : '${_format(quantity)} $unit';
    final note = ingredient.preparationNote;
    return note == null || note.isEmpty ? amount : '$amount · $note';
  }

  String _format(double value) =>
      value == value.roundToDouble() ? value.toInt().toString() : value.toString();
}

final class _RecipeErrorPanel extends StatelessWidget {
  const _RecipeErrorPanel({required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) => Column(
    children: [
      Text(message),
      const SizedBox(height: 12),
      OutlinedButton(
        key: const ValueKey('recipe-detail-retry'),
        onPressed: onRetry,
        child: const Text(AppStrings.retry),
      ),
    ],
  );
}
