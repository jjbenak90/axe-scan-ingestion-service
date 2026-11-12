import { chromium } from 'playwright';
import AxeBuilder from '@axe-core/playwright';
import crypto from 'node:crypto';

// 1. Target your local Spring Boot server on port 8081
const INGESTION_ENDPOINT = process.env.INGESTION_URL || 'http://localhost:8081/api/v1/scans/ingest';

// 2. Accept URLs from the command line, or default to Deque's broken Mars training page
const cliUrls = process.argv.slice(2);
const urlsToScan = cliUrls.length > 0
  ? cliUrls
  : ['https://dequeuniversity.com/demo/mars/'];

async function runScanAndIngest() {
  console.log(`Starting headless Chromium for ${urlsToScan.length} target(s)...`);
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext();
  const targetsPayload = [];

  try {
    for (const url of urlsToScan) {
      console.log(`\n[1/3] Navigating to: ${url}`);
      const page = await context.newPage();

      // Wait until the DOM is parsed and ready for inspection
      await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 30000 });

      console.log(`[2/3] Running axe-core WCAG analysis on: ${url}`);
      const axeResults = await new AxeBuilder({ page }).analyze();

      // Transform raw axe-core output to strictly match your Java DTOs:
      // AxeTargetDto -> List<AxeRuleDto> -> List<AxeNodeDto>
      const mappedViolations = axeResults.violations.map((rule) => ({
        id: rule.id,
        impact: rule.impact || 'minor',
        description: rule.description,
        help: rule.help,
        tags: Array.isArray(rule.tags) ? rule.tags : [],
        nodes: rule.nodes.map((node) => ({
          // AxeNodeDto requires @NotBlank impact, html, failureSummary and @NotEmpty target
          impact: node.impact || rule.impact || 'minor',
          html: node.html || '<unknown>',
          // In axe-core, shadow DOM selectors can be nested arrays (e.g. [["#host", "#input"]]).
          // Flatten each entry to a clean String so Jackson maps it to List<String> target.
          target: (node.target && node.target.length > 0)
            ? node.target.map((selector) =>
                Array.isArray(selector) ? selector.join(' >>> ') : String(selector)
              )
            : ['document'],
          failureSummary: node.failureSummary || rule.help || 'Accessibility violation detected'
        }))
      }));

      // Count total failing DOM nodes across all violated rules for console reporting
      const totalFailingNodes = mappedViolations.reduce(
        (sum, r) => sum + r.nodes.length,
        0
      );
      console.log(
        `      Found ${mappedViolations.length} violated rules (${totalFailingNodes} total DOM nodes)`
      );

      targetsPayload.push({
        url: axeResults.url || url,
        // axeResults.timestamp is already an ISO-8601 UTC string (e.g., "2026-11-12T17:55:00.123Z")
        // which Jackson parses directly into java.time.Instant
        timestamp: axeResults.timestamp || new Date().toISOString(),
        violations: mappedViolations
      });

      await page.close();
    }

    // Build top-level AxeScanIngestionRequest payload
    const ingestionPayload = {
      idempotencyKey: `playwright-batch-${crypto.randomUUID()}`,
      initiatedBy: 'playwright-axe-cli',
      targets: targetsPayload
    };

    console.log(`\n[3/3] POSTing payload to ${INGESTION_ENDPOINT}`);
    console.log(`      idempotencyKey: ${ingestionPayload.idempotencyKey}`);

    const response = await fetch(INGESTION_ENDPOINT, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json'
      },
      body: JSON.stringify(ingestionPayload)
    });

    if (!response.ok) {
      const errorBody = await response.text();
      throw new Error(`Spring Boot returned HTTP ${response.status}: ${errorBody}`);
    }

    const result = await response.json();
    console.log('\nSUCCESS — Spring Boot Ingestion Response (HTTP 201 Created):');
    console.table(result);
  } finally {
    await browser.close();
  }
}

runScanAndIngest().catch((err) => {
  console.error('\nERROR during scan/ingestion pipeline:', err.message);
  process.exit(1);
});