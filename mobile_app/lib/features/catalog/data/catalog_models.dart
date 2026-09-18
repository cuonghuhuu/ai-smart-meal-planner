import 'package:smart_meal_planner/core/api/api_exception.dart';

final class CatalogJson {
  const CatalogJson._();

  static Map<String, dynamic> map(Object? value) {
    if (value is Map<String, dynamic>) {
      return value;
    }
    if (value is Map) {
      return value.map(
        (key, value) => MapEntry(key.toString(), value),
      );
    }
    throw const ApiResponseFormatException();
  }

  static String requiredString(Map<String, dynamic> json, String key) {
    final value = json[key];
    if (value is String && value.isNotEmpty) {
      return value;
    }
    throw const ApiResponseFormatException();
  }

  static String? optionalString(Map<String, dynamic> json, String key) {
    final value = json[key];
    if (value == null) {
      return null;
    }
    if (value is String) {
      return value;
    }
    throw const ApiResponseFormatException();
  }

  static double requiredDouble(Map<String, dynamic> json, String key) {
    final value = json[key];
    if (value is num && value.toDouble().isFinite) {
      return value.toDouble();
    }
    throw const ApiResponseFormatException();
  }

  static double? optionalDouble(Map<String, dynamic> json, String key) {
    final value = json[key];
    if (value == null) {
      return null;
    }
    if (value is num && value.toDouble().isFinite) {
      return value.toDouble();
    }
    throw const ApiResponseFormatException();
  }

  static int requiredInt(Map<String, dynamic> json, String key) {
    final value = json[key];
    if (value is num && value.isFinite && value == value.toInt()) {
      return value.toInt();
    }
    throw const ApiResponseFormatException();
  }

  static int? optionalInt(Map<String, dynamic> json, String key) {
    final value = json[key];
    if (value == null) {
      return null;
    }
    if (value is num && value.isFinite && value == value.toInt()) {
      return value.toInt();
    }
    throw const ApiResponseFormatException();
  }

  static bool requiredBool(Map<String, dynamic> json, String key) {
    final value = json[key];
    if (value is bool) {
      return value;
    }
    throw const ApiResponseFormatException();
  }

  static List<T> list<T>(Object? value, T Function(Object? value) parse) {
    if (value == null) {
      return const [];
    }
    if (value is! List) {
      throw const ApiResponseFormatException();
    }
    return List.unmodifiable(value.map(parse));
  }

  static List<String> strings(Object? value) {
    return list(value, (item) {
      if (item is String) {
        return item;
      }
      throw const ApiResponseFormatException();
    });
  }
}

final class CatalogCategory {
  const CatalogCategory({
    required this.code,
    required this.displayName,
    required this.parentCategoryCode,
    required this.description,
  });

  factory CatalogCategory.fromJson(Object? value) {
    final json = CatalogJson.map(value);
    return CatalogCategory(
      code: CatalogJson.requiredString(json, 'code'),
      displayName: CatalogJson.requiredString(json, 'displayName'),
      parentCategoryCode: CatalogJson.optionalString(
        json,
        'parentCategoryCode',
      ),
      description: CatalogJson.optionalString(json, 'description'),
    );
  }

  final String code;
  final String displayName;
  final String? parentCategoryCode;
  final String? description;
}

final class FoodCatalogItem {
  const FoodCatalogItem({
    required this.publicId,
    required this.code,
    required this.displayName,
    required this.brand,
    required this.category,
  });

  factory FoodCatalogItem.fromJson(Object? value) {
    final json = CatalogJson.map(value);
    return FoodCatalogItem(
      publicId: CatalogJson.requiredString(json, 'publicId'),
      code: CatalogJson.requiredString(json, 'code'),
      displayName: CatalogJson.requiredString(json, 'displayName'),
      brand: CatalogJson.optionalString(json, 'brand'),
      category: _optionalCategory(json['category']),
    );
  }

  final String publicId;
  final String code;
  final String displayName;
  final String? brand;
  final CatalogCategory? category;
}

