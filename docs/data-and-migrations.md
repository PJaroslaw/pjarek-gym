# Data and migrations

PostgreSQL is the source of truth for accounts, routines, workouts, sets, user preferences, and the searchable exercise catalog. Flyway applies the versioned SQL files in `src/main/resources/db/migration` on application startup.

## Main records

| Table              | Purpose                                          | Identifier                                  |
| ------------------ | ------------------------------------------------ | ------------------------------------------- |
| `app_user`         | Accounts, roles, password state, and preferences | UUID                                        |
| `routine`          | User-owned training plan                         | UUID                                        |
| `routine_day`      | Ordered day within a routine                     | UUID                                        |
| `routine_item`     | Planned exercise within a day                    | UUID                                        |
| `workout`          | Active or completed session                      | UUID                                        |
| `workout_exercise` | Exercise and plan snapshot within a session      | UUID                                        |
| `workout_set`      | Logged repetitions and weight                    | UUID                                        |
| `workout_event`    | Pause and resume event history                   | UUID                                        |
| `exercise`         | Upstream exercise catalog record                 | Numeric ID; `source_id` is the upstream key |

User-owned records reference their owner and cascade when that user is deleted. Routine days and items belong to routines, workout exercises belong to workouts, and workout sets belong to workout exercises. Deleting a workout removes its exercises, sets, and events. Exercise catalog rows remain separate from user-owned IDs so imported exercise identifiers stay aligned with upstream data.

Routine and workout exercise rows keep the planned set count, rep range, and rest duration. A workout exercise also stores the instructions used for that session. These snapshots preserve context if the exercise catalog or routine changes later.

## Canonical values

Set weights are stored as kilograms regardless of the user's selected display unit. `app_user.weight_unit` controls conversion for input and display. Theme preference is stored per user as `LIGHT`, `DARK`, or `SYSTEM`.

## Migration naming

Name versioned migrations `V<project-version>_<three-digit-sequence>__description.sql`, using the project version set in `build.gradle.kts`. For example, the first migration for project version `0.1.0` is `V0.1.0_001__add_training_note.sql`; later migrations in that project version increment the sequence. Start again at `001` when the project version increases. Flyway normalizes the underscore separator to a dot in its stored version, so that filename is recorded as `0.1.0.001`. Keep project versions increasing and migration versions unique.

Descriptions explain the change; versions determine execution order. Once a migration is released, keep its filename and SQL unchanged.

The app has deployed databases. For a schema change, add the next Flyway migration and verify it from the current deployed schema with the schema-upgrade integration test. Do not rewrite an already released migration, drop a persistent volume, or reset data as a migration strategy. Back up a database before deploying a schema change.
