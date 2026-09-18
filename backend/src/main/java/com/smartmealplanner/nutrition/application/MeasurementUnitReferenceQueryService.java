package com.smartmealplanner.nutrition.application;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.smartmealplanner.nutrition.persistence.MeasurementUnit;
import com.smartmealplanner.nutrition.persistence.MeasurementUnitRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Nutrition-owned read boundary for measurement units used by recipes. */
@Service
public class MeasurementUnitReferenceQueryService {

    private final MeasurementUnitRepository units;

    public MeasurementUnitReferenceQueryService(MeasurementUnitRepository units) {
        this.units = units;
    }

    @Transactional(readOnly = true)
    public Map<Long, MeasurementUnitReferenceSnapshot> resolveByInternalIds(
            Collection<Long> internalIds) {

        if (internalIds == null || internalIds.isEmpty()) {
            return Map.of();
        }

        List<MeasurementUnit> values = units.findAllWithBaseUnitByIdIn(internalIds);
        Map<Long, MeasurementUnitReferenceSnapshot> snapshots = new LinkedHashMap<>();
        for (MeasurementUnit unit : values) {
            if (unit == null
                    || unit.id() == null
                    || unit.code() == null
                    || unit.displayName() == null
                    || unit.unitType() == null
                    || (unit.baseUnit() != null
                    && (unit.baseUnit().id() == null
                    || unit.baseUnit().code() == null))) {

                throw new IllegalStateException(
                        "Measurement unit reference data is inconsistent");
            }

            snapshots.put(
                    unit.id(),
                    new MeasurementUnitReferenceSnapshot(
                            unit.id(),
                            unit.code(),
                            unit.displayName(),
                            unit.unitType(),
                            unit.baseUnit() == null ? null : unit.baseUnit().id(),
                            unit.baseUnit() == null ? null : unit.baseUnit().code(),
                            unit.factorToBaseUnit()));
        }
        return Map.copyOf(snapshots);
    }
}
