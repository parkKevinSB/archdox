# API Contract

This document records the current REST contract that client, admin, cloud, and
archdox-agent work must respect. Update this file whenever endpoint behavior or
DTO shape changes.

Current report creation APIs should use canonical document type codes. In
particular, new construction daily supervision reports use
`CONSTRUCTION_DAILY_SUPERVISION_LOG`. The active MVP scope is construction
supervision only: `CONSTRUCTION_DAILY_SUPERVISION_LOG` and
`CONSTRUCTION_SUPERVISION_REPORT`. Earlier exploratory examples using
`DAILY_SUPERVISION`, `PERIODIC_SAFETY`, `FACILITY_CHECK`, or demolition
supervision codes are legacy/deferred notes and must not be used as new
implementation guidance. Fresh database seeds create only the two current
construction-supervision document types. Migration
`V40__remove_deferred_document_types.sql` is retained only to clean
dev/pre-production databases that already consumed earlier broad exploratory
seeds.

## Common Rules

- Public API base path: `/api/v1`
- Authentication: `Authorization: Bearer <accessToken>`
- Active office header: `X-Office-Id: <officeId>`
- Content type: `application/json`
- Time values: ISO-8601 strings
- Error body:

```json
{
  "status": 400,
  "code": "ERROR_CODE",
  "messageKey": "errors.domain.reason",
  "message": "Human readable developer-facing message",
  "params": {},
  "fieldErrors": [],
  "requestId": "uuid",
  "timestamp": "2026-05-23T10:00:00+09:00"
}
```

Server error `message` is not the primary user-facing copy. Cloud API must
return stable `code` and `messageKey`; clients translate those codes into the
current UI language. Keep `params` small and safe for display/logging. Use
`fieldErrors` for form validation so clients can show inline field messages.

Report-writing errors must use stable codes such as:

- `REPORT_WRITE_FORBIDDEN`
- `REPORT_ASSIGNMENT_REQUIRED`
- `PROJECT_ASSIGNMENT_REQUIRED`
- `REPORT_STEP_SAVE_NOT_ALLOWED`
- `REPORT_SUBMIT_NOT_ALLOWED`
- `REPORT_REOPEN_NOT_ALLOWED`

Office-owned APIs must require both a valid access token and an active office
header unless the endpoint explicitly manages offices.

Cloud API instances are stateless for user-facing REST requests. Any instance
behind the load balancer may handle a request. Durable state must live in DB or
queue-backed infrastructure, not in one API instance's memory.

## Auth

### POST `/api/v1/auth/signup`

Request:

```json
{
  "email": "user@example.com",
  "password": "password123",
  "name": "User Name",
  "accountType": "PERSONAL"
}
```

`accountType` must be either `PERSONAL` or `OFFICE`.

Personal signup creates one `PERSONAL` workspace and gives the new user
`OWNER` membership in that personal workspace.

Office/company signup does not create a personal workspace. It requires an
existing office code and invitation token:

```json
{
  "email": "staff@example.com",
  "password": "password123",
  "name": "Staff User",
  "accountType": "OFFICE",
  "officeCode": "office-a1b2c3d4",
  "invitationToken": "one-time-invitation-token"
}
```

For `OFFICE` signup, Cloud must verify:

- `officeCode` exists and belongs to an `OFFICE` workspace
- `invitationToken` resolves to a pending invitation
- the invitation belongs to the same office as `officeCode`
- the signup email matches the invitation email
- the invitation has not expired

Response `201`:

```json
{
  "accessToken": "jwt",
  "refreshToken": "opaque-refresh-token",
  "tokenType": "Bearer",
  "expiresInSeconds": 3600
}
```

Invitation links must start a dedicated signup flow. The client must not reuse a
currently logged-in browser session to accept the invitation automatically.
Instead it should load the invitation preview, prefill the office and email
fields as read-only values, and then create the office account through this
signup endpoint.

Email verification is a required hardening phase before production office
onboarding. Until it is implemented, invitation tokens must be treated as
one-time secrets.

### POST `/api/v1/auth/login`

Request:

