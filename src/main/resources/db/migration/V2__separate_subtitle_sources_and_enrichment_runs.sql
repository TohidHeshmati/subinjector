CREATE TABLE subtitle_document (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_filename TEXT NOT NULL,
    source_format VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE enrichment_job
    ADD COLUMN document_id UUID DEFAULT gen_random_uuid();

INSERT INTO subtitle_document (id, source_filename, source_format, created_at)
SELECT document_id, source_filename, 'SRT', created_at
FROM enrichment_job;

ALTER TABLE enrichment_job
    ALTER COLUMN document_id SET NOT NULL,
    ALTER COLUMN document_id DROP DEFAULT,
    ADD CONSTRAINT enrichment_job_document_id_fkey
        FOREIGN KEY (document_id) REFERENCES subtitle_document (id) ON DELETE CASCADE,
    ADD CONSTRAINT enrichment_job_id_document_id_key UNIQUE (id, document_id);

ALTER TABLE subtitle_cue
    ADD COLUMN id UUID DEFAULT gen_random_uuid(),
    ADD COLUMN document_id UUID;

UPDATE subtitle_cue cue
SET document_id = job.document_id
FROM enrichment_job job
WHERE cue.job_id = job.id;

ALTER TABLE subtitle_cue
    ALTER COLUMN id SET NOT NULL,
    ALTER COLUMN id DROP DEFAULT,
    ALTER COLUMN document_id SET NOT NULL,
    DROP CONSTRAINT subtitle_cue_pkey,
    DROP CONSTRAINT subtitle_cue_job_id_fkey,
    ADD CONSTRAINT subtitle_cue_pkey PRIMARY KEY (id),
    ADD CONSTRAINT subtitle_cue_document_id_fkey
        FOREIGN KEY (document_id) REFERENCES subtitle_document (id) ON DELETE CASCADE,
    ADD CONSTRAINT subtitle_cue_id_document_id_key UNIQUE (id, document_id),
    ADD CONSTRAINT subtitle_cue_document_sequence_key UNIQUE (document_id, sequence_number);

CREATE TABLE cue_enrichment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id UUID NOT NULL,
    document_id UUID NOT NULL,
    cue_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL CHECK (status IN ('PENDING', 'PROCESSING', 'SUCCEEDED', 'SKIPPED')),
    enrichment JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT cue_enrichment_job_document_fkey
        FOREIGN KEY (job_id, document_id)
        REFERENCES enrichment_job (id, document_id) ON DELETE CASCADE,
    CONSTRAINT cue_enrichment_cue_document_fkey
        FOREIGN KEY (cue_id, document_id)
        REFERENCES subtitle_cue (id, document_id) ON DELETE CASCADE,
    CONSTRAINT cue_enrichment_job_cue_key UNIQUE (job_id, cue_id)
);

INSERT INTO cue_enrichment (job_id, document_id, cue_id, status, enrichment)
SELECT cue.job_id,
       cue.document_id,
       cue.id,
       CASE cue.status
           WHEN 'SUCCEEDED' THEN 'SUCCEEDED'
           WHEN 'SKIPPED' THEN 'SKIPPED'
           ELSE 'PENDING'
       END,
       cue.enrichment
FROM subtitle_cue cue;

UPDATE enrichment_job
SET total_cue_count = (
    SELECT count(*)
    FROM subtitle_cue cue
    WHERE cue.document_id = enrichment_job.document_id
),
status = CASE
    WHEN status = 'COMPLETED' THEN 'COMPLETED'
    ELSE 'QUEUED'
END;

DROP INDEX subtitle_cue_job_status_idx;

ALTER TABLE subtitle_cue
    DROP COLUMN job_id,
    DROP COLUMN status,
    DROP COLUMN enrichment;

ALTER TABLE enrichment_job
    DROP COLUMN source_filename,
    ADD CONSTRAINT enrichment_job_status_check
        CHECK (status IN ('QUEUED', 'PROCESSING', 'COMPLETED'));

CREATE INDEX subtitle_cue_document_order_idx
    ON subtitle_cue (document_id, sequence_number);

CREATE INDEX enrichment_job_document_created_idx
    ON enrichment_job (document_id, created_at DESC);

CREATE INDEX cue_enrichment_job_status_idx
    ON cue_enrichment (job_id, status);

CREATE INDEX cue_enrichment_pending_idx
    ON cue_enrichment (status, created_at)
    WHERE status = 'PENDING';
