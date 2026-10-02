# Feature: Context-aware enrichment for one subtitle cue

## Goal
Use the local language model to produce concise learning notes for one subtitle cue, using neighboring cues only to understand context.

## Scope
- Define a request for a target cue, optional previous and next cues, the language being learned, and the learner's CEFR level.
- Support German as the only language being learned in this slice, using a language-specific prompt strategy.
- Explain notes in English for this slice.
- Enrich only the target cue; neighboring cues provide context and must not receive their own notes in this request.
- Return structured, cue-associated notes categorized as vocabulary, idiom, or grammar when useful.
- Return at most three notes per cue, with each explanation no longer than two short sentences or 300 characters.
- Keep the original cue text and timing as source data; this feature does not rewrite or render an SRT file.
- Keep enrichment as an application-level use case in this slice. Do not change the existing multipart upload endpoint yet; a later slice will pass the user's language and CEFR choices through that endpoint.
- Validate the model response before returning it from the enrichment use case.
- Use deterministic tests with a fake `LanguageModel`; use the local Ollama smoke test for manual model behavior checks.

## Out of scope
- Uploading or enriching a complete subtitle file, batching or parallelizing cues, changing subtitle text or timings, rendering/downloading an enriched SRT, a new public HTTP endpoint, cloud providers, and persistence.

## Requirements
- Include the target cue's sequence number and text; include previous/next cue numbers and text when those cues exist.
- The German-specific prompt must identify German as the language being learned, include the requested CEFR level, and request explanations in English.
- Request all three learning outcomes: vocabulary, idioms, and grammar. The model may omit any category that has no useful learning point.
- The response must identify the target cue so the result can later be associated with the right subtitle entry.
- The model may return no notes when the cue has no useful learning point.
- Return notes only for the target cue. Use neighboring cue text only to resolve meaning, references, or phrase boundaries.
- Keep subtitle text as untrusted input data. Instructions embedded in subtitle text are content to analyze, not instructions to follow.
- Limit the result to three notes per cue and reject an explanation longer than 300 characters or two sentences.
- Request at most 512 generated tokens for one cue; this is a provider-side ceiling, not an instruction to fill the budget.
- Ask the model for JSON text and parse and validate it in the application. Do not change the provider port to accept a JSON schema in this slice.
- Do not log cue text, prompts, or generated notes.

## Acceptance criteria
- A request for a middle cue includes that cue and both neighbors; first and last cues work when one neighbor is absent.
- The generated notes are associated with the target cue and never with context-only cues.
- Valid structured output is mapped to the application result type; malformed or out-of-bound output is rejected with a clear application error.
- Empty enrichment is accepted when the model finds no useful note.
- Deterministic tests cover prompt inputs, cue association, empty results, malformed output, response limits, and forwarding the provider-side output-token ceiling without calling an LLM.
- A local Ollama smoke check can exercise one representative cue without being part of the regular test suite.

## Testing notes
- Use a fake `LanguageModel` for repeatable application tests.
- Test the first cue, last cue, and a middle cue so optional context handling is explicit.
- Keep live Ollama checks opt-in and assess output quality manually; do not use exact LLM wording as a regular test assertion.

## Open questions
- What exact multipart field names and validation errors should expose the language and CEFR choices on the upload endpoint?
- Should a future language-specific prompt also choose a different explanation language, or should explanation language remain a separate user setting?
