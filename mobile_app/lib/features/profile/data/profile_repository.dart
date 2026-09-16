import 'package:smart_meal_planner/core/api/api_client.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/features/profile/data/profile_models.dart';

abstract interface class ProfileRepository {
  Future<Profile> getProfile();

  Future<Profile> updateProfile(ProfileDraft draft);
}

final class ProfileNotFoundException implements Exception {
  const ProfileNotFoundException();
}

final class HttpProfileRepository implements ProfileRepository {
  HttpProfileRepository(this._apiClient);

  final ApiClient _apiClient;

  @override
  Future<Profile> getProfile() async {
    try {
      final response = await _apiClient.requestJson(
        '/api/v1/me/profile',
        authenticated: true,
      );
      return Profile.fromJson(_map(response));
    } on ApiHttpException catch (error) {
      if (error.statusCode == 404) {
        throw const ProfileNotFoundException();
      }
      rethrow;
    }
  }

  @override
  Future<Profile> updateProfile(ProfileDraft draft) async {
    final response = await _apiClient.requestJson(
      '/api/v1/me/profile',
      method: 'PUT',
      body: draft.toJson(),
      authenticated: true,
    );
    return Profile.fromJson(_map(response));
  }
}

Map<String, dynamic> _map(Object? response) {
  if (response is! Map<String, dynamic>) {
    throw const ApiResponseFormatException();
  }
  return response;
}
