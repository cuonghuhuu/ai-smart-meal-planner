import 'package:flutter_test/flutter_test.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/features/recipes/data/recipe_models.dart';
import 'package:smart_meal_planner/features/recipes/data/recipe_json.dart';

const recipeId = '00000000-0000-4000-8000-000000000001';
const ingredientId = '00000000-0000-4000-8000-000000000002';
const foodId = '00000000-0000-4000-8000-000000000003';

const tagJson = {'code': 'VEGAN', 'displayName': 'Vegan', 'tagKind': 'DIET'};
const slotJson = {
  'code': 'DINNER',
  'displayName': 'Dinner',
  'displayOrder': 5,
  'typicalTime': '18:00:00',
  'mainMeal': true,
};
const itemJson = {
  'publicId': recipeId,
  'title': 'Soup',
  'summary': null,
  'servings': 2,
  'prepMinutes': null,
  'cookMinutes': 20,
  'totalMinutes': 20,
  'difficulty': 'EASY',
  'imageUrl': null,
  'source': 'CURATED',
  'tags': [tagJson],
  'mealSlots': [slotJson],
};
const pageJson = {
  'page': 1,
  'size': 20,
  'totalElements': 22,
  'totalPages': 2,
  'content': [itemJson],
};
const ingredientJson = {
  'lineNumber': 2,
  'ingredientPublicId': ingredientId,
  'ingredientCode': null,
  'ingredientDisplayName': 'Herb',
  'foodPublicId': null,
  'foodCode': null,
  'foodDisplayName': null,
  'quantity': null,
  'unitCode': null,
  'unitDisplayName': null,
  'preparationNote': null,
  'optional': true,
  'allowSubstitution': false,
  'sectionLabel': 'Garnish',
};
const nutritionJson = {
  'computedAt': '2026-09-01T12:30:00',
  'ingredientRevision': 1,
  'completenessRatio': 0.75,
  'computationNote': null,
  'values': [
    {
      'nutrientCode': 'PROTEIN',
      'nutrientDisplayName': 'Protein',
      'amountPerServing': 12.5,
      'unitCode': 'g',
      'unitDisplayName': 'Gram',
    },
  ],
};
final detailJson = <String, Object?>{
  ...itemJson,
  'slug': 'soup',
  'instructionsNote': 'Serve warm',
  'sourceReference': 'CURATED:SOUP',
  'status': 'PUBLISHED',
  'publishedAt': '2026-09-01T10:00:00',
  'ingredients': [
    ingredientJson,
    {
      ...ingredientJson,
      'lineNumber': 1,
      'foodPublicId': foodId,
      'foodCode': null,
      'foodDisplayName': 'Herb food',
      'quantity': 1.25,
      'unitCode': 'g',
      'unitDisplayName': 'Gram',
      'optional': false,
    },
  ],
  'steps': [
    {'stepNumber': 2, 'instruction': 'Simmer', 'durationMinutes': 20},
    {'stepNumber': 1, 'instruction': 'Mix', 'durationMinutes': null},
  ],
  'nutrition': nutritionJson,
};

