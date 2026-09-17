import 'package:flutter_test/flutter_test.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/features/preferences/application/preferences_controller.dart';
import 'package:smart_meal_planner/features/preferences/data/preference_models.dart';
import 'package:smart_meal_planner/features/preferences/data/preferences_repository.dart';

void main() {
  test('load transitions from initial to loading to loaded', () async {
    final controller = PreferencesController(
      repository: FakePreferencesRepository(),
    );
    final statuses = <PreferencesStatus>[];
    controller.addListener(() => statuses.add(controller.state.status));

    await controller.load();

    expect(statuses, [PreferencesStatus.loading, PreferencesStatus.loaded]);
    expect(
      controller.state.dietaryPreferenceReferences.map((item) => item.code),
      ['VEGAN', 'LOW_CARB'],
    );
    expect(controller.state.selectedDietaryCodes, ['VEGAN']);
    expect(controller.state.selectedAllergenCodes, ['PEANUT']);
    expect(
      controller.state.selectedAllergens.single.reactionKind,
      ReactionKind.allergy,
    );
  });

  test(
    'zero dietary selections are valid and saved as an empty replacement',
    () async {
      final repository = FakePreferencesRepository(
        selectedDietaryPreferences: const [],
        selectedAllergens: const [],
        dietaryResponse: const [],
      );
      final controller = PreferencesController(repository: repository);

      await controller.load();
      await controller.saveDietaryPreferences();

      expect(repository.dietaryUpdateCalls, 1);
      expect(repository.lastDietaryCodes, isEmpty);
      expect(controller.state.selectedDietaryPreferences, isEmpty);
    },
  );

  test(
    'multiple dietary selections are supported without duplicates',
    () async {
      final repository = FakePreferencesRepository(
        selectedDietaryPreferences: const [],
      );
      final controller = PreferencesController(repository: repository);
      await controller.load();

      controller.setDietaryPreferenceSelected('VEGAN', true);
      controller.setDietaryPreferenceSelected('LOW_CARB', true);
      controller.setDietaryPreferenceSelected('VEGAN', true);

      expect(controller.state.selectedDietaryCodes, ['VEGAN', 'LOW_CARB']);

      controller.setDietaryPreferenceSelected('VEGAN', false);
      expect(controller.state.selectedDietaryCodes, ['LOW_CARB']);
    },
  );

  test(
    'successful dietary save replaces selections with complete response',
    () async {
      final repository = FakePreferencesRepository(
        selectedDietaryPreferences: const [],
        dietaryResponse: const [
          SelectedDietaryPreference(
            code: 'LOW_CARB',
            displayName: 'Low carbohydrate',
            description: 'Limit carbohydrate-heavy choices.',
            isExclusionary: true,
          ),
        ],
      );
      final controller = PreferencesController(repository: repository);
      await controller.load();
      controller.setDietaryPreferenceSelected('VEGAN', true);

      await controller.saveDietaryPreferences();

      expect(controller.state.status, PreferencesStatus.loaded);
      expect(controller.state.selectedDietaryCodes, ['LOW_CARB']);
      expect(
        controller.state.selectedDietaryPreferences.single.displayName,
        'Low carbohydrate',
      );
      expect(controller.state.saveMessage, 'Đã lưu sở thích ăn uống.');
    },
  );

  test('recoverable dietary save errors preserve selections', () async {
    final repository = FakePreferencesRepository(
      selectedDietaryPreferences: const [],
      dietaryError: const ApiTransportException(
        ApiTransportFailureKind.network,
      ),
    );
    final controller = PreferencesController(repository: repository);
    await controller.load();
    controller.setDietaryPreferenceSelected('VEGAN', true);

    await controller.saveDietaryPreferences();

    expect(controller.state.status, PreferencesStatus.error);
    expect(controller.state.selectedDietaryCodes, ['VEGAN']);
    expect(
      controller.state.errorMessage,
      'Không thể kết nối đến dịch vụ. Vui lòng thử lại.',
    );
    expect(repository.dietaryUpdateCalls, 1);
  });

  test(
    'new allergen selection defaults to unspecified and stays unique',
    () async {
      final repository = FakePreferencesRepository(selectedAllergens: const []);
      final controller = PreferencesController(repository: repository);
      await controller.load();

      controller.setAllergenSelected('PEANUT', true);
      controller.setAllergenSelected('PEANUT', true);

      expect(controller.state.selectedAllergens, hasLength(1));
      expect(
        controller.state.selectedAllergens.single.reactionKind,
        ReactionKind.unspecified,
      );
    },
  );

  test(
    'allergen reaction and note edits are preserved in the request',
    () async {
      final repository = FakePreferencesRepository(selectedAllergens: const []);
      final controller = PreferencesController(repository: repository);
      await controller.load();
      controller.setAllergenSelected('PEANUT', true);
      controller.setAllergenReactionKind('PEANUT', ReactionKind.intolerance);
      controller.setAllergenNote('PEANUT', '  Avoid shared equipment.  ');

      await controller.saveAllergens();

      expect(repository.allergenUpdateCalls, 1);
      expect(repository.lastAllergenSelections, hasLength(1));
      expect(repository.lastAllergenSelections!.single.allergen, 'PEANUT');
      expect(
        repository.lastAllergenSelections!.single.reactionKind,
        ReactionKind.intolerance,
      );
      expect(
        repository.lastAllergenSelections!.single.note,
        '  Avoid shared equipment.  ',
      );
    },
  );

  test(
    'unselecting an allergen removes it and empty replacement clears all',
    () async {
      final repository = FakePreferencesRepository();
      final controller = PreferencesController(repository: repository);
      await controller.load();
      controller.setAllergenSelected('PEANUT', false);

      await controller.saveAllergens();

      expect(repository.lastAllergenSelections, isEmpty);
    },
  );

  test(
    'successful allergen save replaces local state with server response',
    () async {
      final repository = FakePreferencesRepository(
        allergenResponse: const [
          SelectedAllergen(
            allergen: 'TREE_NUT',
            displayName: 'Tree nuts',
            description: 'Tree nuts and products.',
            reactionKind: ReactionKind.unspecified,
            note: null,
          ),
        ],
      );
      final controller = PreferencesController(repository: repository);
      await controller.load();

      await controller.saveAllergens();

      expect(controller.state.selectedAllergenCodes, ['TREE_NUT']);
      expect(
        controller.state.selectedAllergens.single.displayName,
        'Tree nuts',
      );
    },
  );

  test('invalid allergen note prevents a repository save', () async {
    final repository = FakePreferencesRepository(selectedAllergens: const []);
    final controller = PreferencesController(repository: repository);
    await controller.load();
    controller.setAllergenSelected('PEANUT', true);
    controller.setAllergenNote('PEANUT', 'x' * 256);

    await controller.saveAllergens();

    expect(repository.allergenUpdateCalls, 0);
    expect(controller.state.status, PreferencesStatus.error);
    expect(controller.state.selectedAllergens.single.note, 'x' * 256);
  });
}

