ALTER TABLE incident_investigations ADD COLUMN start_requested boolean NOT NULL DEFAULT false;
ALTER TABLE incident_investigations ADD COLUMN start_attempts integer NOT NULL DEFAULT 0 CHECK (start_attempts >= 0);
CREATE INDEX idx_investigations_pending_start ON incident_investigations (created_at) WHERE start_requested = true;
