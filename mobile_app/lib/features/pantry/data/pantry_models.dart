import 'package:smart_meal_planner/features/catalog/data/catalog_models.dart';

final class PantryItem {
  const PantryItem({
    required this.publicId,
    required this.ingredientPublicId,
    required this.ingredientCode,
    required this.ingredientName,
    required this.foodPublicId,
    required this.foodCode,
    required this.foodName,
    required this.quantityInitial,
    required this.quantityRemaining,
    required this.unitCode,
    required this.unitDisplayName,
    required this.storageLocation,
    required this.acquiredOn,
    required this.expiryDate,
    required this.expiryKind,
    required this.expiryConfidence,
    required this.status,
    required this.closedAt,
    required this.note,
    required this.version,
    required this.createdAt,
    required this.updatedAt,
  });

  factory PantryItem.fromJson(Object? value) {
    final json = CatalogJson.map(value);
    return PantryItem(
      publicId: CatalogJson.requiredString(json, 'publicId'),
      ingredientPublicId: CatalogJson.requiredString(
        json,
        'ingredientPublicId',
      ),
      ingredientCode: CatalogJson.requiredString(json, 'ingredientCode'),
      ingredientName: CatalogJson.requiredString(json, 'ingredientName'),
      foodPublicId: CatalogJson.optionalString(json, 'foodPublicId'),
      foodCode: CatalogJson.optionalString(json, 'foodCode'),
      foodName: CatalogJson.optionalString(json, 'foodName'),
      quantityInitial: CatalogJson.requiredDouble(json, 'quantityInitial'),
      quantityRemaining: CatalogJson.requiredDouble(
        json,
        'quantityRemaining',
      ),
      unitCode: CatalogJson.requiredString(json, 'unitCode'),
      unitDisplayName: CatalogJson.requiredString(json, 'unitDisplayName'),
      storageLocation: CatalogJson.requiredString(json, 'storageLocation'),
      acquiredOn: CatalogJson.optionalString(json, 'acquiredOn'),
      expiryDate: CatalogJson.optionalString(json, 'expiryDate'),
      expiryKind: CatalogJson.requiredString(json, 'expiryKind'),
      expiryConfidence: CatalogJson.requiredString(
        json,
        'expiryConfidence',
      ),
      status: CatalogJson.requiredString(json, 'status'),
      closedAt: CatalogJson.optionalString(json, 'closedAt'),
      note: CatalogJson.optionalString(json, 'note'),
      version: CatalogJson.optionalInt(json, 'version'),
      createdAt: CatalogJson.requiredString(json, 'createdAt'),
      updatedAt: CatalogJson.requiredString(json, 'updatedAt'),
    );
  }

  final String publicId;
  final String ingredientPublicId;
  final String ingredientCode;
  final String ingredientName;
  final String? foodPublicId;
  final String? foodCode;
  final String? foodName;
  final double quantityInitial;
  final double quantityRemaining;
  final String unitCode;
  final String unitDisplayName;
  final String storageLocation;
  final String? acquiredOn;
  final String? expiryDate;
  final String expiryKind;
  final String expiryConfidence;
  final String status;
  final String? closedAt;
  final String? note;
  final int? version;
  final String createdAt;
  final String updatedAt;
}

final class PantryCreateRequest {
  const PantryCreateRequest({
    required this.ingredientPublicId,
    required this.foodPublicId,
    required this.quantity,
    required this.unitCode,
    required this.storageLocation,
    required this.acquiredOn,
    required this.expiryDate,
    required this.expiryKind,
    required this.expiryConfidence,
    required this.note,
  });

  final String ingredientPublicId;
  final String? foodPublicId;
  final double quantity;
  final String unitCode;
  final String storageLocation;
  final String? acquiredOn;
  final String? expiryDate;
  final String expiryKind;
  final String expiryConfidence;
  final String? note;

  Map<String, Object?> toJson() => {
    'ingredientPublicId': ingredientPublicId,
    'foodPublicId': foodPublicId,
    'quantity': quantity,
    'unitCode': unitCode,
    'storageLocation': storageLocation,
    'acquiredOn': acquiredOn,
    'expiryDate': expiryDate,
    'expiryKind': expiryKind,
    'expiryConfidence': expiryConfidence,
    'note': note,
  };
}

final class PantryMetadataUpdate {
  const PantryMetadataUpdate({
    required this.storageLocation,
    required this.acquiredOn,
    required this.expiryDate,
    required this.expiryKind,
    required this.expiryConfidence,
    required this.note,
  });

  final String storageLocation;
  final String? acquiredOn;
  final String? expiryDate;
  final String expiryKind;
  final String expiryConfidence;
  final String? note;

  Map<String, Object?> toJson() => {
    'storageLocation': storageLocation,
    'acquiredOn': acquiredOn,
    'expiryDate': expiryDate,
    'expiryKind': expiryKind,
    'expiryConfidence': expiryConfidence,
    'note': note,
  };
}

final class PantryQuantityAdjustment {
  const PantryQuantityAdjustment({required this.quantityDelta, this.note});

  final double quantityDelta;
  final String? note;

  Map<String, Object?> toJson() => {
    'quantityDelta': quantityDelta,
    'note': note,
  };
}

final class PantryQuantityConsumption {
  const PantryQuantityConsumption({required this.quantity, this.note});

  final double quantity;
  final String? note;

  Map<String, Object?> toJson() => {'quantity': quantity, 'note': note};
}
