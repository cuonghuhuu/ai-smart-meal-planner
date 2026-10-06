import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/features/recipes/application/recipe_controller.dart';
import 'package:smart_meal_planner/features/recipes/data/recipe_models.dart';
import 'package:smart_meal_planner/features/recipes/data/recipe_repository.dart';
import 'package:smart_meal_planner/features/recipes/presentation/recipe_detail_page.dart';
import 'package:smart_meal_planner/l10n/app_strings.dart';

const _tag = RecipeTag(
  code: 'VEGAN',
  displayName: 'Vegan',
  tagKind: RecipeTagKind.diet,
);
const _slot = RecipeMealSlot(
  code: 'DINNER',
  displayName: 'Dinner',
  displayOrder: 1,
  typicalTime: null,
  mainMeal: true,
);
const _ingredients = [
  RecipeIngredient(
    lineNumber: 2,
    ingredientPublicId: 'rice-id',
    ingredientCode: 'RICE',
    ingredientDisplayName: 'Rice',
    foodPublicId: null,
    foodCode: null,
    foodDisplayName: null,
    quantity: 1.5,
    unitCode: 'CUP',
    unitDisplayName: 'cups',
    preparationNote: 'Rinse well',
    optional: false,
    allowSubstitution: false,
    sectionLabel: 'Base',
  ),
  RecipeIngredient(
    lineNumber: 1,
    ingredientPublicId: 'basil-id',
    ingredientCode: null,
    ingredientDisplayName: 'Basil',
    foodPublicId: null,
    foodCode: null,
    foodDisplayName: null,
    quantity: null,
    unitCode: null,
    unitDisplayName: null,
    preparationNote: null,
    optional: true,
    allowSubstitution: false,
    sectionLabel: 'Garnish',
  ),
];
const _steps = [
  RecipeStep(stepNumber: 3, instruction: 'Cook the rice', durationMinutes: 15),
  RecipeStep(stepNumber: 1, instruction: 'Serve warm', durationMinutes: null),
];
const _nutrition = RecipeNutritionSnapshot(
  computedAt: '2026-09-01T10:00:00',
  ingredientRevision: 2,
  completenessRatio: 0.75,
  computationNote: 'One ingredient estimated',
  values: [
    RecipeNutritionValue(
      nutrientCode: 'ENERGY',
      nutrientDisplayName: 'Energy',
      amountPerServing: 250.5,
      unitCode: 'KCAL',
      unitDisplayName: 'kcal',
    ),
    RecipeNutritionValue(
      nutrientCode: 'PROTEIN',
      nutrientDisplayName: 'Protein',
      amountPerServing: 12.25,
      unitCode: 'G',
      unitDisplayName: 'g',
    ),
  ],
);

RecipeDetail _detail(
  String id, {
  String? summary = 'Fresh and quick',
  int? prepMinutes = 5,
  int? cookMinutes = 15,
  String? imageUrl,
  List<RecipeIngredient> ingredients = _ingredients,
  List<RecipeStep> steps = _steps,
  RecipeNutritionSnapshot? nutrition = _nutrition,
}) => RecipeDetail(
  publicId: id,
  title: id == 'recipe-b' ? 'Bean stew' : 'Rice bowl',
  slug: 'rice-bowl',
  summary: summary,
  servings: 2,
  prepMinutes: prepMinutes,
  cookMinutes: cookMinutes,
  totalMinutes: 20,
  difficulty: RecipeDifficulty.easy,
  instructionsNote: 'Use a covered pan',
  imageUrl: imageUrl,
  source: RecipeSource.curated,
  sourceReference: null,
  status: RecipeStatus.published,
  publishedAt: '2026-09-01T10:00:00',
  ingredients: ingredients,
  steps: steps,
  tags: const [_tag],
  mealSlots: const [_slot],
  nutrition: nutrition,
);

class _FakeRecipeRepository implements RecipeRepository {
  final detailIds = <String>[];
  Future<RecipeDetail> Function(String)? onDetail;

  @override
  Future<RecipeDetail> getRecipe(String publicId) {
    detailIds.add(publicId);
    return onDetail?.call(publicId) ?? Future.value(_detail(publicId));
  }

  @override
  Future<RecipePage> getRecipes({
    String? query,
    String? mealSlotCode,
    String? tagCode,
    int? maxMinutes,
    int page = 0,
    int size = 20,
  }) => throw UnimplementedError();

  @override
  Future<List<RecipeTag>> getRecipeTags() => throw UnimplementedError();

