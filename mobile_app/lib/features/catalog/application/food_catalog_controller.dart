import 'package:flutter/foundation.dart';
import 'package:smart_meal_planner/features/catalog/application/catalog_error_messages.dart';
import 'package:smart_meal_planner/features/catalog/data/catalog_models.dart';
import 'package:smart_meal_planner/features/catalog/data/catalog_repository.dart';

final class FoodCatalogController extends ChangeNotifier {
  FoodCatalogController({required this.repository, this.pageSize = 20})
    : _state = CatalogListState<FoodCatalogItem>.initial(),
      _detailState = CatalogDetailState<FoodCatalogDetail>.initial();

  final CatalogRepository repository;
  final int pageSize;
  CatalogListState<FoodCatalogItem> _state;
  CatalogDetailState<FoodCatalogDetail> _detailState;
  Future<List<CatalogCategory>>? _categoriesFuture;
  bool _categoriesLoaded = false;
  bool _disposed = false;
  int _generation = 0;
  int _detailGeneration = 0;

  CatalogListState<FoodCatalogItem> get state => _state;
  CatalogDetailState<FoodCatalogDetail> get detailState => _detailState;

  Future<void> loadInitial() {
    if (_state.status == CatalogListStatus.loading || _disposed) {
      return Future<void>.value();
    }
    return _loadFirstPage(
      searchQuery: _state.searchQuery,
      categoryCode: _state.selectedCategoryCode,
    );
  }

  Future<void> reload() => _loadFirstPage(
    searchQuery: _state.searchQuery,
    categoryCode: _state.selectedCategoryCode,
  );

  Future<void> search(String query) => _loadFirstPage(
    searchQuery: query,
    categoryCode: _state.selectedCategoryCode,
  );

  Future<void> setCategory(String? categoryCode) => _loadFirstPage(
    searchQuery: _state.searchQuery,
    categoryCode: categoryCode,
  );

  Future<void> loadMore() async {
    if (_disposed ||
        _state.status != CatalogListStatus.loaded ||
        !_state.hasMore) {
      return;
    }
    final generation = _generation;
    final nextPage = _state.page + 1;
    _state = _state.copyWith(
      status: CatalogListStatus.loadingMore,
      loadMoreErrorMessage: null,
    );
    _notify();
    try {
      final result = await repository.getFoods(
        query: _state.searchQuery,
        categoryCode: _state.selectedCategoryCode,
        page: nextPage,
        size: pageSize,
      );
      if (!_isCurrent(generation)) {
        return;
      }
      final existingIds = {for (final item in _state.items) item.publicId};
      final additions = [
        for (final item in result.content)
          if (existingIds.add(item.publicId)) item,
      ];
      _state = _state.copyWith(
        status: CatalogListStatus.loaded,
        items: [..._state.items, ...additions],
        page: result.page,
        totalElements: result.totalElements,
        totalPages: result.totalPages,
        errorMessage: null,
        loadMoreErrorMessage: null,
      );
    } on Object catch (error) {
      if (!_isCurrent(generation)) {
        return;
      }
      _state = _state.copyWith(
        status: CatalogListStatus.loaded,
        loadMoreErrorMessage: catalogLoadMoreErrorMessage(error),
      );
    }
    if (_isCurrent(generation)) {
      _notify();
    }
  }

  Future<void> loadDetail(String publicId) async {
    final value = publicId.trim();
    if (_disposed) {
      return;
    }
    final generation = ++_detailGeneration;
    _detailState = _detailState.copyWith(
      status: CatalogDetailStatus.loading,
      publicId: value,
      item: null,
      errorMessage: null,
    );
    _notify();
    try {
      final item = await repository.getFood(value);
      if (!_isDetailCurrent(generation)) {
        return;
      }
      _detailState = _detailState.copyWith(
        status: CatalogDetailStatus.loaded,
        item: item,
        errorMessage: null,
      );
    } on Object catch (error) {
      if (!_isDetailCurrent(generation)) {
        return;
      }
      _detailState = _detailState.copyWith(
        status: CatalogDetailStatus.error,
        item: null,
        errorMessage: catalogLoadErrorMessage(error, detail: true),
      );
    }
    if (_isDetailCurrent(generation)) {
      _notify();
    }
  }

  Future<void> reloadDetail() {
    final publicId = _detailState.publicId;
    return publicId == null ? Future<void>.value() : loadDetail(publicId);
  }

  void resetForSessionChange() {
    if (_disposed) {
      return;
    }
    _generation++;
    _detailGeneration++;
    _state = CatalogListState<FoodCatalogItem>.initial();
    _detailState = CatalogDetailState<FoodCatalogDetail>.initial();
    _categoriesLoaded = false;
    _notify();
  }

  Future<void> _loadFirstPage({
    required String searchQuery,
    required String? categoryCode,
  }) async {
    if (_disposed) {
      return;
    }
    final generation = ++_generation;
    final normalizedQuery = searchQuery.trim();
    final normalizedCategory = categoryCode?.trim();
    _state = _state.copyWith(
      status: CatalogListStatus.loading,
      searchQuery: normalizedQuery,
      selectedCategoryCode: normalizedCategory == null || normalizedCategory.isEmpty
          ? null
          : normalizedCategory,
      items: <FoodCatalogItem>[],
      page: 0,
      totalElements: 0,
      totalPages: 0,
      errorMessage: null,
      loadMoreErrorMessage: null,
    );
    _notify();
    try {
      final result = await Future.wait<Object?>([
        _loadCategoriesOnce(),
        repository.getFoods(
          query: normalizedQuery,
          categoryCode: normalizedCategory,
          page: 0,
          size: pageSize,
        ),
      ]);
      if (!_isCurrent(generation)) {
        return;
      }
      final page = result[1] as FoodCatalogPage;
      _state = _state.copyWith(
        status: CatalogListStatus.loaded,
        items: page.content,
        page: page.page,
        totalElements: page.totalElements,
        totalPages: page.totalPages,
        errorMessage: null,
        loadMoreErrorMessage: null,
      );
    } on Object catch (error) {
      if (!_isCurrent(generation)) {
        return;
      }
      _state = _state.copyWith(
        status: CatalogListStatus.error,
        items: <FoodCatalogItem>[],
        errorMessage: catalogLoadErrorMessage(error),
        loadMoreErrorMessage: null,
      );
    }
    if (_isCurrent(generation)) {
      _notify();
    }
  }

  Future<List<CatalogCategory>> _loadCategoriesOnce() {
    if (_categoriesLoaded) {
      return Future.value(_state.categories);
    }
    final active = _categoriesFuture;
    if (active != null) {
      return active;
    }
    final future = _fetchCategories();
    _categoriesFuture = future;
    return future;
  }

  Future<List<CatalogCategory>> _fetchCategories() async {
    try {
      final categories = await repository.getFoodCategories();
      _categoriesLoaded = true;
      if (!_disposed) {
        _state = _state.copyWith(categories: categories);
        _notify();
      }
      return categories;
    } finally {
      _categoriesFuture = null;
    }
  }

  bool _isCurrent(int generation) => !_disposed && generation == _generation;

  bool _isDetailCurrent(int generation) =>
      !_disposed && generation == _detailGeneration;

  void _notify() {
    if (!_disposed) {
      notifyListeners();
    }
  }

  @override
  void dispose() {
    _disposed = true;
    _generation++;
    _detailGeneration++;
    super.dispose();
  }
}
