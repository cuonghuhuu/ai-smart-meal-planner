import 'package:smart_meal_planner/core/api/api_client.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/features/auth/domain/auth_models.dart';

abstract interface class AuthRepository {
  Future<CsrfToken> fetchCsrf();
  Future<AccessSession> loginWeb(String email, String password);
  Future<AccessSession> loginAndroid(String email, String password);
  Future<AccessSession> refreshWeb(CsrfToken csrfToken);
  Future<AccessSession> refreshAndroid(String refreshToken);
  Future<AuthIdentity> me();
  Future<void> logoutWeb(CsrfToken csrfToken);
  Future<void> logoutAndroid(String refreshToken);
  Future<void> logoutAll();
  Future<void> register({
    required String email,
    required String password,
    required String displayName,
  });
  Future<void> verifyEmail(String token);
  Future<void> resendVerification(String email);
  Future<void> forgotPassword(String email);
  Future<void> resetPassword({required String token, required String password});
}

class HttpAuthRepository implements AuthRepository {
  HttpAuthRepository(this._apiClient);

  final ApiClient _apiClient;

  @override
  Future<CsrfToken> fetchCsrf() async => CsrfToken.fromJson(
    await _map(_apiClient.requestJson('/api/v1/auth/csrf')),
  );

  @override
  Future<AccessSession> loginWeb(String email, String password) =>
      _login('/api/v1/auth/login/web', email, password);

  @override
  Future<AccessSession> loginAndroid(String email, String password) =>
      _login('/api/v1/auth/login/android', email, password);

  Future<AccessSession> _login(
    String path,
    String email,
    String password,
  ) async => AccessSession.fromJson(
    await _map(
      _apiClient.requestJson(
        path,
        method: 'POST',
        body: {'email': email, 'password': password},
        allowAuthenticationRetry: false,
      ),
    ),
  );

  @override
  Future<AccessSession> refreshWeb(CsrfToken csrfToken) async =>
      AccessSession.fromJson(
        await _map(
          _apiClient.requestJson(
            '/api/v1/auth/refresh',
            method: 'POST',
            body: const <String, Object>{},
            headers: {csrfToken.headerName: csrfToken.value},
            allowAuthenticationRetry: false,
          ),
        ),
      );

  @override
  Future<AccessSession> refreshAndroid(String refreshToken) async =>
      AccessSession.fromJson(
        await _map(
          _apiClient.requestJson(
            '/api/v1/auth/refresh',
            method: 'POST',
            body: {'refreshToken': refreshToken},
            allowAuthenticationRetry: false,
          ),
        ),
      );

  @override
  Future<AuthIdentity> me() async => AuthIdentity.fromJson(
    await _map(_apiClient.requestJson('/api/v1/auth/me', authenticated: true)),
  );

  @override
  Future<void> logoutWeb(CsrfToken csrfToken) => _noContent(
    '/api/v1/auth/logout',
    body: const <String, Object>{},
    headers: {csrfToken.headerName: csrfToken.value},
  );

  @override
  Future<void> logoutAndroid(String refreshToken) =>
      _noContent('/api/v1/auth/logout', body: {'refreshToken': refreshToken});

  @override
  Future<void> logoutAll() =>
      _noContent('/api/v1/auth/logout-all', authenticated: true);

  @override
  Future<void> register({
    required String email,
    required String password,
    required String displayName,
  }) => _noContent(
    '/api/v1/auth/register',
    body: {'email': email, 'password': password, 'displayName': displayName},
    expectJson: true,
  );

  @override
  Future<void> verifyEmail(String token) =>
      _noContent('/api/v1/auth/verify-email', body: {'token': token});

  @override
  Future<void> resendVerification(String email) =>
      _noContent('/api/v1/auth/resend-verification', body: {'email': email});

  @override
  Future<void> forgotPassword(String email) =>
      _noContent('/api/v1/auth/forgot-password', body: {'email': email});

  @override
  Future<void> resetPassword({
    required String token,
    required String password,
  }) => _noContent(
    '/api/v1/auth/reset-password',
    body: {'token': token, 'password': password},
  );

  Future<void> _noContent(
    String path, {
    Object? body,
    Map<String, String> headers = const {},
    bool authenticated = false,
    bool expectJson = false,
  }) async {
    await _apiClient.requestJson(
      path,
      method: 'POST',
      body: body,
      headers: headers,
      authenticated: authenticated,
      expectJson: expectJson,
      allowAuthenticationRetry: false,
    );
  }

  Future<Map<String, dynamic>> _map(Future<Object?> response) async {
    final value = await response;
    if (value is! Map<String, dynamic>) {
      throw const ApiResponseFormatException();
    }
    return value;
  }
}
