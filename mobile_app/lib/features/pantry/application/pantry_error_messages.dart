import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/l10n/app_strings.dart';

String pantryReadErrorMessage(Object error, {bool detail = false}) {
  if (error is ApiTransportException) return AppStrings.unableToReachService;
  if (error is ApiHttpException) {
    if (detail && error.statusCode == 404) return AppStrings.pantryNotFound;
    if (error.statusCode >= 500) return AppStrings.serviceUnavailable;
  }
  return detail
      ? AppStrings.pantryDetailLoadFailed
      : AppStrings.pantryLoadFailed;
}

String pantryMutationErrorMessage(Object error) {
  if (error is ApiTransportException) return AppStrings.unableToReachService;
  if (error is ApiHttpException) {
    if (error.statusCode >= 500) return AppStrings.serviceUnavailable;
    switch (error.problem?.code) {
      case 'INGREDIENT_NOT_FOUND':
        return AppStrings.pantryIngredientNotFound;
      case 'FOOD_NOT_FOUND':
      case 'FOOD_NOT_MAPPED':
        return AppStrings.pantryFoodNotMapped;
      case 'UNIT_NOT_FOUND':
        return AppStrings.pantryUnitNotFound;
      case 'PANTRY_ITEM_NOT_FOUND':
        return AppStrings.pantryNotFound;
      case 'ITEM_NOT_OPEN':
        return AppStrings.pantryItemNotOpen;
      case 'CONFLICT':
        return AppStrings.pantryConflict;
      case 'INVALID_REQUEST':
        return AppStrings.pantryInvalidRequest;
      case 'UNAUTHORIZED':
      case 'FORBIDDEN':
        return AppStrings.pantryAccessFailed;
    }
    if (error.statusCode == 401 || error.statusCode == 403) {
      return AppStrings.pantryAccessFailed;
    }
    if (error.statusCode == 409) return AppStrings.pantryConflict;
  }
  return AppStrings.pantrySaveFailed;
}
