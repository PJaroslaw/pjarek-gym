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

## Migration history

| Version | Change                                                                                       |
| ------- | -------------------------------------------------------------------------------------------- |
| V1      | Initial accounts, exercise catalog, routines, workouts, sets, and events                     |
| V2      | Temporary-password change requirement                                                        |
| V3      | Rep ranges, workout elapsed timing, and conversion of stored pound values to kilograms       |
| V4      | UUID identifiers for user-owned entities and references; exercise catalog IDs remain numeric |
| V5      | PostgreSQL `pg_trgm` extension for typo-tolerant exercise search                             |

The app has deployed databases. For a schema change, add the next Flyway migration and verify it from the current deployed schema with the schema-upgrade integration test. Do not rewrite an already released migration, drop a persistent volume, or reset data as a migration strategy. Back up a database before deploying a schema change.
