# Architecture

PJarek Gym Log is a server-rendered web application. Kotlin and Spring Boot handle HTTP requests and authentication; PostgreSQL stores accounts and training data; Thymeleaf renders pages and HTMX requests replace page fragments for interactive updates. A small amount of browser JavaScript handles timers, theme selection, and navigation behavior.

## Request and data flow

Controllers in `src/main/kotlin/com/pjarek/gym` handle page and form routes. They use `JdbcTemplate` for SQL and return Thymeleaf template names. `UserAccess` resolves the signed-in account and checks ownership before user data is read or changed. Flyway applies database schema changes when the application starts.

Templates live in `src/main/resources/templates`. Page templates compose fragments from `templates/fragments`; an `HX-Request` can return a fragment instead of a complete page. CSS and JavaScript are served from `src/main/resources/static/assets`. HTMX is bundled locally rather than loaded from a CDN.

## Main components

| Area                  | Code                                                                                                          | Responsibility                                                                      |
| --------------------- | ------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------- |
| Accounts and access   | `SecurityConfig`, `UserAccess`, `AccountController`, `AdminController`                                        | Form login, user preferences, password changes, and admin account actions           |
| Routines and workouts | `RoutineController`, `WorkoutController`, `DashboardController`                                               | Plans, training sessions, set logging, timers, overview, and history                |
| Exercise catalog      | `ExerciseLibraryController`, `ExerciseSearchService`, `ExerciseCatalogImporter`, `ExerciseDatasetSyncService` | Catalog search, catalog import, and local image synchronization                     |
| Views and assets      | `templates/`, `static/assets/`                                                                                | Server-rendered pages, partial updates, styling, and small client-side interactions |
| Database              | `db/migration/`                                                                                               | Versioned PostgreSQL schema managed by Flyway                                       |

## Authentication and authorization

Login, logout, and static assets are public. Other application routes require authentication, and `/admin/**` routes require the admin role. User-owned routines and workouts are checked against the authenticated user's UUID. Admin inspection pages show another user's data read-only; account administration is handled separately.

Spring Security protects POST forms with CSRF tokens. A user given a temporary password must change it before accessing the rest of the app. Passwords have no length cap in the application and are stored through the configured password encoder.

For schema details and migration rules, see [Data and migrations](data-and-migrations.md). For routines, sets, and sessions, see [Workout behavior](workouts.md).
