# Copilot instructions for this repository

Purpose
- Provide concise, machine-actionable info so Copilot sessions can assist efficiently with this Spring Boot project.

1) Build, test, and lint commands
- Build: mvn -U package
- Run locally: mvn spring-boot:run
- Full unit test suite: mvn test
- Single unit test (class): mvn -Dtest=MyTestClass test
- Single unit test (method): mvn -Dtest=MyTestClass#myTestMethod test
- Run integration tests (ITs picked up by Failsafe): mvn failsafe:integration-test failsafe:verify
- Run both unit + integration: mvn verify
- Run E2E Playwright tests (may require browser binaries installed): mvn -Dplaywright.install=true -Dtest=PlaywrightE2EIT test
- Run with a profile: mvn -Dspring-boot.run.profiles=dev spring-boot:run
- Lint/format: no dedicated linter configured by default. If Checkstyle/Spotless/Sonar are added, mirror commands from pom.xml (e.g., mvn checkstyle:check).

2) High-level architecture
- Runtime: Java 21, Spring Boot (Maven)
- Monolith service with these roles:
  - REST API (spring-boot-starter-web)
  - Data persistence (spring-boot-starter-data-jpa)
  - Security / OIDC client + resource-server (spring-boot-starter-security, oauth2)
  - Optional GraphQL API (spring-boot-starter-graphql)
  - OpenAPI UI via springdoc (springdoc-openapi-starter-webmvc-ui)
- Entrypoint: src/main/java/com/copilot/test/SparringFinderApplication.java
- Config: src/main/resources/application.yml (env vars override)
- Dev DB: H2 in-memory (development only)
- Build outputs: target/

3) Key conventions (project-specific)
- Packages: com.copilot.test.{controller,service,repository,domain,dto,config}
- Services:
  - Do not accept or return JPA entity instances on public service method signatures. Use DTOs for input/output.
  - All public service methods are @Transactional by default (use the appropriate propagation/isolation as needed).
  - Service methods should not return entities to avoid unmanaged/lazy-loading issues outside transactional boundaries.
- Method size & docs:
  - Prefer methods ≤ 20 lines; max 50 lines only when no reasonable refactor exists.
  - Every public method must include a short Javadoc explaining purpose, params, and return value.
- Mapping: use MapStruct mappers for entity↔DTO conversions.
- Config precedence: application.yml → environment variables → CI secrets
- OpenAPI: springdoc provides Swagger UI at /swagger-ui/index.html
- Generated/build files: never edit files under target/
- Testcontainers: integration tests use Testcontainers; ensure Docker is running when running ITs locally or in CI.
- Playwright: E2E tests use Playwright for Java. Browsers may need to be installed in CI or locally. Common options:
  - Locally: run mvn -Dplaywright.install=true test once to trigger browser install (or install via Node.js: npx playwright install --with-deps if Node is available).
  - CI: add a step to install Playwright browsers before running the E2E test (e.g., run npx playwright install --with-deps or use the Playwright Docker image).
- Quality: adhere to Sonar quality rules (blocker/critical issues fixed before merge); ensure unit tests cover business logic.

4) Docs & assistant configs
- No README/CONTRIBUTING/assistant config detected at file creation time. If added, copy short runtime/start/CI snippets here.

5) Quick troubleshooting
- H2 console: /h2-console while running locally
- Dev run: mvn -Dspring-boot.run.profiles=dev spring-boot:run

Using the Copilot cloud agent
- copilot-setup-steps.yml is present and preinstalls Java 21, Node 20 and Playwright browsers so the Copilot cloud agent can build and run tests reliably.
- To run E2E tests via the agent, request a Copilot session or run the CI workflow `CI - E2E Tests` (manually from Actions). The agent will use the setup snapshot and can execute:
  - mvn -Dplaywright.install=true verify
  - or run the `CI - E2E Tests` workflow (which runs mvn verify) from GitHub Actions.
- Requirements: Docker available to Testcontainers; network access to download Playwright browsers (or preinstalled via setup steps).

Keeping this file accurate
- Update Build/Test/Lint commands and the architecture/conventions when changing pom.xml, CI, or packages.

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
