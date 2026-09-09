#!/usr/bin/env bash
#
# Runs the CI `owasp` job's gate locally, in the same shape CI runs it:
# `mvn dependency-check:check` with the pom's failBuildOnCVSS=7 and
# dependency-check-suppressions.xml. Exits with dependency-check's own status,
# so a CVSS >= 7 finding fails this script exactly as it fails CI.
#
# The NVD database lives in the `atracker-nvd` volume, mounted *inside* the
# `atracker-m2` volume. Docker layers the inner mount over the outer, so
# `docker volume rm atracker-m2` throws away cached Maven artifacts without
# throwing away the expensive NVD sync.
#
# No NVD API key is configured for this project, so the first run performs a
# full rate-limited sync (30-90 minutes). After that, a run within
# dependency-check's nvdValidForHours window (default 4 hours) of the last one
# skips the update and takes ~22 seconds; a run past that window does a
# rate-limited delta fetch and takes longer (not measured here). A fast run is
# not proof the CVE data is fresh. Set NVD_API_KEY in the environment to use one.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${REPO_ROOT}"

IMAGE="maven:3.9-eclipse-temurin-17"
NVD_PATH="/root/.m2/repository/org/owasp/dependency-check-data"

# Omit the flag entirely when unset; dependency-check treats an empty
# -DnvdApiKey= as a supplied-but-invalid key and errors out.
nvd_args=()
if [[ -n "${NVD_API_KEY:-}" ]]; then
  nvd_args=(-DnvdApiKey="${NVD_API_KEY}")
fi

MSYS_NO_PATHCONV=1 docker run --rm \
  -v "$(pwd):/w" \
  -v "atracker-m2:/root/.m2" \
  -v "atracker-nvd:${NVD_PATH}" \
  -w /w \
  "${IMAGE}" \
  mvn -B dependency-check:check ${nvd_args[@]+"${nvd_args[@]}"}
