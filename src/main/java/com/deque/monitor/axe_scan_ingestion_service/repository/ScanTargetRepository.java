package com.deque.monitor.axe_scan_ingestion_service.repository;

import com.deque.monitor.axe_scan_ingestion_service.domain.ScanTarget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScanTargetRepository extends JpaRepository<ScanTarget, UUID> {

    List<ScanTarget> findByBatchId(UUID batchId);

    /**
     * Fetches a single target along with all associated rule violations in one round-trip.
     */
    @Query("""
        SELECT DISTINCT t 
        FROM ScanTarget t 
        LEFT JOIN FETCH t.violations 
        WHERE t.id = :id
    """)
    Optional<ScanTarget> findByIdWithViolations(@Param("id") UUID id);

    /**
     * Fetches all targets and their violations for a given batch.
     */
    @Query("""
        SELECT DISTINCT t 
        FROM ScanTarget t 
        LEFT JOIN FETCH t.violations 
        WHERE t.batch.id = :batchId
    """)
    List<ScanTarget> findByBatchIdWithViolations(@Param("batchId") UUID batchId);
}