package com.deque.monitor.axe_scan_ingestion_service.service;

import com.deque.monitor.axe_scan_ingestion_service.domain.BatchStatus;
import com.deque.monitor.axe_scan_ingestion_service.domain.ScanBatch;
import com.deque.monitor.axe_scan_ingestion_service.domain.ScanTarget;
import com.deque.monitor.axe_scan_ingestion_service.dto.AxeScanIngestionRequest;
import com.deque.monitor.axe_scan_ingestion_service.dto.AxeScanIngestionResponse;
import com.deque.monitor.axe_scan_ingestion_service.mapper.AxeScanPayloadMapper;
import com.deque.monitor.axe_scan_ingestion_service.repository.ScanBatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScanIngestionService {

    private final ScanBatchRepository scanBatchRepository;
    private final AxeScanPayloadMapper mapper;

    @Transactional
    public AxeScanIngestionResponse ingestScanBatch(AxeScanIngestionRequest request) {
        // 1. Idempotency Guard: return existing batch if duplicate key is received
        Optional<ScanBatch> existingBatch = scanBatchRepository.findByIdempotencyKeyWithTargets(request.idempotencyKey());
        if (existingBatch.isPresent()) {
            ScanBatch batch = existingBatch.get();
            log.info("Duplicate scan batch ignored for idempotency key: {}", request.idempotencyKey());
            return toResponse(batch);
        }

        // 2. Map incoming DTO graph to domain entity graph
        ScanBatch newBatch = mapper.toEntity(request);
        newBatch.setStatus(BatchStatus.COMPLETED);

        // 3. Persist root entity (cascades to targets & violations)
        ScanBatch savedBatch = scanBatchRepository.save(newBatch);
        log.info("Persisted scan batch {} with {} targets", savedBatch.getId(), savedBatch.getTargets().size());

        return toResponse(savedBatch);
    }

    private AxeScanIngestionResponse toResponse(ScanBatch batch) {
        int totalViolations = batch.getTargets().stream()
                .mapToInt(ScanTarget::getTotalViolations)
                .sum();

        return new AxeScanIngestionResponse(
                batch.getId(),
                batch.getIdempotencyKey(),
                batch.getStatus(),
                batch.getTargets().size(),
                totalViolations,
                batch.getCreatedAt()
        );
    }
}