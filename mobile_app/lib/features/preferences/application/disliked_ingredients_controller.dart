import 'package:flutter/foundation.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/features/catalog/data/catalog_models.dart';
import 'package:smart_meal_planner/features/catalog/data/catalog_repository.dart';
import 'package:smart_meal_planner/features/preferences/application/disliked_ingredients_validation.dart';
import 'package:smart_meal_planner/features/preferences/data/disliked_ingredient_models.dart';
import 'package:smart_meal_planner/features/preferences/data/disliked_ingredients_repository.dart';
import 'package:smart_meal_planner/l10n/app_strings.dart';

enum DislikedIngredientsStatus { initial, loading, loaded, saving, error }

final class DislikedIngredientsState {
  DislikedIngredientsState({
    required this.status,
    required List<DislikedIngredientPreference> savedPreferences,
    required List<DislikedIngredientPreference> draftPreferences,
    required this.hasLoadedData,
    this.errorMessage,
    this.saveMessage,
  }) : savedPreferences = List.unmodifiable(savedPreferences),
       draftPreferences = List.unmodifiable(draftPreferences);

  factory DislikedIngredientsState.initial() => DislikedIngredientsState(
    status: DislikedIngredientsStatus.initial,
    savedPreferences: <DislikedIngredientPreference>[],
    draftPreferences: <DislikedIngredientPreference>[],
    hasLoadedData: false,
  );

  final DislikedIngredientsStatus status;
  final List<DislikedIngredientPreference> savedPreferences;
  final List<DislikedIngredientPreference> draftPreferences;
  final bool hasLoadedData;
  final String? errorMessage;
  final String? saveMessage;

  bool get isSaving => status == DislikedIngredientsStatus.saving;

  bool get hasChanges =>
      !_samePreferences(savedPreferences, draftPreferences);

  DislikedIngredientsState copyWith({
    DislikedIngredientsStatus? status,
    List<DislikedIngredientPreference>? savedPreferences,
    List<DislikedIngredientPreference>? draftPreferences,
    bool? hasLoadedData,
    Object? errorMessage = _stateUnset,
    Object? saveMessage = _stateUnset,
  }) => DislikedIngredientsState(
    status: status ?? this.status,
    savedPreferences: savedPreferences ?? this.savedPreferences,
    draftPreferences: draftPreferences ?? this.draftPreferences,
    hasLoadedData: hasLoadedData ?? this.hasLoadedData,
    errorMessage: identical(errorMessage, _stateUnset)
        ? this.errorMessage
        : errorMessage as String?,
    saveMessage: identical(saveMessage, _stateUnset)
        ? this.saveMessage
        : saveMessage as String?,
  );
}

enum DislikedIngredientPickerStatus { initial, loading, loaded, error }

final class DislikedIngredientPickerState {
  DislikedIngredientPickerState({
    required this.status,
    required List<IngredientCatalogItem> items,
    required this.query,
    required this.page,
    required this.totalPages,
    this.errorMessage,
    this.loadMoreErrorMessage,
    this.loadingMore = false,
  }) : items = List.unmodifiable(items);

  factory DislikedIngredientPickerState.initial() =>
      DislikedIngredientPickerState(
        status: DislikedIngredientPickerStatus.initial,
        items: <IngredientCatalogItem>[],
        query: '',
        page: 0,
        totalPages: 0,
      );

  final DislikedIngredientPickerStatus status;
  final List<IngredientCatalogItem> items;
  final String query;
  final int page;
  final int totalPages;
  final String? errorMessage;
  final String? loadMoreErrorMessage;
  final bool loadingMore;

  bool get hasMore => page + 1 < totalPages;

