# Guidelines for AI coding assistants

## Working loop

For feature work, use this sequence:

- **Decision:** Identify whether a real product or architecture decision must be made. Record it as an ADR only when it has meaningful long-term effects or trade-offs; do not invent decisions for possible future work.
- **Specification:** Read or write a small feature specification before implementation.
- **Implementation:** Make one small, reviewable slice. Do not take an entire feature request away for a long, uninterrupted implementation.
- **Verification:** Run focused tests and other relevant verification, and report the actual results.
- **Owner review:** Present the change and explain it so the project owner can inspect it and confirm they understand it. Wait for review before starting another slice.
- **Commit and PR:** Commit only after the owner has reviewed the concrete change and explicitly authorized the commit. Submit changes through a pull request.
- **Learning note:** Add one only when the owner has identified a concrete lesson from the implementation; never invent learning on their behalf.

## Branch naming

Name each work branch `NNNN-type-short-title`, using a four-digit, zero-padded work ID, a short change type, and a kebab-case title. Start at `0001`; for each later pull request, use the next ID after the highest numbered branch or pull request title. Include the ID in the pull request title and, for feature work, in the specification filename. For example, `0001-feat-subtitle-parser` pairs with `docs/specs/0001-subtitle-parser.md` and a pull request title beginning with `0001`. Use `feat`, `fix`, `docs`, or `chore` for the type. The initial `setup/foundation` branch is a one-time bootstrap exception.

1. Read the relevant project documentation before making changes.
2. Implement only the task explicitly requested.
3. For feature work, require a small written specification first.
4. Make the smallest reasonable change.
5. Do not modify unrelated files.
6. Do not introduce dependencies without explaining why they are necessary.
7. Do not introduce abstractions without a demonstrated need.
8. Add or update tests when behavior changes.
9. Do not claim tests pass unless they have actually been run.
10. Explain important implementation decisions and assumptions.
11. Keep the code understandable to the project owner.
12. Stop and ask when requirements are ambiguous or conflict with existing decisions.
13. Do not expose secrets or sensitive data in source code, prompts, logs, or fixtures.
14. Do not make architectural changes without explaining the trade-offs and seeking approval.
15. Keep commits small and focused; commit only after owner review and explicit authorization.
16. Prefer deterministic tests over tests that require live LLM calls.
17. Treat AI output as untrusted input that must be validated.
18. Never silently send data to a cloud AI provider when a local-only mode is requested.
