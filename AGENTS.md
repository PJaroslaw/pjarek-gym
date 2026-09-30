# Instructions for coding agents

`README.md` is the human-facing project entry point. Before changing code, configuration, schema, or user-visible behavior, check the relevant references in [`docs/`](docs/) and `README.md`. Update every affected document as part of the change so the docs stay aligned with the app. README covers human setup and top-level usage; the focused docs hold the deeper technical details.

## Project constraints

- This is a deployed Kotlin and Spring Boot application using PostgreSQL, Thymeleaf, HTMX, and Flyway. The main Kotlin package is `com.pjarek.gym`.
- Preserve deployed data. Make schema changes with a new forward Flyway migration; do not edit migrations that may already have run or delete database volumes as a substitute for migration work.
- User, routine, routine-day, routine-item, workout, workout-exercise, workout-set, and workout-event IDs are UUIDs. Exercise catalog IDs are numeric and are the exception.
- Store all workout weights in kilograms. Convert to or from pounds at the application boundary according to the user's preference.
- Avoid backwards-compatibility fallbacks or scaffolding for superseded behavior, and do not add comments that merely narrate how a new implementation differs from an old one. Preserve existing deployed data through migrations rather than compatibility code.

## Product behavior and UI

- Keep behavior consistent with [workout behavior](docs/workouts.md) and the deployed schema in [data and migrations](docs/data-and-migrations.md).
- Every delete action is a dangerous action: use the strong red treatment in light, dark, and system themes. Place deletion controls at the bottom of the entity they delete. In the routine editor, an exercise removal control may sit next to that exercise's edit control.
- A completed workout summary is read-only except for deleting the workout. Recent sessions on the overview do not have delete controls; history provides Show and Delete actions.
- Keep mobile layouts usable and avoid controls that are visually easy to miss.

## Implementation and verification

- Use imports; do not write fully qualified class names inline.
- Keep classes and functions focused and reasonably sized. Split code at natural responsibilities instead of creating many small helpers or classes.
- Add tests for changed behavior, including successful and failure paths. Keep unit tests and PostgreSQL integration tests in their separate Gradle tasks so failures are easy to locate.
- Use `./gradlew test` for unit tests and `./gradlew integrationTest` for integration tests. Integration tests use Testcontainers and need Docker. Run the formatter for supported file types when changing them.
- Update `README.md` when human-facing setup or top-level usage guidance changes; put detailed technical references in the relevant `docs/*.md` file.

## Git workflow

- Make changes on a topic branch targeting `main`; do not commit directly on `main`.
- Commit and push only when the user explicitly asks. Every commit must be signed. Never change Git configuration or signing keys to make a Git operation work; stop and ask if signing is unavailable. Use a descriptive commit subject and body covering the material changes.
- Do not force-push unless the user explicitly instructs it. If a push would require rewriting remote history, stop and ask first.
- When asked to create a pull request, create it as a draft targeting `main` and leave it in draft for the user.
- After a remote PR merge is confirmed, fetch the remote default branch, switch to its local branch, and fast-forward it. Then delete the merged PR branch locally with `git branch -d`. Leave remote branches and unrelated local branches untouched, and preserve any uncommitted work.
