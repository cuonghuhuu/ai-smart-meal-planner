import 'package:smart_meal_planner/features/catalog/data/catalog_models.dart';
import 'package:smart_meal_planner/features/preferences/data/disliked_ingredient_models.dart';
import 'package:smart_meal_planner/features/preferences/data/disliked_ingredients_repository.dart';

final class FakeDislikedIngredientsRepository
    implements DislikedIngredientsRepository {
  FakeDislikedIngredientsRepository({
    List<DislikedIngredientPreference>? preferences,
    this.loadError,
    this.saveError,
    this.saveResponse,
  }) : preferences = preferences == null ? [] : [...preferences];

  List<DislikedIngredientPreference> preferences;
  final Object? loadError;
  final Object? saveError;
  final List<DislikedIngredientPreference>? saveResponse;
  final List<List<DislikedIngredientSelection>> saveCalls = [];
  Future<List<DislikedIngredientPreference>> Function()? onGet;
  Future<List<DislikedIngredientPreference>> Function(
    List<DislikedIngredientSelection> selections,
  )? onReplace;

  @override
  Future<List<DislikedIngredientPreference>> getDislikedIngredients() {
    final handler = onGet;
    if (handler != null) {
      return handler();
    }
    if (loadError != null) {
      return Future<List<DislikedIngredientPreference>>.error(loadError!);
    }
    return Future<List<DislikedIngredientPreference>>.value([...preferences]);
  }

  @override
  Future<List<DislikedIngredientPreference>> replaceDislikedIngredients(
    List<DislikedIngredientSelection> selections,
  ) {
    saveCalls.add([...selections]);
    final handler = onReplace;
    if (handler != null) {
      return handler(selections);
    }
    if (saveError != null) {
      return Future<List<DislikedIngredientPreference>>.error(saveError!);
    }
    return Future<List<DislikedIngredientPreference>>.value(
      saveResponse ??
          [
            for (final selection in selections)
              _preferenceForSelection(selection),
          ],
    );
  }
}

DislikedIngredientPreference _preferenceForSelection(
  DislikedIngredientSelection selection,
) => DislikedIngredientPreference(
  ingredientPublicId: selection.ingredientPublicId,
  ingredientCode: 'ING_${selection.ingredientPublicId}',
  ingredientDisplayName: 'Test ingredient ${selection.ingredientPublicId}',
  category: const CatalogCategory(
    code: 'GRAINS',
    displayName: 'Grains',
    parentCategoryCode: null,
    description: null,
  ),
  strength: selection.strength,
  note: selection.note?.trim().isEmpty ?? true
      ? null
      : selection.note!.trim(),
);