final class FoodNutrient {
  const FoodNutrient({
    required this.nutrientCode,
    required this.nutrientDisplayName,
    required this.amount,
    required this.unitCode,
    required this.unitDisplayName,
    required this.dataQuality,
  });

  factory FoodNutrient.fromJson(Object? value) {
    final json = CatalogJson.map(value);
    return FoodNutrient(
      nutrientCode: CatalogJson.requiredString(json, 'nutrientCode'),
      nutrientDisplayName: CatalogJson.requiredString(
        json,
        'nutrientDisplayName',
      ),
      amount: CatalogJson.requiredDouble(json, 'amount'),
      unitCode: CatalogJson.requiredString(json, 'unitCode'),
      unitDisplayName: CatalogJson.requiredString(json, 'unitDisplayName'),
      dataQuality: CatalogJson.optionalString(json, 'dataQuality'),
    );
  }

  final String nutrientCode;
  final String nutrientDisplayName;
  final double amount;
  final String unitCode;
  final String unitDisplayName;
  final String? dataQuality;
}

final class FoodServing {
  const FoodServing({
    required this.displayName,
    required this.quantity,
    required this.unitCode,
    required this.unitDisplayName,
    required this.gramWeight,
    required this.milliliters,
    required this.defaultServing,
  });

  factory FoodServing.fromJson(Object? value) {
    final json = CatalogJson.map(value);
    return FoodServing(
      displayName: CatalogJson.requiredString(json, 'displayName'),
      quantity: CatalogJson.optionalDouble(json, 'quantity'),
      unitCode: CatalogJson.requiredString(json, 'unitCode'),
      unitDisplayName: CatalogJson.requiredString(json, 'unitDisplayName'),
      gramWeight: CatalogJson.optionalDouble(json, 'gramWeight'),
      milliliters: CatalogJson.optionalDouble(json, 'milliliters'),
      defaultServing: CatalogJson.requiredBool(json, 'defaultServing'),
    );
  }

  final String displayName;
  final double? quantity;
  final String unitCode;
  final String unitDisplayName;
  final double? gramWeight;
  final double? milliliters;
  final bool defaultServing;
}

final class FoodCatalogDetail {
  const FoodCatalogDetail({
    required this.publicId,
    required this.code,
    required this.displayName,
    required this.brand,
    required this.category,
    required this.description,
    required this.nutritionBasis,
    required this.densityGPerMl,
    required this.source,
    required this.sourceReference,
    required this.revision,
    required this.nutrients,
    required this.servings,
  });

  factory FoodCatalogDetail.fromJson(Object? value) {
    final json = CatalogJson.map(value);
    return FoodCatalogDetail(
      publicId: CatalogJson.requiredString(json, 'publicId'),
      code: CatalogJson.requiredString(json, 'code'),
      displayName: CatalogJson.requiredString(json, 'displayName'),
      brand: CatalogJson.optionalString(json, 'brand'),
      category: _optionalCategory(json['category']),
      description: CatalogJson.optionalString(json, 'description'),
      nutritionBasis: CatalogJson.requiredString(json, 'nutritionBasis'),
      densityGPerMl: CatalogJson.optionalDouble(json, 'densityGPerMl'),
      source: CatalogJson.requiredString(json, 'source'),
      sourceReference: CatalogJson.optionalString(json, 'sourceReference'),
      revision: CatalogJson.optionalInt(json, 'revision'),
      nutrients: CatalogJson.list(json['nutrients'], FoodNutrient.fromJson),
      servings: CatalogJson.list(json['servings'], FoodServing.fromJson),
    );
  }

  final String publicId;
  final String code;
  final String displayName;
  final String? brand;
  final CatalogCategory? category;
  final String? description;
  final String nutritionBasis;
  final double? densityGPerMl;
  final String source;
  final String? sourceReference;
  final int? revision;
  final List<FoodNutrient> nutrients;
  final List<FoodServing> servings;
}

