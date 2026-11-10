package com.deque.monitor.axe_scan_ingestion_service.controller;

import com.deque.monitor.axe_scan_ingestion_service.domain.BatchStatus;
import com.deque.monitor.axe_scan_ingestion_service.dto.AxeNodeDto;
import com.deque.monitor.axe_scan_ingestion_service.dto.AxeRuleDto;
import com.deque.monitor.axe_scan_ingestion_service.dto.AxeScanIngestionRequest;
import com.deque.monitor.axe_scan_ingestion_service.dto.AxeScanIngestionResponse;
import com.deque.monitor.axe_scan_ingestion_service.dto.AxeScanTargetDto;
import com.deque.monitor.axe_scan_ingestion_service.service.ScanIngestionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ScanIngestionControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Mock
    private ScanIngestionService scanIngestionService;

    @InjectMocks
    private ScanIngestionController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("POST /api/v1/scans/ingest - Returns 201 Created on valid scan payload")
    void shouldReturn201WhenPayloadIsValid() throws Exception {
        UUID expectedId = UUID.randomUUID();
        Instant now = Instant.now();

        AxeScanIngestionResponse mockResponse = new AxeScanIngestionResponse(
                expectedId,
                "batch-mock-001",
                BatchStatus.COMPLETED,
                1,
                1,
                now
        );

        when(scanIngestionService.ingestScanBatch(any(AxeScanIngestionRequest.class)))
                .thenReturn(mockResponse);

        AxeNodeDto node = new AxeNodeDto(
                "critical",
                "<img src='hero.png'>",
                List.of("header", "img"),
                "Element missing alt text"
        );

        AxeRuleDto rule = new AxeRuleDto(
                "image-alt",
                "critical",
                "Requires alternate text for images",
                "Images must have alternate text",
                List.of("wcag111"),
                List.of(node)
        );

        AxeScanTargetDto target = new AxeScanTargetDto(
                "https://deque.com",
                now,
                List.of(rule)
        );

        AxeScanIngestionRequest validRequest = new AxeScanIngestionRequest(
                "batch-mock-001",
                "github-actions",
                List.of(target)
        );

        mockMvc.perform(post("/api/v1/scans/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.batchId").value(expectedId.toString()))
                .andExpect(jsonPath("$.idempotencyKey").value("batch-mock-001"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.targetCount").value(1))
                .andExpect(jsonPath("$.totalViolations").value(1));
    }

    @Test
    @DisplayName("POST /api/v1/scans/ingest - Returns 400 Bad Request when validation constraints fail")
    void shouldReturn400WhenRequestHasValidationErrors() throws Exception {
        AxeScanIngestionRequest invalidRequest = new AxeScanIngestionRequest(
                "",
                "   ",
                Collections.emptyList()
        );

        mockMvc.perform(post("/api/v1/scans/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(scanIngestionService);
    }
}