#!/usr/bin/env bash
# Dumps the production database to a compressed, timestamped file.
#
#   ./deploy/backup.sh [output-directory]      (default: ./backups)
#
# Run from the repository root. Copy the result off this host: a backup that lives only
# on the machine it protects is lost along with it. Restore steps: deploy/RUNBOOK.md.
set -euo pipefail

out_dir="${1:-backups}"
mkdir -p "$out_dir"
out_file="$out_dir/activity-tracker-$(date +%F-%H%M%S).dump"

docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env.prod \
  exec -T postgres pg_dump -U activity_tracker -Fc activity_tracker > "$out_file"

# pg_dump failing mid-stream can leave a short file behind; an empty one is never valid.
if [ ! -s "$out_file" ]; then
  echo "Backup failed: $out_file is empty" >&2
  rm -f "$out_file"
  exit 1
fi

echo "Wrote $out_file ($(du -h "$out_file" | cut -f1))"