  @override
  Future<List<RecipeMealSlot>> getMealSlotTypes() => throw UnimplementedError();
}

void main() {
  late _FakeRecipeRepository repository;
  late RecipeController controller;

  setUp(() {
    repository = _FakeRecipeRepository();
    controller = RecipeController(repository: repository);
  });
  tearDown(() => controller.dispose());

  Future<void> show(WidgetTester tester, {String id = 'recipe-a'}) async {
    await tester.pumpWidget(
      MaterialApp(
        home: RecipeDetailPage(controller: controller, publicId: id),
      ),
    );
    await tester.pump();
  }

  testWidgets('loads requested detail once and shows loading state', (
    tester,
  ) async {
    final pending = Completer<RecipeDetail>();
    repository.onDetail = (_) => pending.future;
    await show(tester);
    expect(repository.detailIds, ['recipe-a']);
    expect(find.byKey(const ValueKey('recipe-detail-loading')), findsOneWidget);
    await show(tester);
    expect(repository.detailIds, ['recipe-a']);
    pending.complete(_detail('recipe-a'));
    await tester.pumpAndSettle();
    expect(find.text('Rice bowl'), findsOneWidget);
  });

  testWidgets('reuses matching detail already loaded in the controller', (
    tester,
  ) async {
    await controller.loadDetail('recipe-a');
    await show(tester);
    await tester.pumpAndSettle();
    expect(repository.detailIds, ['recipe-a']);
    expect(find.text('Rice bowl'), findsOneWidget);
  });

  testWidgets('renders header metadata, tags, slots and image placeholder', (
    tester,
  ) async {
    await show(tester);
    await tester.pumpAndSettle();
    expect(find.text('Rice bowl'), findsOneWidget);
    expect(find.text('Fresh and quick'), findsOneWidget);
    expect(find.text('${AppStrings.recipeServingsLabel}: 2'), findsOneWidget);
    expect(
      find.text(
        '${AppStrings.recipeDifficulty}: ${AppStrings.recipeDifficultyEasy}',
      ),
      findsOneWidget,
    );
    expect(
      find.text('${AppStrings.recipePrep}: 5 ${AppStrings.recipeMinutes}'),
      findsOneWidget,
    );
    expect(
      find.text('${AppStrings.recipeCook}: 15 ${AppStrings.recipeMinutes}'),
      findsOneWidget,
    );
    expect(
      find.text(
        '${AppStrings.recipeTotalTime}: 20 ${AppStrings.recipeMinutes}',
      ),
      findsOneWidget,
    );
    expect(find.text('Vegan'), findsOneWidget);
    expect(find.text('Dinner'), findsOneWidget);
    expect(find.text(AppStrings.recipeImageUnavailable), findsOneWidget);
  });

  testWidgets('failed network image uses the fallback', (tester) async {
    repository.onDetail = (id) async =>
        _detail(id, imageUrl: 'https://example.invalid/recipe.png');
    await show(tester);
    await tester.pumpAndSettle();
    expect(find.text(AppStrings.recipeImageUnavailable), findsOneWidget);
  });

  testWidgets('renders ingredients and steps in backend order', (tester) async {
    await show(tester);
    await tester.pumpAndSettle();
    expect(find.text(AppStrings.recipeIngredients), findsOneWidget);
    expect(find.text('Base'), findsOneWidget);
    expect(find.text('Garnish'), findsOneWidget);
    expect(find.text('Rice'), findsOneWidget);
    expect(find.text('1.5 cups'), findsOneWidget);
    expect(
      find.text('${AppStrings.recipePreparationNote}: Rinse well'),
      findsOneWidget,
    );
    expect(find.text('Basil'), findsOneWidget);
    expect(find.text(AppStrings.recipeOptional), findsOneWidget);
    expect(
      tester.getTopLeft(find.text('Rice')).dy,
      lessThan(tester.getTopLeft(find.text('Basil')).dy),
    );
    expect(
      find.text('${AppStrings.recipeInstructionsNote}: Use a covered pan'),
      findsOneWidget,
    );
    expect(find.text('Cook the rice'), findsOneWidget);
    expect(find.text('Serve warm'), findsOneWidget);
    expect(
      find.text(
        '${AppStrings.recipeStepDuration}: 15 ${AppStrings.recipeMinutes}',
      ),
      findsOneWidget,
    );
    expect(
      tester.getTopLeft(find.text('Cook the rice')).dy,
      lessThan(tester.getTopLeft(find.text('Serve warm')).dy),
    );
  });

  testWidgets('renders current nutrition values and snapshot metadata', (
    tester,
  ) async {
    await show(tester);
    await tester.pumpAndSettle();
    expect(find.text(AppStrings.recipeNutrition), findsOneWidget);
    expect(find.text(AppStrings.recipeNutritionPerServing), findsOneWidget);
    expect(find.text('Energy'), findsOneWidget);
    expect(find.text('250.5 kcal'), findsOneWidget);
    expect(find.text('Protein'), findsOneWidget);
    expect(find.text('12.25 g'), findsOneWidget);
    expect(
      find.text('${AppStrings.recipeNutritionCompleteness}: 0.75'),
      findsOneWidget,
    );
    expect(
      find.text('${AppStrings.recipeNutritionComputedAt}: 2026-09-01T10:00:00'),
      findsOneWidget,
    );
    expect(
      find.text('${AppStrings.recipeNutritionNote}: One ingredient estimated'),
      findsOneWidget,
    );
    expect(find.text(AppStrings.recipeNutritionUnavailable), findsNothing);
  });

  testWidgets(
    'null nutrition and empty children have intentional empty states',
    (tester) async {
      repository.onDetail = (id) async => _detail(
        id,
        summary: null,
        prepMinutes: null,
        cookMinutes: null,
        ingredients: const [],
        steps: const [],
        nutrition: null,
      );
      await show(tester);
      await tester.pumpAndSettle();
      expect(find.text(AppStrings.recipeNoIngredients), findsOneWidget);
      expect(find.text(AppStrings.recipeNoSteps), findsOneWidget);
      expect(find.text(AppStrings.recipeNutritionUnavailable), findsOneWidget);
      expect(find.text('0 kcal'), findsNothing);
      expect(find.textContaining('${AppStrings.recipePrep}:'), findsNothing);
      expect(find.textContaining('${AppStrings.recipeCook}:'), findsNothing);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('detail failure shows safe message and retries the same ID', (
    tester,
  ) async {
    var fail = true;
    repository.onDetail = (id) async {
      if (fail) throw const ApiResponseFormatException();
      return _detail(id);
    };
    await show(tester);
    await tester.pumpAndSettle();
    expect(find.text(AppStrings.catalogDetailLoadFailed), findsOneWidget);
    expect(find.textContaining('ApiResponseFormatException'), findsNothing);
    expect(find.byKey(const ValueKey('recipe-detail-retry')), findsOneWidget);
    fail = false;
    await tester.tap(find.byKey(const ValueKey('recipe-detail-retry')));
    await tester.pumpAndSettle();
    expect(repository.detailIds, ['recipe-a', 'recipe-a']);
    expect(find.text('Rice bowl'), findsOneWidget);
  });

  testWidgets('changing public ID ignores the late previous response', (
    tester,
  ) async {
    final first = Completer<RecipeDetail>();
    final second = Completer<RecipeDetail>();
    repository.onDetail = (id) =>
        id == 'recipe-a' ? first.future : second.future;
    await show(tester);
    await show(tester, id: 'recipe-b');
    expect(repository.detailIds, ['recipe-a', 'recipe-b']);
    expect(find.byKey(const ValueKey('recipe-detail-loading')), findsOneWidget);
    second.complete(_detail('recipe-b'));
    await tester.pumpAndSettle();
    expect(find.text('Bean stew'), findsOneWidget);
    first.complete(_detail('recipe-a'));
    await tester.pumpAndSettle();
    expect(find.text('Bean stew'), findsOneWidget);
    expect(find.text('Rice bowl'), findsNothing);
  });

  testWidgets('narrow and wide detail layouts do not overflow', (tester) async {
    await tester.binding.setSurfaceSize(const Size(360, 800));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await show(tester);
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    final narrowIngredientsY = tester
        .getTopLeft(find.text(AppStrings.recipeIngredients))
        .dy;
    final narrowStepsY = tester
        .getTopLeft(find.text(AppStrings.recipeSteps))
        .dy;
    expect(narrowIngredientsY, lessThan(narrowStepsY));
    await tester.binding.setSurfaceSize(const Size(1200, 800));
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    final ingredientsX = tester
        .getTopLeft(find.text(AppStrings.recipeIngredients))
        .dx;
    final stepsX = tester.getTopLeft(find.text(AppStrings.recipeSteps)).dx;
    expect(ingredientsX, lessThan(stepsX));
  });
}
