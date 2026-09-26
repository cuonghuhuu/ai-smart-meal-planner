package com.smartmealplanner.nutrition.application;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.smartmealplanner.shared.application.ReferenceDataIntegrityException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Nutrition-owned conversion graph; copies catalog codes/factors without rounding. */
@Service
public class PlanningUnitQueryService {
    private final MeasurementUnitReferenceQueryService units;

    public PlanningUnitQueryService(MeasurementUnitReferenceQueryService units) {
        this.units = units;
    }

    @Transactional(readOnly = true)
    public List<PlanningUnitSnapshot> definitionsFor(Collection<String> referencedCodes) {
        if (referencedCodes == null || referencedCodes.isEmpty()) { return List.of(); }
        Set<String> codes = Set.copyOf(referencedCodes);
        Map<String, MeasurementUnitReferenceSnapshot> first = units.resolveByCodes(codes);
        if (first.size() != codes.size()) { throw inconsistent(); }
        Set<String> completeCodes = new HashSet<>(codes);
        for (MeasurementUnitReferenceSnapshot unit : first.values()) {
            if (unit.baseUnitCode() != null) { completeCodes.add(unit.baseUnitCode()); }
        }
        Map<String, MeasurementUnitReferenceSnapshot> complete = units.resolveByCodes(completeCodes);
        if (complete.size() != completeCodes.size()) { throw inconsistent(); }
        return complete.values().stream().map(unit -> snapshot(unit, complete))
                .sorted(Comparator.comparing(PlanningUnitSnapshot::unitCode)).toList();
    }

    private static PlanningUnitSnapshot snapshot(MeasurementUnitReferenceSnapshot unit,
            Map<String, MeasurementUnitReferenceSnapshot> all) {
        if (unit == null || unit.code() == null || unit.unitType() == null) {
            throw inconsistent();
        }
        if (unit.baseUnitCode() == null) {
            if (unit.factorToBaseUnit() != null) { throw inconsistent(); }
            return new PlanningUnitSnapshot(unit.code(), unit.unitType().name(),
                    unit.code(), BigDecimal.ONE);
        }
        MeasurementUnitReferenceSnapshot base = all.get(unit.baseUnitCode());
        if (base == null || base.unitType() != unit.unitType()
                || base.baseUnitCode() != null || base.factorToBaseUnit() != null
                || unit.factorToBaseUnit() == null
                || unit.factorToBaseUnit().signum() <= 0) {
            throw inconsistent();
        }
        return new PlanningUnitSnapshot(unit.code(), unit.unitType().name(),
                unit.baseUnitCode(), unit.factorToBaseUnit());
    }

    private static ReferenceDataIntegrityException inconsistent() {
        return new ReferenceDataIntegrityException("Measurement unit graph is inconsistent");
    }
}
