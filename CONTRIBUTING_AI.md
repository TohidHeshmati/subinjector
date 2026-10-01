# Guidelines for AI coding assistants

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
15. Keep commits small and focused; never commit unless explicitly asked.
16. Prefer deterministic tests over tests that require live LLM calls.
17. Treat AI output as untrusted input that must be validated.
18. Never silently send data to a cloud AI provider when a local-only mode is requested.
