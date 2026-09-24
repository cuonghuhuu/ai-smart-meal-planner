package com.smartmealplanner.mealplanning.application;

import java.time.Duration;
import java.util.UUID;

import com.smartmealplanner.mealplanning.contract.v1.MealPlanGenerationRequest;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanGenerationResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Gate-D orchestration only. It intentionally has no transaction or persistence. */
@Service
public class MealPlanGenerationIntegrationService {
    private static final Logger LOG = LoggerFactory.getLogger(
            MealPlanGenerationIntegrationService.class);
    private final MealPlanningSnapshotAssembler snapshots;
    private final MealPlanningAiClient ai;
    private final MealPlanningResponseValidator validator;

    public MealPlanGenerationIntegrationService(MealPlanningSnapshotAssembler snapshots,
            MealPlanningAiClient ai, MealPlanningResponseValidator validator) {
        this.snapshots = snapshots;
        this.ai = ai;
        this.validator = validator;
    }

    /** Caller must supply the authenticated principal's public UUID. */
    public MealPlanGenerationResult generate(UUID authenticatedUserPublicId,
            MealPlanGenerationCommand command) {
        return generate(authenticatedUserPublicId, UUID.randomUUID(), command);
    }

    /** A caller-provided UUID lets Gate E persist correlation before invocation. */
    public MealPlanGenerationResult generate(UUID authenticatedUserPublicId,
            UUID requestId, MealPlanGenerationCommand command) {
        requireNoTransaction();
        String previousRequestId = MDC.get("requestId");
        MDC.put("requestId", String.valueOf(requestId));
        try {
            MealPlanGenerationRequest request = snapshots.assemble(
                    authenticatedUserPublicId, requestId, command);
            requireNoTransaction();
            long started = System.nanoTime();
            try {
                MealPlanGenerationResponse response = ai.generate(request);
                validator.validate(request, response);
                Duration duration = Duration.ofNanos(System.nanoTime() - started);
                LOG.info("AI meal plan requestId={} contractVersion={} algorithmVersion={} "
                                + "aiDurationMs={} outcome={}", requestId,
                        request.contractVersion(), request.algorithmVersion(),
                        duration.toMillis(), response.status());
                return new MealPlanGenerationResult(request, response, duration);
            } catch (MealPlanningIntegrationException exception) {
                LOG.warn("AI meal plan requestId={} contractVersion={} algorithmVersion={} "
                                + "aiDurationMs={} failure={}", requestId,
                        request.contractVersion(), request.algorithmVersion(),
                        Duration.ofNanos(System.nanoTime() - started).toMillis(),
                        exception.failure());
                throw exception;
            }
        } finally {
            if (previousRequestId == null) { MDC.remove("requestId"); }
            else { MDC.put("requestId", previousRequestId); }
        }
    }

    private static void requireNoTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new MealPlanningIntegrationException(
                    MealPlanningIntegrationFailure.TRANSACTION_BOUNDARY_VIOLATION);
        }
    }
}
