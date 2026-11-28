ALTER TABLE account_deletion_requests ADD COLUMN IF NOT EXISTS next_attempt_at timestamptz NOT NULL DEFAULT now();

CREATE TABLE IF NOT EXISTS account_deletion_orders (
    order_id uuid PRIMARY KEY REFERENCES orders(id) ON DELETE CASCADE,
    account_id uuid NOT NULL REFERENCES account_deletion_requests(account_id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_account_deletion_orders_account ON account_deletion_orders (account_id);
CREATE INDEX IF NOT EXISTS idx_account_deletion_due ON account_deletion_requests (next_attempt_at, account_id)
    WHERE status <> 'COMPLETED';
