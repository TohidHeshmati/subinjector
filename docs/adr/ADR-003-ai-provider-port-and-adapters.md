# ADR-003: Isolate AI providers behind an application port

* **Status:** Accepted
* **Date:** 2026-10-01

## Context

Subinjector is expected to use a local model first and may later support cloud or other local providers. Provider APIs, request options, and response formats differ. Application behavior should not become coupled to one vendor, and AI-generated content must be treated as untrusted data and validated. The initial development environment has successfully called Ollama locally with `qwen3:0.6b`; the application integration and enrichment result contract have not yet been implemented or specified.

## Decision

- Keep provider-specific communication behind an application-facing port that describes the capability the application needs, without exposing Ollama-specific request or response types.
- Implement the first provider as an Ollama adapter that translates between the application port and Ollama's local HTTP API.
- Treat provider adapters as interchangeable strategies for fulfilling that port. Select the configured implementation at application startup; do not add a runtime provider registry or user-facing provider selection until a requirement justifies it.
- Require each generation request to provide a positive maximum output-token count. The Ollama adapter maps it to `options.num_predict`; the one-cue enrichment use case currently sets the ceiling to 512 tokens.
- Parse and validate provider output at the application boundary before treating it as trusted domain data.
- Keep prompts, response contracts, and provider transport concerns separately understandable; define the concrete enrichment contract in a feature specification before implementing it.
- Do not silently fall back to a cloud provider when local-only operation is selected.

## Alternatives considered

* **Call Ollama directly from an application service:** Smallest first implementation, but couples use-case logic to Ollama and makes provider replacement and isolated testing harder.
* **Use one shared AI SDK or abstraction framework immediately:** May reduce future adapter work, but introduces dependency and abstraction choices before the required provider set and common capabilities are known.
* **Add a provider registry and select providers dynamically:** Supports runtime choice, but there is no current user or operational requirement for that complexity.

## Consequences

The initial integration will have a small application port and one Ollama adapter, which can be tested independently from provider-independent use-case behavior. The port includes a provider-side output ceiling, so adapters must map it to their provider's supported setting. Adding another provider will require an adapter and verification of whether it can satisfy the same port. The port must represent actual shared requirements rather than force providers into unsupported capabilities. This decision does not select a final prompt format, enrichment response schema, output language, or user-facing provider configuration.

## Revisit conditions

Reconsider the port's shape when the first enrichment specification defines its input and output, or when a second provider is approved and exposes a meaningful capability mismatch. Reconsider startup selection if a concrete requirement calls for per-user or per-request provider choice.
