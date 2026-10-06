package com.smartmealplanner.mealplanning.persistence;

import com.smartmealplanner.recommendation.persistence.RecommendationRequest;
import com.smartmealplanner.recommendation.persistence.RecommendationRequestRepository;
import com.smartmealplanner.mealplanning.persistence.MealPlanPersistenceCommands.Begin;
import com.smartmealplanner.mealplanning.persistence.MealPlanPersistenceCommands.Started;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** TX1: commits the PENDING attempt before any caller invokes the AI service. */
@Service
public class MealPlanRequestWriter {
    private final RecommendationRequestRepository requests;

    public MealPlanRequestWriter(RecommendationRequestRepository requests) {
        this.requests = requests;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Started begin(Begin command) {
        if (command == null || command.userId() == null || command.userId() <= 0
                || command.requestPublicId() == null || command.algorithmVersion() == null
                || command.constraintsHash() == null
                || !command.constraintsHash().matches("[0-9a-fA-F]{64}")) {
            throw new MealPlanPersistenceException(
                    MealPlanPersistenceException.Reason.INVALID_COMMAND);
        }
        RecommendationRequest request = requests.saveAndFlush(
                RecommendationRequest.begin(command.userId(), RecommendationRequest.Kind.MEAL_PLAN,
                        command.requestPublicId(), command.algorithmVersion().name(),
                        command.constraintsHash()));
        return new Started(request.id(), request.publicId());
    }
}
