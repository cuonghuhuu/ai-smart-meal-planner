import 'package:smart_meal_planner/features/profile/data/profile_models.dart';

final class ProfileValidation {
  const ProfileValidation._();

  static String? validateWeeklyChangeText(String? value) {
    final text = value?.trim() ?? '';
    if (text.isEmpty) {
      return null;
    }
    final number = double.tryParse(text);
    if (number == null || !number.isFinite || number <= -5 || number >= 5) {
      return 'Weekly change must be greater than -5 and less than 5 kg.';
    }
    return null;
  }

  static Map<String, String> validate(ProfileDraft draft, {DateTime? today}) {
    final errors = <String, String>{};
    final birthDate = draft.birthDate;
    if (birthDate != null) {
      final date = _dateOnly(birthDate);
      if (!date.isAfter(DateTime(1900, 1, 1))) {
        errors['birthDate'] = 'Birth date must be after 1900-01-01.';
      } else {
        final currentDate = _dateOnly(today ?? DateTime.now());
        if (date.isAfter(currentDate)) {
          errors['birthDate'] = 'Birth date cannot be in the future.';
        }
      }
    }

    final heightCm = draft.heightCm;
    if (heightCm != null &&
        (!heightCm.isFinite || heightCm <= 30 || heightCm >= 300)) {
      errors['heightCm'] = 'Height must be greater than 30 and less than 300.';
    }

    final targetWeightKg = draft.targetWeightKg;
    if (targetWeightKg != null &&
        (!targetWeightKg.isFinite ||
            targetWeightKg <= 2 ||
            targetWeightKg >= 700)) {
      errors['targetWeightKg'] =
          'Target weight must be greater than 2 and less than 700.';
    }

    final weeklyChangeKg = draft.weeklyChangeKg;
    if (weeklyChangeKg != null &&
        (!weeklyChangeKg.isFinite ||
            weeklyChangeKg <= -5 ||
            weeklyChangeKg >= 5)) {
      errors['weeklyChangeKg'] =
          'Weekly change must be greater than -5 and less than 5.';
    }

    if (draft.householdSize < 1) {
      errors['householdSize'] = 'Household size must be at least 1.';
    }

    final maxCookMinutes = draft.maxCookMinutes;
    if (maxCookMinutes != null &&
        (maxCookMinutes < 1 || maxCookMinutes > 1440)) {
      errors['maxCookMinutes'] =
          'Max cooking time must be between 1 and 1440 minutes.';
    }

    final notes = draft.notes;
    if (notes != null && notes.length > 500) {
      errors['notes'] = 'Notes must be 500 characters or fewer.';
    }

    return errors;
  }

  static DateTime _dateOnly(DateTime value) =>
      DateTime(value.year, value.month, value.day);
}
