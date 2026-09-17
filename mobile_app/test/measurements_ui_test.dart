import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:smart_meal_planner/app/app.dart';
import 'package:smart_meal_planner/app/app_router.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/features/auth/application/session_controller.dart';
import 'package:smart_meal_planner/features/auth/data/refresh_token_store.dart';
import 'package:smart_meal_planner/features/measurements/application/measurements_controller.dart';
import 'package:smart_meal_planner/features/measurements/data/measurement_models.dart';
import 'package:smart_meal_planner/features/measurements/data/measurements_repository.dart';
import 'package:smart_meal_planner/features/measurements/presentation/measurements_page.dart';

import 'support/fake_auth_repository.dart';

void main() {
  testWidgets('authenticated /measurements opens Vietnamese page', (
    tester,
  ) async {
    final session = await _authenticatedSession();
    final router = AppRouter(
      session,
      measurementsController: _controller(),
    ).router;
    await tester.pumpWidget(_routerApp(router));
    router.go('/measurements');
    await _pumpAsync(tester);

    expect(find.byKey(const ValueKey('measurements-title')), findsOneWidget);
    expect(find.text('Số đo mới nhất'), findsOneWidget);
    expect(find.text('Bạn chưa có số đo nào.'), findsOneWidget);
    expect(find.text('Lịch sử số đo'), findsOneWidget);
    expect(find.text('Thêm số đo'), findsOneWidget);
  });

  testWidgets('anonymous /measurements redirects to login', (tester) async {
    final session = await _anonymousSession();
    final router = AppRouter(
      session,
      measurementsController: _controller(),
    ).router;
    await tester.pumpWidget(_routerApp(router));
    router.go('/measurements');
    await _pumpAsync(tester);

    expect(find.text('Chào mừng bạn quay lại'), findsOneWidget);
  });

  testWidgets('intended /measurements is restored after login', (tester) async {
    final session = await _anonymousSession();
    final router = AppRouter(
      session,
      measurementsController: _controller(),
    ).router;
    await tester.pumpWidget(_routerApp(router));
    router.go('/measurements');
    await _pumpAsync(tester);

    await tester.enterText(_fieldWithLabel('Email'), 'user@example.test');
    await tester.enterText(_fieldWithLabel('Mật khẩu'), 'Password123!');
    await tester.tap(find.widgetWithText(FilledButton, 'Đăng nhập'));
    await _pumpAsync(tester);

    expect(find.byKey(const ValueKey('measurements-title')), findsOneWidget);
  });

  testWidgets('external intended measurement routes are rejected', (
    tester,
  ) async {
    final session = await _authenticatedSession();
    final router = AppRouter(
      session,
      measurementsController: _controller(),
    ).router;
    await tester.pumpWidget(_routerApp(router));

    for (final target in [
      'https://evil.example/measurements',
      '//evil.example/measurements',
      'https://user@evil.example/measurements',
      'javascript:anything',
      '/measurements/unknown',
    ]) {
      router.go('/auth/login?from=${Uri.encodeComponent(target)}');
      await _pumpAsync(tester);
      expect(
        router.routerDelegate.currentConfiguration.uri.toString(),
        '/catalog/foods',
        reason: 'rejected intended route: $target',
      );
    }
  });

  testWidgets('Measurements navigation opens /measurements', (tester) async {
    final session = await _authenticatedSession();
    final router = AppRouter(
      session,
      measurementsController: _controller(),
    ).router;
    await tester.pumpWidget(_routerApp(router));
    await _pumpAsync(tester);

    await tester.tap(find.text('Số đo cơ thể'));
    await _pumpAsync(tester);
    expect(
      find.text('Theo dõi cân nặng và các chỉ số cơ thể theo thời gian.'),
      findsOneWidget,
    );
  });

  testWidgets('session change resets the injected measurements controller', (
    tester,
  ) async {
    final session = SessionController(
      authRepository: FakeAuthRepository(),
      refreshTokenStore: NoRefreshTokenStore(),
      isWeb: true,
    );
    await session.login('user@example.test', 'Password123!');
    final measurement = _measurement(DateTime(2026, 9, 17), 70);
    final controller = _controller(latest: measurement, history: [measurement]);
    await controller.load();

    await tester.pumpWidget(
      SmartMealPlannerApp(
        sessionController: session,
        measurementsController: controller,
      ),
    );
    await _pumpAsync(tester);

    await session.logout();
    await tester.pump();

    expect(controller.state.status, MeasurementsStatus.initial);
    expect(controller.state.latest, isNull);
    expect(controller.state.history, isEmpty);
  });

  testWidgets(
    'existing measurement displays Vietnamese source and optional fields',
    (tester) async {
      final measurement = _measurement(
        DateTime(2026, 9, 17),
        70.5,
        bodyFat: 18.5,
        waist: 82,
        note: 'Morning',
        source: MeasurementSource.corrected,
      );
      await tester.pumpWidget(
        _pageApp(_controller(latest: measurement, history: [measurement])),
      );
      await _pumpAsync(tester);

      expect(find.text('17/09/2026'), findsNWidgets(2));
      expect(find.textContaining('Đã hiệu chỉnh'), findsWidgets);
      expect(find.textContaining('18,5'), findsWidgets);
      expect(find.textContaining('82'), findsWidgets);
      expect(find.textContaining('Morning'), findsWidgets);
      expect(find.textContaining('Nguồn dữ liệu'), findsWidgets);
      expect(find.byType(DropdownButtonFormField<String>), findsNothing);
    },
  );

  testWidgets('add measurement uses stable fields and calls repository', (
    tester,
  ) async {
    final repository = FakeMeasurementsRepository();
    await tester.pumpWidget(_pageApp(_controller(repository: repository)));
    await _pumpAsync(tester);

    await tester.tap(find.byKey(const ValueKey('add-measurement')));
    await tester.pump();
    final weight = find.byKey(const ValueKey('measurement-weight'));
    await tester.enterText(weight, '70,5');
    final saveFinder = find.widgetWithText(FilledButton, 'Lưu số đo');
    expect(saveFinder, findsOneWidget);
    final saveButton = tester.widget<FilledButton>(saveFinder);
    expect(saveButton.onPressed, isNotNull);
    saveButton.onPressed!();
    await _pumpAsync(tester);

    expect(repository.recordCalls, 1);
    expect(repository.lastDraft?.weightKg, 70.5);
  });

  testWidgets('edit measurement keeps date immutable and uses update', (
    tester,
  ) async {
    final date = _today();
    final measurement = _measurement(date, 70);
    final repository = FakeMeasurementsRepository(
      latest: measurement,
      history: [measurement],
    );
    await tester.pumpWidget(_pageApp(_controller(repository: repository)));
    await _pumpAsync(tester);

    final edit = find.byKey(
      ValueKey('edit-measurement-${formatMeasurementDate(date)}'),
    );
    await tester.ensureVisible(edit);
    await tester.pump();
    await tester.tap(edit);
    await tester.pump();
    expect(
      find.text('Ngày đo không thể thay đổi khi chỉnh sửa.'),
      findsOneWidget,
    );

    final weight = find.byKey(const ValueKey('measurement-weight'));
    await tester.enterText(weight, '71');
    final saveFinder = find.widgetWithText(FilledButton, 'Cập nhật số đo');
    final saveButton = tester.widget<FilledButton>(saveFinder);
    saveButton.onPressed!();
    await _pumpAsync(tester);

    expect(repository.updateCalls, 1);
    expect(repository.lastDraft?.measuredOn, date);
  });

  testWidgets(
    'page remains usable at a narrow width without rendering errors',
    (tester) async {
      addTearDown(() => tester.binding.setSurfaceSize(null));
      await tester.binding.setSurfaceSize(const Size(360, 800));
      await tester.pumpWidget(_pageApp(_controller()));
      await _pumpAsync(tester);

      final scroll = find.byKey(const ValueKey('measurements-scroll'));
      expect(scroll, findsOneWidget);
      final addButton = find.byKey(const ValueKey('add-measurement'));
      await tester.ensureVisible(addButton);
      await tester.pump();
      expect(tester.takeException(), isNull);
    },
  );
}

