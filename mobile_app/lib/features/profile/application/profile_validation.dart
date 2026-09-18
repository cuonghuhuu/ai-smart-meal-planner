import 'package:smart_meal_planner/features/profile/data/profile_models.dart';
import 'package:smart_meal_planner/l10n/app_strings.dart';

final class ProfileValidation {
  const ProfileValidation._();

  static String? validateWeeklyChangeText(String? value) {
    final text = value?.trim() ?? '';
    if (text.isEmpty) {
      return null;
    }
    final number = double.tryParse(text);
    if (number == null || !number.isFinite || number <= -5 || number >= 5) {
      return AppStrings.weeklyChangeInvalidWithUnit;
    }
    return null;
  }

  static Map<String, String> validate(ProfileDraft draft, {DateTime? today}) {
    final errors = <String, String>{};
    final birthDate = draft.birthDate;
    if (birthDate != null) {
      final date = _dateOnly(birthDate);
      if (!date.isAfter(DateTime(1900, 1, 1))) {
        errors['birthDate'] = AppStrings.birthDateTooEarly;
      } else {
        final currentDate = _dateOnly(today ?? DateTime.now());
        if (date.isAfter(currentDate)) {
          errors['birthDate'] = AppStrings.birthDateInFuture;
        }
      }
    }

    final heightCm = draft.heightCm;
    if (heightCm != null &&
        (!heightCm.isFinite || heightCm <= 30 || heightCm >= 300)) {
      errors['heightCm'] = AppStrings.heightInvalid;
    }

    final targetWeightKg = draft.targetWeightKg;
    if (targetWeightKg != null &&
        (!targetWeightKg.isFinite ||
            targetWeightKg <= 2 ||
            targetWeightKg >= 700)) {
      errors['targetWeightKg'] = AppStrings.targetWeightInvalid;
    }

    final weeklyChangeKg = draft.weeklyChangeKg;
    if (weeklyChangeKg != null &&
        (!weeklyChangeKg.isFinite ||
            weeklyChangeKg <= -5 ||
            weeklyChangeKg >= 5)) {
      errors['weeklyChangeKg'] = AppStrings.weeklyChangeInvalid;
    }

    if (draft.householdSize < 1) {
      errors['householdSize'] = AppStrings.householdSizeInvalid;
    }

    final maxCookMinutes = draft.maxCookMinutes;
    if (maxCookMinutes != null &&
        (maxCookMinutes < 1 || maxCookMinutes > 1440)) {
      errors['maxCookMinutes'] = AppStrings.maxCookingTimeInvalid;
    }

    final notes = draft.notes;
    if (notes != null && notes.length > 500) {
      errors['notes'] = AppStrings.notesTooLong;
    }

    return errors;
  }

  static DateTime _dateOnly(DateTime value) =>
      DateTime(value.year, value.month, value.day);
}
