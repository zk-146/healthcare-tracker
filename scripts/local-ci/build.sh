#!/usr/bin/env bash
#
# Runs the CI `build` job locally, in the same shape CI runs it:
# `mvn -B verify`, which compiles and runs the full test suite. Exits with
# Maven's own status, so a compile error or test failure fails this script
# exactly as it fails CI.
#
# This is a direct container run, not `act -j build`, on purpose. `act`'s pinned
# runner image (`catthehacker/ubuntu:act-latest`) ships Node but no Maven and no
# JDK; GitHub's hosted runners preinstall Maven, and `setup-java` only installs a
# JDK. There is no `./mvnw` wrapper in this repo, so under `act` the job dies
# with `mvn: command not found`. Running `maven:3.9-eclipse-temurin-17` directly
# sidesteps that. `act -j frontend` still works and is the documented path for
# the frontend job.
#
# The `atracker-m2` volume holds the Maven local repository, shared with
# owasp-scan.sh so artifacts are downloaded once. The `atracker-nvd` volume is
# only for the OWASP NVD database and is not mounted here.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${REPO_ROOT}"

IMAGE="maven:3.9-eclipse-temurin-17"

MSYS_NO_PATHCONV=1 docker run --rm \
  -v "$(pwd):/w" \
  -v "atracker-m2:/root/.m2" \
  -w /w \
  "${IMAGE}" \
  mvn -B verify
