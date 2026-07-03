---
name: git-commit
description: >-
  Workbench Git commit message conventions (Conventional Commits) and safe
  commit workflow. Use when the user asks to commit, write a commit message,
  or review staged changes before committing.
---

# Git Commit

Conventional Commits format and safe agent workflow for this monolith. Pre-PR checklist lives in [workbench-development](../workbench-development/SKILL.md); examples in [examples.md](examples.md).

## When to Activate

- User asks to commit, amend, or write a commit message
- Reviewing staged changes before commit
- Drafting a message from a diff

## When NOT to Commit

- User did not explicitly ask to commit — draft the message or list steps only
- No staged changes and nothing relevant to add
- Diff contains likely secrets (`.env`, `credentials.json`, API keys, tokens)

## Safety Rules

**Never:**

- Run `git config` (any subcommand)
- Run destructive commands (`push --force`, `reset --hard`, etc.) unless the user explicitly requests them
- Skip hooks (`--no-verify`, `--no-gpg-sign`, etc.) unless the user explicitly requests them
- Force-push to `main`/`master` — warn the user if they ask
- Commit files that likely contain secrets

**Amend only when ALL of these are true:**

1. User explicitly requested amend, **or** commit succeeded but a pre-commit hook auto-modified files that must be included
2. HEAD commit was created by you in this conversation
3. Commit has **not** been pushed to remote

If commit **failed** or was **rejected** by a hook, fix the issue and create a **new** commit — do not amend.

**Do not push** unless the user explicitly asks.

## Message Format (Conventional Commits)

```
<type>(<scope>): <subject>

[optional body — 1-2 sentences explaining why, not a file list]
```

| Field | Rule |
|-------|------|
| `type` | Required — see table below |
| `scope` | Optional — module or domain, lowercase kebab-case |
| `subject` | Imperative mood, English, lowercase first letter, no trailing period, ≤72 chars |
| `body` | Optional — motivation and impact; avoid repeating the diff |

### Types

| type | Use for |
|------|---------|
| `feat` | New feature, API, or domain capability |
| `fix` | Bug fix |
| `refactor` | Restructure without behavior change |
| `test` | Tests only |
| `docs` | Documentation, AGENTS.md, Skills, README |
| `build` | Gradle, dependencies, Dockerfile, toolchain |
| `ci` | `.github/workflows`, CI scripts |
| `chore` | Formatting, minor config, no business meaning |

### Scopes

Align with monolith modules and domains:

`core`, `service`, `data`, `web`, `security`, `worker`, `frontend`, `identity`, `permission`, `invitation`, `workitem`, `ci`, `deps`

- Single-module change → module scope (e.g. `web`, `data`)
- Cross-cutting domain change → domain scope (e.g. `identity`) or omit scope
- Repo-wide docs/CI → omit scope or use `ci` / `deps`

New commits use Conventional Commits. Do not rewrite historical messages in bulk.

## Commit Workflow

Copy and track progress:

```
- [ ] User explicitly requested commit
- [ ] git status + git diff + git log (parallel)
- [ ] Message drafted (type, scope, subject, optional body)
- [ ] Staged files reviewed (no secrets, no junk)
- [ ] git commit via HEREDOC
- [ ] git status to verify success
```

### Step 1 — Gather context (parallel)

```bash
git status
git diff          # staged and unstaged
git log --oneline -10
```

Read **all** staged changes. Match recent repo style from `git log`. Summarize the **why**, not a file-by-file inventory.

### Step 2 — Stage relevant files

```bash
git add <paths>
```

Exclude secrets and transient artifacts (e.g. `.kotlin/sessions/*.salive`).

### Step 3 — Commit with HEREDOC

```bash
git commit -m "$(cat <<'EOF'
feat(identity): add invitation and login account repositories

Introduce dedicated stores for invitation flow and login discovery
to simplify identity management and remove obsolete port methods.
EOF
)"
```

### Step 4 — Verify

```bash
git status
```

If a pre-commit hook fails, fix the issue and create a **new** commit (see amend rules above).

## Additional Resources

- [examples.md](examples.md) — good and bad messages for this repo
- [workbench-development](../workbench-development/SKILL.md) — full PR checklist
