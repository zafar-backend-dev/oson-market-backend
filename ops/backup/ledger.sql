CREATE TABLE IF NOT EXISTS public.oson_erasure_ledger (
    account_id uuid PRIMARY KEY
);
REVOKE ALL ON public.oson_erasure_ledger FROM PUBLIC;
-- Separately grant SELECT, INSERT to the application ledger role. Keep its owner separate.
