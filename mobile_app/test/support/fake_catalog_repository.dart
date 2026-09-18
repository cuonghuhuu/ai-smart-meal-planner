import 'package:smart_meal_planner/features/catalog/data/catalog_models.dart';
import 'package:smart_meal_planner/features/catalog/data/catalog_repository.dart';

final class CatalogPageRequest {
  const CatalogPageRequest({
    required this.query,
    required this.categoryCode,
    required this.page,
    required this.size,
  });

  final String? query;
  final String? categoryCode;
  final int page;
  final int size;
}

const testFoodId = '00000000-0000-4000-8000-000000000001';
const testFoodIdTwo = '00000000-0000-4000-8000-000000000002';
const testIngredientId = '00000000-0000-4000-8000-000000000101';

const testCategory = CatalogCategory(
  code: 'GRAINS',
  displayName: 'Grains',
  parentCategoryCode: null,
  description: null,
);

const testFood = FoodCatalogItem(
  publicId: testFoodId,
  code: 'SMILING_VN_1001',
  displayName: 'Gạo nếp cái',
  brand: null,
  category: testCategory,
);

const testFoodTwo = FoodCatalogItem(
  publicId: testFoodIdTwo,
  code: 'SMILING_VN_1002',
  displayName: 'Cà chua',
  brand: null,
  category: CatalogCategory(
    code: 'VEGETABLES',
    displayName: 'Vegetables',
    parentCategoryCode: null,
    description: null,
  ),
);

const testIngredient = IngredientCatalogItem(
  publicId: testIngredientId,
  code: 'ING_SMILING_VN_1001',
  displayName: 'Gạo nếp cái',
  category: testCategory,
  staple: false,
);

const testFoodPage = FoodCatalogPage(
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
  content: [testFood],
);

const testIngredientPage = IngredientCatalogPage(
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
  content: [testIngredient],
);

const testFoodDetail = FoodCatalogDetail(
  publicId: testFoodId,
  code: 'SMILING_VN_1001',
  displayName: 'Gạo nếp cái',
  brand: null,
  category: testCategory,
  description: null,
  nutritionBasis: 'PER_100_G',
  densityGPerMl: null,
  source: 'IMPORTED',
  sourceReference: 'SMILING Food Composition Table for Vietnam 2013; code=1001',
  revision: 1,
  nutrients: [
    FoodNutrient(
      nutrientCode: 'ENERGY',
      nutrientDisplayName: 'Energy',
      amount: 10.7083,
      unitCode: 'KCAL',
      unitDisplayName: 'kcal',
      dataQuality: 'ANALYTICAL',
    ),
  ],
  servings: [],
);

const testIngredientDetail = IngredientCatalogDetail(
  publicId: testIngredientId,
  code: 'ING_SMILING_VN_1001',
  displayName: 'Gạo nếp cái',
  category: testCategory,
  defaultFoodPublicId: testFoodId,
  defaultFoodCode: 'SMILING_VN_1001',
  defaultFoodDisplayName: 'Gạo nếp cái',
  defaultUnitCode: 'g',
  defaultUnitDisplayName: 'gram',
  pieceGramWeight: null,
  typicalShelfLifeDays: null,
  staple: false,
  aliases: [],
  foodMappings: [
    IngredientFoodMapping(
      foodPublicId: testFoodId,
      foodCode: 'SMILING_VN_1001',
      foodDisplayName: 'Gạo nếp cái',
      preparationState: 'UNSPECIFIED',
      yieldFactor: 1,
      primary: true,
    ),
  ],
  allergens: [],
  unitConversions: [],
);

