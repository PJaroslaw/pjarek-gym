# Development

## Requirements

- Java 25
- PostgreSQL 18
- Node.js 22.22.1 or newer and npm for formatting
- Docker for the PostgreSQL-backed integration tests

For first-time setup and local PostgreSQL environment variables, follow [Local development in the README](../README.md#local-development). The application can run directly with `./gradlew bootRun`; Docker is not required when Java and PostgreSQL are available locally.

## Build and tests

Run the build and test tasks separately when debugging:

```sh
./gradlew --no-daemon bootJar
./gradlew --no-daemon test
./gradlew --no-daemon integrationTest
```

`test` runs unit tests. `integrationTest` runs PostgreSQL integration tests using Testcontainers and needs access to Docker. `./gradlew check` runs both test tasks.

Integration tests use a disposable PostgreSQL 18 container and do not use the app's Compose database volume. They exercise database migrations, route behavior, ownership checks, and full application flows. Keep unit tests and integration tests separate so CI logs make failures easy to locate.

## Formatting

Install development dependencies with `npm install`. The pre-commit hook formats staged HTML, JSON, JavaScript, Markdown, SQL, and YAML. Use:

```sh
npm run format
npm run format:all
npm run format:check
```

`format` handles HTML templates, `format:all` handles all supported files, and `format:check` checks HTML templates.

## Code layout

Kotlin sources are under `src/main/kotlin/com/pjarek/gym`. Keep domain and HTTP handling in focused controllers and services, use SQL through `JdbcTemplate`, and use imports instead of fully qualified names in code. Add happy-path and failure-path tests for changed behavior. See [Architecture](architecture.md) for the component map and [Data and migrations](data-and-migrations.md) for schema conventions.
