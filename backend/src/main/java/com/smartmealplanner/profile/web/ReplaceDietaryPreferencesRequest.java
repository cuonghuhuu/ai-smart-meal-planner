package com.smartmealplanner.profile.web;

import java.util.List;

public record ReplaceDietaryPreferencesRequest(
        List<String> preferences) {
}
