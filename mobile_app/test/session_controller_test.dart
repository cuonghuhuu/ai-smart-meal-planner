import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:smart_meal_planner/core/api/api_client.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/features/auth/application/session_controller.dart';
import 'package:smart_meal_planner/features/auth/data/auth_repository.dart';
import 'package:smart_meal_planner/features/auth/data/refresh_token_store.dart';
import 'package:smart_meal_planner/features/auth/domain/auth_models.dart';

void main() {
  test(
    'concurrent protected 401 responses share one refresh and retry once',
    () async {
      final repository = _FakeAuthRepository();
      final refreshCompleter = Completer<AccessSession>();
      repository.refreshResult = refreshCompleter.future;
      var protectedRequests = 0;
      final apiClient = ApiClient(
        baseUrl: 'https://backend.test',
        httpClient: MockClient((request) async {
          if (request.url.path == '/api/v1/auth/csrf') {
            return http.Response(
              '{"headerName":"X-CSRF-TOKEN","token":"csrf"}',
              200,
            );
          }
          if (request.url.path == '/protected') {
            protectedRequests++;
            if (request.headers['authorization'] == 'Bearer old-access') {
              return http.Response('{"status":401,"code":"UNAUTHORIZED"}', 401);
            }
            return http.Response('{"ok":true}', 200);
          }
          throw StateError('Unexpected request: ${request.url}');
        }),
      );
      final session = SessionController(
        authRepository: repository,
        refreshTokenStore: NoRefreshTokenStore(),
        isWeb: true,
      );
      apiClient.configureAuthentication(
        accessTokenProvider: () => session.accessToken,
        refreshAccessToken: session.refresh,
      );

      await session.login('person@example.test', 'not-logged');
      final requests = List<Future<Object?>>.generate(
        3,
        (_) => apiClient.requestJson('/protected', authenticated: true),
      );

      await Future<void>.delayed(Duration.zero);
      expect(repository.refreshCalls, 1);

      refreshCompleter.complete(_session('new-access'));
      final results = await Future.wait(requests);

      expect(results, everyElement({'ok': true}));
      expect(repository.refreshCalls, 1);
      expect(protectedRequests, 6);
    },
  );

  test(
    'a protected request is retried no more than once after refresh',
    () async {
      final repository = _FakeAuthRepository();
      var protectedRequests = 0;
      final apiClient = ApiClient(
        baseUrl: 'https://backend.test',
        httpClient: MockClient((request) async {
          if (request.url.path == '/protected') {
            protectedRequests++;
            return http.Response('{"status":401,"code":"UNAUTHORIZED"}', 401);
          }
          throw StateError('Unexpected request: ${request.url}');
        }),
      );
      final session = SessionController(
        authRepository: repository,
        refreshTokenStore: NoRefreshTokenStore(),
        isWeb: true,
      );
      apiClient.configureAuthentication(
        accessTokenProvider: () => session.accessToken,
        refreshAccessToken: session.refresh,
      );
      await session.login('person@example.test', 'not-logged');

      await expectLater(
        apiClient.requestJson('/protected', authenticated: true),
        throwsA(
          isA<ApiHttpException>().having(
            (exception) => exception.statusCode,
            'statusCode',
            401,
          ),
        ),
      );

      expect(repository.refreshCalls, 1);
      expect(protectedRequests, 2);
    },
  );

  test('a failed restore becomes anonymous', () async {
    final repository = _FakeAuthRepository()..refreshError = true;
    final session = SessionController(
      authRepository: repository,
      refreshTokenStore: NoRefreshTokenStore(),
      isWeb: true,
    );

    await session.bootstrap();

    expect(session.status, SessionStatus.anonymous);
    expect(session.accessToken, isNull);
  });

  test(
    'web restore fetches CSRF then establishes the authenticated identity',
    () async {
      final repository = _FakeAuthRepository();
      final session = SessionController(
        authRepository: repository,
        refreshTokenStore: NoRefreshTokenStore(),
        isWeb: true,
      );

      await session.bootstrap();

      expect(session.status, SessionStatus.authenticated);
      expect(session.identity?.email, 'person@example.test');
      expect(repository.csrfCalls, 1);
      expect(repository.refreshCalls, 1);
    },
  );

  test('Android login stores only its returned refresh token', () async {
    final repository = _FakeAuthRepository()
      ..androidLoginResult = _session('android-access', refreshToken: 'first');
    final store = _MemoryRefreshTokenStore();
    final session = SessionController(
      authRepository: repository,
      refreshTokenStore: store,
      isWeb: false,
    );

    await session.login('person@example.test', 'not-logged');

    expect(store.value, 'first');
    expect(store.writes, ['first']);
    expect(session.accessToken, 'android-access');
  });

  test('Android refresh replaces the rotated refresh token', () async {
    final repository = _FakeAuthRepository()
      ..androidRefreshResult = _session('new-access', refreshToken: 'second');
    final store = _MemoryRefreshTokenStore(value: 'first');
    final session = SessionController(
      authRepository: repository,
      refreshTokenStore: store,
      isWeb: false,
    );

    expect(await session.refresh(), isTrue);

    expect(repository.androidRefreshTokens, ['first']);
    expect(store.value, 'second');
    expect(store.writes, ['second']);
  });

  test(
    'Android refresh failure clears the secure refresh credential',
    () async {
      final repository = _FakeAuthRepository()..refreshError = true;
      final store = _MemoryRefreshTokenStore(value: 'first');
      final session = SessionController(
        authRepository: repository,
        refreshTokenStore: store,
        isWeb: false,
      );

      expect(await session.refresh(), isFalse);

      expect(store.value, isNull);
      expect(store.clearCalls, 1);
      expect(session.status, SessionStatus.anonymous);
    },
  );

  test('Web login never persists a token in the refresh-token store', () async {
    final store = _MemoryRefreshTokenStore();
    final session = SessionController(
      authRepository: _FakeAuthRepository(),
      refreshTokenStore: store,
      isWeb: true,
    );

    await session.login('person@example.test', 'not-logged');

    expect(store.writes, isEmpty);
    expect(store.readCalls, 0);
  });

  test('logout and logout-all clear the in-memory session', () async {
    final repository = _FakeAuthRepository();
    final session = SessionController(
      authRepository: repository,
      refreshTokenStore: NoRefreshTokenStore(),
      isWeb: true,
    );
    await session.login('person@example.test', 'not-logged');

    await session.logout();
    expect(session.status, SessionStatus.anonymous);
    expect(session.accessToken, isNull);
    expect(repository.webLogoutCalls, 1);

    await session.login('person@example.test', 'not-logged');
    await session.logoutAll();
    expect(session.status, SessionStatus.anonymous);
    expect(session.accessToken, isNull);
    expect(repository.logoutAllCalls, 1);
  });

  test('Android logout clears the persisted refresh token', () async {
    final store = _MemoryRefreshTokenStore(value: 'refresh');
    final session = SessionController(
      authRepository: _FakeAuthRepository(),
      refreshTokenStore: store,
      isWeb: false,
    );

    await session.logout();

    expect(store.value, isNull);
    expect(store.clearCalls, 1);
  });
}

