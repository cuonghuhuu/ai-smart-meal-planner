import 'package:smart_meal_planner/features/preferences/data/disliked_ingredient_models.dart';
import 'package:smart_meal_planner/l10n/app_strings.dart';

final class DislikedIngredientsValidation {
  const DislikedIngredientsValidation._();

  static String? validateNote(String? value) {
    if (value == null || value.length <= 255) {
      return null;
    }
    return AppStrings.dislikedIngredientNoteTooLong;
  }

  static String? validateSelections(
    Iterable<DislikedIngredientPreference> selections,
  ) {
    final seen = <String>{};
    for (final selection in selections) {
      final publicId = selection.ingredientPublicId.trim();
      if (publicId.isEmpty) {
        return AppStrings.dislikedIngredientIdBlank;
      }
      if (!seen.add(publicId)) {
        return AppStrings.dislikedIngredientDuplicate;
      }
      final noteError = validateNote(selection.note);
      if (noteError != null) {
        return noteError;
      }
    }
    return null;
  }
}
