# Feature: Hello world endpoint

## Goal

Provide a minimal HTTP endpoint that demonstrates how the application exposes a response through Spring MVC.

## Scope

- `GET /api/hello`
- Return JSON with a single `message` field whose value is `Hello, world!`.

## Out of scope

- Health checks or operational status endpoints
- Personalized or configurable greetings
- Any subtitle-learning or AI behavior

## Requirements

- Use the existing Kotlin and Spring Boot application.
- Keep the response contract small and explicit.

## Acceptance criteria

- `GET /api/hello` responds with HTTP 200.
- The response is JSON and contains `"message": "Hello, world!"`.

## Testing notes

- Add a focused Spring MVC test for the route, status, and JSON body.
- Run the Gradle build and tests.

## Open questions

- None for this introductory endpoint.
