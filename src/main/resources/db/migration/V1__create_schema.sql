CREATE TABLE subtitle_document (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    filename TEXT NOT NULL,
    format VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE subtitle_cue (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL REFERENCES subtitle_document (id) ON DELETE CASCADE,
    sequence_number INTEGER NOT NULL CHECK (sequence_number > 0),
    start_ms INTEGER NOT NULL,
    end_ms INTEGER NOT NULL,
    original_text TEXT NOT NULL,
    CONSTRAINT subtitle_cue_document_sequence_key UNIQUE (document_id, sequence_number)
);

CREATE TABLE enrichment_job (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL REFERENCES subtitle_document (id) ON DELETE CASCADE,
    language VARCHAR(32) NOT NULL,
    level VARCHAR(2) NOT NULL,
    status VARCHAR(32) NOT NULL CHECK (status IN ('QUEUED', 'PROCESSING', 'COMPLETED')),
    cue_count INTEGER NOT NULL CHECK (cue_count >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE enrichment_task (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id UUID NOT NULL REFERENCES enrichment_job (id) ON DELETE CASCADE,
    cue_id UUID NOT NULL REFERENCES subtitle_cue (id) ON DELETE CASCADE,
    status VARCHAR(32) NOT NULL CHECK (status IN ('PENDING', 'PROCESSING', 'SUCCEEDED', 'SKIPPED')),
    result JSONB,
    error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT enrichment_task_job_cue_key UNIQUE (job_id, cue_id)
);

CREATE INDEX subtitle_cue_document_order_idx
    ON subtitle_cue (document_id, sequence_number);

CREATE INDEX enrichment_job_document_created_idx
    ON enrichment_job (document_id, created_at DESC);

CREATE INDEX enrichment_task_job_status_idx
    ON enrichment_task (job_id, status);

CREATE INDEX enrichment_task_pending_idx
    ON enrichment_task (status, created_at)
    WHERE status = 'PENDING';
