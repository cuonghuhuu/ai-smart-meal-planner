import 'package:flutter/foundation.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/features/preferences/application/preferences_validation.dart';
import 'package:smart_meal_planner/features/preferences/data/preference_models.dart';
import 'package:smart_meal_planner/features/preferences/data/preferences_repository.dart';
import 'package:smart_meal_planner/l10n/app_strings.dart';

enum PreferencesStatus {
  initial,
  loading,
  loaded,
  savingDietary,
  savingAllergens,
  error,
}

final class PreferencesState {
  PreferencesState({
    required this.status,
    required List<DietaryPreferenceReference> dietaryPreferenceReferences,
    required List<SelectedDietaryPreference> selectedDietaryPreferences,
    required List<AllergenReference> allergenReferences,
    required List<SelectedAllergen> selectedAllergens,
    required this.hasLoadedData,
    required this.revision,
    this.errorMessage,
    this.saveMessage,
  }) : dietaryPreferenceReferences = List.unmodifiable(
         dietaryPreferenceReferences,
       ),
       selectedDietaryPreferences = List.unmodifiable(
         selectedDietaryPreferences,
       ),
       allergenReferences = List.unmodifiable(allergenReferences),
       selectedAllergens = List.unmodifiable(selectedAllergens);

  factory PreferencesState.initial() => PreferencesState(
    status: PreferencesStatus.initial,
    dietaryPreferenceReferences: const [],
    selectedDietaryPreferences: const [],
    allergenReferences: const [],
    selectedAllergens: const [],
    hasLoadedData: false,
    revision: 0,
  );

  final PreferencesStatus status;
  final List<DietaryPreferenceReference> dietaryPreferenceReferences;
  final List<SelectedDietaryPreference> selectedDietaryPreferences;
  final List<AllergenReference> allergenReferences;
  final List<SelectedAllergen> selectedAllergens;
  final bool hasLoadedData;
  final int revision;
  final String? errorMessage;
  final String? saveMessage;

  List<String> get selectedDietaryCodes => [
    for (final selection in selectedDietaryPreferences) selection.code,
  ];

  List<String> get selectedAllergenCodes => [
    for (final selection in selectedAllergens) selection.allergen,
  ];

  bool get isSaving =>
      status == PreferencesStatus.savingDietary ||
      status == PreferencesStatus.savingAllergens;

  PreferencesState copyWith({
    PreferencesStatus? status,
    List<DietaryPreferenceReference>? dietaryPreferenceReferences,
    List<SelectedDietaryPreference>? selectedDietaryPreferences,
    List<AllergenReference>? allergenReferences,
    List<SelectedAllergen>? selectedAllergens,
    bool? hasLoadedData,
    int? revision,
    Object? errorMessage = _unset,
    Object? saveMessage = _unset,
  }) => PreferencesState(
    status: status ?? this.status,
    dietaryPreferenceReferences:
        dietaryPreferenceReferences ?? this.dietaryPreferenceReferences,
    selectedDietaryPreferences:
        selectedDietaryPreferences ?? this.selectedDietaryPreferences,
    allergenReferences: allergenReferences ?? this.allergenReferences,
    selectedAllergens: selectedAllergens ?? this.selectedAllergens,
    hasLoadedData: hasLoadedData ?? this.hasLoadedData,
    revision: revision ?? this.revision,
    errorMessage: identical(errorMessage, _unset)
        ? this.errorMessage
        : errorMessage as String?,
    saveMessage: identical(saveMessage, _unset)
        ? this.saveMessage
        : saveMessage as String?,
  );
}

const Object _unset = Object();

final class PreferencesController extends ChangeNotifier {
  PreferencesController({required this.repository})
    : _state = PreferencesState.initial();

  final PreferencesRepository repository;
  PreferencesState _state;
  int _revision = 0;

  PreferencesState get state => _state;

  Future<void> load() async {
    if (_state.status == PreferencesStatus.loading || _state.isSaving) {
      return;
    }

    final previous = _state;
    _state = previous.copyWith(
      status: PreferencesStatus.loading,
      errorMessage: null,
      saveMessage: null,
    );
    notifyListeners();

    try {
      final results = await Future.wait<Object?>([
        repository.getDietaryPreferenceReferences(),
        repository.getSelectedDietaryPreferences(),
        repository.getAllergenReferences(),
        repository.getSelectedAllergens(),
      ]);
      final dietaryReferences = results[0] as List<DietaryPreferenceReference>;
      final selectedDietary = results[1] as List<SelectedDietaryPreference>;
      final allergenReferences = results[2] as List<AllergenReference>;
      final selectedAllergens = results[3] as List<SelectedAllergen>;
      _revision++;
      _state = PreferencesState(
        status: PreferencesStatus.loaded,
        dietaryPreferenceReferences: dietaryReferences,
        selectedDietaryPreferences: selectedDietary,
        allergenReferences: allergenReferences,
        selectedAllergens: selectedAllergens,
        hasLoadedData: true,
        revision: _revision,
      );
    } on Object catch (error) {
      _state = previous.copyWith(
        status: PreferencesStatus.error,
        errorMessage: preferencesErrorMessage(error),
        saveMessage: null,
      );
    }
    notifyListeners();
  }

  Future<void> reload() => load();

