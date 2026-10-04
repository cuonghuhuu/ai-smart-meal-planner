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
