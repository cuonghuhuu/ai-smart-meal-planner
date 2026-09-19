import 'package:flutter/foundation.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/features/meal_plan/data/meal_plan_models.dart';
import 'package:smart_meal_planner/features/meal_plan/data/meal_plan_repository.dart';
import 'package:smart_meal_planner/l10n/app_strings.dart';

enum MealPlanListStatus { initial, loading, loaded, error }

final class MealPlanListState {
  const MealPlanListState({
    required this.status,
    required this.items,
    required this.errorMessage,
  });

  factory MealPlanListState.initial() => const MealPlanListState(
    status: MealPlanListStatus.initial,
    items: <MealPlanItem>[],
    errorMessage: null,
  );

  final MealPlanListStatus status;
  final List<MealPlanItem> items;
  final String? errorMessage;

  bool get isLoading => status == MealPlanListStatus.loading;
}

enum MealPlanDetailStatus { initial, loading, loaded, error }

final class MealPlanDetailState {
  const MealPlanDetailState({
    required this.status,
    required this.publicId,
    required this.item,
    required this.errorMessage,
  });

  factory MealPlanDetailState.initial() => const MealPlanDetailState(
    status: MealPlanDetailStatus.initial,
    publicId: null,
    item: null,
    errorMessage: null,
  );

  final MealPlanDetailStatus status;
  final String? publicId;
  final MealPlanDetail? item;
  final String? errorMessage;
}

final class MealPlanController extends ChangeNotifier {
  MealPlanController({required this.repository})
    : _state = MealPlanListState.initial(),
      _detailState = MealPlanDetailState.initial();

  final MealPlanRepository repository;
  MealPlanListState _state;
  MealPlanDetailState _detailState;
  bool _disposed = false;
  bool _generating = false;
  bool _accepting = false;
  String? _actionErrorMessage;
  int _generation = 0;
  int _detailGeneration = 0;

  MealPlanListState get state => _state;
  MealPlanDetailState get detailState => _detailState;
  bool get generating => _generating;
  bool get accepting => _accepting;
  String? get actionErrorMessage => _actionErrorMessage;

  Future<void> loadInitial() {
    if (_disposed || _state.status == MealPlanListStatus.loading) {
      return Future<void>.value();
    }
    return _loadList();
  }

  Future<void> reload() => _loadList();

  Future<MealPlanDetail?> generateDefault() async {
    if (_disposed || _generating) return null;
    final generation = ++_generation;
    _generating = true;
    _actionErrorMessage = null;
    _notify();
    try {
      final result = await repository.generate(
        MealPlanGenerationRequest(
          startDate: _today(),
          days: 3,
          mealSlotCodes: const ['BREAKFAST', 'LUNCH', 'DINNER'],
          defaultServings: 1,
        ),
      );
      if (!_isCurrent(generation)) return null;
      _detailState = MealPlanDetailState(
        status: MealPlanDetailStatus.loaded,
        publicId: result.plan.publicId,
        item: result.plan,
        errorMessage: null,
      );
      final generatedItem = MealPlanItem(
        publicId: result.plan.publicId,
        title: result.plan.title,
        startDate: result.plan.startDate,
        endDate: result.plan.endDate,
        dayCount: result.plan.dayCount,
        mealsPerDayTarget: result.plan.mealsPerDayTarget,
        defaultServings: result.plan.defaultServings,
        status: result.plan.status,
        createdAt: result.plan.createdAt,
      );
      _state = MealPlanListState(
        status: MealPlanListStatus.loaded,
        items: [
          generatedItem,
          for (final item in _state.items)
            if (item.publicId != generatedItem.publicId) item,
        ],
        errorMessage: null,
      );
      return result.plan;
    } on Object catch (error) {
      if (_isCurrent(generation)) {
        _actionErrorMessage = _mealPlanErrorMessage(error);
        _notify();
      }
      return null;
    } finally {
      if (!_disposed) {
        _generating = false;
        _notify();
      }
    }
  }

