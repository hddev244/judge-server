#!/bin/bash
# Full backup of the judge-server stack: PostgreSQL dump, test case files, .env.
# Writes to $BACKUP_ROOT/<timestamp>/, keeps the newest $KEEP_LOCAL backups,
# then syncs the whole backup root to an rclone remote if one is configured.
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKUP_ROOT="${BACKUP_ROOT:-/var/backups/judge}"
KEEP_LOCAL="${KEEP_LOCAL:-14}"
RCLONE_REMOTE="${RCLONE_REMOTE:-}"   # e.g. "gdrive:judge-backups"; auto-detected if empty

cd "$PROJECT_DIR"
set -a; source .env; set +a

TS="$(date +%Y-%m-%d_%H%M)"
DEST="$BACKUP_ROOT/$TS"
mkdir -p "$DEST"

echo "[backup] $TS -> $DEST"

echo "[backup] pg_dump ${DB_NAME:-judgedb}..."
docker compose exec -T postgres pg_dump -U "${DB_USER:-judge}" -Fc "${DB_NAME:-judgedb}" \
    > "$DEST/judgedb.dump"

TESTCASE_PATH="${JUDGE_TESTCASE_BASE_PATH:-/data/problems}"
echo "[backup] tar $TESTCASE_PATH..."
tar -czf "$DEST/problems.tar.gz" -C "$(dirname "$TESTCASE_PATH")" "$(basename "$TESTCASE_PATH")"

cp .env "$DEST/env"

echo "[backup] manifest..."
GIT_COMMIT="$(git -C "$PROJECT_DIR" rev-parse HEAD 2>/dev/null || echo unknown)"
(
    cd "$DEST"
    {
        echo "{"
        echo "  \"timestamp\": \"$TS\","
        echo "  \"git_commit\": \"$GIT_COMMIT\","
        echo "  \"host\": \"$(hostname)\","
        echo "  \"files\": {"
        first=1
        for f in judgedb.dump problems.tar.gz env; do
            [ $first -eq 1 ] || echo ","
            first=0
            printf '    "%s": "%s"' "$f" "$(sha256sum "$f" | cut -d' ' -f1)"
        done
        echo ""
        echo "  }"
        echo "}"
    } > manifest.json
)

echo "[backup] rotation: keeping newest $KEEP_LOCAL"
ls -1d "$BACKUP_ROOT"/*/ 2>/dev/null | sort | head -n -"$KEEP_LOCAL" | while read -r old; do
    echo "[backup] removing old backup $old"
    rm -rf "$old"
done

if ! command -v rclone >/dev/null; then
    echo "[backup] WARN: rclone not installed — skipping remote sync" >&2
    exit 0
fi
if [ -z "$RCLONE_REMOTE" ]; then
    FIRST_REMOTE="$(rclone listremotes 2>/dev/null | head -1)"
    if [ -z "$FIRST_REMOTE" ]; then
        echo "[backup] WARN: no rclone remote configured — backup is LOCAL ONLY." >&2
        echo "[backup]       run 'rclone config' then re-run, or set RCLONE_REMOTE." >&2
        exit 0
    fi
    RCLONE_REMOTE="${FIRST_REMOTE}judge-backups"
fi
echo "[backup] rclone sync -> $RCLONE_REMOTE"
rclone sync "$BACKUP_ROOT" "$RCLONE_REMOTE" --transfers 4
echo "[backup] done"
