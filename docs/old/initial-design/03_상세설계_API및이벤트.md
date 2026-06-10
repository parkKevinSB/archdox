# ArchDox 상세 설계 — API & Flower/Bloom 이벤트

## 1. API 설계 원칙

- **URL prefix로 테넌시 노출 안 함**: 일반 사용자 API는 `/api/v1/...` 단일 prefix. office는 JWT membership + `X-Office-Id` 헤더로 결정. (개인 사용자가 office를 의식하지 않아도 동작)
- **Admin은 `/admin/api/v1/...`**, 별도 인증·권한, 별도 도메인 호스팅 가능 (Admin SPA + Admin API)
- **ArchDox Agent ↔ Cloud는 `/agent/api/v1/...`**, mTLS + service token 이중 인증
- **Client ↔ ArchDox Agent 직접 업로드는 mTLS가 아니라 signed upload token**을 쓴다. 브라우저/PWA에 사무소별 client cert를 배포하지 않는다.
- **Internal (ArchDox Agent의 내부) `/internal/v1/...`**: localhost binding 또는 IP allowlist
- **AI Service `/ai/v1/...`**: ArchDox Agent 또는 Cloud API만 호출

## 2. Cloud API (사용자 대상)

### 2.1 인증
```http
POST /api/v1/auth/signup
POST /api/v1/auth/login
POST /api/v1/auth/refresh
POST /api/v1/auth/logout
POST /api/v1/auth/password/reset-request
POST /api/v1/auth/password/reset
GET  /api/v1/me
PATCH /api/v1/me
```

### 2.2 사무소 / 멤버십
```http
GET    /api/v1/offices                          # 내가 속한 사무소 목록
POST   /api/v1/offices                          # 사무소 생성 (Owner가 됨)
GET    /api/v1/offices/{officeId}
PATCH  /api/v1/offices/{officeId}
GET    /api/v1/offices/{officeId}/members
POST   /api/v1/offices/{officeId}/members       # 초대
PATCH  /api/v1/offices/{officeId}/members/{userId}
DELETE /api/v1/offices/{officeId}/members/{userId}
```

### 2.3 프로젝트
```http
GET    /api/v1/projects
POST   /api/v1/projects
GET    /api/v1/projects/{projectId}
PATCH  /api/v1/projects/{projectId}
POST   /api/v1/projects/{projectId}/archive
```

### 2.4 감리/점검 리포트
```http
GET   /api/v1/inspection-reports?projectId=&status=&page=
POST  /api/v1/inspection-reports
GET   /api/v1/inspection-reports/{reportId}
PUT   /api/v1/inspection-reports/{reportId}/steps/{stepCode}
POST  /api/v1/inspection-reports/{reportId}/checklist-items:batch
POST  /api/v1/inspection-reports/{reportId}/generate
POST  /api/v1/inspection-reports/{reportId}/deliver
GET   /api/v1/inspection-reports/{reportId}/deliveries
POST  /api/v1/inspection-reports/{reportId}/cancel
GET   /api/v1/inspection-reports/{reportId}/status        # 최신 상태 + job
GET   /api/v1/inspection-reports/{reportId}/timeline      # 이벤트 로그
```

### 2.5 사진
```http
POST  /api/v1/photos/intent           # 업로드 의향 (어디로 올릴지 결정)
                                      # 응답: {target: CLOUD_MEDIATED|ARCHDOX_AGENT_DIRECT|S3,
                                      #        uploads[], photoId}
POST  /api/v1/photos/{photoId}/confirm  # 업로드 완료 통보 (해시/사이즈 보고)
GET   /api/v1/photos/{photoId}/thumbnail-url   # presigned (만료 5분)
PATCH /api/v1/photos/{photoId}/label
DELETE /api/v1/photos/{photoId}
```

`/photos/intent`가 핵심이다. Cloud API가 이 호출에서:
1. office의 plan과 ArchDox Agent 가용성을 보고 업로드 대상을 결정
2. 사무소 플랜 기본값: `CLOUD_MEDIATED`를 반환한다. 클라이언트는 임시 S3에 업로드하고, Agent가 pull해서 NAS로 옮긴다.
3. `ARCHDOX_AGENT_DIRECT` 케이스: 터널/enrollment가 완료된 사무소에만 짧은 만료 signed upload token을 반환한다. 이 토큰은 특정 `photo_id`, MIME, byte range, 만료시각에 묶인다.
4. 개인 플랜 S3 케이스: S3 presigned POST 발급
5. 어느 쪽이든 `photo_id`를 사전 발급 → confirm으로 메타 채움