  DislikedIngredientPickerState copyWith({
    DislikedIngredientPickerStatus? status,
    List<IngredientCatalogItem>? items,
    String? query,
    int? page,
    int? totalPages,
    Object? errorMessage = _stateUnset,
    Object? loadMoreErrorMessage = _stateUnset,
    bool? loadingMore,
  }) => DislikedIngredientPickerState(
    status: status ?? this.status,
    items: items ?? this.items,
    query: query ?? this.query,
    page: page ?? this.page,
    totalPages: totalPages ?? this.totalPages,
    errorMessage: identical(errorMessage, _stateUnset)
        ? this.errorMessage
        : errorMessage as String?,
    loadMoreErrorMessage: identical(loadMoreErrorMessage, _stateUnset)
        ? this.loadMoreErrorMessage
        : loadMoreErrorMessage as String?,
    loadingMore: loadingMore ?? this.loadingMore,
  );
}

final class DislikedIngredientsController extends ChangeNotifier {
  DislikedIngredientsController({
    required this.repository,
    required this.catalogRepository,
    this.pageSize = 20,
  }) : _state = DislikedIngredientsState.initial(),
       _pickerState = DislikedIngredientPickerState.initial();

  final DislikedIngredientsRepository repository;
  final CatalogRepository catalogRepository;
  final int pageSize;

  DislikedIngredientsState _state;
  DislikedIngredientPickerState _pickerState;
  bool _disposed = false;
  int _preferenceGeneration = 0;
  int _pickerGeneration = 0;

  DislikedIngredientsState get state => _state;
  DislikedIngredientPickerState get pickerState => _pickerState;

  Future<void> load() async {
    if (_disposed ||
        _state.status == DislikedIngredientsStatus.loading ||
        _state.status == DislikedIngredientsStatus.saving) {
      return;
    }

    final generation = ++_preferenceGeneration;
    _state = _state.copyWith(
      status: DislikedIngredientsStatus.loading,
      errorMessage: null,
      saveMessage: null,
    );
    _notify();

    try {
      final preferences = await repository.getDislikedIngredients();
      if (!_isPreferenceCurrent(generation)) {
        return;
      }
      _state = DislikedIngredientsState(
        status: DislikedIngredientsStatus.loaded,
        savedPreferences: preferences,
        draftPreferences: preferences,
        hasLoadedData: true,
      );
    } on Object catch (error) {
      if (!_isPreferenceCurrent(generation)) {
        return;
      }
      _state = _state.copyWith(
        status: DislikedIngredientsStatus.error,
        errorMessage: dislikedIngredientsErrorMessage(error),
        saveMessage: null,
      );
    }

    if (_isPreferenceCurrent(generation)) {
      _notify();
    }
  }

  Future<void> reload() => load();

  void addIngredient(IngredientCatalogItem ingredient) {
    if (_disposed || !_state.hasLoadedData || _state.isSaving) {
      return;
    }
    if (isIngredientSelected(ingredient.publicId)) {
      return;
    }
    final draft = [
      ..._state.draftPreferences,
      DislikedIngredientPreference(
        ingredientPublicId: ingredient.publicId,
        ingredientCode: ingredient.code,
        ingredientDisplayName: ingredient.displayName,
        category: ingredient.category,
        strength: DislikedIngredientStrength.dislike,
        note: null,
      ),
    ];
    _updateDraft(draft);
  }

  bool isIngredientSelected(String publicId) => _state.draftPreferences.any(
    (preference) => preference.ingredientPublicId == publicId,
  );

  void removeIngredient(String publicId) {
    if (_disposed || !_state.hasLoadedData || _state.isSaving) {
      return;
    }
    final draft = [
      for (final preference in _state.draftPreferences)
        if (preference.ingredientPublicId != publicId) preference,
    ];
    if (draft.length == _state.draftPreferences.length) {
      return;
    }
    _updateDraft(draft);
  }

  void setStrength(String publicId, DislikedIngredientStrength strength) {
    if (_disposed || !_state.hasLoadedData || _state.isSaving) {
      return;
    }
    final index = _state.draftPreferences.indexWhere(
      (preference) => preference.ingredientPublicId == publicId,
    );
    if (index == -1 || _state.draftPreferences[index].strength == strength) {
      return;
    }
    final draft = [..._state.draftPreferences];
    draft[index] = draft[index].copyWith(strength: strength);
    _updateDraft(draft);
  }

