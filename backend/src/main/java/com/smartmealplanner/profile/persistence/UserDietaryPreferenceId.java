package com.smartmealplanner.profile.persistence;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class UserDietaryPreferenceId
        implements Serializable {

    @Column(
            name = "user_id",
            nullable = false)
    private Long userId;

    @Column(
            name = "dietary_preference_id",
            nullable = false)
    private Long dietaryPreferenceId;

    public UserDietaryPreferenceId() {
    }

    public UserDietaryPreferenceId(
            Long userId,
            Long dietaryPreferenceId) {

        this.userId =
                userId;

        this.dietaryPreferenceId =
                dietaryPreferenceId;
    }

    public Long userId() {
        return userId;
    }

    public Long dietaryPreferenceId() {
        return dietaryPreferenceId;
    }

    @Override
    public boolean equals(
            Object o) {

        if (this == o) {
            return true;
        }

        if (o == null
                || getClass() != o.getClass()) {

            return false;
        }

        UserDietaryPreferenceId that =
                (UserDietaryPreferenceId) o;

        return Objects.equals(userId, that.userId)
                && Objects.equals(dietaryPreferenceId, that.dietaryPreferenceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                userId,
                dietaryPreferenceId);
    }
}
