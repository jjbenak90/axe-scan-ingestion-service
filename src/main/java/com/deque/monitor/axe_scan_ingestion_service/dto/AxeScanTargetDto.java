package com.deque.monitor.axe_scan_ingestion_service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AxeScanTargetDto(
        @NotBlank(message = "url must not be blank")
        String url,

        @NotNull(message = "timestamp must not be null")
        Instant timestamp,

        List<@Valid AxeRuleDto> violations
) {}