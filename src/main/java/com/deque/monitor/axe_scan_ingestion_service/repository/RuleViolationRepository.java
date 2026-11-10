package com.deque.monitor.axe_scan_ingestion_service.repository;

import com.deque.monitor.axe_scan_ingestion_service.domain.ImpactLevel;
import com.deque.monitor.axe_scan_ingestion_service.domain.RuleViolation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RuleViolationRepository extends JpaRepository<RuleViolation, UUID> {

    List<RuleViolation> findByTargetId(UUID targetId);

    List<RuleViolation> findByRuleId(String ruleId);

    List<RuleViolation> findByImpactLevel(ImpactLevel impactLevel);

    long countByTargetId(UUID targetId);

    /**
     * Aggregation query for reporting total violations grouped by impact level for a batch.
     */
    @Query("""
        SELECT v.impactLevel, COUNT(v) 
        FROM RuleViolation v 
        WHERE v.target.batch.id = :batchId 
        GROUP BY v.impactLevel
    """)
    List<Object[]> countViolationsByImpactForBatch(@Param("batchId") UUID batchId);
}