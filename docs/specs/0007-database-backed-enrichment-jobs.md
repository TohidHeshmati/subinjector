# Feature: Database-backed subtitle enrichment jobs

## Goal

Process subtitle enrichment as durable work that can outlive the upload request, while preserving each cue's outcome.

## Scope

- Persist an enrichment job and its parsed subtitle cues in PostgreSQL.
- Use Flyway migrations for schema changes.
- Process cues sequentially and persist each cue's success or skipped outcome as it completes.
- Continue to the next cue when an individual cue cannot be enriched, consistent with the current enrichment behavior.

## Out of scope

- Parallel cue processing or multiple worker instances.
- A message broker, webhook callbacks, or a distributed workflow engine.
- Automatic retries of failed cue enrichment.
- User accounts, collaboration, or long-term subtitle-library management.
- Translating subtitles or generating a rewritten SRT file.

## Requirements

- PostgreSQL is the persistence engine and Flyway owns schema migrations, as recorded in [ADR-005](../adr/ADR-005-postgresql-and-flyway.md).
- Provide a Docker Compose configuration and documented commands to run and stop PostgreSQL locally for development.
- Persist enough information to associate every cue outcome with its original job and cue sequence.
- Record per-cue success or skipped status and allow aggregate job progress to be determined.
- Keep model calls outside database transactions so a slow model request does not hold a database transaction open.
- Validate the model response before storing an enrichment result.
- Do not log full subtitle text, prompts, or generated explanations.

## Acceptance criteria

- An uploaded subtitle can be represented by a durable job with its parsed cues.
- Processing records each cue outcome so a process restart does not erase already completed work.
- A failed cue is recorded as skipped and does not prevent subsequent cues from being processed.
- Database schema creation and evolution use checked-in Flyway migrations.
- A developer can use the documented Docker Compose commands to start PostgreSQL and run the application against it locally.
- Automated tests verify persistence and job behavior without making live LLM calls.

## Testing notes

Use deterministic language-model test doubles. Decide whether persistence tests use an isolated PostgreSQL instance or another verified strategy before implementation; do not depend on a developer's existing local database contents.

## Open questions

- What exact job states are needed, and how are partial completion and terminal failure represented?
- How should clients learn about and retrieve job status and results: polling by job ID, a webhook, a live connection, or another approach?
- Should upload return `202 Accepted` with a job ID, and what should the existing upload endpoint return after this change?
- Should results be available while processing is still underway?
- What data should be retained, and for how long? How can users delete a job?
- How should a worker claim pending cues and recover work after a crash? The first implementation is expected to use one sequential worker, but the exact mechanism is undecided.
- What is the policy for a process restart: resume pending cues automatically or leave the job in a recoverable state for explicit action?
- Which PostgreSQL test setup best balances fidelity and ease of running tests locally and in CI?
