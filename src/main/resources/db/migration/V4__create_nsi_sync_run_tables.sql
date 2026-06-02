CREATE TABLE nsi_sync_run (
    id BIGSERIAL PRIMARY KEY,
    run_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    dictionary_identifier VARCHAR(255),
    dictionary_version VARCHAR(255),
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at TIMESTAMPTZ,
    duration_ms BIGINT,
    force_reload BOOLEAN NOT NULL DEFAULT false,
    nsi_rows_total INTEGER NOT NULL DEFAULT 0,
    nsi_rows_inserted INTEGER NOT NULL DEFAULT 0,
    nsi_rows_updated INTEGER NOT NULL DEFAULT 0,
    nsi_rows_deactivated INTEGER NOT NULL DEFAULT 0,
    git_links_found INTEGER NOT NULL DEFAULT 0,
    git_projects_processed INTEGER NOT NULL DEFAULT 0,
    git_commits_processed INTEGER NOT NULL DEFAULT 0,
    git_files_processed INTEGER NOT NULL DEFAULT 0,
    error_count INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    payload JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_nsi_sync_run_started_at
    ON nsi_sync_run (started_at);

CREATE INDEX ix_nsi_sync_run_status
    ON nsi_sync_run (status);

CREATE INDEX ix_nsi_sync_run_run_type
    ON nsi_sync_run (run_type);

CREATE INDEX ix_nsi_sync_run_dictionary_identifier
    ON nsi_sync_run (dictionary_identifier);

CREATE INDEX ix_nsi_sync_run_dictionary_version
    ON nsi_sync_run (dictionary_version);

CREATE TABLE nsi_sync_run_error (
    id BIGSERIAL PRIMARY KEY,
    sync_run_id BIGINT NOT NULL REFERENCES nsi_sync_run(id) ON DELETE CASCADE,
    error_stage VARCHAR(64) NOT NULL,
    dictionary_identifier VARCHAR(255),
    git_link_id BIGINT,
    project_path TEXT,
    commit_sha VARCHAR(64),
    file_path TEXT,
    error_message TEXT NOT NULL,
    stack_trace TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_nsi_sync_run_error_sync_run_id
    ON nsi_sync_run_error (sync_run_id);

CREATE INDEX ix_nsi_sync_run_error_stage
    ON nsi_sync_run_error (error_stage);

CREATE INDEX ix_nsi_sync_run_error_created_at
    ON nsi_sync_run_error (created_at);
