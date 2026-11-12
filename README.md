# Axe Scan Ingestion Service

A high-throughput accessibility scan ingestion microservice built with **Java 21**, **Spring Boot 4.1.2**, **Hibernate / Spring Data JPA**, **Flyway**, and **PostgreSQL 18**, paired with a headless **Node.js Playwright + `@axe-core/playwright`** CLI scanner agent.

Designed as a reference architecture for enterprise accessibility monitoring pipelines (modeled on workflows like Deque's **Axe Monitor**), this system automates live WCAG DOM audits across multi-URL batches, normalizes hierarchical `axe-core` rule and node outputs, and persists relational audit records with strict idempotency and transactional guarantees.

---

## System Architecture & Data Flow

    +-----------------------------------------------------------------------+
    | 1. Headless Scanner Agent (scanner/scan-and-ingest.mjs)               |
    |    - Launches headless Chromium via Playwright                        |
    |    - Injects @axe-core/playwright into live target URLs               |
    |    - Flattens nested Shadow DOM / iframe CSS selector arrays          |
    |    - Bundles multi-URL results under a cryptographic idempotencyKey   |
    +-----------------------------------+-----------------------------------+
                                        |
                                        | HTTP POST /api/v1/scans/ingest
                                        v
    +-----------------------------------------------------------------------+
    | 2. Spring Boot Ingestion API (Java 21 + Virtual Threads)              |
    |    - Validates payload via Jakarta Bean Validation (@Valid records)   |
    |    - Checks idempotencyKey to prevent duplicate retry ingestion       |
    |    - Extracts WCAG criteria (e.g., 'wcag143' -> '1.4.3') via regex    |
    |    - Flattens rules & failing DOM nodes into relational entities      |
    +-----------------------------------+-----------------------------------+
                                        |
                                        | Hibernate JDBC Batching (size: 50)
                                        v
    +-----------------------------------------------------------------------+
    | 3. PostgreSQL 18 Persistence (Managed via Flyway V1 Migration)        |
    |    - scan_batches (1) ---> (N) scan_targets (1) ---> (N) rule_violations
    |    - Indexed on idempotency_key, batch_id, (target_id, impact_level)  |
    +-----------------------------------------------------------------------+

---

## Key Engineering Highlights (Of Interest to Deque)

* **Real-World `axe-core` Payload Normalization:**
  * **Shadow DOM & Iframe Selector Flattening:** In `axe-core`, standard DOM violations emit `target` as a flat string array (e.g., `[".btn"]`), whereas violations inside Shadow DOM roots or iframes emit nested arrays of arrays (e.g., `[["#shadow-host", "#inner-input"]]`). `scanner/scan-and-ingest.mjs` normalizes cross-boundary selectors with ` >>> ` delimiters so Jackson deserializes a deterministic `List<String>` into `AxeNodeDto`.
  * **WCAG Tag Extraction:** `AxeScanPayloadMapper` parses `axe-core` tag arrays (`["cat.color", "wcag2aa", "wcag143"]`) using compiled regex (`^wcag(\d)(\d)(\d+)$`) to extract structured WCAG success criteria (`1.4.3`, `1.1.1`, `4.1.2`) for database indexing and compliance reporting.
  * **Node-Level Impact Resolution:** Accurately resolves node-specific impact overrides (`CRITICAL`, `SERIOUS`, `MODERATE`, `MINOR`) with fallback inheritance from the parent rule definition.
* **High-Throughput Hibernate & Relational SQL Discipline:**
  * **Zero Open-Session-in-View (`spring.jpa.open-in-view: false`):** Disables anti-pattern lazy loading in the web layer, enforcing explicit data fetching boundaries inside `@Transactional` service methods.
  * **JDBC Statement Batching:** Configured with `hibernate.jdbc.batch_size: 50`, `order_inserts: true`, and `order_updates: true` so multi-page scans containing hundreds of failing DOM nodes are persisted in batched SQL round-trips.
  * **N+1 Query Prevention via `LEFT JOIN FETCH`:** All `@ManyToOne` associations default to `FetchType.LAZY`, paired with custom JPQL fetch-join queries (`findByIdempotencyKeyWithTargets`, `findByBatchIdWithViolations`) to hydrate entity graphs in a single SQL query.
  * **Database-Level Severity Aggregation:** Rather than pulling thousands of violation entities into JVM memory to count severities, `RuleViolationRepository.countViolationsByImpactForBatch` executes a native SQL `GROUP BY` aggregation indexed on `(target_id, impact_level)`.
* **Idempotent Batch Ingestion:**
  * Every scan batch requires a unique `idempotencyKey` backed by a `UNIQUE` database constraint and index (`idx_scan_batches_idempotency`).
  * If a CI runner or scanner worker retries a network request with the same key, `ScanIngestionService` intercepts the duplicate and returns the existing persisted batch metadata without duplicating `scan_targets` or `rule_violations` rows.
* **Multi-Tier Automated Verification:**
  * **Unit Tests (`AxeScanPayloadMapperTest`, `ScanIngestionControllerTest`):** Fast, isolated verification of WCAG tag regex parsing, entity graph bidirectional wiring, and `MockMvc` HTTP 201/400 contract validation.
  * **Repository Slice & Cache-Eviction Tests (`ScanIngestionRepositoryTest`):** Explicitly invokes `entityManager.flush()` and `entityManager.clear()` to evict Hibernate's 1st-level persistence context before asserting cascade inserts, `LEFT JOIN FETCH` queries, and `GROUP BY` aggregations.
  * **End-to-End Testcontainers Integration (`ScanIngestionIntegrationTest`):** Spins up an ephemeral PostgreSQL container via `@ServiceConnection`, executes Flyway migrations (`V1__init_schema.sql`), verifies full HTTP-to-SQL persistence, and asserts idempotency under duplicate POST requests.

---

## Running the Application with Persistent Production Data

### Prerequisites
* **Java 21** (JDK 21+)
* **Docker Desktop** (for persistent PostgreSQL 18 via Docker Compose and Testcontainers)
* **Node.js 20+** (for the headless Playwright + `@axe-core/playwright` scanner CLI)

### Step 1: Start the Persistent PostgreSQL 18 Container
The project includes a `docker-compose.yml` configured with `postgres:18-alpine` and a named persistent volume (`postgres_data` mounted to `/var/lib/postgresql`). To avoid port collisions with native Windows PostgreSQL services on `5432`, the container maps host port **`5433`** to container port `5432`:

    docker compose up -d
    docker ps

### Step 2: Boot the Production Spring Boot Ingestion Service
In Terminal Tab 1, start the production application (`AxeScanIngestionServiceApplication`). On boot, **Flyway** connects to `jdbc:postgresql://localhost:5433/axe_monitor`, applies `V1__init_schema.sql`, and starts Tomcat on port **`8081`**:

    .\mvnw.cmd clean spring-boot:run

### Step 3: Run Live Multi-URL `@axe-core/playwright` Scans
Open a second terminal tab (`Local (2)`), navigate to `scanner/`, install dependencies (first run only), and pass one or more live URLs to audit in headless Chromium and ingest into PostgreSQL:

    cd scanner
    npm install
    npx playwright install chromium

    node scan-and-ingest.mjs "[https://dequeuniversity.com/demo/mars/](https://dequeuniversity.com/demo/mars/)" "[https://www.w3.org/WAI/demos/bad/before/home.html](https://www.w3.org/WAI/demos/bad/before/home.html)" "[https://www.w3.org/WAI/demos/bad/before/news.html](https://www.w3.org/WAI/demos/bad/before/news.html)"

Expected console output:

    Starting headless Chromium for 3 target(s)...

    [1/3] Navigating to: [https://dequeuniversity.com/demo/mars/](https://dequeuniversity.com/demo/mars/)
    [2/3] Running axe-core WCAG analysis on: [https://dequeuniversity.com/demo/mars/](https://dequeuniversity.com/demo/mars/)
          Found 13 violated rules (75 total DOM nodes)

    [1/3] Navigating to: [https://www.w3.org/WAI/demos/bad/before/home.html](https://www.w3.org/WAI/demos/bad/before/home.html)
    [2/3] Running axe-core WCAG analysis on: [https://www.w3.org/WAI/demos/bad/before/home.html](https://www.w3.org/WAI/demos/bad/before/home.html)
          Found 7 violated rules (67 total DOM nodes)

    [1/3] Navigating to: [https://www.w3.org/WAI/demos/bad/before/news.html](https://www.w3.org/WAI/demos/bad/before/news.html)
    [2/3] Running axe-core WCAG analysis on: [https://www.w3.org/WAI/demos/bad/before/news.html](https://www.w3.org/WAI/demos/bad/before/news.html)
          Found 6 violated rules (52 total DOM nodes)

    [3/3] POSTing payload to http://localhost:8081/api/v1/scans/ingest
          idempotencyKey: playwright-batch-3ebafe3d-6628-4500-a838-b3927683fc15

    SUCCESS — Spring Boot Ingestion Response (HTTP 201 Created):
    batchId:         '30a639be-1c17-4acf-962e-0789e214a03d'
    idempotencyKey:  'playwright-batch-3ebafe3d-6628-4500-a838-b3927683fc15'
    status:          'COMPLETED'
    targetCount:     3
    totalViolations: 194
    createdAt:       '2026-11-12T19:15:47.580321100Z'

### Step 4: Query Persistent Production Data in PostgreSQL
Verify the ingested scan batches, targets, and severity breakdowns directly inside the persistent `axe-monitor-postgres` container (`-P pager=off` prevents `psql` from locking the terminal):

    docker exec -it axe-monitor-postgres psql -U postgres -d axe_monitor -P pager=off -c "SELECT t.target_url, t.total_violations, COUNT(v.id) FILTER (WHERE v.impact_level = 'CRITICAL') AS critical, COUNT(v.id) FILTER (WHERE v.impact_level = 'SERIOUS') AS serious, COUNT(v.id) FILTER (WHERE v.impact_level = 'MODERATE') AS moderate FROM scan_targets t LEFT JOIN rule_violations v ON v.target_id = t.id GROUP BY t.id, t.target_url, t.total_violations;"

---

## Running the Automated Test Suite

Execute the full JUnit 5, Mockito, and Testcontainers integration test suite via Maven:

    .\mvnw.cmd test

---

## Roadmap & Next Steps

1. **Read-Side Reporting & Analytics API (`GET /api/v1/scans/{batchId}`):**
   * Expose `@Transactional(readOnly = true)` REST endpoints backed by `RuleViolationRepository.countViolationsByImpactForBatch` and `ScanTargetRepository.findByBatchIdWithViolations` to serve batch summaries, severity breakdowns, and paginated DOM violation lists.
2. **Database Sequence/UUIDv7 Optimization for Bulk Inserts:**
   * Transition UUID generation from runtime strategy to pre-allocated sequence/UUIDv7 keys so Hibernate can batch-insert `RuleViolation` records without per-row flush overhead on massive (1,000+ page) enterprise site crawls.
3. **Asynchronous Queue Decoupling:**
   * Introduce an asynchronous ingestion mode (`202 Accepted` with `BatchStatus.PENDING` -> `PROCESSING` -> `COMPLETED`) using Spring Events / message queues so huge scan payloads return immediately while worker threads persist chunks in the background.
4. **Global Exception Handling (`@RestControllerAdvice`):**
   * Standardize RFC 7807 `ProblemDetail` error responses for Jakarta validation failures (`MethodArgumentNotValidException`) and missing batch lookups (`404 Not Found`).
5. **Accessible React Dashboard Integration:**
   * Connect this backend service to a WCAG AA/AAA compliant React + TypeScript frontend (powered by `accessible-ui-primitives`) to visualize scan trends, filter by WCAG criterion, and inspect failing DOM snippets via keyboard-navigable data tables.
