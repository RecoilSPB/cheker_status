CREATE TABLE nsi_document_git_link (
    id BIGSERIAL PRIMARY KEY,
    dictionary_id BIGINT NOT NULL REFERENCES nsi_dictionary(id) ON DELETE CASCADE,
    nsi_record_id BIGINT NOT NULL REFERENCES nsi_record(id) ON DELETE CASCADE,
    record_key TEXT NOT NULL,
    document_oid TEXT,
    document_name TEXT,
    git_link TEXT NOT NULL,
    git_host TEXT,
    project_path TEXT NOT NULL,
    tree_ref TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    last_sync_at TIMESTAMPTZ,
    last_sync_status TEXT,
    last_sync_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_nsi_document_git_link_record UNIQUE (dictionary_id, record_key)
);

CREATE TABLE nsi_document_git_commit (
    id BIGSERIAL PRIMARY KEY,
    document_git_link_id BIGINT NOT NULL REFERENCES nsi_document_git_link(id) ON DELETE CASCADE,
    commit_id TEXT NOT NULL,
    short_id TEXT,
    title TEXT,
    message TEXT,
    author_name TEXT,
    author_email TEXT,
    authored_date TIMESTAMPTZ,
    committer_name TEXT,
    committer_email TEXT,
    committed_date TIMESTAMPTZ,
    created_at_git TIMESTAMPTZ,
    web_url TEXT,
    raw_json JSONB NOT NULL,
    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_nsi_document_git_commit UNIQUE (document_git_link_id, commit_id)
);

CREATE INDEX ix_nsi_document_git_link_dictionary_active
    ON nsi_document_git_link (dictionary_id, active);

CREATE INDEX ix_nsi_document_git_link_project_ref
    ON nsi_document_git_link (project_path, tree_ref);

CREATE INDEX ix_nsi_document_git_commit_document
    ON nsi_document_git_commit (document_git_link_id, committed_date);

CREATE INDEX ix_nsi_document_git_commit_raw_gin
    ON nsi_document_git_commit USING GIN (raw_json);
