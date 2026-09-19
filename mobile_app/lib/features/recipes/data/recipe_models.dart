import 'package:smart_meal_planner/features/catalog/data/catalog_models.dart';

final class RecipeTag {
  const RecipeTag({required this.code, required this.displayName, this.tagKind});

  factory RecipeTag.fromJson(Object? value) {
    final json = CatalogJson.map(value);
    return RecipeTag(
      code: CatalogJson.requiredString(json, 'code'),
      displayName: CatalogJson.requiredString(json, 'displayName'),
      tagKind: CatalogJson.optionalString(json, 'tagKind'),
    );
  }

  final String code;
  final String displayName;
  final String? tagKind;
}

final class RecipeMealSlot {
  const RecipeMealSlot({
    required this.code,
    required this.displayName,
    required this.displayOrder,
    required this.typicalTime,
    required this.mainMeal,
  });

  factory RecipeMealSlot.fromJson(Object? value) {
    final json = CatalogJson.map(value);
    return RecipeMealSlot(
      code: CatalogJson.requiredString(json, 'code'),
      displayName: CatalogJson.requiredString(json, 'displayName'),
      displayOrder: CatalogJson.requiredInt(json, 'displayOrder'),
      typicalTime: CatalogJson.optionalString(json, 'typicalTime'),
      mainMeal: CatalogJson.requiredBool(json, 'mainMeal'),
    );
  }

  final String code;
  final String displayName;
  final int displayOrder;
  final String? typicalTime;
  final bool mainMeal;
}

final class RecipeListItem {
  const RecipeListItem({
    required this.publicId,
    required this.title,
    required this.summary,
    required this.servings,
    required this.prepMinutes,
    required this.cookMinutes,
    required this.totalMinutes,
    required this.difficulty,
    required this.imageUrl,
    required this.source,
    required this.tags,
    required this.mealSlots,
  });

  factory RecipeListItem.fromJson(Object? value) {
    final json = CatalogJson.map(value);
    return RecipeListItem(
      publicId: CatalogJson.requiredString(json, 'publicId'),
      title: CatalogJson.requiredString(json, 'title'),
      summary: CatalogJson.optionalString(json, 'summary'),
      servings: CatalogJson.requiredInt(json, 'servings'),
      prepMinutes: CatalogJson.optionalInt(json, 'prepMinutes'),
      cookMinutes: CatalogJson.optionalInt(json, 'cookMinutes'),
      totalMinutes: CatalogJson.optionalInt(json, 'totalMinutes'),
      difficulty: CatalogJson.requiredString(json, 'difficulty'),
      imageUrl: CatalogJson.optionalString(json, 'imageUrl'),
      source: CatalogJson.requiredString(json, 'source'),
      tags: CatalogJson.list(json['tags'], RecipeTag.fromJson),
      mealSlots: CatalogJson.list(json['mealSlots'], RecipeMealSlot.fromJson),
    );
  }

  final String publicId;
  final String title;
  final String? summary;
  final int servings;
  final int? prepMinutes;
  final int? cookMinutes;
  final int? totalMinutes;
  final String difficulty;
  final String? imageUrl;
  final String source;
  final List<RecipeTag> tags;
  final List<RecipeMealSlot> mealSlots;
}

final class RecipePage {
  const RecipePage({
    required this.page,
    required this.size,
    required this.totalElements,
    required this.totalPages,
    required this.content,
  });

  factory RecipePage.fromJson(Object? value) {
    final json = CatalogJson.map(value);
    return RecipePage(
      page: CatalogJson.requiredInt(json, 'page'),
      size: CatalogJson.requiredInt(json, 'size'),
      totalElements: CatalogJson.requiredInt(json, 'totalElements'),
      totalPages: CatalogJson.requiredInt(json, 'totalPages'),
      content: CatalogJson.list(json['content'], RecipeListItem.fromJson),
    );
  }

