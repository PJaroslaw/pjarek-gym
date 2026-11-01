CREATE INDEX workout_owner_completed_idx ON workout (owner_id, completed_at DESC)
WHERE
  status = 'COMPLETED';
