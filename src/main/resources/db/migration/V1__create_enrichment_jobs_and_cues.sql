CREATE TABLE enrichment_job (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_filename TEXT NOT NULL,
    learning_language VARCHAR(32) NOT NULL,
    learner_level VARCHAR(2) NOT NULL,
    status VARCHAR(32) NOT NULL,
    total_cue_count INTEGER NOT NULL CHECK (total_cue_count >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE subtitle_cue (
    job_id UUID NOT NULL REFERENCES enrichment_job (id) ON DELETE CASCADE,
    sequence_number INTEGER NOT NULL CHECK (sequence_number > 0),
    start_time TEXT NOT NULL,
    end_time TEXT NOT NULL,
    original_text TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    enrichment JSONB,
    PRIMARY KEY (job_id, sequence_number)
);

CREATE INDEX subtitle_cue_job_status_idx
    ON subtitle_cue (job_id, status);
