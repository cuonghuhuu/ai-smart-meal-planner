package com.smartmealplanner.nutrition.application;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

import com.smartmealplanner.nutrition.persistence.MeasurementUnitType;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlanningUnitQueryServiceTest {
    private final MeasurementUnitReferenceQueryService references =
            mock(MeasurementUnitReferenceQueryService.class);
    private final PlanningUnitQueryService service = new PlanningUnitQueryService(references);

    @Test
    void transportsCanonicalBaseAndTwelveDecimalDerivedFactorsExactly() {
        var floz = unit(2L, "floz", MeasurementUnitType.VOLUME, 1L, "ml",
                "29.573529562500");
        var ml = unit(1L, "ml", MeasurementUnitType.VOLUME, null, null, null);
        var kj = unit(4L, "kj", MeasurementUnitType.ENERGY, 3L, "kcal",
                "0.239005736138");
        var kcal = unit(3L, "kcal", MeasurementUnitType.ENERGY, null, null, null);
        when(references.resolveByCodes(any())).thenReturn(
                Map.of("floz", floz, "kj", kj),
                Map.of("floz", floz, "ml", ml, "kj", kj, "kcal", kcal));
        var definitions = service.definitionsFor(Set.of("floz", "kj"));
        assertThat(definitions).extracting(PlanningUnitSnapshot::unitCode)
                .containsExactly("floz", "kcal", "kj", "ml");
        assertThat(definitions).anySatisfy(value -> {
            assertThat(value.unitCode()).isEqualTo("floz");
            assertThat(value.baseUnitCode()).isEqualTo("ml");
            assertThat(value.toBaseFactor()).isEqualByComparingTo("29.573529562500");
        });
        assertThat(definitions).anySatisfy(value -> {
            assertThat(value.unitCode()).isEqualTo("kj");
            assertThat(value.toBaseFactor()).isEqualByComparingTo("0.239005736138");
        });
        assertThat(definitions.stream().filter(value -> value.unitCode().equals("ml")
                || value.unitCode().equals("kcal")).toList())
                .allSatisfy(value -> {
                    assertThat(value.baseUnitCode()).isEqualTo(value.unitCode());
                    assertThat(value.toBaseFactor()).isEqualByComparingTo(BigDecimal.ONE);
                });
    }

    @Test
    void rejectsCrossDimensionBaseGraph() {
        var bad = unit(2L, "floz", MeasurementUnitType.VOLUME, 1L, "g", "29.573529562500");
        var base = unit(1L, "g", MeasurementUnitType.MASS, null, null, null);
        when(references.resolveByCodes(any())).thenReturn(Map.of("floz", bad),
                Map.of("floz", bad, "g", base));
        assertThatThrownBy(() -> service.definitionsFor(Set.of("floz")))
                .hasMessageContaining("Measurement unit graph is inconsistent");
    }

    @Test
    void mapsMassAndCountBaseRowsToSelfWithoutNormalizingCodes() {
        var g = unit(10L, "g", MeasurementUnitType.MASS, null, null, null);
        var piece = unit(11L, "piece", MeasurementUnitType.COUNT, null, null, null);
        when(references.resolveByCodes(any())).thenReturn(Map.of("g", g, "piece", piece));
        var definitions = service.definitionsFor(Set.of("g", "piece"));
        assertThat(definitions).extracting(PlanningUnitSnapshot::unitCode)
                .containsExactly("g", "piece");
        assertThat(definitions).allSatisfy(value -> {
            assertThat(value.baseUnitCode()).isEqualTo(value.unitCode());
            assertThat(value.toBaseFactor()).isEqualByComparingTo(BigDecimal.ONE);
        });
    }

    private static MeasurementUnitReferenceSnapshot unit(Long id, String code,
            MeasurementUnitType type, Long baseId, String baseCode, String factor) {
        return new MeasurementUnitReferenceSnapshot(id, code, code, type, baseId,
                baseCode, factor == null ? null : new BigDecimal(factor));
    }
}
