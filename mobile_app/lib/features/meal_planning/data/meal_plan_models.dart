import 'package:smart_meal_planner/core/api/api_exception.dart';

enum MealPlanGenerationStatus {
  succeeded('SUCCEEDED'),
  degraded('DEGRADED'),
  infeasible('INFEASIBLE');

  const MealPlanGenerationStatus(this.wireValue);
  final String wireValue;

  static MealPlanGenerationStatus fromWireValue(Object? value) =>
      switch (value) {
        'SUCCEEDED' => succeeded,
        'DEGRADED' => degraded,
        'INFEASIBLE' => infeasible,
        _ => throw const ApiResponseFormatException(),
      };
}

enum MealSlotCode {
  breakfast('BREAKFAST'),
  morningSnack('MORNING_SNACK'),
  lunch('LUNCH'),
  afternoonSnack('AFTERNOON_SNACK'),
  dinner('DINNER'),
  eveningSnack('EVENING_SNACK');

  const MealSlotCode(this.wireValue);
  final String wireValue;

  static MealSlotCode fromWireValue(Object? value) => switch (value) {
    'BREAKFAST' => breakfast,
    'MORNING_SNACK' => morningSnack,
    'LUNCH' => lunch,
    'AFTERNOON_SNACK' => afternoonSnack,
    'DINNER' => dinner,
    'EVENING_SNACK' => eveningSnack,
    _ => throw const ApiResponseFormatException(),
  };
}

enum UnfilledSlotReasonCode {
  noEligibleRecipe('NO_ELIGIBLE_RECIPE'),
  hardConstraintConflict('HARD_CONSTRAINT_CONFLICT'),
  unsupportedHardConstraint('UNSUPPORTED_HARD_CONSTRAINT'),
  pantryInfeasible('PANTRY_INFEASIBLE'),
  nutritionInfeasible('NUTRITION_INFEASIBLE'),
  searchLimitReached('SEARCH_LIMIT_REACHED');

  const UnfilledSlotReasonCode(this.wireValue);
  final String wireValue;

  static UnfilledSlotReasonCode fromWireValue(Object? value) => switch (value) {
    'NO_ELIGIBLE_RECIPE' => noEligibleRecipe,
    'HARD_CONSTRAINT_CONFLICT' => hardConstraintConflict,
    'UNSUPPORTED_HARD_CONSTRAINT' => unsupportedHardConstraint,
    'PANTRY_INFEASIBLE' => pantryInfeasible,
    'NUTRITION_INFEASIBLE' => nutritionInfeasible,
    'SEARCH_LIMIT_REACHED' => searchLimitReached,
    _ => throw const ApiResponseFormatException(),
  };
}

/// Constraints sent to the authenticated generation endpoint.
final class MealPlanGenerationRequest {
  MealPlanGenerationRequest({
    required this.startDate,
    required this.days,
    required List<MealSlotCode> requestedMealSlots,
    required this.defaultServings,
    this.maxMinutesPerMeal,
  }) : requestedMealSlots = List.unmodifiable(requestedMealSlots);

  final DateTime startDate;
  final int days;
  final List<MealSlotCode> requestedMealSlots;
  final double defaultServings;
  final int? maxMinutesPerMeal;

  Map<String, Object> toJson() {
    final json = <String, Object>{
      'startDate': formatMealPlanDate(startDate),
      'days': days,
      'requestedMealSlots': requestedMealSlots
          .map((slot) => slot.wireValue)
          .toList(growable: false),
      'defaultServings': defaultServings,
    };
    if (maxMinutesPerMeal != null) {
      json['maxMinutesPerMeal'] = maxMinutesPerMeal!;
    }
    return json;
  }
}

/// A successful or degraded result contains the ID needed to read or retry the plan.
final class MealPlanGenerationResponse {
  const MealPlanGenerationResponse({
    required this.requestPublicId,
    required this.status,
    required this.mealPlanPublicId,
  });

