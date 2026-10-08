ALTER TABLE incident_investigations ADD COLUMN report jsonb;
ALTER TABLE incident_investigations ADD COLUMN approval varchar(24) NOT NULL DEFAULT 'NONE';
ALTER TABLE incident_investigations ADD CONSTRAINT investigation_approval_valid CHECK (approval IN ('NONE', 'PENDING', 'APPROVED', 'DENIED', 'TIMED_OUT'));
CREATE TABLE mock_remediation_actions (
    investigation_id uuid PRIMARY KEY REFERENCES incident_investigations(id),
    action varchar(64) NOT NULL CHECK (action = 'RESTART_RECOMMENDATION_SERVICE'),
    executed_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);
