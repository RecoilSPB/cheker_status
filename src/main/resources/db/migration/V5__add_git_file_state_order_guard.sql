ALTER TABLE nsi_document_git_file_state
    ADD COLUMN last_committed_date TIMESTAMPTZ,
    ADD COLUMN last_commit_row_id BIGINT;

CREATE INDEX ix_nsi_git_file_state_last_commit_order
    ON nsi_document_git_file_state (git_link_id, file_path, last_committed_date, last_commit_row_id);
