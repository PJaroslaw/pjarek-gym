CREATE TEMPORARY TABLE app_user_id_map (
  old_id BIGINT PRIMARY KEY,
  new_id UUID NOT NULL DEFAULT gen_random_uuid()
) ON
COMMIT
DROP;

CREATE TEMPORARY TABLE routine_day_id_map (
  old_id BIGINT PRIMARY KEY,
  new_id UUID NOT NULL DEFAULT gen_random_uuid()
) ON
COMMIT
DROP;

CREATE TEMPORARY TABLE routine_item_id_map (
  old_id BIGINT PRIMARY KEY,
  new_id UUID NOT NULL DEFAULT gen_random_uuid()
) ON
COMMIT
DROP;

CREATE TEMPORARY TABLE workout_exercise_id_map (
  old_id BIGINT PRIMARY KEY,
  new_id UUID NOT NULL DEFAULT gen_random_uuid()
) ON
COMMIT
DROP;

CREATE TEMPORARY TABLE workout_set_id_map (
  old_id BIGINT PRIMARY KEY,
  new_id UUID NOT NULL DEFAULT gen_random_uuid()
) ON
COMMIT
DROP;

CREATE TEMPORARY TABLE workout_event_id_map (
  old_id BIGINT PRIMARY KEY,
  new_id UUID NOT NULL DEFAULT gen_random_uuid()
) ON
COMMIT
DROP;

INSERT INTO
  app_user_id_map (old_id)
SELECT
  id
FROM
  app_user;

INSERT INTO
  routine_day_id_map (old_id)
SELECT
  id
FROM
  routine_day;

INSERT INTO
  routine_item_id_map (old_id)
SELECT
  id
FROM
  routine_item;

INSERT INTO
  workout_exercise_id_map (old_id)
SELECT
  id
FROM
  workout_exercise;

INSERT INTO
  workout_set_id_map (old_id)
SELECT
  id
FROM
  workout_set;

INSERT INTO
  workout_event_id_map (old_id)
SELECT
  id
FROM
  workout_event;

ALTER TABLE app_user
ADD COLUMN uuid_id UUID;

UPDATE app_user AS entity
SET
  uuid_id = map.new_id
FROM
  app_user_id_map AS map
WHERE
  map.old_id = entity.id;

ALTER TABLE routine
ADD COLUMN uuid_owner_id UUID;

UPDATE routine AS entity
SET
  uuid_owner_id = map.new_id
FROM
  app_user_id_map AS map
WHERE
  map.old_id = entity.owner_id;

ALTER TABLE routine_day
ADD COLUMN uuid_id UUID;

UPDATE routine_day AS entity
SET
  uuid_id = map.new_id
FROM
  routine_day_id_map AS map
WHERE
  map.old_id = entity.id;

ALTER TABLE routine_item
ADD COLUMN uuid_id UUID;

ALTER TABLE routine_item
ADD COLUMN uuid_day_id UUID;

UPDATE routine_item AS entity
SET
  uuid_id = map.new_id
FROM
  routine_item_id_map AS map
WHERE
  map.old_id = entity.id;

UPDATE routine_item AS entity
SET
  uuid_day_id = map.new_id
FROM
  routine_day_id_map AS map
WHERE
  map.old_id = entity.day_id;

ALTER TABLE workout
ADD COLUMN uuid_owner_id UUID;

ALTER TABLE workout
ADD COLUMN uuid_routine_day_id UUID;

UPDATE workout AS entity
SET
  uuid_owner_id = map.new_id
FROM
  app_user_id_map AS map
WHERE
  map.old_id = entity.owner_id;

UPDATE workout AS entity
SET
  uuid_routine_day_id = map.new_id
FROM
  routine_day_id_map AS map
WHERE
  map.old_id = entity.routine_day_id;

ALTER TABLE workout_exercise
ADD COLUMN uuid_id UUID;

UPDATE workout_exercise AS entity
SET
  uuid_id = map.new_id
FROM
  workout_exercise_id_map AS map
WHERE
  map.old_id = entity.id;

ALTER TABLE workout_set
ADD COLUMN uuid_id UUID;

ALTER TABLE workout_set
ADD COLUMN uuid_workout_exercise_id UUID;

UPDATE workout_set AS entity
SET
  uuid_id = map.new_id
FROM
  workout_set_id_map AS map
WHERE
  map.old_id = entity.id;

UPDATE workout_set AS entity
SET
  uuid_workout_exercise_id = map.new_id
FROM
  workout_exercise_id_map AS map
WHERE
  map.old_id = entity.workout_exercise_id;

ALTER TABLE workout_event
ADD COLUMN uuid_id UUID;

UPDATE workout_event AS entity
SET
  uuid_id = map.new_id
FROM
  workout_event_id_map AS map
WHERE
  map.old_id = entity.id;

ALTER TABLE routine
DROP CONSTRAINT routine_owner_id_fkey;

ALTER TABLE routine_item
DROP CONSTRAINT routine_item_day_id_fkey;

ALTER TABLE workout
DROP CONSTRAINT workout_owner_id_fkey;

