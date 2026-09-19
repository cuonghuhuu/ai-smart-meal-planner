package com.smartmealplanner.profile.application;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.smartmealplanner.profile.persistence.Allergen;
import com.smartmealplanner.profile.persistence.AllergenRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Profile-owned read boundary for the shared allergen reference vocabulary. */
@Service
public class AllergenReferenceQueryService {
    private final AllergenRepository allergens;
    public AllergenReferenceQueryService(AllergenRepository allergens) { this.allergens = allergens; }
    @Transactional(readOnly = true)
    public List<AllergenReferenceSnapshot> resolveByInternalIdsInOrder(
            Collection<Long> ids) {

        Map<Long, AllergenReferenceSnapshot> snapshots = allergens.findAllById(ids)
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        Allergen::id,
                        AllergenReferenceQueryService::snapshot));

        return ids.stream()
                .map(snapshots::get)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /** Resolves ids to stable codes for batch safety-constraint evaluation. */
    @Transactional(readOnly = true)
    public Map<Long, String> resolveCodesByInternalIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> result = new LinkedHashMap<>();
        for (Allergen allergen : allergens.findAllById(ids)) {
            if (allergen == null || allergen.id() == null || allergen.code() == null) {
                throw new IllegalStateException("Allergen reference data is inconsistent");
            }
            result.put(allergen.id(), allergen.code());
        }
        return Map.copyOf(result);
    }
    private static AllergenReferenceSnapshot snapshot(Allergen value) {
        return new AllergenReferenceSnapshot(value.code(), value.displayName(), value.displayOrder());
    }
}
