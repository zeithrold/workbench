# API Design Reference

Detailed rules for Workbench HTTP APIs. Read [SKILL.md](SKILL.md) first.

## REST Deep-Dive

### Method / idempotency matrix

| Method | Safe | Idempotent | Body | Typical success |
|--------|------|------------|------|-----------------|
| GET | Yes | Yes | No | 200 |
| HEAD | Yes | Yes | No | 200 |
| POST | No | No* | Yes | 201 / 200 / 202 |
| PATCH | No | No** | Partial | 200 |
| PUT | No | Yes | Full | 200 |
| DELETE | No | Yes | Rare | 204 |

\* Use `Idempotency-Key` header (`@Idempotent`) for safe POST retries when adopted.  
\** Design PATCH to be practically idempotent (same patch → same state).

### Collection / member / sub-resource catalog

```
GET    /api/{resources}                    List
POST   /api/{resources}                    Create → 201 + Location
GET    /api/{resources}/{id}               Read one
PATCH  /api/{resources}/{id}               Partial update
DELETE /api/{resources}/{id}               Delete → 204
GET    /api/{resources}/{id}/{children}    Sub-collection
POST   /api/{resources}/{id}/{children}    Create child
POST   /api/{resources}/{id}/{children}/search        Read action (complex query)
POST   /api/{resources}/{id}/{action}                   Business action on member
POST   /api/{resources}/{id}/{children}/{id}/{action} Business action on child
```

`{id}` = public typed string (`prj_01JABCDEFGHJKMNPQRSTVWXYZ0`).

### Verb action sub-paths

Use when standard CRUD (GET/POST/PATCH/DELETE on collection/member) is not enough. Always nest under the resource the action applies to.

#### Read actions

Complex query — body too large or structured for GET query params:

```
POST /api/projects/{id}/work-items/search
→ 200 List<WorkItemResponse>
```

| Rule | Detail |
|------|--------|
| When | Complex `where` AST, many ids, large filter body |
| Semantics | **Read-only** — no mutations; safe to retry |
| Status | `200` |
| Segments | `search`, `export`, `preview` |
| Prefer GET | Simple filters still use `?query=params` |

#### Business actions

Domain commands that are not a simple PATCH on one resource:

```
POST /api/projects/{id}/archive
→ 200 ProjectResponse

POST /api/projects/{id}/work-items/{id}/transition
{"status": "IN_PROGRESS"}
→ 200 WorkItemResponse

POST /api/projects/{id}/work-items/bulk-update
{"ids": ["wit_…", "wit_…"], "patch": {"assigneeId": "usr_…"}}
→ 200 List<WorkItemResponse>
```

| Rule | Detail |
|------|--------|
| When | State machine transition, workflow step, multi-entity update, operation spanning fields/relations |
| Method | `POST` |
| Path | `{resource}/{id}/{action}` or `…/{children}/{id}/{action}` or `…/{children}/bulk-{action}` |
| Status | `200` + result, `201` if creates resource, `202` async, `204` if no body |
| Prefer PATCH | Single-resource scalar/partial field update |
| Prefer DELETE | Removal with no extra command payload |
| Idempotency | `@Idempotent` when retries must not double-apply |

**Still avoid:**
- CRUD shortcuts: `POST /api/projects/create`, `POST /api/projects/{id}/update`
- Top-level RPC: `POST /api/archive-project` (no resource anchor)
- Verb actions when PATCH/DELETE on member URL suffices

### Location header on 201

```http
HTTP/1.1 201 Created
Location: /api/projects/prj_01JABCDEFGHJKMNPQRSTVWXYZ0
Content-Type: application/json

{"id":"prj_01J…","identifier":"CORE","name":"Core Platform",…}
```

```kotlin
return ResponseEntity
  .created(URI.create("/api/projects/${response.id}"))
  .body(response)
```

### Status code decision tree

