Sparring Finder — Development Tasks

Overview
This file breaks the spec (spec.md) into implementable development tasks, grouped by epic, with priority, estimate, and dependencies. Use task IDs when creating issues or todos.

1. AUTH & REGISTRATION (auth)
- auth-01: Implement local registration API (/api/v1/auth/register). Priority: High. Estimate: 2d. Dependencies: DB user model, KeycloakAdminService. Acceptance: spec.md - Auth & Registration.
- auth-02: Implement password hashing (bcrypt) and validation. Priority: High. Estimate: 0.5d.
- auth-03: Add Keycloak provisioning wrapper (idempotent create/update). Priority: High. Estimate: 1.5d. Dependencies: Keycloak admin client config.
- auth-04: Unit tests for registration flow (+ Keycloak mock). Priority: High. Estimate: 1d.

2. USER PROFILES (profiles)
- profiles-01: Create Profile entity (coords, bio, privacy flags). Priority: High. Estimate: 1d. Dependencies: DB and Liquibase migration.
- profiles-02: MapStruct DTOs and mappers for User/Profile. Priority: High. Estimate: 0.5d.
- profiles-03: Profile CRUD endpoints (/api/v1/profiles). Priority: High. Estimate: 1.5d.
- profiles-04: Privacy enforcement & soft-delete. Priority: Medium. Estimate: 0.5d.

3. PHOTO UPLOAD & STORAGE (photos)
- photos-01: S3 storage integration (MinIO client config + service). Priority: High. Estimate: 1d.
- photos-02: Multipart upload endpoint with validation (<=10MB, type check). Priority: High. Estimate: 1d.
- photos-03: Moderation flag workflow (pending/approved). Priority: Medium. Estimate: 1d.
- photos-04: Presigned URL download + server-side encryption. Priority: Medium. Estimate: 0.5d.

4. GEOSPATIAL SEARCH & MATCHING (matching)
- matching-01: Add PostGIS-compatible geometry column and Liquibase changeset. Priority: High. Estimate: 1d.
- matching-02: Radius search endpoint + service using indexed queries. Priority: High. Estimate: 1.5d.
- matching-03: k-NN endpoint (top-K nearest). Priority: Medium. Estimate: 1d.
- matching-04: Match scoring and filters (skill, availability). Priority: Medium. Estimate: 1d.

5. MESSAGING (messaging)
- messaging-01: WebSocket auth & subscription scaffolding. Priority: High. Estimate: 1d.
- messaging-02: Persisted message model + REST send endpoint. Priority: High. Estimate: 1d.
- messaging-03: Delivery/ACK handling, retry logic. Priority: High. Estimate: 1d.
- messaging-04: Rate limiting and spam detection basics. Priority: Medium. Estimate: 1d.

6. ADMIN & KEYCLOAK MANAGEMENT (admin)
- admin-01: Admin provisioning API (idempotent). Priority: High. Estimate: 1d. Dependencies: KeycloakAdminService.
- admin-02: Audit logging for admin actions. Priority: Medium. Estimate: 0.5d.

7. APIS & DOCS (docs)
- docs-01: OpenAPI annotations and swagger config. Priority: High. Estimate: 0.5d.
- docs-02: Example requests/responses for main endpoints. Priority: Medium. Estimate: 0.5d.

8. DATA MIGRATIONS & DTOS (migrations)
- db-01: Initial Liquibase changelog for users/profiles/photos. Priority: High. Estimate: 1d.
- dtos-01: MapStruct mappers for public DTOs. Priority: High. Estimate: 0.5d.

9. TESTING & CI (testing)
- test-01: Unit tests for services and mappers. Priority: High. Estimate: 2d.
- test-02: Integration tests with Testcontainers (Postgres + MinIO + Keycloak mock). Priority: High. Estimate: 2d.
- test-03: Playwright E2E scenario scripts for register/upload/search/message. Priority: Medium. Estimate: 1.5d.

10. OBSERVABILITY & SECURITY (ops)
- ops-01: Actuator + Prometheus + OTLP config. Priority: High. Estimate: 0.5d.
- ops-02: Add input validation, rate limits, and CORS config. Priority: High. Estimate: 0.5d.

Suggested workplan (sprints)
- Sprint 1 (2 weeks): auth-01..04, profiles-01..03, db-01, dtos-01, docs-01, basic unit tests (test-01).
- Sprint 2 (2 weeks): photos-01..03, matching-01..03, messaging-01..03, integration tests (test-02).
- Sprint 3 (2 weeks): admin-01..02, moderation workflows, E2E Playwright (test-03), observability (ops-01..02).

How to proceed
- Create issues from task IDs above and set dependencies. I can create todos in the session DB or open GitHub issues if you authorize. Which would you prefer? (use ask_user)