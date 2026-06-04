# ArchDox Worker Action Backlog

This document preserves future worker action ideas that are not part of the
current runtime registry.

The runtime registry must contain only actions with a real executor, policy
gate path, and tests. When one of these items becomes implementation work, add
the enum value, action definition, executor, policy rules, and tests together.

| Future action idea | Intent |
| --- | --- |
| `RUN_DOCUMENT_QA` | Review a generated or pre-generation document for completeness, wording, and consistency. |
| `RUN_LEGAL_REVIEW` | Run a legal or compliance review backed by the legal corpus and domain bindings. |
| `RERUN_DOCUMENT_QA_WITH_STRONGER_MODEL` | Re-run document QA with a higher-cost model when the first review is uncertain. |
| `DRAFT_INSPECTION_LOG` | Draft structured inspection log entries from conversation, photos, and site context. |
| `FIND_EVIDENCE_GAPS` | Find missing photo, checklist, or target evidence for a report or supervision ledger. |
| `PREPARE_SUBMISSION_PACKAGE` | Assemble a submission package after report, review, signature, and document artifacts are ready. |
| `REQUEST_HUMAN_REVIEW` | Create a human review task when policy or AI confidence requires manual review. |
| `APPROVE_DOCUMENT` | Record a controlled document approval decision. |
| `FAIL_REVIEW` | Record an explicit review failure and route it back to correction. |
