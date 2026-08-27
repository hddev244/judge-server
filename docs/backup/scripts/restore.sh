#!/bin/bash
# Restore a backup produced by backup.sh.
# Usage: restore.sh <backup_dir> [--yes] [--db <name>]
#   --yes   skip the confirmation prompt
#   --db    restore into an alternate database (created if missing) — used to
#           verify a backup without touching the live DB
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKUP_DIR="${1:?usage: restore.sh <backup_dir> [--yes] [--db <name>]}"
shift
ASSUME_YES=0
DB_OVERRIDE=""
while [ $# -gt 0 ]; do
    case "$1" in
        --yes) ASSUME_YES=1 ;;
        --db) DB_OVERRIDE="$2"; shift ;;
        *) echo "unknown arg: $1" >&2; exit 1 ;;
    esac
    shift
done

cd "$PROJECT_DIR"
set -a; source .env; set +a
DB_USER="${DB_USER:-judge}"
TARGET_DB="${DB_OVERRIDE:-${DB_NAME:-judgedb}}"
TESTCASE_PATH="${JUDGE_TESTCASE_BASE_PATH:-/data/problems}"

[ -f "$BACKUP_DIR/manifest.json" ] || { echo "ERROR: no manifest.json in $BACKUP_DIR" >&2; exit 1; }

echo "[restore] verifying checksums..."
for f in judgedb.dump problems.tar.gz env; do
    want="$(grep -o "\"$f\": \"[a-f0-9]*\"" "$BACKUP_DIR/manifest.json" | cut -d'"' -f4)"
    got="$(sha256sum "$BACKUP_DIR/$f" | cut -d' ' -f1)"
    if [ "$want" != "$got" ]; then
        echo "ERROR: checksum mismatch for $f (want $want, got $got)" >&2
        exit 1
    fi
    echo "[restore]   $f OK"
done

if [ "$ASSUME_YES" -ne 1 ]; then
    echo "About to restore into database '$TARGET_DB'"
    [ -z "$DB_OVERRIDE" ] && echo "  AND overwrite test case files under $TESTCASE_PATH"
    read -r -p "Continue? [y/N] " ans
    [ "$ans" = "y" ] || [ "$ans" = "Y" ] || { echo "aborted"; exit 1; }
fi

if [ -n "$DB_OVERRIDE" ]; then
    echo "[restore] ensuring database $TARGET_DB exists..."
    docker compose exec -T postgres psql -U "$DB_USER" -d postgres -tAc \
        "SELECT 1 FROM pg_database WHERE datname='$TARGET_DB'" | grep -q 1 || \
        docker compose exec -T postgres createdb -U "$DB_USER" "$TARGET_DB"
fi

echo "[restore] pg_restore -> $TARGET_DB..."
docker compose exec -T postgres pg_restore --clean --if-exists --no-owner \
    -U "$DB_USER" -d "$TARGET_DB" < "$BACKUP_DIR/judgedb.dump"

if [ -z "$DB_OVERRIDE" ]; then
    echo "[restore] extracting test cases -> $TESTCASE_PATH..."
    sudo tar -xzf "$BACKUP_DIR/problems.tar.gz" -C "$(dirname "$TESTCASE_PATH")"
else
    echo "[restore] --db mode: skipping test case extraction"
fi

echo "[restore] verifying row counts in $TARGET_DB:"
for t in problems test_cases submissions api_keys; do
    n="$(docker compose exec -T postgres psql -U "$DB_USER" -d "$TARGET_DB" -tAc "SELECT count(*) FROM $t")"
    echo "[restore]   $t: $n"
done
echo "[restore] done"
