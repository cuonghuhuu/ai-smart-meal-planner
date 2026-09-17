import 'package:smart_meal_planner/features/preferences/data/preference_models.dart';
import 'package:smart_meal_planner/l10n/app_strings.dart';

final class PreferencesValidation {
  const PreferencesValidation._();

  static String? validateAllergenNote(String? value) {
    if (value == null || value.length <= 255) {
      return null;
    }
    return AppStrings.allergenNoteTooLong;
  }

  static String? validateDietaryPreferences(
    Iterable<SelectedDietaryPreference> selections,
  ) {
    final seen = <String>{};
    for (final selection in selections) {
      final code = selection.code.trim();
      if (code.isEmpty) {
        return AppStrings.dietaryCodeBlank;
      }
      if (!seen.add(code)) {
        return AppStrings.dietaryDuplicate;
      }
    }
    return null;
  }

  static String? validateAllergens(Iterable<SelectedAllergen> selections) {
    final seen = <String>{};
    for (final selection in selections) {
      final code = selection.allergen.trim();
      if (code.isEmpty) {
        return AppStrings.allergenCodeBlank;
      }
      if (!seen.add(code)) {
        return AppStrings.allergenDuplicate;
      }
      final noteError = validateAllergenNote(selection.note);
      if (noteError != null) {
        return noteError;
      }
    }
    return null;
  }
}
