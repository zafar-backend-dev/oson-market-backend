#!/usr/bin/env bash
source "$(dirname -- "$0")/common.sh"
[[ $# == 2 ]]
archive=$1 target=$2
[[ "$target" =~ ^oson_restore_[a-z0-9_]{1,40}$ && -f "$archive" ]]
: "${OSON_RESTORE_OWNER:?Choose isolated backend DB role}"
[[ "$OSON_RESTORE_OWNER" =~ ^[a-z_][a-z0-9_]{0,40}$ ]]
# Fully authenticate, including ciphertext at the end, before any target mutation.
gpg_private --decrypt "$archive" | pg_restore --exit-on-error --file=- >/dev/null
createdb --no-password --maintenance-db=postgres --template=template0 --owner="$OSON_RESTORE_OWNER" "$target"
psql --no-password -X -v ON_ERROR_STOP=1 -d postgres <<SQL
ALTER DATABASE "$target" SET osonmarket.restore_required TO 'on';
SELECT format('ALTER DATABASE %I SET osonmarket.restore_generation TO %L', '$target', gen_random_uuid()::text) \gexec
REVOKE CONNECT ON DATABASE "$target" FROM PUBLIC;
SQL
gpg_private --decrypt "$archive" |
  pg_restore --exit-on-error --single-transaction --no-owner --no-privileges --no-password \
    --role="$OSON_RESTORE_OWNER" --dbname="$target"
printf 'Restored isolated candidate %s. Reconciliation is required.\n' "$target"
