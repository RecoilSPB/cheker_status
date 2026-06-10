CREATE TABLE nsi_document_git_commit_file_diff (
    id BIGSERIAL PRIMARY KEY,
    file_change_id BIGINT NOT NULL REFERENCES nsi_document_git_commit_file(id) ON DELETE CASCADE,
    git_link_id BIGINT NOT NULL REFERENCES nsi_document_git_link(id) ON DELETE CASCADE,
    status VARCHAR(32) NOT NULL,
    diff_type VARCHAR(32) NOT NULL,
    format_family VARCHAR(32) NOT NULL,
    summary_json JSONB,
    artifact_object_key TEXT,
    error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_nsi_git_commit_file_diff_file_change UNIQUE (file_change_id)
);

CREATE INDEX ix_nsi_git_commit_file_diff_git_link_id
    ON nsi_document_git_commit_file_diff (git_link_id);

CREATE INDEX ix_nsi_git_commit_file_diff_status
    ON nsi_document_git_commit_file_diff (status);

CREATE INDEX ix_nsi_git_commit_file_diff_diff_type
    ON nsi_document_git_commit_file_diff (diff_type);
