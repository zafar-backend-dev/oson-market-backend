#!/usr/bin/env bash
source "$(dirname -- "$0")/common.sh"
: "${PGDATABASE:?Choose the application database}"
: "${OSON_BACKUP_DIR:?Choose a private backup directory outside the website}"
days="${OSON_BACKUP_RETENTION_DAYS:-30}"
[[ $days =~ ^[1-9][0-9]{0,3}$ ]]
[[ "$OSON_BACKUP_DIR" = /* && ! -L "$OSON_BACKUP_DIR" ]]
mkdir -p -- "$OSON_BACKUP_DIR"
root=$(cd -- "$OSON_BACKUP_DIR" && pwd -P)
case "$root/" in /var/www/*|/srv/www/*|/usr/share/nginx/html/*) exit 1;; esac
case "$(uname -s)" in Linux*) [[ "$(stat -c %u:%a "$root")" == "$(id -u):700" ]];; esac
lock="$root/.backup-lock"
mkdir -- "$lock"
tmp=''
cleanup() {
  if [[ -n $tmp ]]; then rm -f -- "$tmp/archive.gpg"; rmdir -- "$tmp"; fi
  rmdir -- "$lock"
}
trap cleanup EXIT
tmp=$(mktemp -d "$root/.backup.XXXXXXXX")
stamp=$(date -u +%Y%m%dT%H%M%SZ)
suffix=${tmp##*.}
final="$root/osonmarket-$stamp-$suffix.dump.gpg"
pg_dump --format=custom --no-password --dbname="$PGDATABASE" |
  gpg_private --symmetric --cipher-algo AES256 --output "$tmp/archive.gpg"
# Drain and authenticate the entire archive. pg_restore consumes all bytes when writing SQL.
gpg_private --decrypt "$tmp/archive.gpg" |
  pg_restore --exit-on-error --file=- >/dev/null
mv -- "$tmp/archive.gpg" "$final"
case "$(uname -s)" in Linux*) sync -f -- "$root";; esac
printf 'Verified backup published: %s\n' "${final##*/}"
cutoff=$(( $(date +%s) - days * 86400 ))
rotated=0
while IFS= read -r -d '' old; do
  [[ $old != "$final" && $(stat -c %Y "$old") -lt $cutoff ]] || continue
  if [[ ${OSON_BACKUP_ROTATION_DRY_RUN:-true} == false ]]; then
    rm -- "$old"
  fi
  rotated=$((rotated+1))
done < <(find "$root" -maxdepth 1 -type f -name 'osonmarket-*.dump.gpg' -print0)
printf 'Rotation candidates=%d dryRun=%s\n' "$rotated" "${OSON_BACKUP_ROTATION_DRY_RUN:-true}"