  factory MealPlanGenerationResponse.fromJson(Object? value) {
    final json = _object(value);
    final status = MealPlanGenerationStatus.fromWireValue(json['status']);
    final planId = json['mealPlanPublicId'];
    if (status == MealPlanGenerationStatus.infeasible) {
      if (planId != null) throw const ApiResponseFormatException();
    } else {
      _publicId(planId);
    }
    return MealPlanGenerationResponse(
      requestPublicId: _publicId(json['requestPublicId']),
      status: status,
      mealPlanPublicId: planId as String?,
    );
  }

  final String requestPublicId;
  final MealPlanGenerationStatus status;
  final String? mealPlanPublicId;
}

/// Public fields of a persisted plan. List order is the backend's plan order.
final class PersistedMealPlan {
  const PersistedMealPlan({
    required this.mealPlanPublicId,
    required this.requestPublicId,
    required this.status,
    required this.startDate,
    required this.endDate,
    required this.defaultServings,
    required this.entries,
    required this.unfilledSlots,
  });

  factory PersistedMealPlan.fromJson(Object? value) {
    final json = _object(value);
    final status = MealPlanGenerationStatus.fromWireValue(json['status']);
    if (status == MealPlanGenerationStatus.infeasible) {
      throw const ApiResponseFormatException();
    }
    return PersistedMealPlan(
      mealPlanPublicId: _publicId(json['mealPlanPublicId']),
      requestPublicId: _publicId(json['requestPublicId']),
      status: status,
      startDate: parseMealPlanDate(json['startDate']),
      endDate: parseMealPlanDate(json['endDate']),
      defaultServings: _positiveNumber(json['defaultServings']),
      entries: _list(json['entries'], MealPlanEntry.fromJson),
      unfilledSlots: _list(
        json['unfilledSlots'],
        MealPlanUnfilledSlot.fromJson,
      ),
    );
  }

  final String mealPlanPublicId;
  final String requestPublicId;
  final MealPlanGenerationStatus status;
  final DateTime startDate;
  final DateTime endDate;
  final double defaultServings;
  final List<MealPlanEntry> entries;
  final List<MealPlanUnfilledSlot> unfilledSlots;
}

final class MealPlanEntry {
  const MealPlanEntry({
    required this.planDate,
    required this.mealSlotCode,
    required this.recipePublicId,
    required this.recipeTitle,
    required this.servings,
  });

  factory MealPlanEntry.fromJson(Object? value) {
    final json = _object(value);
    return MealPlanEntry(
      planDate: parseMealPlanDate(json['planDate']),
      mealSlotCode: MealSlotCode.fromWireValue(json['mealSlotCode']),
      recipePublicId: _publicId(json['recipePublicId']),
      recipeTitle: _requiredString(json['recipeTitle']),
      servings: _positiveNumber(json['servings']),
    );
  }

  final DateTime planDate;
  final MealSlotCode mealSlotCode;
  final String recipePublicId;
  final String recipeTitle;
  final double servings;
}

final class MealPlanUnfilledSlot {
  const MealPlanUnfilledSlot({
    required this.planDate,
    required this.mealSlotCode,
    required this.reasonCode,
    required this.explanation,
  });

  factory MealPlanUnfilledSlot.fromJson(Object? value) {
    final json = _object(value);
    final explanation = json['explanation'];
    if (explanation != null && explanation is! String) {
      throw const ApiResponseFormatException();
    }
    return MealPlanUnfilledSlot(
      planDate: parseMealPlanDate(json['planDate']),
      mealSlotCode: MealSlotCode.fromWireValue(json['mealSlotCode']),
      reasonCode: UnfilledSlotReasonCode.fromWireValue(json['reasonCode']),
      explanation: explanation as String?,
    );
  }

  final DateTime planDate;
  final MealSlotCode mealSlotCode;
  final UnfilledSlotReasonCode reasonCode;
  final String? explanation;
}

/// Quantified and unquantified ingredients needed for a persisted meal plan.
final class MealPlanShoppingList {
  MealPlanShoppingList({
    required this.mealPlanPublicId,
    required this.status,
    required List<ShoppingListItem> items,
    required List<ShoppingListUnquantifiedItem> unquantifiedItems,
  }) : items = List.unmodifiable(items),
       unquantifiedItems = List.unmodifiable(unquantifiedItems);

