package com.smartmealplanner.profile.persistence;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_dietary_preferences")
public class UserDietaryPreference {

    @EmbeddedId
    private UserDietaryPreferenceId id =
            new UserDietaryPreferenceId();

    @Column(
            name = "user_id",
            insertable = false,
            updatable = false)
    private Long userId;

    @MapsId("dietaryPreferenceId")
    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false)
    @JoinColumn(
            name = "dietary_preference_id",
            nullable = false)
    private DietaryPreference preference;

    @Column(
            name = "created_at",
            insertable = false,
            updatable = false,
            columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    protected UserDietaryPreference() {
    }

    public UserDietaryPreference(
            Long userId,
            DietaryPreference preference) {

        if (userId == null) {
            throw new IllegalArgumentException(
                    "userId is required");
        }

        if (preference == null) {
            throw new IllegalArgumentException(
                    "preference is required");
        }

        this.id = new UserDietaryPreferenceId(
                userId,
                preference.id());

        this.userId =
                userId;

        this.preference =
                preference;
    }

    public UserDietaryPreferenceId id() {
        return id;
    }

    public Long userId() {
        return userId;
    }

    public DietaryPreference preference() {
        return preference;
    }

    public LocalDateTime createdAt() {
        return createdAt;
    }
}
