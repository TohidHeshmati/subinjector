# Architecture

## Initial foundation

The repository starts as a minimal Kotlin application on Spring Boot, built with Gradle Kotlin DSL. It contains an application entry point and a smoke test that checks Spring can create the application context. No product behavior or infrastructure has been added.

## Current choices

- **Kotlin** is the primary language, with Java 21 as the JVM toolchain. This provides a current LTS Java baseline while keeping application code concise and type-safe.
- **Spring Boot 4.1.1** provides the application runtime and dependency conventions, with a compatible stable Kotlin toolchain.
- **Gradle Kotlin DSL** keeps the build configuration in Kotlin and the checked-in Gradle wrapper makes the selected Gradle version repeatable.
- **Minimal dependencies** keep the initial build focused on starting the app and running its basic test.

## Guiding principles

- Start with a modular monolith.
- Keep business logic independent of AI providers.
- Validate external and AI-generated data.
- Prefer simple, testable solutions.
- Introduce infrastructure only when a specific requirement justifies it.
- Keep changes small and understandable.

## Future possibilities

Multiple AI providers and local model support may be considered when requirements define how they should work. No provider, database, messaging system, deployment platform, or wider architecture has been selected yet.
