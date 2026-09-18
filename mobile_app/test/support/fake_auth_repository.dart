import 'package:smart_meal_planner/features/auth/data/auth_repository.dart';
import 'package:smart_meal_planner/features/auth/domain/auth_models.dart';

class FakeAuthRepository implements AuthRepository {
  FakeAuthRepository({
    this.loginError,
    this.refreshError,
    this.registerError,
    this.verifyError,
    this.resetError,
  });

  Object? loginError;
  Object? refreshError;
  Object? registerError;
  Object? verifyError;
  Object? resetError;
  var loginCalls = 0;
  var registerCalls = 0;
  var verifyCalls = 0;
  var resendCalls = 0;
  var forgotCalls = 0;
  var resetCalls = 0;
  var logoutCalls = 0;
  String? lastPassword;
  String? lastToken;

  static AccessSession session() => AccessSession(
    accessToken: 'access',
    accessTokenExpiresAt: DateTime.utc(2030),
    refreshTokenExpiresAt: DateTime.utc(2030, 1, 2),
  );

  @override
  Future<CsrfToken> fetchCsrf() async =>
      const CsrfToken(headerName: 'X-CSRF-TOKEN', value: 'csrf');

  @override
  Future<void> forgotPassword(String email) async {
    forgotCalls++;
  }

  @override
  Future<AccessSession> loginAndroid(String email, String password) =>
      loginWeb(email, password);

  @override
  Future<AccessSession> loginWeb(String email, String password) async {
    loginCalls++;
    lastPassword = password;
    if (loginError != null) {
      throw loginError!;
    }
    return session();
  }

  @override
  Future<void> logoutAll() async {
    logoutCalls++;
  }

  @override
  Future<void> logoutAndroid(String refreshToken) async {
    logoutCalls++;
  }

  @override
  Future<void> logoutWeb(CsrfToken csrfToken) async {
    logoutCalls++;
  }

  @override
  Future<AuthIdentity> me() async => const AuthIdentity(
    publicId: 'a0a5b5ef-82bf-4d9f-9fe4-f07ad9bfec14',
    email: 'person@example.test',
    roles: ['ROLE_USER'],
  );

  @override
  Future<AccessSession> refreshAndroid(String refreshToken) =>
      refreshWeb(const CsrfToken(headerName: 'X-CSRF-TOKEN', value: 'csrf'));

  @override
  Future<AccessSession> refreshWeb(CsrfToken csrfToken) async {
    if (refreshError != null) {
      throw refreshError!;
    }
    return session();
  }

  @override
  Future<void> register({
    required String email,
    required String password,
    required String displayName,
  }) async {
    registerCalls++;
    lastPassword = password;
    if (registerError != null) {
      throw registerError!;
    }
  }

  @override
  Future<void> resendVerification(String email) async {
    resendCalls++;
  }

  @override
  Future<void> resetPassword({
    required String token,
    required String password,
  }) async {
    resetCalls++;
    lastToken = token;
    lastPassword = password;
    if (resetError != null) {
      throw resetError!;
    }
  }

  @override
  Future<void> verifyEmail(String token) async {
    verifyCalls++;
    lastToken = token;
    if (verifyError != null) {
      throw verifyError!;
    }
  }
}
