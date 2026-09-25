ALTER TABLE routine_item
ADD COLUMN min_reps INTEGER,
ADD COLUMN max_reps INTEGER;

WITH
  parsed AS (
    SELECT
      id,
      regexp_match(
        btrim(rep_target),
        '^([0-9]{1,4})([[:space:]]*-[[:space:]]*([0-9]{1,4}))?$'
      ) AS parts
    FROM
      routine_item
  ),
  ranges AS (
    SELECT
      id,
      parts[1]::INTEGER AS first_rep,
      COALESCE(parts[3]::INTEGER, parts[1]::INTEGER) AS last_rep
    FROM
      parsed
    WHERE
      parts IS NOT NULL
  ),
  valid_ranges AS (
    SELECT
      id,
      LEAST(first_rep, last_rep) AS min_reps,
      GREATEST(first_rep, last_rep) AS max_reps
    FROM
      ranges
    WHERE
      first_rep BETWEEN 1 AND 1000
      AND last_rep BETWEEN 1 AND 1000
  )
UPDATE routine_item AS item
SET
  min_reps = COALESCE(valid_ranges.min_reps, 8),
  max_reps = COALESCE(valid_ranges.max_reps, 12)
FROM
  valid_ranges
WHERE
  item.id = valid_ranges.id;

UPDATE routine_item
SET
  min_reps = 8,
  max_reps = 12
WHERE
  min_reps IS NULL;

ALTER TABLE routine_item
ALTER COLUMN min_reps
SET NOT NULL,
ALTER COLUMN min_reps
SET DEFAULT 8,
ALTER COLUMN max_reps
SET NOT NULL,
ALTER COLUMN max_reps
SET DEFAULT 12,
ADD CONSTRAINT routine_item_rep_range_check CHECK (
  min_reps BETWEEN 1 AND 1000
  AND max_reps BETWEEN min_reps AND 1000
),
DROP COLUMN rep_target;

ALTER TABLE workout_exercise
ADD COLUMN min_reps INTEGER,
ADD COLUMN max_reps INTEGER;

WITH
  parsed AS (
    SELECT
      id,
      regexp_match(
        btrim(rep_target),
        '^([0-9]{1,4})([[:space:]]*-[[:space:]]*([0-9]{1,4}))?$'
      ) AS parts
    FROM
      workout_exercise
  ),
  ranges AS (
    SELECT
      id,
      parts[1]::INTEGER AS first_rep,
      COALESCE(parts[3]::INTEGER, parts[1]::INTEGER) AS last_rep
    FROM
      parsed
    WHERE
      parts IS NOT NULL
  ),
  valid_ranges AS (
    SELECT
      id,
      LEAST(first_rep, last_rep) AS min_reps,
      GREATEST(first_rep, last_rep) AS max_reps
    FROM
      ranges
    WHERE
      first_rep BETWEEN 1 AND 1000
      AND last_rep BETWEEN 1 AND 1000
  )
UPDATE workout_exercise AS item
SET
  min_reps = COALESCE(valid_ranges.min_reps, 8),
  max_reps = COALESCE(valid_ranges.max_reps, 12)
FROM
  valid_ranges
WHERE
  item.id = valid_ranges.id;

UPDATE workout_exercise
SET
  min_reps = 8,
  max_reps = 12
WHERE
  min_reps IS NULL;

ALTER TABLE workout_exercise
ALTER COLUMN min_reps
SET NOT NULL,
ALTER COLUMN min_reps
SET DEFAULT 8,
ALTER COLUMN max_reps
SET NOT NULL,
ALTER COLUMN max_reps
SET DEFAULT 12,
ADD CONSTRAINT workout_exercise_rep_range_check CHECK (
  min_reps BETWEEN 1 AND 1000
  AND max_reps BETWEEN min_reps AND 1000
),
DROP COLUMN rep_target;

ALTER TABLE workout
ADD COLUMN elapsed_seconds INTEGER NOT NULL DEFAULT 0,
ADD COLUMN active_since TIMESTAMPTZ;

WITH
  timeline AS (
    SELECT
      id AS workout_id,
      started_at AS occurred_at,
      'STARTED' AS kind,
      0 AS event_id
    FROM
      workout
    UNION ALL
    SELECT
      workout_id,
      occurred_at,
      kind,
      id AS event_id
    FROM
      workout_event
    WHERE
      kind IN ('PAUSED', 'RESUMED')
    UNION ALL
    SELECT
      id,
      COALESCE(completed_at, now()),
      'END',
      9223372036854775807
    FROM
      workout
    WHERE
      status = 'COMPLETED'
    UNION ALL
    SELECT
      id,
      now(),
      'ACTIVE_END',
      9223372036854775807
    FROM
      workout
    WHERE
      status = 'IN_PROGRESS'
    UNION ALL
    SELECT
      id,
      now(),
      'PAUSED',
      9223372036854775807
    FROM
      workout
    WHERE
      status = 'PAUSED'
  ),
  intervals AS (
    SELECT
      workout_id,
      kind,
      occurred_at,
      LEAD(kind) OVER (
        PARTITION BY
          workout_id
        ORDER BY
          occurred_at,
          event_id
      ) AS next_kind,
      LEAD(occurred_at) OVER (
        PARTITION BY
          workout_id
        ORDER BY
          occurred_at,
          event_id
      ) AS next_at
    FROM
      timeline
  ),
  durations AS (
    SELECT
      workout_id,
      COALESCE(
        SUM(
          GREATEST(
            0,
            FLOOR(
              EXTRACT(
                EPOCH
                FROM
                  (next_at - occurred_at)
              )
            )::BIGINT
          )
        ),
        0
      ) AS elapsed_seconds
    FROM
      intervals
    WHERE
      kind IN ('STARTED', 'RESUMED')
      AND next_kind IN ('PAUSED', 'END')
    GROUP BY
      workout_id
  ),
  active_points AS (
    SELECT
      workout.id,
      COALESCE(
        (
          SELECT
            event.occurred_at
          FROM
            workout_event AS event
          WHERE
            event.workout_id = workout.id
            AND event.kind = 'RESUMED'
            AND event.occurred_at >= workout.started_at
          ORDER BY
            event.occurred_at DESC,
            event.id DESC
          LIMIT
            1
        ),
        workout.started_at
      ) AS active_since
    FROM
      workout
    WHERE
      workout.status = 'IN_PROGRESS'
  )
UPDATE workout
SET
  elapsed_seconds = COALESCE(durations.elapsed_seconds, 0)::INTEGER,
  active_since = active_points.active_since
FROM
  durations
  FULL JOIN active_points ON active_points.id = durations.workout_id
WHERE
  workout.id = COALESCE(durations.workout_id, active_points.id);

UPDATE workout
SET
  elapsed_seconds = 0,
  active_since = CASE
    WHEN status = 'IN_PROGRESS' THEN started_at
    ELSE NULL
  END
WHERE
  elapsed_seconds IS NULL;

ALTER TABLE workout
ALTER COLUMN active_since
SET DEFAULT now(),
ADD CONSTRAINT workout_timer_state_check CHECK (
  (
    status = 'IN_PROGRESS'
    AND active_since IS NOT NULL
  )
  OR (
    status IN ('PAUSED', 'COMPLETED')
    AND active_since IS NULL
  )
),
ADD CONSTRAINT workout_elapsed_seconds_check CHECK (elapsed_seconds >= 0);

UPDATE workout_set
SET
  weight = round(weight * 0.45359237, 2)
WHERE
  weight_unit = 'LB';

ALTER TABLE workout_set
DROP COLUMN weight_unit;
