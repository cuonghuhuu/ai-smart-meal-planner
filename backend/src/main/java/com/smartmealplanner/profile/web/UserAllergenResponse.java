package com.smartmealplanner.profile.web;

import com.smartmealplanner.profile.persistence.ReactionKind;

public record UserAllergenResponse(
        String allergen,
        String displayName,
        String description,
        ReactionKind reactionKind,
        String note) {
}
