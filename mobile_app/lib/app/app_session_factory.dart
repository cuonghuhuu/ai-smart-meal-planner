import 'package:flutter/foundation.dart';
import 'package:smart_meal_planner/core/api/api_client.dart';
import 'package:smart_meal_planner/features/auth/application/session_controller.dart';
import 'package:smart_meal_planner/features/auth/data/auth_repository.dart';
import 'package:smart_meal_planner/features/auth/data/refresh_token_store.dart';
import 'package:smart_meal_planner/features/profile/application/profile_controller.dart';
import 'package:smart_meal_planner/features/profile/data/profile_repository.dart';
import 'package:smart_meal_planner/features/profile/data/reference_data_repository.dart';

final class AppSessionDependencies {
  const AppSessionDependencies({
    required this.sessionController,
    required this.profileController,
  });

  final SessionController sessionController;
  final ProfileController profileController;
}

final class AppSessionFactory {
  const AppSessionFactory._();

  static AppSessionDependencies create() {
    final apiClient = ApiClient();
    final session = SessionController(
      authRepository: HttpAuthRepository(apiClient),
      refreshTokenStore: kIsWeb
          ? NoRefreshTokenStore()
          : FlutterSecureRefreshTokenStore(),
      isWeb: kIsWeb,
    );
    apiClient.configureAuthentication(
      accessTokenProvider: () => session.accessToken,
      refreshAccessToken: session.refresh,
    );
    return AppSessionDependencies(
      sessionController: session,
      profileController: ProfileController(
        profileRepository: HttpProfileRepository(apiClient),
        referenceDataRepository: HttpReferenceDataRepository(apiClient),
      ),
    );
  }
}
