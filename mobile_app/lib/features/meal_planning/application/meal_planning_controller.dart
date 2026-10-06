import 'package:flutter/foundation.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/features/meal_planning/data/meal_plan_models.dart';
import 'package:smart_meal_planner/features/meal_planning/data/meal_planning_repository.dart';
import 'package:smart_meal_planner/features/measurements/data/measurement_models.dart';
import 'package:smart_meal_planner/features/meal_planning/application/meal_planning_prerequisites.dart';
import 'package:smart_meal_planner/l10n/app_strings.dart';

enum MealPlanningStatus {
  idle,
  checkingPrerequisites,
  missingPrerequisite,
  generating,
  loadingPlan,
  loaded,
  infeasible,
  error,
}

enum ShoppingListLoadStatus { idle, loading, loaded, error }

final class MealPlanningState {
  const MealPlanningState({
    required this.status,
    this.plan,
    this.pendingMealPlanPublicId,
    this.errorMessage,
    this.prerequisiteIssue,
    this.shoppingListStatus = ShoppingListLoadStatus.idle,
    this.shoppingList,
    this.shoppingListErrorMessage,
  });

  final MealPlanningStatus status;
  final PersistedMealPlan? plan;
  final String? pendingMealPlanPublicId;
  final String? errorMessage;
  final MealPlanningPrerequisiteIssue? prerequisiteIssue;
  final ShoppingListLoadStatus shoppingListStatus;
  final MealPlanShoppingList? shoppingList;
  final String? shoppingListErrorMessage;

  bool get isBusy =>
      status == MealPlanningStatus.checkingPrerequisites ||
      status == MealPlanningStatus.generating ||
      status == MealPlanningStatus.loadingPlan;

  bool get canRetryLoad =>
      status == MealPlanningStatus.error && pendingMealPlanPublicId != null;

  bool get isShoppingListBusy =>
      shoppingListStatus == ShoppingListLoadStatus.loading;

  bool get canLoadShoppingList =>
      status == MealPlanningStatus.loaded &&
      plan != null &&
      shoppingListStatus == ShoppingListLoadStatus.idle;

  bool get canRetryShoppingList =>
      status == MealPlanningStatus.loaded &&
      plan != null &&
      shoppingListStatus == ShoppingListLoadStatus.error;
}

final class MealPlanningController extends ChangeNotifier {
  MealPlanningController({required this.repository, this.prerequisites});

  final MealPlanningRepository repository;
  final MealPlanningPrerequisites? prerequisites;
  MealPlanningState _state = const MealPlanningState(
    status: MealPlanningStatus.idle,
  );
  int _sessionRevision = 0;
  int _shoppingListRevision = 0;

  MealPlanningState get state => _state;

