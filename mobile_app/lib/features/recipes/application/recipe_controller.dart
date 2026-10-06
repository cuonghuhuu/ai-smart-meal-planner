import 'package:flutter/foundation.dart';
import 'package:smart_meal_planner/features/recipes/application/recipe_error_messages.dart';
import 'package:smart_meal_planner/features/recipes/application/recipe_state.dart';
import 'package:smart_meal_planner/features/recipes/data/recipe_models.dart';
import 'package:smart_meal_planner/features/recipes/data/recipe_repository.dart';

/// Current-session Recipe reads. Each independent request scope rejects stale results.
final class RecipeController extends ChangeNotifier {
  RecipeController({required this.repository, this.pageSize = 20}) {
    if (pageSize < 1 || pageSize > 100) {
      throw ArgumentError.value(
        pageSize,
        'pageSize',
        'must be between 1 and 100',
      );
    }
    _catalogState = RecipeCatalogState(
      status: RecipeListStatus.initial,
      filters: const RecipeFilters(),
      items: const [],
      page: 0,
      size: pageSize,
      totalElements: 0,
      totalPages: 0,
    );
  }

  final RecipeRepository repository;
  final int pageSize;
  late RecipeCatalogState _catalogState;
  RecipeDetailState _detailState = const RecipeDetailState();
  RecipeReferenceState<RecipeTag> _tagsState = RecipeReferenceState.initial();
  RecipeReferenceState<RecipeMealSlot> _mealSlotsState =
      RecipeReferenceState.initial();
  bool _disposed = false;
  int _sessionGeneration = 0;
  int _catalogGeneration = 0;
  int _detailGeneration = 0;
  int _tagsGeneration = 0;
  int _mealSlotsGeneration = 0;

  RecipeCatalogState get catalogState => _catalogState;
  RecipeDetailState get detailState => _detailState;
  RecipeReferenceState<RecipeTag> get tagsState => _tagsState;
  RecipeReferenceState<RecipeMealSlot> get mealSlotsState => _mealSlotsState;

  Future<void> loadInitial() {
    if (_disposed || _catalogState.status != RecipeListStatus.initial) {
      return Future<void>.value();
    }
    return _loadFirstPage(_catalogState.filters);
  }

  Future<void> reload() => _loadFirstPage(_catalogState.filters);
  Future<void> retryCatalog() => reload();

  Future<void> search(String query) =>
      _loadFirstPage(_catalogState.filters.copyWith(query: query.trim()));

  Future<void> setMealSlotCode(String? code) => _loadFirstPage(
    _catalogState.filters.copyWith(mealSlotCode: _normalizedCode(code)),
  );

  Future<void> setTagCode(String? code) => _loadFirstPage(
    _catalogState.filters.copyWith(tagCode: _normalizedCode(code)),
  );

  Future<void> setMaxMinutes(int? value) {
    if (value != null && (value < 0 || value > 10080)) {
      throw ArgumentError.value(
        value,
        'maxMinutes',
        'must be between 0 and 10080',
      );
    }
    return _loadFirstPage(_catalogState.filters.copyWith(maxMinutes: value));
  }

  Future<void> clearFilters() => _loadFirstPage(const RecipeFilters());

  Future<void> _loadFirstPage(RecipeFilters filters) async {
    if (_disposed) return;
    final generation = ++_catalogGeneration;
    final session = _sessionGeneration;
    _catalogState = RecipeCatalogState(
      status: RecipeListStatus.loading,
      filters: filters,
      items: const [],
      page: 0,
      size: pageSize,
      totalElements: 0,
      totalPages: 0,
    );
    notifyListeners();
    try {
      final result = await _getPage(filters, 0);
      if (!_isCatalogCurrent(generation, session)) return;
      _catalogState = _catalogFromPage(result, filters);
    } on Object catch (error) {
      if (!_isCatalogCurrent(generation, session)) return;
      _catalogState = RecipeCatalogState(
        status: RecipeListStatus.error,
        filters: filters,
        items: const [],
        page: 0,
        size: pageSize,
        totalElements: 0,
        totalPages: 0,
        errorMessage: recipeReadErrorMessage(error),
      );
    }
    notifyListeners();
  }

  Future<void> loadMore() async {
    if (_disposed ||
        _catalogState.status != RecipeListStatus.loaded ||
        !_catalogState.hasMore) {
      return;
    }
    final generation = _catalogGeneration;
    final session = _sessionGeneration;
    final previous = _catalogState;
    _catalogState = RecipeCatalogState(
      status: RecipeListStatus.loadingMore,
      filters: previous.filters,
      items: previous.items,
      page: previous.page,
      size: previous.size,
      totalElements: previous.totalElements,
      totalPages: previous.totalPages,
    );
    notifyListeners();
    try {
      final result = await _getPage(previous.filters, previous.page + 1);
      if (!_isCatalogCurrent(generation, session)) return;
      final existingIds = {for (final item in previous.items) item.publicId};
      _catalogState = RecipeCatalogState(
        status: RecipeListStatus.loaded,
        filters: previous.filters,
        items: [
          ...previous.items,
          for (final item in result.content)
            if (existingIds.add(item.publicId)) item,
        ],
        page: result.page,
        size: result.size,
        totalElements: result.totalElements,
        totalPages: result.totalPages,
      );
    } on Object catch (error) {
      if (!_isCatalogCurrent(generation, session)) return;
      _catalogState = RecipeCatalogState(
        status: RecipeListStatus.loaded,
        filters: previous.filters,
        items: previous.items,
        page: previous.page,
        size: previous.size,
        totalElements: previous.totalElements,
        totalPages: previous.totalPages,
        loadMoreErrorMessage: recipeLoadMoreErrorMessage(error),
      );
    }
    notifyListeners();
  }

