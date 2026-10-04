import 'package:flutter/foundation.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/features/meal_planning/data/meal_plan_models.dart';
import 'package:smart_meal_planner/features/meal_planning/data/meal_planning_repository.dart';
import 'package:smart_meal_planner/l10n/app_strings.dart';

enum MealPlanningStatus {
  idle,
  generating,
  loadingPlan,
  loaded,
  infeasible,
  error,
}

final class MealPlanningState {
  const MealPlanningState({
    required this.status,
    this.plan,
    this.pendingMealPlanPublicId,
    this.errorMessage,
  });

  final MealPlanningStatus status;
  final PersistedMealPlan? plan;
  final String? pendingMealPlanPublicId;
  final String? errorMessage;

  bool get isBusy =>
      status == MealPlanningStatus.generating ||
      status == MealPlanningStatus.loadingPlan;

  bool get canRetryLoad =>
      status == MealPlanningStatus.error && pendingMealPlanPublicId != null;
}

final class MealPlanningController extends ChangeNotifier {
  MealPlanningController({required this.repository});

  final MealPlanningRepository repository;
  MealPlanningState _state = const MealPlanningState(
    status: MealPlanningStatus.idle,
  );
  int _sessionRevision = 0;

  MealPlanningState get state => _state;

  Future<void> generate(MealPlanGenerationRequest request) async {
    if (_state.isBusy || _state.pendingMealPlanPublicId != null) return;
    final revision = _sessionRevision;
    _setState(const MealPlanningState(status: MealPlanningStatus.generating));
    try {
      final generated = await repository.generate(request);
      if (revision != _sessionRevision) return;
      if (generated.status == MealPlanGenerationStatus.infeasible) {
        _setState(
          const MealPlanningState(status: MealPlanningStatus.infeasible),
        );
        return;
      }
      await _loadPlan(generated.mealPlanPublicId!, revision);
    } on Object catch (error) {
      if (revision != _sessionRevision) return;
      _setState(
        MealPlanningState(
          status: MealPlanningStatus.error,
          errorMessage: mealPlanningErrorMessage(error),
        ),
      );
    }
  }

  Future<void> retryLoad() async {
    if (!_state.canRetryLoad || _state.isBusy) return;
    await _loadPlan(_state.pendingMealPlanPublicId!, _sessionRevision);
  }

  Future<void> _loadPlan(String publicId, int revision) async {
    _setState(
      MealPlanningState(
        status: MealPlanningStatus.loadingPlan,
        pendingMealPlanPublicId: publicId,
      ),
    );
    try {
      final plan = await repository.getPlan(publicId);
      if (revision != _sessionRevision) return;
      _setState(
        MealPlanningState(status: MealPlanningStatus.loaded, plan: plan),
      );
    } on Object catch (error) {
      if (revision != _sessionRevision) return;
      _setState(
        MealPlanningState(
          status: MealPlanningStatus.error,
          pendingMealPlanPublicId: publicId,
          errorMessage: mealPlanningErrorMessage(error),
        ),
      );
    }
  }

  void resetForSessionChange() {
    _sessionRevision++;
    _setState(const MealPlanningState(status: MealPlanningStatus.idle));
  }

  void _setState(MealPlanningState next) {
    _state = next;
    notifyListeners();
  }
}

String mealPlanningErrorMessage(Object error) {
  if (error is ApiTransportException) return AppStrings.unableToReachService;
  if (error is ApiHttpException) {
    if (error.statusCode >= 500) return AppStrings.serviceUnavailable;
    if (error.statusCode == 400 || error.statusCode == 422) {
      return AppStrings.requestFailed;
    }
  }
  return AppStrings.mealPlanRequestFailed;
}