  final int page;
  final int size;
  final int totalElements;
  final int totalPages;
  final List<RecipeListItem> content;
}

final class RecipeIngredient {
  const RecipeIngredient({
    required this.lineNumber,
    required this.ingredientPublicId,
    required this.ingredientCode,
    required this.ingredientDisplayName,
    required this.foodPublicId,
    required this.foodCode,
    required this.foodDisplayName,
    required this.quantity,
    required this.unitCode,
    required this.unitDisplayName,
    required this.preparationNote,
    required this.optional,
    required this.allowSubstitution,
    required this.sectionLabel,
  });

  factory RecipeIngredient.fromJson(Object? value) {
    final json = CatalogJson.map(value);
    return RecipeIngredient(
      lineNumber: CatalogJson.requiredInt(json, 'lineNumber'),
      ingredientPublicId: CatalogJson.requiredString(
        json,
        'ingredientPublicId',
      ),
      ingredientCode: CatalogJson.requiredString(json, 'ingredientCode'),
      ingredientDisplayName: CatalogJson.requiredString(
        json,
        'ingredientDisplayName',
      ),
      foodPublicId: CatalogJson.optionalString(json, 'foodPublicId'),
      foodCode: CatalogJson.optionalString(json, 'foodCode'),
      foodDisplayName: CatalogJson.optionalString(json, 'foodDisplayName'),
      quantity: CatalogJson.optionalDouble(json, 'quantity'),
      unitCode: CatalogJson.optionalString(json, 'unitCode'),
      unitDisplayName: CatalogJson.optionalString(json, 'unitDisplayName'),
      preparationNote: CatalogJson.optionalString(json, 'preparationNote'),
      optional: CatalogJson.requiredBool(json, 'optional'),
      allowSubstitution: CatalogJson.requiredBool(json, 'allowSubstitution'),
      sectionLabel: CatalogJson.optionalString(json, 'sectionLabel'),
    );
  }

  final int lineNumber;
  final String ingredientPublicId;
  final String ingredientCode;
  final String ingredientDisplayName;
  final String? foodPublicId;
  final String? foodCode;
  final String? foodDisplayName;
  final double? quantity;
  final String? unitCode;
  final String? unitDisplayName;
  final String? preparationNote;
  final bool optional;
  final bool allowSubstitution;
  final String? sectionLabel;
}

final class RecipeStep {
  const RecipeStep({
    required this.stepNumber,
    required this.instruction,
    required this.durationMinutes,
  });

  factory RecipeStep.fromJson(Object? value) {
    final json = CatalogJson.map(value);
    return RecipeStep(
      stepNumber: CatalogJson.requiredInt(json, 'stepNumber'),
      instruction: CatalogJson.requiredString(json, 'instruction'),
      durationMinutes: CatalogJson.optionalInt(json, 'durationMinutes'),
    );
  }

  final int stepNumber;
  final String instruction;
  final int? durationMinutes;
}

final class RecipeNutritionValue {
  const RecipeNutritionValue({
    required this.nutrientCode,
    required this.nutrientDisplayName,
    required this.amountPerServing,
    required this.unitCode,
    required this.unitDisplayName,
  });

  factory RecipeNutritionValue.fromJson(Object? value) {
    final json = CatalogJson.map(value);
    return RecipeNutritionValue(
      nutrientCode: CatalogJson.requiredString(json, 'nutrientCode'),
      nutrientDisplayName: CatalogJson.requiredString(
        json,
        'nutrientDisplayName',
      ),
      amountPerServing: CatalogJson.requiredDouble(json, 'amountPerServing'),
      unitCode: CatalogJson.requiredString(json, 'unitCode'),
      unitDisplayName: CatalogJson.requiredString(json, 'unitDisplayName'),
    );
  }

  final String nutrientCode;
  final String nutrientDisplayName;
  final double amountPerServing;
  final String unitCode;
  final String unitDisplayName;
}

