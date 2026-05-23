package com.archdox.cloud.office.domain;

import com.archdox.cloud.account.domain.UserAccount;
import com.archdox.shared.MembershipRole;
import com.archdox.shared.MembershipStatus;
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
@Table(name = "office_memberships")
public class OfficeMembership {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "office_id", nullable = false)
    private Office office;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MembershipRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MembershipStatus status = MembershipStatus.ACTIVE;

    @Column(name = "joined_at", nullable = false)
    private OffsetDateTime joinedAt;

    protected OfficeMembership() {
    }

    public OfficeMembership(UserAccount user, Office office, MembershipRole role, OffsetDateTime now) {
        this.user = user;
        this.office = office;
        this.role = role;
        this.joinedAt = now;
    }

    public void changeRole(MembershipRole role) {
        this.role = role;
    }

    public void reactivate(MembershipRole role, OffsetDateTime now) {
        this.role = role;
        this.status = MembershipStatus.ACTIVE;
        this.joinedAt = now;
    }

    public void suspend() {
        this.status = MembershipStatus.SUSPENDED;
    }

    public Long id() {
        return id;
    }

    public Office office() {
        return office;
    }

    public UserAccount user() {
        return user;
    }

    public MembershipRole role() {
        return role;
    }

    public MembershipStatus status() {
        return status;
    }

    public OffsetDateTime joinedAt() {
        return joinedAt;
    }
}
