# ADR-006: Separate subtitle documents from enrichment runs

* **Status:** Accepted
* **Date:** 2026-10-02

## Context

The same subtitle source may be enriched for several learning languages or CEFR levels. If cues belong directly to an enrichment job, each run duplicates the source cues and makes it harder to compare or retrieve outcomes for the same cue across runs. Storing an enrichment result directly on a cue also cannot represent multiple independent outcomes for one source cue.

PostgreSQL and Flyway have been selected for durable processing. The current V1 migration has already been added; the next schema change must preserve its migration history.

## Decision

- Represent the uploaded subtitle as a `subtitle_document` with source-file metadata and parsed cues.
- Make each `subtitle_cue` belong to one document. Its sequence number is unique within that document.
- Represent each requested enrichment run as an `enrichment_job` referencing one document and recording the run's learning language, learner level, and lifecycle state.
- Store each per-cue outcome in a `cue_enrichment` record that references both the job and the original cue. Allow at most one such record per `(job_id, cue_id)`.
- Let one document have multiple jobs. A new language or learner level creates a separate job and separate cue-enrichment outcomes while reusing the document's cues.
- Use PostgreSQL as the initial work queue. One worker polls and processes a job's cue records sequentially. Add a message broker only if measured workload or deployment needs justify it.
- Introduce this schema through a new Flyway migration after V1; do not modify an applied migration.

## Alternatives considered

* **Keep cues and results directly under each job:** Simpler for one enrichment run, but duplicates parsed cues for each language or level and entangles source data with processing history.
* **Store one mutable enrichment result on each cue:** Simple to query for one run, but a later language or level would overwrite or mix the earlier result.
* **Reparse the file for every language or level:** Avoids persisting a reusable source document, but repeats parsing and makes outcomes harder to relate to the same source.
* **Add a message broker immediately:** Supports richer delivery and more consumers, but adds infrastructure before throughput or multi-worker requirements exist.

## Consequences

- Parsed source cues are stored once and can be reused by several enrichment jobs.
- A cue's enrichment history is represented by joining its cue-enrichment records to their jobs, which identify the language and learner level.
- The model requires separate persistence records for source documents, cues, jobs, and per-cue outcomes.
- The first worker remains sequential and database-backed; polling and restart recovery behavior must be specified and tested.
- The choice of whether to retain the original file bytes remains open.
- Schema changes must be added in a new Flyway migration after V1.

## Revisit conditions

Reconsider the worker mechanism if measured queue delay or processing throughput requires multiple consumers or a message broker. Reconsider document retention if re-parsing, deletion, or storage requirements become clear. Reconsider result reuse if repeated identical enrichment jobs create unnecessary model work.
