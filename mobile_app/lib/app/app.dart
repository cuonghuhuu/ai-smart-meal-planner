import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:smart_meal_planner/app/app_router.dart';
import 'package:smart_meal_planner/app/config/app_config.dart';
import 'package:smart_meal_planner/app/app_session_factory.dart';
import 'package:smart_meal_planner/app/theme/app_theme.dart';
import 'package:smart_meal_planner/features/auth/domain/auth_models.dart';
import 'package:smart_meal_planner/features/auth/application/session_controller.dart';
import 'package:smart_meal_planner/features/preferences/application/preferences_controller.dart';
import 'package:smart_meal_planner/features/preferences/application/disliked_ingredients_controller.dart';
import 'package:smart_meal_planner/features/measurements/application/measurements_controller.dart';
import 'package:smart_meal_planner/features/profile/application/profile_controller.dart';
import 'package:smart_meal_planner/features/catalog/application/food_catalog_controller.dart';
import 'package:smart_meal_planner/features/catalog/application/ingredient_catalog_controller.dart';
import 'package:smart_meal_planner/features/catalog/data/catalog_repository.dart';
import 'package:smart_meal_planner/features/meal_planning/application/meal_planning_controller.dart';
import 'package:smart_meal_planner/features/pantry/application/pantry_controller.dart';

class SmartMealPlannerApp extends StatefulWidget {
  const SmartMealPlannerApp({
    super.key,
    this.sessionController,
    this.profileController,
    this.preferencesController,
    this.dislikedIngredientsController,
    this.measurementsController,
    this.foodCatalogController,
    this.ingredientCatalogController,
    this.mealPlanningController,
    this.pantryController,
    this.pantryCatalogRepository,
  });

  final SessionController? sessionController;
  final ProfileController? profileController;
  final PreferencesController? preferencesController;
  final DislikedIngredientsController? dislikedIngredientsController;
  final MeasurementsController? measurementsController;
  final FoodCatalogController? foodCatalogController;
  final IngredientCatalogController? ingredientCatalogController;
  final MealPlanningController? mealPlanningController;
  final PantryController? pantryController;
  final CatalogRepository? pantryCatalogRepository;

  @override
  State<SmartMealPlannerApp> createState() => _SmartMealPlannerAppState();
}

class _SmartMealPlannerAppState extends State<SmartMealPlannerApp> {
  late final SessionController _sessionController;
  late final ProfileController? _profileController;
  late final PreferencesController? _preferencesController;
  late final DislikedIngredientsController? _dislikedIngredientsController;
  late final MeasurementsController? _measurementsController;
  late final FoodCatalogController? _foodCatalogController;
  late final IngredientCatalogController? _ingredientCatalogController;
  late final MealPlanningController? _mealPlanningController;
  late final PantryController? _pantryController;
  late final CatalogRepository? _pantryCatalogRepository;
  late final bool _ownsMealPlanningController;
  late final bool _ownsPantryController;
  late final AppRouter _appRouter;
  late SessionStatus _observedSessionStatus;
  String? _observedAuthenticatedPublicId;

  @override
  void initState() {
    super.initState();
    if (widget.sessionController == null) {
      final dependencies = AppSessionFactory.create();
      _sessionController = dependencies.sessionController;
      _profileController = dependencies.profileController;
      _preferencesController = dependencies.preferencesController;
      _dislikedIngredientsController =
          dependencies.dislikedIngredientsController;
      _measurementsController = dependencies.measurementsController;
      _foodCatalogController = dependencies.foodCatalogController;
      _ingredientCatalogController = dependencies.ingredientCatalogController;
      _mealPlanningController = MealPlanningController(
        repository: dependencies.mealPlanningRepository,
      );
      _ownsMealPlanningController = true;
      _pantryController = PantryController(
        repository: dependencies.pantryRepository,
        onInventoryChanged: _mealPlanningController?.invalidateShoppingList,
      );
      _ownsPantryController = true;
      _pantryCatalogRepository = dependencies.catalogRepository;
    } else {
      _sessionController = widget.sessionController!;
      _profileController = widget.profileController;
      _preferencesController = widget.preferencesController;
      _dislikedIngredientsController = widget.dislikedIngredientsController;
      _measurementsController = widget.measurementsController;
      _foodCatalogController = widget.foodCatalogController;
      _ingredientCatalogController = widget.ingredientCatalogController;
      _mealPlanningController = widget.mealPlanningController;
      _ownsMealPlanningController = false;
      _pantryController = widget.pantryController;
      _ownsPantryController = false;
      _pantryCatalogRepository = widget.pantryCatalogRepository;
    }
    _appRouter = AppRouter(
      _sessionController,
      profileController: _profileController,
      preferencesController: _preferencesController,
      dislikedIngredientsController: _dislikedIngredientsController,
      measurementsController: _measurementsController,
      foodCatalogController: _foodCatalogController,
      ingredientCatalogController: _ingredientCatalogController,
      mealPlanningController: _mealPlanningController,
      pantryController: _pantryController,
      pantryCatalogRepository: _pantryCatalogRepository,
    );
    _observedSessionStatus = _sessionController.status;
    _observedAuthenticatedPublicId = _sessionController.identity?.publicId;
    _sessionController.addListener(_handleSessionChanged);
    _sessionController.bootstrap();
  }

  void _handleSessionChanged() {
    final nextStatus = _sessionController.status;
    final nextPublicId = _sessionController.identity?.publicId;
    final leftAuthenticated =
        _observedSessionStatus == SessionStatus.authenticated &&
        nextStatus != SessionStatus.authenticated;
    final changedAuthenticatedPrincipal =
        _observedSessionStatus == SessionStatus.authenticated &&
        nextStatus == SessionStatus.authenticated &&
        _observedAuthenticatedPublicId != null &&
        nextPublicId != null &&
        nextPublicId != _observedAuthenticatedPublicId;

    _observedSessionStatus = nextStatus;
    _observedAuthenticatedPublicId = nextStatus == SessionStatus.authenticated
        ? nextPublicId
        : null;

    if (leftAuthenticated || changedAuthenticatedPrincipal) {
      _measurementsController?.resetForSessionChange();
      _dislikedIngredientsController?.resetForSessionChange();
      _foodCatalogController?.resetForSessionChange();
      _ingredientCatalogController?.resetForSessionChange();
      _mealPlanningController?.resetForSessionChange();
      _pantryController?.resetForSessionChange();
    }
  }

  @override
  void dispose() {
    _sessionController.removeListener(_handleSessionChanged);
    if (_ownsMealPlanningController) _mealPlanningController?.dispose();
    if (_ownsPantryController) _pantryController?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp.router(
      title: AppConfig.appName,
      debugShowCheckedModeBanner: false,
      theme: AppTheme.light,
      locale: const Locale('vi', 'VN'),
      localizationsDelegates: const [
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: const [Locale('vi', 'VN')],
      routerConfig: _appRouter.router,
    );
  }
}
