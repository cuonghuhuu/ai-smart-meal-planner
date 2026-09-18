import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:smart_meal_planner/core/api/api_client.dart';
import 'package:smart_meal_planner/features/measurements/data/measurement_models.dart';
import 'package:smart_meal_planner/features/measurements/data/measurements_repository.dart';

void main() {
  test(
    'GET latest uses exact authenticated endpoint and parses response',
    () async {
      late http.Request request;
      final repository = HttpMeasurementsRepository(
        _api((incoming) async {
          request = incoming;
          return http.Response(_measurementJson, 200);
        }),
      );

      final result = await repository.getLatestMeasurement();

      expect(request.method, 'GET');
      expect(request.url.path, '/api/v1/me/measurements/latest');
      expect(request.headers['authorization'], 'Bearer access');
      expect(result?.measuredOn, DateTime(2026, 9, 17));
      expect(result?.source.wireValue, 'USER_ENTERED');
    },
  );

  test('latest 404 is an empty state', () async {
    final repository = HttpMeasurementsRepository(
      _api((_) async => http.Response('', 404)),
    );

    expect(await repository.getLatestMeasurement(), isNull);
  });

  test(
    'history uses a plain list and emits both date bounds together',
    () async {
      late http.Request request;
      final repository = HttpMeasurementsRepository(
        _api((incoming) async {
          request = incoming;
          return http.Response('[$_measurementJson]', 200);
        }),
      );

      final result = await repository.getMeasurementHistory(
        page: 1,
        size: 20,
        from: DateTime(2026, 9, 1),
        to: DateTime(2026, 9, 17),
      );

      expect(request.method, 'GET');
      expect(request.url.path, '/api/v1/me/measurements');
      expect(request.url.queryParameters, {
        'page': '1',
        'size': '20',
        'from': '2026-09-01',
        'to': '2026-09-17',
      });
      expect(result, hasLength(1));
    },
  );

  test(
    'history rejects a one-sided date filter before making a request',
    () async {
      var calls = 0;
      final repository = HttpMeasurementsRepository(
        _api((_) async {
          calls++;
          return http.Response('[]', 200);
        }),
      );

      expect(
        repository.getMeasurementHistory(from: DateTime(2026, 9, 1)),
        throwsArgumentError,
      );
      await Future<void>.delayed(Duration.zero);
      expect(calls, 0);
    },
  );

  test(
    'POST sends numeric JSON, optional values, and no source field',
    () async {
      late http.Request request;
      final repository = HttpMeasurementsRepository(
        _api((incoming) async {
          request = incoming;
          return http.Response(_measurementJson, 201);
        }),
      );

      await repository.recordMeasurement(
        MeasurementDraft(
          measuredOn: DateTime(2026, 9, 17),
          weightKg: 70.5,
          bodyFatPercent: null,
          waistCm: 82.5,
          note: null,
        ),
      );
      final body = jsonDecode(request.body) as Map<String, dynamic>;

      expect(request.method, 'POST');
      expect(request.url.path, '/api/v1/me/measurements');
      expect(request.headers['authorization'], 'Bearer access');
      expect(body['measuredOn'], '2026-09-17');
      expect(body['weightKg'], 70.5);
      expect(body['bodyFatPercent'], isNull);
      expect(body['waistCm'], 82.5);
      expect(body['note'], isNull);
      expect(body.containsKey('source'), isFalse);
    },
  );

  test('PUT uses the date in the path and excludes it from the body', () async {
    late http.Request request;
    final repository = HttpMeasurementsRepository(
      _api((incoming) async {
        request = incoming;
        return http.Response(_measurementJson, 200);
      }),
    );

    await repository.updateMeasurement(
      MeasurementDraft(
        measuredOn: DateTime(2026, 9, 17),
        weightKg: 70.2,
        bodyFatPercent: 18,
        waistCm: 81.5,
        note: 'Corrected',
      ),
    );
    final body = jsonDecode(request.body) as Map<String, dynamic>;

    expect(request.method, 'PUT');
    expect(request.url.path, '/api/v1/me/measurements/2026-09-17');
    expect(body, {
      'weightKg': 70.2,
      'bodyFatPercent': 18,
      'waistCm': 81.5,
      'note': 'Corrected',
    });
    expect(body.containsKey('measuredOn'), isFalse);
    expect(body.containsKey('source'), isFalse);
  });
}

ApiClient _api(Future<http.Response> Function(http.Request) handler) =>
    ApiClient(baseUrl: 'https://backend.test', httpClient: MockClient(handler))
      ..configureAuthentication(
        accessTokenProvider: () => 'access',
        refreshAccessToken: () async => false,
      );

const _measurementJson =
    '{"measuredOn":"2026-09-17","weightKg":70.5,'
    '"bodyFatPercent":18.5,"waistCm":82.0,"source":"USER_ENTERED",'
    '"note":"Morning measurement","createdAt":"2026-09-17T06:30:00Z"}';
