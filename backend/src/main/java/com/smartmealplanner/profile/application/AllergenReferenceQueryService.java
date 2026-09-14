package com.smartmealplanner.profile.application;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

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
    public Map<Long, AllergenReferenceSnapshot> findByInternalIds(Collection<Long> ids) {
        return allergens.findAllById(ids).stream().map(AllergenReferenceQueryService::snapshot)
                .collect(java.util.stream.Collectors.toMap(AllergenReferenceSnapshot::internalId, Function.identity()));
    }
    private static AllergenReferenceSnapshot snapshot(Allergen value) {
        return new AllergenReferenceSnapshot(value.id(), value.code(), value.displayName(), value.displayOrder());
    }
}
