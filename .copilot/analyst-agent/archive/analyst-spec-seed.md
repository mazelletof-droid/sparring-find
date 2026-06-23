# Analyst Agent Seed: "analyst-spec-writer"

Purpose
- Seed file for the analyst agent that produces a developer-focused Markdown backend specification for the Sparring Finder project.

Location
- .copilot\analyst-agent\analyst-spec-seed.md

How to run (recommended)
- Ask this Copilot assistant to run the analyst agent using this seed. Example prompt to the assistant: "Run the analyst-spec-writer agent using seed .copilot/analyst-agent/analyst-spec-seed.md and save output to docs/backend-spec.md".
- The assistant will launch the agent synchronously and return the generated Markdown; it can also save it into the repo at docs/backend-spec.md on request.

How to run (automation / advanced)
- Provide the contents of this file as the agent prompt to whatever automation launches Copilot sub-agents (task tooling). Example pseudocode:

  task({
    agent_type: 'general-purpose',
    name: 'analyst-spec-writer',
    prompt: readFile('.copilot/analyst-agent/analyst-spec-seed.md'),
    mode: 'sync',
    reasoning_effort: 'high',
    context_tier: 'long_context'
  })

What this seed contains
- Full analyst prompt + project context and strict output requirements (Table of Contents, prioritized epics, testable acceptance criteria, example JSON payloads, DB changes, CI guidance).

KEY_CONTEXT (edit before re-run if things changed)
- Java 21, Spring Boot (Maven)
- PostgreSQL (+PostGIS recommended), Liquibase
- Keycloak used for auth; KeycloakAdminService exists and supports client-credentials and password grant
- S3-compatible storage (MinIO dev, S3 prod)
- Existing: /api/v1/auth/register, User and Profile entities, Liquibase changelog, Dockerfile, k8s manifests, unit+integration tests, Playwright scaffolding

Recommended output path
- docs/backend-spec.md (review before commit)

Recommended run options
- mode: sync
- reasoning_effort: high
- context_tier: long_context

Maintenance
- Keep this seed updated when core infra (DB type, Keycloak details, S3 provider) or project scope changes.
- After generation, review spec and commit to docs/backend-spec.md with a short summary.

Support
- If you want, ask the assistant: "Run analyst agent now and persist result to docs/backend-spec.md" and I will run it and commit the file.