Future<void> _pumpAsync(WidgetTester tester) async {
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 100));
}

Finder _fieldWithLabel(String label) =>
    find.ancestor(of: find.text(label), matching: find.byType(TextFormField));

Widget _routerApp(GoRouter router) => MaterialApp.router(routerConfig: router);

Widget _pageApp(MeasurementsController controller) => MaterialApp(
  home: MeasurementsPage(
    sessionController: _pageSession(),
    measurementsController: controller,
  ),
);

Future<SessionController> _anonymousSession() async {
  final session = SessionController(
    authRepository: FakeAuthRepository(
      refreshError: const ApiHttpException(401),
    ),
    refreshTokenStore: NoRefreshTokenStore(),
    isWeb: true,
  );
  await session.bootstrap();
  return session;
}

Future<SessionController> _authenticatedSession() async {
  final session = SessionController(
    authRepository: FakeAuthRepository(
      refreshError: const ApiHttpException(401),
    ),
    refreshTokenStore: NoRefreshTokenStore(),
    isWeb: true,
  );
  await session.login('user@example.test', 'Password123!');
  return session;
}

SessionController _pageSession() => SessionController(
  authRepository: FakeAuthRepository(),
  refreshTokenStore: NoRefreshTokenStore(),
  isWeb: true,
);

