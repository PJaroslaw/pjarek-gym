ALTER TABLE exercise
ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX exercise_active_name_idx ON exercise (active, name);
