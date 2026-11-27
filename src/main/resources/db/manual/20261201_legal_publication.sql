CREATE TABLE IF NOT EXISTS account_deletion_requests (
    account_id uuid PRIMARY KEY, verified_at timestamptz NOT NULL
);
ALTER TABLE account_deletion_requests ADD COLUMN IF NOT EXISTS status varchar(16) NOT NULL DEFAULT 'PENDING';
ALTER TABLE account_deletion_requests ADD COLUMN IF NOT EXISTS completed_at timestamptz;
ALTER TABLE account_deletion_requests ADD COLUMN IF NOT EXISTS receipt uuid;
CREATE UNIQUE INDEX IF NOT EXISTS idx_account_deletion_receipt ON account_deletion_requests(receipt);
CREATE INDEX IF NOT EXISTS idx_auth_code_purpose_issued ON auth_verification_codes(purpose, issued_at);
ALTER TABLE product_comments ALTER COLUMN author_id DROP NOT NULL;

CREATE TABLE IF NOT EXISTS legal_document_revisions (
    id uuid PRIMARY KEY, document_kind varchar(16) NOT NULL, version varchar(40) NOT NULL,
    content_uz text NOT NULL, content_ru text NOT NULL, published_at timestamptz NOT NULL,
    UNIQUE (document_kind, version)
);
ALTER TABLE legal_document_revisions ADD COLUMN IF NOT EXISTS published_by uuid;
ALTER TABLE legal_document_revisions ADD COLUMN IF NOT EXISTS active boolean;
UPDATE legal_document_revisions SET active = false WHERE active IS NULL;
ALTER TABLE legal_document_revisions ALTER COLUMN active SET DEFAULT false;
ALTER TABLE legal_document_revisions ALTER COLUMN active SET NOT NULL;
UPDATE legal_document_revisions AS revision SET active = true
WHERE revision.id IN (
    SELECT DISTINCT ON (document_kind) id FROM legal_document_revisions AS candidate
    WHERE NOT EXISTS (SELECT 1 FROM legal_document_revisions AS current
                      WHERE current.document_kind = candidate.document_kind AND current.active)
    ORDER BY document_kind, published_at DESC, id DESC
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_legal_active_kind ON legal_document_revisions(document_kind) WHERE active;
CREATE UNIQUE INDEX IF NOT EXISTS idx_legal_kind_version ON legal_document_revisions(document_kind, version);
