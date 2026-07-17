# API Design Examples

Annotated examples for Workbench API shape. See [SKILL.md](SKILL.md) for rules.

## 1. CRUD Resource — Create Project

**Endpoint:** `POST /api/projects` → `201 Created`

```http
POST /api/projects HTTP/1.1
Content-Type: application/json
X-Workbench-API-Version: 2026-07-03

{"identifier":"CORE","name":"Core Platform","description":"Platform engineering."}
```

```http
HTTP/1.1 201 Created
Location: /api/projects/prj_01JABCDEFGHJKMNPQRSTVWXYZ0
Content-Type: application/json

{
  "id": "prj_01JABCDEFGHJKMNPQRSTVWXYZ0",
  "identifier": "CORE",
  "name": "Core Platform",
  "description": "Platform engineering."
}
```

**Why correct:**
- Noun URL + POST create → `201` + `Location`
- Direct Resource body (no envelope)
- Own resource fields flat; public `id` not `apiId`

**Controller pattern** (`ProjectController.kt`):

```kotlin
@PostMapping
@Authenticated
@TenantScoped
@Authorize(action = "project.create", resource = "project")
@ResponseStatus(HttpStatus.CREATED)
suspend fun create(
  @Valid @RequestBody request: CreateProjectRequest,
  tenantContext: TenantRequestContext,
): ProjectResponse {
  val record = service.create(CreateProjectCommand(
    tenantId = tenantContext.tenantId,
    identifier = request.identifier,
    name = request.name,
    description = request.description,
  ))
  return ProjectResponse.from(record)
}
```

---

## 2. Collection + Member URL Template

```
GET    /api/projects              → 200 List<ProjectResponse>
POST   /api/projects              → 201 ProjectResponse
GET    /api/projects/{id}         → 200 ProjectResponse
PATCH  /api/projects/{id}         → 200 ProjectResponse
DELETE /api/projects/{id}         → 204 (empty)
```

`{id}` = `prj_01J…` public string. Filtering: `GET /api/projects?identifier=CORE`.

---

## 2a. Verb Actions — Search vs Business Command

**Read action** — complex query, no side effects:

```
POST /api/projects/prj_01J…/work-items/search
{
  "query": {
    "version": 1,
    "resource": "work_item",
    "where": { "field": "statusGroup", "op": "eq", "value": "todo" },
    "sort": [{ "field": "updatedAt", "direction": "desc" }],
    "group": { "field": "statusGroup", "direction": "asc" }
  },
  "scope": {
    "excludeGroupKeys": [{ "field": "statusGroup", "op": "eq", "value": "done" }]
  },
  "limit": 50,
  "cursor": null
}
→ 200 List<WorkItemSearchHitResponse>
   Header: X-Workbench-Next-Cursor (when more results exist)

POST /api/projects/prj_01J…/work-items/search/groups
{
  "query": {
    "version": 1,
    "resource": "work_item",
    "group": { "field": "statusGroup", "direction": "asc" }
  },
  "groupLimit": 20
}
→ 200 { "groups": [...], "groupsPage": { "limit", "truncated", "nextGroupCursor" } }
```

**Business action** — domain command, not a simple PATCH:

```
POST /api/projects/prj_01J…/work-items/wit_01J…/transition
{"status": "IN_PROGRESS"}
→ 200 WorkItemResponse
```

| | Read action | Business action |
|---|-------------|-------------------|
| Examples | `search`, `export`, `preview` | `transition`, `archive`, `bulk-update` |
| Mutates state | No | Yes |
| Prefer instead | GET + query params | PATCH on member when only fields change |

**Avoid:** `POST /api/projects/create` (CRUD tunneling) or `POST /api/archive-project` (top-level RPC).

---

## 3. Assign Role — Request Refs, Response Bare FKs

**Request** (`POST /api/admin/permissions/assignments`):

```json
{
  "userId": "usr_01JABCDEFGHJKMNPQRSTVWXYZ0",
  "roleId": "rol_01JABCDEFGHJKMNPQRSTVWXYZ0",
  "projectId": "prj_01JABCDEFGHJKMNPQRSTVWXYZ0"
}
```

Three different entities → flat `{entity}Id` refs. No nesting in request.

**Response** (`201`):

```json
{
  "id": "ras_01JABCDEFGHJKMNPQRSTVWXYZ0",
  "userId": "usr_01JABCDEFGHJKMNPQRSTVWXYZ0",
  "roleId": "rol_01JABCDEFGHJKMNPQRSTVWXYZ0",
  "projectId": "prj_01JABCDEFGHJKMNPQRSTVWXYZ0",
  "validFrom": "2026-07-02T10:00:00+00:00",
  "validTo": null
}
```

Bare FKs only — no `userName`/`roleName` alongside ids. If display metadata needed later, embed `user: UserSummary` instead of adding `userDisplayName`.

---

## 4. Membership List — Tenant Embed

**Endpoint:** `GET /api/auth/memberships` → `200`

**Target:**

```json
[
  {
    "id": "tmb_01JABCDEFGHJKMNPQRSTVWXYZ0",
    "tenant": {
      "id": "ten_01JABCDEFGHJKMNPQRSTVWXYZ0",
      "name": "Acme Corp",
      "slug": "acme"
    }
  }
]
```

