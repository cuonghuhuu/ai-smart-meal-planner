import 'package:smart_meal_planner/features/measurements/data/measurement_models.dart';
import 'package:smart_meal_planner/l10n/app_strings.dart';

final class MeasurementsValidation {
  const MeasurementsValidation._();

  static String? validateMeasuredOn(DateTime? value, {DateTime? today}) {
    if (value == null) {
      return AppStrings.measuredDateRequired;
    }
    final currentDay = today ?? backendUtcToday();
    if (_isAfterCalendarDate(value, currentDay)) {
      return AppStrings.measuredDateInFuture;
    }
    return null;
  }

  static String? validateWeight(double? value) {
    if (value == null) {
      return AppStrings.weightRequired;
    }
    if (!value.isFinite || value <= 2 || value >= 700) {
      return AppStrings.weightInvalid;
    }
    return null;
  }

  static String? validateBodyFat(double? value) {
    if (value == null) {
      return null;
    }
    if (!value.isFinite || value < 0 || value > 100) {
      return AppStrings.bodyFatInvalid;
    }
    return null;
  }

  static String? validateWaist(double? value) {
    if (value == null) {
      return null;
    }
    if (!value.isFinite || value <= 10 || value >= 400) {
      return AppStrings.waistInvalid;
    }
    return null;
  }

  static String? validateNote(String? value) {
    if (value == null || value.length <= 255) {
      return null;
    }
    return AppStrings.measurementNoteTooLong;
  }

  static String? validateWeightText(String? value) {
    if (value == null || value.trim().isEmpty) {
      return AppStrings.weightRequired;
    }
    final parsed = parseLocalizedMeasurementNumber(value);
    if (parsed == null) {
      return AppStrings.measurementInvalidNumber;
    }
    if (!_hasAtMostDecimalPlaces(value, 2)) {
      return AppStrings.weightPrecision;
    }
    return validateWeight(parsed);
  }

  static String? validateOptionalBodyFatText(String? value) {
    if (value == null || value.trim().isEmpty) {
      return null;
    }
    final parsed = parseLocalizedMeasurementNumber(value);
    if (parsed == null) {
      return AppStrings.measurementInvalidNumber;
    }
    if (!_hasAtMostDecimalPlaces(value, 1)) {
      return AppStrings.bodyFatPrecision;
    }
    return validateBodyFat(parsed);
  }

  static String? validateOptionalWaistText(String? value) {
    if (value == null || value.trim().isEmpty) {
      return null;
    }
    final parsed = parseLocalizedMeasurementNumber(value);
    if (parsed == null) {
      return AppStrings.measurementInvalidNumber;
    }
    if (!_hasAtMostDecimalPlaces(value, 1)) {
      return AppStrings.waistPrecision;
    }
    return validateWaist(parsed);
  }

  static Map<String, String> validateDraft(
    MeasurementDraft draft, {
    DateTime? today,
  }) {
    final errors = <String, String>{};
    final measuredOnError = validateMeasuredOn(draft.measuredOn, today: today);
    if (measuredOnError != null) {
      errors['measuredOn'] = measuredOnError;
    }
    final weightError = validateWeight(draft.weightKg);
    if (weightError != null) {
      errors['weightKg'] = weightError;
    }
    final bodyFatError = validateBodyFat(draft.bodyFatPercent);
    if (bodyFatError != null) {
      errors['bodyFatPercent'] = bodyFatError;
    }
    final waistError = validateWaist(draft.waistCm);
    if (waistError != null) {
      errors['waistCm'] = waistError;
    }
    final noteError = validateNote(draft.note);
    if (noteError != null) {
      errors['note'] = noteError;
    }
    return errors;
  }

  static double? parseLocalizedMeasurementNumber(String? value) {
    final trimmed = value?.trim();
    if (trimmed == null || trimmed.isEmpty) {
      return null;
    }
    if (trimmed.contains(',') && trimmed.contains('.')) {
      return null;
    }
    if (','.allMatches(trimmed).length > 1 ||
        '.'.allMatches(trimmed).length > 1) {
      return null;
    }
    if (!RegExp(r'^[+-]?(?:\d+(?:[.,]\d+)?|[.,]\d+)$').hasMatch(trimmed)) {
      return null;
    }
    final parsed = double.tryParse(trimmed.replaceFirst(',', '.'));
    return parsed != null && parsed.isFinite ? parsed : null;
  }

  static String? normalizeLocalizedMeasurementNumber(String? value) {
    final parsed = parseLocalizedMeasurementNumber(value);
    return parsed?.toString();
  }

  static bool _hasAtMostDecimalPlaces(String value, int maximum) {
    final trimmed = value.trim();
    final separator = trimmed.contains(',') ? ',' : '.';
    final separatorIndex = trimmed.indexOf(separator);
    if (separatorIndex < 0) {
      return true;
    }
    return trimmed.length - separatorIndex - 1 <= maximum;
  }

  static bool _isAfterCalendarDate(DateTime value, DateTime other) {
    if (value.year != other.year) {
      return value.year > other.year;
    }
    if (value.month != other.month) {
      return value.month > other.month;
    }
    return value.day > other.day;
  }
}
