import 'package:smart_meal_planner/core/api/api_exception.dart';

final class Profile {
  const Profile({
    required this.birthDate,
    required this.sex,
    required this.heightCm,
    required this.activityLevel,
    required this.nutritionGoal,
    required this.targetWeightKg,
    required this.weeklyChangeKg,
    required this.householdSize,
    required this.maxCookMinutes,
    required this.notes,
    required this.version,
    required this.createdAt,
    required this.updatedAt,
  });

  factory Profile.fromJson(Map<String, dynamic> json) => Profile(
    birthDate: _optionalDate(json['birthDate']),
    sex: _optionalString(json['sex']),
    heightCm: _optionalDouble(json['heightCm']),
    activityLevel: _optionalString(json['activityLevel']),
    nutritionGoal: _optionalString(json['nutritionGoal']),
    targetWeightKg: _optionalDouble(json['targetWeightKg']),
    weeklyChangeKg: _optionalDouble(json['weeklyChangeKg']),
    householdSize: _requiredInt(json['householdSize']),
    maxCookMinutes: _optionalInt(json['maxCookMinutes']),
    notes: _optionalString(json['notes']),
    version: _requiredInt(json['version']),
    createdAt: _requiredDateTime(json['createdAt']),
    updatedAt: _requiredDateTime(json['updatedAt']),
  );

  final DateTime? birthDate;
  final String? sex;
  final double? heightCm;
  final String? activityLevel;
  final String? nutritionGoal;
  final double? targetWeightKg;
  final double? weeklyChangeKg;
  final int householdSize;
  final int? maxCookMinutes;
  final String? notes;
  final int version;
  final DateTime createdAt;
  final DateTime updatedAt;

  ProfileDraft toDraft() => ProfileDraft(
    birthDate: birthDate,
    sex: sex,
    heightCm: heightCm,
    activityLevel: activityLevel,
    nutritionGoal: nutritionGoal,
    targetWeightKg: targetWeightKg,
    weeklyChangeKg: weeklyChangeKg,
    householdSize: householdSize,
    maxCookMinutes: maxCookMinutes,
    notes: notes,
    version: version,
  );
}

const Object _unset = Object();

final class ProfileDraft {
  const ProfileDraft({
    required this.birthDate,
    required this.sex,
    required this.heightCm,
    required this.activityLevel,
    required this.nutritionGoal,
    required this.targetWeightKg,
    required this.weeklyChangeKg,
    required this.householdSize,
    required this.maxCookMinutes,
    required this.notes,
    required this.version,
  });

  const ProfileDraft.empty()
    : birthDate = null,
      sex = null,
      heightCm = null,
      activityLevel = null,
      nutritionGoal = null,
      targetWeightKg = null,
      weeklyChangeKg = null,
      householdSize = 1,
      maxCookMinutes = null,
      notes = null,
      version = 0;

  final DateTime? birthDate;
  final String? sex;
  final double? heightCm;
  final String? activityLevel;
  final String? nutritionGoal;
  final double? targetWeightKg;
  final double? weeklyChangeKg;
  final int householdSize;
  final int? maxCookMinutes;
  final String? notes;
  final int version;

  Map<String, Object?> toJson() => {
    'birthDate': _dateOnly(birthDate),
    'sex': sex,
    'heightCm': heightCm,
    'activityLevel': activityLevel,
    'nutritionGoal': nutritionGoal,
    'targetWeightKg': targetWeightKg,
    'weeklyChangeKg': weeklyChangeKg,
    'householdSize': householdSize,
    'maxCookMinutes': maxCookMinutes,
    'notes': notes,
    'version': version,
  };

