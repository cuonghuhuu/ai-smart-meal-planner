import 'package:flutter/foundation.dart';
import 'package:smart_meal_planner/core/api/api_client.dart';
import 'package:smart_meal_planner/features/auth/application/session_controller.dart';
import 'package:smart_meal_planner/features/auth/data/auth_repository.dart';
import 'package:smart_meal_planner/features/auth/data/refresh_token_store.dart';
import 'package:smart_meal_planner/features/profile/application/profile_controller.dart';
import 'package:smart_meal_planner/features/profile/data/profile_repository.dart';
import 'package:smart_meal_planner/features/profile/data/reference_data_repository.dart';
import 'package:smart_meal_planner/features/preferences/application/preferences_controller.dart';
import 'package:smart_meal_planner/features/preferences/application/disliked_ingredients_controller.dart';
import 'package:smart_meal_planner/features/preferences/data/preferences_repository.dart';
import 'package:smart_meal_planner/features/preferences/data/disliked_ingredients_repository.dart';
import 'package:smart_meal_planner/features/measurements/application/measurements_controller.dart';
import 'package:smart_meal_planner/features/measurements/data/measurements_repository.dart';
import 'package:smart_meal_planner/features/catalog/application/food_catalog_controller.dart';
import 'package:smart_meal_planner/features/catalog/application/ingredient_catalog_controller.dart';
import 'package:smart_meal_planner/features/catalog/data/catalog_repository.dart';
import 'package:smart_meal_planner/features/catalog/data/ingredient_recognition_repository.dart';
import 'package:smart_meal_planner/features/meal_planning/data/meal_planning_repository.dart';
import 'package:smart_meal_planner/features/meal_planning/data/nutrition_target_repository.dart';
import 'package:smart_meal_planner/features/meal_planning/application/meal_planning_prerequisites.dart';
import 'package:smart_meal_planner/features/pantry/data/pantry_repository.dart';
import 'package:smart_meal_planner/features/recipes/data/recipe_repository.dart';

final class AppSessionDependencies {
  const AppSessionDependencies({
    required this.sessionController,
    required this.profileController,
    required this.preferencesController,
    required this.dislikedIngredientsController,
    required this.measurementsController,
    required this.foodCatalogController,
    required this.ingredientCatalogController,
    required this.mealPlanningRepository,
    required this.mealPlanningPrerequisites,
    required this.pantryRepository,
    required this.catalogRepository,
    required this.recipeRepository,
  });

  final SessionController sessionController;
  final ProfileController profileController;
  final PreferencesController preferencesController;
  final DislikedIngredientsController dislikedIngredientsController;
  final MeasurementsController measurementsController;
  final FoodCatalogController foodCatalogController;
  final IngredientCatalogController ingredientCatalogController;
  final MealPlanningRepository mealPlanningRepository;
  final MealPlanningPrerequisites mealPlanningPrerequisites;
  final PantryRepository pantryRepository;
  final CatalogRepository catalogRepository;
  final RecipeRepository recipeRepository;
}

final class AppSessionFactory {
  const AppSessionFactory._();

  static AppSessionDependencies create() {
    final apiClient = ApiClient();
    final authRepository = HttpAuthRepository(apiClient);
    final session = SessionController(
      authRepository: authRepository,
      refreshTokenStore: kIsWeb
          ? NoRefreshTokenStore()
          : FlutterSecureRefreshTokenStore(),
      isWeb: kIsWeb,
    );
    apiClient.configureAuthentication(
      accessTokenProvider: () => session.accessToken,
      refreshAccessToken: session.refresh,
    );
    if (kIsWeb) {
      apiClient.configureCsrfHeaders(() async {
        final token = await authRepository.fetchCsrf();
        return {token.headerName: token.value};
      });
    }
    final catalogRepository = HttpCatalogRepository(apiClient);
    final profileRepository = HttpProfileRepository(apiClient);
    final preferencesRepository = HttpPreferencesRepository(apiClient);
    final dislikedRepository = HttpDislikedIngredientsRepository(apiClient);
    final measurementsRepository = HttpMeasurementsRepository(apiClient);
    final pantryRepository = HttpPantryRepository(
      apiClient,
      csrfTokenProvider: kIsWeb ? authRepository.fetchCsrf : null,
    );
    final recipeRepository = HttpRecipeRepository(apiClient);
    return AppSessionDependencies(
      sessionController: session,
      profileController: ProfileController(
        profileRepository: profileRepository,
        referenceDataRepository: HttpReferenceDataRepository(apiClient),
      ),
      preferencesController: PreferencesController(
        repository: preferencesRepository,
      ),
      dislikedIngredientsController: DislikedIngredientsController(
        repository: dislikedRepository,
        catalogRepository: catalogRepository,
      ),
      measurementsController: MeasurementsController(
        repository: measurementsRepository,
      ),
      foodCatalogController: FoodCatalogController(
        repository: catalogRepository,
      ),
      ingredientCatalogController: IngredientCatalogController(
        repository: catalogRepository,
        recognitionRepository: HttpIngredientRecognitionRepository(apiClient),
      ),
      mealPlanningRepository: HttpMealPlanningRepository(
        apiClient,
        csrfTokenProvider: kIsWeb ? authRepository.fetchCsrf : null,
      ),
      mealPlanningPrerequisites: MealPlanningPrerequisites(
        profile: profileRepository,
        measurements: measurementsRepository,
        nutritionTarget: HttpNutritionTargetRepository(apiClient),
        preferences: preferencesRepository,
        dislikedIngredients: dislikedRepository,
        pantry: pantryRepository,
        recipes: recipeRepository,
      ),
      pantryRepository: pantryRepository,
      catalogRepository: catalogRepository,
      recipeRepository: recipeRepository,
    );
  }
}