final class FoodCatalogPage {
  const FoodCatalogPage({
    required this.page,
    required this.size,
    required this.totalElements,
    required this.totalPages,
    required this.content,
  });

  factory FoodCatalogPage.fromJson(Object? value) {
    final json = CatalogJson.map(value);
    return FoodCatalogPage(
      page: CatalogJson.requiredInt(json, 'page'),
      size: CatalogJson.requiredInt(json, 'size'),
      totalElements: CatalogJson.requiredInt(json, 'totalElements'),
      totalPages: CatalogJson.requiredInt(json, 'totalPages'),
      content: CatalogJson.list(json['content'], FoodCatalogItem.fromJson),
    );
  }

  final int page;
  final int size;
  final int totalElements;
  final int totalPages;
  final List<FoodCatalogItem> content;
}

final class IngredientCatalogItem {
  const IngredientCatalogItem({
    required this.publicId,
    required this.code,
    required this.displayName,
    required this.category,
    required this.staple,
  });

  factory IngredientCatalogItem.fromJson(Object? value) {
    final json = CatalogJson.map(value);
    return IngredientCatalogItem(
      publicId: CatalogJson.requiredString(json, 'publicId'),
      code: CatalogJson.requiredString(json, 'code'),
      displayName: CatalogJson.requiredString(json, 'displayName'),
      category: _optionalCategory(json['category']),
      staple: CatalogJson.requiredBool(json, 'staple'),
    );
  }

  final String publicId;
  final String code;
  final String displayName;
  final CatalogCategory? category;
  final bool staple;
}

final class IngredientFoodMapping {
  const IngredientFoodMapping({
    required this.foodPublicId,
    required this.foodCode,
    required this.foodDisplayName,
    required this.preparationState,
    required this.yieldFactor,
    required this.primary,
  });

  factory IngredientFoodMapping.fromJson(Object? value) {
    final json = CatalogJson.map(value);
    return IngredientFoodMapping(
      foodPublicId: CatalogJson.requiredString(json, 'foodPublicId'),
      foodCode: CatalogJson.requiredString(json, 'foodCode'),
      foodDisplayName: CatalogJson.requiredString(json, 'foodDisplayName'),
      preparationState: CatalogJson.requiredString(json, 'preparationState'),
      yieldFactor: CatalogJson.requiredDouble(json, 'yieldFactor'),
      primary: CatalogJson.requiredBool(json, 'primary'),
    );
  }

  final String foodPublicId;
  final String foodCode;
  final String foodDisplayName;
  final String preparationState;
  final double yieldFactor;
  final bool primary;
}

final class IngredientAllergen {
  const IngredientAllergen({
    required this.allergenCode,
    required this.allergenDisplayName,
    required this.presence,
    required this.note,
  });

  factory IngredientAllergen.fromJson(Object? value) {
    final json = CatalogJson.map(value);
    return IngredientAllergen(
      allergenCode: CatalogJson.requiredString(json, 'allergenCode'),
      allergenDisplayName: CatalogJson.requiredString(
        json,
        'allergenDisplayName',
      ),
      presence: CatalogJson.requiredString(json, 'presence'),
      note: CatalogJson.optionalString(json, 'note'),
    );
  }

  final String allergenCode;
  final String allergenDisplayName;
  final String presence;
  final String? note;
}

final class IngredientUnitConversion {
  const IngredientUnitConversion({
    required this.fromUnitCode,
    required this.fromUnitDisplayName,
    required this.fromQuantity,
    required this.toUnitCode,
    required this.toUnitDisplayName,
    required this.toQuantity,
    required this.confidence,
    required this.sourceNote,
  });

  factory IngredientUnitConversion.fromJson(Object? value) {
    final json = CatalogJson.map(value);
    return IngredientUnitConversion(
      fromUnitCode: CatalogJson.requiredString(json, 'fromUnitCode'),
      fromUnitDisplayName: CatalogJson.requiredString(
        json,
        'fromUnitDisplayName',
      ),
      fromQuantity: CatalogJson.requiredDouble(json, 'fromQuantity'),
      toUnitCode: CatalogJson.requiredString(json, 'toUnitCode'),
      toUnitDisplayName: CatalogJson.requiredString(
        json,
        'toUnitDisplayName',
      ),
      toQuantity: CatalogJson.requiredDouble(json, 'toQuantity'),
      confidence: CatalogJson.requiredString(json, 'confidence'),
      sourceNote: CatalogJson.optionalString(json, 'sourceNote'),
    );
  }

