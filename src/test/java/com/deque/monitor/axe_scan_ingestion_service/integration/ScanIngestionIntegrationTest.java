package com.deque.monitor.axe_scan_ingestion_service.integration;

import com.deque.monitor.axe_scan_ingestion_service.TestcontainersConfiguration;
import com.deque.monitor.axe_scan_ingestion_service.domain.BatchStatus;
import com.deque.monitor.axe_scan_ingestion_service.repository.RuleViolationRepository;
import com.deque.monitor.axe_scan_ingestion_service.repository.ScanBatchRepository;
import com.deque.monitor.axe_scan_ingestion_service.repository.ScanTargetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ScanIngestionIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ScanBatchRepository scanBatchRepository;

    @Autowired
    private ScanTargetRepository scanTargetRepository;

    @Autowired
    private RuleViolationRepository ruleViolationRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        scanBatchRepository.deleteAll();
    }

    @Test
    @DisplayName("E2E Ingestion: Valid payload runs Flyway migrations, persists graph, and guards idempotency")
    void shouldIngestAndPersistPayloadE2E() throws Exception {
        String idempotencyKey = "e2e-batch-" + UUID.randomUUID();

        String payload = """
                {
                  "idempotencyKey": "%s",
                  "initiatedBy": "integration-test-runner",
                  "targets": [
                    {
                      "url": "https://deque.com/axe",
                      "timestamp": "2026-11-11T12:00:00Z",
                      "violations": [
                        {
                          "id": "color-contrast",
                          "impact": "serious",
                          "description": "Ensures contrast meets WCAG thresholds",
                          "help": "Elements must meet minimum color contrast ratio",
                          "tags": ["wcag2aa", "wcag143"],
                          "nodes": [
                            {
                              "impact": "serious",
                              "html": "<button class='btn'>Click</button>",
                              "target": ["body", "button"],
                              "failureSummary": "Contrast ratio below 4.5:1"
                            }
                          ]
                        },
                        {
                          "id": "image-alt",
                          "impact": "critical",
                          "description": "Requires alternate text for images",
                          "help": "Images must have alternate text",
                          "tags": ["wcag111"],
                          "nodes": [
                            {
                              "impact": "critical",
                              "html": "<img src='logo.png'>",
                              "target": ["header", "img"],
                              "failureSummary": "Image missing alt attribute"
                            }
                          ]
                        }
                      ]
                    }
                  ]
                }
                """.formatted(idempotencyKey);

        // 1. Initial Ingestion Request -> 201 Created
        mockMvc.perform(post("/api/v1/scans/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idempotencyKey").value(idempotencyKey))
                .andExpect(jsonPath("$.status").value(BatchStatus.COMPLETED.name()))
                .andExpect(jsonPath("$.targetCount").value(1))
                .andExpect(jsonPath("$.totalViolations").value(2));

        // 2. Database Assertions: Verify records exist in real PostgreSQL tables
        var savedBatch = scanBatchRepository.findByIdempotencyKeyWithTargets(idempotencyKey);
        assertThat(savedBatch).isPresent();

        UUID batchId = savedBatch.get().getId();
        var persistedTargets = scanTargetRepository.findByBatchIdWithViolations(batchId);
        assertThat(persistedTargets).hasSize(1);

        var target = persistedTargets.getFirst();
        assertThat(target.getTargetUrl()).isEqualTo("https://deque.com/axe");
        assertThat(target.getViolations()).hasSize(2);
        assertThat(target.getViolations())
                .extracting("ruleId")
                .containsExactlyInAnyOrder("color-contrast", "image-alt");

        // 3. Idempotency Check: Re-posting payload returns existing batch
        mockMvc.perform(post("/api/v1/scans/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.batchId").value(batchId.toString()))
                .andExpect(jsonPath("$.idempotencyKey").value(idempotencyKey));

        // Confirm database record counts did not change
        assertThat(scanBatchRepository.count()).isEqualTo(1);
        assertThat(scanTargetRepository.count()).isEqualTo(1);
        assertThat(ruleViolationRepository.count()).isEqualTo(2);
    }
}