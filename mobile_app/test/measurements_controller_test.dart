import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:smart_meal_planner/features/measurements/application/measurements_controller.dart';
import 'package:smart_meal_planner/features/measurements/data/measurement_models.dart';
import 'package:smart_meal_planner/features/measurements/data/measurements_repository.dart';

void main() {
  test('initial load loads latest and first history page', () async {
    final repository = FakeMeasurementsRepository(
      latest: _measurement(DateTime(2026, 9, 17), 70.5),
      historyPages: {
        0: [_measurement(DateTime(2026, 9, 17), 70.5)],
      },
    );
    final controller = MeasurementsController(repository: repository);
    final statuses = <MeasurementsStatus>[];
    controller.addListener(() => statuses.add(controller.state.status));

    await controller.load();

    expect(statuses, [MeasurementsStatus.loading, MeasurementsStatus.loaded]);
    expect(controller.state.latest?.weightKg, 70.5);
    expect(controller.state.history, hasLength(1));
    expect(repository.historyRequests.single, (page: 0, from: null, to: null));
  });

  test('latest 404 empty state does not block history', () async {
    final repository = FakeMeasurementsRepository(
      latest: null,
      historyPages: {
        0: [_measurement(DateTime(2026, 9, 15), 71)],
      },
    );
    final controller = MeasurementsController(repository: repository);

    await controller.load();

    expect(controller.state.status, MeasurementsStatus.loaded);
    expect(controller.state.latest, isNull);
    expect(controller.state.history, hasLength(1));
  });

  test('record and update reload complete server state', () async {
    final serverMeasurement = _measurement(_today(), 69.8);
    final repository = FakeMeasurementsRepository(
      latest: serverMeasurement,
      historyPages: {
        0: [serverMeasurement],
      },
      recordResult: serverMeasurement,
      updateResult: serverMeasurement,
    );
    final controller = MeasurementsController(repository: repository);
    await controller.load();

    await controller.recordMeasurement(_draft(serverMeasurement.measuredOn));
    expect(repository.recordCalls, 1);
    expect(controller.state.latest?.weightKg, 69.8);
    expect(controller.state.saveMessage, 'Đã lưu số đo.');

    await controller.updateMeasurement(_draft(serverMeasurement.measuredOn));
    expect(repository.updateCalls, 1);
    expect(controller.state.latest?.weightKg, 69.8);
    expect(controller.state.saveMessage, 'Đã cập nhật số đo.');
  });

  test(
    'history load more appends in order and avoids duplicate dates',
    () async {
      final firstPage = [
        for (var index = 0; index < 20; index++)
          _measurement(DateTime(2026, 9, 20 - index), 70.0 - index),
      ];
      final secondPage = [
        firstPage.last,
        _measurement(DateTime(2026, 8, 31), 49),
      ];
      final repository = FakeMeasurementsRepository(
        latest: firstPage.first,
        historyPages: {0: firstPage, 1: secondPage},
      );
      final controller = MeasurementsController(repository: repository);
      await controller.load();
      await controller.loadMore();

      expect(controller.state.history, hasLength(21));
      expect(controller.state.history.last.measuredOn, DateTime(2026, 8, 31));
      expect(controller.state.hasMore, isFalse);
    },
  );

  test('concurrent load more requests are guarded', () async {
    final firstPage = [
      for (var index = 0; index < 20; index++)
        _measurement(DateTime(2026, 9, 20 - index), 70.0 - index),
    ];
    final pending = Completer<List<BodyMeasurement>>();
    final repository = FakeMeasurementsRepository(
      latest: firstPage.first,
      historyPages: {0: firstPage},
      pendingPage: pending,
    );
    final controller = MeasurementsController(repository: repository);
    await controller.load();

    final first = controller.loadMore();
    final second = controller.loadMore();
    expect(repository.pageOneCalls, 1);
    pending.complete([_measurement(DateTime(2026, 8, 31), 50)]);
    await Future.wait([first, second]);
    expect(controller.state.history, hasLength(21));
  });

  test(
    'complete date range is preserved and one-sided range is rejected',
    () async {
      final repository = FakeMeasurementsRepository(
        latest: _measurement(DateTime(2026, 9, 17), 70),
        historyPages: {0: []},
      );
      final controller = MeasurementsController(repository: repository);
      await controller.load(
        from: DateTime(2026, 9, 1),
        to: DateTime(2026, 9, 17),
      );
      expect(repository.historyRequests.single.from, DateTime(2026, 9, 1));
      expect(repository.historyRequests.single.to, DateTime(2026, 9, 17));

      repository.historyRequests.clear();
      await controller.load(from: DateTime(2026, 9, 1));
      expect(controller.state.errorMessage, isNotNull);
      expect(repository.historyRequests, isEmpty);
    },
  );

  test(
    'session reset invalidates stale loads before a fresh session load',
    () async {
      final repository = _SessionRaceRepository();
      final controller = MeasurementsController(repository: repository);
      final userAMeasurement = _measurement(DateTime(2026, 9, 17), 70);
      final userBMeasurement = _measurement(DateTime(2026, 9, 16), 71);

      final staleLoad = controller.load();
      expect(controller.state.status, MeasurementsStatus.loading);

      controller.resetForSessionChange();
      expect(controller.state.status, MeasurementsStatus.initial);
      expect(controller.state.latest, isNull);
      expect(controller.state.history, isEmpty);
      expect(controller.state.page, 0);
      expect(controller.state.hasMore, isFalse);
      expect(controller.state.hasLoadedData, isFalse);
      expect(controller.state.appliedFrom, isNull);
      expect(controller.state.appliedTo, isNull);
      expect(controller.state.errorMessage, isNull);
      expect(controller.state.saveMessage, isNull);

      final freshLoad = controller.load();
      expect(controller.state.status, MeasurementsStatus.loading);

      repository.completeUserA(userAMeasurement);
      await staleLoad;

      expect(controller.state.status, MeasurementsStatus.loading);
      expect(controller.state.latest, isNull);
      expect(controller.state.history, isEmpty);

      repository.completeUserB(userBMeasurement);
      await freshLoad;

      expect(controller.state.status, MeasurementsStatus.loaded);
      expect(controller.state.latest?.weightKg, 71);
      expect(controller.state.history.single.weightKg, 71);
    },
  );

  test('stale load errors cannot overwrite the reset session state', () async {
    final repository = _SessionRaceRepository();
    final controller = MeasurementsController(repository: repository);

    final staleLoad = controller.load();
    controller.resetForSessionChange();
    repository.failUserA(StateError('stale User-A failure'));

    await staleLoad;

    expect(controller.state.status, MeasurementsStatus.initial);
    expect(controller.state.latest, isNull);
    expect(controller.state.history, isEmpty);
    expect(controller.state.errorMessage, isNull);
  });
}