  final String fromUnitCode;
  final String fromUnitDisplayName;
  final double fromQuantity;
  final String toUnitCode;
  final String toUnitDisplayName;
  final double toQuantity;
  final String confidence;
  final String? sourceNote;
}

final class IngredientCatalogDetail {
  const IngredientCatalogDetail({
    required this.publicId,
    required this.code,
    required this.displayName,
    required this.category,
    required this.defaultFoodPublicId,
    required this.defaultFoodCode,
    required this.defaultFoodDisplayName,
    required this.defaultUnitCode,
    required this.defaultUnitDisplayName,
    required this.pieceGramWeight,
    required this.typicalShelfLifeDays,
    required this.staple,
    required this.aliases,
    required this.foodMappings,
    required this.allergens,
    required this.unitConversions,
  });

  factory IngredientCatalogDetail.fromJson(Object? value) {
    final json = CatalogJson.map(value);
    return IngredientCatalogDetail(
      publicId: CatalogJson.requiredString(json, 'publicId'),
      code: CatalogJson.requiredString(json, 'code'),
      displayName: CatalogJson.requiredString(json, 'displayName'),
      category: _optionalCategory(json['category']),
      defaultFoodPublicId: CatalogJson.optionalString(
        json,
        'defaultFoodPublicId',
      ),
      defaultFoodCode: CatalogJson.optionalString(json, 'defaultFoodCode'),
      defaultFoodDisplayName: CatalogJson.optionalString(
        json,
        'defaultFoodDisplayName',
      ),
      defaultUnitCode: CatalogJson.optionalString(json, 'defaultUnitCode'),
      defaultUnitDisplayName: CatalogJson.optionalString(
        json,
        'defaultUnitDisplayName',
      ),
      pieceGramWeight: CatalogJson.optionalDouble(json, 'pieceGramWeight'),
      typicalShelfLifeDays: CatalogJson.optionalInt(
        json,
        'typicalShelfLifeDays',
      ),
      staple: CatalogJson.requiredBool(json, 'staple'),
      aliases: CatalogJson.strings(json['aliases']),
      foodMappings: CatalogJson.list(
        json['foodMappings'],
        IngredientFoodMapping.fromJson,
      ),
      allergens: CatalogJson.list(
        json['allergens'],
        IngredientAllergen.fromJson,
      ),
      unitConversions: CatalogJson.list(
        json['unitConversions'],
        IngredientUnitConversion.fromJson,
      ),
    );
  }

  final String publicId;
  final String code;
  final String displayName;
  final CatalogCategory? category;
  final String? defaultFoodPublicId;
  final String? defaultFoodCode;
  final String? defaultFoodDisplayName;
  final String? defaultUnitCode;
  final String? defaultUnitDisplayName;
  final double? pieceGramWeight;
  final int? typicalShelfLifeDays;
  final bool staple;
  final List<String> aliases;
  final List<IngredientFoodMapping> foodMappings;
  final List<IngredientAllergen> allergens;
  final List<IngredientUnitConversion> unitConversions;
}

final class IngredientCatalogPage {
  const IngredientCatalogPage({
    required this.page,
    required this.size,
    required this.totalElements,
    required this.totalPages,
    required this.content,
  });

  factory IngredientCatalogPage.fromJson(Object? value) {
    final json = CatalogJson.map(value);
    return IngredientCatalogPage(
      page: CatalogJson.requiredInt(json, 'page'),
      size: CatalogJson.requiredInt(json, 'size'),
      totalElements: CatalogJson.requiredInt(json, 'totalElements'),
      totalPages: CatalogJson.requiredInt(json, 'totalPages'),
      content: CatalogJson.list(json['content'], IngredientCatalogItem.fromJson),
    );
  }

