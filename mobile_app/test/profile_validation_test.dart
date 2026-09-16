import 'package:flutter_test/flutter_test.dart';
import 'package:smart_meal_planner/features/profile/application/profile_validation.dart';
import 'package:smart_meal_planner/features/profile/data/profile_models.dart';

Map<String, String> _validationErrors({
  DateTime? birthDate,
  double? heightCm,
  double? targetWeightKg,
  double? weeklyChangeKg,
  int householdSize = 1,
  int? maxCookMinutes,
  String? notes,
}) => ProfileValidation.validate(
  ProfileDraft(
    birthDate: birthDate,
    sex: null,
    heightCm: heightCm,
    activityLevel: null,
    nutritionGoal: null,
    targetWeightKg: targetWeightKg,
    weeklyChangeKg: weeklyChangeKg,
    householdSize: householdSize,
    maxCookMinutes: maxCookMinutes,
    notes: notes,
    version: 0,
  ),
  today: DateTime(2026, 9, 16),
);

void main() {
  final today = DateTime(2026, 9, 16);

  test('birth date uses strict lower bound and rejects future dates', () {
    expect(
      _validationErrors(birthDate: DateTime(1900, 1, 1)),
      contains('birthDate'),
    );
    expect(_validationErrors(birthDate: DateTime(1900, 1, 2)), isEmpty);
    expect(
      _validationErrors(birthDate: today.add(const Duration(days: 1))),
      contains('birthDate'),
    );
  });

  test('height uses exclusive bounds', () {
    expect(_validationErrors(heightCm: 30), contains('heightCm'));
    expect(_validationErrors(heightCm: 30.1), isEmpty);
    expect(_validationErrors(heightCm: 300), contains('heightCm'));
  });

  test('target weight uses exclusive bounds', () {
    expect(_validationErrors(targetWeightKg: 2), contains('targetWeightKg'));
    expect(_validationErrors(targetWeightKg: 2.1), isEmpty);
    expect(_validationErrors(targetWeightKg: 700), contains('targetWeightKg'));
  });

  test('weekly change uses exclusive bounds', () {
    expect(_validationErrors(), isEmpty);
    expect(ProfileValidation.validateWeeklyChangeText(''), isNull);
    expect(ProfileValidation.validateWeeklyChangeText('   '), isNull);
    expect(ProfileValidation.validateWeeklyChangeText('0'), isNull);
    expect(ProfileValidation.validateWeeklyChangeText('-0.5'), isNull);
    expect(ProfileValidation.validateWeeklyChangeText('0.5'), isNull);
    expect(ProfileValidation.validateWeeklyChangeText('-4.9'), isNull);
    expect(ProfileValidation.validateWeeklyChangeText('4.9'), isNull);
    expect(ProfileValidation.validateWeeklyChangeText('-5'), isNotNull);
    expect(ProfileValidation.validateWeeklyChangeText('5'), isNotNull);
    expect(ProfileValidation.validateWeeklyChangeText('abc'), isNotNull);
    expect(_validationErrors(weeklyChangeKg: -5), contains('weeklyChangeKg'));
    expect(_validationErrors(weeklyChangeKg: -4.99), isEmpty);
    expect(_validationErrors(weeklyChangeKg: 4.99), isEmpty);
    expect(_validationErrors(weeklyChangeKg: 5), contains('weeklyChangeKg'));
  });

  test('household size requires at least one person', () {
    expect(_validationErrors(householdSize: 0), contains('householdSize'));
    expect(_validationErrors(householdSize: 1), isEmpty);
  });

  test('max cooking time has inclusive valid bounds', () {
    expect(_validationErrors(maxCookMinutes: 0), contains('maxCookMinutes'));
    expect(_validationErrors(maxCookMinutes: 1), isEmpty);
    expect(_validationErrors(maxCookMinutes: 1440), isEmpty);
    expect(_validationErrors(maxCookMinutes: 1441), contains('maxCookMinutes'));
  });

  test('notes allow 500 characters but reject 501', () {
    expect(_validationErrors(notes: 'a' * 500), isEmpty);
    expect(_validationErrors(notes: 'a' * 501), contains('notes'));
  });
}
