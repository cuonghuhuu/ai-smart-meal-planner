import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:smart_meal_planner/core/api/api_client.dart';

void main() {
  test('authenticated browser mutation receives CSRF header', () async {
    var csrfReads = 0;
    final requests = <http.Request>[];
    final api = ApiClient(
      baseUrl: 'https://backend.test',
      httpClient: MockClient((request) async {
        requests.add(request);
        return http.Response('{}', 200);
      }),
    );
    api.configureAuthentication(
      accessTokenProvider: () => 'test-access-token',
      refreshAccessToken: () async => false,
    );
    api.configureCsrfHeaders(() async {
      csrfReads++;
      return {'X-CSRF-TOKEN': 'test-csrf-token'};
    });

    await api.requestJson('/api/v1/me/profile', authenticated: true);
    await api.requestJson(
      '/api/v1/me/profile',
      method: 'PUT',
      authenticated: true,
      body: {'householdSize': 1},
    );
    await api.requestJson(
      '/api/v1/auth/register',
      method: 'POST',
      body: {'email': 'test@example.test'},
    );

    expect(csrfReads, 1);
    expect(requests[0].headers.containsKey('X-CSRF-TOKEN'), isFalse);
    expect(requests[1].headers['X-CSRF-TOKEN'], 'test-csrf-token');
    expect(requests[1].headers['Authorization'], 'Bearer test-access-token');
    expect(requests[2].headers.containsKey('X-CSRF-TOKEN'), isFalse);
  });

  test(
    'explicit CSRF header is preserved without fetching another token',
    () async {
      var csrfReads = 0;
      final api = ApiClient(
        baseUrl: 'https://backend.test',
        httpClient: MockClient((request) async {
          expect(request.headers['X-CSRF-TOKEN'], 'explicit-token');
          return http.Response('{}', 200);
        }),
      );
      api.configureCsrfHeaders(() async {
        csrfReads++;
        return {'X-CSRF-TOKEN': 'fetched-token'};
      });

      await api.requestJson(
        '/api/v1/me/pantry',
        method: 'POST',
        authenticated: true,
        headers: {'X-CSRF-TOKEN': 'explicit-token'},
        body: const {},
      );
      expect(csrfReads, 0);
    },
  );
}
