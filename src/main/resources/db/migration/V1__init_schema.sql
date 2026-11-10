CREATE TABLE scan_batches (
                              id UUID PRIMARY KEY,
                              idempotency_key VARCHAR(128) NOT NULL UNIQUE,
                              initiated_by VARCHAR(64) NOT NULL,
                              status VARCHAR(32) NOT NULL,
                              created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE scan_targets (
                              id UUID PRIMARY KEY,
                              batch_id UUID NOT NULL REFERENCES scan_batches(id) ON DELETE CASCADE,
                              target_url TEXT NOT NULL,
                              scanned_at TIMESTAMP WITH TIME ZONE NOT NULL,
                              total_violations INT NOT NULL DEFAULT 0,
                              created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE rule_violations (
                                 id UUID PRIMARY KEY,
                                 target_id UUID NOT NULL REFERENCES scan_targets(id) ON DELETE CASCADE,
                                 rule_id VARCHAR(64) NOT NULL,
                                 impact_level VARCHAR(16) NOT NULL,
                                 wcag_criterion VARCHAR(32) NOT NULL,
                                 html_snippet TEXT,
                                 css_selector TEXT,
                                 failure_summary TEXT NOT NULL
);

CREATE INDEX idx_scan_batches_idempotency ON scan_batches(idempotency_key);
CREATE INDEX idx_scan_targets_batch_id ON scan_targets(batch_id);
CREATE INDEX idx_rule_violations_target_impact ON rule_violations(target_id, impact_level);
CREATE INDEX idx_rule_violations_rule_id ON rule_violations(rule_id);