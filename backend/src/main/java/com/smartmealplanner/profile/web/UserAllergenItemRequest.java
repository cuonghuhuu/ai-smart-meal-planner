package com.smartmealplanner.profile.web;

import com.smartmealplanner.profile.persistence.ReactionKind;

public record UserAllergenItemRequest(
        String allergen,
        ReactionKind reactionKind,
        String note) {
}
