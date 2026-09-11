package com.smartmealplanner.auth.persistence;

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
@Table(name = "user_roles")
public class UserRole {

    @EmbeddedId
    private UserRoleId id = new UserRoleId();

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @MapsId("roleId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Column(
            name = "granted_at",
            nullable = false,
            insertable = false,
            updatable = false,
            columnDefinition = "DATETIME(6)")
    private LocalDateTime grantedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "granted_by")
    private UserAccount grantedBy;

    protected UserRole() {
    }

    public UserRole(UserAccount user, Role role, UserAccount grantedBy) {
        if (user == null) {
            throw new IllegalArgumentException("user is required");
        }

        if (role == null) {
            throw new IllegalArgumentException("role is required");
        }

        this.user = user;
        this.role = role;
        this.grantedBy = grantedBy;
    }

    public UserAccount user() {
        return user;
    }

    public Role role() {
        return role;
    }

    public LocalDateTime grantedAt() {
        return grantedAt;
    }

    public UserAccount grantedBy() {
        return grantedBy;
    }
}