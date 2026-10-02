-- 1. Rename subtitle_document columns
ALTER TABLE subtitle_document
    RENAME COLUMN source_filename TO filename;

ALTER TABLE subtitle_document
    RENAME COLUMN source_format TO format;

-- 2. Convert subtitle_cue timestamps from TEXT to milliseconds
ALTER TABLE subtitle_cue
    ADD COLUMN start_ms INTEGER,
    ADD COLUMN end_ms INTEGER;

UPDATE subtitle_cue SET
    start_ms = (
        split_part(start_time, ':', 1)::int * 3600000 +
        split_part(start_time, ':', 2)::int * 60000 +
        split_part(split_part(start_time, ':', 3), ',', 1)::int * 1000 +
        split_part(split_part(start_time, ':', 3), ',', 2)::int
    ),
    end_ms = (
        split_part(end_time, ':', 1)::int * 3600000 +
        split_part(end_time, ':', 2)::int * 60000 +
        split_part(split_part(end_time, ':', 3), ',', 1)::int * 1000 +
        split_part(split_part(end_time, ':', 3), ',', 2)::int
    );

ALTER TABLE subtitle_cue
    ALTER COLUMN start_ms SET NOT NULL,
    ALTER COLUMN end_ms SET NOT NULL,
    DROP COLUMN start_time,
    DROP COLUMN end_time;

-- 3. Clean up cue_enrichment (rename, drop composite FKs, add simple FKs, remove document_id)
--    Drop composite FKs first since they depend on composite UNIQUEs on the parent tables
ALTER TABLE cue_enrichment
    DROP CONSTRAINT cue_enrichment_cue_document_fkey,
    DROP CONSTRAINT cue_enrichment_job_document_fkey;

ALTER TABLE cue_enrichment
    RENAME TO enrichment_task;

ALTER TABLE enrichment_task
    DROP COLUMN document_id,
    ADD COLUMN error TEXT,
    ADD CONSTRAINT enrichment_task_job_fkey
        FOREIGN KEY (job_id) REFERENCES enrichment_job (id) ON DELETE CASCADE,
    ADD CONSTRAINT enrichment_task_cue_fkey
        FOREIGN KEY (cue_id) REFERENCES subtitle_cue (id) ON DELETE CASCADE;

ALTER TABLE enrichment_task RENAME COLUMN enrichment TO result;

ALTER INDEX cue_enrichment_job_cue_key RENAME TO enrichment_task_job_cue_key;

-- 4. Now that composite FKs are gone, drop composite UNIQUEs on parent tables
ALTER TABLE subtitle_cue
    DROP CONSTRAINT subtitle_cue_id_document_id_key;

ALTER TABLE enrichment_job
    DROP CONSTRAINT enrichment_job_id_document_id_key;

-- 5. Rename enrichment_job columns
ALTER TABLE enrichment_job
    RENAME COLUMN learning_language TO language;

ALTER TABLE enrichment_job
    RENAME COLUMN learner_level TO level;

ALTER TABLE enrichment_job
    RENAME COLUMN total_cue_count TO cue_count;

-- 6. Rebuild indexes for the renamed table
DROP INDEX cue_enrichment_job_status_idx;
DROP INDEX cue_enrichment_pending_idx;

CREATE INDEX enrichment_task_job_status_idx
    ON enrichment_task (job_id, status);

CREATE INDEX enrichment_task_pending_idx
    ON enrichment_task (status, created_at)
    WHERE status = 'PENDING';
