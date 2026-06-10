ALTER TABLE nsi_document_git_commit_file
    ADD COLUMN content_after_object_key TEXT,
    ADD COLUMN content_before_object_key TEXT;

ALTER TABLE nsi_document_git_file_state
    ADD COLUMN content_object_key TEXT;