  void setDietaryPreferenceSelected(String code, bool selected) {
    if (_state.isSaving || code.trim().isEmpty) {
      return;
    }
    final current = [..._state.selectedDietaryPreferences];
    final existingIndex = current.indexWhere((item) => item.code == code);
    if (selected && existingIndex == -1) {
      final reference = _findDietaryReference(code);
      if (reference == null) {
        return;
      }
      current.add(
        SelectedDietaryPreference(
          code: reference.code,
          displayName: reference.displayName,
          description: reference.description,
          isExclusionary: reference.isExclusionary,
        ),
      );
    } else if (!selected && existingIndex != -1) {
      current.removeAt(existingIndex);
    } else {
      return;
    }
    _state = _state.copyWith(
      status: PreferencesStatus.loaded,
      selectedDietaryPreferences: current,
      errorMessage: null,
      saveMessage: null,
    );
    notifyListeners();
  }

  void setAllergenSelected(String code, bool selected) {
    if (_state.isSaving || code.trim().isEmpty) {
      return;
    }
    final current = [..._state.selectedAllergens];
    final existingIndex = current.indexWhere((item) => item.allergen == code);
    if (selected && existingIndex == -1) {
      final reference = _findAllergenReference(code);
      if (reference == null) {
        return;
      }
      current.add(
        SelectedAllergen(
          allergen: reference.code,
          displayName: reference.displayName,
          description: reference.description,
          reactionKind: ReactionKind.unspecified,
          note: null,
        ),
      );
    } else if (!selected && existingIndex != -1) {
      current.removeAt(existingIndex);
    } else {
      return;
    }
    _state = _state.copyWith(
      status: PreferencesStatus.loaded,
      selectedAllergens: current,
      errorMessage: null,
      saveMessage: null,
    );
    notifyListeners();
  }

  void setAllergenReactionKind(String code, ReactionKind reactionKind) {
    if (_state.isSaving) {
      return;
    }
    final index = _state.selectedAllergens.indexWhere(
      (item) => item.allergen == code,
    );
    if (index == -1 ||
        _state.selectedAllergens[index].reactionKind == reactionKind) {
      return;
    }
    final current = [..._state.selectedAllergens];
    current[index] = current[index].copyWith(reactionKind: reactionKind);
    _state = _state.copyWith(
      status: PreferencesStatus.loaded,
      selectedAllergens: current,
      errorMessage: null,
      saveMessage: null,
    );
    notifyListeners();
  }

  void setAllergenNote(String code, String value) {
    if (_state.isSaving) {
      return;
    }
    final index = _state.selectedAllergens.indexWhere(
      (item) => item.allergen == code,
    );
    if (index == -1) {
      return;
    }
    final current = [..._state.selectedAllergens];
    current[index] = current[index].copyWith(
      note: value.isEmpty ? null : value,
    );
    _state = _state.copyWith(
      status: PreferencesStatus.loaded,
      selectedAllergens: current,
      errorMessage: null,
      saveMessage: null,
    );
    notifyListeners();
  }

  Future<void> saveDietaryPreferences() async {
    if (_state.isSaving) {
      return;
    }
    final validationError = PreferencesValidation.validateDietaryPreferences(
      _state.selectedDietaryPreferences,
    );
    if (validationError != null) {
      _setError(validationError);
      return;
    }

    _state = _state.copyWith(
      status: PreferencesStatus.savingDietary,
      errorMessage: null,
      saveMessage: null,
    );
    notifyListeners();

    try {
      final updated = await repository.replaceDietaryPreferences(
        _state.selectedDietaryCodes,
      );
      _revision++;
      _state = _state.copyWith(
        status: PreferencesStatus.loaded,
        selectedDietaryPreferences: updated,
        revision: _revision,
        errorMessage: null,
        saveMessage: AppStrings.dietaryPreferencesSaved,
      );
    } on Object catch (error) {
      _state = _state.copyWith(
        status: PreferencesStatus.error,
        errorMessage: preferencesErrorMessage(error),
        saveMessage: null,
      );
    }
    notifyListeners();
  }

  Future<void> saveAllergens() async {
    if (_state.isSaving) {
      return;
    }
    final validationError = PreferencesValidation.validateAllergens(
      _state.selectedAllergens,
    );
    if (validationError != null) {
      _setError(validationError);
      return;
    }

    _state = _state.copyWith(
      status: PreferencesStatus.savingAllergens,
      errorMessage: null,
      saveMessage: null,
    );
    notifyListeners();

    try {
      final updated = await repository.replaceAllergens([
        for (final selection in _state.selectedAllergens) selection.toRequest(),
      ]);
      _revision++;
      _state = _state.copyWith(
        status: PreferencesStatus.loaded,
        selectedAllergens: updated,
        revision: _revision,
        errorMessage: null,
        saveMessage: AppStrings.allergensSaved,
      );
    } on Object catch (error) {
      _state = _state.copyWith(
        status: PreferencesStatus.error,
        errorMessage: preferencesErrorMessage(error),
        saveMessage: null,
      );
    }
    notifyListeners();
  }

  DietaryPreferenceReference? _findDietaryReference(String code) {
    for (final reference in _state.dietaryPreferenceReferences) {
      if (reference.code == code) {
        return reference;
      }
    }
    return null;
  }

  AllergenReference? _findAllergenReference(String code) {
    for (final reference in _state.allergenReferences) {
      if (reference.code == code) {
        return reference;
      }
    }
    return null;
  }

  void _setError(String message) {
    _state = _state.copyWith(
      status: PreferencesStatus.error,
      errorMessage: message,
      saveMessage: null,
    );
    notifyListeners();
  }
}

String preferencesErrorMessage(Object error) {
  if (error is ApiTransportException) {
    return AppStrings.unableToReachService;
  }
  if (error is ApiHttpException) {
    if (error.statusCode >= 500) {
      return AppStrings.serviceUnavailable;
    }
    if (error.statusCode == 400) {
      return AppStrings.requestFailed;
    }
  }
  return AppStrings.preferencesLoadSaveFailed;
}
