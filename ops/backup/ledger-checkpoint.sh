#!/usr/bin/env bash
set -Eeuo pipefail
unset PGPASSWORD
: "${PGDATABASE:?Choose the independent ledger database with the authoritative history}"
psql --no-password -XAt -v ON_ERROR_STOP=1 -d "$PGDATABASE" \
  -c 'SELECT account_id FROM public.oson_erasure_ledger ORDER BY account_id' |
  sha256sum | cut -d' ' -f1