  Future<RecipePage> _getPage(RecipeFilters filters, int page) =>
      repository.getRecipes(
        query: filters.query,
        mealSlotCode: filters.mealSlotCode,
        tagCode: filters.tagCode,
        maxMinutes: filters.maxMinutes,
        page: page,
        size: pageSize,
      );

  RecipeCatalogState _catalogFromPage(RecipePage page, RecipeFilters filters) =>
      RecipeCatalogState(
        status: RecipeListStatus.loaded,
        filters: filters,
        items: page.content,
        page: page.page,
        size: page.size,
        totalElements: page.totalElements,
        totalPages: page.totalPages,
      );

  Future<void> loadDetail(String publicId) async {
    if (_disposed) return;
    final id = publicId.trim();
    final generation = ++_detailGeneration;
    final session = _sessionGeneration;
    _detailState = RecipeDetailState(
      status: RecipeDetailStatus.loading,
      publicId: id,
    );
    notifyListeners();
    try {
      final detail = await repository.getRecipe(id);
      if (!_isDetailCurrent(generation, session)) return;
      _detailState = RecipeDetailState(
        status: RecipeDetailStatus.loaded,
        publicId: id,
        recipe: detail,
      );
    } on Object catch (error) {
      if (!_isDetailCurrent(generation, session)) return;
      _detailState = RecipeDetailState(
        status: RecipeDetailStatus.error,
        publicId: id,
        errorMessage: recipeReadErrorMessage(error, detail: true),
      );
    }
    notifyListeners();
  }

  Future<void> retryDetail() {
    final id = _detailState.publicId;
    return id == null ? Future<void>.value() : loadDetail(id);
  }

  Future<void> loadReferences() async {
    await Future.wait([loadRecipeTags(), loadMealSlotTypes()]);
  }

  Future<void> loadRecipeTags() async {
    if (_disposed) return;
    final generation = ++_tagsGeneration;
    final session = _sessionGeneration;
    final previous = _tagsState.items;
    _tagsState = RecipeReferenceState(
      status: RecipeReferenceStatus.loading,
      items: previous,
    );
    notifyListeners();
    try {
      final tags = await repository.getRecipeTags();
      if (!_isTagsCurrent(generation, session)) return;
      _tagsState = RecipeReferenceState(
        status: RecipeReferenceStatus.loaded,
        items: tags,
      );
    } on Object catch (error) {
      if (!_isTagsCurrent(generation, session)) return;
      _tagsState = RecipeReferenceState(
        status: RecipeReferenceStatus.error,
        items: previous,
        errorMessage: recipeReadErrorMessage(error),
      );
    }
    notifyListeners();
  }

  Future<void> loadMealSlotTypes() async {
    if (_disposed) return;
    final generation = ++_mealSlotsGeneration;
    final session = _sessionGeneration;
    final previous = _mealSlotsState.items;
    _mealSlotsState = RecipeReferenceState(
      status: RecipeReferenceStatus.loading,
      items: previous,
    );
    notifyListeners();
    try {
      final slots = await repository.getMealSlotTypes();
      if (!_isMealSlotsCurrent(generation, session)) return;
      _mealSlotsState = RecipeReferenceState(
        status: RecipeReferenceStatus.loaded,
        items: slots,
      );
    } on Object catch (error) {
      if (!_isMealSlotsCurrent(generation, session)) return;
      _mealSlotsState = RecipeReferenceState(
        status: RecipeReferenceStatus.error,
        items: previous,
        errorMessage: recipeReadErrorMessage(error),
      );
    }
    notifyListeners();
  }

  Future<void> retryRecipeTags() => loadRecipeTags();
  Future<void> retryMealSlotTypes() => loadMealSlotTypes();

  void resetForSessionChange() {
    if (_disposed) return;
    _sessionGeneration++;
    _catalogGeneration++;
    _detailGeneration++;
    _tagsGeneration++;
    _mealSlotsGeneration++;
    _catalogState = RecipeCatalogState(
      status: RecipeListStatus.initial,
      filters: const RecipeFilters(),
      items: const [],
      page: 0,
      size: pageSize,
      totalElements: 0,
      totalPages: 0,
    );
    _detailState = const RecipeDetailState();
    _tagsState = RecipeReferenceState.initial();
    _mealSlotsState = RecipeReferenceState.initial();
    notifyListeners();
  }

  bool _isCatalogCurrent(int generation, int session) =>
      !_disposed &&
      generation == _catalogGeneration &&
      session == _sessionGeneration;
  bool _isDetailCurrent(int generation, int session) =>
      !_disposed &&
      generation == _detailGeneration &&
      session == _sessionGeneration;
  bool _isTagsCurrent(int generation, int session) =>
      !_disposed &&
      generation == _tagsGeneration &&
      session == _sessionGeneration;
  bool _isMealSlotsCurrent(int generation, int session) =>
      !_disposed &&
      generation == _mealSlotsGeneration &&
      session == _sessionGeneration;

  static String? _normalizedCode(String? code) {
    final value = code?.trim();
    return value == null || value.isEmpty ? null : value;
  }

  @override
  void dispose() {
    _disposed = true;
    _sessionGeneration++;
    super.dispose();
  }
}