  void setNote(String publicId, String note) {
    if (_disposed || !_state.hasLoadedData || _state.isSaving) {
      return;
    }
    final index = _state.draftPreferences.indexWhere(
      (preference) => preference.ingredientPublicId == publicId,
    );
    if (index == -1 || _state.draftPreferences[index].note == note) {
      return;
    }
    final draft = [..._state.draftPreferences];
    draft[index] = draft[index].copyWith(note: note);
    _updateDraft(draft);
  }

  Future<void> save() async {
    if (_disposed || !_state.hasLoadedData || _state.isSaving) {
      return;
    }
    final validationError =
        DislikedIngredientsValidation.validateSelections(
          _state.draftPreferences,
        );
    if (validationError != null) {
      _state = _state.copyWith(
        status: DislikedIngredientsStatus.error,
        errorMessage: validationError,
        saveMessage: null,
      );
      _notify();
      return;
    }

    final generation = ++_preferenceGeneration;
    final draft = List<DislikedIngredientPreference>.from(
      _state.draftPreferences,
    );
    _state = _state.copyWith(
      status: DislikedIngredientsStatus.saving,
      errorMessage: null,
      saveMessage: null,
    );
    _notify();

    try {
      final updated = await repository.replaceDislikedIngredients([
        for (final preference in draft) preference.toSelection(),
      ]);
      if (!_isPreferenceCurrent(generation)) {
        return;
      }
      _state = DislikedIngredientsState(
        status: DislikedIngredientsStatus.loaded,
        savedPreferences: updated,
        draftPreferences: updated,
        hasLoadedData: true,
        saveMessage: AppStrings.dislikedIngredientsSaved,
      );
    } on Object catch (error) {
      if (!_isPreferenceCurrent(generation)) {
        return;
      }
      _state = _state.copyWith(
        status: DislikedIngredientsStatus.error,
        errorMessage: dislikedIngredientsErrorMessage(error, saving: true),
        saveMessage: null,
      );
    }

    if (_isPreferenceCurrent(generation)) {
      _notify();
    }
  }

  Future<void> loadPickerInitial() => searchPicker('');

  Future<void> searchPicker(String query) async {
    if (_disposed) {
      return;
    }
    final generation = ++_pickerGeneration;
    final normalizedQuery = query.trim();
    _pickerState = DislikedIngredientPickerState(
      status: DislikedIngredientPickerStatus.loading,
      items: <IngredientCatalogItem>[],
      query: normalizedQuery,
      page: 0,
      totalPages: 0,
    );
    _notify();

    try {
      final result = await catalogRepository.getIngredients(
        query: normalizedQuery.isEmpty ? null : normalizedQuery,
        page: 0,
        size: pageSize,
      );
      if (!_isPickerCurrent(generation)) {
        return;
      }
      _pickerState = DislikedIngredientPickerState(
        status: DislikedIngredientPickerStatus.loaded,
        items: result.content,
        query: normalizedQuery,
        page: result.page,
        totalPages: result.totalPages,
      );
    } on Object catch (error) {
      if (!_isPickerCurrent(generation)) {
        return;
      }
      _pickerState = DislikedIngredientPickerState(
        status: DislikedIngredientPickerStatus.error,
        items: <IngredientCatalogItem>[],
        query: normalizedQuery,
        page: 0,
        totalPages: 0,
        errorMessage: dislikedIngredientPickerErrorMessage(error),
      );
    }

    if (_isPickerCurrent(generation)) {
      _notify();
    }
  }

