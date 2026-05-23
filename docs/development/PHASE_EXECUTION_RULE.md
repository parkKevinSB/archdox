# Phase Execution Rule

This document controls how AI agents should implement ArchDox phases. The goal
is to prevent uncontrolled drift while still allowing steady development.

## Phase Workflow

For any phase-sized change, cross-module change, DB migration, public API change,
security change, or document generation pipeline change, follow this order:

1. Plan
2. Approval
3. Implementation
4. Test
5. Report

Small local fixes inside an already approved scope may proceed with a brief
implementation note, but the final report still needs tests and limitations.

## 1. Plan

Before implementation, provide:

- Target phase or task name
- Files/modules expected to change
- Data ownership decision, especially `office_id`
- API contract changes, if any
- DB migration plan, if any
- Tests to add or run
- Known risks

Do not start broad implementation until the user approves the plan.

## 2. Approval

Approval can be explicit Korean or English confirmation, such as:

- "네 진행하세요"
- "시작하세요"
- "좋습니다"
- "Approved"

If the user gives a concrete ordered task list and says to start, that counts as
approval for that scope only.

## 3. Implementation

During implementation:

1. Keep changes inside the approved scope.
2. Preserve module boundaries.
3. Add Flyway migrations before depending on new tables.
4. Add/update DTOs and update `API_CONTRACT.md` when public APIs change.
5. Add/update `DOMAIN_MODEL.md` when a domain concept is added or renamed.
6. Add tests near the changed behavior.
7. Do not silently replace existing design decisions.

## 4. Test

Run the smallest meaningful test set first, then broader tests when the blast
radius is larger.

Recommended levels:

- Pure domain/helper change: module unit tests.
- API/service/persistence change: `:cloud-api:test`.
- Shared module change: affected module tests plus consumers compile.
- Migration-heavy change: integration test with Testcontainers where possible.
- Cross-module document flow: `:cloud-api:test`, `:document-engine:test`,
  `:archdox-agent:classes`, and any flow tests added.

If Docker/Testcontainers is unavailable, tests may be skipped only when the test
is annotated to skip safely. Report this explicitly.

## 5. Report

Final report must include:

- What changed
- Files or modules changed
- Tests run
- Tests skipped or blocked
- Known limitations
- Recommended next step

## Phase Status Terms

Use these status terms consistently:

- `PLANNED`: documented but not started
- `IN_PROGRESS`: currently being implemented
- `IMPLEMENTED`: code exists and compiles
- `TESTED`: relevant tests passed
- `BLOCKED`: waiting on decision, dependency, credential, Docker, or external
  system

## Definition Of Done

A phase task is done only when:

1. Code compiles.
2. Relevant tests pass or a blocker is clearly documented.
3. API/domain/migration docs are updated if behavior changed.
4. No known tenant isolation issue remains.
5. The user receives a concise completion report.
