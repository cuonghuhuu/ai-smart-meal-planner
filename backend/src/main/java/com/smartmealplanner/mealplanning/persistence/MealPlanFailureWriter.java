package com.smartmealplanner.mealplanning.persistence;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Separate short transaction, including after a rolled-back terminal write. */
@Service
public class MealPlanFailureWriter {
    private final RecommendationRequestRepository requests;
    private final RecommendationResultRepository results;
    private final MealPlanRepository plans;

    public MealPlanFailureWriter(RecommendationRequestRepository requests,
            RecommendationResultRepository results, MealPlanRepository plans) {
        this.requests = requests;
        this.results = results;
        this.plans = plans;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long requestId, Integer durationMs, String failureCode) {
        if (requestId == null || requestId <= 0 || durationMs != null && durationMs < 0
                || failureCode == null || !failureCode.matches("[A-Z][A-Z0-9_]{0,254}")) {
            throw new MealPlanPersistenceException(
                    MealPlanPersistenceException.Reason.INVALID_COMMAND);
        }
        RecommendationRequest request = requests.findByIdForUpdate(requestId)
                .orElseThrow(() -> new MealPlanPersistenceException(
                        MealPlanPersistenceException.Reason.REQUEST_NOT_FOUND));
        if (request.requestKind() != RecommendationRequest.Kind.MEAL_PLAN
                || request.status() != RecommendationRequest.Status.PENDING) {
            throw new MealPlanPersistenceException(
                    MealPlanPersistenceException.Reason.INVALID_TRANSITION);
        }
        if (results.existsByRequestId(requestId) || plans.existsBySourceRequestId(requestId)) {
            throw new MealPlanPersistenceException(
                    MealPlanPersistenceException.Reason.EXISTING_GRAPH);
        }
        if (requests.completePending(requestId, RecommendationRequest.Status.FAILED.name(),
                durationMs, failureCode) != 1) {
            throw new MealPlanPersistenceException(
                    MealPlanPersistenceException.Reason.INVALID_TRANSITION);
        }
    }
}
