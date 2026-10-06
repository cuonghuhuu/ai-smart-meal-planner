import 'package:flutter/material.dart';
import 'package:smart_meal_planner/core/ui/responsive_content.dart';
import 'package:smart_meal_planner/features/recipes/application/recipe_controller.dart';
import 'package:smart_meal_planner/features/recipes/application/recipe_state.dart';
import 'package:smart_meal_planner/features/recipes/data/recipe_models.dart';
import 'package:smart_meal_planner/l10n/app_strings.dart';

/// Standalone detail view. The caller owns navigation and the controller.
class RecipeDetailPage extends StatefulWidget {
  const RecipeDetailPage({
    super.key,
    required this.controller,
    required this.publicId,
  });

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
    _controller.addListener(_onControllerChanged);
    _scheduleLoad();
  }

  @override
  void didUpdateWidget(covariant RecipeDetailPage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller != widget.controller) {
      oldWidget.controller.removeListener(_onControllerChanged);
      _controller.addListener(_onControllerChanged);
    }
    if (oldWidget.controller != widget.controller ||
        oldWidget.publicId != widget.publicId) {
      _scheduleLoad();
    }
  }

  void _scheduleLoad() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      final state = _controller.detailState;
      if (state.publicId == widget.publicId &&
          state.status != RecipeDetailStatus.initial) {
        return;
      }
      _controller.loadDetail(widget.publicId);
    });
  }

  void _onControllerChanged() {
    if (mounted) setState(() {});
  }

  @override
  void dispose() {
    _controller.removeListener(_onControllerChanged);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final state = _controller.detailState;
    final matchesCurrentId = state.publicId == widget.publicId;
    return Scaffold(
      body: SafeArea(
        child: SingleChildScrollView(
          key: ValueKey('recipe-detail-scroll-${widget.publicId}'),
          child: ResponsiveContent(
            child: LayoutBuilder(
              builder: (context, constraints) => Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  if (!matchesCurrentId ||
                      state.status == RecipeDetailStatus.initial ||
                      state.status == RecipeDetailStatus.loading)
                    const Padding(
                      padding: EdgeInsets.all(32),
                      child: Center(
                        child: CircularProgressIndicator(
                          key: ValueKey('recipe-detail-loading'),
                        ),
                      ),
                    )
                  else if (state.status == RecipeDetailStatus.error)
                    _errorPanel(state.errorMessage)
                  else if (state.recipe case final recipe?)
                    _detailContent(
                      context,
                      recipe,
                      wide: constraints.maxWidth >= 700,
                    ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _errorPanel(String? message) => Card(
    child: Padding(
      padding: const EdgeInsets.all(20),
      child: Column(
        children: [
          const Icon(Icons.cloud_off_outlined, size: 32),
          const SizedBox(height: 8),
          Text(
            message ?? AppStrings.recipeDetailLoadFailed,
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: 12),
          OutlinedButton(
            key: const ValueKey('recipe-detail-retry'),
            onPressed: () => _controller.loadDetail(widget.publicId),
            child: const Text(AppStrings.retry),
          ),
        ],
      ),
    ),
  );

  Widget _detailContent(
    BuildContext context,
    RecipeDetail recipe, {
    required bool wide,
  }) {
    final ingredients = _ingredients(context, recipe.ingredients);
    final steps = _steps(context, recipe);
    final nutrition = _nutrition(context, recipe.nutrition);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _header(context, recipe),
        const SizedBox(height: 16),
        if (wide)
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(child: ingredients),
              const SizedBox(width: 16),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [steps, const SizedBox(height: 16), nutrition],
                ),
              ),
            ],
          )
        else ...[
          ingredients,
          const SizedBox(height: 16),
          steps,
          const SizedBox(height: 16),
          nutrition,
        ],
      ],
    );
  }

  Widget _header(BuildContext context, RecipeDetail recipe) => Card(
    clipBehavior: Clip.antiAlias,
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        AspectRatio(
          aspectRatio: 16 / 7,
          child: recipe.imageUrl == null || recipe.imageUrl!.isEmpty
              ? const _RecipeImageFallback()
              : Image.network(
                  recipe.imageUrl!,
                  fit: BoxFit.cover,
                  semanticLabel: recipe.title,
                  errorBuilder: (_, _, _) => const _RecipeImageFallback(),
                ),
        ),
        Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                recipe.title,
                key: const ValueKey('recipe-detail-title'),
                style: Theme.of(context).textTheme.headlineMedium,
              ),
              if (recipe.summary case final summary?) ...[
                const SizedBox(height: 8),
                Text(summary),
              ],
              const SizedBox(height: 12),
              Wrap(
                spacing: 8,
                runSpacing: 4,
                children: [
                  Chip(
                    label: Text(
                      '${AppStrings.recipeServingsLabel}: ${recipe.servings}',
                    ),
                  ),
                  Chip(
                    label: Text(
                      '${AppStrings.recipeDifficulty}: ${_difficulty(recipe.difficulty)}',
                    ),
                  ),
                  if (recipe.prepMinutes case final minutes?)
                    Chip(
                      label: Text(
                        '${AppStrings.recipePrep}: $minutes ${AppStrings.recipeMinutes}',
                      ),
                    ),
                  if (recipe.cookMinutes case final minutes?)
                    Chip(
                      label: Text(
                        '${AppStrings.recipeCook}: $minutes ${AppStrings.recipeMinutes}',
                      ),
                    ),
                  Chip(
                    label: Text(
                      '${AppStrings.recipeTotalTime}: ${recipe.totalMinutes} ${AppStrings.recipeMinutes}',
                    ),
                  ),
                ],
              ),
              if (recipe.mealSlots.isNotEmpty) ...[
                const SizedBox(height: 12),
                Text(
                  AppStrings.recipeMealSlot,
                  style: Theme.of(context).textTheme.titleSmall,
                ),
                Wrap(
                  spacing: 8,
                  children: [
                    for (final slot in recipe.mealSlots)
                      Chip(label: Text(slot.displayName)),
                  ],
                ),
              ],
              if (recipe.tags.isNotEmpty) ...[
                const SizedBox(height: 8),
                Text(
                  AppStrings.recipeTag,
                  style: Theme.of(context).textTheme.titleSmall,
                ),
                Wrap(
                  spacing: 8,
                  children: [
                    for (final tag in recipe.tags)
                      Chip(label: Text(tag.displayName)),
                  ],
                ),
              ],
            ],
          ),
        ),
      ],
    ),
  );

  Widget _ingredients(
    BuildContext context,
    List<RecipeIngredient> ingredients,
  ) => _sectionCard(
    context,
    AppStrings.recipeIngredients,
    ingredients.isEmpty
        ? [const Text(AppStrings.recipeNoIngredients)]
        : [
            for (var index = 0; index < ingredients.length; index++) ...[
              if (ingredients[index].sectionLabel case final section?)
                if (section.isNotEmpty &&
                    (index == 0 ||
                        section != ingredients[index - 1].sectionLabel))
                  Padding(
                    padding: const EdgeInsets.only(top: 12, bottom: 4),
                    child: Text(
                      section,
                      style: Theme.of(context).textTheme.titleSmall,
                    ),
                  ),
              _ingredientRow(context, ingredients[index]),
            ],
          ],
  );

  Widget _ingredientRow(BuildContext context, RecipeIngredient ingredient) {
    final unit = ingredient.unitDisplayName ?? ingredient.unitCode;
    final amount = [
      if (ingredient.quantity != null) '${ingredient.quantity}',
      if (unit != null && unit.isNotEmpty) unit,
    ].join(' ');
    return Padding(
      key: ValueKey('recipe-ingredient-${ingredient.lineNumber}'),
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('${ingredient.lineNumber}.'),
          const SizedBox(width: 8),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(ingredient.ingredientDisplayName),
                if (amount.isNotEmpty) Text(amount),
                if (ingredient.preparationNote case final note?)
                  Text('${AppStrings.recipePreparationNote}: $note'),
                if (ingredient.optional) const Text(AppStrings.recipeOptional),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _steps(
    BuildContext context,
    RecipeDetail recipe,
  ) => _sectionCard(context, AppStrings.recipeSteps, [
    if (recipe.instructionsNote case final note?) ...[
      Text('${AppStrings.recipeInstructionsNote}: $note'),
      const SizedBox(height: 8),
    ],
    if (recipe.steps.isEmpty)
      const Text(AppStrings.recipeNoSteps)
    else
      for (final step in recipe.steps)
        Padding(
          key: ValueKey('recipe-step-${step.stepNumber}'),
          padding: const EdgeInsets.symmetric(vertical: 6),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('${step.stepNumber}.'),
              const SizedBox(width: 8),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(step.instruction),
                    if (step.durationMinutes case final minutes?)
                      Text(
                        '${AppStrings.recipeStepDuration}: $minutes ${AppStrings.recipeMinutes}',
                      ),
                  ],
                ),
              ),
            ],
          ),
        ),
  ]);

  Widget _nutrition(
    BuildContext context,
    RecipeNutritionSnapshot? nutrition,
  ) => _sectionCard(
    context,
    AppStrings.recipeNutrition,
    nutrition == null
        ? [const Text(AppStrings.recipeNutritionUnavailable)]
        : [
            const Text(AppStrings.recipeNutritionPerServing),
            const SizedBox(height: 8),
            if (nutrition.values.isEmpty)
              const Text(AppStrings.recipeNutritionNoValues)
            else
              for (final value in nutrition.values)
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 4),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(value.nutrientDisplayName),
                      Text(
                        '${value.amountPerServing} ${value.unitDisplayName}',
                      ),
                    ],
                  ),
                ),
            const SizedBox(height: 8),
            Text(
              '${AppStrings.recipeNutritionCompleteness}: ${nutrition.completenessRatio}',
            ),
            Text(
              '${AppStrings.recipeNutritionComputedAt}: ${nutrition.computedAt}',
            ),
            if (nutrition.computationNote case final note?)
              Text('${AppStrings.recipeNutritionNote}: $note'),
          ],
  );

  Widget _sectionCard(
    BuildContext context,
    String title,
    List<Widget> children,
  ) => Card(
    child: Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(title, style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: 8),
          ...children,
        ],
      ),
    ),
  );

  String _difficulty(RecipeDifficulty difficulty) => switch (difficulty) {
    RecipeDifficulty.easy => AppStrings.recipeDifficultyEasy,
    RecipeDifficulty.medium => AppStrings.recipeDifficultyMedium,
    RecipeDifficulty.hard => AppStrings.recipeDifficultyHard,
  };
}

class _RecipeImageFallback extends StatelessWidget {
  const _RecipeImageFallback();

  @override
  Widget build(BuildContext context) => ColoredBox(
    color: Theme.of(context).colorScheme.surfaceContainerHighest,
    child: const Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.restaurant_menu),
          Text(AppStrings.recipeImageUnavailable),
        ],
      ),
    ),
  );
}
