# Feature: Ollama provider adapter

## Goal
Make the local Ollama model callable from the Spring application through a provider-independent application contract, so the provider boundary can be verified before subtitle enrichment is designed.

## Scope
- Define an application-facing text-generation port that accepts a prompt and returns generated text.
- Implement an Ollama adapter that calls the local `/api/chat` endpoint using the configured model.
- Configure the local development defaults to `http://localhost:11434` and `qwen3:0.6b`, while allowing environment-based overrides.
- Keep provider selection at application startup; Ollama is the only implementation in this slice.
- Add deterministic tests for request construction, response decoding, and provider errors, plus an opt-in local Ollama smoke test.

## Out of scope
- Subtitle enrichment behavior, prompt design, structured output, persistence, retries, cloud providers, runtime provider selection, and a public HTTP endpoint for arbitrary prompts.

## Requirements
- The application-facing port must not expose Ollama request or response types.
- The adapter must issue a non-streaming chat request and return only the assistant message content.
- The adapter must not log prompts or generated text.
- Ollama communication failures and unusable responses must become a clear application-level exception.
- The adapter must not fall back to a cloud provider.
- The optional smoke test must be skipped unless explicitly enabled with `OLLAMA_SMOKE_TEST=true`.

## Acceptance criteria
- The Spring application context can provide the provider port backed by the Ollama adapter.
- Deterministic tests verify the Ollama request and response behavior without a running Ollama service.
- With Ollama running and the configured model available, the opt-in smoke test can verify a local completion.
- The regular Gradle test suite does not require Ollama or network access.

## Testing notes
- Use Spring's mock HTTP server support for adapter tests.
- Run the opt-in smoke test only for local verification with the user's running Ollama service.

## Open questions
- What structured enrichment result, explanation language, and subtitle context should the first product use case require?
- Which application workflow should call the provider once that result contract is defined?
