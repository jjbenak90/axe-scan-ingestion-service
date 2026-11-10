package com.deque.monitor.axe_scan_ingestion_service.mapper;

import com.deque.monitor.axe_scan_ingestion_service.domain.BatchStatus;
import com.deque.monitor.axe_scan_ingestion_service.domain.ImpactLevel;
import com.deque.monitor.axe_scan_ingestion_service.domain.RuleViolation;
import com.deque.monitor.axe_scan_ingestion_service.domain.ScanBatch;
import com.deque.monitor.axe_scan_ingestion_service.domain.ScanTarget;
import com.deque.monitor.axe_scan_ingestion_service.dto.AxeNodeDto;
import com.deque.monitor.axe_scan_ingestion_service.dto.AxeRuleDto;
import com.deque.monitor.axe_scan_ingestion_service.dto.AxeScanIngestionRequest;
import com.deque.monitor.axe_scan_ingestion_service.dto.AxeScanTargetDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AxeScanPayloadMapperTest {

    private AxeScanPayloadMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new AxeScanPayloadMapper();
    }

    @Test
    @DisplayName("Should map full Axe scan request into ScanBatch entity graph")
    void shouldMapRequestToEntityGraph() {
        AxeNodeDto node1 = new AxeNodeDto(
                "serious",
                "<button class='btn'>Action</button>",
                List.of("body", "div.container", "button.btn"),
                "Fix contrast ratio"
        );

        AxeRuleDto rule1 = new AxeRuleDto(
                "color-contrast",
                "serious",
                "Ensures the contrast between foreground and background colors meets WCAG 2 AA minimum thresholds",
                "Elements must meet minimum color contrast ratio thresholds",
                List.of("cat.color", "wcag2aa", "wcag143"),
                List.of(node1)
        );

        AxeScanTargetDto targetDto = new AxeScanTargetDto(
                "https://deque.com/axe",
                Instant.now(),
                List.of(rule1)
        );

        AxeScanIngestionRequest request = new AxeScanIngestionRequest(
                "batch-test-123",
                "playwright-runner",
                List.of(targetDto)
        );

        ScanBatch batch = mapper.toEntity(request);

        assertThat(batch.getIdempotencyKey()).isEqualTo("batch-test-123");
        assertThat(batch.getInitiatedBy()).isEqualTo("playwright-runner");
        assertThat(batch.getStatus()).isEqualTo(BatchStatus.PENDING);
        assertThat(batch.getTargets()).hasSize(1);

        ScanTarget target = batch.getTargets().getFirst();
        assertThat(target.getBatch()).isEqualTo(batch);
        assertThat(target.getTargetUrl()).isEqualTo("https://deque.com/axe");
        assertThat(target.getTotalViolations()).isEqualTo(1);
        assertThat(target.getViolations()).hasSize(1);

        RuleViolation violation = target.getViolations().getFirst();
        assertThat(violation.getTarget()).isEqualTo(target);
        assertThat(violation.getRuleId()).isEqualTo("color-contrast");
        assertThat(violation.getImpactLevel()).isEqualTo(ImpactLevel.SERIOUS);
        assertThat(violation.getWcagCriterion()).isEqualTo("1.4.3");
        assertThat(violation.getCssSelector()).isEqualTo("body > div.container > button.btn");
        assertThat(violation.getFailureSummary()).isEqualTo("Fix contrast ratio");
    }
}