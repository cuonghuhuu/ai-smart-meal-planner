import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/features/recipes/data/recipe_json.dart';

enum RecipeDifficulty {
  easy('EASY'),
  medium('MEDIUM'),
  hard('HARD');

  const RecipeDifficulty(this.wireValue);
  final String wireValue;
  static RecipeDifficulty fromJson(Object? value) => switch (value) {
    'EASY' => easy,
    'MEDIUM' => medium,
    'HARD' => hard,
    _ => throw const ApiResponseFormatException(),
  };
}

enum RecipeSource {
  curated('CURATED'),
  imported('IMPORTED'),
  userCreated('USER_CREATED');

  const RecipeSource(this.wireValue);
  final String wireValue;
  static RecipeSource fromJson(Object? value) => switch (value) {
    'CURATED' => curated,
    'IMPORTED' => imported,
    'USER_CREATED' => userCreated,
    _ => throw const ApiResponseFormatException(),
  };
}

enum RecipeStatus {
  draft('DRAFT'),
  published('PUBLISHED'),
  archived('ARCHIVED');

  const RecipeStatus(this.wireValue);
  final String wireValue;
  static RecipeStatus fromJson(Object? value) => switch (value) {
    'DRAFT' => draft,
    'PUBLISHED' => published,
    'ARCHIVED' => archived,
    _ => throw const ApiResponseFormatException(),
  };
}

enum RecipeTagKind {
  cuisine('CUISINE'),
  mealType('MEAL_TYPE'),
  method('METHOD'),
  diet('DIET'),
  occasion('OCCASION'),
  other('OTHER');

  const RecipeTagKind(this.wireValue);
  final String wireValue;
  static RecipeTagKind fromJson(Object? value) => switch (value) {
    'CUISINE' => cuisine,
    'MEAL_TYPE' => mealType,
    'METHOD' => method,
    'DIET' => diet,
    'OCCASION' => occasion,
    'OTHER' => other,
    _ => throw const ApiResponseFormatException(),
  };
}

final class RecipeTag {
  const RecipeTag({
    required this.code,
    required this.displayName,
    required this.tagKind,
  });
  factory RecipeTag.fromJson(Object? value) {
    final json = RecipeJson.map(value);
    return RecipeTag(
      code: RecipeJson.string(json, 'code'),
      displayName: RecipeJson.string(json, 'displayName'),
      tagKind: RecipeTagKind.fromJson(RecipeJson.field(json, 'tagKind')),
    );
  }
  final String code;
  final String displayName;
  final RecipeTagKind tagKind;
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
    final json = RecipeJson.map(value);
    return RecipeMealSlot(
      code: RecipeJson.string(json, 'code'),
      displayName: RecipeJson.string(json, 'displayName'),
      displayOrder: RecipeJson.integer(json, 'displayOrder'),
      typicalTime: RecipeJson.nullableString(json, 'typicalTime'),
      mainMeal: RecipeJson.boolean(json, 'mainMeal'),
    );
  }
  final String code;
  final String displayName;
  final int displayOrder;

  /// Backend LocalTime, retained without timezone conversion.
  final String? typicalTime;
  final bool mainMeal;
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
    final json = RecipeJson.map(value);
    return RecipePage(
      page: RecipeJson.integer(json, 'page'),
      size: RecipeJson.integer(json, 'size'),
      totalElements: RecipeJson.integer(json, 'totalElements'),
      totalPages: RecipeJson.integer(json, 'totalPages'),
      content: RecipeJson.list(
        RecipeJson.field(json, 'content'),
        RecipeItem.fromJson,
      ),
    );
  }
  final int page;
  final int size;
  final int totalElements;
  final int totalPages;
  final List<RecipeItem> content;
}

