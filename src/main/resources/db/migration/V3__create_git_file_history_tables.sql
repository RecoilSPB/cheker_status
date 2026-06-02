CREATE TABLE nsi_document_git_commit_file (
    id BIGSERIAL PRIMARY KEY,
    git_link_id BIGINT NOT NULL REFERENCES nsi_document_git_link(id) ON DELETE CASCADE,
    commit_id BIGINT NOT NULL REFERENCES nsi_document_git_commit(id) ON DELETE CASCADE,
    commit_sha VARCHAR(64) NOT NULL,
    parent_sha VARCHAR(64),
    old_path TEXT,
    new_path TEXT NOT NULL,
    file_path TEXT NOT NULL,
    change_type VARCHAR(32) NOT NULL,
    renamed_file BOOLEAN NOT NULL DEFAULT false,
    new_file BOOLEAN NOT NULL DEFAULT false,
    deleted_file BOOLEAN NOT NULL DEFAULT false,
    diff TEXT,
    diff_too_large BOOLEAN NOT NULL DEFAULT false,
    content_after TEXT,
    content_after_sha256 VARCHAR(64),
    content_after_size BIGINT,
    content_before TEXT,
    content_before_sha256 VARCHAR(64),
    content_before_size BIGINT,
    content_fetch_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    content_fetch_error TEXT,
    committed_date TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_nsi_git_commit_file UNIQUE (commit_id, old_path, new_path)
);

CREATE INDEX ix_nsi_git_commit_file_git_link_id
    ON nsi_document_git_commit_file (git_link_id);

CREATE INDEX ix_nsi_git_commit_file_commit_sha
    ON nsi_document_git_commit_file (commit_sha);

CREATE INDEX ix_nsi_git_commit_file_file_path
    ON nsi_document_git_commit_file (file_path);

CREATE INDEX ix_nsi_git_commit_file_change_type
    ON nsi_document_git_commit_file (change_type);

CREATE INDEX ix_nsi_git_commit_file_committed_date
    ON nsi_document_git_commit_file (committed_date);

CREATE TABLE nsi_document_git_file_state (
    id BIGSERIAL PRIMARY KEY,
    git_link_id BIGINT NOT NULL REFERENCES nsi_document_git_link(id) ON DELETE CASCADE,
    file_path TEXT NOT NULL,
    last_commit_sha VARCHAR(64) NOT NULL,
    content TEXT,
    content_sha256 VARCHAR(64),
    content_size BIGINT,
    deleted BOOLEAN NOT NULL DEFAULT false,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_nsi_git_file_state UNIQUE (git_link_id, file_path)
);

CREATE INDEX ix_nsi_git_file_state_git_link_id
    ON nsi_document_git_file_state (git_link_id);
