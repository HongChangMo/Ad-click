# AGENTS.md

This is the canonical operating guide for all agents working in this repository.
Codex is the primary implementation agent from 2026-06-19 onward. Claude is a
secondary planning/review assistant unless the user explicitly says otherwise.

---

## Session Start

Read these files in order before making changes:

1. `AGENTS.md` - canonical agent rules
2. `harness/session-handoff.md` - current verified state and next action
3. `harness/feature_list.json` - feature status and evidence
4. `harness/claude-progress.md` - historical all-agent progress log
5. The relevant `docs/plans/*` file for the current feature

Before editing code, also check:

```bash
git status --short
```

If the working tree contains unrelated changes, do not revert them. Work around
them or stop and report only when they block the requested task.

---

## Agent Roles

### Codex

Codex owns implementation by default:

- read the handoff and feature documents;
- choose the smallest next task from the documented priority order;
- implement code and tests;
- run focused tests first, then broader verification when feasible;
- update harness documents at the end of the session;
- record only verified facts as evidence.

### Claude

Claude is optional and secondary:

- planning help;
- requirements clarification;
- design review;
- test scenario review;
- blog/documentation drafting.

Claude should not be treated as the source of truth over code, tests, or harness
evidence. Codex must verify Claude-provided claims against the repository before
recording them.

---

## Harness Discipline

Harness files are the project ledger. Keep them stricter than ordinary notes.

- `harness/session-handoff.md` must describe the current state for the next
  session.
- `harness/feature_list.json` must contain only statuses backed by evidence.
- `harness/claude-progress.md` keeps historical records for all agents despite
  the legacy filename.
- `harness/clean-state-checklist.md` is the exit checklist.

Rules:

- Do not mark a feature `done` unless the relevant tests were actually run.
- Do not write "BUILD SUCCESSFUL" unless the command was actually executed in
  the current or explicitly cited prior session.
- If a command was not run, write "not run" and explain why.
- Keep at most one feature `in_progress`.
- Record known gaps in `Still Broken or Unverified`, `Known issues`, or feature
  `notes`.
- When documentation and code disagree, report the disagreement before relying
  on either side.

---

## Build And Test Commands

```bash
./gradlew build
./gradlew test
./gradlew :apps:ad-management:test
./gradlew :apps:ad-click:test
./gradlew :apps:ad-click:test --tests "com.adclick.click.application.ClickFacadeTest"
./gradlew :apps:ad-api:test
./gradlew :apps:ad-api:bootRun
```

`bootRun` requires local DB/Valkey connectivity unless the environment provides
compatible services.

---

## Module Dependency Direction

Keep this one-way dependency direction:

```text
ad-api -> ad-management
ad-api -> ad-click
ad-click -> ad-management
```

`ad-management` must never import or depend on `ad-click`.

---

## Core Invariants

1. `GET /api/v1/ads/next` deducts exactly 10 won per view.
2. `POST /api/v1/ads/{adId}/clicks` deducts exactly 50 won per valid click.
3. Balance must not go below zero.
4. EXHAUSTED ads are excluded from the view queue and return 404 for clicks
   after exhaustion.
5. Re-clicks from the same IP or anonymous ID within 60 seconds must be stored
   as invalid click events and must not deduct balance.
6. Click rate-limit overflow returns HTTP 429.
7. `GET /api/v1/ads/{adId}/clicks/stats` returns valid and invalid click
   counts filtered by optional `from` / `to` ISO date-time query params.
8. When Valkey is unavailable, click requests fail open and ad rotation falls
   back to DB random ACTIVE selection.
9. Reconciliation can invalidate duplicate valid clicks in a failure window and
   refund 50 won per duplicate click with a `REFUND` balance transaction.

Current known scope boundaries:

- MVP 1 feature list priorities 1-10 are implemented.
- No priority 11+ feature is defined in `harness/feature_list.json` yet.

Do not silently implement later-priority behavior while working on an earlier
feature unless the user explicitly expands the scope.

---

## Architecture Rules

Domain modules follow this package structure:

```text
interfaces/
  api/
    dto/
application/
  info/
domain/
infrastructure/
```

Dependency direction inside a module:

```text
interfaces -> application -> domain <- infrastructure
```

- Controllers call application facades.
- Facades coordinate use cases and transaction boundaries.
- Domain repository interfaces live in `domain`.
- JPA repositories and adapters live in `infrastructure`.
- Facades should depend on domain repository interfaces, not JPA repositories.

---

## Current Priority

As of the latest handoff, the highest-priority unfinished feature is:

```text
none in harness/feature_list.json
```

Ask the user before starting a new feature beyond the current MVP 1 ledger.