DateTime _today() {
  return backendUtcToday();
}

MeasurementsController _controller({
  FakeMeasurementsRepository? repository,
  BodyMeasurement? latest,
  List<BodyMeasurement>? history,
}) {
  return MeasurementsController(
    repository:
        repository ??
        FakeMeasurementsRepository(latest: latest, history: history),
  );
}

BodyMeasurement _measurement(
  DateTime date,
  double weight, {
  double? bodyFat,
  double? waist,
  String? note,
  MeasurementSource source = MeasurementSource.userEntered,
}) => BodyMeasurement(
  measuredOn: date,
  weightKg: weight,
  bodyFatPercent: bodyFat,
  waistCm: waist,
  source: source,
  note: note,
  createdAt: date,
);

final class FakeMeasurementsRepository implements MeasurementsRepository {
  FakeMeasurementsRepository({
    BodyMeasurement? latest,
    List<BodyMeasurement>? history,
  }) : latest =
           latest ??
           (history == null || history.isEmpty ? null : history.first),
       history = history ?? const [];

  BodyMeasurement? latest;
  List<BodyMeasurement> history;
  var recordCalls = 0;
  var updateCalls = 0;
  MeasurementDraft? lastDraft;

  @override
  Future<BodyMeasurement?> getLatestMeasurement() async => latest;

  @override
  Future<List<BodyMeasurement>> getMeasurementHistory({
    int page = 0,
    int size = 20,
    DateTime? from,
    DateTime? to,
  }) async => page == 0 ? history : const [];

  @override
  Future<BodyMeasurement> recordMeasurement(MeasurementDraft draft) async {
    recordCalls++;
    lastDraft = draft;
    final result = _measurement(draft.measuredOn, draft.weightKg!);
    latest = result;
    history = [result];
    return result;
  }

  @override
  Future<BodyMeasurement> updateMeasurement(MeasurementDraft draft) async {
    updateCalls++;
    lastDraft = draft;
    final result = _measurement(draft.measuredOn, draft.weightKg!);
    latest = result;
    history = [result];
    return result;
  }
}
