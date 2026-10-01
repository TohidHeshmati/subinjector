# ADR-002: Parse SRT with an explicit parser contract and format-specific state machine

* **Status:** Accepted
* **Date:** 2026-10-01

## Context

Subtitle upload needs to turn SRT text into ordered entries while keeping parsing independent of the HTTP upload path. SRT has a simple but ordered structure: a cue number, a timing line, and one or more text lines. Malformed input must be rejected clearly. Other formats may be considered later, but no additional format is currently required.

## Decision

- Define a small `SubtitleParser` contract that accepts subtitle text and returns parsed `SubtitleEntry` values.
- Implement SRT rules in `SrtSubtitleParser` and use explicit parsing states for cue number, timing, and text.
- Keep transient parsing details, such as the current cue timing and parser state, private to the SRT parser implementation.
- Keep parser behavior deterministic and independently testable; do not make the parser depend on Spring MVC or file upload concerns.

## Alternatives considered

* **Parse the entire file with one regular expression:** Compact for idealized input, but harder to read and maintain for multiline cue text and useful malformed-input errors.
* **Put SRT parsing directly in the upload service:** Fewer types initially, but couples format rules to transport handling and makes direct parser testing or reuse harder.
* **Build a generalized parser framework for multiple formats now:** Could add extension points early, but there is only one required format and no validated need for a framework.

## Consequences

The upload flow depends on a format-neutral contract while the SRT-specific state machine owns the format rules. This makes parser behavior straightforward to test and leaves room for another implementation if another format is required. The contract does not yet define format discovery or parser selection, and the current parser supports the specified UTF-8 SRT input only.

## Revisit conditions

Reconsider this design if a second subtitle format is approved, if format selection becomes non-trivial, or if real SRT inputs require parsing behavior that makes the current state machine difficult to maintain.