  final int page;
  final int size;
  final int totalElements;
  final int totalPages;
  final List<IngredientCatalogItem> content;
}

enum CatalogListStatus { initial, loading, loaded, loadingMore, error }

enum CatalogDetailStatus { initial, loading, loaded, error }

final class CatalogListState<T> {
  const CatalogListState({
    required this.status,
    required this.categories,
    required this.selectedCategoryCode,
    required this.searchQuery,
    required this.items,
    required this.page,
    required this.totalElements,
    required this.totalPages,
    required this.errorMessage,
    required this.loadMoreErrorMessage,
  });

  factory CatalogListState.initial() => CatalogListState<T>(
    status: CatalogListStatus.initial,
    categories: <CatalogCategory>[],
    selectedCategoryCode: null,
    searchQuery: '',
    items: <T>[],
    page: 0,
    totalElements: 0,
    totalPages: 0,
    errorMessage: null,
    loadMoreErrorMessage: null,
  );

  final CatalogListStatus status;
  final List<CatalogCategory> categories;
  final String? selectedCategoryCode;
  final String searchQuery;
  final List<T> items;
  final int page;
  final int totalElements;
  final int totalPages;
  final String? errorMessage;
  final String? loadMoreErrorMessage;

  bool get hasMore => page + 1 < totalPages;
  bool get isInitialLoading =>
      status == CatalogListStatus.loading && items.isEmpty;
  bool get isLoadingMore => status == CatalogListStatus.loadingMore;

  CatalogListState<T> copyWith({
    CatalogListStatus? status,
    List<CatalogCategory>? categories,
    Object? selectedCategoryCode = _catalogUnset,
    String? searchQuery,
    List<T>? items,
    int? page,
    int? totalElements,
    int? totalPages,
    Object? errorMessage = _catalogUnset,
    Object? loadMoreErrorMessage = _catalogUnset,
  }) => CatalogListState<T>(
    status: status ?? this.status,
    categories: categories ?? this.categories,
    selectedCategoryCode: identical(selectedCategoryCode, _catalogUnset)
        ? this.selectedCategoryCode
        : selectedCategoryCode as String?,
    searchQuery: searchQuery ?? this.searchQuery,
    items: items ?? this.items,
    page: page ?? this.page,
    totalElements: totalElements ?? this.totalElements,
    totalPages: totalPages ?? this.totalPages,
    errorMessage: identical(errorMessage, _catalogUnset)
        ? this.errorMessage
        : errorMessage as String?,
    loadMoreErrorMessage: identical(loadMoreErrorMessage, _catalogUnset)
        ? this.loadMoreErrorMessage
        : loadMoreErrorMessage as String?,
  );
}

final class CatalogDetailState<T> {
  const CatalogDetailState({
    required this.status,
    required this.publicId,
    required this.item,
    required this.errorMessage,
  });

  factory CatalogDetailState.initial() => CatalogDetailState<T>(
    status: CatalogDetailStatus.initial,
    publicId: null,
    item: null,
    errorMessage: null,
  );

  final CatalogDetailStatus status;
  final String? publicId;
  final T? item;
  final String? errorMessage;

  CatalogDetailState<T> copyWith({
    CatalogDetailStatus? status,
    Object? publicId = _catalogUnset,
    Object? item = _catalogUnset,
    Object? errorMessage = _catalogUnset,
  }) => CatalogDetailState<T>(
    status: status ?? this.status,
    publicId: identical(publicId, _catalogUnset)
        ? this.publicId
        : publicId as String?,
    item: identical(item, _catalogUnset) ? this.item : item as T?,
    errorMessage: identical(errorMessage, _catalogUnset)
        ? this.errorMessage
        : errorMessage as String?,
  );
}

const _catalogUnset = Object();

CatalogCategory? _optionalCategory(Object? value) =>
    value == null ? null : CatalogCategory.fromJson(value);
