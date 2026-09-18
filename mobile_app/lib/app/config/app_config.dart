/// Centralized compile-time application configuration.
///
/// Future API-facing code must read its base URL from this class rather than
/// duplicating environment values across features.
final class AppConfig {
  const AppConfig._();

  static const String appName = 'Smart Meal Planner';
  static const String environment = String.fromEnvironment(
    'APP_ENV',
    defaultValue: 'development',
  );
  static const String apiBaseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'http://localhost:8080',
  );
}