final class RecipeItem {
  const RecipeItem({
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
  factory RecipeItem.fromJson(Object? value) {
    final json = RecipeJson.map(value);
    return RecipeItem(
      publicId: RecipeJson.string(json, 'publicId'),
      title: RecipeJson.string(json, 'title'),
      summary: RecipeJson.nullableString(json, 'summary'),
      servings: RecipeJson.integer(json, 'servings'),
      prepMinutes: RecipeJson.nullableInteger(json, 'prepMinutes'),
      cookMinutes: RecipeJson.nullableInteger(json, 'cookMinutes'),
      totalMinutes: RecipeJson.integer(json, 'totalMinutes'),
      difficulty: RecipeDifficulty.fromJson(
        RecipeJson.field(json, 'difficulty'),
      ),
      imageUrl: RecipeJson.nullableString(json, 'imageUrl'),
      source: RecipeSource.fromJson(RecipeJson.field(json, 'source')),
      tags: RecipeJson.list(RecipeJson.field(json, 'tags'), RecipeTag.fromJson),
      mealSlots: RecipeJson.list(
        RecipeJson.field(json, 'mealSlots'),
        RecipeMealSlot.fromJson,
      ),
    );
  }
  final String publicId;
  final String title;
  final String? summary;
  final int servings;
  final int? prepMinutes;
  final int? cookMinutes;
  final int totalMinutes;
  final RecipeDifficulty difficulty;
  final String? imageUrl;
  final RecipeSource source;
  final List<RecipeTag> tags;
  final List<RecipeMealSlot> mealSlots;
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
    final json = RecipeJson.map(value);
    return RecipeIngredient(
      lineNumber: RecipeJson.integer(json, 'lineNumber'),
      ingredientPublicId: RecipeJson.string(json, 'ingredientPublicId'),
      ingredientCode: RecipeJson.nullableString(json, 'ingredientCode'),
      ingredientDisplayName: RecipeJson.string(json, 'ingredientDisplayName'),
      foodPublicId: RecipeJson.nullableString(json, 'foodPublicId'),
      foodCode: RecipeJson.nullableString(json, 'foodCode'),
      foodDisplayName: RecipeJson.nullableString(json, 'foodDisplayName'),
      quantity: RecipeJson.nullableNumber(json, 'quantity'),
      unitCode: RecipeJson.nullableString(json, 'unitCode'),
      unitDisplayName: RecipeJson.nullableString(json, 'unitDisplayName'),
      preparationNote: RecipeJson.nullableString(json, 'preparationNote'),
      optional: RecipeJson.boolean(json, 'optional'),
      allowSubstitution: RecipeJson.boolean(json, 'allowSubstitution'),
      sectionLabel: RecipeJson.nullableString(json, 'sectionLabel'),
    );
  }
  final int lineNumber;
  final String ingredientPublicId;
  final String? ingredientCode;
  final String ingredientDisplayName;
  final String? foodPublicId;
  final String? foodCode;
  final String? foodDisplayName;
  final num? quantity;
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
    final json = RecipeJson.map(value);
    return RecipeStep(
      stepNumber: RecipeJson.integer(json, 'stepNumber'),
      instruction: RecipeJson.string(json, 'instruction'),
      durationMinutes: RecipeJson.nullableInteger(json, 'durationMinutes'),
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
    final json = RecipeJson.map(value);
    return RecipeNutritionValue(
      nutrientCode: RecipeJson.string(json, 'nutrientCode'),
      nutrientDisplayName: RecipeJson.string(json, 'nutrientDisplayName'),
      amountPerServing: RecipeJson.number(json, 'amountPerServing'),
      unitCode: RecipeJson.string(json, 'unitCode'),
      unitDisplayName: RecipeJson.string(json, 'unitDisplayName'),
    );
  }
  final String nutrientCode;
  final String nutrientDisplayName;
  final num amountPerServing;
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
    final json = RecipeJson.map(value);
    return RecipeNutritionSnapshot(
      computedAt: RecipeJson.string(json, 'computedAt'),
      ingredientRevision: RecipeJson.integer(json, 'ingredientRevision'),
      completenessRatio: RecipeJson.number(json, 'completenessRatio'),
      computationNote: RecipeJson.nullableString(json, 'computationNote'),
      values: RecipeJson.list(
        RecipeJson.field(json, 'values'),
        RecipeNutritionValue.fromJson,
      ),
    );
  }

  /// Backend LocalDateTime, retained without timezone conversion.
  final String computedAt;
  final int ingredientRevision;
  final num completenessRatio;
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
    final json = RecipeJson.map(value);
    final nutrition = RecipeJson.field(json, 'nutrition');
    return RecipeDetail(
      publicId: RecipeJson.string(json, 'publicId'),
      title: RecipeJson.string(json, 'title'),
      slug: RecipeJson.string(json, 'slug'),
      summary: RecipeJson.nullableString(json, 'summary'),
      servings: RecipeJson.integer(json, 'servings'),
      prepMinutes: RecipeJson.nullableInteger(json, 'prepMinutes'),
      cookMinutes: RecipeJson.nullableInteger(json, 'cookMinutes'),
      totalMinutes: RecipeJson.integer(json, 'totalMinutes'),
      difficulty: RecipeDifficulty.fromJson(
        RecipeJson.field(json, 'difficulty'),
      ),
      instructionsNote: RecipeJson.nullableString(json, 'instructionsNote'),
      imageUrl: RecipeJson.nullableString(json, 'imageUrl'),
      source: RecipeSource.fromJson(RecipeJson.field(json, 'source')),
      sourceReference: RecipeJson.nullableString(json, 'sourceReference'),
      status: RecipeStatus.fromJson(RecipeJson.field(json, 'status')),
      publishedAt: RecipeJson.string(json, 'publishedAt'),
      ingredients: RecipeJson.list(
        RecipeJson.field(json, 'ingredients'),
        RecipeIngredient.fromJson,
      ),
      steps: RecipeJson.list(
        RecipeJson.field(json, 'steps'),
        RecipeStep.fromJson,
      ),
      tags: RecipeJson.list(RecipeJson.field(json, 'tags'), RecipeTag.fromJson),
      mealSlots: RecipeJson.list(
        RecipeJson.field(json, 'mealSlots'),
        RecipeMealSlot.fromJson,
      ),
      nutrition: nutrition == null
          ? null
          : RecipeNutritionSnapshot.fromJson(nutrition),
    );
  }
  final String publicId;
  final String title;
  final String slug;
  final String? summary;
  final int servings;
  final int? prepMinutes;
  final int? cookMinutes;
  final int totalMinutes;
  final RecipeDifficulty difficulty;
  final String? instructionsNote;
  final String? imageUrl;
  final RecipeSource source;
  final String? sourceReference;
  final RecipeStatus status;

  /// Backend LocalDateTime, retained without timezone conversion.
  final String publishedAt;
  final List<RecipeIngredient> ingredients;
  final List<RecipeStep> steps;
  final List<RecipeTag> tags;
  final List<RecipeMealSlot> mealSlots;
  final RecipeNutritionSnapshot? nutrition;
}
