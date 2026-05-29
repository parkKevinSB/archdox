package com.archdox.cloud.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Duration;
import java.time.OffsetDateTime;

@Entity
@Table(
        name = "auth_login_guards",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_auth_login_guards_scope_key",
                columnNames = {"scope", "guard_key_hash"}))
public class AuthLoginGuard {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuthLoginGuardScope scope;

    @Column(name = "guard_key_hash", nullable = false)
    private String guardKeyHash;

    @Column(name = "display_key")
    private String displayKey;

    @Column(name = "failure_count", nullable = false)
    private int failureCount;

    @Column(name = "first_failed_at")
    private OffsetDateTime firstFailedAt;

    @Column(name = "last_failed_at")
    private OffsetDateTime lastFailedAt;

    @Column(name = "locked_until")
    private OffsetDateTime lockedUntil;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected AuthLoginGuard() {
    }

    public AuthLoginGuard(
            AuthLoginGuardScope scope,
            String guardKeyHash,
            String displayKey,
            OffsetDateTime now
    ) {
        this.scope = scope;
        this.guardKeyHash = guardKeyHash;
        this.displayKey = displayKey;
        this.failureCount = 0;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public boolean isLocked(OffsetDateTime now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public boolean recordFailure(
            OffsetDateTime now,
            int maxFailures,
            Duration failureWindow,
            Duration lockDuration
    ) {
        if (firstFailedAt == null || firstFailedAt.plus(failureWindow).isBefore(now)) {
            failureCount = 0;
            firstFailedAt = now;
        }
        failureCount += 1;
        lastFailedAt = now;
        updatedAt = now;
        if (failureCount >= maxFailures) {
            lockedUntil = now.plus(lockDuration);
            return true;
        }
        return false;
    }

    public void clearFailures(OffsetDateTime now) {
        failureCount = 0;
        firstFailedAt = null;
        lastFailedAt = null;
        lockedUntil = null;
        updatedAt = now;
    }

    public Long id() {
        return id;
    }

    public AuthLoginGuardScope scope() {
        return scope;
    }

    public String guardKeyHash() {
        return guardKeyHash;
    }

    public String displayKey() {
        return displayKey;
    }

    public int failureCount() {
        return failureCount;
    }

    public OffsetDateTime lockedUntil() {
        return lockedUntil;
    }
}