AccessSession _session(String accessToken, {String? refreshToken}) =>
    AccessSession(
      accessToken: accessToken,
      accessTokenExpiresAt: DateTime.utc(2030),
      refreshTokenExpiresAt: DateTime.utc(2030, 1, 2),
      refreshToken: refreshToken,
    );

class _FakeAuthRepository implements AuthRepository {
  var csrfCalls = 0;
  var refreshCalls = 0;
  var refreshError = false;
  Future<AccessSession>? refreshResult;
  AccessSession? androidLoginResult;
  AccessSession? androidRefreshResult;
  final androidRefreshTokens = <String>[];
  var webLogoutCalls = 0;
  var logoutAllCalls = 0;

  @override
  Future<CsrfToken> fetchCsrf() async {
    csrfCalls++;
    return const CsrfToken(headerName: 'X-CSRF-TOKEN', value: 'csrf');
  }

  @override
  Future<AccessSession> loginWeb(String email, String password) async =>
      _session('old-access');

  @override
  Future<AccessSession> loginAndroid(String email, String password) async =>
      androidLoginResult ?? _session('old-access', refreshToken: 'refresh');

  @override
  Future<AccessSession> refreshWeb(CsrfToken csrfToken) async {
    refreshCalls++;
    if (refreshError) {
      throw const ApiHttpException(401);
    }
    return refreshResult ?? _session('restored-access');
  }

  @override
  Future<AccessSession> refreshAndroid(String refreshToken) async {
    androidRefreshTokens.add(refreshToken);
    if (refreshError) {
      throw const ApiHttpException(401);
    }
    return androidRefreshResult ??
        _session('restored-access', refreshToken: 'next');
  }

  @override
  Future<AuthIdentity> me() async => const AuthIdentity(
    publicId: 'a0a5b5ef-82bf-4d9f-9fe4-f07ad9bfec14',
    email: 'person@example.test',
    roles: ['ROLE_USER'],
  );

  @override
  Future<void> forgotPassword(String email) async {}
  @override
  Future<void> logoutAll() async {
    logoutAllCalls++;
  }

  @override
  Future<void> logoutAndroid(String refreshToken) async {}
  @override
  Future<void> logoutWeb(CsrfToken csrfToken) async {
    webLogoutCalls++;
  }

  @override
  Future<void> register({
    required String email,
    required String password,
    required String displayName,
  }) async {}
  @override
  Future<void> resendVerification(String email) async {}
  @override
  Future<void> resetPassword({
    required String token,
    required String password,
  }) async {}
  @override
  Future<void> verifyEmail(String token) async {}
}

class _MemoryRefreshTokenStore implements RefreshTokenStore {
  _MemoryRefreshTokenStore({this.value});

  String? value;
  final writes = <String>[];
  var readCalls = 0;
  var clearCalls = 0;

  @override
  Future<void> clear() async {
    clearCalls++;
    value = null;
  }

  @override
  Future<String?> read() async {
    readCalls++;
    return value;
  }

  @override
  Future<void> replace(String token) async {
    writes.add(token);
    value = token;
  }
}
