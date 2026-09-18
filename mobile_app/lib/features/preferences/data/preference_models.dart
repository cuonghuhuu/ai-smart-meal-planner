import 'package:smart_meal_planner/core/api/api_exception.dart';

enum ReactionKind {
  allergy('ALLERGY', 'Dị ứng'),
  intolerance('INTOLERANCE', 'Không dung nạp'),
  unspecified('UNSPECIFIED', 'Chưa xác định');

  const ReactionKind(this.wireValue, this.displayName);

  final String wireValue;
  final String displayName;

  static ReactionKind fromWireValue(Object? value) => switch (value) {
    'ALLERGY' => ReactionKind.allergy,
    'INTOLERANCE' => ReactionKind.intolerance,
    'UNSPECIFIED' => ReactionKind.unspecified,
    _ => throw const ApiResponseFormatException(),
  };
}

final class DietaryPreferenceReference {
  const DietaryPreferenceReference({
    required this.code,
    required this.displayName,
    required this.description,
    required this.isExclusionary,
    required this.displayOrder,
  });

  factory DietaryPreferenceReference.fromJson(Map<String, dynamic> json) =>
      DietaryPreferenceReference(
        code: _requiredString(json['code']),
        displayName: _requiredString(json['displayName']),
        description: _requiredString(json['description']),
        isExclusionary: _requiredBool(json['isExclusionary']),
        displayOrder: _requiredInt(json['displayOrder']),
      );

  final String code;
  final String displayName;
  final String description;
  final bool isExclusionary;
  final int displayOrder;
}

final class SelectedDietaryPreference {
  const SelectedDietaryPreference({
    required this.code,
    required this.displayName,
    required this.description,
    required this.isExclusionary,
  });

  factory SelectedDietaryPreference.fromJson(Map<String, dynamic> json) =>
      SelectedDietaryPreference(
        code: _requiredString(json['code']),
        displayName: _requiredString(json['displayName']),
        description: _requiredString(json['description']),
        isExclusionary: _requiredBool(json['isExclusionary']),
      );

  final String code;
  final String displayName;
  final String description;
  final bool isExclusionary;
}

final class AllergenReference {
  const AllergenReference({
    required this.code,
    required this.displayName,
    required this.description,
    required this.displayOrder,
  });

  factory AllergenReference.fromJson(Map<String, dynamic> json) =>
      AllergenReference(
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

final class SelectedAllergen {
  const SelectedAllergen({
    required this.allergen,
    required this.displayName,
    required this.description,
    required this.reactionKind,
    required this.note,
  });

  factory SelectedAllergen.fromJson(Map<String, dynamic> json) =>
      SelectedAllergen(
        allergen: _requiredString(json['allergen']),
        displayName: _requiredString(json['displayName']),
        description: _requiredString(json['description']),
        reactionKind: ReactionKind.fromWireValue(json['reactionKind']),
        note: _optionalString(json['note']),
      );

  final String allergen;
  final String displayName;
  final String description;
  final ReactionKind reactionKind;
  final String? note;

  SelectedAllergen copyWith({
    ReactionKind? reactionKind,
    Object? note = _unset,
  }) => SelectedAllergen(
    allergen: allergen,
    displayName: displayName,
    description: description,
    reactionKind: reactionKind ?? this.reactionKind,
    note: identical(note, _unset) ? this.note : note as String?,
  );

  AllergenSelection toRequest() => AllergenSelection(
    allergen: allergen,
    reactionKind: reactionKind,
    note: note,
  );
}

final class AllergenSelection {
  const AllergenSelection({
    required this.allergen,
    required this.reactionKind,
    required this.note,
  });

  final String allergen;
  final ReactionKind reactionKind;
  final String? note;

  Map<String, Object?> toJson() => {
    'allergen': allergen,
    'reactionKind': reactionKind.wireValue,
    'note': _trimmedNote(note),
  };
}

const Object _unset = Object();

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
  if (value is String) {
    return value;
  }
  throw const ApiResponseFormatException();
}

bool _requiredBool(Object? value) {
  if (value is bool) {
    return value;
  }
  throw const ApiResponseFormatException();
}

int _requiredInt(Object? value) {
  if (value is int) {
    return value;
  }
  if (value is num && value.isFinite && value == value.roundToDouble()) {
    return value.toInt();
  }
  throw const ApiResponseFormatException();
}

String? _trimmedNote(String? value) {
  if (value == null) {
    return null;
  }
  final trimmed = value.trim();
  return trimmed.isEmpty ? null : trimmed;
}
