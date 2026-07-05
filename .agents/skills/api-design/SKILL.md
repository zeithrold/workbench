---
name: api-design
description: >-
  Workbench REST API design principles: RESTful conventions, Direct Resource payloads,
  Ref/Embed field organization, RFC 7807 errors, auth annotations, and OpenAPI patterns.
  Use when designing or implementing API endpoints, controllers, request/response types, or reviewing API changes.
---

# Workbench API Design

Source of truth for HTTP API shape in this monolith. For generic Spring patterns see [springboot-patterns](../springboot-patterns/SKILL.md) — **do not** use its `ApiError` or `Page<T>` examples here.

## When to Activate

- New controller or endpoint
- Request/response type design (`*Request`, `*Response`)
- API review or OpenAPI documentation
- Pagination, error format, or payload organization questions

## Architecture

```
Client → Controller (*Request → *Command) → Service (*View with *Summary) → Controller (*Response) → Client
```

| Layer | Module | Naming |
|-------|--------|--------|
| Controller + HTTP boundary types | `workbench-web` | `*Request`, `*Response` |
| Business logic + view assembly | `workbench-service` | `*Command`, `*View` |
| Domain / ports / exchange projections | `workbench-core` | `*Record`, `*Query`, `*Projection`, `*Summary` |

`*Summary` is a domain-level lightweight exchange projection (public `id` + display fields), shared by Service `*View` and Web `*Response`. It lives in `workbench-core/.../common/summary/`, not in the web module.

- Controllers are `suspend fun`; HTTP adaptation only — no business logic.
- Avoid `BO`/`PO`/`VO`/`DTO` suffixes and `*Dtos.kt` files (see [README.md](../../../README.md)).
- Canonical controller: `workbench-web/.../project/ProjectController.kt`

## Thin Controller (required)

Each endpoint method body target **≤ 8 lines**. Controllers **only** adapt HTTP:

| Allowed | Forbidden |
|---------|-----------|
| `*Request` → `*Command` field mapping | Inject `*Repository` (bypass Service) |
| Call **one** Service method per use case | Multi-service orchestration, domain branching |
| `*View` → `*Response` / `Response.from(view)` | `SecurityContextHolder`, handwritten `currentPrincipal()` |
| HTTP semantics: status, `Location`, `Set-Cookie` | Domain rules, permission assembly, state machines |
| `TenantRequestContext`, `AuthenticatedPrincipal` params | `PermissionCondition` or Record mapping in Controller |

HTTP-only helpers live in `workbench-web/.../api/http/` (`HttpClientContext`, `SessionCookieWriter`) and `AuthenticatedPrincipalResolver`.

```kotlin
suspend fun create(
  @Valid @RequestBody request: CreateProjectRequest,
  tenantContext: TenantRequestContext,
): ResponseEntity<ProjectResponse> {
  val view = projectService.create(request.toCommand(tenantContext))
  val response = ProjectResponse.from(view)
  return ResponseEntity.created(locationFor(response)).body(response)
}
```

Public id resolution (`tenantId` string → UUID) belongs in **Service** (`PublicIdResolver`), not Controller.

## Response Envelope — Direct Resource

Return the resource or `List<T>` directly. No `{ "data": … }` wrapper.

| Operation | Status | Body |
|-----------|--------|------|
| Get one / list / patch | `200` | Resource or `List<Resource>` |
| Create | `201` | Created resource (+ `Location` header when practical) |
| Delete | `204` | Empty |
| Async accept | `202` | Optional status body |

Use `ResponseEntity<T>` only when setting headers (e.g. `Set-Cookie` in auth flows).

**Rejected:** JSON:API envelopes, Stripe `object` discriminator, generic `{ data, meta }`.

## Payload — Universal `id` + Ref/Embed

### Primary keys

Public id prefixes (three letters + ULID via `workbench-core/.../ids/PublicId.kt`):

| Entity | Prefix |
|--------|--------|
| Tenant | `ten_` |
| Membership | `tmb_` |
| Project | `prj_` |
| User | `usr_` |
| Role | `rol_` |
| Policy | `pol_` |
| Role assignment | `ras_` |
| Login method | `lmg_` |
| Bearer token | `btk_` |

- Resource public id field is always **`id`** in JSON.
- Never `apiId` in JSON; never internal `UUID` in request/response bodies.
- URL path params: `/api/projects/{id}`.

```kotlin
data class ProjectResponse(val id: String, ...)  // id = record.apiId.value
```

