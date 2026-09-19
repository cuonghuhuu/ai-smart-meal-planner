import 'package:flutter/foundation.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/features/admin/recipes/data/admin_recipe_models.dart';
import 'package:smart_meal_planner/features/admin/recipes/data/admin_recipe_repository.dart';
import 'package:smart_meal_planner/features/catalog/data/catalog_models.dart';
import 'package:smart_meal_planner/features/catalog/data/catalog_repository.dart';
import 'package:smart_meal_planner/features/recipes/data/recipe_models.dart';
import 'package:smart_meal_planner/l10n/app_strings.dart';

enum AdminRecipeListStatus { initial, loading, loaded, loadingMore, error }

final class AdminRecipeListState {
  const AdminRecipeListState({
    required this.status,
    required this.searchQuery,
    required this.statusFilter,
    required this.items,
    required this.page,
    required this.totalElements,
    required this.totalPages,
    required this.errorMessage,
    required this.loadMoreErrorMessage,
  });

  factory AdminRecipeListState.initial() => const AdminRecipeListState(
    status: AdminRecipeListStatus.initial,
    searchQuery: '',
    statusFilter: null,
    items: <AdminRecipeItem>[],
    page: 0,
    totalElements: 0,
    totalPages: 0,
    errorMessage: null,
    loadMoreErrorMessage: null,
  );

  final AdminRecipeListStatus status;
  final String searchQuery;
  final String? statusFilter;
  final List<AdminRecipeItem> items;
  final int page;
  final int totalElements;
  final int totalPages;
  final String? errorMessage;
  final String? loadMoreErrorMessage;

  bool get hasMore => page + 1 < totalPages;
  bool get isInitialLoading =>
      status == AdminRecipeListStatus.loading && items.isEmpty;
  bool get isLoadingMore => status == AdminRecipeListStatus.loadingMore;
}

enum AdminRecipeDetailStatus { initial, loading, loaded, error }

final class AdminRecipeDetailState {
  const AdminRecipeDetailState({
    required this.status,
    required this.publicId,
    required this.item,
    required this.errorMessage,
  });

  factory AdminRecipeDetailState.initial() => const AdminRecipeDetailState(
    status: AdminRecipeDetailStatus.initial,
    publicId: null,
    item: null,
    errorMessage: null,
  );

  final AdminRecipeDetailStatus status;
  final String? publicId;
  final AdminRecipeDetail? item;
  final String? errorMessage;
}

final class AdminRecipeController extends ChangeNotifier {
  AdminRecipeController({
    required this.repository,
    required this.catalogRepository,
    this.pageSize = 20,
  }) : _state = AdminRecipeListState.initial(),
       _detailState = AdminRecipeDetailState.initial();

  final AdminRecipeRepository repository;
  final CatalogRepository catalogRepository;
  final int pageSize;

  AdminRecipeListState _state;
  AdminRecipeDetailState _detailState;
  List<RecipeTag> _tags = const <RecipeTag>[];
  List<RecipeMealSlot> _mealSlots = const <RecipeMealSlot>[];
  bool _referencesLoaded = false;
  bool _referencesLoading = false;
  String? _referencesErrorMessage;
  bool _disposed = false;
  bool _saving = false;
  String? _mutatingPublicId;
  String? _actionErrorMessage;
  int _generation = 0;
  int _detailGeneration = 0;
  int _sessionGeneration = 0;

  AdminRecipeListState get state => _state;
  AdminRecipeDetailState get detailState => _detailState;
  List<RecipeTag> get tags => _tags;
  List<RecipeMealSlot> get mealSlots => _mealSlots;
  bool get referencesLoaded => _referencesLoaded;
  bool get referencesLoading => _referencesLoading;
  String? get referencesErrorMessage => _referencesErrorMessage;
  bool get saving => _saving;
  String? get mutatingPublicId => _mutatingPublicId;
  String? get actionErrorMessage => _actionErrorMessage;

  Future<void> loadInitial() {
    if (_disposed || _state.status == AdminRecipeListStatus.loading) {
      return Future<void>.value();
    }
    return _loadFirstPage(_state.searchQuery, _state.statusFilter);
  }

  Future<void> reload() =>
      _loadFirstPage(_state.searchQuery, _state.statusFilter);

  Future<void> search(String query) =>
      _loadFirstPage(query, _state.statusFilter);

  Future<void> setStatus(String? status) =>
      _loadFirstPage(_state.searchQuery, status);

