# ArchDox Document Governance

Level: L0
Status: canonical
Last reviewed: 2026-06-10

This file defines how ArchDox Markdown documents are trusted, updated, and used
by humans and AI agents. It does not replace the architecture documents. It
decides which document wins when there are too many documents or when documents
appear to conflict.

## Why This Exists

ArchDox is now large enough that documentation can become a source of design
drift. The goal is not to reduce every document into one file. The goal is to
make the document set controllable:

- read the smallest useful set before coding
- know which document is authoritative
- keep temporary notes from overriding architecture
- update the right document after behavior changes
- make AI-assisted development follow the same hierarchy every time

## Document Levels

| Level | Meaning | Examples |
| --- | --- | --- |
| L0 | Entry maps, governance, current-state snapshots, and non-negotiable agent rules. These decide reading order and conflict handling. | `docs/README.md`, `docs/DOCUMENT_GOVERNANCE.md`, `docs/DOCUMENT_INDEX.md`, `docs/architecture/SYSTEM_MAP.md`, `docs/development/AGENT_RULES.md`, `docs/CURRENT_STATE.md` |
| L1 | Canonical architecture contracts. These define module boundaries, ownership, domain rules, security posture, and platform direction. | domain model, Engine boundary, Legal architecture, Agent architecture, AI provider policy |
| L2 | Detailed implementation contracts, operational procedures, API references, UI rules, testing guides, and module-specific details. | API contract, frontend architecture, local E2E, render runtime policy |
| L3 | Roadmaps, phase notes, experiments, migration notes, older handoff notes, and planning material. These are useful context but do not override L0-L2. | implementation roadmap, exploratory plans, deprecated direction notes |
| Reference | External or source-material reference. These can explain forms or laws, but they do not define ArchDox architecture by themselves. | reference forms, source examples |

## Status Values

Use one of these status values in `docs/DOCUMENT_INDEX.md` and, gradually, in
front matter at the top of individual Markdown files.

| Status | Meaning |
| --- | --- |
| `canonical` | Authoritative for its topic. Must be followed unless code proves it is stale. |
| `active-snapshot` | Current operational snapshot. Update after major work, but do not treat it as full architecture. |
| `guide` | Practical instructions or procedures. Follow for the named workflow. |
| `reference` | Source material or neutral reference. Useful, but not a design contract. |
| `roadmap` | Planned direction. Does not mean implemented. |
| `draft` | Work in progress. Use carefully and verify against canonical docs and code. |
| `superseded` | Kept for history. Do not use for new implementation unless explicitly requested. |

## Precedence Rules

When documents disagree, use this order:

1. Code, database migrations, deployed configuration, and tests are the factual
   implementation record.
2. `docs/DOCUMENT_GOVERNANCE.md` decides document hierarchy.
3. `docs/development/AGENT_RULES.md` decides AI-agent and implementation
   discipline.
4. `docs/architecture/SYSTEM_MAP.md` decides the current module map and
   responsibility split.
5. L1 canonical architecture documents decide their own domain.
6. L2 implementation details apply inside the boundaries set by L0 and L1.
7. L3, roadmap, draft, and conversation history are context only.

If code and canonical docs disagree, do not silently pick one. Treat it as a
documentation drift issue:

1. Identify the conflicting documents.
2. Identify the code or migration that proves current behavior.
3. Update the smallest canonical document needed.
4. Mention the drift in the final change summary.

## Canonical Reading Order

For AI agents and humans entering the project:

1. `docs/README.md`
2. `docs/DOCUMENT_GOVERNANCE.md`
3. `docs/DOCUMENT_INDEX.md`
4. `docs/architecture/SYSTEM_MAP.md`
5. `docs/development/AGENT_RULES.md`
6. `docs/CURRENT_STATE.md`
7. Only the topic-specific documents listed in `docs/README.md` or
   `docs/DOCUMENT_INDEX.md`

Do not read every Markdown file before a normal implementation task. That makes
the model overfit old context and increases the chance of following stale notes.

## Front Matter Standard

New or heavily edited architecture documents should use this header:

```md
# Document Title

Level: L1
Status: canonical
Owner: architecture
Module: cloud-api
Last reviewed: YYYY-MM-DD
Supersedes: none
```

Do not block small edits only because older files do not yet have this header.
The migration is gradual. `docs/DOCUMENT_INDEX.md` is the current source of
truth for levels until every important document has metadata.

## Update Rules

Use these rules when changing docs:

- Update an existing canonical document instead of creating a new one when the
  topic already has a home.
- Create a new document only when the topic has a durable owner and would make
  the existing document too broad.
- Mark planning documents as `roadmap` or `draft`; do not let them read like
  implemented behavior.
- When a new document replaces an older one, mark the older one as
  `superseded` in `docs/DOCUMENT_INDEX.md` before removing or ignoring it.
- Keep `docs/CURRENT_STATE.md` short. It is an operational snapshot, not a
  second architecture book.
- Keep `docs/architecture/SYSTEM_MAP.md` as the system entry map. Add links to
  deeper documents, not full detailed specs.

## AI Agent Rules

Before implementing, an AI agent should answer these questions from the docs:

1. Which module owns the change?
2. Which module must not own it?
3. Which L1 document is canonical for this area?
4. Is there an L2 implementation contract that constrains the code?
5. Is the requested behavior already contradicted by code or tests?

If the answer is unclear, inspect code before adding new architecture. Do not
invent a new boundary when an existing canonical document already defines one.

## Document Health Checks

Run these checks periodically:

- Every Markdown file is listed in `docs/DOCUMENT_INDEX.md`.
- Every L1 canonical document has a clear topic and owner.
- No L3 roadmap is linked as a primary source from `docs/README.md`.
- `docs/CURRENT_STATE.md` date is recent after major deployed changes.
- New implementation work updates the smallest relevant canonical document.