  factory MealPlanShoppingList.fromJson(Object? value) {
    final json = _object(value);
    final status = MealPlanGenerationStatus.fromWireValue(json['status']);
    if (status == MealPlanGenerationStatus.infeasible) {
      throw const ApiResponseFormatException();
    }
    return MealPlanShoppingList(
      mealPlanPublicId: _publicId(json['mealPlanPublicId']),
      status: status,
      items: _list(json['items'], ShoppingListItem.fromJson),
      unquantifiedItems: _list(
        json['unquantifiedItems'],
        ShoppingListUnquantifiedItem.fromJson,
      ),
    );
  }

  final String mealPlanPublicId;
  final MealPlanGenerationStatus status;
  final List<ShoppingListItem> items;
  final List<ShoppingListUnquantifiedItem> unquantifiedItems;
}

final class ShoppingListItem {
  const ShoppingListItem({
    required this.ingredientPublicId,
    required this.ingredientCode,
    required this.ingredientDisplayName,
    required this.requiredQuantity,
    required this.pantryCoveredQuantity,
    required this.quantityToBuy,
    required this.unitCode,
  });

  factory ShoppingListItem.fromJson(Object? value) {
    final json = _object(value);
    return ShoppingListItem(
      ingredientPublicId: _publicId(json['ingredientPublicId']),
      ingredientCode: _requiredString(json['ingredientCode']),
      ingredientDisplayName: _requiredString(json['ingredientDisplayName']),
      requiredQuantity: _positiveNumber(json['requiredQuantity']),
      pantryCoveredQuantity: _nonNegativeNumber(json['pantryCoveredQuantity']),
      quantityToBuy: _nonNegativeNumber(json['quantityToBuy']),
      unitCode: _requiredString(json['unitCode']),
    );
  }

  final String ingredientPublicId;
  final String ingredientCode;
  final String ingredientDisplayName;
  final double requiredQuantity;
  final double pantryCoveredQuantity;
  final double quantityToBuy;
  final String unitCode;
}

final class ShoppingListUnquantifiedItem {
  const ShoppingListUnquantifiedItem({
    required this.ingredientPublicId,
    required this.ingredientCode,
    required this.ingredientDisplayName,
  });

  factory ShoppingListUnquantifiedItem.fromJson(Object? value) {
    final json = _object(value);
    return ShoppingListUnquantifiedItem(
      ingredientPublicId: _publicId(json['ingredientPublicId']),
      ingredientCode: _requiredString(json['ingredientCode']),
      ingredientDisplayName: _requiredString(json['ingredientDisplayName']),
    );
  }

  final String ingredientPublicId;
  final String ingredientCode;
  final String ingredientDisplayName;
}

String formatMealPlanDate(DateTime value) =>
    '${value.year.toString().padLeft(4, '0')}-'
    '${value.month.toString().padLeft(2, '0')}-'
    '${value.day.toString().padLeft(2, '0')}';

DateTime parseMealPlanDate(Object? value) {
  if (value is! String || !RegExp(r'^\d{4}-\d{2}-\d{2}$').hasMatch(value)) {
    throw const ApiResponseFormatException();
  }
  final date = DateTime.tryParse(value);
  if (date == null || formatMealPlanDate(date) != value) {
    throw const ApiResponseFormatException();
  }
  return date;
}

Map<String, dynamic> _object(Object? value) {
  if (value is Map<String, dynamic>) return value;
  throw const ApiResponseFormatException();
}

String _requiredString(Object? value) {
  if (value is String && value.isNotEmpty) return value;
  throw const ApiResponseFormatException();
}

final _uuidPattern = RegExp(
  r'^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$',
);

String _publicId(Object? value) {
  if (value is String && _uuidPattern.hasMatch(value)) return value;
  throw const ApiResponseFormatException();
}

double _positiveNumber(Object? value) {
  if (value is num && value.isFinite && value > 0) return value.toDouble();
  throw const ApiResponseFormatException();
}

double _nonNegativeNumber(Object? value) {
  if (value is num && value.isFinite && value >= 0) return value.toDouble();
  throw const ApiResponseFormatException();
}

List<T> _list<T>(Object? value, T Function(Object?) parse) {
  if (value is! List) throw const ApiResponseFormatException();
  return List.unmodifiable(value.map(parse));
}