BodyMeasurement _measurement(DateTime date, double weight) => BodyMeasurement(
  measuredOn: date,
  weightKg: weight,
  bodyFatPercent: null,
  waistCm: null,
  source: MeasurementSource.userEntered,
  note: null,
  createdAt: date,
);

MeasurementDraft _draft(DateTime date) => MeasurementDraft(
  measuredOn: date,
  weightKg: 70,
  bodyFatPercent: null,
  waistCm: null,
  note: null,
);

DateTime _today() {
  return backendUtcToday();
}

final class _SessionRaceRepository implements MeasurementsRepository {
  final userALatest = Completer<BodyMeasurement?>();
  final userAHistory = Completer<List<BodyMeasurement>>();
  final userBLatest = Completer<BodyMeasurement?>();
  final userBHistory = Completer<List<BodyMeasurement>>();
  var _latestCalls = 0;
  var _historyCalls = 0;

  @override
  Future<BodyMeasurement?> getLatestMeasurement() =>
      (_latestCalls++ == 0 ? userALatest : userBLatest).future;

  @override
  Future<List<BodyMeasurement>> getMeasurementHistory({
    int page = 0,
    int size = 20,
    DateTime? from,
    DateTime? to,
  }) => (_historyCalls++ == 0 ? userAHistory : userBHistory).future;

  void completeUserA(BodyMeasurement measurement) {
    userALatest.complete(measurement);
    userAHistory.complete([measurement]);
  }

  void completeUserB(BodyMeasurement measurement) {
    userBLatest.complete(measurement);
    userBHistory.complete([measurement]);
  }

  void failUserA(Object error) {
    userALatest.completeError(error);
    userAHistory.complete(const []);
  }

  @override
  Future<BodyMeasurement> recordMeasurement(MeasurementDraft draft) =>
      throw UnimplementedError();

  @override
  Future<BodyMeasurement> updateMeasurement(MeasurementDraft draft) =>
      throw UnimplementedError();
}

final class FakeMeasurementsRepository implements MeasurementsRepository {
  FakeMeasurementsRepository({
    required this.latest,
    required this.historyPages,
    this.recordResult,
    this.updateResult,
    this.pendingPage,
  });

  BodyMeasurement? latest;
  final Map<int, List<BodyMeasurement>> historyPages;
  final BodyMeasurement? recordResult;
  final BodyMeasurement? updateResult;
  final Completer<List<BodyMeasurement>>? pendingPage;
  final historyRequests = <({int page, DateTime? from, DateTime? to})>[];
  var recordCalls = 0;
  var updateCalls = 0;
  var pageOneCalls = 0;

  @override
  Future<BodyMeasurement?> getLatestMeasurement() async => latest;

  @override
  Future<List<BodyMeasurement>> getMeasurementHistory({
    int page = 0,
    int size = 20,
    DateTime? from,
    DateTime? to,
  }) async {
    historyRequests.add((page: page, from: from, to: to));
    if (page == 1) {
      pageOneCalls++;
      if (pendingPage != null) {
        return pendingPage!.future;
      }
    }
    return historyPages[page] ?? const [];
  }

  @override
  Future<BodyMeasurement> recordMeasurement(MeasurementDraft draft) async {
    recordCalls++;
    return recordResult ?? latest!;
  }

  @override
  Future<BodyMeasurement> updateMeasurement(MeasurementDraft draft) async {
    updateCalls++;
    return updateResult ?? latest!;
  }
}