1. Success read → `200` + body
2. Success create → `201` + body + `Location`
3. Success delete → `204` no body
4. Async queued → `202` + optional status body
5. Validation / malformed input → `400` ProblemDetail
6. Not authenticated → `401`
7. Authenticated but denied → `403`
8. Resource missing (or hide existence) → `404`
9. State conflict (tenant not selected, duplicate) → `409`
10. Wrong HTTP method → `405`
11. Rate limited (future) → `429`
12. DB / infra unavailable → `503`

Do not use `200` with error payload. Prefer `409` over `400` for business state conflicts.

### Protocol endpoint registry

| Method | Path | Type | Notes |
|--------|------|------|-------|
| POST | `/api/auth/login` | Auth protocol | Sets session cookie |
| POST | `/api/auth/logout` | Auth protocol | Clears session |
| GET | `/api/auth/memberships` | Read | List tenant memberships |
| GET | `/api/auth/login-options` | Read | Discovery before login |
| POST | `/api/auth/tokens` | Create | Bearer token issuance |
| DELETE | `/api/auth/tokens/{id}` | Delete | Revoke token |
| POST | `/api/auth/federated/authorize` | Auth protocol | Start OAuth/SAML |
| GET | `/api/auth/oauth2/callback` | Auth protocol | Browser redirect |
| POST | `/api/auth/saml2/acs` | Auth protocol | SAML assertion |
| POST | `/api/auth/magic-link/request` | Auth protocol | Send email |
| GET | `/api/auth/magic-link/verify` | Auth protocol | Email link (GET required) |
| GET | `/api/session` | Session aggregate | Not `/sessions/{id}` CRUD |
| PATCH | `/api/session` | Session aggregate | Tenant switch |
| POST | `/api/admin/permissions/actions` | Upsert | `ensureAction` — document as upsert |

Domain resources (projects, roles, policies) use standard CRUD patterns under `/api/…`.

## Request / Response Asymmetry

### Requests — Ref only

```json
{
  "tenantId": "ten_01J…",
  "loginMethodId": "lmg_01J…",
  "userId": "usr_01J…",
  "roleId": "rol_01J…",
  "projectId": "prj_01J…",
  "name": "My Project",
  "password": "secret"
}
```

- Entity references: `{entity}Id` public strings
- Credentials and free-text inputs: flat on root (`password`, `subject`, `email`)
- No `tenant: { id }` nesting in POST/PATCH bodies

### Responses — Ref or Embed

**Ref** (bare FK, different entities):

```json
{
  "id": "ras_01J…",
  "userId": "usr_01J…",
  "roleId": "rol_01J…",
  "projectId": "prj_01J…",
  "validFrom": "2026-07-02T10:00:00+00:00",
  "validTo": null
}
```

**Embed** (same entity, multiple fields):

```json
{
  "id": "tmb_01J…",
  "tenant": { "id": "ten_01J…", "name": "Acme Corp", "slug": "acme" }
}
```

### Ref vs Embed decision

1. Is this a **request**? → Ref only (`{entity}Id`)
2. Is this a **response** with multiple fields from the **same** related entity? → Embed
3. Is this a **response** with only bare FKs to **different** entities? → flat `{entity}Id`
4. Are fields **own** resource attributes? → flat top level

## Summary Type Catalog

Response embeds only. Share across controllers.

```kotlin
data class TenantSummary(
  val id: String,
  val name: String,
  val slug: String,
)

data class UserSummary(
  val id: String,
  val displayName: String,
  val primaryEmail: String?,
)

data class LoginMethodSummary(
  val id: String,
  val code: String,
  val kind: LoginMethodKind,
  val name: String,
)

data class RoleSummary(
  val id: String,
  val code: String,
  val name: String,
)

data class ProjectSummary(
  val id: String,
  val identifier: String,
  val name: String,
)
```

Place shared summaries in `workbench-core/.../common/summary/`. Each provides `companion object { fun from(record): XSummary }` with `id = record.apiId.value`.

## Exception → ProblemDetail

Handler: `workbench-web/.../api/GlobalExceptionHandler.kt`

