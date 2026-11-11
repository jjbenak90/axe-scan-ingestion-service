package com.deque.monitor.axe_scan_ingestion_service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record AxeScanIngestionRequest(
        @NotBlank(message = "idempotencyKey must not be blank")
        String idempotencyKey,

        @NotBlank(message = "initiatedBy must not be blank")
        String initiatedBy,

        @NotEmpty(message = "targets list must contain at least one scan target")

        List<@Valid AxeScanTargetDto> targets
) {}