#!/usr/bin/env bash
#
# Runs a local SonarQube analysis of the backend and the frontend.
#
# A red quality gate never fails this script - the gate is informational.
# Non-zero exits mean setup failed: no token, server never came up, or a build
# broke.
#
# Requires Docker Desktop running, and SONAR_TOKEN in the environment or .env.
# See SONARQUBE.md for first-run setup.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MAVEN_IMAGE="maven:3.9-eclipse-temurin-17"
NODE_IMAGE="node:22-alpine"
M2_CACHE="C:/Users/Zaid/.m2"
SONAR_URL="http://localhost:9000"

cd "$REPO_ROOT"

# Git Bash rewrites container-side paths without this. Applies to every docker run.
export MSYS_NO_PATHCONV=1

if [[ -z "${SONAR_TOKEN:-}" && -f .env ]]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env
  set +a
fi

if [[ -z "${SONAR_TOKEN:-}" ]]; then
  cat >&2 <<'EOF'
SONAR_TOKEN is not set.

First run:
  1. docker compose -f docker-compose.sonar.yml up -d
  2. Open http://localhost:9000, log in as admin / admin, then set a password.
  3. My Account -> Security -> generate a Global Analysis Token.
  4. echo 'SONAR_TOKEN=<token>' >> .env

.env is gitignored. Do not commit the token.
EOF
  exit 1
fi

echo "==> Starting SonarQube"
docker compose -f docker-compose.sonar.yml up -d

echo "==> Waiting for the server (cold start takes 1-3 minutes)"
for _ in $(seq 1 60); do
  if curl -sf "$SONAR_URL/api/system/status" 2>/dev/null | grep -q '"status":"UP"'; then
    echo "    up"
    break
  fi
  sleep 5
done

if ! curl -sf "$SONAR_URL/api/system/status" 2>/dev/null | grep -q '"status":"UP"'; then
  echo "Server did not come up. Last 40 log lines:" >&2
  docker compose -f docker-compose.sonar.yml logs --tail 40 sonarqube >&2
  echo >&2
  echo "A 'vm.max_map_count [65530] is too low' error means you need:" >&2
  echo "  wsl -d docker-desktop sysctl -w vm.max_map_count=262144" >&2
  exit 1
fi

echo "==> Backend: compiling, testing with coverage, and staging dependency jars"
# copy-dependencies populates target/dependency, which sonar.java.libraries globs
# so Java type resolution is precise (otherwise the scanner warns).
docker run --rm \
  -v "$REPO_ROOT:/app" \
  -v "$M2_CACHE:/root/.m2" \
  -w /app "$MAVEN_IMAGE" \
  mvn -B test dependency:copy-dependencies -DoutputDirectory=target/dependency

echo "==> Frontend: running tests with coverage"
docker run --rm \
  -v "$REPO_ROOT/ui:/ui" \
  -w /ui "$NODE_IMAGE" \
  sh -c "npm ci && npm run test:coverage"

echo "==> Scanning"
docker run --rm \
  --network sonar-net \
  -e SONAR_HOST_URL=http://sonarqube:9000 \
  -e SONAR_TOKEN="$SONAR_TOKEN" \
  -v "$REPO_ROOT:/usr/src" \
  sonarsource/sonar-scanner-cli

# The gate is reported, not enforced, so this never affects the exit code.
# `|| true` guards the whole pipeline: under pipefail a curl/grep/head/cut
# failure (network blip, gate not yet computed, auth hiccup) would otherwise
# make this bare assignment fatal under set -e.
GATE=$(curl -su "$SONAR_TOKEN:" \
  "$SONAR_URL/api/qualitygates/project_status?projectKey=healthcare-tracker" |
  grep -o '"status":"[A-Z]*"' | head -1 | cut -d'"' -f4 || true)

echo
echo "Quality gate: ${GATE:-unknown}"
echo "Results: $SONAR_URL/dashboard?id=healthcare-tracker"
echo
echo "Stop the server with:"
echo "  docker compose -f docker-compose.sonar.yml down"
