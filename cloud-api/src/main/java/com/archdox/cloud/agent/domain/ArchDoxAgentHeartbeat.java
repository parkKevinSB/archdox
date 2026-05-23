package com.archdox.cloud.agent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "archdox_agent_heartbeats")
public class ArchDoxAgentHeartbeat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_id", nullable = false)
    private ArchDoxAgent agent;

    private String version;

    @Column(name = "disk_free_bytes")
    private Long diskFreeBytes;

    @Column(name = "pending_jobs")
    private Integer pendingJobs;

    @Column(name = "recent_error_count")
    private Integer recentErrorCount;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    protected ArchDoxAgentHeartbeat() {
    }

    public ArchDoxAgentHeartbeat(
            ArchDoxAgent agent,
            String version,
            Long diskFreeBytes,
            Integer pendingJobs,
            Integer recentErrorCount,
            OffsetDateTime receivedAt
    ) {
        this.agent = agent;
        this.version = version;
        this.diskFreeBytes = diskFreeBytes;
        this.pendingJobs = pendingJobs;
        this.recentErrorCount = recentErrorCount;
        this.receivedAt = receivedAt;
    }
}
