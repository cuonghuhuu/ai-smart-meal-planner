import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:smart_meal_planner/core/api/api_client.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/features/profile/data/profile_models.dart';
import 'package:smart_meal_planner/features/profile/data/profile_repository.dart';
import 'package:smart_meal_planner/features/profile/data/reference_data_repository.dart';

void main() {
  test(
    'GET profile uses the exact authenticated endpoint and parses all fields',
    () async {
      late http.Request request;
      final repository = HttpProfileRepository(
        _api((incoming) async {
          request = incoming;
          return http.Response(_profileJson, 200);
        }),
      );

      final profile = await repository.getProfile();

      expect(request.method, 'GET');
      expect(request.url.path, '/api/v1/me/profile');
      expect(request.headers['authorization'], 'Bearer access');
      expect(profile.birthDate, DateTime(1995, 2, 3));
      expect(profile.sex, 'FEMALE');
      expect(profile.heightCm, 172.5);
      expect(profile.activityLevel, 'MODERATE');
      expect(profile.nutritionGoal, 'MAINTAIN');
      expect(profile.targetWeightKg, 65.5);
      expect(profile.weeklyChangeKg, -0.25);
      expect(profile.householdSize, 3);
      expect(profile.maxCookMinutes, 45);
      expect(profile.notes, 'Prefer quick meals.');
      expect(profile.version, 7);
      expect(profile.createdAt, DateTime.parse('2026-01-01T10:00:00Z'));
      expect(profile.updatedAt, DateTime.parse('2026-01-02T10:00:00Z'));
    },
  );

  test('GET profile 404 is distinguishable as no profile', () async {
    final repository = HttpProfileRepository(
      _api((_) async => http.Response('', 404)),
    );

    expect(repository.getProfile(), throwsA(isA<ProfileNotFoundException>()));
  });

  test('PUT profile sends the exact request keys and loaded version', () async {
    late http.Request request;
    final repository = HttpProfileRepository(
      _api((incoming) async {
        request = incoming;
        return http.Response(
          _profileJson.replaceFirst('"version":7', '"version":8'),
          200,
        );
      }),
    );
    const draft = ProfileDraft(
      birthDate: null,
      sex: 'PREFER_NOT_TO_SAY',
      heightCm: 180,
      activityLevel: 'LIGHT',
      nutritionGoal: 'LOSE',
      targetWeightKg: 80,
      weeklyChangeKg: 0.5,
      householdSize: 2,
      maxCookMinutes: 30,
      notes: 'No rush.',
      version: 7,
    );

    final updated = await repository.updateProfile(draft);
    final body = jsonDecode(request.body) as Map<String, dynamic>;

    expect(request.method, 'PUT');
    expect(request.url.path, '/api/v1/me/profile');
    expect(
      body.keys,
      containsAll([
        'birthDate',
        'sex',
        'heightCm',
        'activityLevel',
        'nutritionGoal',
        'targetWeightKg',
        'weeklyChangeKg',
        'householdSize',
        'maxCookMinutes',
        'notes',
        'version',
      ]),
    );
    expect(body.keys.length, 11);
    expect(body['birthDate'], isNull);
    expect(body['sex'], 'PREFER_NOT_TO_SAY');
    expect(body['heightCm'], 180);
    expect(body['activityLevel'], 'LIGHT');
    expect(body['nutritionGoal'], 'LOSE');
    expect(body['targetWeightKg'], 80);
    expect(body['weeklyChangeKg'], 0.5);
    expect(body['householdSize'], 2);
    expect(body['maxCookMinutes'], 30);
    expect(body['notes'], 'No rush.');
    expect(body['version'], 7);
    expect(updated.version, 8);
  });

  test('PUT profile serializes blank weekly change as JSON null', () async {
    late http.Request request;
    final repository = HttpProfileRepository(
      _api((incoming) async {
        request = incoming;
        return http.Response(_profileJson, 200);
      }),
    );

    await repository.updateProfile(const ProfileDraft.empty());

    final body = jsonDecode(request.body) as Map<String, dynamic>;
    expect(body.containsKey('weeklyChangeKg'), isTrue);
    expect(body['weeklyChangeKg'], isNull);
  });

  test('PUT 409 is returned without an automatic retry', () async {
    var calls = 0;
    var refreshCalls = 0;
    final api = ApiClient(
      baseUrl: 'https://backend.test',
      httpClient: MockClient((request) async {
        calls++;
        return http.Response('', 409);
      }),
    );
    api.configureAuthentication(
      accessTokenProvider: () => 'access',
      refreshAccessToken: () async {
        refreshCalls++;
        return true;
      },
    );
    final repository = HttpProfileRepository(api);

    expect(
      repository.updateProfile(const ProfileDraft.empty()),
      throwsA(
        isA<ApiHttpException>().having((e) => e.statusCode, 'status', 409),
      ),
    );
    await Future<void>.delayed(Duration.zero);
    expect(calls, 1);
    expect(refreshCalls, 0);
  });

  test('reference repositories use exact endpoints and parse items', () async {
    final paths = <String>[];
    final repository = HttpReferenceDataRepository(
      _api((request) async {
        paths.add(request.url.path);
        if (request.url.path.endsWith('activity-levels')) {
          return http.Response(
            '[{"code":"MODERATE","displayName":"Moderately Active",'
            '"description":"Some movement","energyFactor":1.55,"displayOrder":2}]',
            200,
          );
        }
        return http.Response(
          '[{"code":"MAINTAIN","displayName":"Maintain Weight",'
          '"description":"Keep weight stable","displayOrder":1}]',
          200,
        );
      }),
    );

    final activity = await repository.getActivityLevels();
    final goals = await repository.getNutritionGoals();

    expect(paths, [
      '/api/v1/reference/activity-levels',
      '/api/v1/reference/nutrition-goals',
    ]);
    expect(activity.single.code, 'MODERATE');
    expect(activity.single.displayName, 'Moderately Active');
    expect(activity.single.description, 'Some movement');
    expect(activity.single.energyFactor, 1.55);
    expect(activity.single.displayOrder, 2);
    expect(goals.single.code, 'MAINTAIN');
    expect(goals.single.displayName, 'Maintain Weight');
    expect(goals.single.description, 'Keep weight stable');
    expect(goals.single.displayOrder, 1);
  });
}

ApiClient _api(Future<http.Response> Function(http.Request) handler) =>
    ApiClient(baseUrl: 'https://backend.test', httpClient: MockClient(handler))
      ..configureAuthentication(
        accessTokenProvider: () => 'access',
        refreshAccessToken: () async => false,
      );

const _profileJson =
    '{"birthDate":"1995-02-03","sex":"FEMALE","heightCm":172.5,'
    '"activityLevel":"MODERATE","nutritionGoal":"MAINTAIN",'
    '"targetWeightKg":65.5,"weeklyChangeKg":-0.25,"householdSize":3,'
    '"maxCookMinutes":45,"notes":"Prefer quick meals.","version":7,'
    '"createdAt":"2026-01-01T10:00:00Z","updatedAt":"2026-01-02T10:00:00Z"}';
