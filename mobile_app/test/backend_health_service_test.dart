import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:smart_meal_planner/core/api/api_client.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/features/foundation/data/backend_health_service.dart';

void main() {
  test('maps a successful UP health response', () async {
    final service = _serviceFor((request) async {
      expect(request.method, 'GET');
      expect(request.url.toString(), 'https://backend.test/actuator/health');
      return http.Response('{"status":"UP"}', 200);
    });

    final health = await service.checkHealth();

    expect(health.status, 'UP');
    expect(health.isUp, isTrue);
  });

  test('surfaces non-success HTTP responses', () async {
    final service = _serviceFor((request) async => http.Response('', 503));

    await expectLater(
      service.checkHealth(),
      throwsA(
        isA<ApiHttpException>().having(
          (exception) => exception.statusCode,
          'statusCode',
          503,
        ),
      ),
    );
  });

  test('rejects malformed health JSON', () async {
    final service = _serviceFor((request) async => http.Response('{', 200));

    await expectLater(
      service.checkHealth(),
      throwsA(isA<ApiResponseFormatException>()),
    );
  });

  test('rejects a health response without a string status', () async {
    final service = _serviceFor(
      (request) async => http.Response('{"status":123}', 200),
    );

    await expectLater(
      service.checkHealth(),
      throwsA(isA<ApiResponseFormatException>()),
    );
  });
}

BackendHealthService _serviceFor(
  Future<http.Response> Function(http.Request request) handler,
) {
  return BackendHealthService(
    apiClient: ApiClient(
      baseUrl: 'https://backend.test',
      httpClient: MockClient(handler),
    ),
  );
}
