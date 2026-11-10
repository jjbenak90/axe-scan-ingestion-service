package com.deque.monitor.axe_scan_ingestion_service.repository;

import com.deque.monitor.axe_scan_ingestion_service.domain.BatchStatus;
import com.deque.monitor.axe_scan_ingestion_service.domain.ImpactLevel;
import com.deque.monitor.axe_scan_ingestion_service.domain.RuleViolation;
import com.deque.monitor.axe_scan_ingestion_service.domain.ScanBatch;
import com.deque.monitor.axe_scan_ingestion_service.domain.ScanTarget;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional // Automatically rolls back every DB operation when each test finishes so tests never bleed data into each other
class ScanIngestionRepositoryTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ScanBatchRepository scanBatchRepository;

    @Autowired
    private ScanTargetRepository scanTargetRepository;

    @Autowired
    private RuleViolationRepository ruleViolationRepository;

    /**
     * TEST 1: Cascade Persistence Verification
     * ----------------------------------------
     * WHAT WE ARE TESTING:
     * When we save just the root object (ScanBatch), does Hibernate automatically
     * traverse the object graph and insert all child ScanTargets AND grandchild RuleViolations
     * without us having to call scanTargetRepository.save() or ruleViolationRepository.save()?
     *
     * WHY IT MATTERS:
     * In an ingestion engine handling high-throughput axe-core payloads, we want to construct
     * the entire parsed scan hierarchy in memory and persist it with a single repository call.
     * If CascadeType.ALL or bidirectional relationship pointers are broken, child records get dropped.
     */
    @Test
    @DisplayName("Should persist entire object hierarchy via cascade when saving a ScanBatch")
    void shouldCascadePersistBatchGraph() {
        // STEP 1: Build the parent ScanBatch in memory
        ScanBatch batch = ScanBatch.builder()
                .idempotencyKey("idemp-" + UUID.randomUUID())
                .initiatedBy("ci-pipeline-worker")
                .status(BatchStatus.PENDING)
                .build();

        // STEP 2: Build a child ScanTarget (a web page scanned by axe-core)
        ScanTarget target = ScanTarget.builder()
                .targetUrl("https://example.com/checkout")
                .scannedAt(Instant.now())
                .totalViolations(2)
                .build();

        // STEP 3: Build two grandchild RuleViolations (actual accessibility failures found on that page)
        RuleViolation violation1 = RuleViolation.builder()
                .ruleId("color-contrast")
                .impactLevel(ImpactLevel.SERIOUS)
                .wcagCriterion("1.4.3")
                .htmlSnippet("<button class='low-contrast'>Submit</button>")
                .cssSelector("#main > form > button")
                .failureSummary("Element has insufficient color contrast of 2.8:1 (expected 4.5:1)")
                .build();

        RuleViolation violation2 = RuleViolation.builder()
                .ruleId("image-alt")
                .impactLevel(ImpactLevel.CRITICAL)
                .wcagCriterion("1.1.1")
                .htmlSnippet("<img src='logo.png'>")
                .cssSelector("header > img")
                .failureSummary("Image element missing alt attribute")
                .build();

        // STEP 4: Wire the relationships bidirectionally.
        // target.addViolation() sets violation.setTarget(target) AND adds it to target.violations list.
        // batch.addTarget() sets target.setBatch(batch) AND adds it to batch.targets list.
        target.addViolation(violation1);
        target.addViolation(violation2);
        batch.addTarget(target);

        // STEP 5: Persist ONLY the root ScanBatch entity.
        // If CascadeType.ALL is configured properly, Hibernate cascades inserts down to targets and violations.
        ScanBatch savedBatch = scanBatchRepository.save(batch);

        // STEP 6: Flush and clear the Hibernate session.
        // - flush(): Forces Hibernate to execute all queued SQL INSERT statements to the H2 database immediately.
        // - clear(): Wipes the 1st-level cache. Without this, calling findById() below would just read the
        //   Java objects already sitting in JVM memory instead of verifying they actually got written to the DB tables.
        entityManager.flush();
        entityManager.clear();

        // STEP 7: Assertions - Query the DB from scratch to confirm all 3 tables received the cascaded records.
        assertThat(savedBatch.getId()).isNotNull();

        // Confirm parent batch exists in scan_batches table
        Optional<ScanBatch> fetchedBatch = scanBatchRepository.findById(savedBatch.getId());
        assertThat(fetchedBatch).isPresent();
        assertThat(fetchedBatch.get().getIdempotencyKey()).isEqualTo(batch.getIdempotencyKey());

        // Confirm child target exists in scan_targets table with the correct foreign key
        List<ScanTarget> persistedTargets = scanTargetRepository.findByBatchId(savedBatch.getId());
        assertThat(persistedTargets).hasSize(1);
        assertThat(persistedTargets.getFirst().getTargetUrl()).isEqualTo("https://example.com/checkout");

        // Confirm grandchild violations exist in rule_violations table with the correct target foreign key
        List<RuleViolation> persistedViolations = ruleViolationRepository.findByTargetId(persistedTargets.getFirst().getId());
        assertThat(persistedViolations).hasSize(2)
                .extracting(RuleViolation::getRuleId)
                .containsExactlyInAnyOrder("color-contrast", "image-alt");
    }

    /**
     * TEST 2: Custom JPQL Fetch Join (N+1 Query Prevention)
     * -----------------------------------------------------
     * WHAT WE ARE TESTING:
     * Does findByIdempotencyKeyWithTargets() execute a SQL JOIN to pull both the ScanBatch
     * AND all its child ScanTargets in ONE query, rather than executing 1 query for the batch
     * and N separate queries for each target?
     *
     * WHY IT MATTERS:
     * If a batch has 100 scanned URLs, standard lazy loading would trigger 101 database round-trips.
     * Furthermore, if you access batch.getTargets() outside an active transaction, Hibernate crashes
     * with LazyInitializationException. A JOIN FETCH fixes both problems by joining the tables up front.
     */
    @Test
    @DisplayName("Should fetch ScanBatch with targets using custom JPQL fetch join without N+1 queries")
    void shouldFetchBatchWithTargets() {
        // STEP 1: Set up a batch with two targets (page-a and page-b)
        String idempotencyKey = "idemp-fetch-test-" + UUID.randomUUID();
        ScanBatch batch = ScanBatch.builder()
                .idempotencyKey(idempotencyKey)
                .initiatedBy("nightly-cron")
                .status(BatchStatus.PROCESSING)
                .build();

        ScanTarget targetA = ScanTarget.builder()
                .targetUrl("https://example.com/page-a")
                .scannedAt(Instant.now())
                .totalViolations(0)
                .build();

        ScanTarget targetB = ScanTarget.builder()
                .targetUrl("https://example.com/page-b")
                .scannedAt(Instant.now())
                .totalViolations(0)
                .build();

        batch.addTarget(targetA);
        batch.addTarget(targetB);

        // STEP 2: Save to DB, write SQL, and empty the cache
        ScanBatch persisted = scanBatchRepository.save(batch);
        entityManager.flush();
        entityManager.clear();

        // STEP 3: Call our custom JPQL method:
        // "SELECT DISTINCT b FROM ScanBatch b LEFT JOIN FETCH b.targets WHERE b.idempotencyKey = :idempotencyKey"
        Optional<ScanBatch> result = scanBatchRepository.findByIdempotencyKeyWithTargets(idempotencyKey);

        // STEP 4: Assertions - Verify the batch was returned and its targets list was eagerly initialized
        assertThat(result).isPresent();
        ScanBatch loadedBatch = result.get();
        assertThat(loadedBatch.getId()).isEqualTo(persisted.getId());

        // Accessing getTargets() here works safely because JOIN FETCH loaded them into memory in the initial SELECT
        assertThat(loadedBatch.getTargets()).hasSize(2);
        assertThat(loadedBatch.getTargets())
                .extracting(ScanTarget::getTargetUrl)
                .containsExactlyInAnyOrder("https://example.com/page-a", "https://example.com/page-b");
    }

    /**
     * TEST 3: Violation Fetching & SQL GROUP BY Aggregation
     * -----------------------------------------------------
     * WHAT WE ARE TESTING:
     * 1. Does ScanTargetRepository.findByIdWithViolations() eagerly load all violations for a given URL?
     * 2. Does RuleViolationRepository.countViolationsByImpactForBatch() run an aggregate GROUP BY query
     *    in the database to compute violation counts bucketed by severity (CRITICAL, MINOR, etc.)?
     *
     * WHY IT MATTERS:
     * Dashboard summaries and accessibility reports need counts by severity (e.g., "5 critical, 12 minor").
     * Computing this inside SQL via GROUP BY is orders of magnitude faster than loading thousands of entity
     * objects into JVM memory and counting them in Java streams.
     */
    @Test
    @DisplayName("Should fetch ScanTarget with violations and compute impact aggregation")
    void shouldFetchTargetWithViolationsAndAggregateImpact() {
        // STEP 1: Create a batch with 1 target and 3 violations (2 CRITICAL, 1 MINOR)
        ScanBatch batch = ScanBatch.builder()
                .idempotencyKey("idemp-agg-" + UUID.randomUUID())
                .initiatedBy("manual-trigger")
                .status(BatchStatus.COMPLETED)
                .build();

        ScanTarget target = ScanTarget.builder()
                .targetUrl("https://example.com/report")
                .scannedAt(Instant.now())
                .totalViolations(3)
                .build();

        RuleViolation crit1 = RuleViolation.builder()
                .ruleId("button-name")
                .impactLevel(ImpactLevel.CRITICAL)
                .wcagCriterion("4.1.2")
                .failureSummary("Button has no accessible name")
                .build();

        RuleViolation crit2 = RuleViolation.builder()
                .ruleId("aria-roles")
                .impactLevel(ImpactLevel.CRITICAL)
                .wcagCriterion("4.1.2")
                .failureSummary("Invalid ARIA role specified")
                .build();

        RuleViolation minor = RuleViolation.builder()
                .ruleId("empty-heading")
                .impactLevel(ImpactLevel.MINOR)
                .wcagCriterion("1.3.1")
                .failureSummary("Heading contains no text")
                .build();

        target.addViolation(crit1);
        target.addViolation(crit2);
        target.addViolation(minor);
        batch.addTarget(target);

        // STEP 2: Persist and evict session cache so we read clean data from H2
        scanBatchRepository.save(batch);
        entityManager.flush();
        entityManager.clear();

        // STEP 3: Verify target-level fetch join query
        Optional<ScanTarget> targetResult = scanTargetRepository.findByIdWithViolations(target.getId());
        assertThat(targetResult).isPresent();
        assertThat(targetResult.get().getViolations()).hasSize(3);

        // STEP 4: Execute the SQL aggregation query:
        // "SELECT v.impactLevel, COUNT(v) FROM RuleViolation v WHERE v.target.batch.id = :batchId GROUP BY v.impactLevel"
        List<Object[]> impactSummary = ruleViolationRepository.countViolationsByImpactForBatch(batch.getId());

        // STEP 5: Assertions - Verify the DB grouped the counts accurately
        assertThat(impactSummary).hasSize(2); // Exactly 2 groups: CRITICAL and MINOR

        long criticalCount = 0;
        long minorCount = 0;

        for (Object[] row : impactSummary) {
            ImpactLevel level = (ImpactLevel) row[0]; // First selected column is the ImpactLevel enum
            Long count = (Long) row[1];              // Second selected column is the COUNT(v) aggregate

            if (level == ImpactLevel.CRITICAL) {
                criticalCount = count;
            } else if (level == ImpactLevel.MINOR) {
                minorCount = count;
            }
        }

        // Verify counts match what we inserted: 2 CRITICAL, 1 MINOR
        assertThat(criticalCount).isEqualTo(2L);
        assertThat(minorCount).isEqualTo(1L);
    }
}