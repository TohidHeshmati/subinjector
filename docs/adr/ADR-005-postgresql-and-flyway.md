# ADR-005: Use PostgreSQL with Flyway for persistence

* **Status:** Accepted
* **Date:** 2026-10-01

## Context

Subtitle enrichment can take a long time because each cue is sent to a language model. The application needs durable storage for submitted jobs, parsed cues, and per-cue outcomes so work and progress can be represented independently of the upload request. Database-backed processing has been selected for the upcoming enrichment workflow.

The project needs a relational database that works well with the Spring Boot application and supports versioned schema changes as the project evolves.

## Decision

Use PostgreSQL as the relational database and Flyway to apply ordered, versioned database migrations. Database schema changes will be represented in source control as Flyway migrations rather than relying on automatic ORM schema generation.

This decision selects the database and migration mechanism. The source-document, cue, enrichment-job, and per-cue outcome relationships are recorded in [ADR-006](ADR-006-subtitle-documents-and-enrichment-runs.md). The job API, detailed status vocabulary, retention policy, and deployment arrangement remain open.

## Alternatives considered

* **H2:** Easy to start locally and useful for isolated tests, but it would not provide the same database engine as the intended persistent application environment.
* **PostgreSQL with Hibernate-managed schema changes:** Familiar Spring integration, but automatic schema generation does not provide the explicit, ordered migration history desired for this project.
* **No database:** Keeps the current application simpler, but cannot provide durable job and cue state across application restarts.

## Consequences

* PostgreSQL adds a local development dependency and requires connection configuration.
* Flyway migrations make schema evolution reviewable and repeatable, while requiring migration files to be maintained with schema changes.
* Tests will need a deliberate database strategy; they should remain deterministic and should not depend on a developer's local database state.
* The database choice does not itself provide background processing, retries, progress APIs, or job recovery; those behaviors require separate design.

## Revisit conditions

Reconsider if deployment constraints make PostgreSQL unavailable, the persistence requirements change materially, or the operational cost is disproportionate to the project.
