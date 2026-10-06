import 'package:smart_meal_planner/features/recipes/data/recipe_models.dart';

enum RecipeListStatus { initial, loading, loaded, loadingMore, error }

/// One server-side filter set. Empty text is represented consistently.
final class RecipeFilters {
  const RecipeFilters({
    this.query = '',
    this.mealSlotCode,
    this.tagCode,
    this.maxMinutes,
  });

  final String query;
  final String? mealSlotCode;
  final String? tagCode;
  final int? maxMinutes;

  RecipeFilters copyWith({
    String? query,
    Object? mealSlotCode = _unset,
    Object? tagCode = _unset,
    Object? maxMinutes = _unset,
  }) => RecipeFilters(
    query: query ?? this.query,
    mealSlotCode: identical(mealSlotCode, _unset)
        ? this.mealSlotCode
        : mealSlotCode as String?,
    tagCode: identical(tagCode, _unset) ? this.tagCode : tagCode as String?,
    maxMinutes: identical(maxMinutes, _unset)
        ? this.maxMinutes
        : maxMinutes as int?,
  );
}

const _unset = Object();

final class RecipeCatalogState {
  RecipeCatalogState({
    required this.status,
    required this.filters,
    required List<RecipeItem> items,
    required this.page,
    required this.size,
    required this.totalElements,
    required this.totalPages,
    this.errorMessage,
    this.loadMoreErrorMessage,
  }) : items = List.unmodifiable(items);

  factory RecipeCatalogState.initial() => RecipeCatalogState(
    status: RecipeListStatus.initial,
    filters: const RecipeFilters(),
    items: const [],
    page: 0,
    size: 20,
    totalElements: 0,
    totalPages: 0,
  );

  final RecipeListStatus status;
  final RecipeFilters filters;
  final List<RecipeItem> items;
  final int page;
  final int size;
  final int totalElements;
  final int totalPages;
  final String? errorMessage;
  final String? loadMoreErrorMessage;

  bool get hasMore => page + 1 < totalPages;
}

enum RecipeDetailStatus { initial, loading, loaded, error }

final class RecipeDetailState {
  const RecipeDetailState({
    this.status = RecipeDetailStatus.initial,
    this.publicId,
    this.recipe,
    this.errorMessage,
  });

  final RecipeDetailStatus status;
  final String? publicId;
  final RecipeDetail? recipe;
  final String? errorMessage;
}

enum RecipeReferenceStatus { initial, loading, loaded, error }

final class RecipeReferenceState<T> {
  RecipeReferenceState({
    required this.status,
    required List<T> items,
    this.errorMessage,
  }) : items = List.unmodifiable(items);

  factory RecipeReferenceState.initial() => RecipeReferenceState<T>(
    status: RecipeReferenceStatus.initial,
    items: const [],
  );

  final RecipeReferenceStatus status;
  final List<T> items;
  final String? errorMessage;
}