```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

Response `200`: `AuthTokenResponse`

### POST `/api/v1/auth/refresh`

Request:

```json
{
  "refreshToken": "opaque-refresh-token"
}
```

Response `200`: `AuthTokenResponse`

### POST `/api/v1/auth/logout`

Request:

```json
{
  "refreshToken": "opaque-refresh-token"
}
```

Response `204`

### GET `/api/v1/me`

Response `200`:

```json
{
  "id": 1,
  "email": "user@example.com",
  "name": "User Name",
  "offices": [
    {
      "id": 10,
      "officeCode": "P-ABC123",
      "displayName": "User Name",
      "type": "PERSONAL",
      "planCode": "PERSONAL_FREE",
      "role": "OWNER",
      "permissions": {
        "manageOfficeMembers": false,
        "manageProjects": true,
        "manageProjectAssignments": false,
        "manageSites": true,
        "createReports": true,
        "writeReports": true,
        "deleteReports": true,
        "generateDocuments": true,
        "uploadPhotos": true,
        "accessOfficeAdmin": false
      }
    }
  ]
}
```

The client must use `permissions` for coarse office-scoped button visibility.
Project-specific and report-specific actions must prefer `ProjectResponse`
permission fields and `InspectionReportResponse.writeAllowed`.

## Offices

### GET `/api/v1/offices`

Returns offices where the authenticated user is a member.

Response `200`:

```json
[
  {
    "id": 10,
    "officeCode": "P-ABC123",
    "displayName": "My Office",
    "type": "PERSONAL",
    "planCode": "PERSONAL_FREE",
    "role": "OWNER"
  }
]
```

### POST `/api/v1/offices`

Request:

```json
{
  "displayName": "Office Name"
}
```

Response `201`: `OfficeResponse`

### GET `/api/v1/offices/{officeId}`

Response `200`: `OfficeResponse`

### GET `/api/v1/offices/{officeId}/members`

Requires the authenticated user to be an active `OWNER` or `ADMIN` member of
the office.

Response `200`:

```json
[
  {
    "membershipId": 100,
    "userId": 1,
    "officeId": 10,
    "email": "user@example.com",
    "name": "User Name",
    "role": "OWNER",
    "status": "ACTIVE",
    "joinedAt": "2026-05-22T00:00:00+09:00"
  }
]
```

### POST `/api/v1/offices/{officeId}/members`

MVP rule: the invited user must already exist.
Requires `OWNER` or `ADMIN`. Only `OWNER` can assign `OWNER` role.
If a suspended/left membership already exists, this endpoint reactivates it.

Request:

```json
{
  "email": "member@example.com",
  "role": "MEMBER"
}
```

Response `201`: `OfficeMemberResponse`

### PATCH `/api/v1/offices/{officeId}/members/{memberUserId}/role`

Requires `OWNER` or `ADMIN`. Only `OWNER` can assign or modify an `OWNER`.
The actor cannot change their own role. The office must keep at least one
active `OWNER`.

Request:

```json
{
  "role": "ADMIN"
}
```

Response `200`: `OfficeMemberResponse`

### DELETE `/api/v1/offices/{officeId}/members/{memberUserId}`

Deactivates the membership by setting status to `SUSPENDED`; it does not hard
delete the row.

Requires `OWNER` or `ADMIN`. Only `OWNER` can deactivate another `OWNER`.
The actor cannot deactivate their own membership. The office must keep at least
one active `OWNER`.

Response `200`: `OfficeMemberResponse`

Operation events:

- `OFFICE_MEMBER_ADDED`
- `OFFICE_MEMBER_REACTIVATED`
- `OFFICE_MEMBER_ROLE_CHANGED`
- `OFFICE_MEMBER_DEACTIVATED`

### GET `/api/v1/offices/{officeId}/invitations`

Requires the authenticated user to be an active `OWNER` or `ADMIN` member of
the office.

Response `200`:

```json
[
  {
    "id": 200,
    "officeId": 10,
    "email": "new-user@example.com",
    "role": "MEMBER",
    "status": "PENDING",
    "invitedByUserId": 1,
    "acceptedByUserId": null,
    "tokenPreview": "abc12345",
    "acceptToken": null,
    "acceptPath": null,
    "createdAt": "2026-05-22T00:00:00+09:00",
    "expiresAt": "2026-06-05T00:00:00+09:00",
    "acceptedAt": null,
    "cancelledAt": null,
    "updatedAt": "2026-05-22T00:00:00+09:00"
  }
]
```

The raw invite token is intentionally not returned from list APIs.

### POST `/api/v1/offices/{officeId}/invitations`

Creates an office invitation token. This does not send email yet; the admin UI
can copy the generated accept URL.

Requires `OWNER` or `ADMIN`. Only `OWNER` can invite another `OWNER`.

Request:

```json
{
  "email": "new-user@example.com",
  "role": "MEMBER",
  "expiresInDays": 14
}
```

Response `201`: `OfficeInvitationResponse` with one-time-only `acceptToken`
and `acceptPath` populated:

```json
{
  "id": 200,
  "officeId": 10,
  "email": "new-user@example.com",
  "role": "MEMBER",
  "status": "PENDING",
  "tokenPreview": "abc12345",
  "acceptToken": "raw-one-time-visible-token",
  "acceptPath": "/api/v1/office-invitations/raw-one-time-visible-token/accept",
  "expiresAt": "2026-06-05T00:00:00+09:00"
}
```

Cloud stores only the invite token hash. If the response is lost, create a new
invitation after cancelling or letting the previous one expire.

### GET `/api/v1/office-invitations/{token}`

Public preview endpoint for an invitation link. This endpoint is used before
signup so the UI can show the target office and invited email without requiring
an existing user session.

Response `200`:

```json
{
  "email": "staff@example.com",
  "officeId": 10,
  "officeCode": "office-a1b2c3d4",
  "officeDisplayName": "ABC 건축사사무소",
  "role": "MEMBER",
  "status": "PENDING",
  "expiresAt": "2026-06-01T00:00:00Z"
}
```

### DELETE `/api/v1/offices/{officeId}/invitations/{invitationId}`

Cancels a pending invitation.

Response `200`: `OfficeInvitationResponse` with `status = CANCELLED`.

### POST `/api/v1/office-invitations/{token}/accept`

Accepts a pending invitation. The caller must be authenticated, and the caller's
email must match the invitation email. If the user does not have an account yet,
they should use the dedicated invitation signup flow instead of this endpoint.
This endpoint remains as a REST fallback for already-created office accounts.

Response `200`: `OfficeMemberResponse`

Operation events:

- `OFFICE_INVITATION_CREATED`
- `OFFICE_INVITATION_CANCELLED`
- `OFFICE_INVITATION_ACCEPTED`
- `OFFICE_INVITATION_EXPIRED`

## Projects

All project endpoints require `Authorization` and `X-Office-Id`.
Create/archive additionally require project-manager permission: personal
`OWNER`, or office `OWNER`/`ADMIN`.
Project assignment management requires office `OWNER`/`ADMIN`; personal
workspaces do not expose assignment management UI.

`project` is the largest business container. It may contain one or many
`site` rows. User-facing Korean copy may call a `site` "현장"; do not collapse
the two concepts in new APIs.

### GET `/api/v1/projects`

Response `200`:

```json
[
  {
    "id": 100,
    "officeId": 10,
    "name": "Site A",
    "address": "Seoul",
    "buildingType": "CONSTRUCTION_SUPERVISION",
    "startDate": "2026-05-01",
    "endDate": null,
    "status": "ACTIVE",
    "manageAllowed": true,
    "structureManageAllowed": true,
    "reportCreateAllowed": true
  }
]
```

Permission fields are calculated for the authenticated user:

- `manageAllowed`: can edit/archive/delete the project.
- `structureManageAllowed`: can create/archive sites and inspection targets
  under the project. Office `MEMBER` requires project `MANAGER`.
- `reportCreateAllowed`: can create reports under the project. Office `MEMBER`
  requires project `MANAGER` or `REPORT_WRITER`.

### POST `/api/v1/projects`

Request:

```json
{
  "name": "Site A",
  "address": "Seoul",
  "buildingType": "CONSTRUCTION_SUPERVISION",
  "startDate": "2026-05-01",
  "endDate": null
}
```

`buildingType` is the current API field name, but it represents the project
business type in the product UI. The active MVP business scope is construction
supervision only, so the supported active code is:

- `CONSTRUCTION_SUPERVISION`

Other business lines such as building safety inspection, facility inspection,
asbestos supervision, maintenance inspection, and demolition supervision are
deferred domain scopes. They must not be exposed in current report creation or
project creation flows until their own domain catalog and document set are
implemented.

Response `201`: `ProjectResponse`

### GET `/api/v1/projects/{projectId}`

Response `200`: `ProjectResponse`

### POST `/api/v1/projects/{projectId}/archive`

Response `200`: `ProjectResponse` with `status = ARCHIVED`

### GET `/api/v1/projects/{projectId}/assignments`

Lists active project assignments. Any active office member can read the list.

Response `200`:

```json
[
  {
    "id": 1,
    "officeId": 10,
    "projectId": 100,
    "userId": 20,
    "email": "member@example.com",
    "name": "Member",
    "role": "REPORT_WRITER",
    "status": "ACTIVE",
    "assignedBy": 1,
    "assignedAt": "2026-05-22T17:00:00+09:00",
    "updatedAt": "2026-05-22T17:00:00+09:00"
  }
]
```

### PUT `/api/v1/projects/{projectId}/assignments`

Creates, updates, or reactivates a project assignment.

Request:

```json
{
  "userId": 20,
  "role": "REPORT_WRITER"
}
```

Supported `role` values:

- `MANAGER`: can manage sites/targets and write reports under the project
- `REPORT_WRITER`: can write reports under the project
- `VIEWER`: explicit read-only project membership

Response `200`: `ProjectAssignmentResponse`

### DELETE `/api/v1/projects/{projectId}/assignments/{userId}`

Soft-removes the project assignment by setting `status = REMOVED`.

Response `200`: `ProjectAssignmentResponse`

## Sites

All site endpoints require `Authorization` and `X-Office-Id`.
Create/archive additionally require project-structure-manager permission:
personal `OWNER`, office `OWNER`/`ADMIN`, or project assignment role `MANAGER`.

`site` is `현장`: the physical or operational place where inspection,
supervision, photo capture, and report work happen. Sites belong to projects.

### GET `/api/v1/projects/{projectId}/sites`

Response `200`:

```json
[
  {
    "id": 200,
    "officeId": 10,
    "projectId": 100,
    "siteCode": "SITE-A",
    "name": "Main Building Site",
    "address": "Seoul",
    "siteType": "BUILDING",
    "startDate": "2026-05-01",
    "endDate": null,
    "status": "ACTIVE"
  }
]
```

### POST `/api/v1/projects/{projectId}/sites`

Request:

```json
{
  "siteCode": "SITE-A",
  "name": "Main Building Site",
  "address": "Seoul",
  "siteType": "BUILDING",
  "startDate": "2026-05-01",
  "endDate": null
}
```

Supported MVP `siteType` codes:

- `CONSTRUCTION_SITE`
- `BUILDING`
- `FACILITY`
- `PLANT`
- `CAMPUS`
- `WORK_AREA`
- `OTHER`

Response `201`: `SiteResponse`

### GET `/api/v1/projects/{projectId}/sites/{siteId}`

Response `200`: `SiteResponse`

### POST `/api/v1/projects/{projectId}/sites/{siteId}/archive`

Response `200`: `SiteResponse` with `status = ARCHIVED`

## Inspection Targets

All inspection target endpoints require `Authorization` and `X-Office-Id`.
Create/archive additionally require project-structure-manager permission:
personal `OWNER`, office `OWNER`/`ADMIN`, or project assignment role `MANAGER`.

`inspection_target` is a tree below a site. It represents the physical or
logical object being inspected, photographed, checked, or written into a
document.

### GET `/api/v1/projects/{projectId}/sites/{siteId}/targets`

Response `200`:

```json
[
  {
    "id": 300,
    "officeId": 10,
    "projectId": 100,
    "siteId": 200,
    "parentTargetId": null,
    "targetType": "BUILDING",
    "code": "B-01",
    "name": "Main Building",
    "address": "North block",
    "metadata": {},
    "status": "ACTIVE"
  }
]
```

### POST `/api/v1/projects/{projectId}/sites/{siteId}/targets`

Request:

```json
{
  "parentTargetId": null,
  "targetType": "BUILDING",
  "code": "B-01",
  "name": "Main Building",
  "address": "North block",
  "metadata": {}
}
```

Supported MVP `targetType` codes:

- `BUILDING`
- `FACILITY`
- `FLOOR`
- `ROOM`
- `ZONE`
- `STRUCTURAL_ELEMENT`
- `EQUIPMENT`
- `COMPONENT`
- `MATERIAL`
- `WORK_AREA`
- `OTHER`

Response `201`: `InspectionTargetResponse`

### POST `/api/v1/projects/{projectId}/sites/{siteId}/targets/{targetId}/archive`

Response `200`: `InspectionTargetResponse` with `status = ARCHIVED`

## Document Types

All document type endpoints require `Authorization` and `X-Office-Id`.
Document types are the product-level defaults used when a report is created:
they resolve the normalized `reportType`, default checklist pack, report wizard
steps, default template, and neutral output layout.

Default document type packs are not only labels. They are the first practical
configuration layer for ordinary field workflows:

- `workflow_json` describes the report-writing steps that the normal user UI
  should render, such as basic information, checklist entry, photo evidence,
  and issue/action follow-up.
- `output_layout_json` describes neutral generated-document sections such as
  `CHECKLIST_TABLE`, `CHECKLIST_PHOTO_TABLE`, and `PHOTO_TABLE`.
- Office-specific configuration revisions may override these defaults later,
  but new built-in document types should still provide a useful system default
  pack.

### GET `/api/v1/document-types`

Response `200`:

```json
[
  {
    "id": 1,
    "officeId": null,
    "code": "CONSTRUCTION_DAILY_SUPERVISION_LOG",
    "reportType": "CONSTRUCTION_DAILY_SUPERVISION_LOG",
    "name": "Construction daily supervision log",
    "description": "Default construction supervision daily log.",
    "category": "CONSTRUCTION_SUPERVISION",
    "defaultTemplateCode": "KOREAN_CONSTRUCTION_DAILY_SUPERVISION_LOG_APPENDIX_2",
    "defaultTemplateStorageRef": "templates/korean/korean-construction-daily-supervision-log-appendix-2.docx",
    "checklistSchemaCode": "CONSTRUCTION_DAILY_SUPERVISION_LOG_DEFAULT",
    "defaultOutputFormat": "DOCX",
    "displayOrder": 40,
    "steps": [
      {
        "code": "BASIC_INFO",
        "title": "Basic information",
        "description": "Work date, weather, chief supervisor, and architect assistant.",
        "stepType": "FORM",
        "savePolicy": "ON_NAVIGATE",
        "fields": []
      }
    ]
  }
]
```

### GET `/api/v1/document-types/{code}`

Response `200`: one `DocumentTypeResponse`.

Report creation should send one of these `code`/`reportType` values. The Cloud
API normalizes it and stores the canonical `reportType` on the report.

## Inspection Reports

All inspection report endpoints require `Authorization` and `X-Office-Id`.
Create, step save, submit, cancel, checklist save, and report-target attach
require report-writer permission: personal `OWNER`, office `OWNER`/`ADMIN`, or
an office `MEMBER` with an active project/report assignment. A plain office
`MEMBER` without assignment is not a writer. `VIEWER` is read-only.

### GET `/api/v1/inspection-reports`

Query parameters:

- `projectId`: optional
- `status`: optional

Response `200`:

```json
[
  {
    "id": 1000,
    "officeId": 10,
    "projectId": 100,
    "siteId": 200,
    "reportNo": "IR-20260521-0001",
    "reportType": "CONSTRUCTION_DAILY_SUPERVISION_LOG",
    "title": "Construction Daily Supervision Log",
    "status": "DRAFT",
    "currentStep": null,
    "templateId": null,
    "contentRevision": 1,
    "submittedRevision": null,
    "generatedRevision": null,
    "lastDocumentJobId": null,
    "writeAllowed": true,
    "reopenAllowed": false
  }
]
```

`writeAllowed` and `reopenAllowed` are calculated for the authenticated user
and active office. The client must use these fields for edit/reopen controls
because project/report assignments may narrow a general office `MEMBER` role.

### POST `/api/v1/inspection-reports`

Request:

```json
{
  "projectId": 100,
  "siteId": 200,
  "reportType": "CONSTRUCTION_DAILY_SUPERVISION_LOG",
  "title": "Construction Daily Supervision Log",
  "templateId": null
}
```

Response `201`: `InspectionReportResponse`

### GET `/api/v1/inspection-reports/{reportId}`

Response `200`: `InspectionReportResponse`

### GET `/api/v1/inspection-reports/{reportId}/targets`

Response `200`:

```json
[
  {
    "id": 400,
    "officeId": 10,
    "reportId": 1000,
    "targetId": 300,
    "role": "PRIMARY",
    "snapshot": {
      "name": "Main Building",
      "targetType": "BUILDING"
    },
    "createdAt": "2026-05-22T14:30:00+09:00"
  }
]
```

### POST `/api/v1/inspection-reports/{reportId}/targets`

Request:

```json
{
  "targetId": 300,
  "role": "PRIMARY"
}
```

Supported `role` values: `PRIMARY`, `SECONDARY`, `REFERENCE`.

Response `201`: `InspectionReportTargetResponse`

### GET `/api/v1/inspection-reports/{reportId}/assignments`

Lists active report assignments. Any active office member can read the list.

Response `200`:

```json
[
  {
    "id": 1,
    "officeId": 10,
    "reportId": 1000,
    "userId": 20,
    "email": "member@example.com",
    "name": "Member",
    "role": "WRITER",
    "status": "ACTIVE",
    "assignedBy": 1,
    "assignedAt": "2026-05-22T17:00:00+09:00",
    "updatedAt": "2026-05-22T17:00:00+09:00"
  }
]
```

### PUT `/api/v1/inspection-reports/{reportId}/assignments`

Creates, updates, or reactivates a report assignment.

Request:

```json
{
  "userId": 20,
  "role": "WRITER"
}
```

Supported `role` values:

- `WRITER`: can edit/save/submit while report lifecycle allows it
- `REVIEWER`: reserved for approval/review workflow, read-only for now
- `VIEWER`: explicit read-only report membership

Response `200`: `ReportAssignmentResponse`

### DELETE `/api/v1/inspection-reports/{reportId}/assignments/{userId}`

Soft-removes the report assignment by setting `status = REMOVED`.

Response `200`: `ReportAssignmentResponse`

### GET `/api/v1/inspection-reports/{reportId}/steps`

Used by the client report wizard to resume a draft report.

Response `200`:

```json
[
  {
    "stepCode": "BASIC_INFO",
    "payloadStorageMode": "CLOUD_ENCRYPTED",
    "payload": {
      "inspectionDate": "2026-05-21",
      "weather": "SUNNY",
      "inspectorName": "Kim"
    },
    "clientRevision": 1,
    "savedAt": "2026-05-21T14:30:00+09:00"
  }
]
```

### GET `/api/v1/inspection-reports/{reportId}/workflow-definition`

Resolves the report-writing flow that the normal client UI should render.

Resolution order:

1. active office override workflow revision for the report type
2. published system workflow revision for the report type
3. built-in report-writing fallback

The endpoint also returns the active checklist schema selected for the report
context when one exists. `siteType` comes from the report site. `targetType`
comes from the primary attached inspection target snapshot when available.

Response `200`:

```json
{
  "reportId": 10,
  "officeId": 1,
  "reportType": "CONSTRUCTION_DAILY_SUPERVISION_LOG",
  "siteType": "BUILDING",
  "targetType": "BUILDING",
  "flowId": "construction-daily-supervision-log-writing",
  "title": "공사감리일지 작성",
  "source": "BUILT_IN_DEFAULT",
  "definitionId": null,
  "revisionId": null,
  "version": null,
  "checklistSchemaId": 500,
  "checklistSchemaCode": "CONSTRUCTION_DAILY_SUPERVISION_LOG_DEFAULT",
  "checklistSchemaVersion": 1,
  "steps": [
    {
      "code": "BASIC_INFO",
      "title": "기본 정보",
      "description": "일자, 날씨, 담당자처럼 보고서가 공유하는 머리말 정보를 정리합니다.",
      "stepType": "FORM",
      "savePolicy": "ON_NAVIGATE",
      "fields": [
        {
          "key": "inspectionDate",
          "label": "점검일",
          "type": "date",
          "placeholder": null,
          "required": true
        }
      ]
    }
  ]
}
```

Supported `stepType` values for the client are currently:

- `FORM`
- `CHECKLIST`
- `PHOTO`

Unsupported or missing config falls back to the built-in flow. Configuration
must choose from supported step components; it must not attempt to render
arbitrary React UI.

### PUT `/api/v1/inspection-reports/{reportId}/steps/{stepCode}`

Request:

```json
{
  "payload": {
    "weather": "SUNNY",
    "temperature": 23,
    "workItems": []
  }
}
```

Response `200`:

```json
{
  "stepCode": "BASIC_INFO",
  "payloadStorageMode": "CLOUD_ENCRYPTED",
  "payload": {
    "weather": "SUNNY",
    "temperature": 23,
    "workItems": []
  },
  "clientRevision": 1,
  "savedAt": "2026-05-21T14:30:00+09:00"
}
```

Current MVP stores JSON payload directly while the encryption adapter is not yet
implemented. The contract name remains `CLOUD_ENCRYPTED` to preserve the target
design.

### GET `/api/v1/inspection-reports/{reportId}/checklist`

Resolves the active checklist schema for the report and returns saved answers.

Response `200`:

```json
{
  "schema": {
    "id": 500,
    "officeId": null,
    "reportType": "CONSTRUCTION_SUPERVISION_REPORT",
    "siteType": null,
    "targetType": null,
    "code": "CONSTRUCTION_SUPERVISION_REPORT_DEFAULT",
    "name": "공사감리보고서 기본 체크리스트",
    "version": 1,
    "schema": {},
    "items": [
      {
        "id": 501,
        "itemCode": "CRACK_CHECK",
        "label": "균열 여부",
        "description": "주요 구조부 균열 또는 의심 흔적을 확인합니다.",
        "answerType": "SELECT",
        "required": true,
        "displayOrder": 10,
        "options": ["이상없음", "관찰", "조치필요"]
      }
    ]
  },
  "answers": []
}
```

### PUT `/api/v1/inspection-reports/{reportId}/checklist/answers/{itemCode}`

Request:

```json
{
  "targetId": 300,
  "answer": {
    "value": "관찰"
  },
  "note": "2층 복도 미세 균열 관찰"
}
```

Response `200`: `ChecklistAnswerResponse`

### GET `/api/v1/inspection-reports/{reportId}/submit-validation`

Checks whether a report is ready to move from draft writing into document
generation readiness. This endpoint is safe for the UI to call before showing a
final submit action.

Current MVP blocking rules are resolved from workflow definition and rule set
configuration:

- `FORM` steps with required fields must be saved and contain non-empty values
  for those fields.
- `CHECKLIST` steps must be saved, or checklist answers must exist for the
  report.
- `PHOTO` steps require uploaded `WORKING` photo assets. The default minimum is
  1 when the workflow contains a `PHOTO` step.
- Rule set payload may override photo count with `minWorkingPhotos` or
  `minPhotos`.
- Rule set payload may force extra saved steps with `requiredSteps`.

Built-in fallback still behaves like the old MVP contract:

- required basic information step
- checklist step or checklist answers
- at least one working photo

Response `200`:

```json
{
  "valid": false,
  "message": "Report is not ready to submit",
  "blockingIssues": [
    {
      "code": "MISSING_STEP_BASIC_INFO",
      "message": "Basic information step must be saved before submit.",
      "resourceType": "INSPECTION_REPORT_STEP",
      "resourceKey": "BASIC_INFO"
    }
  ],
  "warnings": []
}
```

### POST `/api/v1/inspection-reports/{reportId}/submit`

Allowed only while the report status is `DRAFT` or `STEP_SAVED`.

Response `200`: `InspectionReportResponse` with `status = READY_TO_GENERATE`.
The response has `submittedRevision = contentRevision`.

If the report is not ready to submit, Cloud returns `409` with the same
`ReportSubmitValidationResponse` shape as the validation endpoint. The frontend
must display the blocking issue messages instead of treating this as a generic
failure.

### POST `/api/v1/inspection-reports/{reportId}/reopen`

Creates the next editable report revision after a report has already been
submitted or generated.

Allowed statuses:

- `READY_TO_GENERATE`
- `GENERATED`
- `DELIVERED`
- `FAILED`

Blocked statuses:

- `GENERATION_REQUESTED`
- `GENERATING`
- `CANCELLED`

Response `200`: `InspectionReportResponse` with `status = STEP_SAVED` and
`contentRevision` incremented by 1. Existing document jobs/artifacts are not
deleted. They remain immutable generated history for their `reportRevision`.

### POST `/api/v1/inspection-reports/{reportId}/cancel`

Allowed only before generation/output completion.

Response `200`: `InspectionReportResponse` with `status = CANCELLED`

After a report reaches `READY_TO_GENERATE`, `GENERATED`, `DELIVERED`, or
`FAILED`, step-save requests return `400` until the report is reopened for edit.
`GENERATION_REQUESTED` and `GENERATING` are locked because a document snapshot
is already being processed.

## Photos

All photo endpoints require `Authorization` and `X-Office-Id`.

Development default is `API_LOCAL`: the API returns authenticated upload URLs
under `/api/v1/photos/{photoId}/content/{kind}`.

Configured production targets may return S3-compatible presigned `PUT` URLs:

- `S3`: personal/cloud direct upload
- `CLOUD_MEDIATED`: temporary original handoff plus cloud working/thumbnail
- `ARCHDOX_AGENT_DIRECT`: future advanced option

### POST `/api/v1/photos/intent`

Creates a pending photo row and returns upload instructions.

Request:

```json
{
  "projectId": null,
  "reportId": 1000,
  "stepCode": "BASIC_INFO",
  "checklistItemId": null,
  "captureKind": "CAMERA",
  "mime": "image/jpeg",
  "bytes": 380000,
  "hash": "sha256:abc123...",
  "width": 1600,
  "height": 1200,
  "takenAt": "2026-05-21T14:30:00+09:00",
  "gpsLat": null,
  "gpsLng": null,
  "wantsOriginal": true
}
```

Response `201`:

```json
{
  "photoId": 9881,
  "target": "API_LOCAL",
  "uploadRequired": true,
  "uploads": [
    {
      "kind": "ORIGINAL",
      "method": "PUT",
      "url": "/api/v1/photos/9881/content/ORIGINAL",
      "fields": {},
      "headers": {},
      "token": null,
      "expiresAt": "2026-05-21T14:40:00+09:00"
    },
    {
      "kind": "WORKING",
      "method": "PUT",
      "url": "/api/v1/photos/9881/content/WORKING",
      "fields": {},
      "headers": {},
      "token": null,
      "expiresAt": "2026-05-21T14:40:00+09:00"
    },
    {
      "kind": "THUMBNAIL",
      "method": "PUT",
      "url": "/api/v1/photos/9881/content/THUMBNAIL",
      "fields": {},
      "headers": {},
      "token": null,
      "expiresAt": "2026-05-21T14:40:00+09:00"
    }
  ],
  "mediationJobId": null,
  "expiresAt": "2026-05-21T14:40:00+09:00",
  "photo": {
    "id": 9881,
    "officeId": 10,
    "projectId": 100,
    "reportId": 1000,
    "stepCode": "BASIC_INFO",
    "checklistItemId": null,
    "captureKind": "CAMERA",
    "status": "PENDING_UPLOAD",
    "mime": "image/jpeg",
    "width": null,
    "height": null,
    "bytes": 380000,
    "hash": "sha256:abc123...",
    "storageKind": "API_LOCAL",
    "storageRef": "offices/10/reports/1000/photos/.../working.jpg",
    "thumbnailStorageRef": "offices/10/reports/1000/photos/.../thumbnail.webp",
    "uploadTarget": "API_LOCAL",
    "originalPickupStatus": "PENDING",
    "originalPickedUpAt": null,
    "originalTemporaryDeletedAt": null,
    "assets": [
      {
        "assetType": "ORIGINAL",
        "status": "PENDING_UPLOAD",
        "storageKind": "API_LOCAL",
        "storageRef": "offices/10/reports/1000/photos/.../original.jpg",
        "mime": "image/jpeg",
        "bytes": 380000,
        "width": null,
        "height": null,
        "hash": "sha256:abc123...",
        "temporary": true
      },
      {
        "assetType": "WORKING",
        "status": "PENDING_UPLOAD",
        "storageKind": "API_LOCAL",
        "storageRef": "offices/10/reports/1000/photos/.../working.jpg",
        "mime": "image/jpeg",
        "bytes": 380000,
        "width": null,
        "height": null,
        "hash": "sha256:abc123...",
        "temporary": false
      },
      {
        "assetType": "THUMBNAIL",
        "status": "PENDING_UPLOAD",
        "storageKind": "API_LOCAL",
        "storageRef": "offices/10/reports/1000/photos/.../thumbnail.webp",
        "mime": "image/webp",
        "bytes": null,
        "width": null,
        "height": null,
        "hash": null,
        "temporary": false
      }
    ]
  }
}
```

If the same office already has an uploaded photo with the same hash, the
response has `uploadRequired = false` and `uploads = []`.

### PUT `/api/v1/photos/{photoId}/content/{kind}`

Uploads binary content for `ORIGINAL`, `WORKING`, or `THUMBNAIL`.

Request body: raw bytes.

Response `204`.

For S3-compatible targets, clients upload to the returned URL instead of this
API-local endpoint and must send returned `headers`, such as `Content-Type`,
exactly as provided.

### POST `/api/v1/photos/{photoId}/confirm`

Marks the photo as uploaded. For the office-default flow, `ORIGINAL` content is
enough to confirm the upload. Cloud then publishes `PhotoUploadConfirmed` and a
Flower flow generates:

- `WORKING`: resized working image for preview/document generation
- `THUMBNAIL`: WebP thumbnail for lists and wizard previews

`WORKING` and `THUMBNAIL` upload instructions may still be returned for clients
that can provide optimized derivatives, but backend generation is the default
fallback path.

Request:

```json
{
  "hash": "sha256:abc123...",
  "bytes": 380000,
  "width": 1600,
  "height": 1200
}
```

Response `200`: `PhotoResponse` with `status = UPLOADED`

The initial response may still show derivative assets as `PENDING_UPLOAD`
because generation runs asynchronously. Follow-up `GET /api/v1/photos/{photoId}`
or list calls should be used to observe generated asset status.

### POST `/api/v1/photos/{photoId}/agent-pickup-complete`

MVP REST fallback for ArchDox Agent pickup completion. The primary office flow is
now WebSocket `PHOTO_PICKUP` completion.
Records that the ArchDox Agent/NAS has the original and deletes the temporary
cloud/API-local original when requested.

Request:

```json
{
  "agentOriginalStorageRef": "reports/1000/photos/9881/original.jpg",
  "deleteTemporaryOriginal": true
}
```

Response `200`: `PhotoResponse` with:

```json
{
  "originalPickupStatus": "PICKED_UP",
  "originalPickedUpAt": "2026-05-21T14:45:00+09:00",
  "originalTemporaryDeletedAt": "2026-05-21T14:45:00+09:00"
}
```

### GET `/api/v1/photos?reportId={reportId}`

Response `200`: `PhotoResponse[]`

### GET `/api/v1/photos/{photoId}`

Response `200`: `PhotoResponse`

### GET `/api/v1/photos/{photoId}/assets/{assetType}/content`

User-facing authenticated preview endpoint.

Allowed `assetType` values:

- `THUMBNAIL`
- `WORKING`

`ORIGINAL` is intentionally rejected by this user API. Office-plan originals
are owned by the ArchDox Agent/NAS handoff policy, not by normal browser
preview.

Response `200`: raw image bytes.

Headers:

```text
Content-Type: <asset mime>
Content-Disposition: inline; filename="photo-..."
Cache-Control: no-store
```

Responses:

- `400`: `ORIGINAL` requested or invalid asset type
- `404`: photo or asset not found in the active office
- `409`: asset exists but is not ready for preview, or is agent-managed only

Browser clients should fetch this endpoint with `Authorization` and
`X-Office-Id`, create an object URL from the returned `Blob`, and use that URL
as the `<img>` source. Do not put this URL directly in `<img>` because the
request needs auth headers.

### GET `/agent/api/v1/photos/{photoId}/assets/{assetType}/content`

Internal Agent API endpoint used only when a Cloud command gives the ArchDox
Agent a Cloud API-local download URL. S3-compatible pickups normally use a
presigned `GET` URL instead.

Headers:

```text
X-Agent-Office-Id: <officeId>
X-Agent-Id: <agentId>
X-Agent-Device-Secret: <agent device secret>
```

Development fallback:

```text
X-Agent-Office-Id: <officeId>
X-Agent-Token: <agent shared secret>
```

Response `200`: raw asset bytes.

This endpoint is not a public client API. It validates the agent device
credential and office ownership before streaming `API_LOCAL` storage content.
`X-Agent-Token` remains a development fallback only while
`AGENT_ALLOW_SHARED_SECRET_AUTH=true`.

## Document Jobs

All document job endpoints require `Authorization` and `X-Office-Id`.

Document job creation is asynchronous: Cloud creates `document_jobs`, then the
REST entrypoint submits a `document-generation` Flower flow. Cloud API owns the
REST contract, tenancy, state, and progress records, but actual render/export
execution belongs to `archdox-agent`.

Document generation is a polling-based API flow:

1. Client calls the create endpoint.
2. Cloud resolves the current office/report configuration and snapshots the
   selected template/workflow/rule/layout revision references into the job input
   snapshot.
3. Cloud returns the created job immediately.
4. Cloud runs the Flower flow in the background.
5. Client polls `GET /api/v1/document-jobs/{jobId}` until the job reaches a
   terminal state.

Progress fields:

- `status`: coarse job state. Use this to decide whether the job is still
  running or finished.
- `progressStep`: UI-facing current step.
- `progressPercent`: integer `0` to `100`.
- `progressMessage`: short user-facing Korean message.

Current `progressStep` values:

- `QUEUED`: request accepted, waiting for background execution.
- `VALIDATING`: checking report/template/input readiness.
- `DISPATCHING`: sending a render command to the selected worker.
- `WAITING_FOR_AGENT`: ArchDox Agent accepted the command; Cloud is waiting for
  completion.
- `RENDERING`: generating document content.
- `STORING_ARTIFACTS`: saving generated file metadata and binaries.
- `GENERATED`: generation completed.
- `FAILED`: generation failed.

Worker routing:

- `workerType` is policy-routed by Cloud API when the client omits it. The UI
  should normally send only `outputFormat`.
- The only execution worker type is `ARCHDOX_AGENT`. Cloud API routes a
  `GENERATE_DOCUMENT` command to a selected ArchDox Agent through the WebSocket
  command channel. The ArchDox Agent runs `document-engine`, stores or uploads
  the result according to policy, then reports completion.
- Office plans with a local runtime should route to an online Agent with
  `deploymentMode=LOCAL_OFFICE`.
- Personal plans, and offices without a local runtime, should route to an online
  managed Agent with `deploymentMode=CLOUD_MANAGED`.
- Cloud API must not run `document-engine` directly and must not create an
  in-process render fallback.
- Cloud API must remain the owner of job state, progress state, tenant checks,
  and artifact metadata. Render workers own execution, not the public REST
  contract.
- PDF-capable routing requires an online ArchDox Agent with
  `capabilities.outputFormats` containing `PDF`, `DOCX_AND_PDF`, or
  `HTML_AND_PDF`, or `capabilities.pdfExport=true`.
- Template Binding V1 reads the selected template revision's `storageRef` from
  document storage when available and replaces `${...}` placeholders using the
  job input snapshot. DOCX Placeholder Hardening V1 supports both intact
  placeholders and placeholders split across multiple Word text nodes.
  Configured template revisions are `contentRequired`: if the selected DOCX
  content is missing or unreadable, generation fails with a template-content
  error instead of silently falling back to a simple generated document. The
  simple fallback is allowed only when no configured template revision is
  selected.
- Template Binding V1 now snapshots `project`, `site`, and `templateFields`.
  Template revision `schema.bindings` can map business-friendly placeholders to
  snapshot paths:

```json
{
  "bindings": {
    "projectName": "project.name",
    "siteName": "site.name",
    "inspectionDate": "steps.BASIC_INFO.payload.inspectionDate",
    "weather": "steps.BASIC_INFO.payload.weather"
  }
}
```

With that schema, a DOCX template may use `${projectName}` instead of exposing
the internal path `${steps.BASIC_INFO.payload.inspectionDate}`. The job snapshot
keeps the resolved `templateFields` values immutable for later auditing.

Template field catalog:

```http
GET /api/v1/config/document-template-fields?reportType=CONSTRUCTION_DAILY_SUPERVISION_LOG
```

The endpoint is read-only admin metadata. It lists standard placeholders that
can be used in DOCX templates and form presets derived from inspected Korean
construction supervision forms. It does not create templates and does not replace
template revision `schema.bindings`.

Example response:

```json
{
  "reportType": "CONSTRUCTION_DAILY_SUPERVISION_LOG",
  "fields": [
    {
      "key": "constructionName",
      "label": "Construction name",
      "category": "project-site",
      "source": "project.name",
      "example": "Document Tower",
      "description": "Public-form alias for project name.",
      "reportTypes": []
    }
  ],
  "presets": [
    {
      "code": "KOREAN_CONSTRUCTION_DAILY_SUPERVISION_APPENDIX_2",
      "title": "공식 제출용 공사감리일지 별지 2",
      "description": "ArchDox의 공사감리일지 정본입니다. 별지 제2호서식 기준의 공식 제출용 렌더러를 사용합니다.",
      "templateKind": "OFFICIAL_SUBMISSION",
      "customizationPolicy": "COPY_AND_OVERRIDE",
      "renderingPolicy": "BUNDLED_OFFICIAL_RENDERER",
      "reportTypes": ["CONSTRUCTION_DAILY_SUPERVISION_LOG"],
      "recommendedFields": ["documentTitle", "constructionName", "inspectionDate"],
      "layoutSections": ["CHECKLIST_TABLE", "PHOTO_TABLE"]
    }
  ]
}
```

Output formats:

- `DOCX`: render and return a DOCX artifact.
- `PDF`: render the configured source artifact, then export to PDF.
- `DOCX_AND_PDF`: return DOCX plus exported PDF.
- `HTML`: render a browser-preview HTML artifact from the report snapshot,
  template fields, layout sections, photos, and checklist answers.
- `HTML_AND_PDF`: return the HTML preview artifact plus exported PDF. The HTML
  artifact does not require an external converter, but the PDF artifact still
  requires a configured PDF exporter.
- `HWP`, `HWPX`: export Korean office document artifacts when a converter is
  configured.

If an output format requires an exporter that is not configured, such as PDF,
HWP, or HWPX conversion, generation fails with
`DOCUMENT_EXPORTER_NOT_CONFIGURED`.

If Cloud API can decide before job creation that no route can handle the
requested format, the create API returns a structured `400` instead of creating
a doomed job:

- `REPORT_PREFLIGHT_REVIEW_REQUIRED`: the current report generation revision has
  no passed preflight review.
- `REPORT_PREFLIGHT_REVIEW_STALE`: only an older report revision has a passed
  preflight review, so the latest submitted revision must be reviewed again.
- `DOCUMENT_WORKER_UNAVAILABLE`: no Cloud exporter and no online capable
  ArchDox Agent exists for the requested output format.
- `DOCUMENT_WORKER_UNSUPPORTED`: the request explicitly selected a worker type
  that cannot handle the requested output format.

PDF exporter V1 uses LibreOffice when enabled by runtime configuration. If the
exporter is enabled but conversion cannot complete, document generation fails
with one of:

- `DOCUMENT_PDF_EXPORTER_NOT_AVAILABLE`
- `DOCUMENT_PDF_EXPORT_TIMEOUT`
- `DOCUMENT_PDF_EXPORT_FAILED`
- `DOCUMENT_PDF_EXPORT_NO_OUTPUT`
- `DOCUMENT_PDF_EXPORT_NO_SOURCE_CONTENT`

Phase 4-3 target flow:

```text
POST document-jobs
-> persist document_jobs row
-> resolve office/report configuration
-> snapshot selected revisions into document_jobs.input_snapshot_json
-> return DocumentJobResponse with jobId immediately
-> submit DocumentRenderFlow
-> choose a capable ARCHDOX_AGENT target
-> dispatch GENERATE_DOCUMENT command
-> receive ACK / COMPLETED / FAILED event
-> update document_jobs progress/status
-> UI polls GET document-jobs/{jobId}
```

Minimum verification before adding more worker routes:

- Create API returns immediately with `status=REQUESTED`,
  `progressStep=QUEUED`, and `progressPercent=0`.
- Create API snapshots selected config under
  `input_snapshot_json.configuration`.
- Background flow can move the same job to `GENERATED`.
- Polling `GET /api/v1/document-jobs/{jobId}` returns
  `progressStep=GENERATED`, `progressPercent=100`, and artifact metadata.
- The test covers the REST response, persistence/migration, background flow,
  and artifact row creation together.

### POST `/api/v1/inspection-reports/{reportId}/document-jobs`

Creates a document generation job from the submitted inspection report snapshot.
The report must be in `READY_TO_GENERATE`, `GENERATED`, or `FAILED`.
The job records `reportRevision` so generated artifacts can be tied to the
exact report content revision used at generation time.

Before creating a job, Cloud API verifies that the latest generation revision has
a `PASSED` preflight review run. A passed review for an older revision is treated
as stale and cannot be reused for document generation.

Request:

```json
{
  "outputFormat": "DOCX",
  "signature": {
    "signedByName": "김감리",
    "signedByRole": "작성자",
    "signatureImageMimeType": "image/png",
    "signatureImageDataUrl": "data:image/png;base64,..."
  }
}
```

Fields:

- `outputFormat`: `DOCX`
- `workerType`: omitted means Cloud API applies the routing policy. Explicit
  `workerType` is accepted only for tests, admin tooling, or emergency
  overrides and is validated against worker capability.
- `signature`: optional. The user-facing UI may offer signing before document
  generation, but the user can generate without a signature. The image must be a
  base64 data URL using `image/png`, `image/jpeg`, or `image/webp`.

When `signature` is present, Cloud API stores it in the job
`inputSnapshotJson.signature` and also exposes common aliases under
`inputSnapshotJson.templateFields` such as `signedByName`, `signedByRole`, and
`signatureSignedAt`. Cloud API also normalizes internal `signatureSlots` from the
active office type and role/assignment policy. Clients do not send
`signatureSlots` directly. For a `PERSONAL` workspace, a supplied signature is
treated as an all-in-one personal signer and may be applied to official slots
such as `CHIEF_SUPERVISOR` and `ARCHITECT_ASSISTANT`; skipped signatures still
render blank areas. For an `OFFICE` workspace, report/project assignments select
the matching slot, for example project `MANAGER` to `CHIEF_SUPERVISOR` and
report `WRITER`/project `REPORT_WRITER` to `ARCHITECT_ASSISTANT`/`WRITER`.
The document type/template decides whether the signature is rendered. The
document engine may render `${signatureBlock}`,
`${signatureImage}`, `${writerSignature}`, or document-type default signature
blocks such as the default daily supervision log signature area. If no signature
is supplied, signature placeholders render as blank.

Response `201`:

```json
{
  "id": 7001,
  "officeId": 10,
  "reportId": 1000,
  "projectId": 100,
  "reportRevision": 1,
  "status": "REQUESTED",
  "progressStep": "QUEUED",
  "progressPercent": 0,
  "progressMessage": "...",
  "workerType": "ARCHDOX_AGENT",
  "outputFormat": "DOCX",
  "errorCode": null,
  "errorMessage": null,
  "requestedAt": "2026-05-21T18:00:00+09:00",
  "startedAt": null,
  "completedAt": null,
  "artifacts": []
}
```

Poll the job detail endpoint until `status = GENERATED` to read artifact
metadata:

```json
{
  "id": 7001,
  "reportRevision": 1,
  "status": "GENERATED",
  "progressStep": "GENERATED",
  "progressPercent": 100,
  "progressMessage": "...",
  "artifacts": [
    {
      "id": 9001,
      "artifactType": "DOCX",
      "storageKind": "API_LOCAL",
      "storageRef": "documents/jobs/7001/inspection-report-1000.docx",
      "fileName": "inspection-report-1000.docx",
      "mimeType": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
      "bytes": 12345,
      "hashSha256": "hex-sha256",
      "createdAt": "2026-05-21T18:00:01+09:00"
    }
  ]
}
```

### GET `/api/v1/inspection-reports/{reportId}/document-jobs`

Response `200`: `DocumentJobResponse[]` ordered by newest request first.

### GET `/api/v1/document-jobs/{jobId}`

Response `200`: `DocumentJobResponse`

## Document Artifact Delivery

All document delivery endpoints require `Authorization` and `X-Office-Id`.

Phase 4-4 implements direct download for Cloud-owned `API_LOCAL` artifacts and
delivery request tracking for all document artifacts. Phase 4-6 adds
`DOWNLOAD + ARCHDOX_AGENT` preparation: Cloud asks the ArchDox Agent to upload
the selected artifact to a temporary Cloud-managed delivery object, then the
browser downloads that prepared object through Cloud.

### POST `/api/v1/document-jobs/{jobId}/delivery-requests`

Creates a delivery request for a generated document job. The job must be
`GENERATED`.

Request:

```json
{
  "artifactId": 9001,
  "channel": "DOWNLOAD",
  "recipientRef": null
}
```

Defaults:

- `channel`: `DOWNLOAD`
- `artifactId`: first artifact for the job when omitted

Response `201`:

```json
{
  "id": 9101,
  "officeId": 10,
  "documentJobId": 7001,
  "artifactId": 9001,
  "channel": "DOWNLOAD",
  "status": "COMPLETED",
  "recipientRef": null,
  "errorMessage": null,
  "downloadUrl": "/api/v1/document-artifacts/9001/download",
  "requestedAt": "2026-05-21T18:00:02+09:00",
  "completedAt": "2026-05-21T18:00:02+09:00",
  "updatedAt": "2026-05-21T18:00:02+09:00"
}
```

For `DOWNLOAD + API_LOCAL`, Cloud can immediately return `downloadUrl` and mark
the request `COMPLETED`.

For `DOWNLOAD + ARCHDOX_AGENT`, Cloud records the request as `SENDING`, creates
and submits the `document-delivery` Flower flow, which then creates an
`UPLOAD_DOCUMENT_ARTIFACT` ArchDox Agent command. The initial response has
`downloadUrl = null`. The UI should poll the delivery request. When the Agent
uploads the artifact successfully, Cloud marks the request `COMPLETED` and
returns a delivery-specific download URL. Retry/backoff and terminal failure for
this delivery are Flower responsibilities.

### GET `/api/v1/document-jobs/{jobId}/delivery-requests`

Response `200`: `DocumentDeliveryRequestResponse[]` ordered by newest request
first.

### GET `/api/v1/document-delivery-requests/{deliveryRequestId}`

Response `200`: `DocumentDeliveryRequestResponse`

When an Agent-backed delivery is completed, `downloadUrl` uses the delivery
request endpoint:

```json
{
  "id": 9101,
  "status": "COMPLETED",
  "downloadUrl": "/api/v1/document-delivery-requests/9101/download"
}
```

### GET `/api/v1/document-delivery-requests/{deliveryRequestId}/download`

Streams a prepared delivery object. This endpoint is used when the original
artifact is `ARCHDOX_AGENT` but the Agent has uploaded a Cloud-managed prepared
copy for this delivery request.

Response `409`: delivery request is not ready.

### GET `/api/v1/document-artifacts/{artifactId}/download`

Streams the artifact binary when the artifact belongs to the active office and
`storageKind = API_LOCAL`.

Response `200`:

```text
Content-Type: application/vnd.openxmlformats-officedocument.wordprocessingml.document
Content-Disposition: attachment; filename="inspection-report-1000.docx"
Cache-Control: no-store
```

Response `409`: direct Cloud download is not available for this storage kind,
for example `ARCHDOX_AGENT`.

## ArchDox Agent Delivery Upload

Agent-only endpoint. The ArchDox Agent calls this after receiving
`UPLOAD_DOCUMENT_ARTIFACT`.

### PUT `/agent/api/v1/document-delivery-requests/{deliveryRequestId}/content`

Headers:

```text
X-Agent-Office-Id: <officeId>
X-Agent-Id: <agentId>
X-Agent-Device-Secret: <deviceSecret>
```

Development fallback:

```text
X-Agent-Token: <sharedSecret>
```

Request content type: `multipart/form-data`

Fields:

- `file`: generated artifact binary

Response `200`:

```json
{
  "deliveryRequestId": 9101,
  "artifactId": 9001,
  "preparedStorageKind": "API_LOCAL",
  "preparedStorageRef": "deliveries/9101/inspection-report-1000.docx",
  "bytes": 12345,
  "hashSha256": "hex-sha256"
}
```

Cloud validates the delivery office, artifact ownership, byte count when known,
and SHA-256 when the artifact has a valid hash.

## ArchDox Agent Install Tokens

### POST `/api/v1/archdox-agents/install-tokens`

Creates or reuses a registered ArchDox Agent row for the active office and
issues a short-lived one-time install token bound to that Agent. The token is
not a generic office password.

Headers:

```text
Authorization: Bearer <accessToken>
X-Office-Id: <officeId>
```

Request:

```json
{
  "expiresInMinutes": 30,
  "agentCode": "office-main",
  "deploymentMode": "LOCAL_OFFICE"
}
```

Defaults:

- `agentCode`: `office-main`
- `deploymentMode`: `LOCAL_OFFICE`

For a managed cloud document Agent use a stable code such as
`cloud-managed-1` and `deploymentMode=CLOUD_MANAGED`.

Response `201`:

```json
{
  "id": 12,
  "officeId": 10,
  "agentId": 1,
  "agentCode": "office-main",
  "deploymentMode": "LOCAL_OFFICE",
  "status": "ACTIVE",
  "token": "one-time-install-token",
  "expiresAt": "2026-05-21T15:45:00+09:00"
}
```

The raw `token` is returned only once. Cloud stores only its hash and the
registered Agent id it was issued for.

## ArchDox Agent WebSocket

The ArchDox Agent connects outbound to Cloud API.

Official runtime concept:

- `archdox-agent` is one executable runtime.
- `deploymentMode=LOCAL_OFFICE` means office-installed agent using NAS/local
  storage by default.
- `deploymentMode=CLOUD_MANAGED` means the same runtime can be deployed as a
  managed cloud document agent; storage should normally be S3-compatible.
- Storage is reported as a profile, not inferred from the agent name.

Endpoint:

```text
ws://{cloud-host}/agent/ws
```

Default authentication uses install-token pairing followed by agent-specific
device credentials.

Registration/authentication rules:

- Every Agent, including `CLOUD_MANAGED` Agents running on the same server as
  Cloud API, must be registered before it can connect.
- `INSTALL_TOKEN` pairing must match the registered `agentId`, `agentCode`,
  office, and deployment mode.
- After pairing, the Agent reconnects with `agentId + deviceSecret`.
- The registered deployment mode is authoritative; an already paired Agent
  cannot reconnect as a different deployment mode by changing local config.
- `AGENT_SHARED_SECRET` is disabled by default and is a development fallback
  only when `AGENT_ALLOW_SHARED_SECRET_AUTH=true` is explicitly set.

### Agent -> Cloud: `HELLO` with install token

```json
{
  "type": "HELLO",
  "authMode": "INSTALL_TOKEN",
  "officeId": 10,
  "agentCode": "office-main",
  "installToken": "one-time-install-token",
  "version": "0.0.1-dev",
  "deploymentMode": "LOCAL_OFFICE",
  "capabilities": {
    "nas": false,
    "photoPickup": true,
    "documentGeneration": true,
    "documentRender": true,
    "documentArtifactDelivery": true,
    "pdfExport": false,
    "outputFormats": ["DOCX", "HTML"]
  },
  "storageProfile": {
    "original": {"kind": "LOCAL_FILE", "fileSystemBacked": true, "rootConfigured": true},
    "working": {"kind": "LOCAL_FILE", "fileSystemBacked": true, "rootConfigured": true},
    "artifact": {"kind": "LOCAL_FILE", "fileSystemBacked": true, "rootConfigured": true},
    "template": {"kind": "LOCAL_FILE", "fileSystemBacked": true, "rootConfigured": true}
  }
}
```

Cloud response:

```json
{
  "type": "WELCOME",
  "agentId": 1,
  "authMode": "INSTALL_TOKEN",
  "deviceSecret": "agent-device-secret-returned-once"
}
```

The ArchDox Agent operator must store `agentId` and `deviceSecret`, then remove
the install token.

### Agent -> Cloud: `HELLO` with device secret

```json
{
  "type": "HELLO",
  "authMode": "DEVICE_SECRET",
  "agentId": 1,
  "deviceSecret": "agent-device-secret",
  "version": "0.0.1-dev",
  "deploymentMode": "LOCAL_OFFICE",
  "capabilities": {
    "nas": true,
    "photoPickup": true,
    "documentGeneration": true,
    "documentRender": true,
    "documentArtifactDelivery": true,
    "pdfExport": true,
    "outputFormats": ["DOCX", "HTML", "PDF", "DOCX_AND_PDF", "HTML_AND_PDF"]
  },
  "storageProfile": {
    "original": {"kind": "NAS", "fileSystemBacked": true, "rootConfigured": true},
    "working": {"kind": "NAS", "fileSystemBacked": true, "rootConfigured": true},
    "artifact": {"kind": "NAS", "fileSystemBacked": true, "rootConfigured": true},
    "template": {"kind": "NAS", "fileSystemBacked": true, "rootConfigured": true}
  }
}
```

Cloud response:

```json
{
  "type": "WELCOME",
  "agentId": 1,
  "authMode": "DEVICE_SECRET"
}
```

Development fallback `authMode=SHARED_SECRET` keeps the previous
`officeId + agentCode + token` shape only when explicitly allowed by Cloud
configuration. Production, AWS, and local server operations should keep it
disabled.

`storageProfile` is safe capability metadata. It must not include local or NAS
absolute `rootPath` values. `LOCAL_FS` may be accepted by old Agent
configuration as an alias, but WebSocket contracts should report `LOCAL_FILE`.

### Agent -> Cloud: `HEARTBEAT`

```json
{
  "type": "HEARTBEAT",
  "version": "0.0.1-dev",
  "diskFreeBytes": 1000000000,
  "pendingJobs": 0,
  "recentErrorCount": 0
}
```

### Cloud -> Agent: `COMMAND`

```json
{
  "type": "COMMAND",
  "commandId": 55,
  "commandType": "PHOTO_PICKUP",
  "payload": {
    "photoId": 9881,
    "officeId": 10,
    "projectId": 100,
    "reportId": 1000,
    "sourceStorageKind": "S3_TEMP",
    "sourceStorageRef": "offices/10/reports/1000/photos/.../original.jpg",
    "mime": "image/jpeg",
    "bytes": 380000,
    "hash": "sha256:abc123...",
    "downloadMethod": "GET",
    "downloadUrl": "https://s3-provider.example/presigned-get-url",
    "downloadHeaders": {},
    "downloadExpiresAt": "2026-05-21T15:45:00+09:00",
    "suggestedAgentOriginalStorageRef": "offices/10/reports/1000/photos/.../original.jpg",
    "attempt": 1,
    "maxAttempts": 5,
    "deleteTemporaryOriginal": true
  }
}
```

For `API_LOCAL` development storage, `downloadUrl` may be a relative Cloud API
URL such as `/agent/api/v1/photos/9881/assets/ORIGINAL/content`. The ArchDox Agent
resolves it against `CLOUD_API_BASE_URL` and adds agent headers.

For document rendering, Cloud sends `commandType=GENERATE_DOCUMENT`:

```json
{
  "type": "COMMAND",
  "commandId": 56,
  "commandType": "GENERATE_DOCUMENT",
  "payload": {
    "documentJobId": 7001,
    "officeId": 10,
    "reportId": 1000,
    "outputFormat": "DOCX",
    "renderPackageMethod": "GET",
    "renderPackageUrl": "/agent/api/v1/document-jobs/7001/render-package",
    "resultStorageKind": "ARCHDOX_AGENT",
    "attempt": 1,
    "maxAttempts": 3,
    "expiresAt": "2026-05-21T15:45:00+09:00"
  }
}
```

The WebSocket command payload is intentionally small. It is a control-plane
envelope only. The ArchDox Agent then fetches the full render package over
Agent-authenticated HTTP.

### Agent -> Cloud: render package

`GET /agent/api/v1/document-jobs/{documentJobId}/render-package`

Headers:

- `X-Agent-Id`
- `X-Agent-Device-Secret`
- `X-Agent-Office-Id`

Development installs may use `X-Agent-Token` only when explicitly enabled.

Response `200`:

```json
{
  "documentJobId": 7001,
  "officeId": 10,
  "reportId": 1000,
  "outputFormat": "DOCX",
  "resultStorageKind": "ARCHDOX_AGENT",
  "template": {
    "templateCode": "INSPECTION_REPORT",
    "version": 1,
    "storageRef": "templates/default.docx",
    "schemaJson": "{}",
    "composePolicyJson": "{}",
    "downloadMethod": "GET",
    "downloadUrl": "/agent/api/v1/document-jobs/7001/template/content"
  },
  "inputSnapshot": {},
  "photos": [
    {
      "photoId": "9881",
      "checklistItemKey": "BASIC_INFO",
      "storageRef": "offices/10/reports/1000/photos/.../working.jpg",
      "caption": "Front view",
      "layoutSize": "MEDIUM",
      "mimeType": "image/jpeg",
      "downloadUrl": "/agent/api/v1/photos/9881/assets/WORKING/content"
    }
  ]
}
```

When `downloadUrl` is present in the render package, the ArchDox Agent resolves
relative URLs against `CLOUD_API_BASE_URL`, authenticates with its agent
credentials, downloads the DOCX template or working photo, and may cache it
under its configured storage root by `storageRef`.
If the template is already cached locally, the Agent can render without
downloading again because template revisions are immutable. Photo `downloadUrl`
is used only when the Agent cannot already resolve the working image from its
configured local working-photo storage.

Older command payloads may contain full render inputs during migration, but new
Cloud implementations must use the render-package URL shape above.

For Agent-backed document delivery, Cloud sends
`commandType=UPLOAD_DOCUMENT_ARTIFACT`:

```json
{
  "type": "COMMAND",
  "commandId": 57,
  "commandType": "UPLOAD_DOCUMENT_ARTIFACT",
  "payload": {
    "officeId": 10,
    "deliveryRequestId": 9101,
    "documentJobId": 7001,
    "artifactId": 9001,
    "sourceStorageKind": "ARCHDOX_AGENT",
    "sourceStorageRef": "documents/jobs/7001/inspection-report-1000.docx",
    "fileName": "inspection-report-1000.docx",
    "mimeType": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "bytes": 12345,
    "hashSha256": "hex-sha256",
    "uploadMethod": "PUT_MULTIPART",
    "uploadUrl": "/agent/api/v1/document-delivery-requests/9101/content",
    "resultStorageKind": "API_LOCAL",
    "attempt": 1,
    "maxAttempts": 5,
    "expiresAt": "2026-05-21T15:45:00+09:00"
  }
}
```

### Agent -> Cloud: `ACK`

```json
{
  "type": "ACK",
  "commandId": 55
}
```

### Agent -> Cloud: `COMPLETE`

For `PHOTO_PICKUP`, completion result must include the agent-managed logical
original reference.

```json
{
  "type": "COMPLETE",
  "commandId": 55,
  "result": {
    "photoId": 9881,
    "officeId": 10,
    "agentOriginalStorageRef": "offices/10/reports/1000/photos/.../original.jpg",
    "storedBytes": 380000,
    "deleteTemporaryOriginal": true
  }
}
```

For `GENERATE_DOCUMENT`, completion result reports artifact metadata. The binary
stays in ArchDox Agent/NAS storage unless a later delivery flow uploads or shares
it.

```json
{
  "type": "COMPLETE",
  "commandId": 56,
  "result": {
    "documentJobId": 7001,
    "officeId": 10,
    "reportId": 1000,
    "artifacts": [
      {
        "artifactType": "DOCX",
        "storageKind": "ARCHDOX_AGENT",
        "storageRef": "documents/jobs/7001/inspection-report-1000.docx",
        "fileName": "inspection-report-1000.docx",
        "mimeType": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "bytes": 12345,
        "hashSha256": "hex-sha256"
      }
    ]
  }
}
```

For `UPLOAD_DOCUMENT_ARTIFACT`, completion result mirrors the Agent upload API
response.

```json
{
  "type": "COMPLETE",
  "commandId": 57,
  "result": {
    "deliveryRequestId": 9101,
    "artifactId": 9001,
    "preparedStorageKind": "API_LOCAL",
    "preparedStorageRef": "deliveries/9101/inspection-report-1000.docx",
    "bytes": 12345,
    "hashSha256": "hex-sha256"
  }
}
```

### Agent -> Cloud: `FAIL`

```json
{
  "type": "FAIL",
  "commandId": 55,
  "errorCode": "AGENT_REMOTE_SERVICE_UNAVAILABLE",
  "retryable": true,
  "errorMessage": "NAS unavailable",
  "result": {
    "errorCode": "AGENT_REMOTE_SERVICE_UNAVAILABLE",
    "retryable": true,
    "message": "NAS unavailable"
  }
}
```

`errorCode` is the machine-readable failure reason. `retryable` tells the
Flower flow whether the failed attempt may be retried. New Agent
implementations should send both as top-level fields and also mirror them in
`result` for older command handlers. If `retryable` is missing, Cloud must treat
the Agent-reported failure as non-retryable unless a bounded error-code policy
explicitly says otherwise.

For `PHOTO_PICKUP`, Cloud records the command as failed and publishes a Bloom
event. The `photo-pickup` Flower flow treats the failed command attempt as
retryable until the flow retry budget is exhausted.
The default retry policy is exponential backoff:

- max attempts: `5`
- base delay: `30` seconds
- max delay: `300` seconds

Each Flower retry dispatches a new `PHOTO_PICKUP` command and refreshes the
download URL, so S3-compatible presigned URLs are not reused after expiry. When
the retry budget is exhausted or an in-flight command times out at the Flower
step timeout, Cloud marks the pickup as failed.

## Operation Events

Operation events are structured, searchable workflow and operations facts. They
are not raw application logs and they are not a replacement for audit logs.

### GET `/api/v1/operation-events`

Returns recent operation events for the current office.

Headers:

- `Authorization: Bearer <accessToken>`
- `X-Office-Id: <officeId>`

Query parameters:

- `eventType`
- `workflowType`
- `workflowKey`
- `resourceType`
- `resourceId`
- `limit`

Default limit is `50`. Maximum limit is `200`.

Response `200`:

```json
[
  {
    "id": 120,
    "officeId": 10,
    "severity": "INFO",
    "eventType": "DOCUMENT_JOB_GENERATED",
    "workflowType": "document-generation",
    "workflowKey": "document-job:700",
    "resourceType": "DOCUMENT_JOB",
    "resourceId": "700",
    "actorUserId": 1,
    "correlationId": null,
    "message": "Document job generated.",
    "payload": {
      "reportId": 1000,
      "workerType": "ARCHDOX_AGENT",
      "artifactCount": 1
    },
    "createdAt": "2026-05-21T10:15:30Z"
  }
]
```

Implemented event families:

- flow recovery summary
- ArchDox Agent command enqueue/ack/complete/fail/recovery-expire
- photo original pickup complete/fail
- document job requested/generated/failed
- document delivery requested/completed/failed

Platform-wide operation event search is not part of this office-scoped API.
That belongs to future Admin APIs with explicit platform admin authorization.

## Configuration Registry

Configuration registry APIs are office-admin scoped metadata APIs. They are the
foundation for absorbing customer-specific differences without office-specific
code branches.

Rules:

- require `Authorization: Bearer <accessToken>`
- require `X-Office-Id: <officeId>`
- caller must be active office `OWNER` or `ADMIN`
- current MVP creates office-owned configuration only
- schema supports system defaults with `office_id NULL`, but system-default
  authoring belongs to future platform-admin or seed tooling
- revisions are created as `DRAFT` and become selectable after explicit
  publication
- office overrides can reference published office-owned revisions or published
  system default revisions

### Definition APIs

The following definition groups share the same basic create/list shape:

- `/api/v1/config/document-templates`
- `/api/v1/config/workflow-definitions`
- `/api/v1/config/rule-sets`
- `/api/v1/config/output-layouts`

List:

```text
GET /api/v1/config/{group}?reportType=CONSTRUCTION_DAILY_SUPERVISION_LOG
```

Create:

```json
{
  "code": "CONSTRUCTION_DAILY_TEMPLATE",
  "name": "Construction Daily Template",
  "reportType": "CONSTRUCTION_DAILY_SUPERVISION_LOG"
}
```

Response:

```json
{
  "id": 1,
  "officeId": 10,
  "code": "CONSTRUCTION_DAILY_TEMPLATE",
  "name": "Construction Daily Template",
  "reportType": "CONSTRUCTION_DAILY_SUPERVISION_LOG",
  "status": "ACTIVE",
  "createdBy": 1,
  "createdAt": "2026-05-22T00:00:00+09:00",
  "updatedAt": "2026-05-22T00:00:00+09:00"
}
```

### Document Template Revisions

```text
GET /api/v1/config/document-templates/{templateId}/revisions
POST /api/v1/config/document-templates/{templateId}/revisions
PUT /api/v1/config/document-template-revisions/{revisionId}/content
GET /api/v1/config/document-template-revisions/{revisionId}/content
POST /api/v1/config/document-template-revisions/{revisionId}/publish
```

Create request:

```json
{
  "templateStorageKind": "API_LOCAL",
  "templateStorageRef": "templates/daily.docx",
  "schema": {
    "required": ["projectName"]
  },
  "composePolicy": {
    "photoSection": "photoTable"
  },
  "aiPrompts": {}
}
```

Template content upload:

```http
PUT /api/v1/config/document-template-revisions/{revisionId}/content
Content-Type: multipart/form-data
```

Multipart fields:

```text
file: .docx
```

Rules:

- only office-owned `DRAFT` revisions can receive uploads
- upload accepts DOCX template files only
- the server stores the file under document object storage and assigns
  `templateStorageKind = API_LOCAL`
- the server generates `templateStorageRef`; callers should not construct
  storage paths themselves
- once the revision is published, template content is immutable

Upload response:

```json
{
  "id": 101,
  "templateId": 1,
  "version": 1,
  "status": "DRAFT",
  "templateStorageKind": "API_LOCAL",
  "templateStorageRef": "templates/offices/10/document-templates/1/revisions/101/daily.docx",
  "schema": {},
  "composePolicy": {},
  "aiPrompts": {},
  "createdBy": 1,
  "publishedBy": null,
  "createdAt": "2026-05-22T00:00:00+09:00",
  "publishedAt": null
}
```

Template content download:

```http
GET /api/v1/config/document-template-revisions/{revisionId}/content
```

Response is the stored DOCX binary with `Content-Disposition: attachment`.

### JSON Config Revisions

Workflow definitions, rule sets, and output layouts use the same revision shape:

```text
GET /api/v1/config/workflow-definitions/{definitionId}/revisions
POST /api/v1/config/workflow-definitions/{definitionId}/revisions
POST /api/v1/config/workflow-definition-revisions/{revisionId}/publish

