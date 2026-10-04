import 'package:smart_meal_planner/features/pantry/data/pantry_models.dart';

/// Frontend checks that mirror the current Pantry request constraints.
final class PantryValidation {
  PantryValidation._();

  static PantryDecimal? decimal(String? text) {
    if (text == null) return null;
    try {
      return PantryDecimal.parse(text);
    } on FormatException {
      return null;
    }
  }

  static bool isPositiveQuantity(String? text) =>
      decimal(text)?.isPositive ?? false;

  static bool isNonzeroAdjustment(String? text) {
    final value = decimal(text);
    return value != null && !value.isZero;
  }

  static bool canAdjust({
    required PantryDecimal initial,
    required PantryDecimal remaining,
    required String quantityDelta,
    PantryItemStatus status = PantryItemStatus.available,
  }) {
    final delta = decimal(quantityDelta);
    if (status != PantryItemStatus.available || delta == null || delta.isZero) {
      return false;
    }
    final result = remaining.scaledUnits + delta.scaledUnits;
    return result > BigInt.zero && result <= initial.scaledUnits;
  }

  static bool canConsume({
    required PantryDecimal remaining,
    required String quantity,
    PantryItemStatus status = PantryItemStatus.available,
  }) {
    final amount = decimal(quantity);
    return status == PantryItemStatus.available &&
        amount != null &&
        amount.isPositive &&
        amount.compareTo(remaining) <= 0;
  }

  static bool canDiscard(PantryItemStatus status) =>
      status == PantryItemStatus.available ||
      status == PantryItemStatus.reserved;

  static bool isValidExpiry({
    required DateTime? acquiredOn,
    required DateTime? expiryDate,
    required PantryExpiryKind expiryKind,
    required PantryExpiryConfidence expiryConfidence,
  }) {
    if (expiryDate == null) {
      return expiryKind == PantryExpiryKind.unknown &&
          expiryConfidence == PantryExpiryConfidence.unknown;
    }
    if (expiryKind == PantryExpiryKind.unknown ||
        expiryConfidence == PantryExpiryConfidence.unknown) {
      return false;
    }
    return acquiredOn == null ||
        PantryDate.format(expiryDate)
                .compareTo(PantryDate.format(acquiredOn)) >=
            0;
  }

  static bool isValidNote(String? note) {
    try {
      PantryNote.normalize(note);
      return true;
    } on ArgumentError {
      return false;
    }
  }

  static bool isValidUnitCode(String? unitCode) {
    if (unitCode == null) return false;
    try {
      PantryUnitCode.normalize(unitCode);
      return true;
    } on ArgumentError {
      return false;
    }
  }
}
