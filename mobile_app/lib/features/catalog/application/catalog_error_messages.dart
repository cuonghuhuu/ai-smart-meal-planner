import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/l10n/app_strings.dart';

String catalogLoadErrorMessage(Object error, {bool detail = false}) {
  if (error is ApiTransportException) {
    return AppStrings.unableToReachService;
  }
  if (error is ApiHttpException) {
    if (error.statusCode >= 500) {
      return AppStrings.serviceUnavailable;
    }
    if (error.statusCode == 400 || error.statusCode == 404) {
      return AppStrings.requestFailed;
    }
  }
  return detail ? AppStrings.catalogDetailLoadFailed : AppStrings.catalogLoadFailed;
}

String catalogLoadMoreErrorMessage(Object error) {
  if (error is ApiTransportException) {
    return AppStrings.unableToReachService;
  }
  if (error is ApiHttpException && error.statusCode >= 500) {
    return AppStrings.serviceUnavailable;
  }
  return AppStrings.catalogLoadMoreFailed;
}
