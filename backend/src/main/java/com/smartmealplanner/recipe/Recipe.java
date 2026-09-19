package com.smartmealplanner.recipe;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Module-private mapping and invariant holder for the recipes table. */
@Entity
@Table(name = "recipes")
class Recipe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true,
            updatable = false, columnDefinition = "BINARY(16)")
    private byte[] publicId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, unique = true, length = 220)
    private String slug;

    @Column(length = 500)
    private String summary;

    @Column(nullable = false)
    private Short servings;

    @Column(name = "prep_minutes")
    private Short prepMinutes;

    @Column(name = "cook_minutes")
    private Short cookMinutes;

    @Column(name = "total_minutes", insertable = false, updatable = false)
    private Short totalMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecipeDifficulty difficulty;

    @Column(name = "instructions_note", length = 1000)
    private String instructionsNote;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RecipeSource source;

    @Column(name = "source_reference", length = 255)
    private String sourceReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecipeStatus status;

    @Column(name = "published_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime publishedAt;

    @Column(name = "archived_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime archivedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", insertable = false, updatable = false,
            columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false,
            columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    protected Recipe() {
    }

    Recipe(
            String title,
            String slug,
            Integer servings,
            Integer prepMinutes,
            Integer cookMinutes,
            RecipeDifficulty difficulty,
            String instructionsNote,
            String imageUrl,
            RecipeSource source,
            String sourceReference,
            RecipeStatus status,
            LocalDateTime publishedAt,
            LocalDateTime archivedAt) {

        this(
                UUID.randomUUID(),
                title,
                slug,
                servings,
                prepMinutes,
                cookMinutes,
                difficulty,
                instructionsNote,
                imageUrl,
                null,
                source,
                sourceReference,
                status,
                publishedAt,
                archivedAt);
    }

    Recipe(
            UUID publicId,
            String title,
            String slug,
            Integer servings,
            Integer prepMinutes,
            Integer cookMinutes,
            RecipeDifficulty difficulty,
            String instructionsNote,
            String imageUrl,
            Long createdByUserId,
            RecipeSource source,
            String sourceReference,
            RecipeStatus status,
            LocalDateTime publishedAt,
            LocalDateTime archivedAt) {

        if (publicId == null) {
            throw new IllegalArgumentException("publicId is required");
        }
        this.publicId = RecipeIds.uuidToBytes(publicId);
        this.title = requiredText(title, 200, "title");
        this.slug = requiredText(slug, 220, "slug");
        this.summary = null;
        this.servings = requireRange(servings, 1, 100, "servings");
        this.prepMinutes = optionalMinutes(prepMinutes, "prepMinutes");
        this.cookMinutes = optionalMinutes(cookMinutes, "cookMinutes");
        this.difficulty = required(difficulty, "difficulty");
        this.instructionsNote = optionalText(instructionsNote, 1000, "instructionsNote");
        this.imageUrl = optionalText(imageUrl, 500, "imageUrl");
        this.createdByUserId = createdByUserId;
        this.source = required(source, "source");
        this.sourceReference = optionalText(sourceReference, 255, "sourceReference");
        this.status = required(status, "status");
        validateLifecycle(status, publishedAt, archivedAt);
        this.publishedAt = publishedAt;
        this.archivedAt = archivedAt;
    }

    Long internalId() { return id; }

    UUID publicId() { return RecipeIds.bytesToUuid(publicId); }

    String title() { return title; }

    String slug() { return slug; }

    String summary() { return summary; }

    Short servings() { return servings; }

    Short prepMinutes() { return prepMinutes; }

    Short cookMinutes() { return cookMinutes; }

    Short totalMinutes() { return totalMinutes; }

    RecipeDifficulty difficulty() { return difficulty; }

    String instructionsNote() { return instructionsNote; }

    String imageUrl() { return imageUrl; }

    Long createdByUserId() { return createdByUserId; }

    RecipeSource source() { return source; }

    String sourceReference() { return sourceReference; }

    RecipeStatus status() { return status; }

    LocalDateTime publishedAt() { return publishedAt; }

    LocalDateTime archivedAt() { return archivedAt; }

    Long version() { return version; }

    LocalDateTime createdAt() { return createdAt; }

    LocalDateTime updatedAt() { return updatedAt; }

    void setSummary(String summary) {
        this.summary = optionalText(summary, 500, "summary");
    }

    /** Applies fields owned by the project-curated offline Recipe dataset. */
    boolean applyImportedDefinition(
            String title,
            String slug,
            String summary,
            Integer servings,
            Integer prepMinutes,
            Integer cookMinutes,
            RecipeDifficulty difficulty,
            String instructionsNote,
            String imageUrl,
            RecipeSource source,
            String sourceReference,
            LocalDateTime publishedAt) {

        String validatedTitle = requiredText(title, 200, "title");
        String validatedSlug = requiredText(slug, 220, "slug");
        String validatedSummary = optionalText(summary, 500, "summary");
        Short validatedServings = requireRange(servings, 1, 100, "servings");
        Short validatedPrepMinutes = optionalMinutes(prepMinutes, "prepMinutes");
        Short validatedCookMinutes = optionalMinutes(cookMinutes, "cookMinutes");
        RecipeDifficulty validatedDifficulty = required(difficulty, "difficulty");
        String validatedInstructions = optionalText(
                instructionsNote, 1000, "instructionsNote");
        String validatedImageUrl = optionalText(imageUrl, 500, "imageUrl");
        RecipeSource validatedSource = required(source, "source");
        String validatedSourceReference = optionalText(
                sourceReference, 255, "sourceReference");
        validateLifecycle(RecipeStatus.PUBLISHED, publishedAt, null);

        boolean changed = !Objects.equals(this.title, validatedTitle)
                || !Objects.equals(this.slug, validatedSlug)
                || !Objects.equals(this.summary, validatedSummary)
                || !Objects.equals(this.servings, validatedServings)
                || !Objects.equals(this.prepMinutes, validatedPrepMinutes)
                || !Objects.equals(this.cookMinutes, validatedCookMinutes)
                || !Objects.equals(this.difficulty, validatedDifficulty)
                || !Objects.equals(this.instructionsNote, validatedInstructions)
                || !Objects.equals(this.imageUrl, validatedImageUrl)
                || !Objects.equals(this.source, validatedSource)
                || !Objects.equals(this.sourceReference, validatedSourceReference)
                || !Objects.equals(this.status, RecipeStatus.PUBLISHED)
                || !Objects.equals(this.publishedAt, publishedAt)
                || this.archivedAt != null;

        this.title = validatedTitle;
        this.slug = validatedSlug;
        this.summary = validatedSummary;
        this.servings = validatedServings;
        this.prepMinutes = validatedPrepMinutes;
        this.cookMinutes = validatedCookMinutes;
        this.difficulty = validatedDifficulty;
        this.instructionsNote = validatedInstructions;
        this.imageUrl = validatedImageUrl;
        this.source = validatedSource;
        this.sourceReference = validatedSourceReference;
        this.status = RecipeStatus.PUBLISHED;
        this.publishedAt = publishedAt;
        this.archivedAt = null;
        return changed;
    }

    static void validateLifecycle(
            RecipeStatus status,
            LocalDateTime publishedAt,
            LocalDateTime archivedAt) {

        required(status, "status");
        boolean published = publishedAt != null;
        boolean archived = archivedAt != null;
        if (status == RecipeStatus.DRAFT && (published || archived)
                || status == RecipeStatus.PUBLISHED && (!published || archived)
                || status == RecipeStatus.ARCHIVED && (!published || !archived)) {

            throw new IllegalArgumentException("Invalid recipe lifecycle");
        }
    }

    static void validateIngredientLines(Collection<RecipeIngredient> lines) {
        if (lines == null) {
            throw new IllegalArgumentException("lines are required");
        }
        Set<Short> lineNumbers = new HashSet<>();
        Set<Long> ingredientIds = new HashSet<>();
        for (RecipeIngredient line : lines) {
            if (line == null
                    || line.lineNumber() == null
                    || line.lineNumber() < 1
                    || line.ingredientId() == null
                    || !lineNumbers.add(line.lineNumber())
                    || !ingredientIds.add(line.ingredientId())) {
                throw new IllegalArgumentException(
                        "Recipe ingredient lines are not unique and valid");
            }
            RecipeIngredient.validateQuantityUnit(
                    line.quantity(), line.unitId());
        }
    }

    private static Short optionalMinutes(Integer value, String field) {
        if (value != null && (value < 0 || value > 10080)) {
            throw new IllegalArgumentException(field + " is out of range");
        }
        return RecipeSmallInt.toShort(value, field);
    }

    private static Short requireRange(Integer value, int minimum, int maximum,
            String field) {
        if (value == null || value < minimum || value > maximum) {
            throw new IllegalArgumentException(field + " is out of range");
        }
        return RecipeSmallInt.toShort(value, field);
    }

    private static String requiredText(String value, int maximum, String field) {
        if (value == null || value.isBlank() || value.length() > maximum) {
            throw new IllegalArgumentException("Invalid " + field);
        }
        return value;
    }

    private static String optionalText(String value, int maximum, String field) {
        if (value != null && value.length() > maximum) {
            throw new IllegalArgumentException("Invalid " + field);
        }
        return value;
    }

    private static <T> T required(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
