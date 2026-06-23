# Sparring Finder — Backend Specification (Server-side)

Short overview

A concise, developer-friendly backend specification for Sparring Finder (Java 21, Spring Boot, Maven). Covers epics, prioritized user stories, testable acceptance criteria, DB changes, CI/test guidance, security, transactional failure handling (Keycloak + DB), and implementation mapping. This document targets server-side deliverables only.

Audience

- Backend engineers
- QA / Test engineers
- DevOps / SRE
- Product owner

Assumptions

- Postgres is the primary DB; Liquibase is used for schema migrations (changelogs exist).
- Keycloak is the authorization server; KeycloakAdminService exists and supports client-credentials and password grant.
- S3-compatible object storage is used: MinIO for dev, AWS S3 for prod.
- Current codebase already implements /api/v1/auth/register which provisions Keycloak user and persists User and Profile entities.
- Kubernetes is the target deployment environment.
- Tests currently include unit, integration and Playwright E2E scaffolding. Heavy Keycloak tests run on self-hosted runners.

---

## Table of Contents

- [Epics & Prioritized User Stories](#epics--prioritized-user-stories)
  - [Authentication & Authorization (MUST)](#authentication--authorization-must)
    - [AUTH-001: Register — provision Keycloak user + create local User & Profile](#auth-001-register---provision-keycloak-user--create-local-user--profile)
    - [AUTH-002: Login / Token Exchange](#auth-002-login--token-exchange)
    - [AUTH-003: Token Refresh (Silent Refresh)](#auth-003-token-refresh-silent-refresh)
    - [AUTH-004: Admin provisioning & idempotency](#auth-004-admin-provisioning--idempotency)
    - [AUTH-005: Transactional compensation for Keycloak/DB failures](#auth-005-transactional-compensation-for-keycloakdb-failures)
  - [User Profile (MUST)](#user-profile-must)
    - [PROFILE-001: Create / Update Profile (with geo)](#profile-001-create--update-profile-with-geo)
    - [PROFILE-002: View Public Profile](#profile-002-view-public-profile)
    - [PROFILE-003: Privacy & visibility controls](#profile-003-privacy--visibility-controls)
  - [Search & Match (MUST)](#search--match-must)
    - [SEARCH-001: Geolocation-based search endpoint (radius)](#search-001-geolocation-based-search-endpoint-radius)
    - [SEARCH-002: Match suggestion/hints (MVP algorithm)](#search-002-match-suggestionhints-mvp-algorithm)
  - [Photos & Storage (MUST)](#photos--storage-must)
    - [PHOTO-001: Upload photo via presigned URL (MVP)](#photo-001-upload-photo-via-presigned-url-mvp)
    - [PHOTO-002: Serve photo URLs & caching](#photo-002-serve-photo-urls--caching)
    - [PHOTO-003: Delete photo and GC](#photo-003-delete-photo-and-gc)
  - [Scheduling / Availability (SHOULD)](#scheduling--availability-should)
    - [SCHED-001: Manage availability slots](#sched-001-manage-availability-slots)
    - [SCHED-002: Book a sparring session (conflict detection)](#sched-002-book-a-sparring-session-conflict-detection)
  - [Messaging (SHOULD)](#messaging-should)
    - [MSG-001: Send / persist message](#msg-001-send--persist-message)
    - [MSG-002: List conversation messages / pagination](#msg-002-list-conversation-messages--pagination)
  - [Ratings (SHOULD)](#ratings-should)
    - [RATING-001: Rate a completed session](#rating-001-rate-a-completed-session)
  - [Admin / Monitoring (MUST for operators)](#admin--monitoring-must-for-operators)
    - [ADMIN-001: Admin: list users & force-provision in Keycloak](#admin-001-admin-list-users--force-provision-in-keycloak)
    - [ADMIN-002: Audit log retrieval & export](#admin-002-audit-log-retrieval--export)
    - [ADMIN-003: Health & metrics endpoints](#admin-003-health--metrics-endpoints)
- [Transactional & Failure Behavior (explicit stories)](#transactional--failure-behavior-explicit-stories)
- [Database schema changes (summary)](#database-schema-changes-summary)
- [Security & Privacy Considerations](#security--privacy-considerations)
- [CI / Test guidance (unit / integration / E2E)](#ci--test-guidance-unit--integration--e2e)
- [Open questions & assumptions (for product/infra decisions)](#open-questions--assumptions-for-productinfra-decisions)
- [Next steps checklist for engineering](#next-steps-checklist-for-engineering)
- [Implementation mapping (files & components to touch)](#implementation-mapping-files--components-to-touch)

---

## Epics & Prioritized User Stories

NOTE: Each story is compact and testable. Prioritization: MUST = MVP; SHOULD = next milestones; LATER = backlog.

### Authentication & Authorization (MUST)

#### AUTH-001: Register — provision Keycloak user + create local User & Profile

- ID: AUTH-001
- Title: Register — provision Keycloak user and create local User + Profile
- Role(s): Anonymous (public)
- Priority: MUST
- Preconditions:
  - Keycloak realm configured and reachable; KeycloakAdminService configured with client credentials.
  - Postgres and Liquibase migrations applied.
- Description: Public registration endpoint creates a Keycloak user, persists a local User (authServerId) and an empty Profile row. Must be idempotent-friendly and safe for retry.
- HTTP:
  - Method: POST
  - Endpoint: /api/v1/auth/register
  - Request JSON example:

```json
{
  "email": "alice@example.com",
  "password": "P@ssw0rd!",
  "displayName": "Alice",
  "skills": ["boxing", "sparring"],
  "latitude": 48.8566,
  "longitude": 2.3522
}
```

  - Response JSON example (201 Created):

```json
{
  "id": "uuid-local-user",
  "authServerId": "keycloak-uuid",
  "profileId": "uuid-profile",
  "email": "alice@example.com"
}
```

  - Status codes: 201 Created, 400 Bad Request (validation), 409 Conflict (email already exists), 500 Internal Server Error
  - Authorization: none
  - Idempotency: server MUST reject duplicate email registrations (409). For client-request retries on transport failure, accept an Idempotency-Key header (optional): if provided, treat identical request body+key as idempotent and return the same result or error.
  - Rate-limit hint: throttle to 10 req/min per IP for anonymous register endpoint.
- Acceptance criteria (testable):
  - Given valid payload, when POST /api/v1/auth/register, then returns 201 and JSON with local id, authServerId and profileId; Keycloak contains user with returned authServerId; DB contains User row with email and authServerId; Profile row exists linked to user.
  - Edge: Given existing email, when POST, then 409 Conflict with error {"code":"EMAIL_EXISTS"}.
  - Negative: malformed email or weak password => 400 with validation errors.
  - Race: concurrent POSTs with same email must result in only one successful user; the other returns 409.
  - Idempotency-key: repeated identical requests with same Idempotency-Key must return same response (200/201 or 409) without duplicate resources.
- DB changes (example):
  - Table: users (if not present) columns: id UUID PK, email VARCHAR UNIQUE NOT NULL, auth_server_id UUID NULL, created_at TIMESTAMP, updated_at TIMESTAMP
  - Table: profiles columns: id UUID PK, user_id UUID FK users(id), display_name VARCHAR, bio TEXT, latitude DOUBLE PRECISION, longitude DOUBLE PRECISION, location GEOGRAPHY(Point,4326) (if PostGIS), created_at TIMESTAMP
- Suggested tests:
  - Unit: UserService.register happy path and validation failures mocked KeycloakAdminService.
  - Integration: RegistrationIntegrationIT using Testcontainers with Postgres + Keycloak container (or KeycloakMock) asserting Keycloak user created and DB rows present. Include a test where DB persist fails (simulate by throwing from repository) and assert Keycloak user deleted (see AUTH-005).
  - E2E: Playwright script hits front-end registration flow, asserts success and redirects to onboarding.

---

#### AUTH-002: Login / Token Exchange

- ID: AUTH-002
- Title: Login / Token Exchange
- Role(s): Anonymous -> authenticated user
- Priority: MUST
- Preconditions: Keycloak realm + client configured; login flows enabled
- Description: Frontend obtains tokens from Keycloak via browser redirect / OIDC or direct Resource Owner Password (if allowed). Backend provides a token-introspection / exchange endpoint when server-side exchange required.
- HTTP (server-side token exchange, optional):
  - Method: POST
  - Endpoint: /api/v1/auth/token
  - Request:

```json
{
  "grantType": "password",
  "username": "alice@example.com",
  "password": "..."
}
```

  - Response (200):

```json
{
  "access_token": "ey...",
  "refresh_token": "ey...",
  "expires_in": 3600,
  "token_type": "Bearer"
}
```

  - Status codes: 200, 400, 401
  - Authorization: none
- Acceptance criteria:
  - Given valid creds, when POST /api/v1/auth/token, then returns 200 and Keycloak tokens.
  - Given invalid creds, return 401.
  - Tokens must include user id; server should not persist raw passwords.
  - Use Keycloak endpoints directly where possible; backend must validate tokens when protecting APIs.
- DB changes: none
- Tests:
  - Unit: KeycloakTokenService interaction mocked
  - Integration: call token endpoint backed by test Keycloak container (heavy). Skip on shared CI; mark as integration-keycloak.

---

#### AUTH-003: Token Refresh (Silent Refresh)

- ID: AUTH-003
- Title: Token Refresh
- Priority: MUST
- Description: Support refresh flow via Keycloak refresh token; backend exposes a proxy endpoint if needed for single-origin clients.
- HTTP: POST /api/v1/auth/refresh
  - Request: {"refresh_token":"..."}
  - Response: 200 with new access_token & refresh_token
- Acceptance criteria:
  - Given valid refresh_token, returns new tokens; invalid/expired returns 401.
- Tests: Unit + integration against Keycloak (selective)

---

#### AUTH-004: Admin provisioning & idempotency

- ID: AUTH-004
- Title: Admin: provision user in Keycloak and grant admin roles
- Roles: Admin (operator)
- Priority: SHOULD (admin ops are important but can be manual initially)
- Description: Admin can force-provision or repair a Keycloak user and assign realm roles (e.g., sparring_admin). Endpoint must be idempotent.
- HTTP:
  - Method: POST
  - Endpoint: /api/v1/admin/provision-user
  - Request: {"email":"bob@example.com","roles":["sparring_admin"]}
  - Authorization: Bearer token with realm role sparring_admin (or client scope)
- Acceptance criteria:
  - Idempotent: repeated calls with same email+roles result in same state and 200 OK.
  - If Keycloak user exists but missing roles, roles are added.
- DB changes: none or update users.auth_server_id if missing
- Tests: integration with Keycloak; admin APIs require audit log entry.

---

#### AUTH-005: Transactional compensation for Keycloak/DB failures

- ID: AUTH-005
- Title: Ensure compensation when Keycloak provisioning and DB persist diverge
- Priority: MUST
- Description: If Keycloak user creation succeeds but saving User/Profile to DB fails, backend must remove the Keycloak user and return 500. If Keycloak deletion fails, backend must log and enqueue a retry.
- Preconditions: KeycloakAdminService supports deleteUser(authServerId)
- Acceptance criteria (testable):
  - Given Keycloak returns created id and repository.save throws exception, when register called, then:
    - server attempts to delete the created Keycloak user, and returns 500
    - DB contains no partial User row
    - Audit log records the failure and cleanup attempt
  - If cleanup fails (Keycloak delete returns error), then an Audit entry is created and a retry job is enqueued (e.g., in a table cleanup_jobs) and an admin alert is produced.
- Tests:
  - Unit: mock KeycloakAdminService to return ID then mock repo to throw; verify deleteUser called.
  - Integration: simulated fail in DB layer (Testcontainers) asserting cleanup call invoked.
- DB changes:
  - Table: audit_log (id, actor_user_id, action, details JSONB, created_at)
  - Table: cleanup_jobs (id, resource_type, resource_id, status, attempt_count, payload, created_at)

---

### User Profile (MUST)

#### PROFILE-001: Create / Update Profile (with geo)

- ID: PROFILE-001
- Title: Create / Update Profile (set location, skills, bio)
- Roles: Authenticated user
- Priority: MUST
- Preconditions: User exists and authenticated (Bearer token)
- HTTP:
  - Method: PUT
  - Endpoint: /api/v1/profiles/{profileId}
  - Request example:

```json
{
  "displayName": "Alice",
  "bio": "Looking for weekly sparring sessions",
  "skills": ["boxing"],
  "latitude": 48.8566,
  "longitude": 2.3522,
  "visibility": "public" 
}
```

  - Response: 200 OK with updated profile JSON
- Acceptance criteria:
  - Given an authenticated user owns the profile, when they PUT valid data, then profile persisted, location set (latitude/longitude and location geography), and a 200 returned.
  - Deny updates from users who do not own the profile (403).
  - Validation: lat/lon must be within valid ranges => 400.
- DB changes:
  - profiles: add columns skills JSONB, visibility ENUM('private','friends','public'), last_seen_at TIMESTAMP
- Tests:
  - Unit: ProfileService.update validation
  - Integration: update persists and is queryable by search
  - E2E: profile edit UI flow

---

#### PROFILE-002: View Public Profile

- ID: PROFILE-002
- Title: View public profile
- Roles: Authenticated / Anonymous (depends on visibility)
- Priority: MUST
- HTTP: GET /api/v1/profiles/{profileId}
  - Response: 200 with public fields (displayName, skills, rating, photo URLs, distance if requester location provided)
- Acceptance criteria:
  - Public profile returns public fields only. Private profiles return 403 for non-authorized users.
  - If requester supplies lat/long as query params, response includes computed distance in meters.
- Tests: integration verifying visibility filters

---

#### PROFILE-003: Privacy & visibility controls

- ID: PROFILE-003
- Title: Set visibility and data sharing preferences
- Roles: Authenticated user
- Priority: SHOULD
- Acceptance criteria:
  - Users can set visibility, data retention preferences, and opt-out of appearing in search results.
  - Changes reflect in search results within 30s (or as soon as re-indexing completes).
- DB changes: profiles.opt_out_search BOOLEAN
- Tests: integration and E2E toggling visibility

---

### Search & Match (MUST)

#### SEARCH-001: Geolocation-based search endpoint (radius)

- ID: SEARCH-001
- Title: Search sparring partners by geo radius and filters
- Roles: Authenticated user
- Priority: MUST
- Preconditions: profiles table populated with location geography and GiST index (PostGIS) or earthdistance extension enabled
- HTTP:
  - Method: GET
  - Endpoint: /api/v1/search
  - Query params: lat, lon, radiusKm, skills (comma), minRating, page, size
  - Example: GET /api/v1/search?lat=48.85&lon=2.35&radiusKm=10&skills=boxing
  - Response: 200 OK

```json
{
  "page": 0,
  "size": 20,
  "total": 42,
  "results": [
    {"profileId":"...","displayName":"Bob","distanceMeters":4500,"skills":["boxing"]}
  ]
}
```

- Acceptance criteria:
  - Given profiles within radius, they appear ordered by distance.
  - Filters (skills, minRating) narrow results; pagination works.
  - Invalid coordinates => 400. radiusKm cap at e.g., 100 km; values beyond return 400.
  - Edge: if PostGIS disabled, endpoint returns 503 with clear message or falls back to Haversine calculation (documented).
- DB changes: ensure profiles.location GEOGRAPHY(Point,4326) and index: CREATE INDEX idx_profiles_location ON profiles USING GIST (location)
- Tests:
  - Unit: SearchService.haversine calculations
  - Integration: Testcontainers Postgres with PostGIS extension; seed profiles and verify ordering & filters
  - E2E: search flow from UI
- Implementation note: Use PostGIS ST_DWithin or ST_DistanceSphere for accurate radius queries

---

#### SEARCH-002: Match suggestion/hints (MVP algorithm)

- ID: SEARCH-002
- Title: Provide match suggestions using simple score (distance + shared skills + rating)
- Priority: MUST
- Description: Return ranked suggestions combining proximity, skill overlap, and rating. Keep simple for MVP (weighted sum). Make algorithm injectable for future ML/rules.
- Acceptance criteria:
  - Given multiple candidates, suggestions are returned ordered by score. Document weights in code config.
- Tests:
  - Unit: Score calculation tests with controlled inputs
  - Integration: validate ranking with seeded data

---

### Photos & Storage (MUST)

#### PHOTO-001: Upload photo via presigned URL (MVP)

- ID: PHOTO-001
- Title: Upload profile photo via presigned S3 URL
- Roles: Authenticated user
- Priority: MUST
- Description: Backend returns a presigned PUT URL to S3/MinIO; client uploads directly. After upload, client notifies backend to attach URL to profile.
- HTTP:
  - Method: POST
  - Endpoint: /api/v1/profiles/{profileId}/photos
  - Request: {"filename":"avatar.jpg","contentType":"image/jpeg"}
  - Response (200): {"uploadUrl":"https://...","objectKey":"profiles/{profileId}/avatar-uuid.jpg","expiresIn":3600}
  - Authorization: Bearer token; user must own profile
- Acceptance criteria:
  - Given valid request, backend returns presigned URL; after client PUT to that URL, when client calls POST /api/v1/profiles/{profileId}/photos/confirm with objectKey, backend validates object exists (HEAD) and persists photo metadata and thumbnail generation job enqueued.
  - If object not found on confirm => 400
  - Rate-limit: limit to 10 upload requests per user per hour
- DB changes:
  - photos: id UUID PK, user_id UUID FK, object_key TEXT, url TEXT, width INT, height INT, is_primary BOOLEAN, created_at TIMESTAMP
- Tests:
  - Unit: PhotoService presign and confirm logic mocked S3 client
  - Integration: Testcontainers MinIO container to confirm presigned flow; E2E: front-end upload + confirm

---

#### PHOTO-002: Serve photo URLs & caching

- ID: PHOTO-002
- Title: Serve photo URLs (signed or CDN-ready)
- Priority: SHOULD
- Description: Backend returns either CDN public URL or short-lived presigned GET URL to avoid exposing private buckets.
- Acceptance criteria:
  - Given a request for profile photo, return 200 with URL and expiresIn if using signed URL. Public CDN paths acceptable for public photos.
- Tests: integration against MinIO

---

#### PHOTO-003: Delete photo and GC

- ID: PHOTO-003
- Title: Delete profile photo
- Roles: Authenticated owner or admin
- Priority: SHOULD
- HTTP: DELETE /api/v1/profiles/{profileId}/photos/{photoId}
- Acceptance criteria:
  - Owner can delete; file removed from S3 and DB metadata removed or soft-deleted; if deletion of S3 object fails, create cleanup job.
- DB changes: photos.deleted_at TIMESTAMP
- Tests: integration with MinIO; simulate deletion failure and verify cleanup_jobs entry

---

### Scheduling / Availability (SHOULD)

#### SCHED-001: Manage availability slots

- ID: SCHED-001
- Title: Manage availability slots
- Roles: Authenticated user
- Priority: SHOULD
- Description: Users create availability time slots (recurring or one-off). Slots are used by the booking flow.
- HTTP: POST /api/v1/availability
  - Request example: {"startTime":"2026-07-01T18:00:00Z","endTime":"2026-07-01T19:00:00Z","recurrence":"WEEKLY"}
- Acceptance criteria:
  - Create, update, delete availability; prevents overlapping slots for same user (400 on overlap).
- DB changes: availability(id, user_id, start_ts, end_ts, recurrence, timezone)
- Tests: unit validation; integration conflict detection

---

#### SCHED-002: Book a sparring session (conflict detection)

- ID: SCHED-002
- Title: Book a sparring session
- Roles: Authenticated user (requester), target user
- Priority: SHOULD
- HTTP: POST /api/v1/bookings
  - Request:

```json
{
  "profileId": "target-profile-id",
  "startTime":"2026-07-01T18:00:00Z",
  "endTime":"2026-07-01T19:00:00Z",
  "location": {"lat":48.85,"lon":2.35}
}
```

  - Response: 201 booking object
- Acceptance criteria:
  - Booking succeeds if both users have availability and no conflict; returns 201 and booking record. If conflict, return 409 with reason. If target user cancels in the meantime, return 410 Gone.
- DB changes: bookings(id, requester_user_id, target_user_id, start_ts, end_ts, status ENUM(created,confirmed,cancelled,completed))
- Tests: unit conflict detection; integration end-to-end booking flow

---

### Messaging (SHOULD)

#### MSG-001: Send / persist message

- ID: MSG-001
- Title: Send a message to a match
- Roles: Authenticated user
- Priority: SHOULD
- HTTP: POST /api/v1/conversations/{conversationId}/messages
  - Request: {"text":"Hi, want to spar next Tuesday?"}
  - Response: 201 with message object
- Acceptance criteria:
  - Authenticated user in conversation can create messages; message persisted with sender, timestamp. If user not part of conversation => 403.
  - Rate-limit: soft limit e.g., 60 messages/min per user; abuse triggers temporary mute.
- DB changes: conversations(id, participant_ids JSONB, last_message_at), messages(id, conversation_id, sender_id, text, created_at)
- Tests: unit validation; integration conversation flows; E2E UI chat scenario

---

#### MSG-002: List conversation messages / pagination

- ID: MSG-002
- Title: List messages with cursor/pagination
- Roles: Authenticated user
- Priority: SHOULD
- HTTP: GET /api/v1/conversations/{conversationId}/messages?page=0&size=50
- Acceptance criteria:
  - Returns messages page ordered newest-first or cursor-based, supports limit max 100, returns 403 if not participant.
- Tests: integration for pagination, ordering

---

### Ratings (SHOULD)

#### RATING-001: Rate a completed session

- ID: RATING-001
- Title: Rate a completed session
- Roles: Authenticated user
- Priority: SHOULD
- HTTP: POST /api/v1/bookings/{bookingId}/rating
  - Request: {"score":4,"comment":"Great partner"}
  - Response: 201 created
- Acceptance criteria:
  - Only participants of a booking and only after booking end time can submit a rating. Duplicate rating attempts return 409. Average rating updated on profile.
- DB changes: ratings(id, booking_id, rater_user_id, score INT CHECK 1..5, comment TEXT)
- Tests: unit validation; integration timing-based acceptance criteria

---

### Admin / Monitoring (MUST for operators)

#### ADMIN-001: Admin: list users & force-provision in Keycloak

- ID: ADMIN-001
- Title: Admin: list users and repair provisioning
- Roles: sparring_admin
- Priority: MUST
- HTTP: GET /api/v1/admin/users?page=0&size=50 (requires admin token)
- Acceptance criteria:
  - Admin can list users, filter by provisioning status (missing authServerId). Admin can send repair actions which call KeycloakAdminService to create missing Keycloak users.
- Tests: integration with admin token and audit log

---

#### ADMIN-002: Audit log retrieval & export

- ID: ADMIN-002
- Title: Audit log retrieval
- Roles: sparring_admin, support
- Priority: MUST
- HTTP: GET /api/v1/admin/audit?from=...&to=...
- Acceptance criteria:
  - Admins can retrieve audit entries; sensitive details redacted by default; export supports CSV/JSON
- DB changes: audit_log table (see AUTH-005)
- Tests: unit + integration

---

#### ADMIN-003: Health & metrics endpoints

- ID: ADMIN-003
- Title: Health, readiness and metrics
- Roles: system (prometheus)
- Priority: MUST
- Endpoints: /health (liveness), /ready (readiness), /metrics (Prometheus)
- Acceptance criteria:
  - Endpoints return standard codes; readiness checks DB, Keycloak connectivity, S3 connectivity.
- Tests: integration smoke tests in CI

---

## Transactional & Failure Behavior (explicit stories)

We already listed AUTH-005. Additional transactional considerations:

- TRX-001: Idempotency & deduplication on registration
  - If request contains Idempotency-Key, persist request key in a table idempotency_keys (key, fingerprint, created_at, response_payload) to ensure safe retries.
  - Acceptance criteria: duplicate retries with same key return stored response.
  - DB: idempotency_keys(key PK, fingerprint TEXT, response JSONB, status)

- TRX-002: Compensating delete if S3 upload confirmed but DB persist fails
  - If presigned upload succeeded but photo metadata persist fails, enqueue S3 object deletion in cleanup_jobs.

- TRX-003: Booking transactional consistency
  - When creating a booking, acquire DB lock on availability rows (SELECT FOR UPDATE) to avoid double-booking; if lock cannot be acquired, return 409.


## Database schema changes (summary)

Suggested additions/changes (Liquibase changesets):

- users: id UUID PK, email VARCHAR UNIQUE, auth_server_id UUID, created_at, updated_at
- profiles: id UUID PK, user_id FK, display_name, bio TEXT, skills JSONB, latitude DOUBLE PRECISION, longitude DOUBLE PRECISION, location GEOGRAPHY(Point,4326), visibility ENUM, opt_out_search BOOLEAN, last_seen_at
- photos: id UUID PK, user_id FK, object_key TEXT, url TEXT, width INT, height INT, is_primary BOOLEAN, deleted_at TIMESTAMP
- availability: id UUID PK, user_id FK, start_ts TIMESTAMP WITH TIME ZONE, end_ts TIMESTAMP WITH TIME ZONE, recurrence VARCHAR, timezone VARCHAR
- bookings: id UUID PK, requester_user_id FK, target_user_id FK, start_ts TIMESTAMP WITH TIME ZONE, end_ts TIMESTAMP WITH TIME ZONE, status ENUM
- conversations: id UUID PK, participant_ids JSONB, last_message_at TIMESTAMP
- messages: id UUID PK, conversation_id FK, sender_id FK, text TEXT, created_at TIMESTAMP
- ratings: id UUID PK, booking_id FK, rater_user_id FK, score INT, comment TEXT
- audit_log: id UUID PK, actor_user_id UUID NULL, action VARCHAR, details JSONB, created_at TIMESTAMP
- cleanup_jobs: id UUID PK, resource_type VARCHAR, resource_id TEXT, status VARCHAR, attempt_count INT, payload JSONB, created_at
- idempotency_keys: key VARCHAR PK, fingerprint TEXT, response JSONB, created_at

Indexes:
- GiST/BRIN on profiles.location for geo queries
- Unique index on users.email
- Indexes on bookings (target_user_id, start_ts)

Note: PostGIS must be enabled in Postgres or fallback to earthdistance. Add Liquibase changeset to create extension if infrastructure allows.

---

## Security & Privacy Considerations

- PII: store minimal PII (email, display name). Do not store raw passwords. Passwords are stored in Keycloak only.
- Data minimization: profiles.skills and bio should be free-text but consider rate-limiting and length limits.
- Password policy: delegate to Keycloak. Enforce minimum 8 chars, at least 1 uppercase, 1 digit, 1 symbol (adjustable by realm policy).
- Rate limiting & brute-force:
  - Authentication endpoints: aggressive rate limits (e.g., 5 failed login attempts per 10 minutes, then cooldown). Consider Keycloak brute-force protection.
  - Messaging & registration: per-user and per-IP rate limits to mitigate spam.
- Transport: TLS mandatory between client and server; S3 signed URLs must use https.
- Audit logging: all admin actions, user provisioning, deletion and cleanup actions must be recorded in audit_log with actor id and correlation id.
- Admin roles & scopes:
  - Realm role: sparring_admin — required for /api/v1/admin/* endpoints
  - Support role: support — read-only audit access
  - Service account for KeycloakAdminService must be restricted to only user management scopes
- Secrets handling: store Keycloak admin credentials and S3 credentials in Kubernetes Secrets, not version-controlled.
- GDPR / user data removal: provide endpoint for data export and deletion (right to erasure). Deletion must remove DB rows, photos in S3, and Keycloak user (compensating actions if any step fails must be recorded and retried).

---

## CI / Test guidance (unit / integration / E2E)

- Unit tests: fast, mocked dependencies (mock KeycloakAdminService, mock S3 client). Run on every commit in CI.
- Integration tests:
  - Use Testcontainers for Postgres (optionally PostGIS-enabled), MinIO, and lightweight Keycloak image for integration. Keep these tests in an integration test profile and run on CI's integration workflow.
  - Heavy Keycloak tests (realm import, full OIDC flows) should be tagged (e.g., @KeycloakIntegration) and run on self-hosted runners or scheduled pipelines to avoid cold-start flakiness.
  - Tests that require real S3/MinIO should run against Testcontainers MinIO and assert presigned flows and HEAD/GET operations.
- E2E (Playwright): run against a deployed test environment (docker-compose or k8s test namespace). The existing run-e2e.ps1 should be extended to set environment variables for MinIO and Keycloak.
- Selective skipping: mark tests that require slow Keycloak operations with @Slow or @KeycloakIntegration; CI should skip them by default to keep shared runners fast.
- Test data seeding: provide SQL or Liquibase changelog for test seed data or use factory patterns in tests.
- Local developer guidance: provide a docker-compose.dev.yml with Postgres+MinIO+Keycloak for local integration testing.

---

## Open questions & assumptions (for product/infra decisions)

1. Use PostGIS vs earthdistance/cube? PostGIS is recommended for future-proof geo queries, but requires DB extension support in managed DBs.
2. Upload strategy: presigned URLs (preferred) vs server-side multipart upload? Presigned reduces server bandwidth and scales better.
3. Do we require email verification on registration? Current system provisions Keycloak user — should we mark users unverified until email click? (UX decision.)
4. Real-time messaging: WebSocket vs polling? MVP will use REST + poll; consider WebSocket/Socket.IO later.
5. ID format: UUID for all IDs? Recommended.
6. Retention policy for messages and audit logs: how long to keep PII?
7. Do we allow Resource Owner Password Credentials (ROPC) for token exchange? Prefer OIDC redirect flows for security.
8. Rate-limiting thresholds and abuse mitigation specifics.

---

## Next steps checklist for engineering

- [ ] Create issues for each MVP story (AUTH-001, PROFILE-001, SEARCH-001, PHOTO-001)
- [ ] Implement MapStruct DTOs and request/response models for the above endpoints
- [ ] Add Liquibase changesets for profiles.location (PostGIS) and photos/availability/bookings
- [ ] Implement transactional compensation in UserService (AUTH-005)
- [ ] Add idempotency key table & middleware for registration
- [ ] Implement presigned upload flow and confirm endpoint for photos
- [ ] Add Testcontainers profiles and mark heavy Keycloak tests with @KeycloakIntegration
- [ ] Update Dockerfile / k8s manifests to include readiness checks for Keycloak and MinIO
- [ ] Write unit, integration and E2E tests per story (see tests below)

---

## Suggested Tests by type (summary)

- Unit: Service logic, validation, mapping (MapStruct), KeycloakAdminService interactions mocked, PhotoService presign logic mocked S3
- Integration: RegistrationIntegrationIT (Postgres + Keycloak Testcontainer), PhotoIntegrationIT (MinIO), SearchIntegrationIT (Postgres+PostGIS)
- E2E: Playwright flows for register -> profile -> search -> upload photo -> booking (use run-e2e.ps1)

---

## Implementation mapping (files & components to touch)

Likely files / components to add or modify in repo (adjust package names to your codebase):

- Services:
  - src/main/java/.../service/UserService.java (register, transactional compensation)
  - src/main/java/.../service/KeycloakAdminService.java (exists; extend with deleteUser, findByEmail if not present)
  - src/main/java/.../service/ProfileService.java
  - src/main/java/.../service/SearchService.java
  - src/main/java/.../service/PhotoStorageService.java (S3 presign, confirm)
  - src/main/java/.../service/BookingService.java
  - src/main/java/.../service/MessagingService.java
  - src/main/java/.../service/AuditService.java

- Controllers:
  - src/main/java/.../controller/AuthController.java (register endpoint exists)
  - src/main/java/.../controller/ProfileController.java
  - src/main/java/.../controller/SearchController.java
  - src/main/java/.../controller/PhotoController.java
  - src/main/java/.../controller/BookingController.java
  - src/main/java/.../controller/MessageController.java
  - src/main/java/.../controller/AdminController.java

- Entities / Repositories:
  - src/main/java/.../entity/User.java (exists)
  - src/main/java/.../entity/Profile.java (exists)
  - src/main/java/.../entity/Photo.java
  - src/main/java/.../entity/Availability.java
  - src/main/java/.../entity/Booking.java
  - src/main/java/.../entity/Conversation.java
  - src/main/java/.../entity/Message.java
  - src/main/java/.../entity/Rating.java
  - src/main/java/.../entity/AuditLog.java

  - src/main/java/.../repository/UserRepository.java
  - src/main/java/.../repository/ProfileRepository.java
  - src/main/java/.../repository/PhotoRepository.java
  - src/main/java/.../repository/BookingRepository.java
  - src/main/java/.../repository/MessageRepository.java
  - src/main/java/.../repository/AuditLogRepository.java

- Config / infra:
  - src/main/resources/db/changelog/db.changelog-master.xml (Liquibase changelogs)
  - src/main/resources/application.yml (add s3, keycloak timeouts, rate-limits)
  - Dockerfile (update readiness checks if needed)
  - k8s/*.yaml (secrets for KEYCLOAK_ADMIN, S3 creds; init containers for DB extensions like PostGIS)

- Tests:
  - src/test/java/.../integration/RegistrationIntegrationIT.java (Testcontainers Postgres + Keycloak)
  - src/test/java/.../integration/PhotoIntegrationIT.java (MinIO)
  - src/test/java/.../unit/UserServiceTest.java
  - Playwright tests: e2e/specs/register.spec.ts (existing scaffolding extend)

- Others:
  - MapStruct mappers: src/main/java/.../mapper/*Mapper.java
  - DTOs: src/main/java/.../api/*Dto.java

Add/modify Liquibase changelog files under src/main/resources/db/changelog/ to create new tables and indices.

---

If you want, I will also:

- create GitHub issues (not requested), or
- generate liquibase changeset templates for the tables above, or
- produce example MapStruct mappers and DTOs for the MVP endpoints.


---

Generated for file: docs/backend-spec.md