**Before (incorrect — prefix-flatten):**

```json
{
  "tenantApiId": "ten_01J…",
  "tenantName": "Acme Corp",
  "tenantSlug": "acme",
  "membershipApiId": "tmb_01J…"
}
```

**Fix:** Nest `tenant`; rename `membershipApiId` → `id`.

```kotlin
data class MembershipResponse(
  val id: String,
  val tenant: TenantSummary,
)

data class TenantSummary(val id: String, val name: String, val slug: String)
```

---

## 5. Login Options — Tenant + LoginMethod Embeds

**Endpoint:** `GET /api/auth/login-options?identifier=user@example.com` → `200`

**Target:**

```json
[
  {
    "tenant": {
      "id": "ten_01JABCDEFGHJKMNPQRSTVWXYZ0",
      "name": "Acme Corp"
    },
    "loginMethod": {
      "id": "lmg_01JABCDEFGHJKMNPQRSTVWXYZ0",
      "code": "password",
      "kind": "PASSWORD",
      "name": "Password"
    }
  }
]
```

**Before (incorrect):**

```json
{
  "tenantApiId": "ten_01J…",
  "tenantName": "Acme Corp",
  "loginMethodCode": "password",
  "loginMethodKind": "PASSWORD",
  "loginMethodName": "Password"
}
```

Two entities → two embed objects. Inner fields unprefixed (`code`, not `loginMethodCode`).

---

## 6. Login Request — Id Refs Only

**Endpoint:** `POST /api/auth/login`

**Target:**

```json
{
  "method": "PASSWORD",
  "tenantId": "ten_01JABCDEFGHJKMNPQRSTVWXYZ0",
  "loginMethodId": "lmg_01JABCDEFGHJKMNPQRSTVWXYZ0",
  "subject": "user@example.com",
  "password": "secret",
  "issueBearerToken": false
}
```

**Before:** `tenantApiId`, `loginMethodCode` — migrate to `tenantId`, `loginMethodId`.

Credentials (`password`, `token`) stay flat — they are inputs, not entity refs.

---

## 7. Session Aggregate — Protocol Endpoint

**Endpoint:** `GET /api/session` → `200`

Not `GET /api/sessions/{id}` — session is an ephemeral aggregate keyed by auth cookie.

```json
{
  "user": {
    "id": "usr_01JABCDEFGHJKMNPQRSTVWXYZ0",
    "displayName": "Jane Doe",
    "primaryEmail": "jane@example.com"
  },
  "activeTenant": {
    "id": "ten_01JABCDEFGHJKMNPQRSTVWXYZ0",
    "name": "Acme Corp",
    "slug": "acme"
  },
  "sessionExpiresAt": "2026-07-02T12:00:00+00:00"
}
```

**Tenant switch** — `PATCH /api/session`:

```json
{ "tenantId": "ten_01JOTHER…" }
```

Request: single id ref. Response: full `SessionResponse` with embeds.

---

## 8. Protocol vs CRUD — Contrast

| | CRUD resource | Protocol step |
|---|---------------|---------------|
| Example | `POST /api/projects` | `POST /api/auth/login` |
| Creates | Persistent `Project` with `id` | Session state (cookie) |
| URL | Noun collection | Verb under `/api/auth/*` |
| Status | `201` + resource body | `200` + session aggregate |
| Repeatable URL | Yes (`/api/projects/{id}`) | No member URL for session |

Use protocol pattern only for auth flows and session. Domain entities always get resource URLs.

---

## 9. Before → After — RoleResponse Migration

**Before (admin, incorrect):**

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "apiId": "rol_01J…",
  "tenantId": "550e8400-…",
  "scope": "TENANT",
  "code": "admin",
  "name": "Administrator",
  "description": "Full access",
  "isBuiltin": true
}
```

**After (target):**

```json
{
  "id": "rol_01JABCDEFGHJKMNPQRSTVWXYZ0",
  "scope": "TENANT",
  "code": "admin",
  "name": "Administrator",
  "description": "Full access",
  "builtin": true
}
```

Changes: remove UUID `id`/`apiId` duality; single public `id`; `isBuiltin` → `builtin`; embed `tenant` if tenant metadata needed.

---

## 10. Validation Error — ProblemDetail

**Request:** `POST /api/projects` with `identifier: "invalid"`

```http
HTTP/1.1 400 Bad Request
Content-Type: application/problem+json

{
  "type": "https://api.ztd.one/workbench/problems/validation-failed",
  "title": "Validation Failed",
  "status": 400,
  "detail": "identifier: must match \"^[A-Z][A-Z0-9]{1,9}$\""
}
```

Thrown via `@Valid` + `MethodArgumentNotValidException` → `GlobalExceptionHandler`. Never return this shape with `200`.

---

## Quick Reference Card

```
REQUEST:  entity refs → {entity}Id strings, flat, no nesting
RESPONSE: same entity multi-field → {entity}: { id, …unprefixed attrs }
RESPONSE: different entity bare FKs → userId, roleId, projectId
REST:     nouns for CRUD; nested verb actions for complex reads + domain commands
ERRORS:   ProblemDetail, never 200 with error body
IDS:      public id field always "id", never apiId or UUID in JSON
```