void main() {
  test('parses a Recipe page with metadata and nullable summary fields', () {
    final page = RecipePage.fromJson(pageJson);
    expect(page.page, 1);
    expect(page.size, 20);
    expect(page.totalElements, 22);
    expect(page.totalPages, 2);
    expect(page.content.single.publicId, recipeId);
    expect(page.content.single.prepMinutes, isNull);
    expect(page.content.single.difficulty, RecipeDifficulty.easy);
    expect(page.content.single.source, RecipeSource.curated);
    expect(page.content.single.tags.single.tagKind, RecipeTagKind.diet);
    expect(page.content.single.mealSlots.single.typicalTime, '18:00:00');
  });

  test('parses full detail and preserves backend child ordering', () {
    final detail = RecipeDetail.fromJson(detailJson);
    expect(detail.slug, 'soup');
    expect(detail.status, RecipeStatus.published);
    expect(detail.publishedAt, '2026-09-01T10:00:00');
    expect(detail.ingredients.map((line) => line.lineNumber), [2, 1]);
    expect(detail.ingredients.first.quantity, isNull);
    expect(detail.ingredients.first.unitCode, isNull);
    expect(detail.ingredients.first.foodPublicId, isNull);
    expect(detail.ingredients.first.ingredientCode, isNull);
    expect(detail.ingredients.last.quantity, 1.25);
    expect(detail.ingredients.last.foodCode, isNull);
    expect(detail.steps.map((step) => step.stepNumber), [2, 1]);
    expect(detail.steps.last.durationMinutes, isNull);
    expect(detail.nutrition?.ingredientRevision, 1);
    expect(detail.nutrition?.completenessRatio, 0.75);
    expect(detail.nutrition?.values.single.amountPerServing, 12.5);
  });

  test('preserves absent current nutrition as null', () {
    final detail = RecipeDetail.fromJson({...detailJson, 'nutrition': null});
    expect(detail.nutrition, isNull);
  });

  test('parses reference arrays', () {
    expect(RecipeJson.list([tagJson], RecipeTag.fromJson).single.code, 'VEGAN');
    final slots = RecipeJson.list([slotJson], RecipeMealSlot.fromJson);
    expect(slots.single.displayOrder, 5);
    expect(slots.single.mainMeal, isTrue);
    expect(slots.single.typicalTime, '18:00:00');
  });

  test('rejects missing and mistyped required fields', () {
    expect(
      () => RecipePage.fromJson({...pageJson, 'page': '1'}),
      throwsA(isA<ApiResponseFormatException>()),
    );
    final missingTitle = Map<String, Object?>.from(itemJson)..remove('title');
    expect(
      () => RecipeItem.fromJson(missingTitle),
      throwsA(isA<ApiResponseFormatException>()),
    );
    expect(
      () => RecipeItem.fromJson({...itemJson, 'difficulty': 'UNKNOWN'}),
      throwsA(isA<ApiResponseFormatException>()),
    );
    expect(
      () => RecipeDetail.fromJson({
        ...detailJson,
        'nutrition': {'values': []},
      }),
      throwsA(isA<ApiResponseFormatException>()),
    );
    expect(
      () => RecipeIngredient.fromJson({...ingredientJson, 'optional': 'true'}),
      throwsA(isA<ApiResponseFormatException>()),
    );
    expect(
      () => RecipeItem.fromJson({...itemJson, 'totalMinutes': null}),
      throwsA(isA<ApiResponseFormatException>()),
    );
    expect(
      () => RecipeNutritionSnapshot.fromJson({
        ...nutritionJson,
        'ingredientRevision': null,
      }),
      throwsA(isA<ApiResponseFormatException>()),
    );
  });

  test('rejects missing, null, and mistyped collections', () {
    for (final content in [null, {}, 'bad']) {
      expect(
        () => RecipePage.fromJson({...pageJson, 'content': content}),
        throwsA(isA<ApiResponseFormatException>()),
      );
    }
    expect(
      () => RecipeDetail.fromJson({...detailJson, 'steps': null}),
      throwsA(isA<ApiResponseFormatException>()),
    );
    expect(
      () => RecipeDetail.fromJson({
        ...detailJson,
        'ingredients': [null],
      }),
      throwsA(isA<ApiResponseFormatException>()),
    );
    expect(
      () => RecipeJson.list(null, RecipeTag.fromJson),
      throwsA(isA<ApiResponseFormatException>()),
    );
  });

  test('rejects malformed nested summary, reference, and nutrient data', () {
    expect(
      () => RecipePage.fromJson({
        ...pageJson,
        'content': [
          {...itemJson, 'summary': 42},
        ],
      }),
      throwsA(isA<ApiResponseFormatException>()),
    );
    expect(
      () => RecipeJson.list([
        {...tagJson, 'tagKind': 'UNKNOWN'},
      ], RecipeTag.fromJson),
      throwsA(isA<ApiResponseFormatException>()),
    );
    expect(
      () => RecipeDetail.fromJson({
        ...detailJson,
        'nutrition': {
          ...nutritionJson,
          'values': [
            {
              'nutrientCode': 'PROTEIN',
              'nutrientDisplayName': 'Protein',
              'amountPerServing': '12.5',
              'unitCode': 'g',
              'unitDisplayName': 'Gram',
            },
          ],
        },
      }),
      throwsA(isA<ApiResponseFormatException>()),
    );
  });
}
