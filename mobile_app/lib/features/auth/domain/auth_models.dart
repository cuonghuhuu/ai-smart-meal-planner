final class AuthIdentity {
  const AuthIdentity({
    required this.publicId,
    required this.email,
    required this.roles,
  });

  final String publicId;
  final String email;
  final List<String> roles;

  factory AuthIdentity.fromJson(Map<String, dynamic> json) => AuthIdentity(
    publicId: json['publicId'] as String,
    email: json['email'] as String,
    roles: (json['roles'] as List<Object?>).cast<String>(),
  );
}

final class AccessSession {
  const AccessSession({
    required this.accessToken,
    required this.accessTokenExpiresAt,
    required this.refreshTokenExpiresAt,
    this.refreshToken,
  });

  final String accessToken;
  final DateTime accessTokenExpiresAt;
  final DateTime refreshTokenExpiresAt;
  final String? refreshToken;

  factory AccessSession.fromJson(Map<String, dynamic> json) => AccessSession(
    accessToken: json['accessToken'] as String,
    accessTokenExpiresAt: DateTime.parse(
      json['accessTokenExpiresAt'] as String,
    ),
    refreshTokenExpiresAt: DateTime.parse(
      json['refreshTokenExpiresAt'] as String,
    ),
    refreshToken: json['refreshToken'] as String?,
  );
}

final class CsrfToken {
  const CsrfToken({required this.headerName, required this.value});

  final String headerName;
  final String value;

  factory CsrfToken.fromJson(Map<String, dynamic> json) => CsrfToken(
    headerName: json['headerName'] as String,
    value: json['token'] as String,
  );
}

enum SessionStatus { bootstrapping, anonymous, authenticated }