ALTER TABLE workout
DROP CONSTRAINT workout_routine_day_id_fkey;

ALTER TABLE workout_set
DROP CONSTRAINT workout_set_workout_exercise_id_fkey;

ALTER TABLE app_user
DROP CONSTRAINT app_user_pkey;

ALTER TABLE routine_day
DROP CONSTRAINT routine_day_pkey;

ALTER TABLE routine_item
DROP CONSTRAINT routine_item_pkey;

ALTER TABLE workout_exercise
DROP CONSTRAINT workout_exercise_pkey;

ALTER TABLE workout_set
DROP CONSTRAINT workout_set_pkey;

ALTER TABLE workout_event
DROP CONSTRAINT workout_event_pkey;

ALTER TABLE app_user
DROP COLUMN id;

ALTER TABLE routine
DROP COLUMN owner_id;

ALTER TABLE routine_day
DROP COLUMN id;

ALTER TABLE routine_item
DROP COLUMN id;

ALTER TABLE routine_item
DROP COLUMN day_id;

ALTER TABLE workout
DROP COLUMN owner_id;

ALTER TABLE workout
DROP COLUMN routine_day_id;

ALTER TABLE workout_exercise
DROP COLUMN id;

ALTER TABLE workout_set
DROP COLUMN id;

ALTER TABLE workout_set
DROP COLUMN workout_exercise_id;

ALTER TABLE workout_event
DROP COLUMN id;

ALTER TABLE app_user
RENAME COLUMN uuid_id TO id;

ALTER TABLE routine
RENAME COLUMN uuid_owner_id TO owner_id;

ALTER TABLE routine_day
RENAME COLUMN uuid_id TO id;

ALTER TABLE routine_item
RENAME COLUMN uuid_id TO id;

ALTER TABLE routine_item
RENAME COLUMN uuid_day_id TO day_id;

ALTER TABLE workout
RENAME COLUMN uuid_owner_id TO owner_id;

ALTER TABLE workout
RENAME COLUMN uuid_routine_day_id TO routine_day_id;

ALTER TABLE workout_exercise
RENAME COLUMN uuid_id TO id;

ALTER TABLE workout_set
RENAME COLUMN uuid_id TO id;

ALTER TABLE workout_set
RENAME COLUMN uuid_workout_exercise_id TO workout_exercise_id;

ALTER TABLE workout_event
RENAME COLUMN uuid_id TO id;

ALTER TABLE app_user
ALTER COLUMN id
SET DEFAULT gen_random_uuid();

ALTER TABLE routine_day
ALTER COLUMN id
SET DEFAULT gen_random_uuid();

ALTER TABLE routine_item
ALTER COLUMN id
SET DEFAULT gen_random_uuid();

ALTER TABLE workout_exercise
ALTER COLUMN id
SET DEFAULT gen_random_uuid();

ALTER TABLE workout_set
ALTER COLUMN id
SET DEFAULT gen_random_uuid();

ALTER TABLE workout_event
ALTER COLUMN id
SET DEFAULT gen_random_uuid();

ALTER TABLE routine
ALTER COLUMN owner_id
SET NOT NULL;

ALTER TABLE workout
ALTER COLUMN owner_id
SET NOT NULL;

ALTER TABLE routine_item
ALTER COLUMN day_id
SET NOT NULL;

ALTER TABLE workout_set
ALTER COLUMN workout_exercise_id
SET NOT NULL;

ALTER TABLE app_user
ADD PRIMARY KEY (id);

ALTER TABLE routine_day
ADD PRIMARY KEY (id);

ALTER TABLE routine_item
ADD PRIMARY KEY (id);

ALTER TABLE workout_exercise
ADD PRIMARY KEY (id);

ALTER TABLE workout_set
ADD PRIMARY KEY (id);

ALTER TABLE workout_event
ADD PRIMARY KEY (id);

ALTER TABLE routine
ADD CONSTRAINT routine_owner_id_fkey FOREIGN KEY (owner_id) REFERENCES app_user (id) ON DELETE CASCADE;

ALTER TABLE routine_item
ADD CONSTRAINT routine_item_day_id_fkey FOREIGN KEY (day_id) REFERENCES routine_day (id) ON DELETE CASCADE;

ALTER TABLE workout
ADD CONSTRAINT workout_owner_id_fkey FOREIGN KEY (owner_id) REFERENCES app_user (id) ON DELETE CASCADE;

ALTER TABLE workout
ADD CONSTRAINT workout_routine_day_id_fkey FOREIGN KEY (routine_day_id) REFERENCES routine_day (id) ON DELETE SET NULL;

ALTER TABLE workout_set
ADD CONSTRAINT workout_set_workout_exercise_id_fkey FOREIGN KEY (workout_exercise_id) REFERENCES workout_exercise (id) ON DELETE CASCADE;

CREATE INDEX routine_owner_idx ON routine (owner_id);

CREATE INDEX workout_owner_started_idx ON workout (owner_id, started_at DESC);

CREATE INDEX workout_set_parent_idx ON workout_set (workout_exercise_id);
