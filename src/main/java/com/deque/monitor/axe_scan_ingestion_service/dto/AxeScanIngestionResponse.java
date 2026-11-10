package com.deque.monitor.axe_scan_ingestion_service.dto;

import com.deque.monitor.axe_scan_ingestion_service.domain.BatchStatus;

import java.time.Instant;
import java.util.UUID;

public record AxeScanIngestionResponse(
        UUID batchId,
        String idempotencyKey,
        BatchStatus status,
        int targetCount,
        int totalViolations,
        Instant createdAt
) {}