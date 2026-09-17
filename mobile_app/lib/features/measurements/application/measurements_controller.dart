import 'package:flutter/foundation.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/features/measurements/application/measurements_validation.dart';
import 'package:smart_meal_planner/features/measurements/data/measurement_models.dart';
import 'package:smart_meal_planner/features/measurements/data/measurements_repository.dart';
import 'package:smart_meal_planner/l10n/app_strings.dart';

enum MeasurementsStatus { initial, loading, loaded, saving, loadingMore, error }

final class MeasurementsState {
  const MeasurementsState({
    required this.status,
    required this.latest,
    required this.history,
    required this.page,
    required this.hasMore,
    required this.hasLoadedData,
    required this.appliedFrom,
    required this.appliedTo,
    required this.errorMessage,
    required this.saveMessage,
  });

  factory MeasurementsState.initial() => const MeasurementsState(
    status: MeasurementsStatus.initial,
    latest: null,
    history: [],
    page: 0,
    hasMore: false,
    hasLoadedData: false,
    appliedFrom: null,
    appliedTo: null,
    errorMessage: null,
    saveMessage: null,
  );

  final MeasurementsStatus status;
  final BodyMeasurement? latest;
  final List<BodyMeasurement> history;
  final int page;
  final bool hasMore;
  final bool hasLoadedData;
  final DateTime? appliedFrom;
  final DateTime? appliedTo;
  final String? errorMessage;
  final String? saveMessage;

  bool get isBusy =>
      status == MeasurementsStatus.loading ||
      status == MeasurementsStatus.saving ||
      status == MeasurementsStatus.loadingMore;

  MeasurementsState copyWith({
    MeasurementsStatus? status,
    BodyMeasurement? latest,
    bool clearLatest = false,
    List<BodyMeasurement>? history,
    int? page,
    bool? hasMore,
    bool? hasLoadedData,
    Object? appliedFrom = _unset,
    Object? appliedTo = _unset,
    Object? errorMessage = _unset,
    Object? saveMessage = _unset,
  }) => MeasurementsState(
    status: status ?? this.status,
    latest: clearLatest ? null : latest ?? this.latest,
    history: history ?? this.history,
    page: page ?? this.page,
    hasMore: hasMore ?? this.hasMore,
    hasLoadedData: hasLoadedData ?? this.hasLoadedData,
    appliedFrom: identical(appliedFrom, _unset)
        ? this.appliedFrom
        : appliedFrom as DateTime?,
    appliedTo: identical(appliedTo, _unset)
        ? this.appliedTo
        : appliedTo as DateTime?,
    errorMessage: identical(errorMessage, _unset)
        ? this.errorMessage
        : errorMessage as String?,
    saveMessage: identical(saveMessage, _unset)
        ? this.saveMessage
        : saveMessage as String?,
  );
}

const _unset = Object();

final class MeasurementsController extends ChangeNotifier {
  MeasurementsController({required this.repository, this.pageSize = 20})
    : _state = MeasurementsState.initial();

  final MeasurementsRepository repository;
  final int pageSize;
  MeasurementsState _state;
  int _generation = 0;

  MeasurementsState get state => _state;

  void resetForSessionChange() {
    _generation++;
    _state = MeasurementsState.initial();
    if (hasListeners) {
      notifyListeners();
    }
  }

  Future<void> load({DateTime? from, DateTime? to}) async {
    if ((from == null) != (to == null)) {
      _setError(AppStrings.measurementDateRangeRequired);
      return;
    }
    if (from != null && to != null && from.isAfter(to)) {
      _setError(AppStrings.measurementDateRangeInvalid);
      return;
    }
    if (_state.status == MeasurementsStatus.loading ||
        _state.status == MeasurementsStatus.saving ||
        _state.status == MeasurementsStatus.loadingMore) {
      return;
    }
    final generation = _generation;
    await _loadData(from: from, to: to, generation: generation);
  }

  Future<void> reload() => load(from: _state.appliedFrom, to: _state.appliedTo);

