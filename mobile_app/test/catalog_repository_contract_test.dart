import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:smart_meal_planner/core/api/api_client.dart';
import 'package:smart_meal_planner/features/catalog/data/catalog_repository.dart';

void main() {
  test(
    'catalog repository uses authenticated exact paths, queries, and response shapes',
    () async {
      final requests = <http.Request>[];
      final api = ApiClient(
        baseUrl: 'https://backend.test',
        httpClient: MockClient((request) async {
          requests.add(request);
          expect(request.headers['authorization'], 'Bearer access-token');
          switch (request.url.path) {
            case '/api/v1/foods':
              expect(request.url.queryParameters, {
                'q': 'Gạo nếp',
                'categoryCode': 'GRAINS',
                'page': '2',
                'size': '20',
              });
              return _jsonResponse(_foodPageJson);
            case '/api/v1/foods/00000000-0000-4000-8000-000000000001':
              return _jsonResponse(_foodDetailJson);
            case '/api/v1/reference/food-categories':
              return _jsonResponse(_categoriesJson);
            case '/api/v1/ingredients':
              expect(request.url.queryParameters, {
                'page': '0',
                'size': '20',
              });
              return _jsonResponse(_ingredientPageJson);
            case '/api/v1/ingredients/00000000-0000-4000-8000-000000000101':
              return _jsonResponse(_ingredientDetailJson);
          }
          throw StateError('Unexpected catalog request: ${request.url}');
        }),
      );
      api.configureAuthentication(
        accessTokenProvider: () => 'access-token',
        refreshAccessToken: () async => false,
      );
      final repository = HttpCatalogRepository(api);

      final foodPage = await repository.getFoods(
        query: ' Gạo nếp ',
        categoryCode: ' GRAINS ',
        page: 2,
        size: 20,
      );
      final food = await repository.getFood(
        '00000000-0000-4000-8000-000000000001',
      );
      final categories = await repository.getFoodCategories();
      final ingredientPage = await repository.getIngredients(
        query: '  ',
        categoryCode: ' ',
      );
      final ingredient = await repository.getIngredient(
        '00000000-0000-4000-8000-000000000101',
      );

      expect(foodPage.page, 2);
      expect(foodPage.totalElements, 164);
      expect(foodPage.content.single.displayName, 'Gạo nếp cái');
      expect(foodPage.content.single.publicId, isNot(contains('internal')));
      expect(foodPage.content.single.category?.code, 'GRAINS');

      expect(food.nutritionBasis, 'PER_100_G');
      expect(food.nutrients.single.amount, closeTo(10.7083, 0.00001));
      expect(food.nutrients.single.nutrientCode, 'ENERGY');
      expect(food.servings, isEmpty);
      expect(food.sourceReference, contains('SMILING'));

      expect(categories.single.code, 'GRAINS');
      expect(ingredientPage.content.single.displayName, 'Gạo nếp cái');
      expect(ingredientPage.content.single.staple, isFalse);

      expect(ingredient.defaultUnitCode, 'g');
      expect(ingredient.pieceGramWeight, isNull);
      expect(ingredient.typicalShelfLifeDays, isNull);
      expect(ingredient.aliases, isEmpty);
      expect(ingredient.allergens, isEmpty);
      expect(ingredient.unitConversions, isEmpty);
      expect(ingredient.foodMappings.single.foodPublicId,
          '00000000-0000-4000-8000-000000000001');
      expect(ingredient.foodMappings.single.yieldFactor, 1);
      expect(requests, hasLength(5));
    },
  );
}

http.Response _jsonResponse(Object value) => http.Response(
  jsonEncode(value),
  200,
  headers: const {'content-type': 'application/json'},
);

const _categoriesJson = [
  {
    'code': 'GRAINS',
    'displayName': 'Grains',
    'parentCategoryCode': null,
    'description': null,
  },
];

const _foodPageJson = {
  'page': 2,
  'size': 20,
  'totalElements': 164,
  'totalPages': 9,
  'content': [
    {
      'publicId': '00000000-0000-4000-8000-000000000001',
      'code': 'SMILING_VN_1001',
      'displayName': 'Gạo nếp cái',
      'brand': null,
      'category': {
        'code': 'GRAINS',
        'displayName': 'Grains',
        'parentCategoryCode': null,
        'description': null,
      },
    },
  ],
};

const _foodDetailJson = {
  'publicId': '00000000-0000-4000-8000-000000000001',
  'code': 'SMILING_VN_1001',
  'displayName': 'Gạo nếp cái',
  'brand': null,
  'category': {
    'code': 'GRAINS',
    'displayName': 'Grains',
    'parentCategoryCode': null,
    'description': null,
  },
  'description': null,
  'nutritionBasis': 'PER_100_G',
  'densityGPerMl': null,
  'source': 'IMPORTED',
  'sourceReference':
      'SMILING Food Composition Table for Vietnam 2013; code=1001',
  'revision': 1,
  'nutrients': [
    {
      'nutrientCode': 'ENERGY',
      'nutrientDisplayName': 'Energy',
      'amount': 10.7083,
      'unitCode': 'KCAL',
      'unitDisplayName': 'kcal',
      'dataQuality': 'ANALYTICAL',
    },
  ],
  'servings': [],
};

const _ingredientPageJson = {
  'page': 0,
  'size': 20,
  'totalElements': 163,
  'totalPages': 9,
  'content': [
    {
      'publicId': '00000000-0000-4000-8000-000000000101',
      'code': 'ING_SMILING_VN_1001',
      'displayName': 'Gạo nếp cái',
      'category': {
        'code': 'GRAINS',
        'displayName': 'Grains',
        'parentCategoryCode': null,
        'description': null,
      },
      'staple': false,
    },
  ],
};

const _ingredientDetailJson = {
  'publicId': '00000000-0000-4000-8000-000000000101',
  'code': 'ING_SMILING_VN_1001',
  'displayName': 'Gạo nếp cái',
  'category': {
    'code': 'GRAINS',
    'displayName': 'Grains',
    'parentCategoryCode': null,
    'description': null,
  },
  'defaultFoodPublicId': '00000000-0000-4000-8000-000000000001',
  'defaultFoodCode': 'SMILING_VN_1001',
  'defaultFoodDisplayName': 'Gạo nếp cái',
  'defaultUnitCode': 'g',
  'defaultUnitDisplayName': 'gram',
  'pieceGramWeight': null,
  'typicalShelfLifeDays': null,
  'staple': false,
  'aliases': [],
  'foodMappings': [
    {
      'foodPublicId': '00000000-0000-4000-8000-000000000001',
      'foodCode': 'SMILING_VN_1001',
      'foodDisplayName': 'Gạo nếp cái',
      'preparationState': 'UNSPECIFIED',
      'yieldFactor': 1.0000,
      'primary': true,
    },
  ],
  'allergens': [],
  'unitConversions': [],
};
