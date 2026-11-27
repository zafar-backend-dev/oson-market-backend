ALTER TABLE account_deletion_requests ADD COLUMN IF NOT EXISTS receipt uuid;
CREATE UNIQUE INDEX IF NOT EXISTS idx_account_deletion_receipt ON account_deletion_requests(receipt);
CREATE INDEX IF NOT EXISTS idx_auth_code_purpose_issued ON auth_verification_codes(purpose, issued_at);
