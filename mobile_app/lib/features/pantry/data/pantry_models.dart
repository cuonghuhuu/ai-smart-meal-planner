import 'package:smart_meal_planner/core/api/api_exception.dart';

/// An exact DECIMAL(12,4) quantity. Arithmetic and comparisons use scaled units.
final class PantryDecimal implements Comparable<PantryDecimal> {
  PantryDecimal._(this.scaledUnits);

  static final BigInt _scale = BigInt.from(10000);
  static final BigInt _maximum = BigInt.from(999999999999);
  static final RegExp _decimalPattern = RegExp(r'^-?[0-9]+(?:\.[0-9]{1,4})?$');

  /// The signed number of ten-thousandths represented by this value.
  final BigInt scaledUnits;

  static PantryDecimal parse(String text) {
    final value = text.trim();
    if (!_decimalPattern.hasMatch(value)) {
      throw FormatException('Invalid pantry decimal');
    }
    final unsigned = value.startsWith('-') ? value.substring(1) : value;
    final parts = unsigned.split('.');
    final whole = BigInt.parse(parts[0]);
    final fraction = parts.length == 1
        ? BigInt.zero
        : BigInt.parse(parts[1].padRight(4, '0'));
    final scaled =
        (whole * _scale + fraction) *
        (value.startsWith('-') ? BigInt.from(-1) : BigInt.one);
    if (scaled.abs() > _maximum) {
      throw FormatException('Pantry decimal exceeds DECIMAL(12,4)');
    }
    return PantryDecimal._(scaled);
  }

  static PantryDecimal fromJson(Object? value) {
    if (value is! num || !value.isFinite) {
      throw const ApiResponseFormatException();
    }
    try {
      return parse(value.toString());
    } on FormatException {
      throw const ApiResponseFormatException();
    }
  }

  bool get isPositive => scaledUnits > BigInt.zero;
  bool get isNegative => scaledUnits < BigInt.zero;
  bool get isZero => scaledUnits == BigInt.zero;

  /// JSON numbers retain the decimal value; no double is used for validation.
  num toJsonNumber() => num.parse(toString());

  num toPositiveJsonNumber() {
    if (!isPositive) throw ArgumentError.value(toString(), 'quantity');
    return toJsonNumber();
  }

  num toNonzeroJsonNumber() {
    if (isZero) throw ArgumentError.value(toString(), 'quantityDelta');
    return toJsonNumber();
  }

  @override
  int compareTo(PantryDecimal other) =>
      scaledUnits.compareTo(other.scaledUnits);

  @override
  String toString() {
    final absolute = scaledUnits.abs();
    final whole = absolute ~/ _scale;
    final fractional = (absolute % _scale).toString().padLeft(4, '0');
    final digits = fractional.replaceFirst(RegExp(r'0+$'), '');
    return '${isNegative ? '-' : ''}$whole${digits.isEmpty ? '' : '.$digits'}';
  }

  @override
  bool operator ==(Object other) =>
      other is PantryDecimal && scaledUnits == other.scaledUnits;

  @override
  int get hashCode => scaledUnits.hashCode;
}

final class PantryPublicId {
  PantryPublicId._();

  static final RegExp _pattern = RegExp(
    r'^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$',
  );

  static String normalize(String value) {
    final normalized = value.trim();
    if (!_pattern.hasMatch(normalized)) {
      throw ArgumentError.value(value, 'publicId', 'must be a UUID');
    }
    return normalized;
  }
}

final class PantryUnitCode {
  PantryUnitCode._();

  static String normalize(String value) {
    final normalized = value.trim();
    if (normalized.isEmpty) {
      throw ArgumentError.value(value, 'unitCode', 'must be nonblank');
    }
    return normalized;
  }
}

final class PantryNote {
  PantryNote._();

  static String? normalize(String? value) {
    final normalized = value?.trim();
    if (normalized == null || normalized.isEmpty) return null;
    if (normalized.length > 255) {
      throw ArgumentError.value(value, 'note', 'maximum length is 255');
    }
    return normalized;
  }
}

/// Calendar dates remain local calendar fields; no UTC conversion is applied.
final class PantryDate {
  PantryDate._();

  static DateTime? parseOptional(Object? value) {
    if (value == null) return null;
    if (value is! String || !RegExp(r'^\d{4}-\d{2}-\d{2}$').hasMatch(value)) {
      throw const ApiResponseFormatException();
    }
    final year = int.parse(value.substring(0, 4));
    final month = int.parse(value.substring(5, 7));
    final day = int.parse(value.substring(8, 10));
    final date = DateTime(year, month, day);
    if (format(date) != value) throw const ApiResponseFormatException();
    return date;
  }

