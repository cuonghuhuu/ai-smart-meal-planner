import 'package:smart_meal_planner/features/meal_planning/data/nutrition_target_repository.dart';
import 'package:smart_meal_planner/features/measurements/data/measurements_repository.dart';
import 'package:smart_meal_planner/features/pantry/data/pantry_repository.dart';
import 'package:smart_meal_planner/features/preferences/data/disliked_ingredients_repository.dart';
import 'package:smart_meal_planner/features/preferences/data/preferences_repository.dart';
import 'package:smart_meal_planner/features/profile/data/profile_repository.dart';
import 'package:smart_meal_planner/features/recipes/data/recipe_repository.dart';

enum MealPlanningPrerequisiteIssue {
  profile,
  measurement,
  nutritionTarget,
  recipes,
}

/// Reads the same authenticated sources that Spring uses to build a plan.
/// Spring remains authoritative and reads them again during generation.
final class MealPlanningPrerequisites {
  const MealPlanningPrerequisites({
    required this.profile,
    required this.measurements,
    required this.nutritionTarget,
    required this.preferences,
    required this.dislikedIngredients,
    required this.pantry,
    required this.recipes,
  });

  final ProfileRepository profile;
  final MeasurementsRepository measurements;
  final NutritionTargetRepository nutritionTarget;
  final PreferencesRepository preferences;
  final DislikedIngredientsRepository dislikedIngredients;
  final PantryRepository pantry;
  final RecipeRepository recipes;

  Future<MealPlanningPrerequisiteIssue?> check() async {
    var hasProfile = true;
    try {
      await profile.getProfile();
    } on ProfileNotFoundException {
      hasProfile = false;
    }
    if (!hasProfile) return MealPlanningPrerequisiteIssue.profile;

    // These reads establish that all user constraints and inventory are
    // accessible. Empty selections and an empty pantry are valid user state.
    await preferences.getSelectedDietaryPreferences();
    await preferences.getSelectedAllergens();
    await dislikedIngredients.getDislikedIngredients();
    await pantry.list();

    final recipePage = await recipes.getRecipes(size: 1);
    if (recipePage.totalElements == 0) {
      return MealPlanningPrerequisiteIssue.recipes;
    }

    if (!await nutritionTarget.hasCurrentTarget()) {
      final latest = await measurements.getLatestMeasurement();
      return latest == null
          ? MealPlanningPrerequisiteIssue.measurement
          : MealPlanningPrerequisiteIssue.nutritionTarget;
    }
    return null;
  }

  Future<void> createCalculatedTarget(DateTime effectiveFrom) =>
      nutritionTarget.createCalculatedTarget(effectiveFrom);
}