GET /api/v1/config/rule-sets/{ruleSetId}/revisions
POST /api/v1/config/rule-sets/{ruleSetId}/revisions
POST /api/v1/config/rule-set-revisions/{revisionId}/publish

GET /api/v1/config/output-layouts/{configId}/revisions
POST /api/v1/config/output-layouts/{configId}/revisions
POST /api/v1/config/output-layout-revisions/{revisionId}/publish
```

Create request:

```json
{
  "payload": {
    "workflow": ["PHOTO_UPLOAD", "REVIEW", "PDF_GENERATE"]
  }
}
```

Output layout revision payloads may use a `sections` array:

```json
{
  "payload": {
    "sections": [
      {
        "key": "photoSection",
        "type": "PHOTO_TABLE",
        "title": "Photo Section",
        "photosPerRow": 2,
        "imageSize": "THUMBNAIL",
        "fields": [
          {
            "label": "Caption",
            "source": "caption"
          }
        ]
      },
      {
        "key": "checklistSection",
        "type": "CHECKLIST_TABLE",
        "title": "Checklist Section",
        "fields": [
          {
            "label": "Item",
            "source": "itemCode",
            "width": 1800
          },
          {
            "label": "Result",
            "source": "answer.value",
            "width": 2400
          },
          {
            "label": "Note",
            "source": "note",
            "width": 4800
          }
        ],
        "emptyText": "No checklist answers saved.",
        "headerFill": "FFF2CC",
        "borderColor": "C9A227"
      }
    ]
  }
}
```

During document generation, Cloud writes rendered section text to
`input_snapshot_json.layoutSections` and also exposes each section key under
`input_snapshot_json.templateFields`. A DOCX template can therefore reference
`${photoSection}` and `${checklistSection}`.

Supported V1 section types:

- `PHOTO_SUMMARY`, `PHOTO_LIST`, `PHOTO_TABLE`
- `CHECKLIST_SUMMARY`, `CHECKLIST_LIST`, `CHECKLIST_TABLE`
- `CHECKLIST_PHOTO_SUMMARY`, `CHECKLIST_PHOTO_LIST`,
  `CHECKLIST_PHOTO_TABLE`
- `VALUE`, `FIELD`, `SNAPSHOT_VALUE`
- `TEXT`

Text-block binding remains available for all section types. In addition,
`PHOTO_TABLE` is a rich DOCX section: when the template contains the section
placeholder as its own Word paragraph, for example `${photoSection}`,
`document-engine` replaces that paragraph with a Word table. If the selected
worker can resolve working-image content from Cloud or Agent storage, the table
embeds the images into `word/media/*` and writes the corresponding document
relationships. If image content is unavailable, the table still renders metadata
and marks the image cell as unavailable.

Supported `PHOTO_TABLE` layout options:

- `photosPerRow`: `1`, `2`, or `3`; values outside the range are clamped.
  `1` renders an image/detail table. `2` and `3` render a grid.
- `imageSize`: `THUMBNAIL`, `MEDIUM`, or `ORIGINAL`.
- `fields`: supported photo metadata sources include `photoId`, `stepCode`
  or `checklistItemKey`, `caption`, `storageRef`, and `mimeType`.
- `photoColumnWidth` and `descriptionColumnWidth`, or `columnWidths` with two
  values, may adjust the detail-table column widths.

`CHECKLIST_TABLE` is also a rich DOCX section. It renders the saved
`checklistAnswers` snapshot as a Word table when the section placeholder is its
own paragraph. Supported field sources include `itemCode`, `label`,
`answer.value`, `answer.result`, `photoCount`, `photoIds`, `photos`, and
`note`. This is still a narrow table layout, not an arbitrary DOCX programming
layer.

`CHECKLIST_PHOTO_TABLE` renders grouped checklist evidence rows from
`input_snapshot_json.checklistPhotos`. Default field sources are `itemCode`,
`label`, `photoCount`, and `photoIds`. Use this when the document needs a
separate proof-photo summary by checklist item rather than a full photo grid.

Shared rich table polish options:

- `includeTitle`: defaults to `true`; `false` suppresses the title row.
- `emptyText` or `emptyMessage`: message shown when the source list is empty.
- `tableStyle`: optional Word table style id already available in the DOCX
  template.
- `borderColor`, `headerFill`, `titleFill`: six-digit hex colors with or
  without `#`.
- `tableWidth`: Word DXA width; defaults to `9000`.
- `CHECKLIST_TABLE` and `CHECKLIST_PHOTO_TABLE` can use `fields[].width` or
  `columnWidths` to control visible column widths.

### Office Config Overrides

```text
GET /api/v1/config/office-overrides
PUT /api/v1/config/office-overrides/{reportType}
```

Request:

```json
{
  "templateRevisionId": 100,
  "workflowRevisionId": 200,
  "ruleSetRevisionId": 300,
  "outputLayoutRevisionId": 400,
  "effectiveFrom": null,
  "effectiveTo": null
}
```

Response includes each selected part with `source=OFFICE_OVERRIDE`.

### Resolve Configuration

```text
GET /api/v1/config/resolve?reportType=CONSTRUCTION_DAILY_SUPERVISION_LOG
```

Response:

```json
{
  "officeId": 10,
  "reportType": "CONSTRUCTION_DAILY_SUPERVISION_LOG",
  "template": {
    "source": "OFFICE_OVERRIDE",
    "definitionId": 1,
    "revisionId": 100,
    "code": "CONSTRUCTION_DAILY_TEMPLATE",
    "name": "Construction Daily Template",
    "reportType": "CONSTRUCTION_DAILY_SUPERVISION_LOG",
    "version": 1
  },
  "workflow": {
    "source": "NOT_CONFIGURED",
    "definitionId": null,
    "revisionId": null,
    "code": null,
    "name": null,
    "reportType": null,
    "version": null
  },
  "ruleSet": {
    "source": "NOT_CONFIGURED",
    "definitionId": null,
    "revisionId": null,
    "code": null,
    "name": null,
    "reportType": null,
    "version": null
  },
  "outputLayout": {
    "source": "NOT_CONFIGURED",
    "definitionId": null,
    "revisionId": null,
    "code": null,
    "name": null,
    "reportType": null,
    "version": null
  }
}
```

## Office Ops Read APIs

Office Ops APIs are office-scoped operational read models. They are intended
for the office admin console, not for normal report editing screens.

Rules:

- require `Authorization: Bearer <accessToken>`
- require `X-Office-Id: <officeId>`
- caller must be an active office `OWNER` or `ADMIN`
- endpoints are read-only
- responses must not include device secrets, install tokens, signed URLs, raw
  file contents, or full command payloads
- default `limit` is `50`
- max `limit` is `200`

### GET `/api/v1/office-ops/summary`

Returns aggregate state for the current office.

Response `200`:

```json
{
  "officeId": 10,
  "agents": {
    "total": 2,
    "byStatus": {
      "ONLINE": 1,
      "OFFLINE": 1
    }
  },
  "activeAgentSessions": 1,
  "inFlightAgentCommands": 3,
  "documentJobs": {
    "total": 12,
    "byStatus": {
      "REQUESTED": 1,
      "GENERATING": 2,
      "GENERATED": 8,
      "FAILED": 1
    }
  },
  "photos": {
    "total": 40,
    "byStatus": {
      "PENDING_UPLOAD": 1,
      "UPLOADED": 39
    }
  },
  "photoOriginalPickups": {
    "total": 40,
    "byStatus": {
      "PENDING": 2,
      "PICKED_UP": 35,
      "FAILED": 1,
      "NOT_REQUIRED": 2
    }
  },
  "documentDeliveries": {
    "total": 5,
    "byStatus": {
      "REQUESTED": 0,
      "SENDING": 1,
      "COMPLETED": 4,
      "FAILED": 0
    }
  },
  "generatedAt": "2026-05-21T10:15:30Z"
}
```

### GET `/api/v1/office-ops/agents`

Query parameters:

- `limit`

Returns registered ArchDox Agents for the current office with active session
summaries and command counts.

### GET `/api/v1/office-ops/agent-sessions`

Query parameters:

- `limit`

Returns recent ArchDox Agent WebSocket sessions for the current office.

### GET `/api/v1/office-ops/agent-commands`

Query parameters:

- `agentId`
- `status`
- `limit`

Returns recent command transport records without raw command payloads.

### GET `/api/v1/office-ops/document-jobs`

Query parameters:

- `status`
- `limit`

Returns recent document jobs with artifact metadata.

### GET `/api/v1/office-ops/photos`

Query parameters:

- `status`
- `originalPickupStatus`
- `limit`

Returns recent photo pipeline rows with asset summaries. GPS coordinates are
not returned; the response only exposes whether GPS is present.

### GET `/api/v1/office-ops/document-deliveries`

Query parameters:

- `status`
- `limit`

Returns recent document delivery requests. Prepared storage references are not
returned from this office ops view.

## Platform Admin Ops

All platform admin endpoints require an authenticated user with an active
`platform_admins` row. Office `OWNER` or `ADMIN` membership is not enough.

### GET `/api/v1/platform-admin/me`

Returns the current platform admin identity:

```json
{
  "userId": 1,
  "email": "owner@example.com",
  "role": "SUPER_ADMIN"
}
```

### GET `/api/v1/platform-admin/ops/summary`

Returns cross-office counts for users, offices, Agents, commands, document
jobs, photo pickups, and deliveries.

### Read APIs

- `GET /api/v1/platform-admin/ops/users`
- `GET /api/v1/platform-admin/ops/offices`
- `GET /api/v1/platform-admin/ops/agents`
- `GET /api/v1/platform-admin/ops/agent-commands`
- `GET /api/v1/platform-admin/ops/document-jobs`
- `GET /api/v1/platform-admin/ops/photos`
- `GET /api/v1/platform-admin/ops/deliveries`
- `GET /api/v1/platform-admin/ops/events`
- `GET /api/v1/platform-admin/ops/ops-runs`
- `GET /api/v1/platform-admin/ops/incidents`
- `GET /api/v1/platform-admin/ops/findings`
- `POST /api/v1/platform-admin/ops/incidents/{incidentId}/diagnose`

Supported filters vary by resource, but all list APIs support `limit`. Most
workflow resources also support `officeId` and `status`.

### POST `/api/v1/platform-admin/ops/health/detect-stuck`

Submits the on-demand stuck-state detector flow. The HTTP request returns after
the platform ops run is created and submitted to the `platform-ops` Flower
worker. The detector flow records `operation_events` for old in-flight document
jobs, Agent commands, photo pickups, and document deliveries, and persists
platform ops run/incident/finding records.

```json
{
  "stuckDocumentJobs": 0,
  "stuckAgentCommands": 0,
  "stuckPhotoPickups": 0,
  "stuckDeliveries": 0,
  "detectedAt": "2026-05-25T12:30:00Z",
  "total": 0,
  "opsRunId": 3001,
  "incidentCount": 0,
  "findingCount": 0
}
```

The immediate response is an accepted workflow response. Read the run, incident,
and finding APIs to see completed detector output.

The detector creates platform ops workflow records:

- `platform_ops_runs`: one run for this detection request
- `platform_ops_incidents`: active incident records grouped by category and
  resource
- `platform_ops_findings`: detector findings linked to the run and incident

This is deterministic detection only. It does not call AI.

### POST `/api/v1/platform-admin/ops/incidents/{incidentId}/diagnose`

Submits a platform operations diagnosis flow for one incident.

It creates a `MANUAL_DIAGNOSIS` `platform_ops_runs` row, submits
`PlatformOpsDiagnosisFlow` to the `platform-ops` Flower worker, builds a
redacted diagnosis snapshot from the incident, recent findings, and related
operation events, and writes an `OPS_DIAGNOSIS_SNAPSHOT_READY` finding with
source `SYSTEM_DIAGNOSIS`.

If platform ops AI diagnosis is enabled and configured, the flow submits
`OpsDiagnosisHarness` as child work to the shared `ai-harness` Flower worker
lane and stores resulting findings with source `AI_HARNESS`. If AI diagnosis is
disabled or not configured, the same endpoint completes as deterministic-only.

Example response:

```json
{
  "id": 3002,
  "triggerType": "MANUAL_DIAGNOSIS",
  "status": "RUNNING",
  "startedByUserId": 1,
  "incidentId": 401,
  "inputSnapshotJson": {
    "state": "REQUESTED",
    "diagnosisType": "DETERMINISTIC_FIRST"
  },
  "aiHarnessRunId": null,
  "startedAt": "2026-05-25T12:35:00Z",
  "completedAt": null,
  "failureCode": null
}
```

Read `GET /api/v1/platform-admin/ops/ops-runs?triggerType=MANUAL_DIAGNOSIS`
and `GET /api/v1/platform-admin/ops/findings?incidentId={incidentId}` to see
the completed snapshot result.

### GET `/api/v1/platform-admin/ops/ops-runs`

Returns recent platform operations workflow runs.

Query parameters:

- `status`: `RUNNING`, `COMPLETED`, or `FAILED`
- `triggerType`: `MANUAL_DETECT_STUCK`, `MANUAL_DIAGNOSIS`,
  `DETECTOR_TRIGGERED`, or `FUTURE_MONITOR_FLOW`
- `limit`

Example response:

```json
[
  {
    "id": 3001,
    "triggerType": "MANUAL_DETECT_STUCK",
    "status": "COMPLETED",
    "startedByUserId": 1,
    "incidentId": 401,
    "inputSnapshotJson": {
      "findingCount": 4,
      "incidentCount": 4,
      "byCategory": {
        "DOCUMENT_JOB_STUCK": 1,
        "AGENT_COMMAND_STUCK": 2,
        "DOCUMENT_DELIVERY_STUCK": 1
      }
    },
    "aiHarnessRunId": null,
    "startedAt": "2026-05-25T12:30:00Z",
    "completedAt": "2026-05-25T12:30:01Z",
    "failureCode": null
  }
]
```

### GET `/api/v1/platform-admin/ops/incidents`

Returns recent operational incidents.

Query parameters:

- `officeId`
- `status`: `OPEN`, `ACKNOWLEDGED`, `RESOLVED`, or `IGNORED`
- `severity`: `INFO`, `WARN`, `ERROR`, or `CRITICAL`
- `category`
- `limit`

### GET `/api/v1/platform-admin/ops/findings`

Returns detector, system diagnosis, or future AI harness findings.

Query parameters:

- `officeId`
- `runId`
- `incidentId`
- `severity`: `INFO`, `WARN`, `ERROR`, or `CRITICAL`
- `source`: `DETECTOR`, `SYSTEM_DIAGNOSIS`, or `AI_HARNESS`
- `category`
- `limit`

Findings must expose redacted evidence only. Raw logs, prompts, signed URLs,
device secrets, API keys, and file contents must not be returned.

## Planned APIs

The following APIs are planned but not yet implemented. Add exact DTOs before
coding them:

- Template binary upload/download APIs
- Platform admin system-default configuration authoring APIs
- ArchDox Agent mTLS APIs
- Platform admin user/office/member management APIs
- Admin plan, usage, billing-state, and quota APIs
- Admin Cloud API instance health APIs
- Platform admin mutation APIs for user suspension, office repair, command
  retry/cancel, and support actions
- Ops Agent read-only report/log-manifest APIs
