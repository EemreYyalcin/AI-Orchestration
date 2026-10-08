CREATE TABLE incident_investigations (
    id uuid PRIMARY KEY,
    owner_id uuid NOT NULL REFERENCES app_users(id),
    question varchar(4000) NOT NULL CHECK (length(btrim(question)) > 0),
    status varchar(32) NOT NULL CHECK (status IN ('CREATED', 'ANALYZING', 'COLLECTING_EVIDENCE', 'REASONING', 'WAITING_APPROVAL', 'COMPLETED', 'FAILED')),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL
);
CREATE INDEX ix_investigations_owner ON incident_investigations(owner_id);
