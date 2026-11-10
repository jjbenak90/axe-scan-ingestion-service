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
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AxeScanPayloadMapper {

    private static final Pattern WCAG_TAG_PATTERN = Pattern.compile("^wcag(\\d)(\\d)(\\d+)$");

    public ScanBatch toEntity(AxeScanIngestionRequest request) {
        ScanBatch batch = ScanBatch.builder()
                .idempotencyKey(request.idempotencyKey())
                .initiatedBy(request.initiatedBy())
                .status(BatchStatus.PENDING)
                .build();

        if (request.targets() != null) {
            for (AxeScanTargetDto targetDto : request.targets()) {
                ScanTarget target = toTargetEntity(targetDto);
                batch.addTarget(target);
            }
        }

        return batch;
    }

    private ScanTarget toTargetEntity(AxeScanTargetDto dto) {
        ScanTarget target = ScanTarget.builder()
                .targetUrl(dto.url())
                .scannedAt(dto.timestamp())
                .build();

        int totalViolationsCount = 0;

        if (dto.violations() != null) {
            for (AxeRuleDto rule : dto.violations()) {
                String wcagCriterion = extractWcagCriterion(rule.tags());
                ImpactLevel defaultRuleImpact = mapImpact(rule.impact());

                List<AxeNodeDto> nodes = rule.nodes() != null ? rule.nodes() : Collections.emptyList();
                for (AxeNodeDto node : nodes) {
                    ImpactLevel nodeImpact = node.impact() != null ? mapImpact(node.impact()) : defaultRuleImpact;

                    String cssSelector = (node.target() != null && !node.target().isEmpty())
                            ? String.join(" > ", node.target())
                            : null;

                    RuleViolation violation = RuleViolation.builder()
                            .ruleId(rule.id())
                            .impactLevel(nodeImpact)
                            .wcagCriterion(wcagCriterion)
                            .htmlSnippet(node.html())
                            .cssSelector(cssSelector)
                            .failureSummary(node.failureSummary() != null ? node.failureSummary() : rule.help())
                            .build();

                    target.addViolation(violation);
                    totalViolationsCount++;
                }
            }
        }

        target.setTotalViolations(totalViolationsCount);
        return target;
    }

    private ImpactLevel mapImpact(String impact) {
        if (impact == null) {
            return ImpactLevel.MODERATE;
        }
        return switch (impact.trim().toLowerCase()) {
            case "critical" -> ImpactLevel.CRITICAL;
            case "serious" -> ImpactLevel.SERIOUS;
            case "minor" -> ImpactLevel.MINOR;
            default -> ImpactLevel.MODERATE;
        };
    }

    private String extractWcagCriterion(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return "unknown";
        }

        for (String tag : tags) {
            Matcher matcher = WCAG_TAG_PATTERN.matcher(tag.trim().toLowerCase());
            if (matcher.matches()) {
                // Translates axe tag 'wcag143' -> '1.4.3'
                return matcher.group(1) + "." + matcher.group(2) + "." + matcher.group(3);
            }
        }

        return tags.stream()
                .filter(t -> t.startsWith("wcag"))
                .findFirst()
                .orElse("other");
    }
}