상세는 `04` 문서 §5.

### 2.6 실시간 상태
```http
GET   /api/v1/events/stream           # SSE, office scope
                                      # 이벤트: report.status, job.status,
                                      #         agent.status, notification
```

`SSE`로 시작. 확장 시 WebSocket으로 교체. (abyss-runner도 동일하게 SSE 또는 polling 중심)

### 2.7 알림
```http
GET   /api/v1/notifications?page=
POST  /api/v1/notifications/{id}/read
GET   /api/v1/notification-preferences
PATCH /api/v1/notification-preferences
```

### 2.8 라이선스 / 사용량
```http
GET   /api/v1/entitlements              # 우리 사무소가 쓸 수 있는 기능
GET   /api/v1/usage?period=YYYYMM
```

## 3. Cloud API — ArchDox Agent 연동 (Pull 방식)

VPN/고정IP/터널 없이 운영하기 위해 **Agent → Cloud outbound pull**을 기본으로 한다. (1차 결론 문서 §3.2 권장 2)

```http
POST  /agent/api/v1/pair                     # install token으로 첫 페어링,
                                              # 클라이언트 인증서 발급(CSR)
POST  /agent/api/v1/heartbeat                # status, version, disk, jobs
GET   /agent/api/v1/commands/next?max=10     # PENDING 명령 가져가기 (long-poll 25s)
POST  /agent/api/v1/commands/{cmdId}/ack
POST  /agent/api/v1/commands/{cmdId}/complete
POST  /agent/api/v1/commands/{cmdId}/fail
POST  /agent/api/v1/jobs/{jobId}/progress    # step 단위 진척
POST  /agent/api/v1/photos/{photoId}/meta    # NAS 저장 완료 후 메타 통보
POST  /agent/api/v1/photos/{photoId}/thumbnail   # 썸네일 업로드 (또는 presigned 사용)
POST  /agent/api/v1/deliveries/{deliveryId}/ack
POST  /agent/api/v1/deliveries/{deliveryId}/complete
POST  /agent/api/v1/deliveries/{deliveryId}/fail
```

### 3.1 long-poll 패턴
`commands/next`는 25초 long-poll. 명령이 없으면 204를, 있으면 200 + JSON 배열을 반환. Agent는 timeout 후 즉시 재요청. (Cloud는 ALB idle timeout보다 짧게)

### 3.2 인증
- 첫 페어링: `install_token` (사무소 admin이 발급, 24h 유효, 1회용)
- 페어링 응답으로 client cert 발급
- 이후 모든 `/agent/api/v1/*`는 **mTLS + service JWT** 이중 인증
- service JWT는 7일 rotation

## 4. ArchDox Agent Internal API

```http
GET   /internal/v1/health
GET   /internal/v1/status                       # 현재 진행 중인 job
POST  /internal/v1/reports/{reportId}/generate  # admin 강제 트리거
POST  /internal/v1/admin/reload-template
POST  /internal/v1/admin/purge-cache
POST  /internal/v1/admin/test-nas               # NAS 연결 테스트
POST  /internal/v1/admin/test-printer
```

binding은 `127.0.0.1:18080` 기본, 외부 노출 금지. Local Mgmt UI에서만 호출.

## 5. Admin API

```http
POST  /admin/api/v1/auth/login                  # 별도 admin 계정 풀
GET   /admin/api/v1/offices?status=&plan=
POST  /admin/api/v1/offices                     # 사무소 직접 생성
PATCH /admin/api/v1/offices/{officeId}/plan
POST  /admin/api/v1/offices/{officeId}/suspend
POST  /admin/api/v1/offices/{officeId}/install-tokens  # ArchDox Agent 페어링 토큰 발급
GET   /admin/api/v1/agents?status=
GET   /admin/api/v1/agents/{agentId}/heartbeats
POST  /admin/api/v1/agents/{agentId}/disable
GET   /admin/api/v1/usage?periodFrom=&periodTo=
GET   /admin/api/v1/audit-logs?actor=&action=&from=&to=
POST  /admin/api/v1/notifications/broadcast     # 점검 공지 등
```

상세는 `07` 문서.

## 6. AI API
```http
POST  /ai/v1/predict/photo-category    # multipart 또는 {imageUrl}
POST  /ai/v1/feedback/photo-label
GET   /ai/v1/models/current
```

## 7. Bloom 이벤트 (도메인 이벤트 표준)

