import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:smart_meal_planner/core/api/api_client.dart';
import 'package:smart_meal_planner/features/auth/application/session_controller.dart';
import 'package:smart_meal_planner/features/auth/data/auth_repository.dart';
import 'package:smart_meal_planner/features/auth/data/refresh_token_store.dart';

void main() {
  const email = 'planner@example.test';
  const password = 'Password with spaces 123!';
  const displayName = 'Meal Planner';
  final token = List<String>.filled(64, 'a').join();

  test(
    'registration sends the exact backend JSON contract unchanged',
    () async {
      final request = await _singleRequest(
        (request) async {
          expect(request.method, 'POST');
          expect(request.url.path, '/api/v1/auth/register');
          expect(jsonDecode(request.body), {
            'email': email,
            'password': password,
            'displayName': displayName,
          });
          return http.Response(
            '{"publicId":"00000000-0000-0000-0000-000000000001",'
            '"accountStatus":"PENDING_VERIFICATION"}',
            201,
          );
        },
        (api) => HttpAuthRepository(
          api,
        ).register(email: email, password: password, displayName: displayName),
      );

      expect(request, isNotNull);
    },
  );

  test('web login uses the web endpoint and preserves credentials', () async {
    await _singleRequest((request) async {
      expect(request.method, 'POST');
      expect(request.url.path, '/api/v1/auth/login/web');
      expect(jsonDecode(request.body), {'email': email, 'password': password});
      return http.Response(_sessionJson, 200);
    }, (api) => HttpAuthRepository(api).loginWeb(email, password));
  });

  test('reset password sends only token and password unchanged', () async {
    await _singleRequest(
      (request) async {
        expect(request.method, 'POST');
        expect(request.url.path, '/api/v1/auth/reset-password');
        expect(jsonDecode(request.body), {
          'token': token,
          'password': password,
        });
        return http.Response('', 204);
      },
      (api) =>
          HttpAuthRepository(api)
              .resetPassword(token: token, password: password),
    );
  });

  test(
    'successful web login loads identity and authenticates the session',
    () async {
      late final SessionController session;
      final api = ApiClient(
        baseUrl: 'https://backend.test',
        httpClient: MockClient((request) async {
          if (request.url.path == '/api/v1/auth/login/web') {
            expect(jsonDecode(request.body), {
              'email': email,
              'password': password,
            });
            return http.Response(_sessionJson, 200);
          }
          if (request.url.path == '/api/v1/auth/me') {
            expect(request.headers['authorization'], 'Bearer access-token');
            return http.Response(
              '{"publicId":"00000000-0000-0000-0000-000000000001",'
              '"email":"$email","roles":["ROLE_USER"]}',
              200,
            );
          }
          throw StateError('Unexpected request: ${request.url}');
        }),
      );
      session = SessionController(
        authRepository: HttpAuthRepository(api),
        refreshTokenStore: NoRefreshTokenStore(),
        isWeb: true,
      );
      api.configureAuthentication(
        accessTokenProvider: () => session.accessToken,
        refreshAccessToken: session.refresh,
      );

      await session.login(email, password);

      expect(session.isAuthenticated, isTrue);
      expect(session.identity?.email, email);
    },
  );
}

const _sessionJson =
    '{"tokenType":"Bearer","accessToken":"access-token",'
    '"accessTokenExpiresAt":"2030-01-01T00:00:00Z",'
    '"refreshTokenExpiresAt":"2030-01-02T00:00:00Z"}';

Future<http.Request?> _singleRequest(
  Future<http.Response> Function(http.Request request) handler,
  Future<void> Function(ApiClient api) action,
) async {
  http.Request? captured;
  final api = ApiClient(
    baseUrl: 'https://backend.test',
    httpClient: MockClient((request) async {
      captured = request;
      return handler(request);
    }),
  );
  await action(api);
  return captured;
}
