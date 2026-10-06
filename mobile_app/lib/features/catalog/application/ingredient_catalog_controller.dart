import 'dart:typed_data';

import 'package:flutter/foundation.dart';
import 'package:smart_meal_planner/features/catalog/application/catalog_error_messages.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/features/catalog/data/catalog_models.dart';
import 'package:smart_meal_planner/features/catalog/data/catalog_repository.dart';
import 'package:smart_meal_planner/features/catalog/data/ingredient_recognition_repository.dart';

final class IngredientCatalogController extends ChangeNotifier {
  IngredientCatalogController({
    required this.repository,
    this.recognitionRepository,
    this.pageSize = 20,
  }) : _state = CatalogListState<IngredientCatalogItem>.initial(),
       _detailState = CatalogDetailState<IngredientCatalogDetail>.initial(),
       _recognitionState = IngredientRecognitionState.idle();

  final CatalogRepository repository;
  final IngredientRecognitionRepository? recognitionRepository;
  final int pageSize;
  CatalogListState<IngredientCatalogItem> _state;
  CatalogDetailState<IngredientCatalogDetail> _detailState;
  IngredientRecognitionState _recognitionState;
  Future<List<CatalogCategory>>? _categoriesFuture;
  bool _categoriesLoaded = false;
  bool _disposed = false;
  int _generation = 0;
  int _detailGeneration = 0;

  CatalogListState<IngredientCatalogItem> get state => _state;
  CatalogDetailState<IngredientCatalogDetail> get detailState => _detailState;
  IngredientRecognitionState get recognitionState => _recognitionState;

  Future<void> recognizeImage(
    Uint8List imageBytes,
    String contentType,
  ) async {
    final recognizer = recognitionRepository;
    if (_disposed || recognizer == null) {
      return;
    }
    _recognitionState = IngredientRecognitionState.loading();
    _notify();
    try {
      final result = await recognizer.detect(
        imageBytes: imageBytes,
        contentType: contentType,
      );
      if (_disposed) {
        return;
      }
      _recognitionState = IngredientRecognitionState.loaded(result);
    } on Object catch (error) {
      if (_disposed) {
        return;
      }
      _recognitionState = IngredientRecognitionState.error(
        _recognitionErrorMessage(error),
      );
    }
    _notify();
  }

  void clearRecognition() {
    if (_disposed) {
      return;
    }
    _recognitionState = IngredientRecognitionState.idle();
    _notify();
  }

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
      final result = await repository.getIngredients(
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
      final item = await repository.getIngredient(value);
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
    _state = CatalogListState<IngredientCatalogItem>.initial();
    _detailState = CatalogDetailState<IngredientCatalogDetail>.initial();
    _recognitionState = IngredientRecognitionState.idle();
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
      items: <IngredientCatalogItem>[],
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
        repository.getIngredients(
          query: normalizedQuery,
          categoryCode: normalizedCategory,
          page: 0,
          size: pageSize,
        ),
      ]);
      if (!_isCurrent(generation)) {
        return;
      }
      final page = result[1] as IngredientCatalogPage;
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
        items: <IngredientCatalogItem>[],
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


enum IngredientRecognitionStatus { idle, loading, loaded, error }

final class IngredientRecognitionState {
  const IngredientRecognitionState._({
    required this.status,
    this.result,
    this.errorMessage,
  });

  factory IngredientRecognitionState.idle() =>
      const IngredientRecognitionState._(status: IngredientRecognitionStatus.idle);

  factory IngredientRecognitionState.loading() =>
      const IngredientRecognitionState._(status: IngredientRecognitionStatus.loading);

  factory IngredientRecognitionState.loaded(IngredientRecognitionResult result) =>
      IngredientRecognitionState._(
        status: IngredientRecognitionStatus.loaded,
        result: result,
      );

  factory IngredientRecognitionState.error(String message) =>
      IngredientRecognitionState._(
        status: IngredientRecognitionStatus.error,
        errorMessage: message,
      );

  final IngredientRecognitionStatus status;
  final IngredientRecognitionResult? result;
  final String? errorMessage;
}

String _recognitionErrorMessage(Object error) {
  if (error is ApiTransportException) {
    return error.kind == ApiTransportFailureKind.timeout
        ? 'Nhận diện AI mất quá nhiều thời gian. Vui lòng thử lại.'
        : 'Không thể kết nối tới dịch vụ nhận diện AI.';
  }
  if (error is ApiHttpException) {
    return switch (error.statusCode) {
      413 => 'Ảnh quá lớn. Vui lòng chọn ảnh nhỏ hơn.',
      415 || 422 => 'Ảnh không hợp lệ hoặc định dạng chưa được hỗ trợ.',
      502 || 503 || 504 => 'Dịch vụ nhận diện AI hiện không khả dụng.',
      _ => 'Không thể nhận diện ảnh. Vui lòng thử lại.',
    };
  }
  return 'Không thể nhận diện ảnh. Vui lòng thử lại.';
}
