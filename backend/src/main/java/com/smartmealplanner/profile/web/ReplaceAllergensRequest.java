package com.smartmealplanner.profile.web;

import java.util.List;

public record ReplaceAllergensRequest(
        List<UserAllergenItemRequest> allergens) {
}