  Future<void> loadDetail(String publicId) async {
    final value = publicId.trim();
    if (_disposed) return;
    final generation = ++_detailGeneration;
    _detailState = MealPlanDetailState(
      status: MealPlanDetailStatus.loading,
      publicId: value,
      item: null,
      errorMessage: null,
    );
    _notify();
    try {
      final item = await repository.getMealPlan(value);
      if (!_isDetailCurrent(generation)) return;
      _detailState = MealPlanDetailState(
        status: MealPlanDetailStatus.loaded,
        publicId: value,
        item: item,
        errorMessage: null,
      );
    } on Object catch (error) {
      if (!_isDetailCurrent(generation)) return;
      _detailState = MealPlanDetailState(
        status: MealPlanDetailStatus.error,
        publicId: value,
        item: null,
        errorMessage: _mealPlanErrorMessage(error),
      );
    }
    if (_isDetailCurrent(generation)) _notify();
  }

  Future<void> reloadDetail() {
    final publicId = _detailState.publicId;
    return publicId == null ? Future<void>.value() : loadDetail(publicId);
  }

  Future<MealPlanDetail?> accept(String publicId) async {
    if (_disposed || _accepting) return null;
    final generation = _generation;
    _accepting = true;
    _actionErrorMessage = null;
    _notify();
    try {
      final item = await repository.acceptMealPlan(publicId);
      if (!_isCurrent(generation)) return null;
      _detailState = MealPlanDetailState(
        status: MealPlanDetailStatus.loaded,
        publicId: item.publicId,
        item: item,
        errorMessage: null,
      );
      _state = MealPlanListState(
        status: MealPlanListStatus.loaded,
        items: [
          MealPlanItem(
            publicId: item.publicId,
            title: item.title,
            startDate: item.startDate,
            endDate: item.endDate,
            dayCount: item.dayCount,
            mealsPerDayTarget: item.mealsPerDayTarget,
            defaultServings: item.defaultServings,
            status: item.status,
            createdAt: item.createdAt,
          ),
          for (final plan in _state.items)
            if (plan.publicId != item.publicId) plan,
        ],
        errorMessage: null,
      );
      return item;
    } on Object catch (error) {
      if (!_disposed) {
        _actionErrorMessage = _mealPlanErrorMessage(error);
        _notify();
      }
      return null;
    } finally {
      if (!_disposed) {
        _accepting = false;
        _notify();
      }
    }
  }

  void resetForSessionChange() {
    if (_disposed) return;
    _generation++;
    _detailGeneration++;
    _state = MealPlanListState.initial();
    _detailState = MealPlanDetailState.initial();
    _generating = false;
    _accepting = false;
    _actionErrorMessage = null;
    _notify();
  }

  Future<void> _loadList() async {
    if (_disposed) return;
    final generation = ++_generation;
    _state = MealPlanListState(
      status: MealPlanListStatus.loading,
      items: _state.items,
      errorMessage: null,
    );
    _notify();
    try {
      final items = await repository.getMealPlans();
      if (!_isCurrent(generation)) return;
      _state = MealPlanListState(
        status: MealPlanListStatus.loaded,
        items: items,
        errorMessage: null,
      );
    } on Object catch (error) {
      if (!_isCurrent(generation)) return;
      _state = MealPlanListState(
        status: MealPlanListStatus.error,
        items: _state.items,
        errorMessage: _mealPlanErrorMessage(error),
      );
    }
    if (_isCurrent(generation)) _notify();
  }

  static String _today() {
    final date = DateTime.now();
    final month = date.month.toString().padLeft(2, '0');
    final day = date.day.toString().padLeft(2, '0');
    return '${date.year}-$month-$day';
  }

  bool _isCurrent(int generation) =>
      !_disposed && generation == _generation;

  bool _isDetailCurrent(int generation) =>
      !_disposed && generation == _detailGeneration;

  void _notify() {
    if (!_disposed) notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    _generation++;
    _detailGeneration++;
    super.dispose();
  }
}

String _mealPlanErrorMessage(Object error) {
  if (error is ApiTransportException) {
    return AppStrings.unableToReachService;
  }
  if (error is ApiHttpException && error.statusCode >= 500) {
    return AppStrings.serviceUnavailable;
  }
  return AppStrings.mealPlanRequestFailed;
}