final class FakePreferencesRepository implements PreferencesRepository {
  FakePreferencesRepository({
    List<DietaryPreferenceReference>? dietaryReferences,
    List<SelectedDietaryPreference>? selectedDietaryPreferences,
    List<AllergenReference>? allergenReferences,
    List<SelectedAllergen>? selectedAllergens,
    this.dietaryResponse,
    this.allergenResponse,
    this.dietaryError,
    this.allergenError,
  }) : dietaryReferences = dietaryReferences ?? _dietaryReferences,
       selectedDietaryPreferences =
           selectedDietaryPreferences ?? _selectedDietaryPreferences,
       allergenReferences = allergenReferences ?? _allergenReferences,
       selectedAllergens = selectedAllergens ?? _selectedAllergens;

  final List<DietaryPreferenceReference> dietaryReferences;
  final List<SelectedDietaryPreference> selectedDietaryPreferences;
  final List<AllergenReference> allergenReferences;
  final List<SelectedAllergen> selectedAllergens;
  final List<SelectedDietaryPreference>? dietaryResponse;
  final List<SelectedAllergen>? allergenResponse;
  final Object? dietaryError;
  final Object? allergenError;
  var dietaryUpdateCalls = 0;
  var allergenUpdateCalls = 0;
  List<String>? lastDietaryCodes;
  List<AllergenSelection>? lastAllergenSelections;

  @override
  Future<List<DietaryPreferenceReference>>
  getDietaryPreferenceReferences() async => dietaryReferences;

  @override
  Future<List<SelectedDietaryPreference>>
  getSelectedDietaryPreferences() async => selectedDietaryPreferences;

  @override
  Future<List<SelectedDietaryPreference>> replaceDietaryPreferences(
    List<String> codes,
  ) async {
    dietaryUpdateCalls++;
    lastDietaryCodes = [...codes];
    if (dietaryError != null) throw dietaryError!;
    return dietaryResponse ?? selectedDietaryPreferences;
  }

  @override
  Future<List<AllergenReference>> getAllergenReferences() async =>
      allergenReferences;

  @override
  Future<List<SelectedAllergen>> getSelectedAllergens() async =>
      selectedAllergens;

  @override
  Future<List<SelectedAllergen>> replaceAllergens(
    List<AllergenSelection> selections,
  ) async {
    allergenUpdateCalls++;
    lastAllergenSelections = [...selections];
    if (allergenError != null) throw allergenError!;
    return allergenResponse ?? selectedAllergens;
  }
}

const _dietaryReferences = [
  DietaryPreferenceReference(
    code: 'VEGAN',
    displayName: 'Vegan',
    description: 'Plant-based meals.',
    isExclusionary: true,
    displayOrder: 1,
  ),
  DietaryPreferenceReference(
    code: 'LOW_CARB',
    displayName: 'Low carbohydrate',
    description: 'Limit carbohydrate-heavy choices.',
    isExclusionary: true,
    displayOrder: 2,
  ),
];

const _selectedDietaryPreferences = [
  SelectedDietaryPreference(
    code: 'VEGAN',
    displayName: 'Vegan',
    description: 'Plant-based meals.',
    isExclusionary: true,
  ),
];

const _allergenReferences = [
  AllergenReference(
    code: 'PEANUT',
    displayName: 'Peanuts',
    description: 'Peanuts and peanut products.',
    displayOrder: 1,
  ),
  AllergenReference(
    code: 'TREE_NUT',
    displayName: 'Tree nuts',
    description: 'Tree nuts and products.',
    displayOrder: 2,
  ),
];

const _selectedAllergens = [
  SelectedAllergen(
    allergen: 'PEANUT',
    displayName: 'Peanuts',
    description: 'Peanuts and peanut products.',
    reactionKind: ReactionKind.allergy,
    note: 'Avoid cross-contact.',
  ),
];