  Future<void> generate(MealPlanGenerationRequest request) async {
    if (_state.isBusy || _state.pendingMealPlanPublicId != null) return;
    final revision = _sessionRevision;
    if (prerequisites != null && !await _checkPrerequisites(revision)) return;
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

  Future<void> checkPrerequisites() async {
    if (_state.isBusy ||
        _state.status == MealPlanningStatus.loaded ||
        _state.pendingMealPlanPublicId != null) {
      return;
    }
    await _checkPrerequisites(_sessionRevision);
  }

  Future<bool> _checkPrerequisites(int revision) async {
    final checker = prerequisites;
    if (checker == null) return true;
    _setState(
      const MealPlanningState(status: MealPlanningStatus.checkingPrerequisites),
    );
    try {
      final issue = await checker.check();
      if (revision != _sessionRevision) return false;
      if (issue != null) {
        _setState(
          MealPlanningState(
            status: MealPlanningStatus.missingPrerequisite,
            prerequisiteIssue: issue,
          ),
        );
        return false;
      }
      _setState(const MealPlanningState(status: MealPlanningStatus.idle));
      return true;
    } on Object catch (error) {
      if (revision != _sessionRevision) return false;
      _setState(
        MealPlanningState(
          status: MealPlanningStatus.error,
          errorMessage: mealPlanningErrorMessage(error),
        ),
      );
      return false;
    }
  }

  Future<void> createCalculatedTarget() async {
    if (_state.isBusy ||
        _state.prerequisiteIssue !=
            MealPlanningPrerequisiteIssue.nutritionTarget ||
        prerequisites == null) {
      return;
    }
    final revision = _sessionRevision;
    _setState(
      const MealPlanningState(status: MealPlanningStatus.checkingPrerequisites),
    );
    try {
      await prerequisites!.createCalculatedTarget(backendUtcToday());
      if (revision == _sessionRevision) await _checkPrerequisites(revision);
    } on ApiHttpException catch (error) {
      if (revision != _sessionRevision) return;
      final issue = switch (error.problem?.code) {
        'PROFILE_NOT_FOUND' ||
        'MISSING_CALCULATION_INPUT' => MealPlanningPrerequisiteIssue.profile,
        'MEASUREMENT_NOT_FOUND' => MealPlanningPrerequisiteIssue.measurement,
        _ => null,
      };
      _setState(
        MealPlanningState(
          status: issue == null
              ? MealPlanningStatus.error
              : MealPlanningStatus.missingPrerequisite,
          prerequisiteIssue: issue,
          errorMessage: issue == null ? mealPlanningErrorMessage(error) : null,
        ),
      );
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

  Future<void> loadShoppingList() async {
    if (!_state.canLoadShoppingList) return;
    await _loadShoppingList(_state.plan!, _sessionRevision);
  }

  Future<void> retryShoppingList() async {
    if (!_state.canRetryShoppingList) return;
    await _loadShoppingList(_state.plan!, _sessionRevision);
  }

  /// Drops only the derived shopping-list projection after inventory changes.
  void invalidateShoppingList() {
    _shoppingListRevision++;
    if (_state.shoppingListStatus == ShoppingListLoadStatus.idle &&
        _state.shoppingList == null &&
        _state.shoppingListErrorMessage == null) {
      return;
    }
    _setState(
      MealPlanningState(
        status: _state.status,
        plan: _state.plan,
        pendingMealPlanPublicId: _state.pendingMealPlanPublicId,
        errorMessage: _state.errorMessage,
      ),
    );
  }

  Future<void> _loadShoppingList(PersistedMealPlan plan, int revision) async {
    final shoppingListRevision = _shoppingListRevision;
    _setState(
      MealPlanningState(
        status: MealPlanningStatus.loaded,
        plan: plan,
        shoppingListStatus: ShoppingListLoadStatus.loading,
      ),
    );
    try {
      final shoppingList = await repository.getShoppingList(
        plan.mealPlanPublicId,
      );
      if (!_isCurrentShoppingListLoad(plan, revision, shoppingListRevision)) {
        return;
      }
      if (shoppingList.mealPlanPublicId != plan.mealPlanPublicId) {
        throw const ApiResponseFormatException();
      }
      _setState(
        MealPlanningState(
          status: MealPlanningStatus.loaded,
          plan: plan,
          shoppingListStatus: ShoppingListLoadStatus.loaded,
          shoppingList: shoppingList,
        ),
      );
    } on Object catch (error) {
      if (!_isCurrentShoppingListLoad(plan, revision, shoppingListRevision)) {
        return;
      }
      _setState(
        MealPlanningState(
          status: MealPlanningStatus.loaded,
          plan: plan,
          shoppingListStatus: ShoppingListLoadStatus.error,
          shoppingListErrorMessage: mealPlanningErrorMessage(error),
        ),
      );
    }
  }

  bool _isCurrentShoppingListLoad(
    PersistedMealPlan plan,
    int revision,
    int shoppingListRevision,
  ) =>
      revision == _sessionRevision &&
      shoppingListRevision == _shoppingListRevision &&
      _state.status == MealPlanningStatus.loaded &&
      identical(_state.plan, plan) &&
      _state.isShoppingListBusy;

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
    _shoppingListRevision++;
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
    if (error.problem?.code == 'AI_SERVICE_UNAVAILABLE' ||
        error.problem?.code == 'AI_SERVICE_TIMEOUT') {
      return 'Dịch vụ lập thực đơn chưa sẵn sàng. Hãy thử lại sau.';
    }
    if (error.problem?.code == 'NO_CURRENT_TARGET') {
      return 'Chưa có mục tiêu dinh dưỡng. Hãy tạo mục tiêu trước khi lập thực đơn.';
    }
    if (error.statusCode >= 500) return AppStrings.serviceUnavailable;
    if (error.statusCode == 400 || error.statusCode == 422) {
      return AppStrings.requestFailed;
    }
  }
  return AppStrings.mealPlanRequestFailed;
}
