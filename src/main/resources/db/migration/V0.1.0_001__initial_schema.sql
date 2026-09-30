CREATE TABLE app_user (
  id BIGSERIAL PRIMARY KEY,
  username VARCHAR(80) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  role VARCHAR(20) NOT NULL,
  weight_unit VARCHAR(8) NOT NULL DEFAULT 'KG',
  theme_mode VARCHAR(12) NOT NULL DEFAULT 'SYSTEM',
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE exercise (
  id BIGSERIAL PRIMARY KEY,
  source_id VARCHAR(160) UNIQUE,
  name VARCHAR(200) NOT NULL,
  category VARCHAR(80),
  equipment VARCHAR(100),
  difficulty VARCHAR(40),
  force_type VARCHAR(40),
  mechanic VARCHAR(40),
  primary_muscles TEXT NOT NULL DEFAULT '',
  secondary_muscles TEXT NOT NULL DEFAULT '',
  instructions TEXT NOT NULL DEFAULT '',
  image_paths TEXT NOT NULL DEFAULT ''
);
CREATE INDEX exercise_name_idx ON exercise USING GIN (to_tsvector('english', name));
CREATE TABLE routine (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  name VARCHAR(160) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE routine_day (
  id BIGSERIAL PRIMARY KEY,
  routine_id UUID NOT NULL REFERENCES routine(id) ON DELETE CASCADE,
  name VARCHAR(120) NOT NULL,
  position INTEGER NOT NULL
);
CREATE TABLE routine_item (
  id BIGSERIAL PRIMARY KEY,
  day_id BIGINT NOT NULL REFERENCES routine_day(id) ON DELETE CASCADE,
  exercise_id BIGINT NOT NULL REFERENCES exercise(id),
  position INTEGER NOT NULL,
  planned_sets INTEGER NOT NULL DEFAULT 3,
  rep_target VARCHAR(40) NOT NULL DEFAULT '8-12',
  rest_seconds INTEGER NOT NULL DEFAULT 90,
  notes TEXT NOT NULL DEFAULT ''
);
CREATE TABLE workout (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  routine_day_id BIGINT REFERENCES routine_day(id) ON DELETE SET NULL,
  title VARCHAR(160) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
  started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  completed_at TIMESTAMPTZ
);
CREATE TABLE workout_exercise (
  id BIGSERIAL PRIMARY KEY,
  workout_id UUID NOT NULL REFERENCES workout(id) ON DELETE CASCADE,
  exercise_id BIGINT NOT NULL REFERENCES exercise(id),
  position INTEGER NOT NULL,
  instructions_snapshot TEXT NOT NULL DEFAULT '',
  planned_sets INTEGER NOT NULL DEFAULT 0,
  rep_target VARCHAR(40) NOT NULL DEFAULT '',
  rest_seconds INTEGER NOT NULL DEFAULT 90
);
CREATE TABLE workout_set (
  id BIGSERIAL PRIMARY KEY,
  workout_exercise_id BIGINT NOT NULL REFERENCES workout_exercise(id) ON DELETE CASCADE,
  set_number INTEGER NOT NULL,
  reps INTEGER NOT NULL CHECK (reps >= 0),
  weight NUMERIC(8,2) NOT NULL CHECK (weight >= 0),
  weight_unit VARCHAR(8) NOT NULL DEFAULT 'KG',
  completed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE workout_event (
  id BIGSERIAL PRIMARY KEY,
  workout_id UUID NOT NULL REFERENCES workout(id) ON DELETE CASCADE,
  kind VARCHAR(30) NOT NULL,
  occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX routine_owner_idx ON routine(owner_id);
CREATE INDEX workout_owner_started_idx ON workout(owner_id, started_at DESC);
CREATE INDEX workout_set_parent_idx ON workout_set(workout_exercise_id);
