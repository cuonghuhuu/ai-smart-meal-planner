import 'package:smart_meal_planner/features/pantry/data/pantry_models.dart';
import 'package:smart_meal_planner/l10n/app_strings.dart';

/// Presentation labels; backend enum values remain unchanged in the data layer.
final class PantryLocalizations {
  const PantryLocalizations._();

  static String storage(PantryStorageLocation value) => switch (value) {
    PantryStorageLocation.pantry => AppStrings.pantryStoragePantry,
    PantryStorageLocation.fridge => AppStrings.pantryStorageFridge,
    PantryStorageLocation.freezer => AppStrings.pantryStorageFreezer,
    PantryStorageLocation.other => AppStrings.pantryStorageOther,
  };

  static String status(PantryItemStatus value) => switch (value) {
    PantryItemStatus.available => AppStrings.pantryStatusAvailable,
    PantryItemStatus.reserved => AppStrings.pantryStatusReserved,
    PantryItemStatus.consumed => AppStrings.pantryStatusConsumed,
    PantryItemStatus.discarded => AppStrings.pantryStatusDiscarded,
    PantryItemStatus.expired => AppStrings.pantryStatusExpired,
  };

  static String expiryKind(PantryExpiryKind value) => switch (value) {
    PantryExpiryKind.useBy => AppStrings.pantryExpiryUseBy,
    PantryExpiryKind.bestBefore => AppStrings.pantryExpiryBestBefore,
    PantryExpiryKind.unknown => AppStrings.pantryUnknown,
  };

  static String expiryConfidence(PantryExpiryConfidence value) =>
      switch (value) {
        PantryExpiryConfidence.labelled => AppStrings.pantryConfidenceLabelled,
        PantryExpiryConfidence.estimated =>
          AppStrings.pantryConfidenceEstimated,
        PantryExpiryConfidence.unknown => AppStrings.pantryUnknown,
      };

  static String unit(PantryItem item) => item.unitDisplayName.trim().isEmpty
      ? item.unitCode
      : item.unitDisplayName;

  static String timestamp(DateTime value) =>
      '${PantryDate.format(value)} '
      '${value.hour.toString().padLeft(2, '0')}:'
      '${value.minute.toString().padLeft(2, '0')}';
}
