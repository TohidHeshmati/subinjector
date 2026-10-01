# Feature: SRT parser boundary coverage

## Goal
Make the current SRT parser's important invalid-input behavior explicit and repeatable before other features depend on it.

## Scope
- Add deterministic parser tests for empty/no-cue input, malformed cue numbers, missing timing, invalid clock fields, and non-increasing cue times.
- Add upload tests for empty and structurally invalid SRT files returning HTTP 400.
- Fix implementation behavior only if these tests expose a defect.

## Out of scope
- New SRT features, support for other subtitle formats or encodings, upload size limits, changes to cue numbering policy, persistence, and AI processing.

## Requirements
- Keep tests independent of downloaded subtitle files and external services.
- Preserve current valid-input behavior, including LF, CRLF, and multiline cue text.
- Do not add dependencies.

## Acceptance criteria
- Each specified invalid parser case is covered by a deterministic unit test.
- Empty and malformed multipart uploads return HTTP 400.
- The full Gradle build passes.

## Testing notes
- Test each invalid structure directly against `SrtSubtitleParser`.
- Exercise HTTP status mapping through MockMvc.

## Open questions
- Whether cue numbers must be unique and sequential; current scope does not change that policy.
- What upload size limit should apply when uploads are exposed beyond local development.
