package com.deque.monitor.axe_scan_ingestion_service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AxeRuleDto(
        @NotBlank
        String id,

        String impact,

        String description,

        String help,

        List<String> tags,

        List<AxeNodeDto> nodes
) {}