abyss-runner의 `21-flower-bloom-guidelines.md`와 동일 규칙:
- **과거형/상태 진입형** 이름
- 핵심 상태 변경의 소유자는 단일 use case 또는 Flower step 내부
- 후속 처리·관측·확장에 사용. HTTP 즉시 응답이 필요한 명령은 직접 호출.

### 7.1 Cloud API에서 발행되는 이벤트
```text
# 계정 / 사무소
UserSignedUpEvent(userId, email)
OfficeCreatedEvent(officeId, type, plan)
OfficeMemberInvitedEvent(officeId, inviteeEmail, role)
OfficeMemberJoinedEvent(officeId, userId, role)
OfficeSuspendedEvent(officeId, reason)

# 리포트 / 문서
InspectionDraftCreatedEvent(reportId, officeId, projectId)
InspectionStepSavedEvent(reportId, stepCode, savedBy)
InspectionSubmittedEvent(reportId)
DocumentGenerationRequestedEvent(reportId, jobId, target)
DocumentGenerationStartedEvent(jobId)
DocumentGeneratedEvent(jobId, reportId, artifactIds)
DocumentGenerationFailedEvent(jobId, errorCode)
DocumentDeliveryRequestedEvent(deliveryId, jobId, channel)
DocumentDeliveryCompletedEvent(deliveryId, channel)
DocumentDeliveryFailedEvent(deliveryId, errorCode)

# 사진
PhotoUploadIntentCreatedEvent(photoId, target)
PhotoUploadedEvent(photoId, storageKind, bytes)
PhotoClassificationRequestedEvent(photoId)
PhotoClassifiedEvent(photoId, top3)
PhotoLabelConfirmedEvent(photoId, label, confirmedBy)

# Agent
AgentPairedEvent(agentId, officeId)
AgentHeartbeatReceivedEvent(agentId, snapshot)
AgentOfflineDetectedEvent(agentId, lastSeenAt)
AgentCommandQueuedEvent(commandId, agentId, type)

# 라이선스 / 알림
LicenseFeatureCheckedEvent(officeId, featureCode, allowed)
LicenseUsageExceededEvent(officeId, featureCode, used, limit)
NotificationDispatchedEvent(notificationId, channel, status)

# 감사
AuditLogRecordedEvent(...)
```

### 7.2 핸들러 배치
- `InspectionSubmittedEvent` → `DocumentGenerationOrchestrator`가 받아서 `document_jobs` row를 만들고 Flower flow submit
- `DocumentGenerationRequestedEvent` → ArchDox Agent 타깃이면 `ArchDoxAgentCommandPublisher`가 `archdox_agent_commands`에 INSERT (Agent의 long-poll이 가져감)
- `DocumentGeneratedEvent` → `NotificationDispatcher` + `UsageCounterIncrementer` + `AuditWriter`
- `DocumentDeliveryRequestedEvent` → `DocumentDeliveryDispatcher`가 `LOCAL_INBOX`/`EMAIL_LINK`/`LOCAL_PRINT` 정책에 따라 Cloud 발송 또는 ArchDox Agent command로 분기 (`12` 문서)
- `AgentHeartbeatReceivedEvent` → 상태 갱신 + offline 감지 cancel
- `AgentOfflineDetectedEvent`(스케줄러가 발행) → admin 알림 + 진행 중 job WAITING_AGENT 처리

### 7.3 ArchDox Agent의 Bloom 이벤트 (별도 JVM)
```text
LocalCommandReceivedEvent(commandId, type)
LocalDocumentRenderStartedEvent(jobId)
LocalDocumentRenderCompletedEvent(jobId, artifactPaths)
LocalDocumentRenderFailedEvent(jobId, cause)
AgentPhotoStoredEvent(photoId, localPath)
LocalNasUnreachableEvent(at)
LocalPrinterJobSubmittedEvent(jobId, printerName)
```

Cross-JVM은 `/agent/api/v1/*` REST로 매핑. Local의 `LocalDocumentRenderCompletedEvent` 핸들러가 Cloud에 `complete` 호출 → Cloud에서 `DocumentGeneratedEvent` 발행. abyss-runner는 단일 JVM이지만 ArchDox는 이 변환 layer가 추가된다.

## 8. Flower Workflow

abyss-runner의 `DeathResolveFlowFactory` 패턴을 그대로 따른다. `Flow.builder(FLOW_TYPE, key).step(...)`.

### 8.1 Cloud 측 — DocumentGenerationOrchestrationFlow
Cloud API에서 도는 flow. Agent에게 명령을 보내고 콜백을 기다린다.

