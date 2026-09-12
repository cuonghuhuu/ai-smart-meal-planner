package com.smartmealplanner.auth.persistence;

public enum SessionRevocationReason {
    USER_LOGOUT,
    ROTATED,
    PASSWORD_CHANGE,
    ADMIN_REVOKED,
    SUSPECTED_REUSE,
    ACCOUNT_CLOSED
}