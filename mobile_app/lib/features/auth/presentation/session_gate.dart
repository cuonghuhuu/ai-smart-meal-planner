import 'package:flutter/material.dart';
import 'package:smart_meal_planner/features/auth/application/session_controller.dart';
import 'package:smart_meal_planner/features/auth/domain/auth_models.dart';

class SessionGate extends StatelessWidget {
  const SessionGate({super.key, required this.sessionController});

  final SessionController sessionController;

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: sessionController,
      builder: (context, child) => switch (sessionController.status) {
        SessionStatus.bootstrapping => const _BootstrapPage(),
        SessionStatus.anonymous => const _AnonymousPlaceholder(),
        SessionStatus.authenticated => _AuthenticatedPlaceholder(
          sessionController: sessionController,
        ),
      },
    );
  }
}

class _BootstrapPage extends StatelessWidget {
  const _BootstrapPage();

  @override
  Widget build(BuildContext context) => Scaffold(
    body: Center(
      child: Semantics(
        label: 'Restoring session',
        child: CircularProgressIndicator(),
      ),
    ),
  );
}

class _AnonymousPlaceholder extends StatelessWidget {
  const _AnonymousPlaceholder();

  @override
  Widget build(BuildContext context) => const Scaffold(
    body: Center(child: Text('Sign in is available in the next step.')),
  );
}

class _AuthenticatedPlaceholder extends StatelessWidget {
  const _AuthenticatedPlaceholder({required this.sessionController});

  final SessionController sessionController;

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: const Text('AI Smart Meal Planner'),
      actions: [
        TextButton(
          onPressed: sessionController.logout,
          child: const Text('Sign out'),
        ),
      ],
    ),
    body: Center(
      child: Text('Signed in as ${sessionController.identity?.email ?? ''}'),
    ),
  );
}
