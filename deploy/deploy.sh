#!/usr/bin/env bash
# Pull-based deploy, run by iplay-deploy.timer on the server.
# Copied to ~/deploy.sh on the server rather than run from inside the repo, so
# the `git pull` below can never rewrite the script while bash is reading it.
set -euo pipefail

REPO_DIR="$HOME/i_play"
COMPOSE="docker compose -f docker-compose.yml -f docker-compose.prod.yml"

cd "$REPO_DIR"

log() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"; }

log "Deploy started."

git pull --ff-only --quiet

$COMPOSE pull --quiet

# nginx resolves backend1/2/3 once at startup, so it must be restarted whenever
# a backend container is replaced and gets a new IP - otherwise it keeps the
# stale address, marks the backend unreachable, and quietly sends all traffic
# to whichever one still answers. Container IDs change only on recreation, so
# comparing them tells us exactly when that restart is needed.
BEFORE=$($COMPOSE ps -q backend1 backend2 backend3)
$COMPOSE up -d --quiet-pull
AFTER=$($COMPOSE ps -q backend1 backend2 backend3)

if [ "$BEFORE" != "$AFTER" ]; then
    log "Backends replaced - restarting frontend to refresh DNS."
    $COMPOSE restart frontend
else
    log "No backend changes."
fi

docker image prune -f --filter "dangling=true" >/dev/null

log "Deploy complete."