| Exception | HTTP | Title |
|-----------|------|-------|
| `ResourceNotFoundException` | 404 | Resource Not Found |
| `PermissionDeniedException` | 403 | Permission Denied |
| `AuthenticationFailedException` | 401 | Authentication Failed |
| `TenantNotSelectedException` | 409 | Tenant Not Selected |
| `InvalidRequestException` | 400 | Invalid Request |
| `IllegalArgumentException` | 400 | Invalid Request |
| `MethodArgumentNotValidException` | 400 | Validation Failed |
| `DataAccessException` | 503 | Database Unavailable |
| `InfrastructureUnavailableException` | 503 | Infrastructure Unavailable |

Example validation error:

```json
{
  "type": "https://api.ink.doa/workbench/problems/validation-failed",
  "title": "Validation Failed",
  "status": 400,
  "detail": "identifier: must match \"^[A-Z][A-Z0-9]{1,9}$\""
}
```

## Warnings — `X-Workbench-Warning`

See [SKILL.md](SKILL.md) Warnings section. Implementation:

| Piece | Location |
|-------|----------|
| Codes / typed meta | `workbench-core/.../common/warning/` |
| Request collector | `workbench-web/.../api/warning/RequestScopedWorkbenchWarningCollector` |
| Header serialization | `workbench-web/.../api/warning/WorkbenchWarningFilter` |
| OpenAPI | `@MayReturnWarnings` on endpoints that may emit warnings |

Header constraints: max 8 items, max 4KB UTF-8; overflow adds `warning.truncated` item.

## PATCH Semantics

- Include only mutable fields in `*Request`
- `null` on optional field → **clear** the field (explicit null semantics)
- Omitted field → leave unchanged
- Document per-endpoint if a field cannot be cleared

## Pagination

Cursor-based (preferred), Direct Resource compatible:

```
GET /api/projects?limit=20&pageToken=eyJ…
```

| Param | Rule |
|-------|------|
| `limit` | Default 20, max 100 |
| `pageToken` | Opaque cursor from previous response |
| `sort` | Optional, e.g. `-createdAt` (future) |

Response:

```http
HTTP/1.1 200 OK
X-Next-Page-Token: eyJ…
Content-Type: application/json

[{ "id": "prj_…", … }, …]
```

Empty list → `200` + `[]`. No next token when exhausted.

Admin-only small lists may use `page`/`size` offset as exception — document in OpenAPI.

## Idempotency

`@Idempotent` annotation (future):

```
Idempotency-Key: <client-generated-uuid>
```

Server stores key + response for replay window. Required for payment-like or duplicate-sensitive POST creates.

## Rate Limiting (future)

`@RateLimited(permitsPerMinute = …)` → `429 Too Many Requests` ProblemDetail.

## Caching / Optimistic Concurrency (future)

- Stable GET: `ETag` + `If-None-Match` → `304`
- PATCH: `If-Match` with resource version for conflict → `409`

HATEOAS is not a Workbench goal; clients use OpenAPI + known routes.

## Admin API (`/api/admin/*`)

Same payload rules as public API:
- Public `id` strings only — no UUID in JSON
- Ref/Embed asymmetry applies
- Class-level `@Authenticated` + `@TenantScoped`; per-method `@Authorize`

## Session Aggregate Nesting

`SessionResponse` / `LoginResponse` are composition roots:

```json
{
  "user": { "id": "usr_…", "displayName": "…", "primaryEmail": "…" },
  "activeTenant": { "id": "ten_…", "name": "…", "slug": "…" },
  "sessionExpiresAt": "2026-07-02T12:00:00+00:00"
}
```

- `user` embeds `UserSummary` fields (include `id`)
- `activeTenant` is `TenantSummary?`
- `bearerToken` in `LoginResponse` is a nested object with `id`, `token`, `expiresAt` — token is a credential, not an entity ref

## OpenAPI Templates

### Shared examples

Canonical JSON payloads and public ids live in `workbench-web/.../api/OpenApiExamples.kt`. Reference them from `@ExampleObject(value = OpenApiExamples.…)` so controllers stay DRY and match [examples.md](examples.md).

```kotlin
ExampleObject(
  name = "success",
  summary = "Created project",
  value = OpenApiExamples.PROJECT_CREATED,
)
```

