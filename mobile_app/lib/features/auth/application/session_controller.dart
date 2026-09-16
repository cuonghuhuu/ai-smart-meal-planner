import 'package:flutter/foundation.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/features/auth/data/auth_repository.dart';
import 'package:smart_meal_planner/features/auth/data/refresh_token_store.dart';
import 'package:smart_meal_planner/features/auth/domain/auth_models.dart';

class SessionController extends ChangeNotifier {
  SessionController({
    required this.authRepository,
    required this.refreshTokenStore,
    required this.isWeb,
  });

  final AuthRepository authRepository;
  final RefreshTokenStore refreshTokenStore;
  final bool isWeb;
  Future<bool>? _refreshFlight;
  CsrfToken? _csrfToken;
  String? _accessToken;
  DateTime? _accessTokenExpiresAt;
  AuthIdentity? _identity;
  SessionStatus _status = SessionStatus.bootstrapping;

  SessionStatus get status => _status;
  AuthIdentity? get identity => _identity;
  String? get accessToken => _accessToken;
  DateTime? get accessTokenExpiresAt => _accessTokenExpiresAt;
  bool get isAuthenticated => _status == SessionStatus.authenticated;

  Future<void> bootstrap() async {
    final restored = await refresh();
    if (restored) {
      try {
        _identity = await authRepository.me();
        _status = SessionStatus.authenticated;
      } on ApiException {
        await _clearSession();
      }
    }
    if (_status == SessionStatus.bootstrapping) {
      _status = SessionStatus.anonymous;
    }
    notifyListeners();
  }

  Future<void> login(String email, String password) async {
    try {
      final session = isWeb
          ? await authRepository.loginWeb(email, password)
          : await authRepository.loginAndroid(email, password);
      await _acceptSession(session);
      try {
        _identity = await authRepository.me();
      } on ApiException catch (error) {
        throw SessionInitializationException(error);
      }
      _status = SessionStatus.authenticated;
      notifyListeners();
    } on Object {
      await _clearSession();
      notifyListeners();
      rethrow;
    }
  }

  Future<void> register({
    required String email,
    required String password,
    required String displayName,
  }) => authRepository.register(
    email: email,
    password: password,
    displayName: displayName,
  );

  Future<void> verifyEmail(String token) => authRepository.verifyEmail(token);

  Future<void> resendVerification(String email) =>
      authRepository.resendVerification(email);

  Future<void> forgotPassword(String email) =>
      authRepository.forgotPassword(email);

  Future<void> resetPassword({
    required String token,
    required String password,
  }) => authRepository.resetPassword(token: token, password: password);

  /// Returns a single shared refresh result for all concurrent 401 responses.
  Future<bool> refresh() {
    final active = _refreshFlight;
    if (active != null) {
      return active;
    }
    final flight = _refresh();
    _refreshFlight = flight;
    return flight.whenComplete(() => _refreshFlight = null);
  }

  Future<bool> _refresh() async {
    try {
      final session = isWeb
          ? await authRepository.refreshWeb(await _csrf())
          : await _refreshAndroid();
      if (session == null) {
        await _clearSession();
        return false;
      }
      await _acceptSession(session);
      return true;
    } on Object {
      await _clearSession();
      return false;
    }
  }

  Future<AccessSession?> _refreshAndroid() async {
    final refreshToken = await refreshTokenStore.read();
    return refreshToken == null
        ? null
        : authRepository.refreshAndroid(refreshToken);
  }

  Future<CsrfToken> _csrf() async =>
      _csrfToken ??= await authRepository.fetchCsrf();

  Future<void> _acceptSession(AccessSession session) async {
    if (!isWeb) {
      final refreshToken = session.refreshToken;
      if (refreshToken == null || refreshToken.isEmpty) {
        throw const ApiResponseFormatException();
      }
      await refreshTokenStore.replace(refreshToken);
    }
    _accessToken = session.accessToken;
    _accessTokenExpiresAt = session.accessTokenExpiresAt;
  }

  Future<void> logout() async {
    try {
      if (isWeb) {
        await authRepository.logoutWeb(await _csrf());
      } else {
        final token = await refreshTokenStore.read();
        if (token != null) {
          await authRepository.logoutAndroid(token);
        }
      }
    } on ApiException {
      // Local state must still be cleared after a failed remote logout.
    } finally {
      await _clearSession();
      notifyListeners();
    }
  }

  Future<void> logoutAll() async {
    try {
      await authRepository.logoutAll();
    } finally {
      await _clearSession();
      notifyListeners();
    }
  }

  Future<void> _clearSession() async {
    _accessToken = null;
    _accessTokenExpiresAt = null;
    _identity = null;
    _status = SessionStatus.anonymous;
    if (!isWeb) {
      await refreshTokenStore.clear();
    }
  }
}
