package com.archdox.cloud.office.domain;

import com.archdox.cloud.account.domain.UserAccount;
import com.archdox.shared.MembershipRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "office_invitations")
public class OfficeInvitation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "office_id", nullable = false)
    private Office office;

    @Column(nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MembershipRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OfficeInvitationStatus status = OfficeInvitationStatus.PENDING;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "token_preview", nullable = false)
    private String tokenPreview;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invited_by", nullable = false)
    private UserAccount invitedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accepted_by")
    private UserAccount acceptedBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "accepted_at")
    private OffsetDateTime acceptedAt;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected OfficeInvitation() {
    }

    public OfficeInvitation(
            Office office,
            String email,
            MembershipRole role,
            String tokenHash,
            String tokenPreview,
            UserAccount invitedBy,
            OffsetDateTime now,
            OffsetDateTime expiresAt
    ) {
        this.office = office;
        this.email = email;
        this.role = role;
        this.tokenHash = tokenHash;
        this.tokenPreview = tokenPreview;
        this.invitedBy = invitedBy;
        this.createdAt = now;
        this.expiresAt = expiresAt;
        this.updatedAt = now;
    }

    public boolean isExpired(OffsetDateTime now) {
        return status == OfficeInvitationStatus.PENDING && !expiresAt.isAfter(now);
    }

    public OfficeInvitationStatus effectiveStatus(OffsetDateTime now) {
        return isExpired(now) ? OfficeInvitationStatus.EXPIRED : status;
    }

    public void accept(UserAccount user, OffsetDateTime now) {
        this.status = OfficeInvitationStatus.ACCEPTED;
        this.acceptedBy = user;
        this.acceptedAt = now;
        this.updatedAt = now;
    }

    public void cancel(OffsetDateTime now) {
        this.status = OfficeInvitationStatus.CANCELLED;
        this.cancelledAt = now;
        this.updatedAt = now;
    }

    public void expire(OffsetDateTime now) {
        this.status = OfficeInvitationStatus.EXPIRED;
        this.updatedAt = now;
    }

    public Long id() {
        return id;
    }

    public Office office() {
        return office;
    }

    public String email() {
        return email;
    }

    public MembershipRole role() {
        return role;
    }

    public OfficeInvitationStatus status() {
        return status;
    }

    public String tokenPreview() {
        return tokenPreview;
    }

    public UserAccount invitedBy() {
        return invitedBy;
    }

    public UserAccount acceptedBy() {
        return acceptedBy;
    }

    public OffsetDateTime createdAt() {
        return createdAt;
    }

    public OffsetDateTime expiresAt() {
        return expiresAt;
    }

    public OffsetDateTime acceptedAt() {
        return acceptedAt;
    }

    public OffsetDateTime cancelledAt() {
        return cancelledAt;
    }

    public OffsetDateTime updatedAt() {
        return updatedAt;
    }
}
