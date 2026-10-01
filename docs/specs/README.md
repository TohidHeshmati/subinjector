# Feature specifications

Write a small feature specification before implementation so the goal, boundaries, and observable acceptance criteria are clear. Name it `NNNN-short-title.md`, using the same work ID as its branch (for example, `0001-subtitle-parser.md` for branch `0001-feat-subtitle-parser`). Keep it proportional to the feature; record unresolved details as open questions rather than guessing. Use the specification to guide implementation and review. Implement and verify one small slice at a time, checking the result against the acceptance criteria before continuing.

## Template

```markdown
# Feature: Title

## Goal
What user need should this address?

## Scope
What will be included?

## Out of scope
What will not be included?

## Requirements
- ...

## Acceptance criteria
- ...

## Testing notes
What behavior needs verification?

## Open questions
- ...
```

Specifications are numbered by work item and kept small enough to guide one reviewable implementation slice. See [0001: Hello World endpoint](0001-hello-world-endpoint.md), [0002: SRT multipart upload](0002-srt-multipart-upload.md), [0003: SRT parser boundaries](0003-srt-parser-boundaries.md), and [0004: Ollama provider adapter](0004-ollama-provider-adapter.md).
