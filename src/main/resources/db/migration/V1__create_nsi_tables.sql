CREATE TABLE nsi_dictionary (
    id BIGSERIAL PRIMARY KEY,
    oid TEXT NOT NULL,
    api_identifier TEXT,
    external_identifier TEXT,
    current_version TEXT,
    rows_count INTEGER,
    full_name TEXT,
    short_name TEXT,
    publish_date_text TEXT,
    last_update_text TEXT,
    passport_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_nsi_dictionary_oid UNIQUE (oid)
);

CREATE TABLE nsi_dictionary_version (
    id BIGSERIAL PRIMARY KEY,
    dictionary_id BIGINT NOT NULL REFERENCES nsi_dictionary(id) ON DELETE CASCADE,
    version TEXT NOT NULL,
    rows_count INTEGER,
    publish_date_text TEXT,
    last_update_text TEXT,
    passport_json JSONB NOT NULL,
    loaded_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_nsi_dictionary_version UNIQUE (dictionary_id, version)
);

CREATE TABLE nsi_dictionary_field (
    id BIGSERIAL PRIMARY KEY,
    dictionary_id BIGINT NOT NULL REFERENCES nsi_dictionary(id) ON DELETE CASCADE,
    field_name TEXT NOT NULL,
    data_type TEXT,
    alias TEXT,
    description TEXT,
    reference_oid TEXT,
    field_json JSONB NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_nsi_dictionary_field UNIQUE (dictionary_id, field_name)
);

CREATE TABLE nsi_record (
    id BIGSERIAL PRIMARY KEY,
    dictionary_id BIGINT NOT NULL REFERENCES nsi_dictionary(id) ON DELETE CASCADE,
    dictionary_version_id BIGINT NOT NULL REFERENCES nsi_dictionary_version(id) ON DELETE CASCADE,
    record_key TEXT NOT NULL,
    row_number INTEGER NOT NULL,
    content_hash TEXT NOT NULL,
    data JSONB NOT NULL,
    raw_cells JSONB NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_nsi_record_key UNIQUE (dictionary_id, record_key)
);

CREATE TABLE nsi_record_history (
    id BIGSERIAL PRIMARY KEY,
    dictionary_id BIGINT NOT NULL REFERENCES nsi_dictionary(id) ON DELETE CASCADE,
    dictionary_version_id BIGINT NOT NULL REFERENCES nsi_dictionary_version(id) ON DELETE CASCADE,
    record_key TEXT NOT NULL,
    row_number INTEGER NOT NULL,
    content_hash TEXT NOT NULL,
    data JSONB NOT NULL,
    raw_cells JSONB NOT NULL,
    captured_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_nsi_record_history UNIQUE (dictionary_version_id, record_key)
);

CREATE INDEX ix_nsi_dictionary_version_loaded
    ON nsi_dictionary_version (dictionary_id, version, loaded_at);

CREATE INDEX ix_nsi_record_dictionary_active
    ON nsi_record (dictionary_id, active);

CREATE INDEX ix_nsi_record_content_hash
    ON nsi_record (content_hash);

CREATE INDEX ix_nsi_record_data_gin
    ON nsi_record USING GIN (data);

CREATE INDEX ix_nsi_dictionary_passport_gin
    ON nsi_dictionary USING GIN (passport_json);
