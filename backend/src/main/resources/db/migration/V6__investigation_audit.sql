ALTER TABLE incident_investigations ADD COLUMN approval_requested_at timestamp with time zone;
CREATE TABLE investigation_audit (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    investigation_id uuid NOT NULL REFERENCES incident_investigations(id),
    stage varchar(32) NOT NULL,
    workflow_version varchar(16) NOT NULL,
    prompt_version varchar(16) NOT NULL,
    tools varchar(200) NOT NULL,
    outcome varchar(32) NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (investigation_id, stage, outcome)
);
