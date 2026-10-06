import 'package:smart_meal_planner/core/api/api_exception.dart';

/// Strict readers for the RecipeResponse wire contract.
final class RecipeJson {
  const RecipeJson._();

  static Map<String, dynamic> map(Object? value) {
    if (value is Map<String, dynamic>) return value;
    throw const ApiResponseFormatException();
  }

  static Object? field(Map<String, dynamic> json, String key) {
    if (!json.containsKey(key)) throw const ApiResponseFormatException();
    return json[key];
  }

  static String string(Map<String, dynamic> json, String key) {
    final value = field(json, key);
    if (value is String && value.isNotEmpty) return value;
    throw const ApiResponseFormatException();
  }

  static String? nullableString(Map<String, dynamic> json, String key) {
    final value = field(json, key);
    if (value == null || value is String) return value as String?;
    throw const ApiResponseFormatException();
  }

  static int integer(Map<String, dynamic> json, String key) {
    final value = field(json, key);
    if (value is int) return value;
    throw const ApiResponseFormatException();
  }

  static int? nullableInteger(Map<String, dynamic> json, String key) {
    final value = field(json, key);
    if (value == null || value is int) return value as int?;
    throw const ApiResponseFormatException();
  }

  static num number(Map<String, dynamic> json, String key) {
    final value = field(json, key);
    if (value is num && value.isFinite) return value;
    throw const ApiResponseFormatException();
  }

  static num? nullableNumber(Map<String, dynamic> json, String key) {
    final value = field(json, key);
    if (value == null) return null;
    if (value is num && value.isFinite) return value;
    throw const ApiResponseFormatException();
  }

  static bool boolean(Map<String, dynamic> json, String key) {
    final value = field(json, key);
    if (value is bool) return value;
    throw const ApiResponseFormatException();
  }

  static List<T> list<T>(Object? value, T Function(Object?) parse) {
    if (value is! List) throw const ApiResponseFormatException();
    return List.unmodifiable(value.map(parse));
  }
}