  Future<void> loadMore() async {
    if (_disposed ||
        _state.status != AdminRecipeListStatus.loaded ||
        !_state.hasMore) {
      return;
    }
    final generation = _generation;
    final nextPage = _state.page + 1;
    _state = AdminRecipeListState(
      status: AdminRecipeListStatus.loadingMore,
      searchQuery: _state.searchQuery,
      statusFilter: _state.statusFilter,
      items: _state.items,
      page: _state.page,
      totalElements: _state.totalElements,
      totalPages: _state.totalPages,
      errorMessage: null,
      loadMoreErrorMessage: null,
    );
    _notify();
    try {
      final result = await repository.getRecipes(
        query: _state.searchQuery,
        status: _state.statusFilter,
        page: nextPage,
        size: pageSize,
      );
      if (!_isCurrent(generation)) return;
      final ids = {for (final item in _state.items) item.publicId};
      final additions = [
        for (final item in result.content)
          if (ids.add(item.publicId)) item,
      ];
      _state = AdminRecipeListState(
        status: AdminRecipeListStatus.loaded,
        searchQuery: _state.searchQuery,
        statusFilter: _state.statusFilter,
        items: [..._state.items, ...additions],
        page: result.page,
        totalElements: result.totalElements,
        totalPages: result.totalPages,
        errorMessage: null,
        loadMoreErrorMessage: null,
      );
    } on Object catch (error) {
      if (!_isCurrent(generation)) return;
      _state = AdminRecipeListState(
        status: AdminRecipeListStatus.loaded,
        searchQuery: _state.searchQuery,
        statusFilter: _state.statusFilter,
        items: _state.items,
        page: _state.page,
        totalElements: _state.totalElements,
        totalPages: _state.totalPages,
        errorMessage: null,
        loadMoreErrorMessage: _adminRecipeErrorMessage(error),
      );
    }
    if (_isCurrent(generation)) _notify();
  }

  Future<void> loadReferences() async {
    if (_disposed || _referencesLoaded || _referencesLoading) return;
    final sessionGeneration = _sessionGeneration;
    _referencesLoading = true;
    _referencesErrorMessage = null;
    _notify();
    try {
      final loadedTags = await repository.getTags();
      final loadedMealSlots = await repository.getMealSlots();
      if (_disposed || sessionGeneration != _sessionGeneration) return;
      _tags = loadedTags;
      _mealSlots = loadedMealSlots;
      _referencesLoaded = true;
    } on Object catch (error) {
      if (!_disposed && sessionGeneration == _sessionGeneration) {
        _referencesErrorMessage = _adminRecipeErrorMessage(error);
      }
    } finally {
      if (!_disposed) {
        _referencesLoading = false;
        _notify();
      }
    }
  }

  Future<IngredientCatalogPage> searchIngredients(String query) =>
      catalogRepository.getIngredients(query: query.trim(), page: 0, size: 20);

  void prepareCreate() {
    if (_disposed) return;
    _detailGeneration++;
    _detailState = AdminRecipeDetailState.initial();
    _actionErrorMessage = null;
    _notify();
  }

  Future<void> loadDetail(String publicId) async {
    if (_disposed) return;
    final value = publicId.trim();
    final generation = ++_detailGeneration;
    _detailState = AdminRecipeDetailState(
      status: AdminRecipeDetailStatus.loading,
      publicId: value,
      item: null,
      errorMessage: null,
    );
    _notify();
    loadReferences();
    try {
      final item = await repository.getRecipe(value);
      if (!_isDetailCurrent(generation)) return;
      _detailState = AdminRecipeDetailState(
        status: AdminRecipeDetailStatus.loaded,
        publicId: value,
        item: item,
        errorMessage: null,
      );
    } on Object catch (error) {
      if (!_isDetailCurrent(generation)) return;
      _detailState = AdminRecipeDetailState(
        status: AdminRecipeDetailStatus.error,
        publicId: value,
        item: null,
        errorMessage: _adminRecipeErrorMessage(error),
      );
    }
    if (_isDetailCurrent(generation)) _notify();
  }

  Future<AdminRecipeDetail?> save(
    AdminRecipeUpsertRequest request, {
    String? publicId,
  }) async {
    if (_disposed || _saving) return null;
    final generation = ++_generation;
    _saving = true;
    _actionErrorMessage = null;
    _notify();
    try {
      final value = publicId?.trim();
      final item = value == null || value.isEmpty
          ? await repository.create(request)
          : await repository.update(value, request);
      if (!_isCurrent(generation)) return null;
      _detailState = AdminRecipeDetailState(
        status: AdminRecipeDetailStatus.loaded,
        publicId: item.publicId,
        item: item,
        errorMessage: null,
      );
      await _loadFirstPage(_state.searchQuery, _state.statusFilter);
      return item;
    } on Object catch (error) {
      if (!_disposed) {
        _actionErrorMessage = _adminRecipeErrorMessage(error);
        _notify();
      }
      return null;
    } finally {
      if (!_disposed) {
        _saving = false;
        _notify();
      }
    }
  }

