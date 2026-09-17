import 'package:flutter_test/flutter_test.dart';
import 'package:smart_meal_planner/features/preferences/application/preferences_validation.dart';
import 'package:smart_meal_planner/features/preferences/data/preference_models.dart';
import 'package:smart_meal_planner/l10n/app_strings.dart';

SelectedAllergen _selection({String? note}) => const SelectedAllergen(
  allergen: 'PEANUT',
  displayName: 'Peanuts',
  description: 'Peanuts and peanut products.',
  reactionKind: ReactionKind.unspecified,
  note: null,
).copyWith(note: note);

void main() {
  test('blank allergen note is valid', () {
    expect(PreferencesValidation.validateAllergenNote(null), isNull);
    expect(PreferencesValidation.validateAllergenNote(''), isNull);
    expect(PreferencesValidation.validateAllergenNote('   '), isNull);
  });

  test('reaction kinds preserve all backend enum values', () {
    expect(ReactionKind.fromWireValue('ALLERGY'), ReactionKind.allergy);
    expect(ReactionKind.fromWireValue('INTOLERANCE'), ReactionKind.intolerance);
    expect(ReactionKind.fromWireValue('UNSPECIFIED'), ReactionKind.unspecified);
  });

  test('allergen note accepts 255 characters and rejects 256', () {
    expect(PreferencesValidation.validateAllergenNote('a' * 255), isNull);
    expect(
      PreferencesValidation.validateAllergenNote('a' * 256),
      AppStrings.allergenNoteTooLong,
    );
    expect(
      PreferencesValidation.validateAllergens([_selection(note: 'a' * 255)]),
      isNull,
    );
    expect(
      PreferencesValidation.validateAllergens([_selection(note: 'a' * 256)]),
      isNotNull,
    );
  });

  test('duplicate allergen selections are invalid', () {
    final selection = _selection();
    expect(
      PreferencesValidation.validateAllergens([selection, selection]),
      isNotNull,
    );
  });
}
