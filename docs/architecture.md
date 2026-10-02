# Architecture

## Current foundation

Subinjector is a Kotlin Spring Boot application built with Gradle Kotlin DSL. The repository currently contains an introductory hello endpoint and an SRT multipart upload flow that parses subtitle cues. It is a single deployable application; the code is organized by feature under `com.subinjector`.

## Current technology choices

- **Kotlin and Java 21:** Kotlin is the application language; Java 21 is the configured LTS JVM toolchain.
- **Spring Boot 4.1.1:** Provides the application runtime and Spring web support used by the current endpoints.
- **Gradle Kotlin DSL:** Keeps the build configuration in Kotlin, and the checked-in wrapper provides a repeatable Gradle entry point.
- **Spring MVC:** Handles the existing REST and multipart HTTP requests.
- **Automated tests:** Spring Boot's test support and MockMvc cover application, web, and parser behavior without requiring a live external service.
- **PostgreSQL and Flyway:** PostgreSQL is the selected relational database, with Flyway migrations under `src/main/resources/db/migration`. A Docker Compose service provides local PostgreSQL. Database-backed job processing is not implemented yet; see [ADR-005](adr/ADR-005-postgresql-and-flyway.md).
- **Feature-first packages:** The application entry point is in `com.subinjector`; endpoint and subtitle code live in their feature packages. Public top-level types have their own matching files. See [ADR-001](adr/ADR-001-package-organization.md).
- **SRT parser:** `SubtitleParser` separates parsing from the upload transport; `SrtSubtitleParser` uses ordered states for cue number, timing, and text. See [ADR-002](adr/ADR-002-srt-parser-design.md).

## AI provider direction

The AI integration has a provider-neutral `LanguageModel` port and an `OllamaLanguageModelAdapter` implementation. Spring selects the available provider adapter at startup; Ollama is the only provider currently registered. The adapter uses Spring AI's `OllamaChatModel`; `OLLAMA_BASE_URL` configures the local service and `OLLAMA_MODEL` selects its model. Each request also sets its output-token ceiling and asks Ollama for JSON when enrichment needs structured output. A one-cue enrichment use case builds a German-specific prompt from a Markdown classpath resource using Spring AI `PromptTemplate`, uses neighboring cues as context, and validates the concise result. Subtitle upload now parses the SRT and enriches cues sequentially, returning original cue data with per-cue enrichment status and notes. Failed cues are skipped without stopping later cues. See [ADR-003](adr/ADR-003-ai-provider-port-and-adapters.md), [ADR-004](adr/ADR-004-language-specific-enrichment-prompts.md), and [specifications 0004](specs/0004-ollama-provider-adapter.md), [0005](specs/0005-single-cue-enrichment.md), and [0006](specs/0006-sequential-subtitle-enrichment.md).

Ollama with `qwen3:0.6b` can be exercised through opt-in smoke tests. Enrichment output is parsed and validated in the application; native provider-constrained JSON Schema is not yet used. No cloud provider is selected.

## Guiding principles

- Start with a modular monolith.
- Keep business logic independent of AI providers.
- Validate external and AI-generated data.
- Prefer simple, testable solutions.
- Introduce infrastructure only when a specific requirement justifies it.
- Keep changes small and understandable.

## Future possibilities

Additional AI provider adapters, cloud providers, and other product capabilities may be considered when a requirement is defined. They are possibilities, not implemented components or current commitments. PostgreSQL, Flyway, and local Docker Compose setup are in place, but durable job repositories, background processing, and status/result APIs are not; see [ADR-005](adr/ADR-005-postgresql-and-flyway.md) and [specification 0007](specs/0007-database-backed-enrichment-jobs.md). Audiobook processing remains outside the initial scope. No message broker, deployment platform, or distributed architecture has been selected.
