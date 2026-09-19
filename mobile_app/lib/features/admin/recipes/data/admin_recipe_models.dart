import 'package:smart_meal_planner/features/catalog/data/catalog_models.dart';
import 'package:smart_meal_planner/features/recipes/data/recipe_models.dart';

final class AdminRecipeItem {
  const AdminRecipeItem({
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
    required this.status,
    required this.tags,
    required this.mealSlots,
  });

  factory AdminRecipeItem.fromJson(Object? value) {
    final json = CatalogJson.map(value);
    return AdminRecipeItem(
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
      status: CatalogJson.requiredString(json, 'status'),
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
  final String status;
  final List<RecipeTag> tags;
  final List<RecipeMealSlot> mealSlots;
}

final class AdminRecipePage {
  const AdminRecipePage({
    required this.page,
    required this.size,
    required this.totalElements,
    required this.totalPages,
    required this.content,
  });

  factory AdminRecipePage.fromJson(Object? value) {
    final json = CatalogJson.map(value);
    return AdminRecipePage(
      page: CatalogJson.requiredInt(json, 'page'),
      size: CatalogJson.requiredInt(json, 'size'),
      totalElements: CatalogJson.requiredInt(json, 'totalElements'),
      totalPages: CatalogJson.requiredInt(json, 'totalPages'),
      content: CatalogJson.list(json['content'], AdminRecipeItem.fromJson),
    );
  }

  final int page;
  final int size;
  final int totalElements;
  final int totalPages;
  final List<AdminRecipeItem> content;
}

final class AdminRecipeDetail {
  const AdminRecipeDetail({
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
    required this.archivedAt,
    required this.ingredients,
    required this.steps,
    required this.tags,
    required this.mealSlots,
    required this.nutrition,
  });

  factory AdminRecipeDetail.fromJson(Object? value) {
    final json = CatalogJson.map(value);
    return AdminRecipeDetail(
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
      archivedAt: CatalogJson.optionalString(json, 'archivedAt'),
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
  final String? archivedAt;
  final List<RecipeIngredient> ingredients;
  final List<RecipeStep> steps;
  final List<RecipeTag> tags;
  final List<RecipeMealSlot> mealSlots;
  final RecipeNutritionSnapshot? nutrition;
}

final class AdminRecipeUpsertRequest {
  const AdminRecipeUpsertRequest({
    required this.title,
    required this.slug,
    required this.summary,
    required this.servings,
    required this.prepMinutes,
    required this.cookMinutes,
    required this.difficulty,
    required this.instructionsNote,
    required this.imageUrl,
    required this.ingredients,
    required this.steps,
    required this.tagCodes,
    required this.mealSlotCodes,
  });

  final String title;
  final String slug;
  final String? summary;
  final int servings;
  final int? prepMinutes;
  final int? cookMinutes;
  final String difficulty;
  final String? instructionsNote;
  final String? imageUrl;
  final List<AdminRecipeIngredientRequest> ingredients;
  final List<AdminRecipeStepRequest> steps;
  final List<String> tagCodes;
  final List<String> mealSlotCodes;

  Map<String, Object?> toJson() => {
    'title': title,
    'slug': slug,
    'summary': summary,
    'servings': servings,
    'prepMinutes': prepMinutes,
    'cookMinutes': cookMinutes,
    'difficulty': difficulty,
    'instructionsNote': instructionsNote,
    'imageUrl': imageUrl,
    'ingredients': ingredients.map((item) => item.toJson()).toList(),
    'steps': steps.map((item) => item.toJson()).toList(),
    'tagCodes': tagCodes,
    'mealSlotCodes': mealSlotCodes,
  };
}

final class AdminRecipeIngredientRequest {
  const AdminRecipeIngredientRequest({
    required this.ingredientPublicId,
    required this.quantity,
    required this.unitCode,
    required this.preparationNote,
    required this.optional,
    required this.allowSubstitution,
    required this.sectionLabel,
  });

  final String ingredientPublicId;
  final double? quantity;
  final String? unitCode;
  final String? preparationNote;
  final bool optional;
  final bool allowSubstitution;
  final String? sectionLabel;

  Map<String, Object?> toJson() => {
    'ingredientPublicId': ingredientPublicId,
    'quantity': quantity,
    'unitCode': unitCode,
    'preparationNote': preparationNote,
    'optional': optional,
    'allowSubstitution': allowSubstitution,
    'sectionLabel': sectionLabel,
  };
}

final class AdminRecipeStepRequest {
  const AdminRecipeStepRequest({
    required this.stepNumber,
    required this.instruction,
    required this.durationMinutes,
  });

  final int stepNumber;
  final String instruction;
  final int? durationMinutes;

  Map<String, Object?> toJson() => {
    'stepNumber': stepNumber,
    'instruction': instruction,
    'durationMinutes': durationMinutes,
  };
}
