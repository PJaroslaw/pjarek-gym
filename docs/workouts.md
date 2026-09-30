# Workout behavior

## Exercise library and search

The catalog is imported from `yuhonas/free-exercise-db`. Exercise IDs are numeric catalog identifiers; `source_id` identifies the upstream record. The exercise picker searches names. The full library searches names, primary-muscle metadata, and equipment. Search terms may be entered in any order and tolerate misspellings through PostgreSQL's `pg_trgm` extension.

## Routines

A routine contains named days, and each day contains ordered exercises. A plan records a set count, minimum and maximum rep counts, and rest duration. Creating a routine does not add a day automatically. Starting a day creates a workout with copies of that day's exercise plan and instruction text, so later plan edits do not rewrite the workout record.

Users can also start an ad hoc workout. Starting one from an exercise detail page places that exercise into the new workout and selects it immediately. An exercise added to an active workout becomes the selected exercise.

## Logging and progression

Each logged set stores repetitions and weight. For a planned exercise, the rep field starts at the plan's maximum reps. Weight starts at zero when there is no previous set, then uses the latest set from the current workout or the most recent completed workout for the same exercise. Weight is stored in kilograms; the user's KG or LB preference controls display and input conversion.

The workout page shows completed sets separately from the next set to log. Exercise instructions and images can be expanded when needed. The user can move between exercises without losing the current selection when pausing or resuming.

## Timers and completion

An active workout has one elapsed-time clock. Paused time is excluded, and finishing the workout stores its elapsed duration. Logging a set starts that exercise's rest countdown; canceling the countdown does not change the workout clock or logged set.

Finishing a workout marks it complete. The history page lists completed sessions and provides Show and Delete actions. A completed session's summary displays the date, workout duration, exercises, and logged sets; it is read-only except for deleting the session. Recent sessions on the overview do not expose delete controls.

## Accounts and preferences

Users can select KG or LB display and LIGHT, DARK, or SYSTEM appearance in Settings. SYSTEM follows the device preference. An administrator can create accounts with permanent or temporary passwords, reset passwords, enable or disable accounts, and delete accounts. Administrators cannot disable or delete their own account. User data inspection is read-only and uses separate routine and workout detail pages.
