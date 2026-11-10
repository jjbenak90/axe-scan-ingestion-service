package com.deque.monitor.axe_scan_ingestion_service.domain;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "rule_violations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RuleViolation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_id", nullable = false)
    private ScanTarget target;

    @Column(nullable = false, length = 64)
    private String ruleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ImpactLevel impactLevel;

    @Column(nullable = false, length = 32)
    private String wcagCriterion;

    @Column(columnDefinition = "TEXT")
    private String htmlSnippet;

    @Column(columnDefinition = "TEXT")
    private String cssSelector;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String failureSummary;
}