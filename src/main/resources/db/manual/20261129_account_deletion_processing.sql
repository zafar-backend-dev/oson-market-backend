ALTER TABLE account_deletion_requests ADD COLUMN IF NOT EXISTS status varchar(16) NOT NULL DEFAULT 'PENDING';
ALTER TABLE account_deletion_requests ADD COLUMN IF NOT EXISTS completed_at timestamptz;
ALTER TABLE product_comments ALTER COLUMN author_id DROP NOT NULL;
