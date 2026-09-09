# Code quality — SonarQube

A local, self-hosted SonarQube Community server analyses the Spring Boot backend
and the React frontend together, with coverage from JaCoCo and vitest.

This is deliberately **not** wired into CI. The quality gate is reported, never
enforced, and no GitHub Actions job runs it. Security scanning stays with
CodeQL, OWASP Dependency-Check and Trivy in `.github/workflows/ci.yml`.

## First run

1. Raise the kernel setting the embedded Elasticsearch needs. This does not
   survive a Docker Desktop restart:

   ```bash
   wsl -d docker-desktop sysctl -w vm.max_map_count=262144
   ```

2. Start the server:

   ```bash
   docker compose -f docker-compose.sonar.yml up -d
   ```

3. Open http://localhost:9000, log in as `admin` / `admin`, and set a password.

4. Go to My Account, then Security, and generate a **Global Analysis Token**.

5. Save it where git will not pick it up:

   ```bash
   echo 'SONAR_TOKEN=<your-token>' >> .env
   ```

## Running a scan

```bash
./scripts/sonar-scan.sh
```

It starts the server if it is not already up, builds the backend and frontend
with coverage, runs the scanner, then prints the gate result and dashboard link.
A red gate does not fail the script.

Results: http://localhost:9000/dashboard?id=healthcare-tracker

Stop the server when you are done, it is not lightweight:

```bash
docker compose -f docker-compose.sonar.yml down
```

## Notes

- **Memory.** The server wants 2-3 GB. Stop the application stack first if it is
  running, since Kafka and Ollama are already heavy.
- **Server exits during startup.** Almost always `vm.max_map_count`. Check with
  `docker compose -f docker-compose.sonar.yml logs sonarqube | tail -40` and
  re-run step 1.
- **Coverage shows 0%.** The surefire `argLine` in `pom.xml` must keep its
  `@{argLine}` prefix. Without it the JaCoCo agent is silently discarded.
- **Java files missing from the report.** `sonar.java.binaries` points at
  `target/classes`, so the backend must have been compiled before the scan.
- **The first scan reports a lot.** Several hundred issues on a codebase seeing
  Sonar for the first time is normal. That is why the gate is not enforced.