ProblemDetail negative examples: `VALIDATION_FAILED`, `PERMISSION_DENIED`, `RESOURCE_NOT_FOUND`, `AUTHENTICATION_FAILED`, `TENANT_NOT_SELECTED`, `INVALID_REQUEST`.

### Standard error responses

`@StandardErrorResponses` (class- or method-level) declares 400/401/403/404/409 with `application/problem+json` schema and named examples. Endpoint-level `@ApiResponse` can add operation-specific negative examples on top.

### Controller tag

```kotlin
@RestController
@RequestMapping("/api/projects")
@Tag(name = "Projects", description = "Tenant-scoped project management")
@SessionSecured
@StandardErrorResponses
class ProjectController(…)
```

### Create endpoint with positive and negative examples

```kotlin
@PostMapping
@Authenticated
@TenantScoped
@Authorize(action = "project.create", resource = "project")
@ResponseStatus(HttpStatus.CREATED)
@Operation(
  summary = "Create a project",
  description = "Creates a project in the active session tenant. Returns 201 with a Location header.",
  responses = [
    ApiResponse(
      responseCode = "201",
      description = "Created",
      content = [Content(
        mediaType = "application/json",
        schema = Schema(implementation = ProjectResponse::class),
        examples = [ExampleObject(name = "created", value = OpenApiExamples.PROJECT_CREATED)],
      )],
    ),
    ApiResponse(
      responseCode = "400",
      description = "Validation failed",
      content = [Content(
        mediaType = "application/problem+json",
        schema = Schema(implementation = ProblemDetail::class),
        examples = [ExampleObject(name = "invalidIdentifier", value = OpenApiExamples.VALIDATION_FAILED)],
      )],
    ),
  ],
)
suspend fun create(
  @io.swagger.v3.oas.annotations.parameters.RequestBody(
    content = [Content(
      mediaType = "application/json",
      schema = Schema(implementation = CreateProjectRequest::class),
      examples = [
        ExampleObject(name = "valid", value = OpenApiExamples.CREATE_PROJECT_REQUEST),
        ExampleObject(name = "invalidIdentifier", value = OpenApiExamples.CREATE_PROJECT_REQUEST_INVALID),
      ],
    )],
  )
  @Valid @RequestBody request: CreateProjectRequest,
  tenantContext: TenantRequestContext,
): ResponseEntity<ProjectResponse>
```

Annotation parameters must be compile-time constants — use single-line `description` strings (no `.trimIndent()`).

### Request field schema

```kotlin
data class CreateProjectRequest(
  @field:NotBlank
  @field:Pattern(regexp = "^[A-Z][A-Z0-9]{1,9}$")
  @field:Schema(example = "CORE", description = "Work item key prefix")
  val identifier: String,
  …
)
```

## Frontend Contract

- Orval generates client from `http://localhost:8080/api/openapi`
- Field renames (`apiId` → `id`) are **breaking** — coordinate with frontend
- Run `pnpm openapi` in `workbench-frontend` after backend OpenAPI changes (backend must be running)

## Migration Guide — `apiId` → `id`

| Current | Target |
|---------|--------|
| `apiId` in response JSON | `id` |
| `tenantApiId` in request | `tenantId` |
| `loginMethodCode` in request | `loginMethodId` |
| `tenantApiId` + `tenantName` + `tenantSlug` flat | `tenant: TenantSummary` |
| `loginMethodCode` + `loginMethodKind` + `loginMethodName` flat | `loginMethod: LoginMethodSummary` |
| `membershipApiId` | `id` on membership resource |
| `id: UUID` in responses | Remove; use public string `id` |
| `userId: UUID` in requests | `userId: String` |
| `isBuiltin` | `builtin` |
| `TenantSummaryResponse` | `TenantSummary` with `id` not `apiId` |
| UUID path params | Public string `{id}` |

### Files aligned (2026-07-03)

All controllers now use public `id`, core `*Summary` embeds, thin-controller delegation to services, and OpenAPI `info.version` `2026-07-03`.
