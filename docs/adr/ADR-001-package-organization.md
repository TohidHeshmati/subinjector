# ADR-001: Organize code by feature and type

* **Status:** Accepted
* **Date:** 2026-10-01

## Context

The first REST endpoint placed both its controller and response type in the root package and the same Kotlin file. As the project grows, code needs a predictable home that keeps related feature code together without creating a large package structure before it is needed.

## Decision

- Keep the Spring Boot application entry point in the base package `com.subinjector`.
- Organize application code under feature packages, such as `com.subinjector.hello`.
- Put each public top-level type in its own Kotlin file with a matching name.
- Keep closely related types in the same feature package. Add subpackages for roles such as API, domain, or infrastructure only when the feature has distinct responsibilities that justify them.

## Alternatives considered

* **Keep all code in the base package and co-locate related types in one file:** Lowest initial structure cost, but the root package and files become crowded as features are added.
* **Organize all code into global technical-layer packages:** Makes types of a particular role easy to find, but splits each feature across the package tree and introduces structure before the project needs it.

## Consequences

Feature code is easier to locate and each public type has a clear source file. Small features will have a few extra files, and moving a type between feature packages later may require updating imports and tests. The convention does not require empty packages or a full layered architecture.

## Revisit conditions

Reconsider the package layout if feature boundaries, team ownership, or distinct technical responsibilities make the current feature-first grouping difficult to navigate.