final class RecipeNutritionSnapshot {
  const RecipeNutritionSnapshot({
    required this.computedAt,
    required this.ingredientRevision,
    required this.completenessRatio,
    required this.computationNote,
    required this.values,
  });

  factory RecipeNutritionSnapshot.fromJson(Object? value) {
    final json = CatalogJson.map(value);
    return RecipeNutritionSnapshot(
      computedAt: CatalogJson.requiredString(json, 'computedAt'),
      ingredientRevision: CatalogJson.optionalInt(json, 'ingredientRevision'),
      completenessRatio: CatalogJson.requiredDouble(
        json,
        'completenessRatio',
      ),
      computationNote: CatalogJson.optionalString(json, 'computationNote'),
      values: CatalogJson.list(
        json['values'],
        RecipeNutritionValue.fromJson,
      ),
    );
  }

  final String computedAt;
  final int? ingredientRevision;
  final double completenessRatio;
  final String? computationNote;
  final List<RecipeNutritionValue> values;
}

final class RecipeDetail {
  const RecipeDetail({
    required this.publicId,
    required this.title,
    required this.slug,
    required this.summary,
    required this.servings,
    required this.prepMinutes,
    required this.cookMinutes,
    required this.totalMinutes,
    required this.difficulty,
    required this.instructionsNote,
    required this.imageUrl,
    required this.source,
    required this.sourceReference,
    required this.status,
    required this.publishedAt,
    required this.ingredients,
    required this.steps,
    required this.tags,
    required this.mealSlots,
    required this.nutrition,
  });

  factory RecipeDetail.fromJson(Object? value) {
    final json = CatalogJson.map(value);
    return RecipeDetail(
      publicId: CatalogJson.requiredString(json, 'publicId'),
      title: CatalogJson.requiredString(json, 'title'),
      slug: CatalogJson.requiredString(json, 'slug'),
      summary: CatalogJson.optionalString(json, 'summary'),
      servings: CatalogJson.requiredInt(json, 'servings'),
      prepMinutes: CatalogJson.optionalInt(json, 'prepMinutes'),
      cookMinutes: CatalogJson.optionalInt(json, 'cookMinutes'),
      totalMinutes: CatalogJson.optionalInt(json, 'totalMinutes'),
      difficulty: CatalogJson.requiredString(json, 'difficulty'),
      instructionsNote: CatalogJson.optionalString(json, 'instructionsNote'),
      imageUrl: CatalogJson.optionalString(json, 'imageUrl'),
      source: CatalogJson.requiredString(json, 'source'),
      sourceReference: CatalogJson.optionalString(json, 'sourceReference'),
      status: CatalogJson.requiredString(json, 'status'),
      publishedAt: CatalogJson.optionalString(json, 'publishedAt'),
      ingredients: CatalogJson.list(
        json['ingredients'],
        RecipeIngredient.fromJson,
      ),
      steps: CatalogJson.list(json['steps'], RecipeStep.fromJson),
      tags: CatalogJson.list(json['tags'], RecipeTag.fromJson),
      mealSlots: CatalogJson.list(
        json['mealSlots'],
        RecipeMealSlot.fromJson,
      ),
      nutrition: json['nutrition'] == null
          ? null
          : RecipeNutritionSnapshot.fromJson(json['nutrition']),
    );
  }

  final String publicId;
  final String title;
  final String slug;
  final String? summary;
  final int servings;
  final int? prepMinutes;
  final int? cookMinutes;
  final int? totalMinutes;
  final String difficulty;
  final String? instructionsNote;
  final String? imageUrl;
  final String source;
  final String? sourceReference;
  final String status;
  final String? publishedAt;
  final List<RecipeIngredient> ingredients;
  final List<RecipeStep> steps;
  final List<RecipeTag> tags;
  final List<RecipeMealSlot> mealSlots;
  final RecipeNutritionSnapshot? nutrition;
}
