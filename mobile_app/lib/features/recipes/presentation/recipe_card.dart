import 'package:flutter/material.dart';
import 'package:smart_meal_planner/features/recipes/data/recipe_models.dart';
import 'package:smart_meal_planner/l10n/app_strings.dart';

class RecipeCard extends StatelessWidget {
  const RecipeCard({super.key, required this.recipe, this.onSelected});

  final RecipeItem recipe;
  final ValueChanged<RecipeItem>? onSelected;

  @override
  Widget build(BuildContext context) => Card(
    clipBehavior: Clip.antiAlias,
    child: InkWell(
      onTap: onSelected == null ? null : () => onSelected!(recipe),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          SizedBox(
            height: 96,
            child: recipe.imageUrl == null || recipe.imageUrl!.isEmpty
                ? const _ImageFallback()
                : Image.network(
                    recipe.imageUrl!,
                    fit: BoxFit.cover,
                    errorBuilder: (_, _, _) => const _ImageFallback(),
                  ),
          ),
          Expanded(
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    recipe.title,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                  if (recipe.summary case final summary?) ...[
                    const SizedBox(height: 3),
                    Text(
                      summary,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                  ],
                  const SizedBox(height: 6),
                  Text(
                    '${AppStrings.recipeTotalTime}: ${recipe.totalMinutes} ${AppStrings.recipeMinutes} · '
                    '${recipe.servings} ${AppStrings.recipeServings} · ${_difficultyLabel(recipe.difficulty)}',
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                  ),
                  if (recipe.prepMinutes != null || recipe.cookMinutes != null)
                    Text(
                      [
                        if (recipe.prepMinutes != null)
                          '${AppStrings.recipePrep}: ${recipe.prepMinutes} ${AppStrings.recipeMinutes}',
                        if (recipe.cookMinutes != null)
                          '${AppStrings.recipeCook}: ${recipe.cookMinutes} ${AppStrings.recipeMinutes}',
                      ].join(' · '),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                  if (recipe.mealSlots.isNotEmpty)
                    Text(
                      recipe.mealSlots
                          .map((slot) => slot.displayName)
                          .join(' · '),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  if (recipe.tags.isNotEmpty)
                    Text(
                      recipe.tags.map((tag) => tag.displayName).join(' · '),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  const Spacer(),
                  Align(
                    alignment: Alignment.centerRight,
                    child: Text(
                      AppStrings.recipeOpen,
                      style: TextStyle(
                        color: Theme.of(context).colorScheme.primary,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    ),
  );

  String _difficultyLabel(RecipeDifficulty difficulty) => switch (difficulty) {
    RecipeDifficulty.easy => AppStrings.recipeDifficultyEasy,
    RecipeDifficulty.medium => AppStrings.recipeDifficultyMedium,
    RecipeDifficulty.hard => AppStrings.recipeDifficultyHard,
  };
}

class _ImageFallback extends StatelessWidget {
  const _ImageFallback();

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
