package com.smartmealplanner.profile.persistence;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_allergens")
public class UserAllergen {

    @EmbeddedId
    private UserAllergenId id =
            new UserAllergenId();

    @Column(
            name = "user_id",
            insertable = false,
            updatable = false)
    private Long userId;

    @MapsId("allergenId")
    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false)
    @JoinColumn(
            name = "allergen_id",
            nullable = false)
    private Allergen allergen;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "reaction_kind",
            nullable = false,
            length = 20)
    private ReactionKind reactionKind;

    @Column(length = 255)
    private String note;

    @Column(
            name = "created_at",
            insertable = false,
            updatable = false,
            columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    protected UserAllergen() {
    }

    public UserAllergen(
            Long userId,
            Allergen allergen,
            ReactionKind reactionKind,
            String note) {

        if (userId == null) {
            throw new IllegalArgumentException(
                    "userId is required");
        }

        if (allergen == null) {
            throw new IllegalArgumentException(
                    "allergen is required");
        }

        this.id = new UserAllergenId(
                userId,
                allergen.id());

        this.userId =
                userId;

        this.allergen =
                allergen;

        this.reactionKind =
                reactionKind == null
                        ? ReactionKind.UNSPECIFIED
                        : reactionKind;

        this.note =
                note;
    }

    public UserAllergenId id() {
        return id;
    }

    public Long userId() {
        return userId;
    }

    public Allergen allergen() {
        return allergen;
    }

    public ReactionKind reactionKind() {
        return reactionKind;
    }

    public String note() {
        return note;
    }

    public LocalDateTime createdAt() {
        return createdAt;
    }

    public void update(
            ReactionKind reactionKind,
            String note) {

        this.reactionKind =
                reactionKind == null
                        ? ReactionKind.UNSPECIFIED
                        : reactionKind;

        this.note =
                note;
    }
}
