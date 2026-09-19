import 'package:flutter/foundation.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/features/recipes/data/recipe_models.dart';
import 'package:smart_meal_planner/features/recipes/data/recipe_repository.dart';
import 'package:smart_meal_planner/l10n/app_strings.dart';

enum RecipeListStatus { initial, loading, loaded, loadingMore, error }

final class RecipeListState {
  const RecipeListState({
    required this.status,
    required this.searchQuery,
    required this.items,
    required this.page,
    required this.totalPages,
    required this.totalElements,
    required this.errorMessage,
    required this.loadMoreErrorMessage,
  });

  factory RecipeListState.initial() => const RecipeListState(
    status: RecipeListStatus.initial,
    searchQuery: '',
    items: <RecipeListItem>[],
    page: 0,
    totalPages: 0,
    totalElements: 0,
    errorMessage: null,
    loadMoreErrorMessage: null,
  );

  final RecipeListStatus status;
  final String searchQuery;
  final List<RecipeListItem> items;
  final int page;
  final int totalPages;
  final int totalElements;
  final String? errorMessage;
  final String? loadMoreErrorMessage;

  bool get hasMore => page + 1 < totalPages;
  bool get isInitialLoading =>
      status == RecipeListStatus.loading && items.isEmpty;
  bool get isLoadingMore => status == RecipeListStatus.loadingMore;
}

enum RecipeDetailStatus { initial, loading, loaded, error }

final class RecipeDetailState {
  const RecipeDetailState({
    required this.status,
    required this.publicId,
    required this.item,
    required this.errorMessage,
  });

  factory RecipeDetailState.initial() => const RecipeDetailState(
    status: RecipeDetailStatus.initial,
    publicId: null,
    item: null,
    errorMessage: null,
  );

  final RecipeDetailStatus status;
  final String? publicId;
  final RecipeDetail? item;
  final String? errorMessage;
}

final class RecipeController extends ChangeNotifier {
  RecipeController({required this.repository, this.pageSize = 20})
    : _state = RecipeListState.initial(),
      _detailState = RecipeDetailState.initial();

  final RecipeRepository repository;
  final int pageSize;
  RecipeListState _state;
  RecipeDetailState _detailState;
  bool _disposed = false;
  int _generation = 0;
  int _detailGeneration = 0;

  RecipeListState get state => _state;
  RecipeDetailState get detailState => _detailState;

  Future<void> loadInitial() {
    if (_disposed || _state.status == RecipeListStatus.loading) {
      return Future<void>.value();
    }
    return _loadFirstPage(_state.searchQuery);
  }

  Future<void> reload() => _loadFirstPage(_state.searchQuery);

  Future<void> search(String query) => _loadFirstPage(query);

  Future<void> loadMore() async {
    if (_disposed ||
        _state.status != RecipeListStatus.loaded ||
        !_state.hasMore) {
      return;
    }
    final generation = _generation;
    final nextPage = _state.page + 1;
    _state = RecipeListState(
      status: RecipeListStatus.loadingMore,
      searchQuery: _state.searchQuery,
      items: _state.items,
      page: _state.page,
      totalPages: _state.totalPages,
      totalElements: _state.totalElements,
      errorMessage: null,
      loadMoreErrorMessage: null,
    );
    _notify();
    try {
      final result = await repository.getRecipes(
        query: _state.searchQuery,
        page: nextPage,
        size: pageSize,
      );
      if (!_isCurrent(generation)) return;
      final ids = {for (final item in _state.items) item.publicId};
      final additions = [
        for (final item in result.content)
          if (ids.add(item.publicId)) item,
      ];
      _state = RecipeListState(
        status: RecipeListStatus.loaded,
        searchQuery: _state.searchQuery,
        items: [..._state.items, ...additions],
        page: result.page,
        totalPages: result.totalPages,
        totalElements: result.totalElements,
        errorMessage: null,
        loadMoreErrorMessage: null,
      );
    } on Object catch (error) {
      if (!_isCurrent(generation)) return;
      _state = RecipeListState(
        status: RecipeListStatus.loaded,
        searchQuery: _state.searchQuery,
        items: _state.items,
        page: _state.page,
        totalPages: _state.totalPages,
        totalElements: _state.totalElements,
        errorMessage: null,
        loadMoreErrorMessage: _recipeErrorMessage(error),
      );
    }
    if (_isCurrent(generation)) _notify();
  }

  Future<void> loadDetail(String publicId) async {
    final value = publicId.trim();
    if (_disposed) return;
    final generation = ++_detailGeneration;
    _detailState = RecipeDetailState(
      status: RecipeDetailStatus.loading,
      publicId: value,
      item: null,
      errorMessage: null,
    );
    _notify();
    try {
      final item = await repository.getRecipe(value);
      if (!_isDetailCurrent(generation)) return;
      _detailState = RecipeDetailState(
        status: RecipeDetailStatus.loaded,
        publicId: value,
        item: item,
        errorMessage: null,
      );
    } on Object catch (error) {
      if (!_isDetailCurrent(generation)) return;
      _detailState = RecipeDetailState(
        status: RecipeDetailStatus.error,
        publicId: value,
        item: null,
        errorMessage: _recipeErrorMessage(error),
      );
    }
    if (_isDetailCurrent(generation)) _notify();
  }

  Future<void> reloadDetail() {
    final publicId = _detailState.publicId;
    return publicId == null ? Future<void>.value() : loadDetail(publicId);
  }

  void resetForSessionChange() {
    if (_disposed) return;
    _generation++;
    _detailGeneration++;
    _state = RecipeListState.initial();
    _detailState = RecipeDetailState.initial();
    _notify();
  }

  Future<void> _loadFirstPage(String query) async {
    if (_disposed) return;
    final generation = ++_generation;
    final normalizedQuery = query.trim();
    _state = RecipeListState(
      status: RecipeListStatus.loading,
      searchQuery: normalizedQuery,
      items: const <RecipeListItem>[],
      page: 0,
      totalPages: 0,
      totalElements: 0,
      errorMessage: null,
      loadMoreErrorMessage: null,
    );
    _notify();
    try {
      final result = await repository.getRecipes(
        query: normalizedQuery,
        page: 0,
        size: pageSize,
      );
      if (!_isCurrent(generation)) return;
      _state = RecipeListState(
        status: RecipeListStatus.loaded,
        searchQuery: normalizedQuery,
        items: result.content,
        page: result.page,
        totalPages: result.totalPages,
        totalElements: result.totalElements,
        errorMessage: null,
        loadMoreErrorMessage: null,
      );
    } on Object catch (error) {
      if (!_isCurrent(generation)) return;
      _state = RecipeListState(
        status: RecipeListStatus.error,
        searchQuery: normalizedQuery,
        items: const <RecipeListItem>[],
        page: 0,
        totalPages: 0,
        totalElements: 0,
        errorMessage: _recipeErrorMessage(error),
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

String _recipeErrorMessage(Object error) {
  if (error is ApiTransportException) {
    return AppStrings.unableToReachService;
  }
  if (error is ApiHttpException && error.statusCode >= 500) {
    return AppStrings.serviceUnavailable;
  }
  return AppStrings.recipeRequestFailed;
}
