CREATE TABLE IF NOT EXISTS backup_admin_settings (
    singleton boolean PRIMARY KEY CHECK (singleton),
    enabled boolean NOT NULL DEFAULT false,
    utc_time time NOT NULL DEFAULT '02:15',
    retention_days integer NOT NULL DEFAULT 30 CHECK (retention_days BETWEEN 7 AND 90),
    last_scheduled_date date
);
INSERT INTO backup_admin_settings (singleton) VALUES (true) ON CONFLICT DO NOTHING;
CREATE TABLE IF NOT EXISTS backup_admin_jobs (
    id uuid PRIMARY KEY, actor_id uuid, operation text NOT NULL, archive text,
    state text NOT NULL, created_at timestamptz NOT NULL DEFAULT now(),
    finished_at timestamptz, result text
);
CREATE UNIQUE INDEX IF NOT EXISTS backup_admin_one_job ON backup_admin_jobs ((true))
    WHERE state IN ('QUEUED', 'RUNNING');
ALTER TABLE backup_admin_jobs ADD COLUMN IF NOT EXISTS archive_sha256 text;
