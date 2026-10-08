ALTER TABLE incident_investigations
    ADD COLUMN version bigint NOT NULL DEFAULT 0,
    ADD COLUMN classification jsonb,
    ADD COLUMN evidence jsonb;
