#!/usr/bin/env bash
set -Eeuo pipefail
umask 077
trap 'printf "Backup/restore failed at line %s\n" "$LINENO" >&2' ERR
: "${OSON_BACKUP_PASSPHRASE:?Provide an absolute path to a private passphrase file}"
[[ "$OSON_BACKUP_PASSPHRASE" = /* && -f "$OSON_BACKUP_PASSPHRASE" && ! -L "$OSON_BACKUP_PASSPHRASE" ]]
[[ -s "$OSON_BACKUP_PASSPHRASE" ]]
case "$(uname -s)" in
  Linux*)
    [[ "$(stat -c %u "$OSON_BACKUP_PASSPHRASE")" == "$(id -u)" ]]
    [[ "$(stat -c %a "$OSON_BACKUP_PASSPHRASE")" =~ ^[46]00$ ]]
    ;;
esac
# libpq reads the password from PGPASSFILE or local peer credentials.
unset PGPASSWORD
gpg_private() {
  gpg --batch --no-tty --pinentry-mode loopback --passphrase-file "$OSON_BACKUP_PASSPHRASE" "$@"
}
