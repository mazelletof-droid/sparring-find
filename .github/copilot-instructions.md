# Copilot instructions for this repository

Purpose
- Machine-actionable guidance so Copilot cloud or local sessions can build, test and reason about this Spring Boot project.

1) Build, test, and lint (quick commands)
- Build artifact: mvn -U package
- Run app locally: mvn spring-boot:run
- Run unit tests only (Surefire): mvn -B -U test
- Run a single test class: mvn -Dtest=MyTestClass test
- Run a single test method: mvn -Dtest=MyTestClass#myTestMethod test
- Run integration tests (Failsafe; *IT.java): mvn -B -U failsafe:integration-test failsafe:verify
- Run unit + integration (full verification): mvn -B -U verify
- Run E2E (Playwright) locally via helper: .\run-e2e.ps1 [-SkipBrowserInstall]
  - Or with Maven only: mvn -Dplaywright.install=true -DskipTests=false verify
- Install Playwright browsers (if Node available): npx -y playwright install --with-deps
- Lint/format: no dedicated linter in pom.xml. If added, expose the plugin goals here.

2) High-level architecture (big picture)
- Language/runtime: Java 21, Spring Boot 3.x, Maven build.
- Layers:
  - Web: spring-boot-starter-web exposes REST controllers.
  - Persistence: spring-boot-starter-data-jpa (H2 for dev; Testcontainers for ITs).
  - Security: Spring Security + OAuth2/OIDC (client + resource-server starters included).
  - API docs: springdoc OpenAPI with Swagger UI at /swagger-ui/index.html.
  - Optional GraphQL support is present via spring-boot-starter-graphql.
- Entry point: src/main/java/com/copilot/test/SparringFinderApplication.java
- Configuration: src/main/resources/application.yml (override with environment variables in CI).

3) Key conventions and repo-specific patterns
- Package layout: com.copilot.test.{controller,service,repository,domain,dto,config}
- DTO-first services:
  - Public service APIs accept/return DTOs only — do not expose JPA entities across service boundaries.
  - Use MapStruct mappers for conversions (mapstruct configured in pom.xml).
- Transactions & lifecycle:
  - Public service methods should be @Transactional; avoid returning entities from transactional methods.
- Method size & docs:
  - Prefer methods ≤ 20 lines (allowed to 50 with justification).
  - All public methods require Javadoc describing behavior, params, and return.
- Tests:
  - Unit tests run via Surefire; integration tests are named *IT.java and executed by Failsafe.
  - DatabaseIntegrationIT uses Testcontainers (PostgreSQL container). Docker must be available in environment.
  - PlaywrightE2EIT uses Playwright Java and spins a browser to assert the Swagger UI loads. Browser binaries must be present.
- CI expectations:
  - CI workflows are under .github/workflows. Unit CI (`CI - Unit Tests`) runs mvn test. E2E CI (`CI - E2E Tests`) installs Playwright browsers, requires Docker and runs mvn verify.

4) Useful patterns for Copilot sessions
- When asked to run tests, prefer: mvn -B -U -DskipTests=false verify for full validation; use -Dtest to limit scope during iteration.
- For debugging failing ITs, capture target/failsafe-reports and the Playwright browser install logs.
- Avoid editing generated files under target/; regenerate from source if needed.

5) Housekeeping & known issues
- Repo currently contains build outputs (target/) including a ~70MB jar committed. Recommended actions:
  - Add target/ to .gitignore and remove the large file from history (git rm --cached target/sparring-finder-0.0.1-SNAPSHOT.jar && commit + push), or enable Git LFS for large artifacts.
- Testcontainers on Windows may require Docker Desktop (WSL2) and correct permissions. If Docker is unavailable, skip ITs with -DskipITs or run with a profile that disables integration tests.
- Playwright browser downloads can fail behind proxies; use the copilot-setup-steps.yml snapshot (preinstalled) or run npx install behind your proxy.

6) Copilot cloud agent specifics
- .github/workflows/copilot-setup-steps.yml is present and creates a snapshot with Java 21, Node 20, and Playwright browsers preinstalled — sessions using Copilot cloud agent should be able to run mvn verify and E2E workflows without extra setup.
- To run the manual E2E workflow: open Actions → Run E2E (Run E2E (manual) workflow) or run the `CI - E2E Tests` workflow.

7) Where to look (entry points)
- Main: src/main/java/com/copilot/test/SparringFinderApplication.java
- Integration tests: src/test/java/com/copilot/test/integration/DatabaseIntegrationIT.java
- E2E tests: src/test/java/com/copilot/test/e2e/PlaywrightE2EIT.java
- Local E2E helper: run-e2e.ps1
- CI workflows: .github/workflows/*.yml

Keeping this file accurate
- Update commands and CI notes when pom.xml, workflows, or package layout change.

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