### Ref vs Embed (core rule)

**Request vs response asymmetry:**
- **Requests:** identify entities with `{entity}Id` public strings only — **no nested entity objects**.
- **Responses:** **Embed** when the same entity needs id + display metadata; **Ref** when only a bare FK to a different entity.

| Pattern | Side | When | Shape |
|---------|------|------|-------|
| Ref | Request | Always for entity references | `tenantId`, `loginMethodId`, `userId` |
| Ref | Response | Bare FK, different entities, no extra attrs | `userId`, `roleId`, `projectId` |
| Embed | Response | Same entity: id + any other field | `tenant: { id, name, slug }` |
| Own fields | Both | Belong to this resource | `name`, `identifier`, `createdAt` |

**Hard rules:**
- ✅ Request: `tenantId` + `loginMethodId` (different entities, flat id refs)
- ✅ Response: `tenant: {…}` + `loginMethod: {…}` (embed per entity)
- ✅ Response: `userId` + `roleId` + `projectId` (different entities, bare FKs)
- ❌ Prefix-flatten one entity: `tenantId`+`tenantName`, `loginMethodCode`+`loginMethodKind`
- ❌ Nested entity objects in request bodies
- ❌ Prefixed names inside embed objects (`loginMethod: { loginMethodCode: … }`)

**Embed inner fields** use the entity's own attribute names:

```json
{
  "tenant": { "id": "ten_01J…", "name": "Acme", "slug": "acme" },
  "loginMethod": { "id": "lmg_01J…", "code": "password", "kind": "PASSWORD", "name": "Password" }
}
```

Reuse core `*Summary` types in responses: `TenantSummary`, `UserSummary`, `LoginMethodSummary`, `RoleSummary` from `workbench-core/.../common/summary/`.

**Session aggregates** (`SessionResponse`, `LoginResponse`) may nest `user`, `activeTenant`, `bearerToken` as composition roots. Children follow the same `id` + Ref/Embed rules.

### HTTP type file layout

| Purpose | Type | File |
|---------|------|------|
| Request body | `CreateProjectRequest` | Same file as controller, or `{Domain}Requests.kt` |
| Response body | `ProjectResponse` | Same file as controller, or `{Domain}Responses.kt` |
| Cross-domain embed | `TenantSummary` | `workbench-core/.../common/summary/{Name}.kt` |

No `dto/` package directory. No `*Dtos.kt`.

### Scalar naming (S2)

| Type | Convention |
|------|------------|
| JSON keys | camelCase |
| Timestamps | `*At`, ISO-8601 with offset (`createdAt`, `expiresAt`); keep domain names like `validFrom`/`validTo` |
| Booleans | No `is` prefix (`builtin`, not `isBuiltin`) |
| Enums | UPPER_SNAKE strings (`"ALLOW"`, `"TENANT"`) |

## RESTful Principles

Resource-oriented URLs and HTTP semantics first; payload rules apply on top.

### URLs

- **Default: nouns for CRUD** — `/api/projects`, `PATCH /api/projects/{id}`; not `/api/projects/create` or `/api/projects/{id}/update`
- **Exception: verb action sub-paths** nested under a resource when CRUD does not fit. Use kebab-case action segment.

| Kind | When | Example | Method / status |
|------|------|---------|-----------------|
| **Read action** | Complex query body; GET query params insufficient | `POST …/work-items/search` | `POST` → `200` (read-only, no side effects) |
| **Business action** | Domain operation that is not a simple field patch on one resource | `POST …/work-items/{id}/transition`, `POST …/projects/{id}/archive` | `POST` → `200` / `201` / `202` / `204` |

**Read actions:** `search`, `export`, `preview` — same safety as GET; safe to retry.

**Business actions:** `archive`, `transition`, `merge`, `publish`, `bulk-update`, … — use when the operation is a **domain command** (state machine, multi-entity side effects, workflow step). Prefer `PATCH` for simple scalar updates on a single resource; prefer collection/member URLs for plain create/delete.

**Action path rules:**
- Nest under the relevant resource: `{resources}/{id}/{action}` or `{resources}/{id}/{children}/{action}`
- Top-level RPC (`POST /api/do-something`) — avoid; anchor to a resource id
- Document in OpenAPI; use `@Authorize` with a specific action code
- Use `@Idempotent` + `Idempotency-Key` when retries must not double-apply

