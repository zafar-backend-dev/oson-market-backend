#!/usr/bin/env bash
source "$(dirname -- "$0")/common.sh"
[[ $(id -u) != 0 ]]
: "${OSON_BACKUP_DIR:?}"
[[ "$OSON_BACKUP_DIR" = /* && ! -L "$OSON_BACKUP_DIR" ]]
root=$(cd -- "$OSON_BACKUP_DIR" && pwd -P)
[[ "$(stat -c %u:%a "$root")" == "$(id -u):700" ]]
archive_pattern='^osonmarket-[0-9]{8}T[0-9]{6}Z-[A-Za-z0-9]{8}\.dump\.gpg$'

verify() {
  [[ $1 =~ $archive_pattern && -f "$root/$1" && ! -L "$root/$1" ]]
  gpg_private --decrypt "$root/$1" | pg_restore --exit-on-error --file=- >/dev/null
}

case "$1" in
  CREATE)
    exec /bin/bash "$(dirname -- "$0")/backup.sh"
    ;;
  VERIFY|RESTORE_CHECK)
    verify "$2"
    ;;
  ROTATION_PREVIEW)
    latest=''
    count=0
    cutoff=$(( $(date +%s) - OSON_BACKUP_RETENTION_DAYS * 86400 ))
    for path in "$root"/osonmarket-*.dump.gpg; do
      name=${path##*/}
      [[ $name =~ $archive_pattern && -f "$path" && ! -L "$path" ]] || continue
      if [[ $name > $latest ]]; then latest=$name; fi
      if [[ $(stat -c %Y -- "$path") -lt $cutoff ]]; then count=$((count+1)); fi
    done
    [[ -n $latest ]]
    verify "$latest"
    if [[ $(stat -c %Y -- "$root/$latest") -lt $cutoff ]]; then count=$((count-1)); fi
    printf '%d\n' "$count"
    ;;
  *) exit 2 ;;
esac
