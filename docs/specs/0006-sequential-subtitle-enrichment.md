# Feature: Sequential subtitle enrichment

## Goal
Let a user upload an SRT file and receive its original cues with per-cue learning notes from the configured local language model.

## Scope
- Extend the existing SRT upload flow to require `learningLanguage` and `learnerLevel` multipart fields.
- Support the currently available learning language, German, and all existing CEFR levels.
- Parse the SRT, then enrich its cues one at a time in sequence, passing the previous and next cue as context where available.
- Preserve each original cue and its timing in the JSON response, with an enrichment status and notes attached to that cue.
- If a cue fails due to a language-model or enrichment-validation error, mark it skipped and continue with the next cue. Do not retry in this slice.
- Keep processing synchronous and sequential; at most one model request is active at a time.
- Keep the Ollama model loaded for five minutes after a request so sequential cue requests do not unload and reload it each time.
- Log cue sequence number and outcome without logging subtitle text, prompts, or generated notes. Log a concise file-level count and elapsed time.

## Out of scope
- Translating cues, rendering or downloading a modified SRT, asynchronous jobs, persistence, parallel cue processing, automatic retries, other subtitle formats, cloud providers, and user-selectable Ollama model settings.

## Requirements
- Keep the parser responsible only for parsing. Keep sequential processing in an enrichment use case and keep the controller limited to HTTP binding and delegation.
- The upload response must include each original cue and timing, its status (`SUCCEEDED` or `SKIPPED`), and its enrichment result when successful. A successful result with no notes must be distinguishable from a skipped cue.
- Include total, successful, and skipped cue counts in the response.
- On a per-cue `LanguageModelException` or `CueEnrichmentException`, retain the original cue, mark it skipped, and continue. File validation and subtitle parsing errors still fail the upload request.
- Do not log subtitle content, prompts, or generated notes. Log per-cue outcome at debug/warning level and aggregate counts and elapsed time at info level.
- Set Ollama `keep_alive` to `5m` for sequential requests; retain the existing per-cue output-token ceiling.
- Request JSON output through the provider-neutral language-model port; the Ollama adapter uses Spring AI's native Ollama JSON format.

## Acceptance criteria
- A multipart upload with `file`, `learningLanguage=GERMAN`, and a valid `learnerLevel` parses the file and returns a JSON result associated with every original cue.
- Middle cues are enriched with immediate neighbors; first and last cues work without a missing neighbor.
- Cues are sent to the model in file order, with no concurrent model requests.
- A failed cue is represented as skipped and does not prevent later cues from being enriched.
- Original sequence numbers, timing, and text remain unchanged in the response.
- Invalid language or CEFR values are rejected as a bad request; malformed SRT and invalid uploads retain their existing bad-request behavior.
- Deterministic tests cover the sequential use case, cue association, a skipped cue followed by success, a large synthetic cue list, multipart binding, and Ollama keep-alive request configuration. No live LLM is required for the regular test suite.
- Adapter tests verify Spring AI receives the configured output token limit and native JSON format for structured enrichment requests.

## Testing notes
- Use a fake `LanguageModel` to verify ordered, sequential calls and failure handling.
- Use MockMvc for multipart binding and response shape; do not call a live Ollama server from ordinary endpoint tests.
- Manually try the real 2,070-cue subtitle after the change and observe total elapsed time and skipped count. A synchronous request may take a long time; this slice does not promise a particular runtime.

## Open questions
- What response-time limit is acceptable for large uploads, and should a later slice move long jobs out of the synchronous HTTP request?
- Should later versions retry transient provider failures, and which failures should qualify?
- What policy should the future SRT assembler use to represent successful enrichment notes in a downloadable subtitle file?