  static String format(DateTime value) =>
      '${value.year.toString().padLeft(4, '0')}-'
      '${value.month.toString().padLeft(2, '0')}-'
      '${value.day.toString().padLeft(2, '0')}';
}

enum PantryStorageLocation {
  pantry('PANTRY'),
  fridge('FRIDGE'),
  freezer('FREEZER'),
  other('OTHER');

  const PantryStorageLocation(this.wireValue);
  final String wireValue;

  static PantryStorageLocation fromWireValue(Object? value) => switch (value) {
    'PANTRY' => pantry,
    'FRIDGE' => fridge,
    'FREEZER' => freezer,
    'OTHER' => other,
    _ => throw const ApiResponseFormatException(),
  };
}

enum PantryItemStatus {
  available('AVAILABLE'),
  reserved('RESERVED'),
  consumed('CONSUMED'),
  discarded('DISCARDED'),
  expired('EXPIRED');

  const PantryItemStatus(this.wireValue);
  final String wireValue;

  static PantryItemStatus fromWireValue(Object? value) => switch (value) {
    'AVAILABLE' => available,
    'RESERVED' => reserved,
    'CONSUMED' => consumed,
    'DISCARDED' => discarded,
    'EXPIRED' => expired,
    _ => throw const ApiResponseFormatException(),
  };
}

enum PantryExpiryKind {
  useBy('USE_BY'),
  bestBefore('BEST_BEFORE'),
  unknown('UNKNOWN');

  const PantryExpiryKind(this.wireValue);
  final String wireValue;

  static PantryExpiryKind fromWireValue(Object? value) => switch (value) {
    'USE_BY' => useBy,
    'BEST_BEFORE' => bestBefore,
    'UNKNOWN' => unknown,
    _ => throw const ApiResponseFormatException(),
  };
}

enum PantryExpiryConfidence {
  labelled('LABELLED'),
  estimated('ESTIMATED'),
  unknown('UNKNOWN');

  const PantryExpiryConfidence(this.wireValue);
  final String wireValue;

  static PantryExpiryConfidence fromWireValue(Object? value) => switch (value) {
    'LABELLED' => labelled,
    'ESTIMATED' => estimated,
    'UNKNOWN' => unknown,
    _ => throw const ApiResponseFormatException(),
  };
}

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
    final json = _map(value);
    return PantryItem(
      publicId: _requiredString(json['publicId']),
      ingredientPublicId: _requiredString(json['ingredientPublicId']),
      ingredientCode: _requiredString(json['ingredientCode']),
      ingredientName: _requiredString(json['ingredientName']),
      foodPublicId: _optionalString(json['foodPublicId']),
      foodCode: _optionalString(json['foodCode']),
      foodName: _optionalString(json['foodName']),
      quantityInitial: PantryDecimal.fromJson(json['quantityInitial']),
      quantityRemaining: PantryDecimal.fromJson(json['quantityRemaining']),
      unitCode: _requiredString(json['unitCode']),
      unitDisplayName: _requiredString(json['unitDisplayName']),
      storageLocation: PantryStorageLocation.fromWireValue(
        json['storageLocation'],
      ),
      acquiredOn: PantryDate.parseOptional(json['acquiredOn']),
      expiryDate: PantryDate.parseOptional(json['expiryDate']),
      expiryKind: PantryExpiryKind.fromWireValue(json['expiryKind']),
      expiryConfidence: PantryExpiryConfidence.fromWireValue(
        json['expiryConfidence'],
      ),
      status: PantryItemStatus.fromWireValue(json['status']),
      closedAt: _optionalTimestamp(json['closedAt']),
      note: _optionalString(json['note']),
      version: _requiredInt(json['version']),
      createdAt: _requiredTimestamp(json['createdAt']),
      updatedAt: _requiredTimestamp(json['updatedAt']),
    );
  }

  final String publicId;
  final String ingredientPublicId;
  final String ingredientCode;
  final String ingredientName;
  final String? foodPublicId;
  final String? foodCode;
  final String? foodName;
  final PantryDecimal quantityInitial;
  final PantryDecimal quantityRemaining;
  final String unitCode;
  final String unitDisplayName;
  final PantryStorageLocation storageLocation;
  final DateTime? acquiredOn;
  final DateTime? expiryDate;
  final PantryExpiryKind expiryKind;
  final PantryExpiryConfidence expiryConfidence;
  final PantryItemStatus status;
  final DateTime? closedAt;
  final String? note;
  final int version;
  final DateTime createdAt;
  final DateTime updatedAt;
}