  Future<AdminRecipeDetail?> publish(String publicId) =>
      _runLifecycle(publicId, repository.publish);

  Future<AdminRecipeDetail?> archive(String publicId) =>
      _runLifecycle(publicId, repository.archive);

  void resetForSessionChange() {
    if (_disposed) return;
    _generation++;
    _detailGeneration++;
    _sessionGeneration++;
    _state = AdminRecipeListState.initial();
    _detailState = AdminRecipeDetailState.initial();
    _tags = const <RecipeTag>[];
    _mealSlots = const <RecipeMealSlot>[];
    _referencesLoaded = false;
    _referencesLoading = false;
    _referencesErrorMessage = null;
    _saving = false;
    _mutatingPublicId = null;
    _actionErrorMessage = null;
    _notify();
  }

  Future<AdminRecipeDetail?> _runLifecycle(
    String publicId,
    Future<AdminRecipeDetail> Function(String) operation,
  ) async {
    if (_disposed || _mutatingPublicId != null) return null;
    final generation = ++_generation;
    _mutatingPublicId = publicId;
    _actionErrorMessage = null;
    _notify();
    try {
      final item = await operation(publicId);
      if (!_isCurrent(generation)) return null;
      _detailState = AdminRecipeDetailState(
        status: AdminRecipeDetailStatus.loaded,
        publicId: item.publicId,
        item: item,
        errorMessage: null,
      );
      await _loadFirstPage(_state.searchQuery, _state.statusFilter);
      return item;
    } on Object catch (error) {
      if (!_disposed) {
        _actionErrorMessage = _adminRecipeErrorMessage(error);
        _notify();
      }
      return null;
    } finally {
      if (!_disposed) {
        _mutatingPublicId = null;
        _notify();
      }
    }
  }

  Future<void> _loadFirstPage(String query, String? status) async {
    if (_disposed) return;
    final generation = ++_generation;
    final normalizedQuery = query.trim();
    final normalizedStatus = status?.trim();
    _state = AdminRecipeListState(
      status: AdminRecipeListStatus.loading,
      searchQuery: normalizedQuery,
      statusFilter: normalizedStatus == null || normalizedStatus.isEmpty
          ? null
          : normalizedStatus,
      items: const <AdminRecipeItem>[],
      page: 0,
      totalElements: 0,
      totalPages: 0,
      errorMessage: null,
      loadMoreErrorMessage: null,
    );
    _notify();
    try {
      final result = await repository.getRecipes(
        query: normalizedQuery,
        status: _state.statusFilter,
        page: 0,
        size: pageSize,
      );
      if (!_isCurrent(generation)) return;
      _state = AdminRecipeListState(
        status: AdminRecipeListStatus.loaded,
        searchQuery: normalizedQuery,
        statusFilter: _state.statusFilter,
        items: result.content,
        page: result.page,
        totalElements: result.totalElements,
        totalPages: result.totalPages,
        errorMessage: null,
        loadMoreErrorMessage: null,
      );
    } on Object catch (error) {
      if (!_isCurrent(generation)) return;
      _state = AdminRecipeListState(
        status: AdminRecipeListStatus.error,
        searchQuery: normalizedQuery,
        statusFilter: _state.statusFilter,
        items: const <AdminRecipeItem>[],
        page: 0,
        totalElements: 0,
        totalPages: 0,
        errorMessage: _adminRecipeErrorMessage(error),
        loadMoreErrorMessage: null,
      );
    }
    if (_isCurrent(generation)) _notify();
  }

  bool _isCurrent(int generation) =>
      !_disposed && generation == _generation;

  bool _isDetailCurrent(int generation) =>
      !_disposed && generation == _detailGeneration;

  void _notify() {
    if (!_disposed) notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    _generation++;
    _detailGeneration++;
    super.dispose();
  }
}

String _adminRecipeErrorMessage(Object error) {
  if (error is ApiTransportException) return AppStrings.unableToReachService;
  if (error is ApiHttpException) {
    if (error.statusCode == 403) return AppStrings.adminForbidden;
    if (error.statusCode == 404) return AppStrings.adminNotFound;
    if (error.statusCode == 409) return AppStrings.adminConflict;
    if (error.statusCode >= 500) return AppStrings.serviceUnavailable;
  }
  return AppStrings.adminRecipeRequestFailed;
}
