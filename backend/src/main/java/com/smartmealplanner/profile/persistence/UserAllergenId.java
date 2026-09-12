package com.smartmealplanner.profile.persistence;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class UserAllergenId
        implements Serializable {

    @Column(
            name = "user_id",
            nullable = false)
    private Long userId;

    @Column(
            name = "allergen_id",
            nullable = false)
    private Long allergenId;

    public UserAllergenId() {
    }

    public UserAllergenId(
            Long userId,
            Long allergenId) {

        this.userId =
                userId;

        this.allergenId =
                allergenId;
    }

    public Long userId() {
        return userId;
    }

    public Long allergenId() {
        return allergenId;
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

        UserAllergenId that =
                (UserAllergenId) o;

        return Objects.equals(userId, that.userId)
                && Objects.equals(allergenId, that.allergenId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                userId,
                allergenId);
    }
}
