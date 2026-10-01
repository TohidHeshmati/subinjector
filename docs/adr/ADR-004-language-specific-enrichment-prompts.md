# ADR-004: Use a separate enrichment prompt strategy per learning language

* **Status:** Accepted
* **Date:** 2026-10-01

## Context

Language-learning explanations depend on the language being studied, while the user may also have a different proficiency level and explanation language. A single universal prompt risks accumulating language-specific conditionals and unclear instructions. The first implementation supports German only, but the prompt should have a clear place to add another learning language later.

## Decision

- Define a common prompt-building contract for cue enrichment.
- Provide one implementation per supported learning language and select the implementation using the request's learning language.
- Start with German only. The German prompt requests concise English explanations for vocabulary, idioms, and grammar.
- Store prompt text in a language-named Markdown resource and render its variables with Spring AI's `PromptTemplate`.
- Keep prompt selection independent of the LLM provider. Prompt strategies produce prompt content; the existing provider port sends it to the selected model.

## Alternatives considered

* **Use one universal multilingual prompt:** Fewer classes initially, but language-specific rules and examples can become conditional branches in one prompt and are harder to tune independently.
* **Use a prompt per model/provider:** Allows provider-specific prompt tuning, but duplicates language behavior across providers and couples product language rules to infrastructure choices.
* **Build prompt text with Kotlin string templates or a custom renderer:** Avoids a library dependency, but duplicates template handling that Spring AI already provides for classpath resources and variable rendering.

## Consequences

Each supported learning language can have an independently reviewed and tested Markdown prompt. Adding a language requires a resource, an implementation, and a selection entry. Spring AI's model module is added for resource-backed prompt rendering, without adding its provider-specific client or replacing the application's `LanguageModel` port. This adds a small amount of structure while German is the only supported language. Prompt text still cannot guarantee correct explanations; output shape and size are validated by the application, and quality requires evaluation with representative examples.

## Revisit conditions

Reconsider the strategy if multiple language prompts prove substantially identical, if prompt selection becomes difficult to maintain, or if Spring AI's templating dependency no longer provides enough value for the resource and rendering needs.
