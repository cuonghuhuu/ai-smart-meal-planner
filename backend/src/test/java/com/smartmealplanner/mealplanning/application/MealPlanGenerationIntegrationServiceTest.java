package com.smartmealplanner.mealplanning.application;

import java.nio.file.Files;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.smartmealplanner.mealplanning.contract.v1.MealPlanGenerationRequest;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanGenerationResponse;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractJson;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.MealSlotCode;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MealPlanGenerationIntegrationServiceTest {
    private final MealPlanningSnapshotAssembler snapshots = mock(MealPlanningSnapshotAssembler.class);
    private final MealPlanningAiClient ai = mock(MealPlanningAiClient.class);
    private final MealPlanningResponseValidator validator = mock(MealPlanningResponseValidator.class);
    private final MealPlanGenerationIntegrationService service = new MealPlanGenerationIntegrationService(
            snapshots, ai, validator);

    @Test
    void callsAiOutsideTransactionAndReturnsOnlyValidatedInternalResult() throws Exception {
        MealPlanGenerationRequest request = MealPlanningContractJson.createDefault().readRequest(
                fixture("valid_request.json"));
        MealPlanGenerationResponse response = MealPlanningContractJson.createDefault().readResponse(
                fixture("valid_degraded_response.json"));
        when(snapshots.assemble(any(), any(), any())).thenReturn(request);
        when(ai.generate(request)).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return response;
        });
        MealPlanGenerationResult result = service.generate(UUID.randomUUID(), request.requestId(),
                new MealPlanGenerationCommand(request.planning().startDate(),
                        request.planning().days(), request.planning().requestedMealSlots(),
                        request.planning().defaultServings(),
                        request.planning().maxMinutesPerMeal()));
        assertThat(result.request()).isSameAs(request);
        assertThat(result.response()).isSameAs(response);
        assertThat(result.aiDuration()).isGreaterThanOrEqualTo(Duration.ZERO);
        verify(validator).validate(request, response);
    }

    @Test
    void rejectsAmbientTransactionBeforeAnySnapshotOrRemoteCall() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThatThrownBy(() -> service.generate(UUID.randomUUID(), UUID.randomUUID(),
                    new MealPlanGenerationCommand(LocalDate.of(2026, 10, 1), 1,
                            List.of(MealSlotCode.BREAKFAST), BigDecimal.ONE, null)))
                    .isInstanceOfSatisfying(MealPlanningIntegrationException.class,
                            error -> assertThat(error.failure()).isEqualTo(
                                    MealPlanningIntegrationFailure.TRANSACTION_BOUNDARY_VIOLATION));
            verifyNoInteractions(snapshots, ai, validator);
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    private static String fixture(String name) throws Exception {
        return Files.readString(Path.of("..", "contract_fixtures", "meal_planning", "v1", name));
    }
}