  ProfileDraft copyWith({
    Object? birthDate = _unset,
    Object? sex = _unset,
    Object? heightCm = _unset,
    Object? activityLevel = _unset,
    Object? nutritionGoal = _unset,
    Object? targetWeightKg = _unset,
    Object? weeklyChangeKg = _unset,
    Object? householdSize = _unset,
    Object? maxCookMinutes = _unset,
    Object? notes = _unset,
    Object? version = _unset,
  }) => ProfileDraft(
    birthDate: identical(birthDate, _unset)
        ? this.birthDate
        : birthDate as DateTime?,
    sex: identical(sex, _unset) ? this.sex : sex as String?,
    heightCm: identical(heightCm, _unset) ? this.heightCm : heightCm as double?,
    activityLevel: identical(activityLevel, _unset)
        ? this.activityLevel
        : activityLevel as String?,
    nutritionGoal: identical(nutritionGoal, _unset)
        ? this.nutritionGoal
        : nutritionGoal as String?,
    targetWeightKg: identical(targetWeightKg, _unset)
        ? this.targetWeightKg
        : targetWeightKg as double?,
    weeklyChangeKg: identical(weeklyChangeKg, _unset)
        ? this.weeklyChangeKg
        : weeklyChangeKg as double?,
    householdSize: identical(householdSize, _unset)
        ? this.householdSize
        : householdSize as int,
    maxCookMinutes: identical(maxCookMinutes, _unset)
        ? this.maxCookMinutes
        : maxCookMinutes as int?,
    notes: identical(notes, _unset) ? this.notes : notes as String?,
    version: identical(version, _unset) ? this.version : version as int,
  );
}

final class ActivityLevelReference {
  const ActivityLevelReference({
    required this.code,
    required this.displayName,
    required this.description,
    required this.energyFactor,
    required this.displayOrder,
  });

  factory ActivityLevelReference.fromJson(Map<String, dynamic> json) =>
      ActivityLevelReference(
        code: _requiredString(json['code']),
        displayName: _requiredString(json['displayName']),
        description: _requiredString(json['description']),
        energyFactor: _requiredDouble(json['energyFactor']),
        displayOrder: _requiredInt(json['displayOrder']),
      );

  final String code;
  final String displayName;
  final String description;
  final double energyFactor;
  final int displayOrder;
}

final class NutritionGoalReference {
  const NutritionGoalReference({
    required this.code,
    required this.displayName,
    required this.description,
    required this.displayOrder,
  });

  factory NutritionGoalReference.fromJson(Map<String, dynamic> json) =>
      NutritionGoalReference(
        code: _requiredString(json['code']),
        displayName: _requiredString(json['displayName']),
        description: _requiredString(json['description']),
        displayOrder: _requiredInt(json['displayOrder']),
      );

  final String code;
  final String displayName;
  final String description;
  final int displayOrder;
}

String? _optionalString(Object? value) {
  if (value == null) {
    return null;
  }
  if (value is! String) {
    throw const ApiResponseFormatException();
  }
  return value;
}

String _requiredString(Object? value) {
  final result = _optionalString(value);
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

double _requiredDouble(Object? value) {
  final result = _optionalDouble(value);
  if (result == null) {
    throw const ApiResponseFormatException();
  }
  return result;
}

int? _optionalInt(Object? value) {
  if (value == null) {
    return null;
  }
  if (value is int) {
    return value;
  }
  if (value is num && value.isFinite && value == value.roundToDouble()) {
    return value.toInt();
  }
  throw const ApiResponseFormatException();
}

int _requiredInt(Object? value) {
  final result = _optionalInt(value);
  if (result == null) {
    throw const ApiResponseFormatException();
  }
  return result;
}

DateTime? _optionalDate(Object? value) {
  final text = _optionalString(value);
  if (text == null) {
    return null;
  }
  final date = DateTime.tryParse(text);
  if (date == null) {
    throw const ApiResponseFormatException();
  }
  return DateTime(date.year, date.month, date.day);
}

DateTime _requiredDateTime(Object? value) {
  final text = _requiredString(value);
  final date = DateTime.tryParse(text);
  if (date == null) {
    throw const ApiResponseFormatException();
  }
  return date;
}

String? _dateOnly(DateTime? value) => value == null
    ? null
    : '${value.year.toString().padLeft(4, '0')}-'
          '${value.month.toString().padLeft(2, '0')}-'
          '${value.day.toString().padLeft(2, '0')}';
