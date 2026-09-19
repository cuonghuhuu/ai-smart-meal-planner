package com.smartmealplanner.food;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Food-module boundary for read-only Ingredient identity resolution.
 * Recipe and Pantry must not depend on the Ingredient entity or repository
 * directly.
 */
@Service
public class IngredientReferenceQueryService {

    private final IngredientRepository ingredients;

    public IngredientReferenceQueryService(IngredientRepository ingredients) {
        this.ingredients = ingredients;
    }

    @Transactional(readOnly = true)
    public Map<Long, IngredientReferenceSnapshot> resolveByInternalIds(
            Collection<Long> internalIds) {

        if (internalIds == null || internalIds.isEmpty()) {
            return Map.of();
        }

        List<Ingredient> values = ingredients.findAllById(internalIds);
        Map<Long, IngredientReferenceSnapshot> snapshots = new LinkedHashMap<>();
        for (Ingredient ingredient : values) {
            if (ingredient == null
                    || ingredient.internalId() == null
                    || ingredient.publicId() == null
                    || ingredient.code() == null
                    || ingredient.displayName() == null) {

                throw new IllegalStateException(
                        "Ingredient reference data is inconsistent");
            }

            snapshots.put(
                    ingredient.internalId(),
                    new IngredientReferenceSnapshot(
                            ingredient.internalId(),
                            ingredient.publicId(),
                            ingredient.code(),
                            ingredient.displayName()));
        }
        return Map.copyOf(snapshots);
    }

    /** Resolves canonical Ingredient identities for an import document in one query. */
    @Transactional(readOnly = true)
    public Map<String, IngredientReferenceSnapshot> resolveByCodes(
            Collection<String> codes) {

        if (codes == null || codes.isEmpty()) {
            return Map.of();
        }

        List<Ingredient> values = ingredients.findAllByCodeIn(codes);
        Map<String, IngredientReferenceSnapshot> snapshots = new LinkedHashMap<>();
        for (Ingredient ingredient : values) {
            if (ingredient == null
                    || ingredient.internalId() == null
                    || ingredient.publicId() == null
                    || ingredient.code() == null
                    || ingredient.displayName() == null) {

                throw new IllegalStateException(
                        "Ingredient reference data is inconsistent");
            }

            snapshots.put(
                    ingredient.code(),
                    new IngredientReferenceSnapshot(
                            ingredient.internalId(),
                            ingredient.publicId(),
                            ingredient.code(),
                            ingredient.displayName()));
        }
        return Map.copyOf(snapshots);
    }

    /** Resolves active canonical Ingredient identities selected by public UUID. */
    @Transactional(readOnly = true)
    public Map<UUID, IngredientReferenceSnapshot> resolveActiveByPublicIds(
            Collection<UUID> publicIds) {

        if (publicIds == null || publicIds.isEmpty()) {
            return Map.of();
        }

        List<byte[]> binaryIds = publicIds.stream()
                .filter(java.util.Objects::nonNull)
                .map(CatalogIds::uuidToBytes)
                .toList();
        if (binaryIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, IngredientReferenceSnapshot> snapshots = new LinkedHashMap<>();
        for (Ingredient ingredient : ingredients.findActiveByPublicIdIn(binaryIds)) {
            IngredientReferenceSnapshot snapshot = snapshot(ingredient);
            snapshots.put(snapshot.publicId(), snapshot);
        }
        return Map.copyOf(snapshots);
    }

    /** Resolves an active Ingredient selected by its public UUID. */
    @Transactional(readOnly = true)
    public Optional<IngredientReferenceSnapshot> resolveActiveByPublicId(
            UUID publicId) {

        if (publicId == null) {
            return Optional.empty();
        }

        return ingredients.findActiveByPublicId(CatalogIds.uuidToBytes(publicId))
                .map(IngredientReferenceQueryService::snapshot);
    }

    /** Resolves a historical Ingredient reference without applying activity filtering. */
    @Transactional(readOnly = true)
    public Optional<IngredientReferenceSnapshot> resolveByPublicId(
            UUID publicId) {

        if (publicId == null) {
            return Optional.empty();
        }

        return ingredients.findByPublicId(CatalogIds.uuidToBytes(publicId))
                .map(IngredientReferenceQueryService::snapshot);
    }

    private static IngredientReferenceSnapshot snapshot(Ingredient ingredient) {
        if (ingredient == null
                || ingredient.internalId() == null
                || ingredient.publicId() == null
                || ingredient.code() == null
                || ingredient.displayName() == null) {

            throw new IllegalStateException(
                    "Ingredient reference data is inconsistent");
        }

        return new IngredientReferenceSnapshot(
                ingredient.internalId(),
                ingredient.publicId(),
                ingredient.code(),
                ingredient.displayName());
    }
}
