# Architecture Decision Records

An Architecture Decision Record (ADR) captures an important technical or architectural choice, the context behind it, and its consequences. ADRs help future contributors understand why a decision was made.

Write an ADR when a decision has meaningful long-term effects, constrains later work, or involves trade-offs that would otherwise be easy to forget. Routine implementation details do not need an ADR.

## Template

```markdown
# ADR-XXX: [Decision title]

* **Status:** Proposed | Accepted | Superseded | Deprecated
* **Date:** YYYY-MM-DD

## Context
What problem are we trying to solve? What constraints, requirements, or trade-offs matter?

## Decision
What have we decided?

## Alternatives considered

* **Option A:** Description, benefits, and drawbacks.
* **Option B:** Description, benefits, and drawbacks.

## Consequences
What are the positive and negative consequences of this decision? What limitations or future costs does it introduce?

## Revisit conditions

Under what circumstances should we reconsider this decision?
```

## Recorded decisions

- [ADR-001: Package organization](ADR-001-package-organization.md)
- [ADR-002: Parse SRT with an explicit parser contract and format-specific state machine](ADR-002-srt-parser-design.md)
- [ADR-003: Isolate AI providers behind an application port](ADR-003-ai-provider-port-and-adapters.md)
- [ADR-004: Use a separate enrichment prompt strategy per learning language](ADR-004-language-specific-enrichment-prompts.md)
