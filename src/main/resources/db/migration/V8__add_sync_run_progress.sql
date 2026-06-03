ALTER TABLE nsi_sync_run
    ADD COLUMN IF NOT EXISTS progress_percent INTEGER NOT NULL DEFAULT 0;

UPDATE nsi_sync_run
SET progress_percent = 100
WHERE status IN ('SUCCESS', 'PARTIAL')
  AND progress_percent = 0;