- Plural collections: `/api/projects`
- Multi-word segments: kebab-case (`/api/auth/magic-link/request`)
- Base: `/api/*` — no URL version segment
- Admin: `/api/admin/*`
- `{id}` in paths = public string id (`prj_…`)
- Simple filter/sort/page via query params: `?identifier=CORE&limit=20&pageToken=…`

### HTTP methods

| Method | Use | Status |
|--------|-----|--------|
| `GET` | Read (safe, no body) | `200` |
| `POST` | Create, protocol step, or resource action | `201` (create), `200`/`202` (action), `204` |
| `PATCH` | Partial update | `200` |
| `DELETE` | Remove / expire | `204` |
| `PUT` | Full replace — **avoid** unless needed | `200` |

- No state-changing GET (except documented OAuth/magic-link callbacks).
- POST create: `201` + `Location: /api/projects/prj_01J…` when practical.
- DELETE: `204` empty body.

### URL patterns

```
GET    /api/projects           → List<ProjectResponse>
POST   /api/projects           → ProjectResponse (201)
GET    /api/projects/{id}      → ProjectResponse
PATCH  /api/projects/{id}      → ProjectResponse
DELETE /api/projects/{id}      → 204
GET    /api/projects/{id}/work-items
POST   /api/projects/{id}/work-items/search      → List<WorkItemResponse> (read action)
POST   /api/projects/{id}/archive                → ProjectResponse (business action)
POST   /api/projects/{id}/work-items/{id}/transition  → WorkItemResponse (business action)
```

### Representations

- `application/json`, UTF-8
- Errors: RFC 7807 ProblemDetail (`application/problem+json`)
- Version: `X-Workbench-API-Version: yyyy-MM-dd` header (not URL)
- Warnings: `X-Workbench-Warning` JSON header on **2xx only** when non-blocking business risks exist (see Warnings section)

### Statelessness

Auth via `WORKBENCH_SESSION` cookie or `Authorization: Bearer`. Tenant resolved from session server-side. No server-side client coupling beyond stored session/token.

### Protocol endpoints (exceptions)

Non-CRUD flows under clear namespaces — document in OpenAPI:

| Endpoint | Purpose |
|----------|---------|
| `POST /api/auth/login`, `POST /api/auth/logout` | Session protocol |
| `POST /api/auth/federated/authorize`, OAuth/SAML callbacks | Federated auth |
| `POST /api/auth/magic-link/request`, `GET …/verify` | Magic link |
| `GET /api/session`, `PATCH /api/session` | Session aggregate |

If a resource persists and has an id, standard CRUD uses resource URLs. Use verb action sub-paths (above) for complex reads and domain commands — not for CRUD shortcuts (`/create`, `/update`).

## Errors — RFC 7807 ProblemDetail

Throw typed exceptions from `workbench-core`; `GlobalExceptionHandler` renders ProblemDetail.

```json
{
  "type": "https://api.ink.doa/workbench/problems/permission-denied",
  "title": "Permission Denied",
  "status": 403,
  "detail": "Actor lacks permission.project.create on project."
}
```

| Exception | Status |
|-----------|--------|
| `ResourceNotFoundException` | 404 |
| `PermissionDeniedException` | 403 |
| `AuthenticationFailedException` | 401 |
| `TenantNotSelectedException` | 409 |
| `InvalidRequestException` / `IllegalArgumentException` | 400 |
| `MethodArgumentNotValidException` | 400 |
| `DataAccessException` | 503 |

Never return `200` with an error body. Security filter may return plain `401` without ProblemDetail — do not add a second error format in new code.

## Warnings — `X-Workbench-Warning`

Non-blocking business risks on **successful** responses (`2xx`). Failures remain ProblemDetail + non-2xx.

| Signal | When |
|--------|------|
| ProblemDetail | Operation must not succeed (validation, auth, conflict) |
| Warning header | Operation succeeded but client should know about risk |

**Rules**

- Omit header when no warnings.
- Body stays Direct Resource — no `_warnings` envelope.
- Value is JSON: `{ "version": 1, "items": [ { "code", "severity", "message", "meta" } ] }`.
- `meta` is **typed** with required `kind` discriminant; embed primary resources via core `*Summary` (`project: { id, identifier, name }`).
- Each `WorkbenchWarningCode` binds to exactly one meta type (`workbench-core/.../common/warning/`).
- Endpoints that may emit warnings: `@MayReturnWarnings` (OpenAPI documents the header).

**Example**

