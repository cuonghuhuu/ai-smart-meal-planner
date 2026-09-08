package com.smartmealplanner.mealplanning;

import java.time.LocalDate;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.smartmealplanner.shared.web.InvalidRequestException;
import static org.assertj.core.api.Assertions.*;

class MealPlanDateValidatorTest {
    private final MealPlanDateValidator validator = new MealPlanDateValidator();
    private final LocalDate start = LocalDate.of(2026, 9, 1);
    private final LocalDate end = LocalDate.of(2026, 9, 7);

    @ParameterizedTest
    @ValueSource(strings = {"2026-09-01", "2026-09-04", "2026-09-07"})
    void acceptsInclusiveWindow(String date) {
        assertThatCode(() -> validator.validate(start, end, LocalDate.parse(date))).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-08-31", "2026-09-08"})
    void rejectsOutsideWindow(String date) {
        assertThatThrownBy(() -> validator.validate(start, end, LocalDate.parse(date)))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void rejectsMissingAndInvalidParentDates() {
        assertThatThrownBy(() -> validator.validate(null, end, start)).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> validator.validate(start, null, start)).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> validator.validate(start, end, null)).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> validator.validate(end, start, start)).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> validator.validate(start, start.plusDays(31), start)).isInstanceOf(InvalidRequestException.class);
        assertThatCode(() -> validator.validate(start, start, start)).doesNotThrowAnyException();
    }

    @Test
    void jakartaValidationRejectsAbsentDate() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var structural = factory.getValidator();
            assertThat(structural.validate(new PlanEntryDateRequest(null))).hasSize(1);
            assertThat(structural.validate(new PlanEntryDateRequest(start))).isEmpty();
        }
    }
}
