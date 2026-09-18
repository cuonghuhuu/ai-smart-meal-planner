import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Persistence boundary for Android's rotating refresh credential.
///
/// A production Android implementation must delegate to OS-backed secure
/// storage. Web implementations deliberately use [NoRefreshTokenStore], which
/// means a browser never persists an access or refresh token in localStorage.
abstract interface class RefreshTokenStore {
  Future<String?> read();
  Future<void> replace(String token);
  Future<void> clear();
}

class NoRefreshTokenStore implements RefreshTokenStore {
  @override
  Future<void> clear() async {}

  @override
  Future<String?> read() async => null;

  @override
  Future<void> replace(String token) async {
    throw UnsupportedError('A secure refresh-token store is required.');
  }
}

/// Android-only persistence for the opaque, rotating refresh credential.
///
/// The app factory never creates this store for Web, so browser sessions do
/// not use secure-storage's Web implementation or any persistent token store.
class FlutterSecureRefreshTokenStore implements RefreshTokenStore {
  FlutterSecureRefreshTokenStore({FlutterSecureStorage? storage})
    : _storage = storage ?? const FlutterSecureStorage();

  static const _refreshTokenKey = 'auth.refresh-token';

  final FlutterSecureStorage _storage;

  @override
  Future<void> clear() => _storage.delete(key: _refreshTokenKey);

  @override
  Future<String?> read() => _storage.read(key: _refreshTokenKey);

  @override
  Future<void> replace(String token) =>
      _storage.write(key: _refreshTokenKey, value: token);
}
