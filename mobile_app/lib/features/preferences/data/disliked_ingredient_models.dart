import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/features/catalog/data/catalog_models.dart';

enum DislikedIngredientStrength {
  dislike('DISLIKE'),
  avoid('AVOID');

  const DislikedIngredientStrength(this.wireValue);

  final String wireValue;

  static DislikedIngredientStrength fromWireValue(Object? value) =>
      switch (value) {
        'DISLIKE' => DislikedIngredientStrength.dislike,
        'AVOID' => DislikedIngredientStrength.avoid,
        _ => throw const ApiResponseFormatException(),
      };
}

final class DislikedIngredientPreference {
  const DislikedIngredientPreference({
    required this.ingredientPublicId,
    required this.ingredientCode,
    required this.ingredientDisplayName,
    required this.category,
    required this.strength,
    required this.note,
  });

  factory DislikedIngredientPreference.fromJson(Object? value) {
    final json = CatalogJson.map(value);
    return DislikedIngredientPreference(
      ingredientPublicId: CatalogJson.requiredString(
        json,
        'ingredientPublicId',
      ),
      ingredientCode: CatalogJson.requiredString(json, 'ingredientCode'),
      ingredientDisplayName: CatalogJson.requiredString(
        json,
        'ingredientDisplayName',
      ),
      category: json['category'] == null
          ? null
          : CatalogCategory.fromJson(json['category']),
      strength: DislikedIngredientStrength.fromWireValue(json['strength']),
      note: CatalogJson.optionalString(json, 'note'),
    );
  }

  final String ingredientPublicId;
  final String ingredientCode;
  final String ingredientDisplayName;
  final CatalogCategory? category;
  final DislikedIngredientStrength strength;
  final String? note;

  DislikedIngredientPreference copyWith({
    DislikedIngredientStrength? strength,
    Object? note = _unset,
  }) => DislikedIngredientPreference(
    ingredientPublicId: ingredientPublicId,
    ingredientCode: ingredientCode,
    ingredientDisplayName: ingredientDisplayName,
    category: category,
    strength: strength ?? this.strength,
    note: identical(note, _unset) ? this.note : note as String?,
  );

  DislikedIngredientSelection toSelection() => DislikedIngredientSelection(
    ingredientPublicId: ingredientPublicId,
    strength: strength,
    note: note,
  );
}

final class DislikedIngredientSelection {
  const DislikedIngredientSelection({
    required this.ingredientPublicId,
    required this.strength,
    required this.note,
  });

  final String ingredientPublicId;
  final DislikedIngredientStrength strength;
  final String? note;

  Map<String, Object?> toJson() => {
    'ingredientPublicId': ingredientPublicId,
    'strength': strength.wireValue,
    'note': _trimmedNote(note),
  };
}

const Object _unset = Object();

String? _trimmedNote(String? value) {
  if (value == null) {
    return null;
  }
  final trimmed = value.trim();
  return trimmed.isEmpty ? null : trimmed;
}
