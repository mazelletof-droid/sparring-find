Sparring Finder — Backend Specification

Summary of assumptions
- MVP features prioritized: auth/registration, profiles, photo upload, geospatial matching, messaging, admin/Keycloak management.
- Registration: local registration with Keycloak provisioning (idempotent APIs).
- Profiles: include coordinates (lat/lon) + optional public bio; privacy settings supported.
- Photo uploads: <= 10 MB; JPG/PNG/WebP; optional moderation workflow.
- Matching: support radius search and k-nearest neighbors (k-NN).
- Messaging: realtime WebSocket + persistent messages.
- Admin: automatic idempotent Keycloak provisioning APIs.

Table of Contents
- Auth & Registration
- User Profiles
- Photo Upload & Storage
- Geospatial Search & Matching
- Messaging
- Admin & Keycloak Management
- APIs & Docs
- Data Migrations & DTOs
- Testing & CI
- Observability & Security
- Cross-cutting rules

Acceptance Criteria (Given / When / Then)

Auth & Registration
- Given a valid registration payload, when submitted, then create local user, provision Keycloak user, store authServerId, and return 201 with user DTO.
- Given Keycloak provisioning fails, when DB user was created, then rollback DB create (transactional) and return 502 with error details.
- Given same email re-submitted, when user already exists, then return 200 with existing user (idempotent) or 409 if conflicting fields differ.
- Passwords hashed (bcrypt) and validation rules enforced (min length, complexity).

User Profiles
- Given authenticated user posts profile (coords + bio), when valid, then save profile with geolocation (PostGIS geometry), return 200 and profile DTO.
- Given privacy set to private, when profile read by non-owner, then return limited fields only.
- Given profile update, when coordinates change, then update geospatial index and lastUpdated timestamp.
- Given profile delete, when requested, then soft-delete (isActive=false) and stop appearing in match searches.

Photo Upload & Storage
- Given authenticated user uploads file, when content-type and magic-bytes match JPG/PNG/WebP and size <= 10MB, then store to S3 (user/<id>/photos/<uuid>), record metadata, and return 201 with URL and photo id.
- Given moderation enabled, when photo uploaded, then mark status=pending and do not publish publicly until approved; return pending state.
- Given invalid content-type/size, when upload attempted, then reject with 400 and descriptive message.
- Stored objects use server-side encryption and presigned URLs for downloads.

Geospatial Search & Matching
- Given search request with lat/lon+radius, when executed, then return users within radius ordered by distance and score, paginated.
- Given search with k parameter (k-NN), when executed, then return top-K nearest users with distances.
- Given filters (skill, availability, maxDistance), when applied, then result set respects filters and score ranking.
- Queries use spatial index (GIST/GiST) and KNN where appropriate; response includes distance field.

Messaging
- Given authenticated client opens WebSocket with valid JWT, when connected, then subscribe to user's messaging channel and receive real-time messages.
- Given message send via WS or REST, when recipient exists, then persist message, attempt immediate WS delivery, mark delivered if ACK received; otherwise mark pending and retry.
- Given message persisted, when recipient later connects, then deliver pending messages and mark delivered/read as appropriate.
- Enforce message size limit and rate limiting; reject spam-like patterns.

Admin & Keycloak Management
- Given admin credentials call provisioning API, when invoked, then perform idempotent creation/update of realm/clients/roles/users and return summary (created/updated/unchanged).
- Given provisioning partially fails, when error occurs, then produce an audit log and safe partial rollback or clear compensating actions documented.
- Admin endpoints require admin role and are logged for audit.

APIs & Docs
- Given code deployed, when /swagger-ui/index.html is opened, then OpenAPI docs describe all /api/v1 endpoints with request/response examples.
- All public APIs return consistent DTOs (MapStruct-mapped), use versioned paths (/api/v1), and include helpful error payloads (code, message, details).

Data Migrations & DTOs
- Given schema changes, when added, then a Liquibase changeset is created under src/main/resources/db/changelog and tested in CI.
- Entities are not returned directly; DTOs and MapStruct mappers cover all public API shapes.
- Migrations are idempotent and reversible where possible.

Testing & CI
- Unit tests: services and mappers with >70% coverage for critical modules (auth, profile, matching).
- Integration tests: Testcontainers spin up Postgres, Keycloak (or Keycloak mock), and MinIO; tests validate provisioning, uploads, and geosearch.
- E2E: Playwright checks major user flows (register, upload photo, search, message).
- CI runs mvn test (unit) and separate job runs mvn verify (ITs + E2E) with Playwright browsers installed.

Observability & Security
- Management endpoints: /actuator/health, /actuator/info, /actuator/prometheus enabled; OTLP exporter configured.
- Input validation, rate limits on write endpoints, and size limits enforced; secrets come from env vars (no secrets in repo).
- JWT validation for resource-server, role-based access control for admin APIs, and CORS configured safely.

Cross-cutting transactional & error-handling rules
- For operations affecting external systems (Keycloak, S3): use transactions + compensating actions; if external step fails, rollback DB and run compensator (e.g., delete created Keycloak user if DB commit fails).
- All public endpoints must return consistent error codes and log structured audit events for admin actions.

Next steps
- Convert acceptance criteria into API contracts (paths, payloads, response codes) and DB schema changes (Liquibase templates).
- Implement MapStruct DTOs and mappers for public APIs.
- Add integration tests (Testcontainers) for Keycloak and MinIO flows.

Generated by analyst-spec-agent session.
