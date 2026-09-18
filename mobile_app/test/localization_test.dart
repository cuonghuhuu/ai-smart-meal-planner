import 'package:flutter_test/flutter_test.dart';
import 'package:smart_meal_planner/features/preferences/data/preference_models.dart';
import 'package:smart_meal_planner/features/profile/data/profile_models.dart';
import 'package:smart_meal_planner/l10n/reference_localizations.dart';

void main() {
  test('reference labels are localized by backend code with safe fallback', () {
    expect(
      ReferenceLocalizations.activityName('MODERATE', 'Moderately active'),
      'Hoạt động vừa phải',
    );
    expect(
      ReferenceLocalizations.nutritionGoalName('MAINTAIN', 'Maintain weight'),
      'Duy trì cân nặng',
    );
    expect(ReferenceLocalizations.dietaryName('VEGAN', 'Vegan'), 'Thuần chay');
    expect(
      ReferenceLocalizations.allergenName('PEANUT', 'Peanuts'),
      'Đậu phộng',
    );
    expect(
      ReferenceLocalizations.reactionKindName('ALLERGY', 'Allergy'),
      'Dị ứng',
    );
    expect(
      ReferenceLocalizations.dietaryName('SERVER_DEFINED', 'Server-defined'),
      'Server-defined',
    );
    expect(
      ReferenceLocalizations.allergenDescription(
        'SERVER_DEFINED',
        'Server description',
      ),
      'Server description',
    );
  });

  test(
    'presentation localization does not change API codes or serialization',
    () {
      final draft = ProfileDraft(
        birthDate: DateTime(2026, 9, 17),
        sex: 'FEMALE',
        heightCm: 170,
        activityLevel: 'MODERATE',
        nutritionGoal: 'MAINTAIN',
        targetWeightKg: 60,
        weeklyChangeKg: 0.5,
        householdSize: 1,
        maxCookMinutes: 30,
        notes: null,
        version: 2,
      );
      expect(draft.toJson(), containsPair('birthDate', '2026-09-17'));
      expect(draft.toJson(), containsPair('sex', 'FEMALE'));
      expect(draft.toJson(), containsPair('activityLevel', 'MODERATE'));
      expect(draft.toJson(), containsPair('nutritionGoal', 'MAINTAIN'));
      expect(draft.toJson(), containsPair('weeklyChangeKg', 0.5));

      const allergen = AllergenSelection(
        allergen: 'PEANUT',
        reactionKind: ReactionKind.intolerance,
        note: null,
      );
      expect(allergen.toJson(), {
        'allergen': 'PEANUT',
        'reactionKind': 'INTOLERANCE',
        'note': null,
      });
    },
  );
}
