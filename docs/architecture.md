# Architecture

## Current foundation

Subinjector is a Kotlin Spring Boot application built with Gradle Kotlin DSL. The repository currently contains an introductory hello endpoint and an SRT multipart upload flow that parses subtitle cues. It is a single deployable application; the code is organized by feature under `com.subinjector`.

## Current technology choices

- **Kotlin and Java 21:** Kotlin is the application language; Java 21 is the configured LTS JVM toolchain.
- **Spring Boot 4.1.1:** Provides the application runtime and Spring web support used by the current endpoints.
- **Gradle Kotlin DSL:** Keeps the build configuration in Kotlin, and the checked-in wrapper provides a repeatable Gradle entry point.
- **Spring MVC:** Handles the existing REST and multipart HTTP requests.
- **Automated tests:** Spring Boot's test support and MockMvc cover application, web, and parser behavior without requiring a live external service.
- **Feature-first packages:** The application entry point is in `com.subinjector`; endpoint and subtitle code live in their feature packages. Public top-level types have their own matching files. See [ADR-001](adr/ADR-001-package-organization.md).
- **SRT parser:** `SubtitleParser` separates parsing from the upload transport; `SrtSubtitleParser` uses ordered states for cue number, timing, and text. See [ADR-002](adr/ADR-002-srt-parser-design.md).

## AI provider direction

The AI integration now has a provider-neutral `LanguageModel` port and an `OllamaLanguageModelAdapter` implementation. Spring selects the available implementation at application startup; Ollama is the only provider currently registered. The adapter calls the local `/api/chat` endpoint, configured by `OLLAMA_BASE_URL` and `OLLAMA_MODEL`, and returns assistant text. See [ADR-003](adr/ADR-003-ai-provider-port-and-adapters.md) and [specification 0004](specs/0004-ollama-provider-adapter.md).

Ollama with `qwen3:0.6b` has been called manually through its local API, and an opt-in application smoke test is available for verifying the configured adapter against a running local service. This is provider connectivity only: no subtitle prompt, enrichment behavior, structured result contract, or cloud provider is implemented or selected.

## Guiding principles

- Start with a modular monolith.
- Keep business logic independent of AI providers.
- Validate external and AI-generated data.
- Prefer simple, testable solutions.
- Introduce infrastructure only when a specific requirement justifies it.
- Keep changes small and understandable.

## Future possibilities

Additional AI provider adapters, cloud providers, and other product capabilities may be considered when a requirement is defined. They are possibilities, not implemented components or current commitments. Audiobook processing remains outside the initial scope. No database, message broker, deployment platform, or distributed architecture has been selected.