final class FakeCatalogRepository implements CatalogRepository {
  FakeCatalogRepository({
    List<CatalogCategory>? categories,
    FoodCatalogPage? foodPage,
    IngredientCatalogPage? ingredientPage,
    FoodCatalogDetail? foodDetail,
    IngredientCatalogDetail? ingredientDetail,
    this.foodCategoriesError,
    this.foodsError,
    this.ingredientsError,
    this.foodDetailError,
    this.ingredientDetailError,
  }) : categories = categories ?? const [testCategory],
       foodPage = foodPage ?? testFoodPage,
       ingredientPage = ingredientPage ?? testIngredientPage,
       foodDetail = foodDetail ?? testFoodDetail,
       ingredientDetail = ingredientDetail ?? testIngredientDetail;

  final List<CatalogCategory> categories;
  FoodCatalogPage foodPage;
  IngredientCatalogPage ingredientPage;
  FoodCatalogDetail foodDetail;
  IngredientCatalogDetail ingredientDetail;
  Object? foodCategoriesError;
  Object? foodsError;
  Object? ingredientsError;
  Object? foodDetailError;
  Object? ingredientDetailError;
  Future<List<CatalogCategory>> Function()? onGetFoodCategories;
  Future<FoodCatalogPage> Function(CatalogPageRequest request)? onGetFoods;
  Future<IngredientCatalogPage> Function(CatalogPageRequest request)?
  onGetIngredients;
  Future<FoodCatalogDetail> Function(String publicId)? onGetFood;
  Future<IngredientCatalogDetail> Function(String publicId)? onGetIngredient;

  final foodRequests = <CatalogPageRequest>[];
  final ingredientRequests = <CatalogPageRequest>[];
  var categoryCalls = 0;
  final foodDetailRequests = <String>[];
  final ingredientDetailRequests = <String>[];

  @override
  Future<FoodCatalogPage> getFoods({
    String? query,
    String? categoryCode,
    int page = 0,
    int size = 20,
  }) {
    final request = CatalogPageRequest(
      query: query,
      categoryCode: categoryCode,
      page: page,
      size: size,
    );
    foodRequests.add(request);
    final handler = onGetFoods;
    if (handler != null) {
      return handler(request);
    }
    if (foodsError != null) {
      return Future<FoodCatalogPage>.error(foodsError!);
    }
    return Future<FoodCatalogPage>.value(foodPage);
  }

  @override
  Future<FoodCatalogDetail> getFood(String publicId) {
    foodDetailRequests.add(publicId);
    final handler = onGetFood;
    if (handler != null) {
      return handler(publicId);
    }
    if (foodDetailError != null) {
      return Future<FoodCatalogDetail>.error(foodDetailError!);
    }
    return Future<FoodCatalogDetail>.value(foodDetail);
  }

  @override
  Future<List<CatalogCategory>> getFoodCategories() {
    categoryCalls++;
    final handler = onGetFoodCategories;
    if (handler != null) {
      return handler();
    }
    if (foodCategoriesError != null) {
      return Future<List<CatalogCategory>>.error(foodCategoriesError!);
    }
    return Future<List<CatalogCategory>>.value(categories);
  }

  @override
  Future<IngredientCatalogPage> getIngredients({
    String? query,
    String? categoryCode,
    int page = 0,
    int size = 20,
  }) {
    final request = CatalogPageRequest(
      query: query,
      categoryCode: categoryCode,
      page: page,
      size: size,
    );
    ingredientRequests.add(request);
    final handler = onGetIngredients;
    if (handler != null) {
      return handler(request);
    }
    if (ingredientsError != null) {
      return Future<IngredientCatalogPage>.error(ingredientsError!);
    }
    return Future<IngredientCatalogPage>.value(ingredientPage);
  }

  @override
  Future<IngredientCatalogDetail> getIngredient(String publicId) {
    ingredientDetailRequests.add(publicId);
    final handler = onGetIngredient;
    if (handler != null) {
      return handler(publicId);
    }
    if (ingredientDetailError != null) {
      return Future<IngredientCatalogDetail>.error(ingredientDetailError!);
    }
    return Future<IngredientCatalogDetail>.value(ingredientDetail);
  }
}
