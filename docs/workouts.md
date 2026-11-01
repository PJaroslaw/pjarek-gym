# Workout behavior

## Exercise library and search

The catalog is imported from `yuhonas/free-exercise-db`. Exercise IDs are numeric catalog identifiers; `source_id` identifies the upstream record. The exercise picker searches names. The full library searches names, primary-muscle metadata, and equipment. Search terms may be entered in any order and tolerate misspellings through PostgreSQL's `pg_trgm` extension.

## Routines

A routine contains named days, and each day contains ordered exercises. A plan records a set count, minimum and maximum rep counts, and rest duration. Creating a routine does not add a day automatically. Starting a day creates a workout with copies of that day's exercise plan and instruction text, so later plan edits do not rewrite the workout record.

The optional routine builder can draft a plan from a user's goals and preferences or interpret a PDF or image of an existing routine. It supports OpenAI and self-hosted Ollama through separate provider adapters. Generated exercises are selected from the active exercise catalog and filtered to match specified equipment. Requests for weight-only training restrict the catalog to strength exercises using barbells, dumbbells, kettlebells, EZ curl bars, or cable equipment; bodyweight, stretching, machine, and other non-weighted entries are excluded. Before showing a generated draft, the app validates its day count, session-sized exercise count, duplicate exercises, exercise IDs, and set prescriptions; it asks the provider to repair validation issues up to two times. When a refinement request explicitly asks for unique exercises across training days, cross-day repeats are also validated and sent back for repair. Exercise order is arranged for a workout, with compound movements before isolation work and core exercises last. Draft exercises include expandable instructions, equipment and muscle details, and the locally cached exercise image; these details update when the user selects a replacement from search. AI refinement requests receive the current draft and latest change request, not a transcript of earlier requests. The builder confirms when a draft changes and tells the user when the AI returns it unchanged. The draft stays in the authenticated user's session while they edit it or request changes and is not written to the database until the user saves. User preferences and processed document contents are sent to the configured AI provider. PDFs are converted to text and page images locally, and the application does not retain uploads.

Users can also start an ad hoc workout. Starting one from an exercise detail page places that exercise into the new workout and selects it immediately. An exercise added to an active workout becomes the selected exercise.

## Logging and progression

Each logged set stores repetitions and weight. For a planned exercise, the rep field starts at the plan's maximum reps. Weight starts at zero when there is no previous set, then uses the latest set from the current workout or the most recent completed workout for the same exercise. Weight is stored in kilograms; the user's KG or LB preference controls display and input conversion.

The workout page shows completed sets separately from the next set to log. Exercise instructions and images can be expanded when needed. The user can move between exercises without losing the current selection when pausing or resuming.

## Timers and completion

An active workout has one elapsed-time clock. Paused time is excluded, and finishing the workout stores its elapsed duration. Logging a set starts that exercise's rest countdown; canceling the countdown does not change the workout clock or logged set.

Finishing a workout marks it complete. The overview and history pages show completed sessions in monthly calendars, grouped by completion date in the server's time zone. Dates with sessions show their count. Select a date to see that day's sessions, use the month arrows to browse, or jump directly to a month. Calendar links use the `day` query parameter for a selected date and `month` for browsing without a selected date. The overview links to session summaries without delete controls. History provides Show and Delete actions for the selected day's sessions and returns to the selected date after a deletion. A completed session's summary displays the date, workout duration, exercises, and logged sets; it is read-only except for deleting the session.

## Accounts and preferences

Users can select KG or LB display and LIGHT, DARK, or SYSTEM appearance in Settings. SYSTEM follows the device preference. An administrator can create accounts with permanent or temporary passwords, reset passwords, enable or disable accounts, and delete accounts. Administrators cannot disable or delete their own account. User data inspection is read-only and uses separate routine and workout detail pages.
