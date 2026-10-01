# Subinjector

Subinjector is a personal project exploring an AI-assisted way to learn languages from subtitles. The planned experience will help learners understand vocabulary, idioms, and grammar in context.

## Status

**Foundation only.** The application currently contains a minimal Kotlin and Spring Boot setup, with no product features implemented.

## Development goals

- Demonstrate modern Kotlin, Java, and Spring Boot backend engineering.
- Learn practical AI engineering, testing, and reliable system design as the project needs them.
- Keep the code understandable and build the portfolio project through small increments.

## Development workflow

Work in short iterations: record a consequential product or architecture decision when one is actually made, write a small feature specification, implement one reviewable slice, run focused tests and verification, review the change for understanding, then commit it on a branch and submit it through a pull request. Add a learning note only when there is a concrete lesson from the work.

AI assistance should stay within one small implementation slice at a time. The project owner reviews the result before work moves to the next slice. ADRs capture real decisions; they are not a place for speculative future choices. See [the AI contribution guide](CONTRIBUTING_AI.md) for the branch naming convention, and see [the specification guide](docs/specs/README.md), [ADR guide](docs/adr/README.md), and [learning notes guide](docs/learnings/README.md).

## Requirements

- Java 21 (LTS)

The Gradle wrapper is included, so a separate Gradle installation is not required.

## Build, test, and run

From the project root:

```bash
./gradlew build
./gradlew test
./gradlew bootRun
```

The foundation exposes one introductory endpoint:

```bash
curl http://localhost:8080/api/hello
# {"message":"Hello, world!"}
```

Windows users can use `gradlew.bat`. No subtitle-learning functionality is implemented yet.

Features will be added incrementally, with requirements and design decisions documented as they are established.
