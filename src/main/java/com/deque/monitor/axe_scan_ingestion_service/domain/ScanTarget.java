package com.deque.monitor.axe_scan_ingestion_service.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "scan_targets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScanTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    private ScanBatch batch;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String targetUrl;

    @Column(nullable = false)
    private Instant scannedAt;

    @Column(nullable = false)
    @Builder.Default
    private int totalViolations = 0;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "target", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<RuleViolation> violations = new ArrayList<>();

    public void addViolation(RuleViolation violation) {
        violations.add(violation);
        violation.setTarget(this);
    }
}