import 'package:flutter/material.dart';
import 'package:smart_meal_planner/features/auth/application/session_controller.dart';
import 'package:smart_meal_planner/features/auth/domain/auth_models.dart';

class SessionRouteGate extends StatelessWidget {
  const SessionRouteGate({
    super.key,
    required this.sessionController,
    required this.child,
  });

  final SessionController sessionController;
  final Widget child;

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
    animation: sessionController,
    builder: (context, _) =>
        sessionController.status == SessionStatus.bootstrapping
        ? const _BootstrapPage()
        : child,
  );
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
