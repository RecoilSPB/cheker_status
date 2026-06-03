ALTER TABLE nsi_document_git_commit
    ADD COLUMN file_history_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN file_history_error TEXT,
    ADD COLUMN file_history_processed_at TIMESTAMPTZ;

UPDATE nsi_document_git_commit c
SET file_history_status = 'SUCCESS',
    file_history_error = NULL,
    file_history_processed_at = now()
WHERE EXISTS (
    SELECT 1
    FROM nsi_document_git_commit_file f
    WHERE f.commit_id = c.id
)
AND NOT EXISTS (
    SELECT 1
    FROM nsi_document_git_commit_file f
    WHERE f.commit_id = c.id
      AND f.content_fetch_status = 'FAILED'
);

CREATE INDEX ix_nsi_document_git_commit_file_history_status
    ON nsi_document_git_commit (document_git_link_id, file_history_status, committed_date);
