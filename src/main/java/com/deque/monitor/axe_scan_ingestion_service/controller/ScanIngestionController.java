package com.deque.monitor.axe_scan_ingestion_service.controller;

import com.deque.monitor.axe_scan_ingestion_service.dto.AxeScanIngestionRequest;
import com.deque.monitor.axe_scan_ingestion_service.dto.AxeScanIngestionResponse;
import com.deque.monitor.axe_scan_ingestion_service.service.ScanIngestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/scans")
@RequiredArgsConstructor
public class ScanIngestionController {

    private final ScanIngestionService scanIngestionService;

    @PostMapping("/ingest")
    public ResponseEntity<AxeScanIngestionResponse> ingestScanBatch(
            @Valid @RequestBody AxeScanIngestionRequest request
    ) {
        AxeScanIngestionResponse response = scanIngestionService.ingestScanBatch(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}