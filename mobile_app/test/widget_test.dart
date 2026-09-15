import 'dart:async';

import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:smart_meal_planner/app/app.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/features/foundation/data/backend_health_service.dart';

void main() {
  testWidgets(
    'renders the connected foundation screen without network access',
    (tester) async {
      await tester.pumpWidget(
        SmartMealPlannerApp(
          backendHealthChecker: _QueuedHealthChecker([
            () async => const BackendHealth(status: 'UP'),
          ]),
        ),
      );
      await tester.pump();

      expect(find.text('AI Smart Meal Planner'), findsWidgets);
      expect(find.text('Flutter Web foundation is ready.'), findsOneWidget);
      expect(find.text('Environment'), findsOneWidget);
      expect(find.text('API URL'), findsOneWidget);
      expect(find.text('Connected / UP'), findsOneWidget);
    },
  );

  testWidgets('shows checking while a health request is pending', (
    tester,
  ) async {
    final completer = Completer<BackendHealth>();

    await tester.pumpWidget(
      SmartMealPlannerApp(
        backendHealthChecker: _QueuedHealthChecker([() => completer.future]),
      ),
    );

    expect(find.text('Checking...'), findsOneWidget);

    completer.complete(const BackendHealth(status: 'UP'));
    await tester.pump();

    expect(find.text('Connected / UP'), findsOneWidget);
  });

  testWidgets('retries after a health check error', (tester) async {
    final firstHealthCheck = Completer<BackendHealth>();
    final secondHealthCheck = Completer<BackendHealth>();
    final healthChecker = _QueuedHealthChecker([
      () => firstHealthCheck.future,
      () => secondHealthCheck.future,
    ]);

    await tester.pumpWidget(
      SmartMealPlannerApp(backendHealthChecker: healthChecker),
    );

    expect(find.text('Checking...'), findsOneWidget);

    firstHealthCheck.completeError(
      const ApiTransportException(ApiTransportFailureKind.network),
    );
    await tester.pump();

    expect(find.text('Backend unavailable/error'), findsOneWidget);
    expect(find.text('Retry'), findsOneWidget);

    await tester.tap(find.byKey(const Key('retry-backend-health')));
    await tester.pump();

    expect(healthChecker.callCount, 2);
    expect(find.text('Checking...'), findsOneWidget);

    secondHealthCheck.complete(const BackendHealth(status: 'UP'));
    await tester.pump();

    expect(find.text('Connected / UP'), findsOneWidget);
  });
}

class _QueuedHealthChecker implements BackendHealthChecker {
  _QueuedHealthChecker(this._responses);

  final List<Future<BackendHealth> Function()> _responses;
  int callCount = 0;

  @override
  Future<BackendHealth> checkHealth() {
    callCount++;
    return _responses.removeAt(0)();
  }
}