```text
FLOW_TYPE = "cloud-doc-generation"
FLOW_KEY  = jobId

steps:
  1. validate-job
  2. resolve-target           # ARCHDOX_AGENT vs CLOUD_GENERATOR 결정
  3. ensure-agent-online      # ARCHDOX_AGENT일 때만, 60s timeout
  4. enqueue-agent-command    # archdox_agent_commands INSERT
                              # 또는 Cloud generator 호출
  5. wait-completion          # event-driven: Cloud generator는 자체 step done,
                              #               Agent는 /complete REST 콜백 → signal
  6. record-artifacts
  7. publish-generated        # DocumentGeneratedEvent
```

step 5는 **event-driven step**. abyss-runner README의 `WaitForPaymentStep` 패턴과 동일하게 `ctx.startTimeout(...)` + `ctx.subscribe(JobCompletedSignal.class, ...)` + `ctx.signal("done", evt)`.

실패 정책:
- step 3 timeout → `WAITING_AGENT` 상태 유지, retry queue
- step 4 실패 (DB 오류 등) → 즉시 FAILED, 사용자 안내
- step 5 timeout(예: 10분) → `RETRYING` 표시, 재시도 정책

### 8.2 Local 측 — DocumentRenderFlow
ArchDox Agent에서 도는 flow. 실제 DOCX/PDF 생성.

```text
FLOW_TYPE = "local-doc-render"
FLOW_KEY  = reportId

steps:
  1. fetch-payload            # 로컬 SQLite + Cloud에서 부족분 fetch
  2. prepare-workspace        # data/offices/{code}/reports/{id}/output/...
  3. resolve-photos           # photo_id → local NAS path 매핑
                              # 누락 사진이 있으면 fail-with-detail
  4. (optional) classify-photos
  5. render-docx              # docx4j 템플릿 바인딩
  6. convert-pdf              # LibreOffice headless
  7. write-artifacts          # 파일 저장 + sha256
  8. report-completion        # Cloud API /complete
  9. publish-rendered         # LocalDocumentRenderCompletedEvent
```

`DuplicatePolicy.IGNORE` (Cloud가 이미 같은 jobId로 보냈으면 무시), 또는 REPLACE 정책 적용 가능.

### 8.3 PhotoClassificationFlow (옵션, AI 도입 시)
```text
FLOW_TYPE = "photo-classification"
FLOW_KEY  = reportId

steps:
  1. load-photo-batch
  2. call-ai-service          # FastAPI
  3. save-predictions
  4. request-human-confirmation   # 신뢰도 낮으면 사용자 확인 요청 (알림)
  5. publish-classified
```

### 8.4 PhotoUploadMediationFlow (Cloud-mediated 업로드 시)
사무소 망 NAT 뒤라 직접 ArchDox Agent로 못 올리는 경우, Cloud가 임시 보관 후 Agent가 가져가는 경로.
```text
FLOW_TYPE = "photo-mediation"
FLOW_KEY  = photoId

steps:
  1. validate-upload          # mime/size/hash
  2. stash-temporary          # S3 temp bucket (1h lifecycle)
  3. notify-agent-pickup      # archdox_agent_commands에 PICKUP 명령
  4. wait-agent-pickup-ack
  5. wait-agent-stored-ack    # NAS 저장 완료까지
  6. purge-temporary
  7. publish-uploaded
```

→ 자세한 결정 트리는 `04` 문서.

### 8.5 AgentOfflineWatchdogFlow
스케줄러 트리거. agent별로 last_seen_at이 threshold 초과면 발행.
```text
FLOW_TYPE = "agent-offline-watch"
FLOW_KEY  = agentId

steps:
  1. fetch-heartbeat-window
  2. evaluate-thresholds
  3. transition-status        # ONLINE → DEGRADED → OFFLINE
  4. publish-status-change
  5. (optional) notify-admin
```

## 9. abyss-runner와 정확히 같게 가져가는 패턴

- `FlowerBloomConfiguration` (`@EnableBloom` + Bloom→Flower bridge) 그대로 복제
- `@Subscribe` 메소드는 application layer의 `*FlowListener`에 모음 (`HeroDefeatFlowListener` 참고)
- Flow factory는 `@Component`, step factory도 `@Component` 분리. Step 자체는 매번 new 인스턴스 (Flower README §"Treat a Step instance as owned by one Flow")
- Worker는 도메인별 또는 사용량별로 분리 (`doc-gen-worker`, `photo-worker`, `watchdog-worker`)
- `LoggingFlowerListener` + `MicrometerFlowerListener` 기본 등록
