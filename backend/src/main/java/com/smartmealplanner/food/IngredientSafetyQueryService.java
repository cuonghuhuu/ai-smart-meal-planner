package com.smartmealplanner.food;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.smartmealplanner.profile.application.AllergenReferenceQueryService;
import com.smartmealplanner.profile.application.AllergenReferenceSnapshot;
import com.smartmealplanner.shared.application.ReferenceDataIntegrityException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Food-owned batch query; never manufactures FREE_FROM for missing evidence. */
@Service
public class IngredientSafetyQueryService {
    private final IngredientRepository ingredients;
    private final IngredientAllergenRepository allergenFacts;
    private final AllergenReferenceQueryService allergenReferences;

    public IngredientSafetyQueryService(IngredientRepository ingredients,
            IngredientAllergenRepository allergenFacts,
            AllergenReferenceQueryService allergenReferences) {
        this.ingredients = ingredients;
        this.allergenFacts = allergenFacts;
        this.allergenReferences = allergenReferences;
    }

    @Transactional(readOnly = true)
    public List<IngredientSafetySnapshot> forIngredients(Collection<UUID> publicIds) {
        if (publicIds == null || publicIds.isEmpty()) { return List.of(); }
        Set<UUID> unique = Set.copyOf(publicIds);
        List<Ingredient> found = ingredients.findAllByPublicIdIn(
                unique.stream().map(CatalogIds::uuidToBytes).toList());
        if (found.size() != unique.size()) { throw inconsistent(); }
        Map<Long, Ingredient> byId = found.stream().collect(Collectors.toMap(
                Ingredient::internalId, ingredient -> ingredient));
        List<IngredientAllergen> facts = allergenFacts.findByIngredientIds(byId.keySet());
        List<AllergenReferenceSnapshot> references = allergenReferences.resolveByInternalIdsInOrder(
                facts.stream().map(IngredientAllergen::allergenId).toList());
        if (references.size() != facts.size()) { throw inconsistent(); }
        Map<Long, List<IngredientSafetySnapshot.AllergenEvidence>> grouped = new HashMap<>();
        for (int index = 0; index < facts.size(); index++) {
            IngredientAllergen fact = facts.get(index);
            if (fact == null || fact.ingredientId() == null
                    || !byId.containsKey(fact.ingredientId())
                    || fact.presence() == null || references.get(index).code() == null) {
                throw inconsistent();
            }
            grouped.computeIfAbsent(fact.ingredientId(), ignored -> new ArrayList<>())
                    .add(new IngredientSafetySnapshot.AllergenEvidence(
                            references.get(index).code(), fact.presence()));
        }
        return found.stream().sorted(Comparator.comparing(item -> item.publicId().toString()))
                .map(item -> new IngredientSafetySnapshot(item.publicId(),
                        grouped.getOrDefault(item.internalId(), List.of())))
                .toList();
    }

    private static ReferenceDataIntegrityException inconsistent() {
        return new ReferenceDataIntegrityException("Ingredient safety references are inconsistent");
    }
}
