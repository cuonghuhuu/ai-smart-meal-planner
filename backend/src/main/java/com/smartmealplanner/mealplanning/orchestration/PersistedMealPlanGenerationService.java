package com.smartmealplanner.mealplanning.orchestration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;

import com.smartmealplanner.auth.application.CurrentUserService;
import com.smartmealplanner.mealplanning.application.MealPlanGenerationCommand;
import com.smartmealplanner.mealplanning.application.MealPlanGenerationIntegrationService;
import com.smartmealplanner.mealplanning.application.MealPlanGenerationResult;
import com.smartmealplanner.mealplanning.application.MealPlanningIntegrationException;
import com.smartmealplanner.mealplanning.application.MealPlanningIntegrationFailure;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanGenerationResponse;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.AlgorithmVersion;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.GenerationStatus;
import com.smartmealplanner.mealplanning.persistence.MealPlanFailureWriter;
import com.smartmealplanner.mealplanning.persistence.MealPlanPersistenceCommands.Begin;
import com.smartmealplanner.mealplanning.persistence.MealPlanPersistenceCommands.Entry;
import com.smartmealplanner.mealplanning.persistence.MealPlanPersistenceCommands.Gap;
import com.smartmealplanner.mealplanning.persistence.MealPlanPersistenceCommands.GeneratedPlan;
import com.smartmealplanner.mealplanning.persistence.MealPlanPersistenceCommands.Score;
import com.smartmealplanner.mealplanning.persistence.MealPlanPersistenceCommands.Started;
import com.smartmealplanner.mealplanning.persistence.MealPlanPersistenceException;
import com.smartmealplanner.mealplanning.persistence.MealPlanRecipeIdResolver;
import com.smartmealplanner.mealplanning.persistence.MealPlanRequestWriter;
import com.smartmealplanner.mealplanning.persistence.MealPlanTerminalWriter;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Connects Gate D to the short E2b writers; this method has no transaction. */
@Service
public class PersistedMealPlanGenerationService {
    private final CurrentUserService users;
    private final MealPlanRequestWriter requests;
    private final MealPlanGenerationIntegrationService gateD;
    private final MealPlanRecipeIdResolver recipes;
    private final MealPlanTerminalWriter terminals;
    private final MealPlanFailureWriter failures;

    public PersistedMealPlanGenerationService(CurrentUserService users,
            MealPlanRequestWriter requests, MealPlanGenerationIntegrationService gateD,
            MealPlanRecipeIdResolver recipes, MealPlanTerminalWriter terminals,
            MealPlanFailureWriter failures) {
        this.users = users;
        this.requests = requests;
        this.gateD = gateD;
        this.recipes = recipes;
        this.terminals = terminals;
        this.failures = failures;
    }

    public Completed generate(UUID authenticatedUserPublicId, MealPlanGenerationCommand command) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new MealPlanningIntegrationException(
                    MealPlanningIntegrationFailure.TRANSACTION_BOUNDARY_VIOLATION);
        }
        if (command == null) {
            throw new MealPlanningIntegrationException(
                    MealPlanningIntegrationFailure.INVALID_GENERATION_INPUT);
        }
        // Authentication lookup is a separate short read before TX1; failure creates no request.
        Long userId = users.getIdentity(authenticatedUserPublicId).internalId();
        UUID publicId = UUID.randomUUID();
        Started started = requests.begin(new Begin(userId, publicId,
                AlgorithmVersion.HEURISTIC_MEAL_PLAN_V1, commandHash(command)));
        try {
            // Gate D assembles one snapshot, invokes Python, and strictly validates the response.
            MealPlanGenerationResult result = gateD.generate(authenticatedUserPublicId,
                    publicId, command);
            Integer durationMs = durationMs(result.aiDuration());
            if (result.response().status() == GenerationStatus.INFEASIBLE) {
                terminals.markInfeasible(started.requestId(), durationMs);
                return new Completed(publicId, GenerationStatus.INFEASIBLE, null);
            }
            Long planId = terminals.persistGenerated(toCommand(started.requestId(), result,
                    durationMs));
            return new Completed(publicId, result.response().status(), planId);
        } catch (RuntimeException failure) {
            // A failed TX2 has rolled back before control returns here. FAILED uses another bean
            // and its own REQUIRES_NEW transaction, including after HTTP or validation failure.
            try {
                failures.markFailed(started.requestId(), null, failureCode(failure));
            } catch (RuntimeException failedWrite) {
                failure.addSuppressed(failedWrite);
            }
            throw failure;
        }
    }

    private GeneratedPlan toCommand(Long requestId, MealPlanGenerationResult result,
            Integer durationMs) {
        var planning = result.request().planning();
        MealPlanGenerationResponse response = result.response();
        return new GeneratedPlan(requestId, response.status(), planning.startDate(),
                planning.startDate().plusDays(planning.days() - 1L),
                planning.defaultServings(), durationMs, planning.requestedMealSlots(),
                response.entries().stream().map(entry -> new Entry(entry.planDate(),
                        entry.mealSlotCode(), (short) 1,
                        recipes.publishedRecipeId(entry.recipePublicId()), entry.servings(),
                        entry.totalScore(), entry.explanation(),
                        entry.scoreComponents().stream().map(component -> new Score(
                                component.componentCode(), component.value(), component.weight()))
                                .toList())).toList(),
                response.unfilledSlots().stream().map(gap -> new Gap(gap.planDate(),
                        gap.mealSlotCode(), gap.reasonCode(), gap.explanation())).toList());
    }

    private static Integer durationMs(Duration duration) {
        long millis = duration.toMillis();
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, millis));
    }

    private static String failureCode(RuntimeException failure) {
        if (failure instanceof MealPlanningIntegrationException integration) {
            return integration.failure().name();
        }
        if (failure instanceof DataAccessException
                || failure instanceof MealPlanPersistenceException) {
            return "PERSISTENCE_WRITE_FAILED";
        }
        return "APPLICATION_FAILURE";
    }

    // TX1 precedes the snapshot, so this hashes the caller's generation constraints only.
    private static String commandHash(MealPlanGenerationCommand command) {
        String canonical = String.join("|", String.valueOf(command.startDate()),
                String.valueOf(command.days()), String.valueOf(command.requestedMealSlots()),
                String.valueOf(command.defaultServings()),
                String.valueOf(command.maxMinutesPerMeal()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public record Completed(UUID requestPublicId, GenerationStatus status, Long mealPlanId) { }
}
