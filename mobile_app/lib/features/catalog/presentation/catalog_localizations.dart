import 'package:smart_meal_planner/features/catalog/data/catalog_models.dart';
import 'package:smart_meal_planner/l10n/app_strings.dart';

final class CatalogLocalizations {
  const CatalogLocalizations._();

  static String categoryName(CatalogCategory? category) {
    if (category == null) {
      return AppStrings.catalogUncategorized;
    }
    return AppStrings.catalogCategoryLabels[category.code] ??
        category.displayName;
  }

  static String nutrientName(FoodNutrient nutrient) {
    return AppStrings.catalogNutrientLabels[nutrient.nutrientCode] ??
        nutrient.nutrientDisplayName;
  }

  static String enumName(String? wireValue) {
    if (wireValue == null || wireValue.isEmpty) {
      return AppStrings.catalogUnknownValue;
    }
    return AppStrings.catalogEnumLabels[wireValue] ?? wireValue;
  }

  static String formatNumber(double value) {
    if (!value.isFinite) {
      return AppStrings.catalogUnknownValue;
    }
    final fixed = value.toStringAsFixed(4);
    return fixed.replaceFirst(RegExp(r'\.?0+$'), '');
  }
}
