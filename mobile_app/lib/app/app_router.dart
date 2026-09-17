import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:smart_meal_planner/l10n/app_strings.dart';
import 'package:smart_meal_planner/features/auth/application/session_controller.dart';
import 'package:smart_meal_planner/features/auth/domain/auth_models.dart';
import 'package:smart_meal_planner/features/auth/presentation/auth_pages.dart';
import 'package:smart_meal_planner/features/auth/presentation/authenticated_shell.dart';
import 'package:smart_meal_planner/features/auth/presentation/session_gate.dart';
import 'package:smart_meal_planner/features/preferences/application/preferences_controller.dart';
import 'package:smart_meal_planner/features/preferences/presentation/preferences_page.dart';
import 'package:smart_meal_planner/features/measurements/application/measurements_controller.dart';
import 'package:smart_meal_planner/features/measurements/presentation/measurements_page.dart';
import 'package:smart_meal_planner/features/profile/application/profile_controller.dart';
import 'package:smart_meal_planner/features/profile/presentation/profile_page.dart';

final class AppRouter {
  AppRouter(
    SessionController sessionController, {
    ProfileController? profileController,
    PreferencesController? preferencesController,
    MeasurementsController? measurementsController,
  }) : router = GoRouter(
         initialLocation: '/catalog/foods',
         refreshListenable: sessionController,
         redirect: (context, state) => _redirect(sessionController, state),
         routes: [
           GoRoute(
             path: '/auth/login',
             builder: (context, state) => SessionRouteGate(
               sessionController: sessionController,
               child: LoginPage(
                 sessionController: sessionController,
                 showResetSuccess:
                     state.uri.queryParameters['reset'] == 'success',
               ),
             ),
           ),
           GoRoute(
             path: '/auth/register',
             builder: (context, state) => SessionRouteGate(
               sessionController: sessionController,
               child: RegistrationPage(sessionController: sessionController),
             ),
           ),
           GoRoute(
             path: '/auth/verify-email',
             builder: (context, state) => SessionRouteGate(
               sessionController: sessionController,
               child: VerifyEmailPage(
                 sessionController: sessionController,
                 token: state.uri.queryParameters['token'],
                 email: state.uri.queryParameters['email'],
               ),
             ),
           ),
           GoRoute(
             path: '/auth/forgot-password',
             builder: (context, state) => SessionRouteGate(
               sessionController: sessionController,
               child: ForgotPasswordPage(sessionController: sessionController),
             ),
           ),
           GoRoute(
             path: '/auth/reset-password',
             builder: (context, state) => SessionRouteGate(
               sessionController: sessionController,
               child: ResetPasswordPage(
                 sessionController: sessionController,
                 token: state.uri.queryParameters['token'],
               ),
             ),
           ),
           GoRoute(
             path: '/catalog/foods',
             builder: (context, state) => SessionRouteGate(
               sessionController: sessionController,
               child: AuthenticatedShell(sessionController: sessionController),
             ),
           ),
           GoRoute(
             path: '/profile',
             builder: (context, state) => SessionRouteGate(
               sessionController: sessionController,
               child: profileController == null
                   ? const _ProfileUnavailablePage()
                   : ProfilePage(
                       sessionController: sessionController,
                       profileController: profileController,
                     ),
             ),
           ),
           GoRoute(
             path: '/preferences',
             builder: (context, state) => SessionRouteGate(
               sessionController: sessionController,
               child: preferencesController == null
                   ? const _PreferencesUnavailablePage()
                   : PreferencesPage(
                       sessionController: sessionController,
                       preferencesController: preferencesController,
                     ),
             ),
           ),
           GoRoute(
             path: '/measurements',
             builder: (context, state) => SessionRouteGate(
               sessionController: sessionController,
               child: measurementsController == null
                   ? const _MeasurementsUnavailablePage()
                   : MeasurementsPage(
                       sessionController: sessionController,
                       measurementsController: measurementsController,
                     ),
             ),
           ),
         ],
         errorBuilder: (context, state) => const _NotFoundPage(),
       );

  final GoRouter router;

  static String? _redirect(SessionController session, GoRouterState state) {
    if (session.status == SessionStatus.bootstrapping) {
      return null;
    }
    final path = state.uri.path;
    final isAuthRoute = path.startsWith('/auth/');
    final isProtectedRoute =
        path == '/catalog/foods' ||
        path == '/profile' ||
        path == '/preferences' ||
        path == '/measurements';

    if (session.status == SessionStatus.anonymous && isProtectedRoute) {
      return '/auth/login?from=${Uri.encodeComponent(state.uri.toString())}';
    }

    if (session.status == SessionStatus.authenticated && isAuthRoute) {
      return _safeIntendedDestination(state.uri.queryParameters['from']);
    }
    return null;
  }

  static String _safeIntendedDestination(String? intended) {
    if (intended == null || intended.isEmpty) {
      return '/catalog/foods';
    }
    final uri = Uri.tryParse(intended);
    return uri != null &&
            !uri.hasAuthority &&
            uri.scheme.isEmpty &&
            uri.host.isEmpty &&
            uri.userInfo.isEmpty &&
            (uri.path == '/catalog/foods' ||
                uri.path == '/profile' ||
                uri.path == '/preferences' ||
                uri.path == '/measurements')
        ? uri.toString()
        : '/catalog/foods';
  }
}

class _ProfileUnavailablePage extends StatelessWidget {
  const _ProfileUnavailablePage();

  @override
  Widget build(BuildContext context) =>
      const Scaffold(body: Center(child: Text(AppStrings.profileUnavailable)));
}

class _PreferencesUnavailablePage extends StatelessWidget {
  const _PreferencesUnavailablePage();

  @override
  Widget build(BuildContext context) => const Scaffold(
    body: Center(child: Text(AppStrings.preferencesUnavailable)),
  );
}

class _MeasurementsUnavailablePage extends StatelessWidget {
  const _MeasurementsUnavailablePage();

  @override
  Widget build(BuildContext context) => const Scaffold(
    body: Center(child: Text(AppStrings.measurementsUnavailable)),
  );
}

class _NotFoundPage extends StatelessWidget {
  const _NotFoundPage();

  @override
  Widget build(BuildContext context) => Scaffold(
    body: Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(
            AppStrings.pageNotFound,
            style: Theme.of(context).textTheme.headlineSmall,
          ),
          const SizedBox(height: 12),
          FilledButton(
            onPressed: () => context.go('/catalog/foods'),
            child: const Text(AppStrings.goToFoods),
          ),
        ],
      ),
    ),
  );
}
