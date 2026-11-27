CREATE TABLE IF NOT EXISTS legal_document_revisions (
    id uuid PRIMARY KEY,
    document_kind varchar(16) NOT NULL,
    version varchar(40) NOT NULL,
    content_uz text NOT NULL,
    content_ru text NOT NULL,
    published_at timestamp with time zone NOT NULL,
    UNIQUE (document_kind, version)
);
