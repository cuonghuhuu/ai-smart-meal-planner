package com.smartmealplanner.recommendation.application;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Produces the bounded 30-day recent-use signal consumed by Recipe Ranking V1. */
@Service
public class RecommendationHistoryQueryService {
    private static final int MAX_TRANSPORT_COUNT = 365;

    private final RecommendationHistoryRepository history;

    public RecommendationHistoryQueryService(RecommendationHistoryRepository history) {
        this.history = history;
    }

    @Transactional(readOnly = true)
    public List<RecentRecipeCount> recentCounts(
            Long userId, Collection<UUID> candidatePublicIds, LocalDateTime since) {
        if (userId == null || userId <= 0 || candidatePublicIds == null || since == null) {
            throw new RecipeRecommendationException(
                    RecipeRecommendationFailure.SNAPSHOT_INCONSISTENT);
        }
        if (candidatePublicIds.isEmpty()) {
            return List.of();
        }
        List<String> hexIds = candidatePublicIds.stream()
                .filter(java.util.Objects::nonNull)
                .map(RecommendationHistoryQueryService::hex)
                .distinct().toList();
        return history.countRecentRecipeUses(userId, since, hexIds).stream()
                .map(value -> new RecentRecipeCount(
                        parseHex(value.getRecipeHex()),
                        Math.min(MAX_TRANSPORT_COUNT,
                                Math.toIntExact(value.getRecommendationCount()))))
                .sorted(Comparator.comparing(value -> value.recipePublicId().toString()))
                .toList();
    }

    public record RecentRecipeCount(UUID recipePublicId, int count) {
        public RecentRecipeCount {
            if (recipePublicId == null || count < 1 || count > MAX_TRANSPORT_COUNT) {
                throw new IllegalArgumentException("Invalid recent Recipe count");
            }
        }
    }

    private static String hex(UUID id) {
        return id.toString().replace("-", "").toLowerCase(Locale.ROOT);
    }

    private static UUID parseHex(String value) {
        if (value == null || !value.matches("[0-9a-fA-F]{32}")) {
            throw new RecipeRecommendationException(
                    RecipeRecommendationFailure.SNAPSHOT_INCONSISTENT);
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        return UUID.fromString(normalized.substring(0, 8) + "-"
                + normalized.substring(8, 12) + "-"
                + normalized.substring(12, 16) + "-"
                + normalized.substring(16, 20) + "-"
                + normalized.substring(20));
    }
}
