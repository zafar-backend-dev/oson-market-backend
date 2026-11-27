CREATE TABLE IF NOT EXISTS account_deletion_requests (
    account_id uuid PRIMARY KEY,
    verified_at timestamp with time zone NOT NULL
);
