# Running CI jobs locally

Mirrors of the jobs in `.github/workflows/ci.yml`, so a change can be checked
without a push. Design and rationale: [`LOCAL_CI_DESIGN.md`](../../LOCAL_CI_DESIGN.md).

## The OWASP gate

```bash
./scripts/local-ci/owasp-scan.sh
```

Runs `mvn dependency-check:check` in a Maven container with the pom's
`failBuildOnCVSS=7` and `dependency-check-suppressions.xml`. Exits non-zero on a
breach, exactly as the `owasp` job does. This is the one to use when tuning
suppressions.

**The first run is a full NVD sync and is slow.** There is no NVD API key
configured, so the initial download of the ~400k-CVE database is rate-limited: it
took 43 minutes when measured; budget 30-90, since it varies with NVD rate
limiting. The database is cached in the `atracker-nvd` Docker volume afterwards,
and warm subsequent runs take about 22 seconds.

During the first sync you will see a couple of
`[ERROR] Failed to process CVE-...` lines. These are dependency-check ingest
errors on unrelated (Mozilla) CVEs whose reference URL is too long for its
database column. They do not fail the run and are not findings against this
project — do not mistake them for results.

If you have a key, `NVD_API_KEY=... ./scripts/local-ci/owasp-scan.sh` uses it.

## The build job

```bash
./scripts/local-ci/build.sh
```

Runs `mvn -B verify` — compile plus the full test suite — in a Maven container,
exactly as the `build` job does. Maven artifacts are cached in the `atracker-m2`
Docker volume (shared with `owasp-scan.sh`), so only the first run pays the
download cost; a warm run is about 3 minutes.

This job is a direct container run rather than `act -j build` on purpose: `act`'s
runner image (`catthehacker/ubuntu:act-latest`) has no Maven and no JDK, GitHub's
hosted runners preinstall Maven, `setup-java` only installs a JDK, and this repo
has no `./mvnw` wrapper — so under `act` the job dies with `mvn: command not
found`. Do not re-attempt it through `act`.

## The frontend job

```bash
act -j frontend
```

Requires [`act`](https://github.com/nektos/act). Config is in `.actrc`; secrets
come from `.secrets`, which is gitignored — copy the `NVD_API_KEY=` placeholder
line into a fresh one if you do not have the file.

`act` logs a cache miss for `setup-node` on every run because it does not
implement `actions/cache`. That is expected.

## Jobs not mirrored here

- `codeql` — CodeQL's analysis action does not run meaningfully outside GitHub.
- `docker` — running it under `act` would need docker-in-docker. Build and scan
  directly with `docker build` and the Trivy CLI if you need to.

## Resetting the caches

```bash
docker volume rm atracker-m2    # Maven artifacts, shared by build.sh and owasp-scan.sh; cheap to rebuild
docker volume rm atracker-nvd   # NVD database; costs another 30-90 min sync
```

`atracker-nvd` is mounted inside `atracker-m2` by `owasp-scan.sh`, so removing
the Maven volume alone leaves the expensive NVD database intact. Removing
`atracker-m2` only forces `build.sh` and `owasp-scan.sh` to re-download Maven
artifacts.
