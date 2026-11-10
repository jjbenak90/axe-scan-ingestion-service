package com.deque.monitor.axe_scan_ingestion_service.repository;

import com.deque.monitor.axe_scan_ingestion_service.domain.BatchStatus;
import com.deque.monitor.axe_scan_ingestion_service.domain.ScanBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScanBatchRepository extends JpaRepository<ScanBatch, UUID> {

    Optional<ScanBatch> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);

    List<ScanBatch> findByStatus(BatchStatus status);

    /**
     * Eagerly loads a ScanBatch along with its child ScanTargets in a single query.
     */
    @Query("""
        SELECT DISTINCT b 
        FROM ScanBatch b 
        LEFT JOIN FETCH b.targets 
        WHERE b.id = :id
    """)
    Optional<ScanBatch> findByIdWithTargets(@Param("id") UUID id);

    /**
     * Eagerly loads by idempotency key with child ScanTargets.
     */
    @Query("""
        SELECT DISTINCT b 
        FROM ScanBatch b 
        LEFT JOIN FETCH b.targets 
        WHERE b.idempotencyKey = :idempotencyKey
    """)
    Optional<ScanBatch> findByIdempotencyKeyWithTargets(@Param("idempotencyKey") String idempotencyKey);
}