  Future<void> loadMore() async {
    if (_state.status != MeasurementsStatus.loaded || !_state.hasMore) {
      return;
    }
    final generation = _generation;
    final nextPage = _state.page + 1;
    final from = _state.appliedFrom;
    final to = _state.appliedTo;
    _state = _state.copyWith(
      status: MeasurementsStatus.loadingMore,
      errorMessage: null,
    );
    notifyListeners();
    try {
      final items = await repository.getMeasurementHistory(
        page: nextPage,
        size: pageSize,
        from: from,
        to: to,
      );
      if (!_isCurrent(generation)) {
        return;
      }
      final existingDates = {
        for (final item in _state.history)
          formatMeasurementDate(item.measuredOn),
      };
      final additions = [
        for (final item in items)
          if (existingDates.add(formatMeasurementDate(item.measuredOn))) item,
      ];
      _state = _state.copyWith(
        status: MeasurementsStatus.loaded,
        history: [..._state.history, ...additions],
        page: nextPage,
        hasMore: items.length == pageSize && additions.isNotEmpty,
        errorMessage: null,
      );
    } on Object catch (error) {
      if (!_isCurrent(generation)) {
        return;
      }
      _state = _state.copyWith(
        status: MeasurementsStatus.error,
        errorMessage: measurementsErrorMessage(error),
      );
    }
    if (_isCurrent(generation)) {
      notifyListeners();
    }
  }

  Future<void> recordMeasurement(MeasurementDraft draft) async {
    await _save(
      draft,
      operation: repository.recordMeasurement,
      successMessage: AppStrings.measurementSaved,
    );
  }

  Future<void> updateMeasurement(MeasurementDraft draft) async {
    await _save(
      draft,
      operation: repository.updateMeasurement,
      successMessage: AppStrings.measurementUpdated,
    );
  }

  Future<void> _save(
    MeasurementDraft draft, {
    required Future<BodyMeasurement> Function(MeasurementDraft) operation,
    required String successMessage,
  }) async {
    if (_state.isBusy) {
      return;
    }
    final errors = MeasurementsValidation.validateDraft(draft);
    if (errors.isNotEmpty) {
      _setError(errors.values.first);
      return;
    }

    _state = _state.copyWith(
      status: MeasurementsStatus.saving,
      errorMessage: null,
      saveMessage: null,
    );
    final generation = _generation;
    notifyListeners();
    try {
      await operation(draft);
      if (!_isCurrent(generation)) {
        return;
      }
      await _loadData(
        from: _state.appliedFrom,
        to: _state.appliedTo,
        successMessage: successMessage,
        generation: generation,
      );
    } on Object catch (error) {
      if (!_isCurrent(generation)) {
        return;
      }
      _state = _state.copyWith(
        status: MeasurementsStatus.error,
        errorMessage: measurementsErrorMessage(error, saving: true),
        saveMessage: null,
      );
      notifyListeners();
    }
  }

  Future<void> _loadData({
    required DateTime? from,
    required DateTime? to,
    required int generation,
    String? successMessage,
  }) async {
    if (!_isCurrent(generation)) {
      return;
    }
    _state = _state.copyWith(
      status: MeasurementsStatus.loading,
      history: const [],
      page: 0,
      hasMore: false,
      appliedFrom: from,
      appliedTo: to,
      errorMessage: null,
      saveMessage: null,
      clearLatest: true,
    );
    notifyListeners();
    try {
      final results = await Future.wait<Object?>([
        repository.getLatestMeasurement(),
        repository.getMeasurementHistory(
          page: 0,
          size: pageSize,
          from: from,
          to: to,
        ),
      ]);
      if (!_isCurrent(generation)) {
        return;
      }
      final history = results[1] as List<BodyMeasurement>;
      _state = _state.copyWith(
        status: MeasurementsStatus.loaded,
        latest: results[0] as BodyMeasurement?,
        history: history,
        page: 0,
        hasMore: history.length == pageSize,
        hasLoadedData: true,
        errorMessage: null,
        saveMessage: successMessage,
      );
    } on Object catch (error) {
      if (!_isCurrent(generation)) {
        return;
      }
      _state = _state.copyWith(
        status: MeasurementsStatus.error,
        hasLoadedData: _state.hasLoadedData,
        errorMessage: measurementsErrorMessage(error),
        saveMessage: null,
      );
    }
    if (_isCurrent(generation)) {
      notifyListeners();
    }
  }

  bool _isCurrent(int generation) => generation == _generation;

  void _setError(String message) {
    _state = _state.copyWith(
      status: MeasurementsStatus.error,
      errorMessage: message,
      saveMessage: null,
    );
    notifyListeners();
  }
}

String measurementsErrorMessage(Object error, {bool saving = false}) {
  if (error is ApiTransportException) {
    return AppStrings.unableToReachService;
  }
  if (error is ApiHttpException) {
    if (error.statusCode >= 500) {
      return AppStrings.serviceUnavailable;
    }
    if (error.statusCode == 400) {
      return AppStrings.requestFailed;
    }
  }
  return saving
      ? AppStrings.measurementSaveFailed
      : AppStrings.measurementLoadFailed;
}
