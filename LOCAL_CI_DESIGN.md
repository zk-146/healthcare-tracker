# Running CI jobs locally in Docker

Date: 2026-09-08
Status: approved, ready for implementation planning

## Problem

Iterating on the `owasp` job in `.github/workflows/ci.yml` currently requires a
push to GitHub. The round trip is roughly 12-18 minutes: commit, push, wait for a
runner, wait for the `build` job the `owasp` job depends on, then wait for
dependency-check itself. The suppression file
(`dependency-check-suppressions.xml`) is the artifact that needs the most
tuning - it currently holds nine CVEs with no published fix, each scoped by a
`packageUrl` regex, and getting those regexes right is exactly the kind of work
that wants a fast feedback loop.

There is no local JDK or Maven on this machine; all builds already run inside a
Maven Docker container.

## Goal

Cut the suppression-tuning loop to a few minutes by running the `owasp` gate
locally in Docker, and make the two other runnable jobs (`build`, `frontend`)
executable locally so workflow-YAML mistakes are caught before pushing.

Expected saving: a suppression-file iteration drops from ~15 minutes to ~3
minutes. Across the 5-8 iterations a suppression review typically takes, that is
roughly 1-1.5 hours, plus the CI minutes and queue waits avoided.

## Approach

A hybrid, because the two halves of the problem want different tools.

The `owasp` job is iterated on for the *result of a Maven command*, not for the
workflow wiring around it. Running it through `act` would mean standing up
`act`'s local cache server just to satisfy two `actions/cache` steps that exist
only to work around GitHub's runner cache semantics - pure overhead locally. So
that job runs as a direct `docker run` of the real Maven command.

The `frontend` job is cheap to run under `act` and there the workflow wiring
*is* part of what we want to check, so it goes through `act`.

> **Change of record (approved mid-implementation):** this section originally
> routed the `build` job through `act` as well. That was dropped. `act`'s pinned
> runner image `catthehacker/ubuntu:act-latest` ships neither Maven nor a JDK
> (GitHub's hosted runners preinstall Maven; `setup-java` only adds a JDK), and
> this repo has no `./mvnw` wrapper, so running `build` under `act` dies with
> `mvn: command not found`. The `build` job is therefore a direct `docker run` of `mvn -B
> verify` via `scripts/local-ci/build.sh`, matching the approach already used for
> `owasp`. `act` covers `frontend` only.

### Component 1: `scripts/local-ci/owasp-scan.sh`

A committed shell script that runs the OWASP gate in a container.

- Image: `maven:3.9-eclipse-temurin-17`
- Repo bind-mounted at `/w`, working directory `/w`
- Named volume `atracker-m2` at `/root/.m2` - persists downloaded Maven
  dependencies between runs
- Named volume `atracker-nvd` at
  `/root/.m2/repository/org/owasp/dependency-check-data` - persists the NVD
  database. This is deliberately a *nested* mount inside `atracker-m2`: Docker
  layers the inner mount over the outer one, so the expensive NVD database
  survives even if the Maven dependency volume is deleted.
- Command: `mvn -B dependency-check:check`
- Prefixed with `MSYS_NO_PATHCONV=1` so Git Bash on Windows does not mangle the
  container-side absolute paths in the `-v` arguments.
- If `NVD_API_KEY` is set in the environment, it is passed through as
  `-DnvdApiKey=...`; if unset, the flag is omitted entirely rather than passed
  empty.
- The script exits with dependency-check's own exit code, so a CVSS >= 7 finding
  fails the script exactly as it fails CI.

No API key is configured. The first run therefore performs a full, rate-limited
NVD sync taking 30-90 minutes. Because the result lands in the `atracker-nvd`
volume, this happens exactly once. Subsequent runs are one of two speeds: a run
within dependency-check's `nvdValidForHours` window (default 4 hours) of the last
one skips the update entirely and finishes in ~22 seconds; a run after that
window does a rate-limited delta fetch and takes materially longer (not measured
on this machine; the delta-fetch figure previously quoted here was only an
unverified estimate). A fast run is therefore not evidence that the CVE data is
current. See
`scripts/local-ci/README.md` for the operator-facing version of this.

### Component 2: `act` configuration for `frontend`, and `build.sh`

- `act` installed on the host (not present today).
- `.actrc` committed at the repo root, pinning the runner image to
  `catthehacker/ubuntu:act-latest` and setting
  `--container-architecture linux/amd64`.
- `.secrets` - a gitignored file holding `NVD_API_KEY=` as an empty placeholder,
  so `act` does not error on the missing secret reference if the `owasp` job is
  ever invoked through it.
- Usage is `act -j frontend`. The `build` job does **not** run under `act` (see
  the change-of-record note above); it runs via `scripts/local-ci/build.sh`, a
  direct `docker run` of `mvn -B verify`.

The one-time cost here is the runner image pull, 5-15 minutes.

### Component 3: wiring and documentation

- `.gitignore` gains a `.secrets` entry.
- `scripts/local-ci/README.md` documents the three commands, the one-time NVD
  sync caveat, and how to remove the volumes to force a clean re-sync.

`.github/workflows/ci.yml` is **not** modified. This work adds a local mirror of
the CI jobs; it does not change CI itself.

## Out of scope

- The `codeql` job. CodeQL's analysis action does not run meaningfully outside
  GitHub's environment.
- The `docker` job and its Trivy scans. Running it under `act` would require
  docker-in-docker; the image can be built and scanned directly with `docker
  build` plus the Trivy CLI if that need arises, but that is separate work.
- Obtaining an NVD API key. The design assumes none and absorbs the one-time
  sync cost instead.
- Any change to the CVSS threshold, the suppression file contents, or the
  workflow.

## Verification

The work is done when:

1. `scripts/local-ci/owasp-scan.sh` completes against a warm `atracker-nvd`
   volume and its pass/fail verdict matches what the `owasp` job produces on CI
   for the same commit.
2. Re-running the script does not re-download the NVD database - the second run
   finishes in minutes, not tens of minutes.
3. `scripts/local-ci/build.sh` and `act -j frontend` both complete successfully.
4. `.secrets` is untracked and `git status` is clean after a run.
