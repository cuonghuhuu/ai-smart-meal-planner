package com.smartmealplanner.food;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.smartmealplanner.auth.application.CurrentUserIdentity;
import com.smartmealplanner.auth.application.CurrentUserService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Current-user preference workflow. Ownership is always resolved from authentication. */
@Service
public class UserDislikedIngredientService {
    private final CurrentUserService currentUserService;
    private final IngredientRepository ingredients;
    private final UserDislikedIngredientRepository preferences;

    UserDislikedIngredientService(CurrentUserService currentUserService, IngredientRepository ingredients,
            UserDislikedIngredientRepository preferences) {
        this.currentUserService = currentUserService; this.ingredients = ingredients; this.preferences = preferences;
    }

    @Transactional(readOnly = true)
    public List<DislikedIngredientView> getPreferences(UUID authenticatedPublicId) {
        CurrentUserIdentity identity = currentUserService.getIdentity(authenticatedPublicId);
        return preferences.findByUserIdWithIngredient(identity.internalId()).stream().map(UserDislikedIngredientService::view).toList();
    }

    @Transactional
    public List<DislikedIngredientView> replacePreferences(UUID authenticatedPublicId, List<DislikedIngredientDraft> drafts) {
        CurrentUserIdentity identity = currentUserService.getIdentity(authenticatedPublicId);
        if (drafts == null) throw new FoodCatalogException(FoodCatalogFailure.INVALID_REQUEST);
        Set<UUID> publicIds = new HashSet<>();
        for (DislikedIngredientDraft draft : drafts) {
            if (draft == null || draft.ingredientPublicId() == null || draft.strength() == null
                    || (draft.note() != null && draft.note().length() > 255)
                    || !publicIds.add(draft.ingredientPublicId())) {
                throw new FoodCatalogException(FoodCatalogFailure.INVALID_REQUEST);
            }
        }
        List<byte[]> ids = publicIds.stream().map(CatalogIds::uuidToBytes).toList();
        Map<UUID, Ingredient> resolved = new HashMap<>();
        for (Ingredient ingredient : ingredients.findActiveByPublicIdIn(ids)) resolved.put(ingredient.publicId(), ingredient);
        if (resolved.size() != publicIds.size()) throw new FoodCatalogException(FoodCatalogFailure.INACTIVE_INGREDIENT);
        List<UserDislikedIngredient> replacement = new ArrayList<>();
        for (DislikedIngredientDraft draft : drafts) replacement.add(new UserDislikedIngredient(identity.internalId(),
                resolved.get(draft.ingredientPublicId()), draft.strength(), draft.note()));
        preferences.deleteByUserId(identity.internalId());
        preferences.flush();
        preferences.saveAllAndFlush(replacement);
        return preferences.findByUserIdWithIngredient(identity.internalId()).stream().map(UserDislikedIngredientService::view).toList();
    }

    private static DislikedIngredientView view(UserDislikedIngredient preference) {
        Ingredient ingredient = preference.ingredient();
        return new DislikedIngredientView(ingredient.publicId(), ingredient.code(), ingredient.displayName(),
                FoodCatalogService.category(ingredient.category()), preference.strength(), preference.note());
    }
}
