# Git Commit Examples

Workbench-specific Conventional Commits examples. See [SKILL.md](SKILL.md) for rules and workflow.

## Good

### feat — new capability

```
feat(identity): add invitation repository and V13 migration
```

```
feat(web): split auth controller into session and bearer endpoints

Separate controllers clarify OpenAPI grouping and security filter paths.
```

### fix — bug fix

```
fix(web): return 409 for duplicate tenant slug on create
```

### refactor — no behavior change

```
refactor(data): split ExposedIdentityRepositories by aggregate
```

### build / ci / docs

```
build(deps): upgrade Redisson to 4.6.1 for Spring Boot 4
```

```
ci: upload Kover XML and mutation reports in quality gate
```

```
docs: add Cursor Cloud setup notes in AGENTS.md
```

### test — tests only

```
test(service): cover login discovery for disabled tenant
```

## Bad

| Message | Why |
|---------|-----|
| `Enhance identity management by introducing new repositories` | Not Conventional Commits; reads like a PR title, not a subject line |
| `fix bug` | Too vague — no scope, no actionable subject |
| `feat: stuff` | Meaningless subject |
| `Add admin user management` | Missing type prefix |
| `feat(identity): Add invitation repository.` | Subject must start lowercase; no trailing period |
| `WIP` | Never commit work-in-progress placeholders |
| `fix(web): return 409 for duplicate tenant slug on create because the user tried to create a tenant with a slug that already exists in the database and we need to tell them` | Subject exceeds 72 characters — move detail to body |
