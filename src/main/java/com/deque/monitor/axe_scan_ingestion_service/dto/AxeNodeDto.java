package com.deque.monitor.axe_scan_ingestion_service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AxeNodeDto(
        String impact,

        String html,

        List<String> target,

        String failureSummary
) {}