final class CreatePantryItemRequest {
  const CreatePantryItemRequest({
    required this.ingredientPublicId,
    this.foodPublicId,
    required this.quantity,
    required this.unitCode,
    required this.storageLocation,
    this.acquiredOn,
    this.expiryDate,
    this.expiryKind = PantryExpiryKind.unknown,
    this.expiryConfidence = PantryExpiryConfidence.unknown,
    this.note,
  });

  final String ingredientPublicId;
  final String? foodPublicId;
  final String quantity;
  final String unitCode;
  final PantryStorageLocation storageLocation;
  final DateTime? acquiredOn;
  final DateTime? expiryDate;
  final PantryExpiryKind expiryKind;
  final PantryExpiryConfidence expiryConfidence;
  final String? note;

  Map<String, Object?> toJson() => {
    'ingredientPublicId': PantryPublicId.normalize(ingredientPublicId),
    'foodPublicId': foodPublicId == null
        ? null
        : PantryPublicId.normalize(foodPublicId!),
    'quantity': PantryDecimal.parse(quantity).toPositiveJsonNumber(),
    'unitCode': PantryUnitCode.normalize(unitCode),
    'storageLocation': storageLocation.wireValue,
    'acquiredOn': acquiredOn == null ? null : PantryDate.format(acquiredOn!),
    'expiryDate': expiryDate == null ? null : PantryDate.format(expiryDate!),
    'expiryKind': expiryKind.wireValue,
    'expiryConfidence': expiryConfidence.wireValue,
    'note': PantryNote.normalize(note),
  };
}

final class UpdatePantryMetadataRequest {
  const UpdatePantryMetadataRequest({
    required this.storageLocation,
    this.acquiredOn,
    this.expiryDate,
    this.expiryKind = PantryExpiryKind.unknown,
    this.expiryConfidence = PantryExpiryConfidence.unknown,
    this.note,
  });

  final PantryStorageLocation storageLocation;
  final DateTime? acquiredOn;
  final DateTime? expiryDate;
  final PantryExpiryKind expiryKind;
  final PantryExpiryConfidence expiryConfidence;
  final String? note;

  Map<String, Object?> toJson() => {
    'storageLocation': storageLocation.wireValue,
    'acquiredOn': acquiredOn == null ? null : PantryDate.format(acquiredOn!),
    'expiryDate': expiryDate == null ? null : PantryDate.format(expiryDate!),
    'expiryKind': expiryKind.wireValue,
    'expiryConfidence': expiryConfidence.wireValue,
    'note': PantryNote.normalize(note),
  };
}

final class AdjustPantryItemRequest {
  const AdjustPantryItemRequest({required this.quantityDelta, this.note});

  final String quantityDelta;
  final String? note;

  Map<String, Object?> toJson() => {
    'quantityDelta': PantryDecimal.parse(quantityDelta).toNonzeroJsonNumber(),
    'note': PantryNote.normalize(note),
  };
}

final class ConsumePantryItemRequest {
  const ConsumePantryItemRequest({required this.quantity, this.note});

  final String quantity;
  final String? note;

  Map<String, Object?> toJson() => {
    'quantity': PantryDecimal.parse(quantity).toPositiveJsonNumber(),
    'note': PantryNote.normalize(note),
  };
}

final class DiscardPantryItemRequest {
  const DiscardPantryItemRequest({this.note});

  final String? note;

  /// A null note omits the optional body; a supplied blank note clears it.
  Map<String, Object?>? toJson() =>
      note == null ? null : {'note': PantryNote.normalize(note)};
}

Map<String, dynamic> _map(Object? value) {
  if (value is Map<String, dynamic>) return value;
  throw const ApiResponseFormatException();
}

String _requiredString(Object? value) {
  if (value is String) return value;
  throw const ApiResponseFormatException();
}

String? _optionalString(Object? value) =>
    value == null ? null : _requiredString(value);

int _requiredInt(Object? value) {
  if (value is int) return value;
  throw const ApiResponseFormatException();
}

DateTime? _optionalTimestamp(Object? value) =>
    value == null ? null : _requiredTimestamp(value);

DateTime _requiredTimestamp(Object? value) {
  if (value is! String) throw const ApiResponseFormatException();
  final timestamp = DateTime.tryParse(value);
  if (timestamp == null) throw const ApiResponseFormatException();
  return timestamp;
}
