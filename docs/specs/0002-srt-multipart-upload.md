# Feature: SRT multipart upload and parsing

## Goal
Accept an SRT subtitle file and parse its cues so the upload and parser can be verified.

## Scope
- Add `POST /api/subtitles/upload` accepting one multipart field named `file`.
- Parse SRT cue numbers, timing ranges, and text, including multiline cue text.
- Return the number of parsed cues and log a safe summary of the parse.
- Keep downloaded subtitle files in `local-test-data/subtitles/`, excluded from Git, for manual local checks.

## Out of scope
- Persisting uploads or parsed cues, subtitle enrichment, AI providers, asynchronous processing, and support for subtitle formats other than SRT.

## Requirements
- Keep parsing independent of Spring MVC so it can be reused and tested directly.
- Use an explicit parser contract and an SRT-specific implementation; the format-specific implementation uses ordered parsing states for cue number, timing, and text.
- Accept UTF-8 SRT content with LF or CRLF line endings.
- Reject empty files, non-SRT filenames, and malformed cues with a client error.
- Log file size, parsed cue count, sequence and timeline ranges, and elapsed time; do not log filenames or subtitle text.

## Acceptance criteria
- A valid multipart SRT upload returns HTTP 200 with its parsed cue count.
- Multiline cue text and common line endings parse correctly.
- Invalid input returns HTTP 400.
- A successful parse logs a structural summary without logging subtitle content or its filename.
- Parser behavior is covered by deterministic tests, and the multipart endpoint has a web-layer test.

## Testing notes
- Test the parser directly with valid, multiline, and malformed input.
- Exercise the multipart endpoint with MockMvc.

## Open questions
- Whether uploads should later be retained or processed asynchronously.
- Whether any subtitle formats or encodings beyond UTF-8 SRT should be supported.