```json
{
  "version": 1,
  "items": [
    {
      "code": "project.destroy.scheduled",
      "severity": "risk",
      "message": "Project destruction has been scheduled.",
      "meta": {
        "kind": "projectDestroyScheduled",
        "project": { "id": "prj_01J…", "identifier": "WB", "name": "Workbench" },
        "deleteReason": "cleanup"
      }
    }
  ]
}
```

## Auth, Tenant, Authorization

From `workbench-web/.../api/Annotations.kt`:

| Annotation | Usage |
|------------|-------|
| `@Authenticated` | Marker; enforced by Spring Security |
| `@TenantScoped` | Requires `TenantRequestContext` parameter |
| `@Authorize(action, resource)` | Permission check via `InfrastructureAspect` |
| `@Audit(action)` | Structured audit log |
| `@RequirePermission` | **Deprecated** — use `@Authorize` |

```kotlin
@PostMapping
@Authenticated
@TenantScoped
@Authorize(action = "project.create", resource = "project")
suspend fun create(
  @Valid @RequestBody request: CreateProjectRequest,
  tenantContext: TenantRequestContext,
): ProjectResponse
```

## Versioning

- Header: `X-Workbench-API-Version: 2026-07-03`
- Default when omitted: `ApiVersion.Default`
- OpenAPI `info.version` stays in sync
- Breaking changes: new date version + migration notes (not `/v2` in URL)

## Validation & request types

- `@Valid @RequestBody` on all request bodies
- Jakarta constraints: `@NotBlank`, `@Pattern`, `@Size`
- OpenAPI: `@field:Schema(example = …)` on non-obvious fields
- Response mapping: `companion object { fun from(view): XResponse }`
- Co-locate small types with controller; extract to `{Domain}Requests.kt` / `{Domain}Responses.kt` when reused or >~30 lines combined

## OpenAPI (required for new endpoints)

- `@Tag` on controller
- `@Operation(summary, description)` per endpoint
- `@StandardErrorResponses` (or explicit `@ApiResponse`) for 400, 401, 403, 404, 409
- `@SessionSecured` / `@SecurityRequirement` when not public
- Config: `workbench-web/.../api/OpenApiConfiguration.kt` — Session + Bearer schemes
- Frontend Orval reads `/api/openapi` — field renames are breaking

## Pagination (future)

Cursor-based, Direct Resource compatible:

```
GET /api/projects?limit=20&pageToken=eyJ…
→ 200 List<ProjectResponse>
Header: X-Next-Page-Token
```

See [reference.md](reference.md) for full spec.

## New Endpoint Checklist

**REST**
- [ ] CRUD uses noun URLs; verb segment only for nested read/business actions when CRUD is insufficient
- [ ] Read actions (`search`, …) are side-effect free; business actions documented with correct status
- [ ] Correct HTTP method and status (`201`+`Location` on create, `204` on delete)
- [ ] No state-changing GET (except documented auth callbacks)
- [ ] `{id}` path param is public string id
- [ ] Filters/pagination in query params

**Payload**
- [ ] `*Request` / `*Response` naming
- [ ] Request: `{entity}Id` refs only; no nested entity objects
- [ ] Response: embed same-entity multi-field data under `{entity}`
- [ ] No prefix-flattening; public `id` string; no UUID in JSON
- [ ] Booleans without `is`; timestamps `*At` ISO-8601

**Implementation**
- [ ] Thin controller (≤8 lines); Service owns orchestration and id resolution
- [ ] `@Valid` + constraints; tenant/auth annotations
- [ ] Map to `*Command`; throw domain exceptions
- [ ] OpenAPI annotations; test happy + failure paths

## Anti-patterns

**REST:** CRUD verb tunneling (`/create`, `/update`, `/delete` on resources), top-level RPC URLs, state-changing GET, wrong status codes, UUID in public URLs

**Payload:** `apiId` vs `id` mix, prefix-flattening, nested entities in requests, `is*` booleans

**General:** controller business logic, repository injection in controllers, `Map<String, Any>` bodies, response envelopes, `@RequirePermission`, missing `TenantRequestContext` on `@TenantScoped`, `*Dtos.kt` files

## Additional Resources

- [workbench-development](../workbench-development/SKILL.md) — end-to-end dev workflow, verification, PR/CI
- [reference.md](reference.md) — REST deep-dive, status codes, pagination, migration, OpenAPI templates
- [examples.md](examples.md) — CRUD patterns, Ref/Embed JSON, before/after migrations
