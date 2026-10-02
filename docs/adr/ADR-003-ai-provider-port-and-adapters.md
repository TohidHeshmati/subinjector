# ADR-003: Isolate AI providers behind an application port

* **Status:** Accepted
* **Date:** 2026-10-01

## Context

Subinjector is expected to use a local model first and may later support cloud or other local providers. Provider APIs, request options, and response formats differ. Application behavior should not become coupled to one vendor, and AI-generated content must be treated as untrusted data and validated. The project has an Ollama text-generation integration and is adding structured cue enrichment.

## Decision

- Keep provider-specific communication behind an application-facing port that describes the capability the application needs, without exposing Ollama-specific request or response types.
- Implement the first provider as an Ollama adapter backed by Spring AI's `OllamaChatModel`. Keep Spring AI and Ollama types inside that adapter and its configuration.
- Treat provider adapters as interchangeable strategies for fulfilling that port. Select the configured implementation at application startup; do not add a runtime provider registry or user-facing provider selection until a requirement justifies it.
- Require each generation request to provide a positive maximum output-token count. The Ollama adapter maps it to `options.num_predict`; the one-cue enrichment use case currently sets the ceiling to 512 tokens.
- Represent the requested output format with a provider-neutral application type. Map JSON output requests to Spring AI's native Ollama JSON format; do not expose Spring AI or Ollama option types through the application port.
- Keep the Ollama model loaded for five minutes after each request by setting `keep_alive` to `5m`. This avoids repeated model loads during sequential cue enrichment while allowing the model to unload after an idle period.
- Parse and validate provider output at the application boundary before treating it as trusted domain data.
- Keep prompts, response contracts, and provider transport concerns separately understandable; define the concrete enrichment contract in a feature specification before implementing it.
- Do not silently fall back to a cloud provider when local-only operation is selected.

## Alternatives considered

* **Call Ollama directly from an application service:** Smallest first implementation, but couples use-case logic to Ollama and makes provider replacement and isolated testing harder.
* **Build the Ollama HTTP request directly with Spring `RestClient`:** Small and gives precise control, but makes the project responsible for mapping Ollama's request and response format as the API evolves.
* **Use one shared AI SDK or abstraction framework immediately across application code:** May reduce future adapter work, but would couple use cases to a framework and choose common abstractions before the required provider set is known.
* **Add a provider registry and select providers dynamically:** Supports runtime choice, but there is no current user or operational requirement for that complexity.

## Consequences

The application keeps a small provider-neutral port, while Spring AI reduces Ollama-specific HTTP and option-mapping code in the Ollama adapter. This adds a Spring AI Ollama integration dependency and means its model API and options evolve with Spring AI versions. The port includes an output-token ceiling and a requested output format, so each provider adapter must map those capabilities or report that it cannot support them. Ollama may keep the local model in memory for up to five idle minutes, trading temporary memory use for avoiding reloads between sequential cue requests. Adding another Ollama model can be done through model configuration; adding another provider still requires a separate adapter. The port must represent actual shared requirements rather than force providers into unsupported capabilities. Application code continues to validate generated content.

## Revisit conditions

Reconsider the port's shape when a second provider is approved and exposes a meaningful capability mismatch. Reconsider model keep-alive duration if measurements show memory pressure or repeated loading. Reconsider startup selection if a concrete requirement calls for per-user or per-request provider choice.
