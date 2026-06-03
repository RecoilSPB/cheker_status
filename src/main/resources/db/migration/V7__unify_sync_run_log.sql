ALTER TABLE nsi_sync_run_error RENAME TO nsi_sync_run_log;

ALTER TABLE nsi_sync_run_log RENAME COLUMN error_stage TO stage;
ALTER TABLE nsi_sync_run_log RENAME COLUMN error_message TO message;

ALTER TABLE nsi_sync_run_log
    ADD COLUMN level VARCHAR(16) NOT NULL DEFAULT 'ERROR',
    ADD COLUMN details JSONB NOT NULL DEFAULT '{}'::jsonb;

CREATE INDEX ix_nsi_sync_run_log_run_created_id
    ON nsi_sync_run_log (sync_run_id, created_at, id);

CREATE INDEX ix_nsi_sync_run_log_run_level
    ON nsi_sync_run_log (sync_run_id, level);

CREATE INDEX ix_nsi_sync_run_log_run_stage
    ON nsi_sync_run_log (sync_run_id, stage);
