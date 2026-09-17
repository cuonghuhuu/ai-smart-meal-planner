import 'package:smart_meal_planner/core/api/api_exception.dart';

final class MeasurementSource {
  const MeasurementSource._(this.wireValue, this.displayName);

  static const userEntered = MeasurementSource._(
    'USER_ENTERED',
    'Người dùng nhập',
  );
  static const imported = MeasurementSource._(
    'IMPORTED',
    'Đã nhập từ nguồn khác',
  );
  static const corrected = MeasurementSource._('CORRECTED', 'Đã hiệu chỉnh');

  final String wireValue;
  final String displayName;

  static MeasurementSource fromWireValue(Object? value) {
    if (value is! String || value.isEmpty) {
      throw const ApiResponseFormatException();
    }
    return switch (value) {
      'USER_ENTERED' => userEntered,
      'IMPORTED' => imported,
      'CORRECTED' => corrected,
      _ => MeasurementSource._(value, value),
    };
  }
}

final class BodyMeasurement {
  const BodyMeasurement({
    required this.measuredOn,
    required this.weightKg,
    required this.bodyFatPercent,
    required this.waistCm,
    required this.source,
    required this.note,
    required this.createdAt,
  });

  factory BodyMeasurement.fromJson(Map<String, dynamic> json) =>
      BodyMeasurement(
        measuredOn: parseMeasurementDate(json['measuredOn']),
        weightKg: _requiredDouble(json['weightKg']),
        bodyFatPercent: _optionalDouble(json['bodyFatPercent']),
        waistCm: _optionalDouble(json['waistCm']),
        source: MeasurementSource.fromWireValue(json['source']),
        note: _optionalString(json['note']),
        createdAt: _requiredDateTime(json['createdAt']),
      );

  final DateTime measuredOn;
  final double weightKg;
  final double? bodyFatPercent;
  final double? waistCm;
  final MeasurementSource source;
  final String? note;
  final DateTime createdAt;
}

final class MeasurementDraft {
  const MeasurementDraft({
    required this.measuredOn,
    required this.weightKg,
    required this.bodyFatPercent,
    required this.waistCm,
    required this.note,
  });

  final DateTime measuredOn;
  final double? weightKg;
  final double? bodyFatPercent;
  final double? waistCm;
  final String? note;

  Map<String, Object?> toRecordJson() => {
    'measuredOn': formatMeasurementDate(measuredOn),
    'weightKg': weightKg,
    'bodyFatPercent': bodyFatPercent,
    'waistCm': waistCm,
    'note': _trimmedNote(note),
  };

  Map<String, Object?> toUpdateJson() => {
    'weightKg': weightKg,
    'bodyFatPercent': bodyFatPercent,
    'waistCm': waistCm,
    'note': _trimmedNote(note),
  };
}

String formatMeasurementDate(DateTime value) =>
    '${value.year.toString().padLeft(4, '0')}-'
    '${value.month.toString().padLeft(2, '0')}-'
    '${value.day.toString().padLeft(2, '0')}';

DateTime backendUtcToday({DateTime? now}) {
  final utcNow = (now ?? DateTime.now()).toUtc();
  return DateTime.utc(utcNow.year, utcNow.month, utcNow.day);
}

DateTime parseMeasurementDate(Object? value) {
  if (value is! String) {
    throw const ApiResponseFormatException();
  }
  final match = RegExp(r'^([0-9]{4})-([0-9]{2})-([0-9]{2})$').firstMatch(value);
  if (match == null) {
    throw const ApiResponseFormatException();
  }
  final date = DateTime(
    int.parse(match.group(1)!),
    int.parse(match.group(2)!),
    int.parse(match.group(3)!),
  );
  if (formatMeasurementDate(date) != value) {
    throw const ApiResponseFormatException();
  }
  return date;
}

String _requiredString(Object? value) {
  if (value is String) {
    return value;
  }
  throw const ApiResponseFormatException();
}

String? _optionalString(Object? value) {
  if (value == null) {
    return null;
  }
  return _requiredString(value);
}

double _requiredDouble(Object? value) {
  final result = _optionalDouble(value);
  if (result == null) {
    throw const ApiResponseFormatException();
  }
  return result;
}

double? _optionalDouble(Object? value) {
  if (value == null) {
    return null;
  }
  if (value is! num || !value.isFinite) {
    throw const ApiResponseFormatException();
  }
  return value.toDouble();
}

DateTime _requiredDateTime(Object? value) {
  final text = _requiredString(value);
  final date = DateTime.tryParse(text);
  if (date == null) {
    throw const ApiResponseFormatException();
  }
  return date;
}

String? _trimmedNote(String? value) {
  if (value == null) {
    return null;
  }
  final trimmed = value.trim();
  return trimmed.isEmpty ? null : trimmed;
}
