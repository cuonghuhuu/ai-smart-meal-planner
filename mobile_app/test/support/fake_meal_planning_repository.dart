import 'package:smart_meal_planner/features/meal_planning/data/meal_plan_models.dart';
import 'package:smart_meal_planner/features/meal_planning/data/meal_planning_repository.dart';

const requestId = '11111111-1111-4111-8111-111111111111';
const planId = '22222222-2222-4222-8222-222222222222';
const recipeId = '33333333-3333-4333-8333-333333333333';

class FakeMealPlanningRepository implements MealPlanningRepository {
  Future<MealPlanGenerationResponse> Function(MealPlanGenerationRequest)?
  onGenerate;
  Future<PersistedMealPlan> Function(String)? onGetPlan;
  final requests = <MealPlanGenerationRequest>[];
  final readIds = <String>[];

  @override
  Future<MealPlanGenerationResponse> generate(
    MealPlanGenerationRequest request,
  ) async {
    requests.add(request);
    return onGenerate == null ? generated() : await onGenerate!(request);
  }

  @override
  Future<PersistedMealPlan> getPlan(String mealPlanPublicId) async {
    readIds.add(mealPlanPublicId);
    return onGetPlan == null
        ? persistedPlan()
        : await onGetPlan!(mealPlanPublicId);
  }
}

MealPlanGenerationRequest generationRequest() => MealPlanGenerationRequest(
  startDate: DateTime(2026, 10, 5),
  days: 2,
  requestedMealSlots: [MealSlotCode.breakfast, MealSlotCode.dinner],
  defaultServings: 2,
  maxMinutesPerMeal: 30,
);

MealPlanGenerationResponse generated({
  MealPlanGenerationStatus status = MealPlanGenerationStatus.succeeded,
}) => MealPlanGenerationResponse(
  requestPublicId: requestId,
  status: status,
  mealPlanPublicId: status == MealPlanGenerationStatus.infeasible
      ? null
      : planId,
);

PersistedMealPlan persistedPlan({
  MealPlanGenerationStatus status = MealPlanGenerationStatus.succeeded,
  String? explanation,
}) => PersistedMealPlan(
  mealPlanPublicId: planId,
  requestPublicId: requestId,
  status: status,
  startDate: DateTime(2026, 10, 5),
  endDate: DateTime(2026, 10, 6),
  defaultServings: 2,
  entries: [
    MealPlanEntry(
      planDate: DateTime(2026, 10, 5),
      mealSlotCode: MealSlotCode.breakfast,
      recipePublicId: recipeId,
      recipeTitle: 'Oatmeal with Banana',
      servings: 2,
    ),
  ],
  unfilledSlots: status == MealPlanGenerationStatus.degraded
      ? [
          MealPlanUnfilledSlot(
            planDate: DateTime(2026, 10, 6),
            mealSlotCode: MealSlotCode.dinner,
            reasonCode: UnfilledSlotReasonCode.noEligibleRecipe,
            explanation: explanation,
          ),
        ]
      : const [],
);