  Future<void> loadPickerMore() async {
    if (_disposed ||
        _pickerState.status != DislikedIngredientPickerStatus.loaded ||
        _pickerState.loadingMore ||
        !_pickerState.hasMore) {
      return;
    }
    final generation = _pickerGeneration;
    final nextPage = _pickerState.page + 1;
    _pickerState = _pickerState.copyWith(
      loadingMore: true,
      loadMoreErrorMessage: null,
    );
    _notify();

    try {
      final result = await catalogRepository.getIngredients(
        query: _pickerState.query.isEmpty ? null : _pickerState.query,
        page: nextPage,
        size: pageSize,
      );
      if (!_isPickerCurrent(generation)) {
        return;
      }
      final existingIds = {
        for (final item in _pickerState.items) item.publicId,
      };
      final additions = [
        for (final item in result.content)
          if (existingIds.add(item.publicId)) item,
      ];
      _pickerState = _pickerState.copyWith(
        status: DislikedIngredientPickerStatus.loaded,
        items: [..._pickerState.items, ...additions],
        page: result.page,
        totalPages: result.totalPages,
        loadingMore: false,
        loadMoreErrorMessage: null,
      );
    } on Object catch (error) {
      if (!_isPickerCurrent(generation)) {
        return;
      }
      _pickerState = _pickerState.copyWith(
        status: DislikedIngredientPickerStatus.loaded,
        loadingMore: false,
        loadMoreErrorMessage: dislikedIngredientPickerErrorMessage(
          error,
          loadMore: true,
        ),
      );
    }

    if (_isPickerCurrent(generation)) {
      _notify();
    }
  }

  Future<void> retryPicker() {
    if (_pickerState.status == DislikedIngredientPickerStatus.error) {
      return searchPicker(_pickerState.query);
    }
    if (_pickerState.loadMoreErrorMessage != null) {
      return loadPickerMore();
    }
    return Future<void>.value();
  }

  void resetForSessionChange() {
    if (_disposed) {
      return;
    }
    _preferenceGeneration++;
    _pickerGeneration++;
    _state = DislikedIngredientsState.initial();
    _pickerState = DislikedIngredientPickerState.initial();
    _notify();
  }

  @override
  void dispose() {
    _disposed = true;
    _preferenceGeneration++;
    _pickerGeneration++;
    super.dispose();
  }

  void _updateDraft(List<DislikedIngredientPreference> draft) {
    _state = _state.copyWith(
      status: DislikedIngredientsStatus.loaded,
      draftPreferences: draft,
      errorMessage: null,
      saveMessage: null,
    );
    _notify();
  }

  bool _isPreferenceCurrent(int generation) =>
      !_disposed && generation == _preferenceGeneration;

  bool _isPickerCurrent(int generation) =>
      !_disposed && generation == _pickerGeneration;

  void _notify() {
    if (!_disposed) {
      notifyListeners();
    }
  }
}

const Object _stateUnset = Object();

bool _samePreferences(
  List<DislikedIngredientPreference> first,
  List<DislikedIngredientPreference> second,
) {
  if (first.length != second.length) {
    return false;
  }
  final byId = {
    for (final preference in first)
      preference.ingredientPublicId: preference,
  };
  for (final preference in second) {
    final original = byId[preference.ingredientPublicId];
    if (original == null ||
        original.strength != preference.strength ||
        original.note != preference.note) {
      return false;
    }
  }
  return true;
}

String dislikedIngredientsErrorMessage(Object error, {bool saving = false}) {
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
  return saving
      ? AppStrings.dislikedIngredientsSaveFailed
      : AppStrings.dislikedIngredientsLoadFailed;
}

String dislikedIngredientPickerErrorMessage(
  Object error, {
  bool loadMore = false,
}) {
  if (error is ApiTransportException) {
    return AppStrings.unableToReachService;
  }
  if (error is ApiHttpException && error.statusCode >= 500) {
    return AppStrings.serviceUnavailable;
  }
  return loadMore
      ? AppStrings.dislikedIngredientPickerLoadMoreFailed
      : AppStrings.dislikedIngredientPickerLoadFailed;
}
