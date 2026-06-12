import {
  Activity,
  AlertTriangle,
  ArrowLeft,
  Camera,
  CheckCircle2,
  ChevronDown,
  Clock3,
  Command,
  Copy,
  Download,
  FileText,
  Gauge,
  HardDrive,
  KeyRound,
  LayoutDashboard,
  Loader2,
  LogOut,
  Plus,
  RefreshCcw,
  Send,
  Server,
  ShieldCheck,
  Truck,
  Upload,
  UserPlus,
  Users,
  Wifi,
  XCircle
} from "lucide-react";
import { FormEvent, ReactNode, useEffect, useMemo, useRef, useState } from "react";
import {
  ApiError,
  acceptOfficeInvitation,
  addOfficeMember,
  applyPlatformLegalDigestAiDraft,
  approvePlatformLegalDigestAiDraft,
  archiveProject,
  approvePlatformWorkerApproval,
  autoGeneratePlatformConstructionSupervisionLegalBindings,
  cancelOfficeInvitation,
  clearPlatformAiObservations,
  configureTokenRefresh,
  createPlatformLegalDomainBinding,
  createPlatformAiUserBudgetOverride,
  createPlatformAiWorkerEvaluationRun,
  createPlatformAiWorkerRuntimeEvaluationRun,
  createPlatformAiWorkerRuntimeScenarioRun,
  createPlatformEngineApiKey,
  createPlatformAiPricingRule,
  createPlatformAiProvider,
  createDocumentTemplate,
  createDocumentTemplateRevision,
  createOfficeInvitation,
  createProject,
  deactivateOfficeMember,
  deactivatePlatformLegalDomainBinding,
  deleteProject,
  diagnosePlatformOpsIncident,
  detectPlatformStuckHealth,
  disablePlatformAiPricingRule,
  disablePlatformAiUserBudgetOverride,
  downloadDocumentTemplateRevisionContent,
  generatePlatformLegalDigestAiDraft,
  getAgentCommands,
  getAgentSessions,
  getAgents,
  getDocumentTemplateFields,
  getDocumentTemplateRevisions,
  getDocumentTemplates,
  getDocumentDeliveries,
  getDocumentJobs,
  getOfficeConfigOverrides,
  getOfficeInvitations,
  getOfficeMembers,
  getOperationEvents,
  getPhotos,
  getProjectAssignments,
  getProjects,
  getPlatformAdminMe,
  getPlatformAgents,
  getPlatformAiCallLogs,
  getPlatformAiBudgetUsageSummary,
  getPlatformAiHarnessPolicies,
  getPlatformAiHarnessTraces,
  getPlatformAiObservationMode,
  getPlatformAiObservations,
  getPlatformAiPreflightFindings,
  getPlatformAiProviders,
  getPlatformAiPricingRules,
  getPlatformAiUsageSummary,
  getPlatformAiUserBudgetOverrides,
  getPlatformAiWorkerEvaluationRuns,
  getPlatformAiWorkerEvaluationSummary,
  getPlatformCommands,
  getPlatformDeliveries,
  getPlatformDocumentJobs,
  getPlatformEvents,
  getPlatformEngineApiKeys,
  getPlatformEngineUsageEvents,
  getPlatformEngineUsageSummary,
  getPlatformFlowerRuntimeDump,
  getPlatformLegalChangeDigests,
  getPlatformLegalChangeSets,
  getPlatformLegalDomainBindingCoverage,
  getPlatformLegalDomainBindings,
  getPlatformLegalDigestAiDrafts,
  getPlatformLegalOpenApiStatus,
  getPlatformLegalSyncRuns,
  getPlatformWorkerApprovals,
  getPlatformWorkerGovernance,
  getPlatformOffices,
  getPlatformOfficeAiPolicies,
  getPlatformOpsFindings,
  getPlatformOpsIncidents,
  getPlatformOpsRuns,
  getPlatformPhotos,
  getPlatformSummary,
  getPlatformUsers,
  getSummary,
  login,
  me,
  publishDocumentTemplateRevision,
  publishPlatformAiProvider,
  refreshAuthToken,
  refreshPlatformLegalDeterministicDigests,
  removeProjectAssignment,
  rejectPlatformWorkerApproval,
  rejectPlatformLegalDigestAiDraft,
  revokePlatformEngineApiKey,
  searchPlatformLegalCorpus,
  signup,
  startPlatformLegalOpenDataSync,
  testPlatformAiProvider,
  updateProject,
  updatePlatformAiObservationMode,
  updatePlatformAiHarnessPolicy,
  updatePlatformAiProvider,
  updatePlatformLegalDomainBinding,
  updatePlatformOfficeAiPolicy,
  updateOfficeMemberRole,
  updateOfficeConfigOverride,
  upsertProjectAssignment,
  uploadDocumentTemplateRevisionContent
} from "./api";
import type {
  Agent,
  AgentCommand,
  AgentSession,
  AiBudgetUsageSummary,
  AiModelCallLog,
  AiHarnessPolicy,
  AiHarnessTraceEvent,
  AiModelPricingRule,
  AiObservation,
  AiObservationMode,
  AiProviderConnectionTestResult,
  AiProviderCredential,
  AiUsageSummary,
  AiUserBudgetOverride,
  AiWorkerEvaluationCase,
  AiWorkerEvaluationRun,
  AiWorkerEvaluationSummary,
  ConfigDefinition,
  CreateEngineApiKeyResponse,
  DocumentDelivery,
  DocumentJob,
  DocumentTemplateRevision,
  EngineApiKey,
  EngineApiUsageEvent,
  EngineApiUsageSummary,
  FlowerRuntimeDump,
  FlowerRuntimeExecutor,
  FlowerRuntimeFlow,
  FlowerRuntimeWorker,
  LegalArticleDiff,
  LegalChangeDigest,
  LegalChangeSet,
  LegalDomainBindingAutoGenerateResponse,
  LegalDomainBindingCoverage,
  LegalDomainBinding,
  LegalLawSearchResult,
  LegalDigestAiDraft,
  LegalOpenApiStatus,
  LegalSyncRun,
  MeResponse,
  MembershipRole,
  Office,
  OfficeInvitation,
  OfficeMember,
  OfficeConfigOverride,
  OfficeAiPolicy,
  OfficeOpsSummary,
  OperationEvent,
  PlatformAdminMe,
  PlatformAgentCommandOps,
  PlatformAgentOps,
  PlatformDeliveryOps,
  PlatformDocumentJobOps,
  PlatformHealthDetection,
  PlatformOfficeOps,
  PlatformOpsFinding,
  PlatformOpsIncident,
  PlatformOpsRun,
  PlatformOpsSummary,
  PlatformPhotoOps,
  PlatformReportPreflightFinding,
  PlatformUserOps,
  Photo,
  Project,
  ProjectAssignment,
  ProjectAssignmentRole,
  ProjectFormRequest,
  TemplateFieldCatalog,
  TemplateFieldDefinition,
  WorkerActionDefinition,
  WorkerApprovalRequest,
  WorkerGovernanceSummary
} from "./types";

type ViewKey =
  | "dashboard"
  | "agents"
  | "commands"
  | "documents"
  | "members"
  | "projects"
  | "templates"
  | "photos"
  | "deliveries"
  | "events"
  | "platform-overview"
  | "platform-incidents"
  | "platform-offices"
  | "platform-users"
  | "platform-agents"
  | "platform-commands"
  | "platform-document-jobs"
  | "platform-photo-delivery"
  | "platform-templates"
  | "platform-legal"
  | "platform-engine-keys"
  | "platform-worker-governance"
  | "platform-worker-approvals"
  | "platform-flower-runtime"
  | "platform-events"
  | "ai-overview"
  | "ai-providers"
  | "ai-harnesses"
  | "ai-evaluation"
  | "ai-budgets"
  | "ai-policies"
  | "ai-observer";

type OfficeViewKey = Extract<
  ViewKey,
  "dashboard" | "agents" | "commands" | "documents" | "members" | "projects" | "templates" | "photos" | "deliveries" | "events"
>;
type PlatformViewKey = Extract<
  ViewKey,
  | "platform-overview"
  | "platform-incidents"
  | "platform-offices"
  | "platform-users"
  | "platform-agents"
  | "platform-commands"
  | "platform-document-jobs"
  | "platform-photo-delivery"
  | "platform-templates"
  | "platform-legal"
  | "platform-engine-keys"
  | "platform-worker-governance"
  | "platform-worker-approvals"
  | "platform-flower-runtime"
  | "platform-events"
>;
type AiViewKey = Extract<ViewKey, "ai-overview" | "ai-providers" | "ai-harnesses" | "ai-evaluation" | "ai-budgets" | "ai-policies" | "ai-observer">;
type AiObserverTabKey = "summary" | "raw" | "findings" | "traces" | "calls";
type EngineUsageEventFilter = "ALL" | "ENGINE" | "MCP" | "LEGAL" | "FAILED";

type AdminState = {
  accessToken: string;
  refreshToken: string;
  user: MeResponse;
};

type OpsData = {
  summary: OfficeOpsSummary | null;
  agents: Agent[];
  sessions: AgentSession[];
  commands: AgentCommand[];
  documents: DocumentJob[];
  photos: Photo[];
  deliveries: DocumentDelivery[];
  events: OperationEvent[];
};

type PlatformOpsData = {
  summary: PlatformOpsSummary | null;
  users: PlatformUserOps[];
  offices: PlatformOfficeOps[];
  agents: PlatformAgentOps[];
  commands: PlatformAgentCommandOps[];
  documents: PlatformDocumentJobOps[];
  photos: PlatformPhotoOps[];
  deliveries: PlatformDeliveryOps[];
  events: OperationEvent[];
  opsRuns: PlatformOpsRun[];
  opsIncidents: PlatformOpsIncident[];
  opsFindings: PlatformOpsFinding[];
  legalSyncRuns: LegalSyncRun[];
  legalChangeSets: LegalChangeSet[];
  legalChangeDigests: LegalChangeDigest[];
  legalDomainBindings: LegalDomainBinding[];
  legalDomainBindingCoverage: LegalDomainBindingCoverage | null;
  legalOpenApiStatus: LegalOpenApiStatus | null;
  engineApiKeys: EngineApiKey[];
  engineApiUsageSummary: EngineApiUsageSummary | null;
  engineApiUsageEvents: EngineApiUsageEvent[];
  workerGovernance: WorkerGovernanceSummary | null;
  workerApprovals: WorkerApprovalRequest[];
  flowerRuntimeDump: FlowerRuntimeDump | null;
  aiProviders: AiProviderCredential[];
  aiHarnessPolicies: AiHarnessPolicy[];
  officeAiPolicies: OfficeAiPolicy[];
  aiCallLogs: AiModelCallLog[];
  aiHarnessTraces: AiHarnessTraceEvent[];
  aiObservationMode: AiObservationMode | null;
  aiObservations: AiObservation[];
  aiPreflightFindings: PlatformReportPreflightFinding[];
  aiPricingRules: AiModelPricingRule[];
  aiUsageSummary: AiUsageSummary | null;
  aiBudgetUsageSummary: AiBudgetUsageSummary | null;
  aiUserBudgetOverrides: AiUserBudgetOverride[];
  aiWorkerEvaluationSummary: AiWorkerEvaluationSummary | null;
  aiWorkerEvaluationRuns: AiWorkerEvaluationRun[];
};

type LegalDomainBindingPayload = {
  bindingScope: string;
  bindingKey?: string | null;
  actId: number;
  articleId?: number | null;
  reportType?: string | null;
  catalogCode?: string | null;
  catalogVersion?: number | null;
  checklistItemCode?: string | null;
  relevance: string;
  status?: string | null;
  effectiveFrom?: string | null;
  effectiveTo?: string | null;
  notes?: string | null;
  metadataJson?: Record<string, unknown>;
};

const AUTH_STORAGE_KEY = "archdox.admin.auth";
const OFFICE_STORAGE_KEY = "archdox.admin.officeId";

const emptyOpsData: OpsData = {
  summary: null,
  agents: [],
  sessions: [],
  commands: [],
  documents: [],
  photos: [],
  deliveries: [],
  events: []
};

const emptyPlatformOpsData: PlatformOpsData = {
  summary: null,
  users: [],
  offices: [],
  agents: [],
  commands: [],
  documents: [],
  photos: [],
  deliveries: [],
  events: [],
  opsRuns: [],
  opsIncidents: [],
  opsFindings: [],
  legalSyncRuns: [],
  legalChangeSets: [],
  legalChangeDigests: [],
  legalDomainBindings: [],
  legalDomainBindingCoverage: null,
  legalOpenApiStatus: null,
  engineApiKeys: [],
  engineApiUsageSummary: null,
  engineApiUsageEvents: [],
  workerGovernance: null,
  workerApprovals: [],
  flowerRuntimeDump: null,
  aiProviders: [],
  aiHarnessPolicies: [],
  officeAiPolicies: [],
  aiCallLogs: [],
  aiHarnessTraces: [],
  aiObservationMode: null,
  aiObservations: [],
  aiPreflightFindings: [],
  aiPricingRules: [],
  aiUsageSummary: null,
  aiBudgetUsageSummary: null,
  aiUserBudgetOverrides: [],
  aiWorkerEvaluationSummary: null,
  aiWorkerEvaluationRuns: []
};

const navItems: Array<{ key: OfficeViewKey; label: string; icon: typeof LayoutDashboard }> = [
  { key: "dashboard", label: "대시보드", icon: LayoutDashboard },
  { key: "agents", label: "에이전트", icon: Server },
  { key: "commands", label: "명령", icon: Command },
  { key: "documents", label: "문서 작업", icon: FileText },
  { key: "members", label: "멤버", icon: Users },
  { key: "projects", label: "프로젝트", icon: FileText },
  { key: "templates", label: "템플릿", icon: Upload },
  { key: "photos", label: "사진", icon: Camera },
  { key: "deliveries", label: "전달", icon: Truck },
  { key: "events", label: "이벤트", icon: Activity }
];

const platformNavItems: Array<{ key: PlatformViewKey; label: string }> = [
  { key: "platform-overview", label: "개요" },
  { key: "platform-offices", label: "사무소" },
  { key: "platform-users", label: "회원" },
  { key: "platform-agents", label: "Agent" },
  { key: "platform-commands", label: "Agent 명령" },
  { key: "platform-document-jobs", label: "문서 작업" },
  { key: "platform-photo-delivery", label: "사진/전달" },
  { key: "platform-incidents", label: "이슈/진단" },
  { key: "platform-events", label: "이벤트/로그" },
  { key: "platform-templates", label: "템플릿/문서설정" },
  { key: "platform-legal", label: "법령" },
  { key: "platform-engine-keys", label: "Engine API Key" },
  { key: "platform-worker-governance", label: "Worker Action Registry" },
  { key: "platform-worker-approvals", label: "Worker 승인" },
  { key: "platform-flower-runtime", label: "Flower Runtime" }
];

const aiNavItems: Array<{ key: AiViewKey; label: string }> = [
  { key: "ai-overview", label: "개요" },
  { key: "ai-providers", label: "AI 제공자" },
  { key: "ai-harnesses", label: "AI 하네스 관리" },
  { key: "ai-evaluation", label: "AI/Worker 평가" },
  { key: "ai-budgets", label: "AI 예산/사용량" },
  { key: "ai-policies", label: "사무소 AI 권한" },
  { key: "ai-observer", label: "AI 관측/검토" }
];

const aiObserverTabs: Array<{ key: AiObserverTabKey; label: string }> = [
  { key: "summary", label: "요약" },
  { key: "raw", label: "원문 관측" },
  { key: "findings", label: "검토 결과" },
  { key: "traces", label: "하네스 실행" },
  { key: "calls", label: "호출/비용" }
];

const platformViewKeys = new Set<ViewKey>(platformNavItems.map((item) => item.key));
const aiViewKeys = new Set<ViewKey>(aiNavItems.map((item) => item.key));

function isPlatformView(view: ViewKey): view is PlatformViewKey {
  return platformViewKeys.has(view);
}

function isAiView(view: ViewKey): view is AiViewKey {
  return aiViewKeys.has(view);
}

function isPlatformScopedView(view: ViewKey) {
  return isPlatformView(view) || isAiView(view);
}

const adminRoles = new Set(["OWNER", "ADMIN"]);
const personalTemplateNavItems = navItems.filter((item) => item.key === "templates");
const memberRoleOptions: MembershipRole[] = ["OWNER", "ADMIN", "MEMBER", "VIEWER"];
const projectAssignmentRoleOptions: ProjectAssignmentRole[] = ["MANAGER", "REPORT_WRITER", "VIEWER"];
const commandFilterOptions = ["ALL", "PENDING", "DELIVERED", "ACKED", "COMPLETED", "FAILED", "EXPIRED"];
const documentFilterOptions = ["ALL", "REQUESTED", "GENERATING", "GENERATED", "FAILED"];
const photoFilterOptions = ["ALL", "PENDING_UPLOAD", "UPLOADED"];
const pickupFilterOptions = ["ALL", "PENDING", "PICKED_UP", "FAILED", "NOT_REQUIRED"];
const deliveryFilterOptions = ["ALL", "REQUESTED", "SENDING", "COMPLETED", "FAILED"];
const aiProviderTypeOptions = ["OPENAI", "OLLAMA", "GEMINI", "ANTHROPIC", "CUSTOM_HTTP"];
const aiFindingResolutionOptions = ["ALL", "OPEN", "RESOLVED", "ACCEPTED"];
const aiFindingSeverityOptions = ["ALL", "CRITICAL", "HIGH", "MEDIUM", "LOW", "INFO"];

function isOfficeAdminOffice(office?: Office | null) {
  return Boolean(office && office.type !== "PERSONAL" && adminRoles.has(office.role));
}

function isPersonalTemplateOffice(office?: Office | null) {
  return Boolean(office && office.type === "PERSONAL" && office.role === "OWNER");
}

function isConsoleOffice(office?: Office | null) {
  return isOfficeAdminOffice(office) || isPersonalTemplateOffice(office);
}

function firstConsoleOffice(offices: Office[]) {
  return offices.find(isOfficeAdminOffice) ?? offices.find(isPersonalTemplateOffice) ?? null;
}

function navItemsForOffice(office?: Office | null) {
  if (isOfficeAdminOffice(office)) {
    return navItems;
  }
  if (isPersonalTemplateOffice(office)) {
    return personalTemplateNavItems;
  }
  return [];
}

const statusLabels: Record<string, string> = {
  ACKED: "수신 확인",
  ACCEPTED: "수용",
  ACTIVE: "활성",
  ADDED: "신설",
  ADMIN: "관리자",
  ALL: "전체",
  CANCELLED: "취소",
  COMPLETED: "완료",
  CRITICAL: "긴급",
  DELIVERED: "전달됨",
  DISABLED: "비활성",
  DRAFT: "초안",
  ERROR: "오류",
  EXPIRED: "만료",
  FAILED: "실패",
  GENERATED: "생성 완료",
  NEEDS_HUMAN_REVIEW: "검토 대기",
  APPROVED: "승인됨",
  REJECTED: "반려됨",
  APPLIED: "게시 반영됨",
  GENERATING: "생성 중",
  SUCCEEDED: "성공",
  HIGH: "높음",
  INFO: "정보",
  LEFT: "탈퇴",
  LOW: "낮음",
  MEDIUM: "보통",
  MEMBER: "멤버",
  MODIFIED: "수정",
  NEEDS_ATTENTION: "확인 필요",
  NOT_REQUIRED: "불필요",
  OFF: "꺼짐",
  OFFLINE: "오프라인",
  ON: "켜짐",
  ONLINE: "온라인",
  OPEN: "미처리",
  OWNER: "소유자",
  PASS: "통과",
  PASSED: "통과",
  PENDING: "대기",
  PENDING_UPLOAD: "업로드 대기",
  PICKED_UP: "회수 완료",
  PUBLISHED: "게시됨",
  REQUESTED: "요청됨",
  RESOLVED: "해결",
  REMOVED: "삭제",
  RUNNING: "실행 중",
  SENDING: "전송 중",
  STEP_SAVED: "작성 중",
  SUBMITTED: "요청 제출",
  SUSPENDED: "정지",
  UPLOADED: "업로드 완료",
  VIEWER: "조회자",
  WARN: "주의"
};

const codeLabels: Record<string, string> = {
  AGENT_COMMAND: "Agent 명령",
  AI: "AI 보강",
  AI_REVIEW: "AI 검토",
  LEGAL_DIGEST_ENRICHMENT: "법령 변경 게시글 AI 초안",
  PLATFORM_OPS_DIAGNOSIS: "플랫폼 운영 진단 AI",
  API_LOCAL: "API 로컬 저장소",
  ARCHDOX_AGENT: "ArchDox Agent",
  CLOUD_MANAGED: "클라우드 관리형",
  CLOUD_OFFICE: "클라우드 사무소",
  CUSTOM_HTTP: "사용자 HTTP",
  CONSTRUCTION_DAILY_SUPERVISION_LOG: "공사감리일지",
  CONSTRUCTION_SUPERVISION_REPORT: "감리보고서",
  CONSTRUCTION_SUPERVISION_LEGAL_CONTEXT: "공사감리 법령 컨텍스트",
  SOURCE_BACKED_SAMPLE: "제공된 법령 근거 샘플",
  DETERMINISTIC: "규칙 생성",
  DOCUMENT_GENERATION: "문서 생성 AI",
  DOCUMENT_REVIEW: "문서 검토 AI",
  DOCUMENT_DELIVERY_REQUEST: "문서 전달 요청",
  DOCUMENT_JOB: "문서 생성 작업",
  DOCUMENT_RENDER: "문서 생성",
  DOWNLOAD: "다운로드",
  IN_FLIGHT: "진행 중",
  NEEDS_HUMAN_REVIEW: "검토 대기",
  APPROVED: "승인됨",
  REJECTED: "반려됨",
  APPLIED: "게시 반영됨",
  LEGAL: "법령",
  LOCAL_OFFICE: "사무소 로컬",
  MANUAL_DIAGNOSIS: "수동 진단",
  PERSONAL: "개인",
  PHOTO_PICKUP: "사진 회수",
  PLATFORM: "플랫폼",
  PROXY_ONLY: "API 서버 경유",
  REPORT_PREFLIGHT_REVIEW: "생성 전 검토",
  OPS_AI_DIAGNOSIS: "운영 AI 진단",
  TEMPLATE: "템플릿",
  SYSTEM: "시스템",
  UI: "UI",
  WORKER_CHAT: "Worker Chat",
  WORKFLOW: "워크플로우"
};

export default function App() {
  const [auth, setAuth] = useState<AdminState | null>(null);
  const [selectedOfficeId, setSelectedOfficeId] = useState<number | null>(null);
  const [activeView, setActiveView] = useState<ViewKey>("dashboard");
  const [expandedNavGroups, setExpandedNavGroups] = useState({ platform: true, ai: true });
  const [opsData, setOpsData] = useState<OpsData>(emptyOpsData);
  const [platformAdmin, setPlatformAdmin] = useState<PlatformAdminMe | null>(null);
  const [platformChecked, setPlatformChecked] = useState(false);
  const [platformData, setPlatformData] = useState<PlatformOpsData>(emptyPlatformOpsData);
  const [lastPlatformDetection, setLastPlatformDetection] = useState<PlatformHealthDetection | null>(null);
  const [legalDigestAiDrafts, setLegalDigestAiDrafts] = useState<Record<number, LegalDigestAiDraft[]>>({});
  const [legalBindingAutoGenerateResult, setLegalBindingAutoGenerateResult] =
    useState<LegalDomainBindingAutoGenerateResponse | null>(null);
  const [aiProviderTestResults, setAiProviderTestResults] = useState<Record<number, AiProviderConnectionTestResult>>({});
  const [issuedEngineApiKey, setIssuedEngineApiKey] = useState<CreateEngineApiKeyResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [booting, setBooting] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [commandStatus, setCommandStatus] = useState("ALL");
  const [documentStatus, setDocumentStatus] = useState("ALL");
  const [photoStatus, setPhotoStatus] = useState("ALL");
  const [pickupStatus, setPickupStatus] = useState("ALL");
  const [deliveryStatus, setDeliveryStatus] = useState("ALL");
  const [pendingInvitationToken, setPendingInvitationToken] = useState(() => invitationTokenFromPath());
  const authRef = useRef<AdminState | null>(null);
  const refreshInFlightRef = useRef<Promise<string | null> | null>(null);

  const adminOffices = useMemo(
    () => auth?.user.offices.filter(isOfficeAdminOffice) ?? [],
    [auth]
  );

  const personalTemplateOffices = useMemo(
    () => auth?.user.offices.filter(isPersonalTemplateOffice) ?? [],
    [auth]
  );

  const visiblePersonalTemplateOffices = useMemo(
    () => platformAdmin ? [] : personalTemplateOffices,
    [personalTemplateOffices, platformAdmin]
  );

  const consoleOffices = useMemo(
    () => [...adminOffices, ...visiblePersonalTemplateOffices],
    [adminOffices, visiblePersonalTemplateOffices]
  );

  const selectedOffice = useMemo(
    () => consoleOffices.find((office) => office.id === selectedOfficeId) ?? null,
    [consoleOffices, selectedOfficeId]
  );

  const officeNavItems = useMemo(
    () => navItemsForOffice(selectedOffice),
    [selectedOffice]
  );
  const platformOnlyAdmin = Boolean(platformAdmin) && adminOffices.length === 0;
  const visibleOfficeNavItems = officeNavItems;

  function togglePlatformGroup() {
    setExpandedNavGroups((current) => ({ ...current, platform: !current.platform }));
    if (!isPlatformView(activeView)) {
      setActiveView("platform-overview");
    }
  }

  function toggleAiGroup() {
    setExpandedNavGroups((current) => ({ ...current, ai: !current.ai }));
    if (!isAiView(activeView)) {
      setActiveView("ai-overview");
    }
  }

  useEffect(() => {
    authRef.current = auth;
  }, [auth]);

  useEffect(() => {
    configureTokenRefresh(async () => {
      const current = authRef.current;
      if (!current) {
        return null;
      }
      if (refreshInFlightRef.current) {
        return refreshInFlightRef.current;
      }
      refreshInFlightRef.current = refreshAuthToken(current.refreshToken)
        .then((token) => {
          const nextAuth: AdminState = {
            ...current,
            accessToken: token.accessToken,
            refreshToken: token.refreshToken,
            user: authRef.current?.user ?? current.user
          };
          authRef.current = nextAuth;
          setAuth(nextAuth);
          window.localStorage.setItem(
            AUTH_STORAGE_KEY,
            JSON.stringify({ accessToken: nextAuth.accessToken, refreshToken: nextAuth.refreshToken })
          );
          return nextAuth.accessToken;
        })
        .catch(() => {
          window.localStorage.removeItem(AUTH_STORAGE_KEY);
          setAuth(null);
          setPlatformAdmin(null);
          return null;
        })
        .finally(() => {
          refreshInFlightRef.current = null;
        });
      return refreshInFlightRef.current;
    });
    return () => configureTokenRefresh(null);
  }, []);

  useEffect(() => {
    if (!auth) {
      setPlatformAdmin(null);
      setPlatformChecked(true);
      return;
    }
    setPlatformChecked(false);
    getPlatformAdminMe(auth.accessToken)
      .then((admin) => {
        setPlatformAdmin(admin);
        setExpandedNavGroups({ platform: true, ai: true });
        if (adminOffices.length === 0) {
          setActiveView("platform-overview");
        }
      })
      .catch(() => setPlatformAdmin(null))
      .finally(() => setPlatformChecked(true));
  }, [auth?.accessToken, adminOffices.length]);

  useEffect(() => {
    const raw = window.localStorage.getItem(AUTH_STORAGE_KEY);
    if (!raw) {
      setBooting(false);
      return;
    }

    const stored = JSON.parse(raw) as Pick<AdminState, "accessToken" | "refreshToken">;
    me(stored.accessToken)
      .then((user) => {
        const savedOfficeId = Number(window.localStorage.getItem(OFFICE_STORAGE_KEY));
        const savedOffice = user.offices.find((office) => office.id === savedOfficeId && isConsoleOffice(office));
        const firstOffice = firstConsoleOffice(user.offices);
        setAuth({ ...stored, user });
        setSelectedOfficeId(savedOffice?.id ?? firstOffice?.id ?? null);
        if ((savedOffice ?? firstOffice) && !isOfficeAdminOffice(savedOffice ?? firstOffice)) {
          setActiveView("templates");
        }
      })
      .catch(() => {
        window.localStorage.removeItem(AUTH_STORAGE_KEY);
        setAuth(null);
      })
      .finally(() => setBooting(false));
  }, []);

  useEffect(() => {
    if (!auth) {
      return;
    }
    if (consoleOffices.length === 0) {
      setSelectedOfficeId(null);
      return;
    }
    if (!selectedOfficeId || !consoleOffices.some((office) => office.id === selectedOfficeId)) {
      setSelectedOfficeId(consoleOffices[0].id);
    }
  }, [auth, consoleOffices, selectedOfficeId]);

  useEffect(() => {
    if (!auth || !platformChecked) {
      return;
    }
    if (platformOnlyAdmin && !selectedOffice && !isPlatformScopedView(activeView)) {
      setActiveView("platform-overview");
      return;
    }
    if (isPlatformScopedView(activeView)) {
      if (!platformAdmin && officeNavItems[0]) {
        setActiveView(officeNavItems[0].key);
      }
      return;
    }
    if (!selectedOffice) {
      if (platformAdmin) {
        setActiveView("platform-overview");
      }
      return;
    }
    if (!officeNavItems.some((item) => item.key === activeView)) {
      setActiveView(officeNavItems[0]?.key ?? "templates");
    }
  }, [activeView, auth, officeNavItems, platformAdmin, platformChecked, platformOnlyAdmin, selectedOffice]);

  useEffect(() => {
    if (!auth || !selectedOfficeId) {
      return;
    }
    window.localStorage.setItem(OFFICE_STORAGE_KEY, String(selectedOfficeId));
    refresh();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [auth?.accessToken, selectedOfficeId]);

  useEffect(() => {
    if (isPlatformScopedView(activeView)) {
      refreshPlatform();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeView, platformAdmin?.role]);

  async function refresh() {
    if (!auth || !selectedOfficeId) {
      return;
    }
    if (!isOfficeAdminOffice(selectedOffice)) {
      setOpsData(emptyOpsData);
      setError(null);
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const [summary, agents, sessions, commands, documents, photos, deliveries, events] = await Promise.all([
        getSummary(auth.accessToken, selectedOfficeId),
        getAgents(auth.accessToken, selectedOfficeId, 100),
        getAgentSessions(auth.accessToken, selectedOfficeId, 100),
        getAgentCommands(auth.accessToken, selectedOfficeId, 100),
        getDocumentJobs(auth.accessToken, selectedOfficeId, 100),
        getPhotos(auth.accessToken, selectedOfficeId, 100),
        getDocumentDeliveries(auth.accessToken, selectedOfficeId, 100),
        getOperationEvents(auth.accessToken, selectedOfficeId, 100)
      ]);
      setOpsData({ summary, agents, sessions, commands, documents, photos, deliveries, events });
    } catch (err) {
      setError(err instanceof Error ? err.message : "운영 데이터를 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }

  async function refreshPlatform(view: ViewKey = activeView) {
    if (!auth || !platformAdmin) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const token = auth.accessToken;
      const next: Partial<PlatformOpsData> = {};

      if (view === "platform-overview") {
        const [summary, opsIncidents, opsRuns, opsFindings] = await Promise.all([
          getPlatformSummary(token),
          getPlatformOpsIncidents(token, 50),
          getPlatformOpsRuns(token, 50),
          getPlatformOpsFindings(token, 50)
        ]);
        Object.assign(next, { summary, opsIncidents, opsRuns, opsFindings });
      } else if (view === "platform-offices") {
        next.offices = await getPlatformOffices(token, 100);
      } else if (view === "platform-users") {
        next.users = await getPlatformUsers(token, 100);
      } else if (view === "platform-agents") {
        next.agents = await getPlatformAgents(token, 100);
      } else if (view === "platform-commands") {
        next.commands = await getPlatformCommands(token, 100);
      } else if (view === "platform-document-jobs") {
        next.documents = await getPlatformDocumentJobs(token, 100);
      } else if (view === "platform-photo-delivery") {
        const [photos, deliveries] = await Promise.all([
          getPlatformPhotos(token, 100),
          getPlatformDeliveries(token, 100)
        ]);
        Object.assign(next, { photos, deliveries });
      } else if (view === "platform-incidents") {
        const [summary, opsRuns, opsIncidents, opsFindings] = await Promise.all([
          getPlatformSummary(token),
          getPlatformOpsRuns(token, 50),
          getPlatformOpsIncidents(token, 50),
          getPlatformOpsFindings(token, 50)
        ]);
        Object.assign(next, { summary, opsRuns, opsIncidents, opsFindings });
      } else if (view === "platform-events") {
        const [summary, events] = await Promise.all([
          getPlatformSummary(token),
          getPlatformEvents(token, 100)
        ]);
        Object.assign(next, { summary, events });
      } else if (view === "platform-templates") {
        const [summary, offices] = await Promise.all([
          getPlatformSummary(token),
          getPlatformOffices(token, 100)
        ]);
        Object.assign(next, { summary, offices });
      } else if (view === "platform-legal") {
        const [
          legalOpenApiStatus,
          legalSyncRuns,
          legalChangeSets,
          legalChangeDigests,
          legalDomainBindings,
          legalDomainBindingCoverage
        ] = await Promise.all([
          getPlatformLegalOpenApiStatus(token),
          getPlatformLegalSyncRuns(token, 50),
          getPlatformLegalChangeSets(token, 50),
          getPlatformLegalChangeDigests(token, 50),
          getPlatformLegalDomainBindings(token, 500),
          getPlatformLegalDomainBindingCoverage(token)
        ]);
        Object.assign(next, {
          legalOpenApiStatus,
          legalSyncRuns,
          legalChangeSets,
          legalChangeDigests,
          legalDomainBindings,
          legalDomainBindingCoverage
        });
      } else if (view === "platform-engine-keys") {
        const [engineApiKeys, engineApiUsageSummary, engineApiUsageEvents, offices, users] = await Promise.all([
          getPlatformEngineApiKeys(token),
          getPlatformEngineUsageSummary(token),
          getPlatformEngineUsageEvents(token, 100),
          getPlatformOffices(token, 100),
          getPlatformUsers(token, 100)
        ]);
        Object.assign(next, { engineApiKeys, engineApiUsageSummary, engineApiUsageEvents, offices, users });
      } else if (view === "platform-worker-governance") {
        next.workerGovernance = await getPlatformWorkerGovernance(token, 7, 30);
      } else if (view === "platform-worker-approvals") {
        next.workerApprovals = await getPlatformWorkerApprovals(token, 50);
      } else if (view === "platform-flower-runtime") {
        next.flowerRuntimeDump = await getPlatformFlowerRuntimeDump(token);
      } else if (view === "ai-overview") {
        const [
          aiProviders,
          aiHarnessPolicies,
          officeAiPolicies,
          aiUsageSummary,
          aiCallLogs,
          aiPreflightFindings
        ] = await Promise.all([
          getPlatformAiProviders(token),
          getPlatformAiHarnessPolicies(token),
          getPlatformOfficeAiPolicies(token, 100),
          getPlatformAiUsageSummary(token),
          getPlatformAiCallLogs(token, 100),
          getPlatformAiPreflightFindings(token, 100)
        ]);
        Object.assign(next, {
          aiProviders,
          aiHarnessPolicies,
          officeAiPolicies,
          aiUsageSummary,
          aiCallLogs,
          aiPreflightFindings
        });
      } else if (view === "ai-providers") {
        const [aiProviders, aiPricingRules, aiUsageSummary] = await Promise.all([
          getPlatformAiProviders(token),
          getPlatformAiPricingRules(token, 100),
          getPlatformAiUsageSummary(token)
        ]);
        Object.assign(next, { aiProviders, aiPricingRules, aiUsageSummary });
      } else if (view === "ai-harnesses") {
        const [aiProviders, aiHarnessPolicies] = await Promise.all([
          getPlatformAiProviders(token),
          getPlatformAiHarnessPolicies(token)
        ]);
        Object.assign(next, { aiProviders, aiHarnessPolicies });
      } else if (view === "ai-evaluation") {
        const [aiWorkerEvaluationSummary, aiWorkerEvaluationRuns] = await Promise.all([
          getPlatformAiWorkerEvaluationSummary(token),
          getPlatformAiWorkerEvaluationRuns(token, 30)
        ]);
        Object.assign(next, { aiWorkerEvaluationSummary, aiWorkerEvaluationRuns });
      } else if (view === "ai-budgets") {
        const [aiBudgetUsageSummary, aiPricingRules, aiUserBudgetOverrides, users] = await Promise.all([
          getPlatformAiBudgetUsageSummary(token),
          getPlatformAiPricingRules(token, 100),
          getPlatformAiUserBudgetOverrides(token, 100),
          getPlatformUsers(token, 100)
        ]);
        Object.assign(next, { aiBudgetUsageSummary, aiPricingRules, aiUserBudgetOverrides, users });
      } else if (view === "ai-policies") {
        const [aiProviders, officeAiPolicies] = await Promise.all([
          getPlatformAiProviders(token),
          getPlatformOfficeAiPolicies(token, 100)
        ]);
        Object.assign(next, { aiProviders, officeAiPolicies });
      } else if (view === "ai-observer") {
        const [aiObservationMode, aiObservations, aiHarnessTraces, aiCallLogs, aiPreflightFindings] = await Promise.all([
          getPlatformAiObservationMode(token),
          getPlatformAiObservations(token, 50),
          getPlatformAiHarnessTraces(token, 100),
          getPlatformAiCallLogs(token, 100),
          getPlatformAiPreflightFindings(token, 100)
        ]);
        Object.assign(next, { aiObservationMode, aiObservations, aiHarnessTraces, aiCallLogs, aiPreflightFindings });
      } else {
        next.summary = await getPlatformSummary(token);
      }

      setPlatformData((current) => ({ ...current, ...next }));
    } catch (err) {
      setError(err instanceof Error ? err.message : "플랫폼 데이터를 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }

  async function refreshPlatformFlowerRuntime(silent = false) {
    if (!auth || !platformAdmin) {
      return;
    }
    if (!silent) {
      setLoading(true);
      setError(null);
    }
    try {
      const flowerRuntimeDump = await getPlatformFlowerRuntimeDump(auth.accessToken);
      setPlatformData((current) => ({ ...current, flowerRuntimeDump }));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Flower Runtime 상태를 불러오지 못했습니다.");
    } finally {
      if (!silent) {
        setLoading(false);
      }
    }
  }

  async function runPlatformDetection() {
    if (!auth || !platformAdmin) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const result = await detectPlatformStuckHealth(auth.accessToken);
      setLastPlatformDetection(result);
      await refreshPlatform();
    } catch (err) {
      setError(err instanceof Error ? err.message : "플랫폼 상태 감지에 실패했습니다.");
    } finally {
      setLoading(false);
    }
  }

  async function runPlatformLegalOpenDataSync() {
    if (!auth || !platformAdmin) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      await startPlatformLegalOpenDataSync(auth.accessToken);
      await refreshPlatform();
    } catch (err) {
      setError(err instanceof Error ? err.message : "실제 법령 동기화를 시작하지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }

  async function runPlatformLegalDigestRefresh() {
    if (!auth || !platformAdmin) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      await refreshPlatformLegalDeterministicDigests(auth.accessToken);
      await refreshPlatform();
    } catch (err) {
      setError(err instanceof Error ? err.message : "법령 변경사항 요약을 재생성하지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }

  async function createLegalDomainBindingFromUi(body: LegalDomainBindingPayload) {
    if (!auth || !platformAdmin) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      await createPlatformLegalDomainBinding(auth.accessToken, body);
      const [legalDomainBindings, legalDomainBindingCoverage] = await Promise.all([
        getPlatformLegalDomainBindings(auth.accessToken, 500),
        getPlatformLegalDomainBindingCoverage(auth.accessToken)
      ]);
      setPlatformData((current) => ({ ...current, legalDomainBindings, legalDomainBindingCoverage }));
    } catch (err) {
      setError(err instanceof Error ? err.message : "법령 도메인 바인딩을 저장하지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }

  async function updateLegalDomainBindingFromUi(bindingId: number, body: LegalDomainBindingPayload) {
    if (!auth || !platformAdmin) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      await updatePlatformLegalDomainBinding(auth.accessToken, bindingId, body);
      const [legalDomainBindings, legalDomainBindingCoverage] = await Promise.all([
        getPlatformLegalDomainBindings(auth.accessToken, 500),
        getPlatformLegalDomainBindingCoverage(auth.accessToken)
      ]);
      setPlatformData((current) => ({ ...current, legalDomainBindings, legalDomainBindingCoverage }));
    } catch (err) {
      setError(err instanceof Error ? err.message : "법령 도메인 바인딩을 수정하지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }

  async function autoGenerateConstructionSupervisionLegalBindingsFromUi() {
    if (!auth || !platformAdmin) {
      return;
    }
    if (!window.confirm("공사감리 카탈로그 전체에 기본 법령 바인딩을 자동 생성할까요? 기존 바인딩은 유지하고 없는 항목만 추가합니다.")) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const result = await autoGeneratePlatformConstructionSupervisionLegalBindings(auth.accessToken);
      const [legalDomainBindings, legalDomainBindingCoverage] = await Promise.all([
        getPlatformLegalDomainBindings(auth.accessToken, 500),
        getPlatformLegalDomainBindingCoverage(auth.accessToken)
      ]);
      setPlatformData((current) => ({ ...current, legalDomainBindings, legalDomainBindingCoverage }));
      setLegalBindingAutoGenerateResult(result);
    } catch (err) {
      setError(err instanceof Error ? err.message : "공사감리 기본 법령 바인딩을 자동 생성하지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }

  async function deactivateLegalDomainBindingFromUi(bindingId: number) {
    if (!auth || !platformAdmin) {
      return;
    }
    if (!window.confirm("이 법령 도메인 바인딩을 비활성화할까요? Engine 검토에서 더 이상 우선 근거로 사용하지 않습니다.")) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      await deactivatePlatformLegalDomainBinding(auth.accessToken, bindingId);
      const [legalDomainBindings, legalDomainBindingCoverage] = await Promise.all([
        getPlatformLegalDomainBindings(auth.accessToken, 500),
        getPlatformLegalDomainBindingCoverage(auth.accessToken)
      ]);
      setPlatformData((current) => ({ ...current, legalDomainBindings, legalDomainBindingCoverage }));
    } catch (err) {
      setError(err instanceof Error ? err.message : "법령 도메인 바인딩을 비활성화하지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }

  async function runPlatformLegalDigestAiDraft(digestId: number) {
    if (!auth || !platformAdmin) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const draft = await generatePlatformLegalDigestAiDraft(auth.accessToken, digestId);
      setLegalDigestAiDrafts((current) => ({ ...current, [digestId]: [draft, ...(current[digestId] ?? [])] }));
    } catch (err) {
      setError(err instanceof Error ? err.message : "법령 변경사항 AI 초안을 생성하지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }

  async function loadPlatformLegalDigestAiDrafts(digestId: number) {
    if (!auth || !platformAdmin) {
      return;
    }
    setError(null);
    try {
      const drafts = await getPlatformLegalDigestAiDrafts(auth.accessToken, digestId);
      setLegalDigestAiDrafts((current) => ({ ...current, [digestId]: drafts }));
    } catch (err) {
      setError(err instanceof Error ? err.message : "법령 변경사항 AI 초안 이력을 불러오지 못했습니다.");
    }
  }

  async function applyPlatformLegalDigestAiDraftFromUi(digestId: number, draftId: number) {
    if (!auth || !platformAdmin) {
      return;
    }
    if (!window.confirm("AI 초안을 사용자용 게시 요약에 적용할까요? 법령 원문과 corpus는 수정하지 않습니다.")) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const applied = await applyPlatformLegalDigestAiDraft(auth.accessToken, digestId, draftId);
      setLegalDigestAiDrafts((current) => ({
        ...current,
        [digestId]: (current[digestId] ?? []).map((draft) => (draft.id === draftId ? applied : draft))
      }));
      const legalChangeDigests = await getPlatformLegalChangeDigests(auth.accessToken, 50);
      setPlatformData((current) => ({ ...current, legalChangeDigests }));
    } catch (err) {
      setError(err instanceof Error ? err.message : "AI 초안을 게시 요약에 적용하지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }

  async function approvePlatformLegalDigestAiDraftFromUi(digestId: number, draftId: number) {
    if (!auth || !platformAdmin) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const approved = await approvePlatformLegalDigestAiDraft(auth.accessToken, digestId, draftId);
      setLegalDigestAiDrafts((current) => ({
        ...current,
        [digestId]: (current[digestId] ?? []).map((draft) => (draft.id === draftId ? approved : draft))
      }));
    } catch (err) {
      setError(err instanceof Error ? err.message : "AI 초안을 승인하지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }

  async function rejectPlatformLegalDigestAiDraftFromUi(digestId: number, draftId: number) {
    if (!auth || !platformAdmin) {
      return;
    }
    if (!window.confirm("AI 초안을 반려할까요? 반려된 초안은 게시 요약에 적용할 수 없습니다.")) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const rejected = await rejectPlatformLegalDigestAiDraft(auth.accessToken, digestId, draftId);
      setLegalDigestAiDrafts((current) => ({
        ...current,
        [digestId]: (current[digestId] ?? []).map((draft) => (draft.id === draftId ? rejected : draft))
      }));
    } catch (err) {
      setError(err instanceof Error ? err.message : "AI 초안을 반려하지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }

  async function handleCreateEngineApiKey(body: {
    displayName: string;
    ownerUserId: number;
    officeId?: number | null;
    scopes: string[];
    dailyRequestUnitLimit?: number | null;
    expiresAt?: string | null;
  }) {
    if (!auth || !platformAdmin) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const created = await createPlatformEngineApiKey(auth.accessToken, body);
      setIssuedEngineApiKey(created);
      await refreshPlatform();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Engine API Key를 발급하지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }

  async function handleRevokeEngineApiKey(apiKeyId: number) {
    if (!auth || !platformAdmin) {
      return;
    }
    if (!window.confirm("이 Engine API Key를 폐기할까요? 폐기 후에는 외부 Engine API 호출에 사용할 수 없습니다.")) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      await revokePlatformEngineApiKey(auth.accessToken, apiKeyId);
      if (issuedEngineApiKey?.key.id === apiKeyId) {
        setIssuedEngineApiKey(null);
      }
      await refreshPlatform();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Engine API Key를 폐기하지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }

  async function handleApproveWorkerApproval(approvalRequestId: number) {
    if (!auth || !platformAdmin) {
      return;
    }
    if (!window.confirm("이 Worker action을 승인하고 실행할까요?")) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      await approvePlatformWorkerApproval(auth.accessToken, approvalRequestId, "Approved from platform admin UI.");
      await refreshPlatform();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Worker 승인 요청을 승인하지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }

  async function handleRejectWorkerApproval(approvalRequestId: number) {
    if (!auth || !platformAdmin) {
      return;
    }
    if (!window.confirm("이 Worker action을 반려할까요?")) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      await rejectPlatformWorkerApproval(auth.accessToken, approvalRequestId, "Rejected from platform admin UI.");
      await refreshPlatform();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Worker 승인 요청을 반려하지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }

  async function runPlatformIncidentDiagnosis(incidentId: number) {
    if (!auth || !platformAdmin) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      await diagnosePlatformOpsIncident(auth.accessToken, incidentId);
      await refreshPlatform();
    } catch (err) {
      setError(err instanceof Error ? err.message : "운영 진단 Flow를 시작하지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }

  async function handleCreateAiProvider(body: {
    providerCode: string;
    displayName: string;
    providerType: string;
    baseUrl?: string | null;
    defaultModel?: string | null;
    apiKey?: string | null;
  }) {
    if (!auth || !platformAdmin) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      await createPlatformAiProvider(auth.accessToken, body);
      await refreshPlatform();
    } catch (err) {
      setError(err instanceof Error ? err.message : "AI 제공자를 저장하지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }

  async function handleUpdateAiProvider(
    providerId: number,
    body: {
      displayName: string;
      providerType: string;
      baseUrl?: string | null;
      defaultModel?: string | null;
      apiKey?: string | null;
    }
  ) {
    if (!auth || !platformAdmin) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      await updatePlatformAiProvider(auth.accessToken, providerId, body);
      setAiProviderTestResults((current) => {
        const next = { ...current };
        delete next[providerId];
        return next;
      });
      await refreshPlatform();
    } catch (err) {
      setError(err instanceof Error ? err.message : "AI Provider를 수정하지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }

  async function handlePublishAiProvider(providerId: number) {
    if (!auth || !platformAdmin) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      await publishPlatformAiProvider(auth.accessToken, providerId);
      await refreshPlatform();
    } catch (err) {
      setError(err instanceof Error ? err.message : "AI 제공자를 게시하지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }

  async function handleTestAiProvider(providerId: number) {
    if (!auth || !platformAdmin) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const result = await testPlatformAiProvider(auth.accessToken, providerId);
      setAiProviderTestResults((current) => ({ ...current, [providerId]: result }));
      await refreshPlatform();
    } catch (err) {
      setError(err instanceof Error ? err.message : "AI provider 연결 테스트를 실행하지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }

  async function handleCreateAiWorkerEvaluationRun(): Promise<AiWorkerEvaluationRun | null> {
    if (!auth || !platformAdmin) {
      return null;
    }
    setLoading(true);
    setError(null);
    try {
      const run = await createPlatformAiWorkerEvaluationRun(auth.accessToken);
      setPlatformData((current) => ({
        ...current,
        aiWorkerEvaluationRuns: [
          run,
          ...current.aiWorkerEvaluationRuns.filter((item) => item.id !== run.id)
        ].slice(0, 30)
      }));
      return run;
    } catch (err) {
      setError(err instanceof Error ? err.message : "AI/Worker 평가 기록을 생성하지 못했습니다.");
      return null;
    } finally {
      setLoading(false);
    }
  }

  async function handleCreateAiWorkerRuntimeEvaluationRun(): Promise<AiWorkerEvaluationRun | null> {
    if (!auth || !platformAdmin) {
      return null;
    }
    if (!window.confirm("런타임 연결 평가는 실제 AI provider에 짧은 연결 테스트를 보낼 수 있습니다. 진행할까요?")) {
      return null;
    }
    setLoading(true);
    setError(null);
    try {
      const run = await createPlatformAiWorkerRuntimeEvaluationRun(auth.accessToken);
      setPlatformData((current) => ({
        ...current,
        aiWorkerEvaluationRuns: [
          run,
          ...current.aiWorkerEvaluationRuns.filter((item) => item.id !== run.id)
        ].slice(0, 30)
      }));
      return run;
    } catch (err) {
      setError(err instanceof Error ? err.message : "AI/Worker 런타임 평가를 실행하지 못했습니다.");
      return null;
    } finally {
      setLoading(false);
    }
  }

  async function handleCreateAiWorkerRuntimeScenarioRun(): Promise<AiWorkerEvaluationRun | null> {
    if (!auth || !platformAdmin) {
      return null;
    }
    if (!window.confirm("시나리오 평가는 Legal Digest Worker dry-run과 문서 법령검토 하네스 평가를 실행합니다. 실제 provider가 배정되어 있으면 외부 모델 호출이 발생할 수 있습니다. 진행할까요?")) {
      return null;
    }
    setLoading(true);
    setError(null);
    try {
      const run = await createPlatformAiWorkerRuntimeScenarioRun(auth.accessToken);
      setPlatformData((current) => ({
        ...current,
        aiWorkerEvaluationRuns: [
          run,
          ...current.aiWorkerEvaluationRuns.filter((item) => item.id !== run.id)
        ].slice(0, 30)
      }));
      return run;
    } catch (err) {
      setError(err instanceof Error ? err.message : "AI/Worker 시나리오 평가를 실행하지 못했습니다.");
      return null;
    } finally {
      setLoading(false);
    }
  }

  async function handleUpdateAiObservationMode(enabled: boolean) {
    if (!auth || !platformAdmin) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      await updatePlatformAiObservationMode(auth.accessToken, { enabled, clearExisting: !enabled });
      await refreshPlatform();
    } catch (err) {
      setError(err instanceof Error ? err.message : "AI 관측 모드를 변경하지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }

  async function handleClearAiObservations() {
    if (!auth || !platformAdmin) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      await clearPlatformAiObservations(auth.accessToken);
      await refreshPlatform();
    } catch (err) {
      setError(err instanceof Error ? err.message : "AI 관측 버퍼를 비우지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }

  async function handleCreateAiPricingRule(body: {
    providerCode: string;
    modelName: string;
    currency: string;
    inputTokenPricePerMillion: number;
    outputTokenPricePerMillion: number;
  }) {
    if (!auth || !platformAdmin) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      await createPlatformAiPricingRule(auth.accessToken, body);
      await refreshPlatform();
    } catch (err) {
      setError(err instanceof Error ? err.message : "AI 단가 규칙을 저장하지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }

  async function handleDisableAiPricingRule(pricingRuleId: number) {
    if (!auth || !platformAdmin) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      await disablePlatformAiPricingRule(auth.accessToken, pricingRuleId);
      await refreshPlatform();
    } catch (err) {
      setError(err instanceof Error ? err.message : "AI 단가 규칙을 비활성화하지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }

  async function handleSaveAiHarnessPolicy(
    policyKey: string,
    body: {
      enabled: boolean;
      providerCredentialId?: number | null;
      modelName?: string | null;
      maxAttempts?: number | null;
      timeoutSeconds?: number | null;
      maxOutputTokens?: number | null;
      budgetEnforcementEnabled?: boolean;
      monthlyBudgetAmount?: number | null;
      budgetCurrency?: string | null;
      dailyCallLimit?: number | null;
      monthlyTokenLimit?: number | null;
    }
  ) {
    if (!auth || !platformAdmin) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      await updatePlatformAiHarnessPolicy(auth.accessToken, policyKey, body);
      await refreshPlatform();
    } catch (err) {
      setError(err instanceof Error ? err.message : "AI 하네스 실행 정책을 저장하지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }

  async function handleSaveOfficeAiPolicy(
    officeId: number,
    body: {
      aiEnabled: boolean;
      documentReviewAiEnabled: boolean;
      documentGenerationAiEnabled: boolean;
      preferredProviderCredentialId?: number | null;
      credentialDeliveryMode: string;
      budgetEnforcementEnabled?: boolean;
      monthlyBudgetAmount?: number | null;
      budgetCurrency?: string | null;
      dailyCallLimit?: number | null;
      monthlyTokenLimit?: number | null;
      maxOutputTokens?: number | null;
      perUserDailyCallLimit?: number | null;
      perUserMonthlyTokenLimit?: number | null;
    }
  ) {
    if (!auth || !platformAdmin) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      await updatePlatformOfficeAiPolicy(auth.accessToken, officeId, body);
      await refreshPlatform();
    } catch (err) {
      setError(err instanceof Error ? err.message : "사무소 AI 정책을 저장하지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }

  async function handleCreateAiUserBudgetOverride(body: {
    officeId: number;
    userId: number;
    dailyCallLimit?: number | null;
    monthlyTokenLimit?: number | null;
    monthlyBudgetAmount?: number | null;
    budgetCurrency?: string | null;
    expiresAt?: string | null;
    reason: string;
  }) {
    if (!auth || !platformAdmin) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      await createPlatformAiUserBudgetOverride(auth.accessToken, body);
      await refreshPlatform();
    } catch (err) {
      setError(err instanceof Error ? err.message : "사용자 AI 한도 상향을 저장하지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }

  async function handleDisableAiUserBudgetOverride(overrideId: number, reason?: string | null) {
    if (!auth || !platformAdmin) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      await disablePlatformAiUserBudgetOverride(auth.accessToken, overrideId, reason);
      await refreshPlatform();
    } catch (err) {
      setError(err instanceof Error ? err.message : "사용자 AI 한도 상향을 해제하지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }

  function handleAuthenticated(nextAuth: AdminState) {
    const firstOffice = firstConsoleOffice(nextAuth.user.offices);
    setAuth(nextAuth);
    setSelectedOfficeId(firstOffice?.id ?? null);
    setActiveView(firstOffice && !isOfficeAdminOffice(firstOffice) ? "templates" : "dashboard");
    window.localStorage.setItem(
      AUTH_STORAGE_KEY,
      JSON.stringify({ accessToken: nextAuth.accessToken, refreshToken: nextAuth.refreshToken })
    );
  }

  function logout() {
    window.localStorage.removeItem(AUTH_STORAGE_KEY);
    window.localStorage.removeItem(OFFICE_STORAGE_KEY);
    setAuth(null);
    setSelectedOfficeId(null);
    setOpsData(emptyOpsData);
  }

  if (booting) {
    return (
      <FullScreenCenter>
        <Loader2 className="spin" size={28} />
        <p>운영 콘솔을 준비하는 중입니다.</p>
      </FullScreenCenter>
    );
  }

  if (!auth) {
    return <LoginScreen invitationToken={pendingInvitationToken} onAuthenticated={handleAuthenticated} />;
  }

  if (pendingInvitationToken) {
    return (
      <InvitationAcceptScreen
        auth={auth}
        invitationToken={pendingInvitationToken}
        onAccepted={async () => {
          const user = await me(auth.accessToken);
          setAuth({ ...auth, user });
          setPendingInvitationToken(null);
          window.history.replaceState(null, "", "/");
        }}
        onLogout={logout}
      />
    );
  }

  if (!platformChecked) {
    return (
      <FullScreenCenter>
        <Loader2 className="spin" size={28} />
        <p>플랫폼 권한을 확인하고 있습니다.</p>
      </FullScreenCenter>
    );
  }

  if (consoleOffices.length === 0 && !platformAdmin) {
    return (
      <FullScreenCenter>
        <ShieldCheck size={42} />
        <h1>운영 권한이 없습니다</h1>
        <p>현재 계정은 OWNER 또는 ADMIN 권한을 가진 사무소가 없습니다.</p>
        <button className="button primary" onClick={logout} type="button">
          <LogOut size={16} />
          로그아웃
        </button>
      </FullScreenCenter>
    );
  }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <div className="brand-mark">A</div>
          <div>
            <strong>ArchDox</strong>
            <span>운영 콘솔</span>
          </div>
        </div>

        <label className="mobile-nav-select">
          <span>화면</span>
          <select
            value={activeView}
            onChange={(event) => setActiveView(event.target.value as ViewKey)}
          >
            {visibleOfficeNavItems.length > 0 ? (
              <optgroup label="사무소 운영">
                {visibleOfficeNavItems.map((item) => (
                  <option key={item.key} value={item.key}>
                    {item.label}
                  </option>
                ))}
              </optgroup>
            ) : null}
            {platformAdmin ? (
              <>
                <optgroup label="플랫폼 관리">
                  {platformNavItems.map((item) => (
                    <option key={item.key} value={item.key}>
                      {item.label}
                    </option>
                  ))}
                </optgroup>
                <optgroup label="AI 관리">
                  {aiNavItems.map((item) => (
                    <option key={item.key} value={item.key}>
                      {item.label}
                    </option>
                  ))}
                </optgroup>
              </>
            ) : null}
          </select>
          <ChevronDown size={15} />
        </label>

        <nav className="main-nav" aria-label="운영 메뉴">
          {visibleOfficeNavItems.length > 0 ? (
            <>
              <span className="nav-section-label">사무소 운영</span>
              {visibleOfficeNavItems.map((item) => {
                const Icon = item.icon;
                return (
                  <button
                    key={item.key}
                    className={activeView === item.key ? "nav-item active" : "nav-item"}
                    type="button"
                    onClick={() => setActiveView(item.key)}
                  >
                    <Icon size={18} />
                    {item.label}
                  </button>
                );
              })}
            </>
          ) : null}
          {platformAdmin ? (
            <>
              <span className="nav-section-label">플랫폼 운영</span>
              <button
                className={isPlatformView(activeView) ? "nav-item nav-group-toggle active" : "nav-item nav-group-toggle"}
                type="button"
                onClick={togglePlatformGroup}
              >
                <span className="nav-group-main">
                  <ShieldCheck size={18} />
                  플랫폼 관리
                </span>
                <ChevronDown className={expandedNavGroups.platform ? "nav-group-chevron open" : "nav-group-chevron"} size={15} />
              </button>
              {expandedNavGroups.platform ? (
                <div className="nav-submenu">
                  {platformNavItems.map((item) => (
                    <button
                      className={activeView === item.key ? "nav-subitem active" : "nav-subitem"}
                      key={item.key}
                      onClick={() => setActiveView(item.key)}
                      type="button"
                    >
                      {item.label}
                    </button>
                  ))}
                </div>
              ) : null}
              <button
                className={isAiView(activeView) ? "nav-item nav-group-toggle active" : "nav-item nav-group-toggle"}
                type="button"
                onClick={toggleAiGroup}
              >
                <span className="nav-group-main">
                  <KeyRound size={18} />
                  AI 관리
                </span>
                <ChevronDown className={expandedNavGroups.ai ? "nav-group-chevron open" : "nav-group-chevron"} size={15} />
              </button>
              {expandedNavGroups.ai ? (
                <div className="nav-submenu">
                  {aiNavItems.map((item) => (
                    <button
                      className={activeView === item.key ? "nav-subitem active" : "nav-subitem"}
                      key={item.key}
                      onClick={() => setActiveView(item.key)}
                      type="button"
                    >
                      {item.label}
                    </button>
                  ))}
                </div>
              ) : null}
            </>
          ) : null}
        </nav>

        <div className="sidebar-footer">
          <span>{auth.user.name}</span>
          <small>{auth.user.email}</small>
        </div>
      </aside>

      <main className="workspace">
        <header className="topbar">
          <div>
            <p className="eyebrow">사무소 운영</p>
            <h1>{viewTitle(activeView)}</h1>
          </div>

          <div className="topbar-actions">
            {isPlatformScopedView(activeView) ? (
              <div className="platform-scope-pill">
                <ShieldCheck size={15} />
                <span>플랫폼 전체 범위</span>
              </div>
            ) : (
              <label className="office-select">
                <span>사무소</span>
                <select
                  value={selectedOfficeId ?? ""}
                  onChange={(event) => setSelectedOfficeId(Number(event.target.value))}
                >
                  {consoleOffices.map((office) => (
                    <option key={office.id} value={office.id}>
                      {office.displayName} · {displayLabel(office.role)}
                    </option>
                  ))}
                </select>
                <ChevronDown size={16} />
              </label>
            )}
            <button className="icon-button" onClick={isPlatformScopedView(activeView) ? refreshPlatform : refresh} type="button" title="새로고침" aria-label="새로고침">
              {loading ? <Loader2 className="spin" size={18} /> : <RefreshCcw size={18} />}
            </button>
            <button className="icon-button" onClick={logout} type="button" title="로그아웃" aria-label="로그아웃">
              <LogOut size={18} />
            </button>
          </div>
        </header>

        {selectedOffice && !isPlatformScopedView(activeView) ? <OfficeStrip office={selectedOffice} /> : null}
        {error ? <InlineAlert message={error} /> : null}

        <section className="content-stage">
          {activeView === "dashboard" && <Dashboard data={opsData} loading={loading} />}
          {activeView === "agents" && <AgentsView agents={opsData.agents} sessions={opsData.sessions} />}
          {activeView === "commands" && (
            <CommandsView
              commands={filterByStatus(opsData.commands, commandStatus)}
              status={commandStatus}
              setStatus={setCommandStatus}
            />
          )}
          {activeView === "documents" && (
            <DocumentsView
              documents={filterByStatus(opsData.documents, documentStatus)}
              status={documentStatus}
              setStatus={setDocumentStatus}
            />
          )}
          {activeView === "members" && auth && selectedOffice && (
            <MembersView
              token={auth.accessToken}
              office={selectedOffice}
              currentUserId={auth.user.id}
              onMutated={refresh}
            />
          )}
          {activeView === "projects" && auth && selectedOffice && (
            <ProjectsManagementView
              token={auth.accessToken}
              office={selectedOffice}
              onMutated={refresh}
            />
          )}
          {activeView === "templates" && auth && selectedOfficeId && (
            <TemplatesView token={auth.accessToken} officeId={selectedOfficeId} />
          )}
          {activeView === "photos" && (
            <PhotosView
              photos={filterPhotos(opsData.photos, photoStatus, pickupStatus)}
              photoStatus={photoStatus}
              pickupStatus={pickupStatus}
              setPhotoStatus={setPhotoStatus}
              setPickupStatus={setPickupStatus}
            />
          )}
          {activeView === "deliveries" && (
            <DeliveriesView
              deliveries={filterByStatus(opsData.deliveries, deliveryStatus)}
              status={deliveryStatus}
              setStatus={setDeliveryStatus}
            />
          )}
          {activeView === "events" && <EventsView events={opsData.events} />}
          {isPlatformView(activeView) && (
            <PlatformView
              view={activeView}
              accessToken={auth.accessToken}
              data={platformData}
              platformAdmin={platformAdmin}
              loading={loading}
              onRefresh={refreshPlatform}
              lastDetection={lastPlatformDetection}
              onDetectStuck={runPlatformDetection}
              legalDigestAiDrafts={legalDigestAiDrafts}
              legalBindingAutoGenerateResult={legalBindingAutoGenerateResult}
              onFlowerRuntimeRefresh={refreshPlatformFlowerRuntime}
              onLegalDigestAiDraft={runPlatformLegalDigestAiDraft}
              onLoadLegalDigestAiDrafts={loadPlatformLegalDigestAiDrafts}
              onApproveLegalDigestAiDraft={approvePlatformLegalDigestAiDraftFromUi}
              onRejectLegalDigestAiDraft={rejectPlatformLegalDigestAiDraftFromUi}
              onApplyLegalDigestAiDraft={applyPlatformLegalDigestAiDraftFromUi}
              onLegalOpenDataSync={runPlatformLegalOpenDataSync}
              onLegalDigestRefresh={runPlatformLegalDigestRefresh}
              onCreateLegalDomainBinding={createLegalDomainBindingFromUi}
              onUpdateLegalDomainBinding={updateLegalDomainBindingFromUi}
              onAutoGenerateConstructionSupervisionLegalBindings={autoGenerateConstructionSupervisionLegalBindingsFromUi}
              onDeactivateLegalDomainBinding={deactivateLegalDomainBindingFromUi}
              issuedEngineApiKey={issuedEngineApiKey}
              onCreateEngineApiKey={handleCreateEngineApiKey}
              onRevokeEngineApiKey={handleRevokeEngineApiKey}
              onDismissIssuedEngineApiKey={() => setIssuedEngineApiKey(null)}
              onDiagnoseIncident={runPlatformIncidentDiagnosis}
              onApproveWorkerApproval={handleApproveWorkerApproval}
              onRejectWorkerApproval={handleRejectWorkerApproval}
            />
          )}
          {isAiView(activeView) && (
            <AiManagementView
              view={activeView}
              loading={loading}
              callLogs={platformData.aiCallLogs}
              harnessTraces={platformData.aiHarnessTraces}
              observationMode={platformData.aiObservationMode}
              observations={platformData.aiObservations}
              preflightFindings={platformData.aiPreflightFindings}
              pricingRules={platformData.aiPricingRules}
              providers={platformData.aiProviders}
              harnessPolicies={platformData.aiHarnessPolicies}
              policies={platformData.officeAiPolicies}
              usageSummary={platformData.aiUsageSummary}
              budgetUsageSummary={platformData.aiBudgetUsageSummary}
              userBudgetOverrides={platformData.aiUserBudgetOverrides}
              users={platformData.users}
              workerEvaluationSummary={platformData.aiWorkerEvaluationSummary}
              workerEvaluationRuns={platformData.aiWorkerEvaluationRuns}
              onCreatePricingRule={handleCreateAiPricingRule}
              onDisablePricingRule={handleDisableAiPricingRule}
              onCreateProvider={handleCreateAiProvider}
              onUpdateProvider={handleUpdateAiProvider}
              onPublishProvider={handlePublishAiProvider}
              onTestProvider={handleTestAiProvider}
              onUpdateObservationMode={handleUpdateAiObservationMode}
              onClearObservations={handleClearAiObservations}
              onRefresh={refreshPlatform}
              onCreateWorkerEvaluationRun={handleCreateAiWorkerEvaluationRun}
              onCreateWorkerRuntimeEvaluationRun={handleCreateAiWorkerRuntimeEvaluationRun}
              onCreateWorkerRuntimeScenarioRun={handleCreateAiWorkerRuntimeScenarioRun}
              onSaveHarnessPolicy={handleSaveAiHarnessPolicy}
              onSaveOfficePolicy={handleSaveOfficeAiPolicy}
              onCreateUserBudgetOverride={handleCreateAiUserBudgetOverride}
              onDisableUserBudgetOverride={handleDisableAiUserBudgetOverride}
              providerTestResults={aiProviderTestResults}
            />
          )}
        </section>
      </main>
    </div>
  );
}

function LoginScreen({
  invitationToken,
  onAuthenticated
}: {
  invitationToken?: string | null;
  onAuthenticated: (auth: AdminState) => void;
}) {
  const [mode, setMode] = useState<"login" | "signup">("login");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [name, setName] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const isInvitationFlow = Boolean(invitationToken);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const token =
        mode === "login"
          ? await login(email, password)
          : await signup(email, password, normalizeFormValue(name) ?? email);
      const user = await me(token.accessToken);
      onAuthenticated({ accessToken: token.accessToken, refreshToken: token.refreshToken, user });
    } catch (err) {
      if (mode === "login" && err instanceof ApiError && err.status === 401) {
        setError("이메일 또는 비밀번호를 확인해주세요.");
        return;
      }
      setError(err instanceof Error ? err.message : mode === "login" ? "로그인에 실패했습니다." : "회원가입에 실패했습니다.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="login-page">
      <section className="login-panel">
        <div className="brand large">
          <div className="brand-mark">A</div>
          <div>
            <strong>{isInvitationFlow ? "ArchDox 초대" : "ArchDox 운영 콘솔"}</strong>
            <span>{isInvitationFlow ? "초대 수락을 위해 로그인 또는 회원가입이 필요합니다" : "문서 워크플로우 운영 콘솔"}</span>
          </div>
        </div>
        {isInvitationFlow ? (
          <div className="auth-mode-row">
            <button
              className={mode === "login" ? "segmented-button active" : "segmented-button"}
              onClick={() => setMode("login")}
              type="button"
            >
              로그인
            </button>
            <button
              className={mode === "signup" ? "segmented-button active" : "segmented-button"}
              onClick={() => setMode("signup")}
              type="button"
            >
              회원가입
            </button>
          </div>
        ) : null}
        <form className="login-form" onSubmit={submit}>
          {mode === "signup" ? (
            <label>
              이름
              <input
                autoComplete="name"
                onChange={(event) => setName(event.target.value)}
                placeholder="홍길동"
                required
                type="text"
                value={name}
              />
            </label>
          ) : null}
          <label>
            이메일
            <input
              autoComplete="email"
              onChange={(event) => setEmail(event.target.value)}
              placeholder="admin@example.com"
              type="email"
              value={email}
            />
          </label>
          <label>
            비밀번호
            <input
              autoComplete="current-password"
              onChange={(event) => setPassword(event.target.value)}
              placeholder="password"
              type="password"
              value={password}
            />
          </label>
          {error ? <InlineAlert message={error} /> : null}
          <button className="button primary" disabled={busy} type="submit">
            {busy ? <Loader2 className="spin" size={16} /> : <ShieldCheck size={16} />}
            {mode === "login" ? "로그인" : "회원가입 후 계속"}
          </button>
        </form>
      </section>
    </div>
  );
}

function InvitationAcceptScreen({
  auth,
  invitationToken,
  onAccepted,
  onLogout
}: {
  auth: AdminState;
  invitationToken: string;
  onAccepted: () => Promise<void>;
  onLogout: () => void;
}) {
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function accept() {
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      const member = await acceptOfficeInvitation(auth.accessToken, invitationToken);
      await onAccepted();
      setNotice(`초대를 수락했습니다. office #${member.officeId}에 ${member.role}로 추가되었습니다.`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "초대를 수락하지 못했습니다.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <FullScreenCenter>
      <div className="accept-panel">
        <div className="brand large">
          <div className="brand-mark">A</div>
          <div>
            <strong>ArchDox 초대</strong>
            <span>{auth.user.email}</span>
          </div>
        </div>
        {notice ? <InlineNotice message={notice} /> : null}
        {error ? <InlineAlert message={error} /> : null}
        <div className="accept-actions">
          <button className="button primary" disabled={busy || Boolean(notice)} onClick={accept} type="button">
            {busy ? <Loader2 className="spin" size={16} /> : <CheckCircle2 size={16} />}
            초대 수락
          </button>
          <button className="button" onClick={onLogout} type="button">
            <LogOut size={16} />
            로그아웃
          </button>
        </div>
      </div>
    </FullScreenCenter>
  );
}

function Dashboard({ data, loading }: { data: OpsData; loading: boolean }) {
  const summary = data.summary;
  const failedJobs = summary?.documentJobs.byStatus.FAILED ?? 0;
  const failedPickups = summary?.photoOriginalPickups.byStatus.FAILED ?? 0;
  const failedDeliveries = summary?.documentDeliveries.byStatus.FAILED ?? 0;
  const activeWarnings = failedJobs + failedPickups + failedDeliveries + (summary?.inFlightAgentCommands ?? 0);

  return (
    <div className="view-stack">
      <Panel title="사용자 AI 한도 상향" icon={<UserPlus size={18} />} count={activeOverrides.length}>
        <>
          <AiUserBudgetOverrideForm
            busy={busy}
            offices={summary.offices}
            users={users}
            onSubmit={onCreateOverride}
          />
          <Table
            columns={["사용자", "사무소", "한도", "월 예산", "만료", "사유", "작업"]}
            empty="현재 적용 중인 사용자별 AI 한도 상향이 없습니다."
            rows={activeOverrides.map((override) => [
              <CellTitle key="user" title={override.userName ?? `User #${override.userId}`} subtitle={override.userEmail ?? `#${override.userId}`} />,
              override.officeName ?? override.officeCode ?? `#${override.officeId}`,
              `${override.dailyCallLimit ?? "-"} calls/day · ${override.monthlyTokenLimit ?? "-"} tokens/month`,
              override.monthlyBudgetAmount == null ? "-" : `${override.budgetCurrency} ${formatMoney(override.monthlyBudgetAmount)}`,
              formatDate(override.expiresAt),
              override.reason,
              <button
                className="button compact"
                disabled={busy}
                key="disable"
                onClick={() => onDisableOverride(override.id, "Disabled from AI budget usage screen.")}
                type="button"
              >
                <XCircle size={14} />
                해제
              </button>
            ])}
          />
        </>
      </Panel>

      <div className="metric-grid">
        <MetricCard icon={<Server size={20} />} label="에이전트" value={summary?.agents.total ?? 0} detail={`${summary?.agents.byStatus.ONLINE ?? 0} 온라인`} tone="green" />
        <MetricCard icon={<Wifi size={20} />} label="활성 세션" value={summary?.activeAgentSessions ?? 0} detail="WebSocket 연결" tone="blue" />
        <MetricCard icon={<Command size={20} />} label="진행 중 명령" value={summary?.inFlightAgentCommands ?? 0} detail="대기 · 수신 확인" tone="amber" />
        <MetricCard icon={<FileText size={20} />} label="문서 작업" value={summary?.documentJobs.total ?? 0} detail={`${summary?.documentJobs.byStatus.GENERATING ?? 0} 생성 중`} tone="blue" />
        <MetricCard icon={<Camera size={20} />} label="사진" value={summary?.photos.total ?? 0} detail={`${summary?.photoOriginalPickups.byStatus.PENDING ?? 0} 회수 대기`} tone="slate" />
        <MetricCard icon={<AlertTriangle size={20} />} label="확인 필요" value={activeWarnings} detail="명령 + 실패" tone={activeWarnings > 0 ? "red" : "green"} />
      </div>

      <div className="dashboard-grid">
        <Panel title="워크플로우 상태" icon={<Gauge size={18} />}>
          <StatusBars
            groups={[
              ["문서 생성", summary?.documentJobs.byStatus ?? {}],
              ["사진 원본 회수", summary?.photoOriginalPickups.byStatus ?? {}],
              ["문서 전달", summary?.documentDeliveries.byStatus ?? {}]
            ]}
          />
        </Panel>

        <Panel title="최근 이벤트" icon={<Activity size={18} />}>
          <EventList events={data.events.slice(0, 6)} />
        </Panel>
        </div>

      {loading ? <p className="muted">운영 데이터를 갱신하고 있습니다.</p> : null}
    </div>
  );
}

function AgentsView({ agents, sessions }: { agents: Agent[]; sessions: AgentSession[] }) {
  return (
    <div className="view-stack">
      <Panel title="에이전트 목록" icon={<Server size={18} />} count={agents.length}>
        <Table
          columns={["에이전트", "상태", "모드", "세션", "명령", "버전", "최근 접속"]}
          empty="등록된 에이전트가 없습니다."
          rows={agents.map((agent) => [
            <CellTitle key="agent" title={agent.agentCode} subtitle={`#${agent.id}`} />,
            <StatusBadge key="status" status={agent.status} />,
            displayLabel(agent.deploymentMode),
            `${agent.activeSessionCount}`,
            `${agent.inFlightCommandCount} 진행 · ${agent.failedCommandCount} 실패`,
            agent.version ?? "-",
            formatDate(agent.lastSeenAt)
          ])}
        />
      </Panel>
      <Panel title="최근 세션" icon={<Wifi size={18} />} count={sessions.length}>
        <Table
          columns={["세션", "에이전트", "상태", "API 인스턴스", "연결", "최근 신호", "종료 사유"]}
          empty="세션 기록이 없습니다."
          rows={sessions.map((session) => [
            <CellTitle key="session" title={`#${session.id}`} subtitle={session.websocketSessionId} />,
            `#${session.agentId}`,
            <StatusBadge key="status" status={session.status} />,
            session.apiInstanceId,
            formatDate(session.connectedAt),
            formatDate(session.lastSeenAt),
            session.disconnectReason ?? "-"
          ])}
        />
      </Panel>
    </div>
  );
}

function CommandsView({
  commands,
  status,
  setStatus
}: {
  commands: AgentCommand[];
  status: string;
  setStatus: (status: string) => void;
}) {
  return (
    <Panel
      title="에이전트 명령"
      icon={<Command size={18} />}
      count={commands.length}
      action={<FilterSelect label="상태" options={commandFilterOptions} value={status} onChange={setStatus} />}
    >
      <Table
        columns={["명령", "에이전트", "상태", "시도", "생성", "수신", "완료/실패", "오류"]}
        empty="표시할 명령이 없습니다."
        rows={commands.map((command) => [
          <CellTitle key="command" title={displayLabel(command.commandType)} subtitle={`${command.commandType} / #${command.id}`} />,
          command.agentCode,
          <StatusBadge key="status" status={command.status} />,
          `${command.attemptCount}/${command.maxAttempts}`,
          formatDate(command.createdAt),
          formatDate(command.ackAt),
          formatDate(command.completedAt ?? command.failedAt),
          command.errorMessage ?? "-"
        ])}
      />
    </Panel>
  );
}

function DocumentsView({
  documents,
  status,
  setStatus
}: {
  documents: DocumentJob[];
  status: string;
  setStatus: (status: string) => void;
}) {
  return (
    <Panel
      title="문서 생성 작업"
      icon={<FileText size={18} />}
      count={documents.length}
      action={<FilterSelect label="상태" options={documentFilterOptions} value={status} onChange={setStatus} />}
    >
      <Table
        columns={["작업", "상태", "진행", "처리 주체", "리포트", "산출물", "요청", "오류"]}
        empty="표시할 문서 작업이 없습니다."
        rows={documents.map((job) => [
          <CellTitle key="job" title={`문서 작업 #${job.id}`} subtitle={job.progressStep} />,
          <StatusBadge key="status" status={job.status} />,
          <Progress key="progress" value={job.progressPercent} />,
          displayLabel(job.workerType),
          `#${job.reportId}`,
          job.artifacts.length === 0 ? "-" : job.artifacts.map((artifact) => artifact.fileName).join(", "),
          formatDate(job.requestedAt),
          job.errorMessage ?? "-"
        ])}
      />
    </Panel>
  );
}

function MembersView({
  token,
  office,
  currentUserId,
  onMutated
}: {
  token: string;
  office: Office;
  currentUserId: number;
  onMutated: () => void | Promise<void>;
}) {
  const [members, setMembers] = useState<OfficeMember[]>([]);
  const [invitations, setInvitations] = useState<OfficeInvitation[]>([]);
  const [roleDrafts, setRoleDrafts] = useState<Record<number, MembershipRole>>({});
  const [email, setEmail] = useState("");
  const [role, setRole] = useState<MembershipRole>("MEMBER");
  const [inviteEmail, setInviteEmail] = useState("");
  const [inviteRole, setInviteRole] = useState<MembershipRole>("MEMBER");
  const [inviteDays, setInviteDays] = useState("14");
  const [latestInviteUrl, setLatestInviteUrl] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [busyAction, setBusyAction] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const isOwner = office.role === "OWNER";
  const activeCount = members.filter((member) => member.status === "ACTIVE").length;
  const ownerCount = members.filter((member) => member.status === "ACTIVE" && member.role === "OWNER").length;

  useEffect(() => {
    refreshMembersAndInvitations();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token, office.id]);

  async function refreshMembersAndInvitations() {
    await Promise.all([loadMembers(), loadInvitations()]);
  }

  async function loadMembers() {
    setLoading(true);
    setError(null);
    try {
      const next = await getOfficeMembers(token, office.id);
      setMembers(next);
      setRoleDrafts(Object.fromEntries(next.map((member) => [member.userId, member.role])));
    } catch (err) {
      setError(err instanceof Error ? err.message : "멤버 목록을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }

  async function loadInvitations() {
    setError(null);
    try {
      setInvitations(await getOfficeInvitations(token, office.id));
    } catch (err) {
      setError(err instanceof Error ? err.message : "초대 목록을 불러오지 못했습니다.");
    }
  }

  async function submitMember(event: FormEvent) {
    event.preventDefault();
    const normalizedEmail = normalizeFormValue(email);
    if (!normalizedEmail) {
      setError("추가할 사용자 이메일을 입력해야 합니다.");
      return;
    }
    setBusyAction("add-member");
    setError(null);
    setNotice(null);
    try {
      const updated = await addOfficeMember(token, office.id, { email: normalizedEmail, role });
      setEmail("");
      setRole("MEMBER");
      await refreshAfterMutation(updated);
      setNotice(`${updated.email} 멤버를 추가하거나 재활성화했습니다.`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "멤버를 추가하지 못했습니다.");
    } finally {
      setBusyAction(null);
    }
  }

  async function submitInvitation(event: FormEvent) {
    event.preventDefault();
    const normalizedEmail = normalizeFormValue(inviteEmail);
    const expiresInDays = Number(inviteDays);
    if (!normalizedEmail || !Number.isInteger(expiresInDays)) {
      setError("초대 이메일과 만료일을 확인해야 합니다.");
      return;
    }
    setBusyAction("create-invitation");
    setError(null);
    setNotice(null);
    setLatestInviteUrl(null);
    try {
      const created = await createOfficeInvitation(token, office.id, {
        email: normalizedEmail,
        role: inviteRole,
        expiresInDays
      });
      setInviteEmail("");
      setInviteRole("MEMBER");
      setInviteDays("14");
      setLatestInviteUrl(invitationUrl(created));
      await loadInvitations();
      setNotice(`${created.email} 초대를 생성했습니다.`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "초대를 생성하지 못했습니다.");
    } finally {
      setBusyAction(null);
    }
  }

  async function cancelInvitation(invitation: OfficeInvitation) {
    setBusyAction(`cancel-invitation-${invitation.id}`);
    setError(null);
    setNotice(null);
    try {
      const updated = await cancelOfficeInvitation(token, office.id, invitation.id);
      setInvitations((current) => current.map((item) => (item.id === updated.id ? updated : item)));
      setNotice(`${updated.email} 초대를 취소했습니다.`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "초대를 취소하지 못했습니다.");
    } finally {
      setBusyAction(null);
    }
  }

  async function copyLatestInviteUrl() {
    if (!latestInviteUrl) {
      return;
    }
    await navigator.clipboard.writeText(latestInviteUrl);
    setNotice("초대 URL을 클립보드에 복사했습니다.");
  }

  async function saveRole(member: OfficeMember) {
    const nextRole = roleDrafts[member.userId] ?? member.role;
    if (nextRole === member.role) {
      return;
    }
    setBusyAction(`role-${member.userId}`);
    setError(null);
    setNotice(null);
    try {
      const updated = await updateOfficeMemberRole(token, office.id, member.userId, { role: nextRole });
      await refreshAfterMutation(updated);
      setNotice(`${updated.email} 역할을 ${updated.role}로 변경했습니다.`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "역할을 변경하지 못했습니다.");
    } finally {
      setBusyAction(null);
    }
  }

  async function deactivateMember(member: OfficeMember) {
    setBusyAction(`deactivate-${member.userId}`);
    setError(null);
    setNotice(null);
    try {
      const updated = await deactivateOfficeMember(token, office.id, member.userId);
      await refreshAfterMutation(updated);
      setNotice(`${updated.email} 멤버를 비활성화했습니다.`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "멤버를 비활성화하지 못했습니다.");
    } finally {
      setBusyAction(null);
    }
  }

  async function refreshAfterMutation(updated: OfficeMember) {
    setMembers((current) => {
      const exists = current.some((member) => member.userId === updated.userId);
      return exists
        ? current.map((member) => (member.userId === updated.userId ? updated : member))
        : [updated, ...current];
    });
    setRoleDrafts((current) => ({ ...current, [updated.userId]: updated.role }));
    await loadMembers();
    await onMutated();
  }

  function canEdit(member: OfficeMember) {
    if (member.userId === currentUserId || member.status !== "ACTIVE") {
      return false;
    }
    if (member.role === "OWNER" && !isOwner) {
      return false;
    }
    return true;
  }

  return (
    <div className="view-stack">
      {notice ? <InlineNotice message={notice} /> : null}
      {error ? <InlineAlert message={error} /> : null}

      <div className="member-grid">
        <Panel title="멤버 추가" icon={<UserPlus size={18} />}>
          <div className="config-panel-body">
            <form className="member-form" onSubmit={submitMember}>
              <label>
                사용자 이메일
                <input
                  autoComplete="email"
                  onChange={(event) => setEmail(event.target.value)}
                  placeholder="member@example.com"
                  required
                  type="email"
                  value={email}
                />
              </label>
              <label>
                역할
                <select onChange={(event) => setRole(event.target.value as MembershipRole)} value={role}>
                  {memberRoleOptions.map((option) => (
                    <option disabled={option === "OWNER" && !isOwner} key={option} value={option}>
                      {option}
                    </option>
                  ))}
                </select>
              </label>
              <button className="button primary" disabled={busyAction === "add-member"} type="submit">
                {busyAction === "add-member" ? <Loader2 className="spin" size={16} /> : <Plus size={16} />}
                추가
              </button>
            </form>
          </div>
        </Panel>

        <Panel title="멤버 관리 규칙" icon={<ShieldCheck size={18} />}>
          <div className="member-rule-list">
            <div>
              <strong>{activeCount}</strong>
              <span>활성 멤버</span>
            </div>
            <div>
              <strong>{ownerCount}</strong>
              <span>활성 OWNER</span>
            </div>
            <div>
              <strong>{office.role}</strong>
              <span>내 관리 권한</span>
            </div>
          </div>
        </Panel>
      </div>

      <Panel title="초대 링크 생성" icon={<UserPlus size={18} />} count={invitations.length}>
        <div className="config-panel-body">
          <form className="member-form" onSubmit={submitInvitation}>
            <label>
              초대 이메일
              <input
                autoComplete="email"
                onChange={(event) => setInviteEmail(event.target.value)}
                placeholder="new-user@example.com"
                required
                type="email"
                value={inviteEmail}
              />
            </label>
            <label>
              역할
              <select onChange={(event) => setInviteRole(event.target.value as MembershipRole)} value={inviteRole}>
                {memberRoleOptions.map((option) => (
                  <option disabled={option === "OWNER" && !isOwner} key={option} value={option}>
                    {option}
                  </option>
                ))}
              </select>
            </label>
            <label>
              만료
              <select onChange={(event) => setInviteDays(event.target.value)} value={inviteDays}>
                <option value="1">1일</option>
                <option value="7">7일</option>
                <option value="14">14일</option>
                <option value="30">30일</option>
              </select>
            </label>
            <button className="button primary" disabled={busyAction === "create-invitation"} type="submit">
              {busyAction === "create-invitation" ? <Loader2 className="spin" size={16} /> : <Plus size={16} />}
              생성
            </button>
          </form>

          {latestInviteUrl ? (
            <div className="invite-url-row">
              <input readOnly type="text" value={latestInviteUrl} />
              <button className="button" onClick={copyLatestInviteUrl} type="button">
                <Copy size={16} />
                복사
              </button>
            </div>
          ) : null}

          <Table
            columns={["이메일", "역할", "상태", "토큰", "만료", "관리"]}
            empty="초대가 없습니다."
            rows={invitations.map((invitation) => [
              invitation.email,
              invitation.role,
              <StatusBadge key="status" status={invitation.status} />,
              `${invitation.tokenPreview}...`,
              formatDate(invitation.expiresAt),
              <button
                className="button danger"
                disabled={invitation.status !== "PENDING" || busyAction !== null}
                key="cancel"
                onClick={() => cancelInvitation(invitation)}
                type="button"
              >
                {busyAction === `cancel-invitation-${invitation.id}` ? (
                  <Loader2 className="spin" size={16} />
                ) : (
                  <XCircle size={16} />
                )}
                취소
              </button>
            ])}
          />
        </div>
      </Panel>

      <Panel
        title="사무소 멤버"
        icon={<Users size={18} />}
        count={members.length}
        action={
          <button className="icon-button" onClick={loadMembers} type="button" title="새로고침" aria-label="새로고침">
            {loading ? <Loader2 className="spin" size={18} /> : <RefreshCcw size={18} />}
          </button>
        }
      >
        <Table
          columns={["사용자", "역할", "상태", "가입", "관리"]}
          empty="사무소 멤버가 없습니다."
          rows={members.map((member) => {
            const editable = canEdit(member);
            const draftRole = roleDrafts[member.userId] ?? member.role;
            const roleChanged = draftRole !== member.role;
            return [
              <CellTitle
                key="user"
                title={member.name}
                subtitle={`${member.email} · user #${member.userId}`}
              />,
              <select
                className="inline-select"
                disabled={!editable || busyAction !== null}
                key="role"
                onChange={(event) =>
                  setRoleDrafts((current) => ({
                    ...current,
                    [member.userId]: event.target.value as MembershipRole
                  }))
                }
                value={draftRole}
              >
                {memberRoleOptions.map((option) => (
                  <option disabled={option === "OWNER" && !isOwner} key={option} value={option}>
                    {option}
                  </option>
                ))}
              </select>,
              <StatusBadge key="status" status={member.status} />,
              formatDate(member.joinedAt),
              <div className="member-actions" key="actions">
                <button
                  className="button"
                  disabled={!editable || !roleChanged || busyAction === `role-${member.userId}` || busyAction !== null}
                  onClick={() => saveRole(member)}
                  type="button"
                >
                  {busyAction === `role-${member.userId}` ? <Loader2 className="spin" size={16} /> : <ShieldCheck size={16} />}
                  저장
                </button>
                <button
                  className="button danger"
                  disabled={!editable || busyAction === `deactivate-${member.userId}` || busyAction !== null}
                  onClick={() => deactivateMember(member)}
                  type="button"
                >
                  {busyAction === `deactivate-${member.userId}` ? <Loader2 className="spin" size={16} /> : <XCircle size={16} />}
                  비활성화
                </button>
              </div>
            ];
          })}
        />
      </Panel>
    </div>
  );
}

function ProjectsManagementView({
  token,
  office,
  onMutated
}: {
  token: string;
  office: Office;
  onMutated: () => void | Promise<void>;
}) {
  const emptyProjectForm: ProjectFormRequest = {
    name: "",
    address: "",
    buildingType: "CONSTRUCTION_SUPERVISION",
    startDate: "",
    endDate: ""
  };
  const [projects, setProjects] = useState<Project[]>([]);
  const [members, setMembers] = useState<OfficeMember[]>([]);
  const [assignments, setAssignments] = useState<ProjectAssignment[]>([]);
  const [selectedProjectId, setSelectedProjectId] = useState<number | null>(null);
  const [createForm, setCreateForm] = useState<ProjectFormRequest>(emptyProjectForm);
  const [editForm, setEditForm] = useState<ProjectFormRequest>(emptyProjectForm);
  const [assignmentUserId, setAssignmentUserId] = useState("");
  const [assignmentRole, setAssignmentRole] = useState<ProjectAssignmentRole>("REPORT_WRITER");
  const [loading, setLoading] = useState(false);
  const [busyAction, setBusyAction] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const selectedProject = useMemo(
    () => projects.find((project) => project.id === selectedProjectId) ?? null,
    [projects, selectedProjectId]
  );
  const activeMembers = members.filter((member) => member.status === "ACTIVE");
  const canManageProjects = adminRoles.has(office.role);

  useEffect(() => {
    refreshProjectsAndMembers();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token, office.id]);

  useEffect(() => {
    if (projects.length === 0) {
      setSelectedProjectId(null);
      return;
    }
    if (!selectedProjectId || !projects.some((project) => project.id === selectedProjectId)) {
      setSelectedProjectId(projects[0].id);
    }
  }, [projects, selectedProjectId]);

  useEffect(() => {
    if (!selectedProject) {
      setAssignments([]);
      setEditForm(emptyProjectForm);
      return;
    }
    setEditForm(projectFormFromProject(selectedProject));
    loadAssignments(selectedProject.id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedProject?.id]);

  async function refreshProjectsAndMembers() {
    setLoading(true);
    setError(null);
    try {
      const [nextProjects, nextMembers] = await Promise.all([
        getProjects(token, office.id),
        getOfficeMembers(token, office.id)
      ]);
      setProjects(nextProjects);
      setMembers(nextMembers);
    } catch (err) {
      setError(err instanceof Error ? err.message : "프로젝트 정보를 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }

  async function loadAssignments(projectId: number) {
    setError(null);
    try {
      setAssignments(await getProjectAssignments(token, office.id, projectId));
    } catch (err) {
      setError(err instanceof Error ? err.message : "프로젝트 배정 정보를 불러오지 못했습니다.");
    }
  }

  async function submitCreateProject(event: FormEvent) {
    event.preventDefault();
    const body = normalizeProjectForm(createForm);
    if (!body.name) {
      setError("프로젝트 이름을 입력해야 합니다.");
      return;
    }
    setBusyAction("create-project");
    setNotice(null);
    setError(null);
    try {
      const created = await createProject(token, office.id, body);
      setCreateForm(emptyProjectForm);
      setProjects((current) => [created, ...current]);
      setSelectedProjectId(created.id);
      setNotice(`${created.name} 프로젝트를 생성했습니다.`);
      await onMutated();
    } catch (err) {
      setError(err instanceof Error ? err.message : "프로젝트를 생성하지 못했습니다.");
    } finally {
      setBusyAction(null);
    }
  }

  async function submitUpdateProject(event: FormEvent) {
    event.preventDefault();
    if (!selectedProject) {
      return;
    }
    const body = normalizeProjectForm(editForm);
    if (!body.name) {
      setError("프로젝트 이름을 입력해야 합니다.");
      return;
    }
    setBusyAction(`update-project-${selectedProject.id}`);
    setNotice(null);
    setError(null);
    try {
      const updated = await updateProject(token, office.id, selectedProject.id, body);
      replaceProject(updated);
      setNotice(`${updated.name} 프로젝트 정보를 저장했습니다.`);
      await onMutated();
    } catch (err) {
      setError(err instanceof Error ? err.message : "프로젝트 정보를 저장하지 못했습니다.");
    } finally {
      setBusyAction(null);
    }
  }

  async function archiveSelectedProject() {
    if (!selectedProject) {
      return;
    }
    setBusyAction(`archive-project-${selectedProject.id}`);
    setNotice(null);
    setError(null);
    try {
      const updated = await archiveProject(token, office.id, selectedProject.id);
      replaceProject(updated);
      setNotice(`${updated.name} 프로젝트를 보관 상태로 변경했습니다.`);
      await onMutated();
    } catch (err) {
      setError(err instanceof Error ? err.message : "프로젝트를 보관하지 못했습니다.");
    } finally {
      setBusyAction(null);
    }
  }

  async function deleteSelectedProject() {
    if (!selectedProject) {
      return;
    }
    const confirmed = window.confirm(
      `${selectedProject.name} 프로젝트를 삭제하면 연결된 현장, 리포트, 사진, 문서가 함께 삭제됩니다. 계속할까요?`
    );
    if (!confirmed) {
      return;
    }
    setBusyAction(`delete-project-${selectedProject.id}`);
    setNotice(null);
    setError(null);
    try {
      await deleteProject(token, office.id, selectedProject.id);
      setProjects((current) => current.filter((project) => project.id !== selectedProject.id));
      setAssignments([]);
      setNotice(`${selectedProject.name} 프로젝트를 삭제했습니다.`);
      await onMutated();
    } catch (err) {
      setError(err instanceof Error ? err.message : "프로젝트를 삭제하지 못했습니다.");
    } finally {
      setBusyAction(null);
    }
  }

  async function submitAssignment(event: FormEvent) {
    event.preventDefault();
    if (!selectedProject) {
      return;
    }
    const userId = Number(assignmentUserId);
    if (!Number.isInteger(userId)) {
      setError("배정할 멤버를 선택해야 합니다.");
      return;
    }
    setBusyAction("upsert-project-assignment");
    setNotice(null);
    setError(null);
    try {
      const updated = await upsertProjectAssignment(token, office.id, selectedProject.id, {
        userId,
        role: assignmentRole
      });
      setAssignmentUserId("");
      setAssignmentRole("REPORT_WRITER");
      setAssignments((current) => {
        const exists = current.some((assignment) => assignment.userId === updated.userId);
        return exists
          ? current.map((assignment) => (assignment.userId === updated.userId ? updated : assignment))
          : [updated, ...current];
      });
      setNotice(`${updated.email ?? `user #${updated.userId}`} 배정을 저장했습니다.`);
      await onMutated();
    } catch (err) {
      setError(err instanceof Error ? err.message : "프로젝트 배정을 저장하지 못했습니다.");
    } finally {
      setBusyAction(null);
    }
  }

  async function removeAssignment(assignment: ProjectAssignment) {
    if (!selectedProject) {
      return;
    }
    setBusyAction(`remove-project-assignment-${assignment.userId}`);
    setNotice(null);
    setError(null);
    try {
      await removeProjectAssignment(token, office.id, selectedProject.id, assignment.userId);
      setAssignments((current) => current.filter((item) => item.userId !== assignment.userId));
      setNotice(`${assignment.email ?? `user #${assignment.userId}`} 배정을 해제했습니다.`);
      await onMutated();
    } catch (err) {
      setError(err instanceof Error ? err.message : "프로젝트 배정을 해제하지 못했습니다.");
    } finally {
      setBusyAction(null);
    }
  }

  function replaceProject(project: Project) {
    setProjects((current) => current.map((item) => (item.id === project.id ? project : item)));
  }

  function updateCreateForm(field: keyof ProjectFormRequest, value: string) {
    setCreateForm((current) => ({ ...current, [field]: value }));
  }

  function updateEditForm(field: keyof ProjectFormRequest, value: string) {
    setEditForm((current) => ({ ...current, [field]: value }));
  }

  return (
    <div className="view-stack">
      {notice ? <InlineNotice message={notice} /> : null}
      {error ? <InlineAlert message={error} /> : null}

      <div className="project-management-grid">
        <Panel
          title="프로젝트 목록"
          icon={<FileText size={18} />}
          count={projects.length}
          action={
            <button className="icon-button" onClick={refreshProjectsAndMembers} type="button" title="새로고침" aria-label="새로고침">
              {loading ? <Loader2 className="spin" size={18} /> : <RefreshCcw size={18} />}
            </button>
          }
        >
          <div className="config-panel-body">
            <div className="selectable-list">
              {projects.length === 0 ? (
                <EmptyState message="관리할 프로젝트가 없습니다." />
              ) : (
                projects.map((project) => (
                  <button
                    className={project.id === selectedProjectId ? "selectable-row active" : "selectable-row"}
                    key={project.id}
                    onClick={() => setSelectedProjectId(project.id)}
                    type="button"
                  >
                    <CellTitle
                      title={project.name}
                      subtitle={`${project.address ?? "주소 미입력"} · #${project.id}`}
                    />
                    <StatusBadge status={project.status} />
                  </button>
                ))
              )}
            </div>

            <form className="project-form" onSubmit={submitCreateProject}>
              <label>
                프로젝트 이름
                <input
                  disabled={!canManageProjects}
                  onChange={(event) => updateCreateForm("name", event.target.value)}
                  placeholder="예: 2026 상반기 공사감리"
                  required
                  value={createForm.name ?? ""}
                />
              </label>
              <label>
                주소
                <input
                  disabled={!canManageProjects}
                  onChange={(event) => updateCreateForm("address", event.target.value)}
                  placeholder="현장 주소 또는 대표 주소"
                  value={createForm.address ?? ""}
                />
              </label>
              <label>
                업무유형
                <select
                  disabled={!canManageProjects}
                  onChange={(event) => updateCreateForm("buildingType", event.target.value)}
                  value={createForm.buildingType ?? ""}
                >
                  <option value="CONSTRUCTION_SUPERVISION">공사감리</option>
                  <option value="">미지정</option>
                </select>
              </label>
              <div className="date-pair">
                <label>
                  시작일
                  <input
                    disabled={!canManageProjects}
                    onChange={(event) => updateCreateForm("startDate", event.target.value)}
                    type="date"
                    value={createForm.startDate ?? ""}
                  />
                </label>
                <label>
                  종료일
                  <input
                    disabled={!canManageProjects}
                    onChange={(event) => updateCreateForm("endDate", event.target.value)}
                    type="date"
                    value={createForm.endDate ?? ""}
                  />
                </label>
              </div>
              <button className="button primary" disabled={!canManageProjects || busyAction === "create-project"} type="submit">
                {busyAction === "create-project" ? <Loader2 className="spin" size={16} /> : <Plus size={16} />}
                프로젝트 생성
              </button>
            </form>
          </div>
        </Panel>

        <Panel
          title="프로젝트 상세/배정"
          icon={<Users size={18} />}
          count={assignments.length}
        >
          <div className="config-panel-body">
            {!selectedProject ? (
              <EmptyState message="왼쪽에서 프로젝트를 선택하세요." />
            ) : (
              <>
                <form className="project-form" onSubmit={submitUpdateProject}>
                  <label>
                    프로젝트 이름
                    <input
                      disabled={!canManageProjects}
                      onChange={(event) => updateEditForm("name", event.target.value)}
                      required
                      value={editForm.name ?? ""}
                    />
                  </label>
                  <label>
                    주소
                    <input
                      disabled={!canManageProjects}
                      onChange={(event) => updateEditForm("address", event.target.value)}
                      value={editForm.address ?? ""}
                    />
                  </label>
                  <label>
                    업무유형
                    <select
                      disabled={!canManageProjects}
                      onChange={(event) => updateEditForm("buildingType", event.target.value)}
                      value={editForm.buildingType ?? ""}
                    >
                      <option value="CONSTRUCTION_SUPERVISION">공사감리</option>
                      <option value="">미지정</option>
                    </select>
                  </label>
                  <div className="date-pair">
                    <label>
                      시작일
                      <input
                        disabled={!canManageProjects}
                        onChange={(event) => updateEditForm("startDate", event.target.value)}
                        type="date"
                        value={editForm.startDate ?? ""}
                      />
                    </label>
                    <label>
                      종료일
                      <input
                        disabled={!canManageProjects}
                        onChange={(event) => updateEditForm("endDate", event.target.value)}
                        type="date"
                        value={editForm.endDate ?? ""}
                      />
                    </label>
                  </div>
                  <div className="project-action-row">
                    <button
                      className="button primary"
                      disabled={!canManageProjects || busyAction === `update-project-${selectedProject.id}`}
                      type="submit"
                    >
                      {busyAction === `update-project-${selectedProject.id}` ? <Loader2 className="spin" size={16} /> : <ShieldCheck size={16} />}
                      기본정보 저장
                    </button>
                    <button
                      className="button"
                      disabled={!canManageProjects || selectedProject.status === "ARCHIVED" || busyAction === `archive-project-${selectedProject.id}`}
                      onClick={archiveSelectedProject}
                      type="button"
                    >
                      보관
                    </button>
                    <button
                      className="button danger"
                      disabled={!canManageProjects || busyAction === `delete-project-${selectedProject.id}`}
                      onClick={deleteSelectedProject}
                      type="button"
                    >
                      {busyAction === `delete-project-${selectedProject.id}` ? <Loader2 className="spin" size={16} /> : <XCircle size={16} />}
                      삭제
                    </button>
                  </div>
                </form>

                <div className="assignment-rule-note">
                  <strong>배정 기준</strong>
                  <span>MANAGER는 총괄감리책임자 서명 후보, REPORT_WRITER는 건축사보/작성자 서명 후보로 사용됩니다.</span>
                </div>

                <form className="project-assignment-form" onSubmit={submitAssignment}>
                  <label>
                    멤버
                    <select
                      disabled={!canManageProjects || activeMembers.length === 0}
                      onChange={(event) => setAssignmentUserId(event.target.value)}
                      value={assignmentUserId}
                    >
                      <option value="">멤버 선택</option>
                      {activeMembers.map((member) => (
                        <option key={member.userId} value={member.userId}>
                          {member.name} · {member.email}
                        </option>
                      ))}
                    </select>
                  </label>
                  <label>
                    프로젝트 역할
                    <select
                      disabled={!canManageProjects}
                      onChange={(event) => setAssignmentRole(event.target.value as ProjectAssignmentRole)}
                      value={assignmentRole}
                    >
                      {projectAssignmentRoleOptions.map((option) => (
                        <option key={option} value={option}>
                          {projectAssignmentRoleLabel(option)}
                        </option>
                      ))}
                    </select>
                  </label>
                  <button className="button primary" disabled={!canManageProjects || busyAction === "upsert-project-assignment"} type="submit">
                    {busyAction === "upsert-project-assignment" ? <Loader2 className="spin" size={16} /> : <Plus size={16} />}
                    배정 저장
                  </button>
                </form>

                <Table
                  columns={["멤버", "역할", "상태", "배정일", "관리"]}
                  empty="프로젝트 배정이 없습니다."
                  rows={assignments.map((assignment) => [
                    <CellTitle
                      key="member"
                      title={assignment.name ?? assignment.email ?? `user #${assignment.userId}`}
                      subtitle={assignment.email ? `user #${assignment.userId}` : null}
                    />,
                    projectAssignmentRoleLabel(assignment.role),
                    <StatusBadge key="status" status={assignment.status} />,
                    formatDate(assignment.assignedAt),
                    <button
                      className="button danger"
                      disabled={!canManageProjects || busyAction === `remove-project-assignment-${assignment.userId}`}
                      key="remove"
                      onClick={() => removeAssignment(assignment)}
                      type="button"
                    >
                      {busyAction === `remove-project-assignment-${assignment.userId}` ? <Loader2 className="spin" size={16} /> : <XCircle size={16} />}
                      해제
                    </button>
                  ])}
                />
              </>
            )}
          </div>
        </Panel>
      </div>
    </div>
  );
}

function TemplatesView({ token, officeId }: { token: string; officeId: number }) {
  const [templates, setTemplates] = useState<ConfigDefinition[]>([]);
  const [revisions, setRevisions] = useState<DocumentTemplateRevision[]>([]);
  const [overrides, setOverrides] = useState<OfficeConfigOverride[]>([]);
  const [fieldCatalog, setFieldCatalog] = useState<TemplateFieldCatalog | null>(null);
  const [selectedTemplateId, setSelectedTemplateId] = useState<number | null>(null);
  const [reportTypeFilter, setReportTypeFilter] = useState("");
  const [templateCode, setTemplateCode] = useState("");
  const [templateName, setTemplateName] = useState("");
  const [templateReportType, setTemplateReportType] = useState("");
  const [schemaText, setSchemaText] = useState('{\n  "required": []\n}');
  const [composePolicyText, setComposePolicyText] = useState('{\n  "photoSection": "photoTable"\n}');
  const [aiPromptsText, setAiPromptsText] = useState("{}");
  const [overrideReportType, setOverrideReportType] = useState("");
  const [overrideRevisionId, setOverrideRevisionId] = useState("");
  const [loading, setLoading] = useState(false);
  const [busyAction, setBusyAction] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const selectedTemplate = useMemo(
    () => templates.find((template) => template.id === selectedTemplateId) ?? null,
    [templates, selectedTemplateId]
  );

  const publishedRevisions = useMemo(
    () => revisions.filter((revision) => revision.status === "PUBLISHED"),
    [revisions]
  );

  const fieldCatalogReportType = selectedTemplate?.reportType ?? normalizeFormValue(reportTypeFilter) ?? undefined;

  useEffect(() => {
    loadTemplates();
    loadOverrides();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token, officeId]);

  useEffect(() => {
    if (!selectedTemplateId) {
      setRevisions([]);
      return;
    }
    loadRevisions(selectedTemplateId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedTemplateId, token, officeId]);

  useEffect(() => {
    if (selectedTemplate?.reportType) {
      setOverrideReportType(selectedTemplate.reportType);
    }
  }, [selectedTemplate?.id, selectedTemplate?.reportType]);

  useEffect(() => {
    loadFieldCatalog(fieldCatalogReportType);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token, officeId, fieldCatalogReportType]);

  useEffect(() => {
    if (publishedRevisions.length > 0 && !publishedRevisions.some((revision) => String(revision.id) === overrideRevisionId)) {
      setOverrideRevisionId(String(publishedRevisions[0].id));
    }
  }, [publishedRevisions, overrideRevisionId]);

  async function loadTemplates(nextReportType = reportTypeFilter) {
    setLoading(true);
    setError(null);
    try {
      const next = await getDocumentTemplates(token, officeId, normalizeFormValue(nextReportType) ?? undefined);
      setTemplates(next);
      setSelectedTemplateId((current) =>
        next.some((template) => template.id === current) ? current : next[0]?.id ?? null
      );
    } catch (err) {
      setError(err instanceof Error ? err.message : "템플릿 목록을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }

  async function loadRevisions(templateId = selectedTemplateId) {
    if (!templateId) {
      return;
    }
    setError(null);
    try {
      setRevisions(await getDocumentTemplateRevisions(token, officeId, templateId));
    } catch (err) {
      setError(err instanceof Error ? err.message : "템플릿 리비전을 불러오지 못했습니다.");
    }
  }

  async function loadFieldCatalog(reportType?: string) {
    setError(null);
    try {
      setFieldCatalog(await getDocumentTemplateFields(token, officeId, reportType));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Template field catalog could not be loaded.");
    }
  }

  async function loadOverrides() {
    setError(null);
    try {
      setOverrides(await getOfficeConfigOverrides(token, officeId));
    } catch (err) {
      setError(err instanceof Error ? err.message : "오버라이드 목록을 불러오지 못했습니다.");
    }
  }

  async function refreshAll() {
    await Promise.all([loadTemplates(), loadOverrides(), loadFieldCatalog(fieldCatalogReportType)]);
    if (selectedTemplateId) {
      await loadRevisions(selectedTemplateId);
    }
  }

  async function submitTemplate(event: FormEvent) {
    event.preventDefault();
    setBusyAction("create-template");
    setError(null);
    setNotice(null);
    try {
      const created = await createDocumentTemplate(token, officeId, {
        code: templateCode,
        name: templateName,
        reportType: normalizeFormValue(templateReportType)
      });
      setTemplateCode("");
      setTemplateName("");
      setTemplateReportType("");
      setSelectedTemplateId(created.id);
      await loadTemplates();
      setNotice("템플릿 정의를 생성했습니다.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "템플릿 정의를 생성하지 못했습니다.");
    } finally {
      setBusyAction(null);
    }
  }

  async function submitRevision(event: FormEvent) {
    event.preventDefault();
    if (!selectedTemplateId) {
      return;
    }
    setBusyAction("create-revision");
    setError(null);
    setNotice(null);
    try {
      const created = await createDocumentTemplateRevision(token, officeId, selectedTemplateId, {
        schema: parseJsonObject(schemaText, "schema"),
        composePolicy: parseJsonObject(composePolicyText, "composePolicy"),
        aiPrompts: parseJsonObject(aiPromptsText, "aiPrompts")
      });
      setRevisions((current) => [created, ...current]);
      setNotice("새 리비전을 만들었습니다. DOCX 업로드 후 게시할 수 있습니다.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "리비전을 생성하지 못했습니다.");
    } finally {
      setBusyAction(null);
    }
  }

  async function uploadRevisionContent(revision: DocumentTemplateRevision, files: FileList | null) {
    const file = files?.item(0);
    if (!file) {
      return;
    }
    if (!file.name.toLowerCase().endsWith(".docx")) {
      setError("DOCX 파일만 업로드할 수 있습니다.");
      return;
    }
    setBusyAction(`upload-${revision.id}`);
    setError(null);
    setNotice(null);
    try {
      const updated = await uploadDocumentTemplateRevisionContent(token, officeId, revision.id, file);
      replaceRevision(updated);
      setNotice(`v${updated.version} 템플릿 파일을 업로드했습니다.`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "템플릿 파일을 업로드하지 못했습니다.");
    } finally {
      setBusyAction(null);
    }
  }

  async function publishRevision(revision: DocumentTemplateRevision) {
    setBusyAction(`publish-${revision.id}`);
    setError(null);
    setNotice(null);
    try {
      const updated = await publishDocumentTemplateRevision(token, officeId, revision.id);
      replaceRevision(updated);
      setOverrideRevisionId(String(updated.id));
      setNotice(`v${updated.version} 리비전을 게시했습니다.`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "리비전을 게시하지 못했습니다.");
    } finally {
      setBusyAction(null);
    }
  }

  async function downloadRevision(revision: DocumentTemplateRevision) {
    setBusyAction(`download-${revision.id}`);
    setError(null);
    try {
      const blob = await downloadDocumentTemplateRevisionContent(token, officeId, revision.id);
      downloadBlob(blob, filenameFromStorageRef(revision.templateStorageRef) ?? `template-v${revision.version}.docx`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "템플릿 파일을 다운로드하지 못했습니다.");
    } finally {
      setBusyAction(null);
    }
  }

  async function copyTemplatePlaceholder(field: TemplateFieldDefinition) {
    await navigator.clipboard.writeText(`\${${field.key}}`);
      setNotice(`\${${field.key}} 플레이스홀더를 복사했습니다.`);
  }

  async function submitOverride(event: FormEvent) {
    event.preventDefault();
    const reportType = normalizeFormValue(overrideReportType);
    const revisionId = Number(overrideRevisionId);
    if (!reportType || !revisionId) {
      setError("보고서 유형과 게시된 템플릿 리비전을 선택해야 합니다.");
      return;
    }
    setBusyAction("save-override");
    setError(null);
    setNotice(null);
    try {
      await updateOfficeConfigOverride(token, officeId, reportType, { templateRevisionId: revisionId });
      await loadOverrides();
      setNotice(`${reportType.toUpperCase()} 오버라이드를 저장했습니다.`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "오버라이드를 저장하지 못했습니다.");
    } finally {
      setBusyAction(null);
    }
  }

  function replaceRevision(updated: DocumentTemplateRevision) {
    setRevisions((current) => current.map((revision) => (revision.id === updated.id ? updated : revision)));
  }

  return (
    <div className="view-stack">
      {notice ? <InlineNotice message={notice} /> : null}
      {error ? <InlineAlert message={error} /> : null}

      <div className="config-grid">
        <Panel
          title="템플릿 정의"
          icon={<FileText size={18} />}
          count={templates.length}
          action={
            <button className="icon-button" onClick={refreshAll} type="button" title="새로고침" aria-label="새로고침">
              {loading ? <Loader2 className="spin" size={18} /> : <RefreshCcw size={18} />}
            </button>
          }
        >
          <div className="config-panel-body">
            <div className="filter-row">
              <label className="filter-select wide">
                <span>보고서 유형</span>
                <input
                  onChange={(event) => setReportTypeFilter(event.target.value)}
                  placeholder="CONSTRUCTION_DAILY_SUPERVISION_LOG"
                  type="text"
                  value={reportTypeFilter}
                />
              </label>
              <button className="button" onClick={() => loadTemplates()} type="button">
                조회
              </button>
            </div>

            <form className="config-form" onSubmit={submitTemplate}>
              <label>
                코드
                <input
                  onChange={(event) => setTemplateCode(event.target.value)}
                  placeholder="DAILY_TEMPLATE"
                  required
                  type="text"
                  value={templateCode}
                />
              </label>
              <label>
                이름
                <input
                  onChange={(event) => setTemplateName(event.target.value)}
                  placeholder="감리일지 기본 템플릿"
                  required
                  type="text"
                  value={templateName}
                />
              </label>
              <label>
                보고서 유형
                <input
                  onChange={(event) => setTemplateReportType(event.target.value)}
                  placeholder="CONSTRUCTION_DAILY_SUPERVISION_LOG"
                  type="text"
                  value={templateReportType}
                />
              </label>
              <button className="button primary" disabled={busyAction === "create-template"} type="submit">
                {busyAction === "create-template" ? <Loader2 className="spin" size={16} /> : <Plus size={16} />}
                생성
              </button>
            </form>

            <div className="selectable-list">
              {templates.length === 0 ? <EmptyState message="템플릿 정의가 없습니다." /> : null}
              {templates.map((template) => (
                <button
                  className={template.id === selectedTemplateId ? "selectable-row active" : "selectable-row"}
                  key={template.id}
                  onClick={() => setSelectedTemplateId(template.id)}
                  type="button"
                >
                  <CellTitle title={template.name} subtitle={`${template.code} · ${template.reportType ?? "전체"}`} />
                  <StatusBadge status={template.status} />
                </button>
              ))}
            </div>
          </div>
        </Panel>

        <Panel
          title="리비전 / DOCX"
          icon={<Upload size={18} />}
          count={revisions.length}
          action={selectedTemplate ? <span className="panel-context">{selectedTemplate.code}</span> : null}
        >
          <div className="config-panel-body">
            {!selectedTemplate ? (
              <EmptyState message="템플릿을 선택하세요." />
            ) : (
              <>
                <form className="revision-form" onSubmit={submitRevision}>
                  <label>
                    Schema JSON
                    <textarea onChange={(event) => setSchemaText(event.target.value)} value={schemaText} />
                  </label>
                  <label>
                    Compose Policy JSON
                    <textarea onChange={(event) => setComposePolicyText(event.target.value)} value={composePolicyText} />
                  </label>
                  <label>
                    AI Prompts JSON
                    <textarea onChange={(event) => setAiPromptsText(event.target.value)} value={aiPromptsText} />
                  </label>
                  <button className="button primary" disabled={busyAction === "create-revision"} type="submit">
                    {busyAction === "create-revision" ? <Loader2 className="spin" size={16} /> : <Plus size={16} />}
                    리비전 생성
                  </button>
                </form>

                <div className="revision-grid">
                  {revisions.length === 0 ? <EmptyState message="리비전이 없습니다." /> : null}
                  {revisions.map((revision) => {
                    const hasContent = Boolean(revision.templateStorageRef);
                    const isDraft = revision.status === "DRAFT";
                    return (
                      <article className="revision-card" key={revision.id}>
                        <div className="revision-card-head">
                          <CellTitle title={`v${revision.version}`} subtitle={`리비전 #${revision.id}`} />
                          <StatusBadge status={revision.status} />
                        </div>
                        <dl className="revision-meta">
                          <div>
                            <dt>저장소</dt>
                            <dd>{displayLabel(revision.templateStorageKind)}</dd>
                          </div>
                          <div>
                            <dt>게시일</dt>
                            <dd>{formatDate(revision.publishedAt)}</dd>
                          </div>
                        </dl>
                        <code className="storage-ref">{revision.templateStorageRef ?? "DOCX 파일 없음"}</code>
                        <div className="card-actions">
                          <label className={isDraft ? "button file-button" : "button file-button disabled"}>
                            <Upload size={16} />
                            업로드
                            <input
                              accept=".docx,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                              disabled={!isDraft || busyAction === `upload-${revision.id}`}
                              onChange={(event) => {
                                uploadRevisionContent(revision, event.currentTarget.files);
                                event.currentTarget.value = "";
                              }}
                              type="file"
                            />
                          </label>
                          <button
                            className="button"
                            disabled={!hasContent || busyAction === `download-${revision.id}`}
                            onClick={() => downloadRevision(revision)}
                            type="button"
                          >
                            {busyAction === `download-${revision.id}` ? <Loader2 className="spin" size={16} /> : <Download size={16} />}
                            다운로드
                          </button>
                          <button
                            className="button primary"
                            disabled={!isDraft || !hasContent || busyAction === `publish-${revision.id}`}
                            onClick={() => publishRevision(revision)}
                            type="button"
                          >
                            {busyAction === `publish-${revision.id}` ? <Loader2 className="spin" size={16} /> : <Send size={16} />}
                            게시
                          </button>
                        </div>
                      </article>
                    );
                  })}
                </div>
              </>
            )}
          </div>
        </Panel>
      </div>

      <Panel
        title="템플릿 필드 카탈로그"
        icon={<Copy size={18} />}
        count={fieldCatalog?.fields.length ?? 0}
        action={<span className="panel-context">{fieldCatalog?.reportType ?? "ALL_REPORT_TYPES"}</span>}
      >
        <div className="config-panel-body">
          {fieldCatalog?.presets.length ? (
            <div className="template-preset-list">
              {fieldCatalog.presets.map((preset) => (
                <article className="template-preset-card" key={preset.code}>
                  <div>
                    <strong>{preset.title}</strong>
                    <span>{preset.code}</span>
                  </div>
                  <p>{preset.description}</p>
                  <div className="template-preset-meta">
                    <span>{displayLabel(preset.templateKind)}</span>
                    <span>{displayLabel(preset.customizationPolicy)}</span>
                    <span>{displayLabel(preset.renderingPolicy)}</span>
                  </div>
                  <code>{preset.recommendedFields.map((field) => `\${${field}}`).join(" ")}</code>
                </article>
              ))}
            </div>
          ) : null}

          <div className="template-field-grid">
            {fieldCatalog?.fields.length ? null : <EmptyState message="등록된 템플릿 필드가 없습니다." />}
            {fieldCatalog?.fields.map((field) => (
              <article className="template-field-card" key={field.key}>
                <div className="template-field-card-head">
                  <div>
                    <strong>{field.label}</strong>
                    <code>{`\${${field.key}}`}</code>
                  </div>
                  <button
                    className="icon-button"
                    onClick={() => copyTemplatePlaceholder(field)}
                    title="플레이스홀더 복사"
                    type="button"
                    aria-label={`${field.key} 플레이스홀더 복사`}
                  >
                    <Copy size={16} />
                  </button>
                </div>
                <p>{field.description}</p>
                <dl>
                  <div>
                    <dt>분류</dt>
                    <dd>{displayLabel(field.category)}</dd>
                  </div>
                  <div>
                    <dt>원천</dt>
                    <dd>{displayLabel(field.source)}</dd>
                  </div>
                  <div>
                    <dt>예시</dt>
                    <dd>{field.example}</dd>
                  </div>
                </dl>
              </article>
            ))}
          </div>
        </div>
      </Panel>

      <Panel title="사무소 오버라이드" icon={<ShieldCheck size={18} />} count={overrides.length}>
        <div className="config-panel-body">
          <form className="override-form" onSubmit={submitOverride}>
            <label>
              보고서 유형
              <input
                onChange={(event) => setOverrideReportType(event.target.value)}
                placeholder="CONSTRUCTION_DAILY_SUPERVISION_LOG"
                required
                type="text"
                value={overrideReportType}
              />
            </label>
            <label>
              게시된 템플릿 리비전
              <select
                onChange={(event) => setOverrideRevisionId(event.target.value)}
                required
                value={overrideRevisionId}
              >
                {publishedRevisions.length === 0 ? <option value="">게시된 리비전 없음</option> : null}
                {publishedRevisions.map((revision) => (
                  <option key={revision.id} value={revision.id}>
                    {selectedTemplate?.code ?? "TEMPLATE"} v{revision.version} · #{revision.id}
                  </option>
                ))}
              </select>
            </label>
            <button
              className="button primary"
              disabled={publishedRevisions.length === 0 || busyAction === "save-override"}
              type="submit"
            >
              {busyAction === "save-override" ? <Loader2 className="spin" size={16} /> : <ShieldCheck size={16} />}
              저장
            </button>
          </form>

          <Table
            columns={["보고서 유형", "상태", "템플릿", "리비전", "소스", "수정"]}
            empty="오버라이드가 없습니다."
            rows={overrides.map((override) => [
              override.reportType,
              <StatusBadge key="status" status={override.status} />,
              override.template.code ?? "-",
              override.template.revisionId ? `#${override.template.revisionId} · v${override.template.version}` : "-",
              displayLabel(override.template.source),
              formatDate(override.updatedAt)
            ])}
          />
        </div>
      </Panel>
    </div>
  );
}

function PhotosView({
  photos,
  photoStatus,
  pickupStatus,
  setPhotoStatus,
  setPickupStatus
}: {
  photos: Photo[];
  photoStatus: string;
  pickupStatus: string;
  setPhotoStatus: (status: string) => void;
  setPickupStatus: (status: string) => void;
}) {
  return (
    <Panel
      title="사진 파이프라인"
      icon={<Camera size={18} />}
      count={photos.length}
      action={
        <div className="filter-row">
          <FilterSelect label="업로드" options={photoFilterOptions} value={photoStatus} onChange={setPhotoStatus} />
          <FilterSelect label="원본 회수" options={pickupFilterOptions} value={pickupStatus} onChange={setPickupStatus} />
        </div>
      }
    >
      <Table
        columns={["사진", "상태", "원본 회수", "리포트", "크기", "파생 파일", "GPS", "생성"]}
        empty="표시할 사진이 없습니다."
        rows={photos.map((photo) => [
          <CellTitle key="photo" title={`사진 #${photo.id}`} subtitle={photo.stepCode ?? "step 없음"} />,
          <StatusBadge key="status" status={photo.status} />,
          <StatusBadge key="pickup" status={photo.originalPickupStatus} />,
          photo.reportId ? `#${photo.reportId}` : "-",
          photo.width && photo.height ? `${photo.width} x ${photo.height}` : formatBytes(photo.bytes),
          photo.assets.map((asset) => `${displayLabel(asset.assetType)}:${displayLabel(asset.status)}`).join(", ") || "-",
          photo.hasGps ? "있음" : "없음",
          formatDate(photo.createdAt)
        ])}
      />
    </Panel>
  );
}

function DeliveriesView({
  deliveries,
  status,
  setStatus
}: {
  deliveries: DocumentDelivery[];
  status: string;
  setStatus: (status: string) => void;
}) {
  return (
    <Panel
      title="문서 전달 요청"
      icon={<Truck size={18} />}
      count={deliveries.length}
      action={<FilterSelect label="상태" options={deliveryFilterOptions} value={status} onChange={setStatus} />}
    >
      <Table
        columns={["전달", "상태", "채널", "작업", "산출물", "Agent 명령", "요청", "오류"]}
        empty="표시할 전달 요청이 없습니다."
        rows={deliveries.map((delivery) => [
          <CellTitle key="delivery" title={`전달 #${delivery.id}`} subtitle={delivery.preparedStorageKind ? displayLabel(delivery.preparedStorageKind) : "준비 전"} />,
          <StatusBadge key="status" status={delivery.status} />,
          displayLabel(delivery.channel),
          `#${delivery.documentJobId}`,
          delivery.artifactId ? `#${delivery.artifactId}` : "-",
          delivery.agentCommandId ? `#${delivery.agentCommandId}` : "-",
          formatDate(delivery.requestedAt),
          delivery.errorMessage ?? "-"
        ])}
      />
    </Panel>
  );
}

function EventsView({ events }: { events: OperationEvent[] }) {
  return (
    <Panel title="운영 이벤트" icon={<Activity size={18} />} count={events.length}>
      <div className="event-timeline">
        {events.length === 0 ? <EmptyState message="표시할 이벤트가 없습니다." /> : null}
        {events.map((event) => (
          <article className="event-item" key={event.id}>
            <StatusIcon status={event.severity} />
            <div>
              <div className="event-head">
                <strong>{event.eventType}</strong>
                <span>{formatDate(event.createdAt)}</span>
              </div>
              <p>{event.message}</p>
              <small>
                {event.workflowType ? displayLabel(event.workflowType) : "워크플로우 없음"} · {event.resourceType ? displayLabel(event.resourceType) : "리소스 없음"}{" "}
                {event.resourceId ? `#${event.resourceId}` : ""}
              </small>
            </div>
          </article>
        ))}
      </div>
    </Panel>
  );
}

function OfficeStrip({ office }: { office: Office }) {
  return (
    <div className="office-strip">
      <div>
        <strong>{office.displayName}</strong>
        <span>{office.officeCode}</span>
      </div>
      <StatusBadge status={office.role} />
      <span>{office.planCode}</span>
      <span>{displayLabel(office.type)}</span>
    </div>
  );
}

function MetricCard({
  icon,
  label,
  value,
  detail,
  tone
}: {
  icon: ReactNode;
  label: string;
  value: ReactNode;
  detail: string;
  tone: "green" | "blue" | "amber" | "red" | "slate";
}) {
  return (
    <div className={`metric-card ${tone}`}>
      <div className="metric-icon">{icon}</div>
      <span>{label}</span>
      <strong>{typeof value === "number" ? value.toLocaleString() : value}</strong>
      <small>{detail}</small>
    </div>
  );
}

function Panel({
  title,
  icon,
  count,
  action,
  children
}: {
  title: string;
  icon: ReactNode;
  count?: number;
  action?: ReactNode;
  children: ReactNode;
}) {
  return (
    <section className="panel">
      <header className="panel-header">
        <div>
          {icon}
          <h2>{title}</h2>
          {count !== undefined ? <span className="count-pill">{count}</span> : null}
        </div>
        {action}
      </header>
      {children}
    </section>
  );
}

function Table({ columns, rows, empty }: { columns: string[]; rows: ReactNode[][]; empty: string }) {
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            {columns.map((column) => (
              <th key={column}>{column}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.length === 0 ? (
            <tr>
              <td colSpan={columns.length}>
                <EmptyState message={empty} />
              </td>
            </tr>
          ) : (
            rows.map((row, rowIndex) => (
              <tr key={rowIndex}>
                {row.map((cell, cellIndex) => (
                  <td key={`${rowIndex}-${cellIndex}`}>{cell}</td>
                ))}
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  );
}

function CellTitle({ title, subtitle }: { title: string; subtitle?: string | null }) {
  return (
    <div className="cell-title">
      <strong>{title}</strong>
      {subtitle ? <span>{subtitle}</span> : null}
    </div>
  );
}

function StatusBadge({ status }: { status: string }) {
  return (
    <span className={`status-badge ${statusTone(status)}`} title={status}>
      {displayLabel(status)}
    </span>
  );
}

function FindingResolutionBadge({ status }: { status: PlatformReportPreflightFinding["resolutionStatus"] }) {
  if (status === "ACCEPTED") {
    return <span className="status-badge amber" title={status}>위험 수용</span>;
  }
  return <StatusBadge status={status} />;
}

function ProviderModeBadge({ provider }: { provider: AiProviderCredential }) {
  const fake = isFakeAiProvider(provider.providerCode);
  return (
    <span className={fake ? "provider-mode-badge fake" : "provider-mode-badge real"} title={provider.providerCode}>
      {fake ? "개발용 Fake" : "실제 Provider"}
    </span>
  );
}

function AiProviderConnectionTestCell({
  busy,
  provider,
  result,
  onTest
}: {
  busy: boolean;
  provider: AiProviderCredential;
  result?: AiProviderConnectionTestResult;
  onTest: (providerId: number) => Promise<void>;
}) {
  return (
    <div className="provider-test-cell">
      <button
        className="button compact"
        disabled={busy || !provider.defaultModel}
        onClick={() => onTest(provider.id)}
        title={provider.defaultModel ? "provider endpoint와 모델 호출을 짧게 테스트합니다." : "기본 모델을 먼저 입력하세요."}
        type="button"
      >
        <Activity size={15} />
        테스트
      </button>
      {result ? (
        <span className={result.success ? "provider-test-result success" : "provider-test-result fail"}>
          {result.success ? "성공" : "실패"}
          {result.latencyMs == null ? "" : ` · ${result.latencyMs}ms`}
        </span>
      ) : null}
      {result?.message ? <small title={result.message}>{result.message}</small> : null}
    </div>
  );
}

function StatusIcon({ status }: { status: string }) {
  const tone = statusTone(status);
  if (tone === "red") {
    return <XCircle className={`status-icon ${tone}`} size={18} />;
  }
  if (tone === "green") {
    return <CheckCircle2 className={`status-icon ${tone}`} size={18} />;
  }
  if (tone === "amber") {
    return <Clock3 className={`status-icon ${tone}`} size={18} />;
  }
  return <Activity className={`status-icon ${tone}`} size={18} />;
}

function Progress({ value }: { value: number }) {
  return (
    <div className="progress">
      <div style={{ width: `${Math.max(0, Math.min(100, value))}%` }} />
      <span>{value}%</span>
    </div>
  );
}

function FilterSelect({
  label,
  options,
  value,
  onChange
}: {
  label: string;
  options: string[];
  value: string;
  onChange: (value: string) => void;
}) {
  return (
    <label className="filter-select">
      <span>{label}</span>
      <select value={value} onChange={(event) => onChange(event.target.value)}>
        {options.map((option) => (
          <option key={option} value={option}>
            {displayLabel(option)}
          </option>
        ))}
      </select>
    </label>
  );
}

function StatusBars({ groups }: { groups: Array<[string, Record<string, number>]> }) {
  return (
    <div className="status-bars">
      {groups.map(([label, counts]) => {
        const total = Object.values(counts).reduce((sum, count) => sum + count, 0) || 1;
        return (
          <div className="status-bar-row" key={label}>
            <div>
              <strong>{label}</strong>
              <span>{Object.values(counts).reduce((sum, count) => sum + count, 0).toLocaleString()}건</span>
            </div>
            <div className="stacked-bar">
              {Object.entries(counts)
                .filter(([, count]) => count > 0)
                .map(([status, count]) => (
                  <span
                    className={statusTone(status)}
                    key={status}
                    style={{ width: `${Math.max(4, (count / total) * 100)}%` }}
                    title={`${displayLabel(status)}: ${count}`}
                  />
                ))}
            </div>
            <div className="status-chip-row">
              {Object.entries(counts).map(([status, count]) => (
                <span key={status}>
                  {displayLabel(status)} {count}
                </span>
              ))}
            </div>
          </div>
        );
      })}
    </div>
  );
}

type LegalAdminTabKey = "SYNC" | "DIGESTS" | "CHANGE_SETS" | "BINDINGS";

function PlatformLegalAdminPanel({
  data,
  accessToken,
  legalDigestAiDrafts,
  legalBindingAutoGenerateResult,
  loading,
  onLegalDigestAiDraft,
  onLoadLegalDigestAiDrafts,
  onApproveLegalDigestAiDraft,
  onRejectLegalDigestAiDraft,
  onApplyLegalDigestAiDraft,
  onLegalOpenDataSync,
  onLegalDigestRefresh,
  onCreateLegalDomainBinding,
  onUpdateLegalDomainBinding,
  onAutoGenerateConstructionSupervisionLegalBindings,
  onDeactivateLegalDomainBinding
}: {
  data: PlatformOpsData;
  accessToken: string;
  legalDigestAiDrafts: Record<number, LegalDigestAiDraft[]>;
  legalBindingAutoGenerateResult: LegalDomainBindingAutoGenerateResponse | null;
  loading: boolean;
  onLegalDigestAiDraft: (digestId: number) => void;
  onLoadLegalDigestAiDrafts: (digestId: number) => void;
  onApproveLegalDigestAiDraft: (digestId: number, draftId: number) => void;
  onRejectLegalDigestAiDraft: (digestId: number, draftId: number) => void;
  onApplyLegalDigestAiDraft: (digestId: number, draftId: number) => void;
  onLegalOpenDataSync: () => void;
  onLegalDigestRefresh: () => void;
  onCreateLegalDomainBinding: (body: LegalDomainBindingPayload) => Promise<void>;
  onUpdateLegalDomainBinding: (bindingId: number, body: LegalDomainBindingPayload) => Promise<void>;
  onAutoGenerateConstructionSupervisionLegalBindings: () => Promise<void>;
  onDeactivateLegalDomainBinding: (bindingId: number) => Promise<void>;
}) {
  const [selectedDigestId, setSelectedDigestId] = useState<number | null>(null);
  const selectedDigest = data.legalChangeDigests.find((digest) => digest.id === selectedDigestId) ?? null;
  const selectedAiDrafts = selectedDigest ? legalDigestAiDrafts[selectedDigest.id] ?? [] : [];
  const [activeTab, setActiveTab] = useState<LegalAdminTabKey>("SYNC");
  function selectDigest(digestId: number) {
    setSelectedDigestId(digestId);
    onLoadLegalDigestAiDrafts(digestId);
  }
  const tabs: Array<{ key: LegalAdminTabKey; label: string; count?: number }> = [
    { key: "SYNC", label: "동기화", count: data.legalSyncRuns.length },
    { key: "DIGESTS", label: "사용자용 변경사항", count: data.legalChangeDigests.length },
    { key: "CHANGE_SETS", label: "원천 기록", count: data.legalChangeSets.length },
    { key: "BINDINGS", label: "도메인 바인딩", count: data.legalDomainBindings.length }
  ];

  return (
    <div className="legal-admin-page">
      <div className="ai-observer-tabs legal-page-tabs">
        {tabs.map((tab) => (
          <button
            className={`ai-observer-tab${activeTab === tab.key ? " active" : ""}`}
            key={tab.key}
            onClick={() => setActiveTab(tab.key)}
            type="button"
          >
            {tab.label}
            <span>{tab.count ?? 0}</span>
          </button>
        ))}
      </div>

      {activeTab === "SYNC" ? (
        <div className="view-stack">
          <Panel
            title="법령 동기화"
            icon={<ShieldCheck size={18} />}
            action={
              <div className="panel-action-row">
                <button className="button primary" disabled={loading} onClick={onLegalOpenDataSync} type="button">
                  {loading ? <Loader2 className="spin" size={16} /> : <RefreshCcw size={16} />}
                  동기화
                </button>
                <button className="button" disabled={loading} onClick={onLegalDigestRefresh} type="button">
                  {loading ? <Loader2 className="spin" size={16} /> : <RefreshCcw size={16} />}
                  게시 요약 재생성
                </button>
              </div>
            }
          >
            <div className="metric-grid compact">
              <MetricCard icon={<Gauge size={20} />} label="동기화 Run" value={data.legalSyncRuns.length} detail="최근 50건" tone="blue" />
              <MetricCard icon={<Activity size={20} />} label="Change Set" value={data.legalChangeSets.length} detail="법령 원천 diff" tone="amber" />
              <MetricCard icon={<FileText size={20} />} label="게시 요약" value={data.legalChangeDigests.length} detail="사용자 노출 digest" tone="green" />
            </div>
            {data.legalOpenApiStatus ? (
              <>
                <div className="metric-grid compact">
                  <MetricCard
                    icon={data.legalOpenApiStatus.ready ? <CheckCircle2 size={20} /> : <XCircle size={20} />}
                    label="Open API"
                    value={data.legalOpenApiStatus.ready ? "READY" : "BLOCKED"}
                    detail={`${data.legalOpenApiStatus.sourceCode} / ${data.legalOpenApiStatus.enabled ? "enabled" : "disabled"}`}
                    tone={data.legalOpenApiStatus.ready ? "green" : "amber"}
                  />
                  <MetricCard
                    icon={data.legalOpenApiStatus.ocConfigured ? <CheckCircle2 size={20} /> : <AlertTriangle size={20} />}
                    label="OC Key"
                    value={data.legalOpenApiStatus.ocConfigured ? "설정됨" : "미설정"}
                    detail="원문 키는 노출하지 않음"
                    tone={data.legalOpenApiStatus.ocConfigured ? "green" : "red"}
                  />
                  <MetricCard
                    icon={<Clock3 size={20} />}
                    label="호출 간격"
                    value={`${data.legalOpenApiStatus.requestIntervalMs}ms`}
                    detail={`timeout ${data.legalOpenApiStatus.requestTimeoutMs}ms / retry ${data.legalOpenApiStatus.maxAttempts}`}
                    tone="slate"
                  />
                  <MetricCard
                    icon={<Gauge size={20} />}
                    label="예상 호출"
                    value={data.legalOpenApiStatus.estimatedRequestCount}
                    detail={`${data.legalOpenApiStatus.targetCount} targets / search+detail`}
                    tone="blue"
                  />
                </div>
                <Table
                  columns={["Target", "검색어", "예상 법령명", "Act Code", "Type"]}
                  empty="설정된 법령 Open API target이 없습니다."
                  rows={data.legalOpenApiStatus.targets.map((target) => [
                    target.target,
                    target.query,
                    target.expectedName,
                    target.actCode,
                    target.actType
                  ])}
                />
                <InlineNotice message={`법령 Open API base URL: ${data.legalOpenApiStatus.baseUrl} / User-Agent: ${data.legalOpenApiStatus.userAgent}`} />
              </>
            ) : (
              <InlineNotice message="법령 Open API 상태를 불러오지 못했습니다. 동기화 실행 전 설정 상태를 확인하세요." />
            )}
          </Panel>

          <Panel title="법령 동기화 Run" icon={<Clock3 size={18} />} count={data.legalSyncRuns.length}>
            <Table
              columns={["Run", "상태", "Source", "Trigger", "시작", "완료"]}
              empty="법령 동기화 Run이 없습니다."
              rows={data.legalSyncRuns.slice(0, 20).map((run) => [
                <CellTitle key="run" title={`Run #${run.id}`} subtitle={run.failureCode ?? "failure 없음"} />,
                <StatusBadge key="status" status={run.status} />,
                run.sourceCode,
                displayLabel(run.triggerType),
                formatDate(run.startedAt),
                formatDate(run.completedAt)
              ])}
            />
          </Panel>
        </div>
      ) : null}

      {activeTab === "DIGESTS" ? (
        selectedDigest ? (
          <Panel
            title="법령 변경사항 상세"
            icon={<FileText size={18} />}
            action={
              <div className="panel-action-row">
                <button className="button compact" disabled={loading} onClick={() => onLegalDigestAiDraft(selectedDigest.id)} type="button">
                  {loading ? <Loader2 className="spin" size={15} /> : <FileText size={15} />}
                  AI 초안 생성
                </button>
                <button className="button compact" onClick={() => setSelectedDigestId(null)} type="button">
                  <ArrowLeft size={15} />
                  목록으로
                </button>
              </div>
            }
          >
            <LegalDigestDetail
              aiDrafts={selectedAiDrafts}
              digest={selectedDigest}
              loading={loading}
              onApproveAiDraft={(draftId) => onApproveLegalDigestAiDraft(selectedDigest.id, draftId)}
              onRejectAiDraft={(draftId) => onRejectLegalDigestAiDraft(selectedDigest.id, draftId)}
              onApplyAiDraft={(draftId) => onApplyLegalDigestAiDraft(selectedDigest.id, draftId)}
            />
          </Panel>
        ) : (
          <Panel title="사용자용 법령 변경사항" icon={<FileText size={18} />} count={data.legalChangeDigests.length}>
            {data.legalChangeDigests.length === 0 ? (
              <EmptyState message="게시된 법령 변경사항이 없습니다." />
            ) : (
              <div className="legal-admin-digest-list">
                {data.legalChangeDigests.slice(0, 20).map((digest) => (
                  <button className="legal-admin-digest-item" key={digest.id} onClick={() => selectDigest(digest.id)} type="button">
                    <span>{formatDate(digest.publishedAt ?? digest.detectedAt)}</span>
                    <strong>{digest.title}</strong>
                    <small>{digest.summary}</small>
                    <em>
                      Digest #{digest.id} / Change Set #{digest.changeSetId} / 시행일 {digest.effectiveDate ?? "미정"}
                    </em>
                  </button>
                ))}
              </div>
            )}
          </Panel>
        )
      ) : null}

      {activeTab === "CHANGE_SETS" ? (
        <Panel title="법령 변경 원천 기록" icon={<Activity size={18} />} count={data.legalChangeSets.length}>
          <Table
            columns={["Change Set", "상태", "Act", "시행일", "감지", "요약"]}
            empty="법령 변경 원천 기록이 없습니다."
            rows={data.legalChangeSets.slice(0, 50).map((changeSet) => [
              <CellTitle key="change-set" title={`Change Set #${changeSet.id}`} subtitle={`run #${changeSet.syncRunId ?? "-"} / version #${changeSet.newVersionId}`} />,
              <StatusBadge key="status" status={changeSet.status} />,
              `#${changeSet.actId}`,
              changeSet.effectiveDate ?? "-",
              formatDate(changeSet.detectedAt),
              changeSet.summary
            ])}
          />
        </Panel>
      ) : null}

      {activeTab === "BINDINGS" ? (
        <LegalDomainBindingPanel
          accessToken={accessToken}
          bindings={data.legalDomainBindings}
          coverage={data.legalDomainBindingCoverage}
          busy={loading}
          autoGenerateResult={legalBindingAutoGenerateResult}
          onCreate={onCreateLegalDomainBinding}
          onUpdate={onUpdateLegalDomainBinding}
          onAutoGenerate={onAutoGenerateConstructionSupervisionLegalBindings}
          onDeactivate={onDeactivateLegalDomainBinding}
        />
      ) : null}
    </div>
  );
}

function LegalDomainBindingPanel({
  accessToken,
  bindings,
  coverage,
  busy,
  autoGenerateResult,
  onCreate,
  onUpdate,
  onAutoGenerate,
  onDeactivate
}: {
  accessToken: string;
  bindings: LegalDomainBinding[];
  coverage: LegalDomainBindingCoverage | null;
  busy: boolean;
  autoGenerateResult: LegalDomainBindingAutoGenerateResponse | null;
  onCreate: (body: LegalDomainBindingPayload) => Promise<void>;
  onUpdate: (bindingId: number, body: LegalDomainBindingPayload) => Promise<void>;
  onAutoGenerate: () => Promise<void>;
  onDeactivate: (bindingId: number) => Promise<void>;
}) {
  const [selectedBindingId, setSelectedBindingId] = useState<number | null>(null);
  const [mode, setMode] = useState<"LIST" | "CREATE" | "EDIT">("LIST");
  const activeCount = bindings.filter((binding) => binding.status === "ACTIVE").length;
  const selectedBinding = bindings.find((binding) => binding.id === selectedBindingId) ?? null;
  useEffect(() => {
    if (mode === "EDIT" && selectedBindingId && !selectedBinding) {
      setSelectedBindingId(null);
      setMode("LIST");
    }
  }, [mode, selectedBinding, selectedBindingId]);
  function openEdit(bindingId: number) {
    setSelectedBindingId(bindingId);
    setMode("EDIT");
  }
  function openCreate() {
    setSelectedBindingId(null);
    setMode("CREATE");
  }
  function backToList() {
    setSelectedBindingId(null);
    setMode("LIST");
  }
  if (mode === "EDIT" && selectedBinding) {
    return (
      <div className="view-stack">
        <Panel title="도메인 바인딩 수정" icon={<ShieldCheck size={18} />} count={selectedBinding.id}>
          <div className="legal-binding-detail-head">
            <button className="button" disabled={busy} onClick={backToList} type="button">
              <ArrowLeft size={16} />
              목록으로
            </button>
            <CellTitle
              title={selectedBinding.bindingKey}
              subtitle={`#${selectedBinding.id} / ${selectedBinding.bindingScope} / ${selectedBinding.status}`}
            />
          </div>
          <InlineNotice message="이 화면에서 수정한 연결은 다음 Engine/preflight 검토부터 적용됩니다. 법령 원문은 수정하지 않고 업무 항목과 조문 연결만 바꿉니다." />
          <LegalDomainBindingForm
            accessToken={accessToken}
            busy={busy}
            initialBinding={selectedBinding}
            key={selectedBinding.id}
            onCancel={backToList}
            onSubmit={async (body) => {
              await onUpdate(selectedBinding.id, body);
              backToList();
            }}
          />
        </Panel>
      </div>
    );
  }
  if (mode === "CREATE") {
    return (
      <div className="view-stack">
        <Panel title="새 도메인 바인딩 추가" icon={<ShieldCheck size={18} />} count={activeCount}>
          <div className="legal-binding-detail-head">
            <button className="button" disabled={busy} onClick={backToList} type="button">
              <ArrowLeft size={16} />
              목록으로
            </button>
            <CellTitle
              title="수동 바인딩 추가"
              subtitle="자동 생성 후 예외/정정이 필요한 항목만 추가하세요."
            />
          </div>
          <InlineNotice message="카탈로그 항목이나 리포트 유형을 synchronized 법령 조문에 연결합니다. 생성된 연결은 preflight/Engine 근거 후보로 사용됩니다." />
          <LegalDomainBindingForm
            accessToken={accessToken}
            busy={busy}
            key="new"
            onCancel={backToList}
            onSubmit={async (body) => {
              await onCreate(body);
              backToList();
            }}
          />
        </Panel>
      </div>
    );
  }
  return (
    <div className="view-stack">
      {coverage ? <LegalDomainBindingCoverageSummary coverage={coverage} /> : null}
      <Panel title="바인딩 목록" icon={<FileText size={18} />} count={bindings.length}>
        <InlineNotice message="수동 바인딩은 예외/정정용입니다. 기본 공사감리 카탈로그 연결은 자동 생성으로 깔고, 운영자는 중요한 항목만 수정합니다." />
        <div className="legal-binding-toolbar">
          <button className="button primary" disabled={busy} onClick={onAutoGenerate} type="button">
            {busy ? <Loader2 className="spin" size={16} /> : <RefreshCcw size={16} />}
            공사감리 기본 바인딩 자동 생성
          </button>
          <button className="button" disabled={busy} onClick={openCreate} type="button">
            <Plus size={16} />
            새 바인딩 추가
          </button>
          <span className="muted">기존 바인딩은 유지하고 없는 카탈로그 항목만 추가합니다.</span>
        </div>
        {autoGenerateResult ? (
          <div className="legal-binding-result">
            <strong>{autoGenerateResult.message}</strong>
            <span>
              생성 {autoGenerateResult.createdCount.toLocaleString()}건 · 건너뜀 {autoGenerateResult.skippedCount.toLocaleString()}건 ·
              카탈로그 {autoGenerateResult.catalogItemCount.toLocaleString()}개
            </span>
            <small>
              대표 근거: {autoGenerateResult.primaryReference || "-"} / {autoGenerateResult.supportingReference || "-"}
            </small>
          </div>
        ) : null}
        <Table
          columns={["바인딩", "법령/조문", "업무 연결", "관련도", "상태", "기간", "작업"]}
          empty="등록된 법령 도메인 바인딩이 없습니다."
          rows={bindings.map((binding) => [
            <CellTitle
              key="binding"
              title={legalBindingDomainTitle(binding)}
              subtitle={`${binding.bindingScope} / ${binding.bindingKey} / #${binding.id}`}
            />,
            <CellTitle
              key="law"
              title={`${binding.actName ?? binding.actCode ?? `Act #${binding.actId}`}`}
              subtitle={binding.articleNo ? `${binding.articleNo} ${binding.articleTitle ?? ""}` : "법령 전체"}
            />,
            <CellTitle
              key="domain"
              title={legalBindingWorkLabel(binding)}
              subtitle={legalBindingCodeLine(binding)}
            />,
            displayLabel(binding.relevance),
            <StatusBadge key="status" status={binding.status} />,
            `${binding.effectiveFrom ?? "-"} ~ ${binding.effectiveTo ?? "-"}`,
            <div className="table-action-group" key="actions">
              <button className="button compact" disabled={busy} onClick={() => openEdit(binding.id)} type="button">
                수정
              </button>
              {binding.status === "ACTIVE" ? (
                <button className="button compact danger" disabled={busy} onClick={() => onDeactivate(binding.id)} type="button">
                  비활성화
                </button>
              ) : null}
            </div>
          ])}
        />
      </Panel>
    </div>
  );
}

function LegalDomainBindingCoverageSummary({ coverage }: { coverage: LegalDomainBindingCoverage }) {
  const coveragePercent = coverage.catalogItemCount > 0
    ? Math.round((coverage.activeBoundItemCount / coverage.catalogItemCount) * 100)
    : 0;
  const missingSamples = coverage.missingItems.slice(0, 8);
  return (
    <Panel title="공사감리 바인딩 커버리지" icon={<ShieldCheck size={18} />} count={coverage.activeBoundItemCount}>
      <InlineNotice
        message={`${coverage.catalogName || coverage.catalogCode} v${coverage.catalogVersion} 기준입니다. 저장값은 코드이고, 화면 설명은 카탈로그에서 계산해 함께 표시합니다.`}
      />
      <div className="metric-grid compact">
        <MetricCard icon={<FileText size={20} />} label="카탈로그 항목" value={coverage.catalogItemCount} detail="구조화 업무 데이터" tone="blue" />
        <MetricCard icon={<CheckCircle2 size={20} />} label="활성 연결" value={coverage.activeBoundItemCount} detail={`전체의 ${coveragePercent}%`} tone="green" />
        <MetricCard icon={<AlertTriangle size={20} />} label="누락 항목" value={coverage.missingItemCount} detail="활성 바인딩 없음" tone={coverage.missingItemCount > 0 ? "amber" : "green"} />
        <MetricCard icon={<ShieldCheck size={20} />} label="수동 보정" value={coverage.manualBindingCount} detail={`자동 ${coverage.autoGeneratedBindingCount} / 전체 ${coverage.totalBindingCount}`} tone="slate" />
      </div>
      {missingSamples.length > 0 ? (
        <div className="legal-binding-coverage-list">
          <strong>활성 바인딩이 없는 항목 예시</strong>
          {missingSamples.map((item) => (
            <div className="legal-binding-coverage-item" key={item.checklistItemCode}>
              <span>{item.tradeName} / {item.processName}</span>
              <b>{item.checklistItemName}</b>
              <small>{item.checklistItemCode}</small>
            </div>
          ))}
        </div>
      ) : (
        <InlineNotice message="공사감리 카탈로그 항목 전체에 활성 법령 바인딩이 있습니다." />
      )}
    </Panel>
  );
}

function legalBindingDomainTitle(binding: LegalDomainBinding) {
  return binding.bindingDisplayName || legalBindingWorkLabel(binding) || binding.bindingKey;
}

function legalBindingWorkLabel(binding: LegalDomainBinding) {
  if (binding.checklistItemName) {
    return binding.checklistItemName;
  }
  if (binding.reportTypeLabel) {
    return binding.reportTypeLabel;
  }
  return binding.checklistItemCode ?? binding.reportType ?? "-";
}

function legalBindingCodeLine(binding: LegalDomainBinding) {
  const domainParts = [
    binding.tradeCode,
    binding.processCode,
    binding.checklistItemCode
  ].filter(Boolean);
  const catalogParts = [
    binding.catalogCode,
    binding.catalogVersion ? `v${binding.catalogVersion}` : null
  ].filter(Boolean);
  if (domainParts.length > 0 && catalogParts.length > 0) {
    return `${domainParts.join(" / ")} · ${catalogParts.join(" / ")}`;
  }
  if (domainParts.length > 0) {
    return domainParts.join(" / ");
  }
  if (binding.reportType) {
    return binding.reportType;
  }
  return catalogParts.join(" / ") || "-";
}

function LegalDomainBindingForm({
  accessToken,
  busy,
  initialBinding,
  onCancel,
  onSubmit
}: {
  accessToken: string;
  busy: boolean;
  initialBinding?: LegalDomainBinding | null;
  onCancel?: () => void;
  onSubmit: (body: LegalDomainBindingPayload) => Promise<void>;
}) {
  const defaultCatalogCode = "CONSTRUCTION_SUPERVISION_CHECKLIST_2020_12_24";
  const defaultCatalogVersion = "2";
  const [bindingScope, setBindingScope] = useState("CATALOG_ITEM");
  const [bindingKey, setBindingKey] = useState("");
  const [actId, setActId] = useState("");
  const [articleId, setArticleId] = useState("");
  const [reportType, setReportType] = useState("CONSTRUCTION_DAILY_SUPERVISION_LOG");
  const [catalogCode, setCatalogCode] = useState(defaultCatalogCode);
  const [catalogVersion, setCatalogVersion] = useState(defaultCatalogVersion);
  const [checklistItemCode, setChecklistItemCode] = useState("");
  const [relevance, setRelevance] = useState("PRIMARY");
  const [status, setStatus] = useState("ACTIVE");
  const [effectiveFrom, setEffectiveFrom] = useState("");
  const [effectiveTo, setEffectiveTo] = useState("");
  const [notes, setNotes] = useState("");
  const [lawQuery, setLawQuery] = useState("감리");
  const [lawResults, setLawResults] = useState<LegalLawSearchResult[]>([]);
  const [lawSearchBusy, setLawSearchBusy] = useState(false);
  const [selectedLaw, setSelectedLaw] = useState<LegalLawSearchResult | null>(null);

  useEffect(() => {
    if (!initialBinding) {
      setBindingScope("CATALOG_ITEM");
      setBindingKey("");
      setActId("");
      setArticleId("");
      setReportType("CONSTRUCTION_DAILY_SUPERVISION_LOG");
      setCatalogCode(defaultCatalogCode);
      setCatalogVersion(defaultCatalogVersion);
      setChecklistItemCode("");
      setRelevance("PRIMARY");
      setStatus("ACTIVE");
      setEffectiveFrom("");
      setEffectiveTo("");
      setNotes("");
      setSelectedLaw(null);
      return;
    }
    setBindingScope(initialBinding.bindingScope || "CATALOG_ITEM");
    setBindingKey(initialBinding.bindingKey || "");
    setActId(String(initialBinding.actId));
    setArticleId(initialBinding.articleId ? String(initialBinding.articleId) : "");
    setReportType(initialBinding.reportType ?? "");
    setCatalogCode(initialBinding.catalogCode ?? "");
    setCatalogVersion(initialBinding.catalogVersion ? String(initialBinding.catalogVersion) : "");
    setChecklistItemCode(initialBinding.checklistItemCode ?? "");
    setRelevance(initialBinding.relevance || "REFERENCE");
    setStatus(initialBinding.status || "ACTIVE");
    setEffectiveFrom(initialBinding.effectiveFrom ?? "");
    setEffectiveTo(initialBinding.effectiveTo ?? "");
    setNotes(initialBinding.notes ?? "");
    setSelectedLaw(null);
  }, [defaultCatalogCode, defaultCatalogVersion, initialBinding]);

  async function searchLaw() {
    if (!lawQuery.trim()) {
      return;
    }
    setLawSearchBusy(true);
    try {
      const response = await searchPlatformLegalCorpus(accessToken, {
        query: lawQuery,
        limit: 8
      });
      setLawResults(response.items);
    } finally {
      setLawSearchBusy(false);
    }
  }

  function selectLaw(result: LegalLawSearchResult) {
    setSelectedLaw(result);
    setActId(String(result.actId));
    setArticleId(String(result.articleId));
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const parsedActId = Number(actId);
    if (!Number.isFinite(parsedActId) || parsedActId <= 0) {
      return;
    }
    const parsedArticleId = articleId ? Number(articleId) : null;
    const parsedCatalogVersion = catalogVersion ? Number(catalogVersion) : null;
    await onSubmit({
      bindingScope,
      bindingKey: bindingKey || null,
      actId: parsedActId,
      articleId: parsedArticleId && Number.isFinite(parsedArticleId) && parsedArticleId > 0 ? parsedArticleId : null,
      reportType: reportType || null,
      catalogCode: catalogCode || null,
      catalogVersion: parsedCatalogVersion && Number.isFinite(parsedCatalogVersion) ? parsedCatalogVersion : null,
      checklistItemCode: checklistItemCode || null,
      relevance,
      status,
      effectiveFrom: effectiveFrom || null,
      effectiveTo: effectiveTo || null,
      notes: notes || null,
      metadataJson: initialBinding?.metadataJson ?? {}
    });
    if (!initialBinding) {
      setBindingKey("");
      setArticleId("");
      setChecklistItemCode("");
      setNotes("");
    }
  }

  const selectedReferenceLabel = selectedLaw
    ? `${selectedLaw.actName} ${selectedLaw.articleNo} ${selectedLaw.articleTitle ?? ""}`
    : initialBinding
      ? `${initialBinding.actName ?? initialBinding.actCode ?? `Act #${initialBinding.actId}`} ${initialBinding.articleNo ?? ""} ${initialBinding.articleTitle ?? ""}`
      : "";

  return (
    <form className="ai-policy-form" onSubmit={submit}>
      <div className="wide stack-panel subtle">
        <label>
          법령 조문 검색
          <div className="inline-form-row">
            <input onChange={(event) => setLawQuery(event.target.value)} placeholder="예: 감리, 사진, 제25조" value={lawQuery} />
            <button className="button" disabled={lawSearchBusy || !lawQuery.trim()} onClick={searchLaw} type="button">
              {lawSearchBusy ? <Loader2 className="spin" size={16} /> : <RefreshCcw size={16} />}
              검색
            </button>
          </div>
        </label>
        {selectedReferenceLabel ? (
          <InlineNotice message={`선택된 근거: ${selectedReferenceLabel}`} />
        ) : null}
        {lawResults.length > 0 ? (
          <div className="legal-admin-digest-list compact-list">
            {lawResults.map((result) => (
              <button className="legal-admin-digest-item" key={result.referenceId} onClick={() => selectLaw(result)} type="button">
                <span>{result.actCode} / {result.effectiveDate ?? "시행일 미상"}</span>
                <strong>{result.actName} {result.articleNo} {result.articleTitle ?? ""}</strong>
                <small>{result.snippet}</small>
                <em>act #{result.actId} / article #{result.articleId} / version #{result.articleVersionId}</em>
              </button>
            ))}
          </div>
        ) : null}
      </div>
      <label>
        Scope
        <select value={bindingScope} onChange={(event) => setBindingScope(event.target.value)}>
          <option value="CATALOG_ITEM">CATALOG_ITEM</option>
          <option value="REPORT_TYPE">REPORT_TYPE</option>
        </select>
      </label>
      <label>
        Binding Key
        <input onChange={(event) => setBindingKey(event.target.value)} placeholder="비우면 catalog/report 기준 자동 생성" value={bindingKey} />
      </label>
      <label>
        Act ID
        <input min="1" onChange={(event) => setActId(event.target.value)} required type="number" value={actId} />
      </label>
      <label>
        Article ID
        <input min="1" onChange={(event) => setArticleId(event.target.value)} placeholder="선택" type="number" value={articleId} />
      </label>
      <label>
        Report Type
        <input onChange={(event) => setReportType(event.target.value)} value={reportType} />
      </label>
      <label>
        Catalog Code
        <input onChange={(event) => setCatalogCode(event.target.value)} value={catalogCode} />
      </label>
      <label>
        Version
        <input min="1" onChange={(event) => setCatalogVersion(event.target.value)} type="number" value={catalogVersion} />
      </label>
      <label>
        Checklist Item
        <input onChange={(event) => setChecklistItemCode(event.target.value)} placeholder="inspectionItemCode" value={checklistItemCode} />
      </label>
      <label>
        Relevance
        <select value={relevance} onChange={(event) => setRelevance(event.target.value)}>
          <option value="PRIMARY">PRIMARY</option>
          <option value="SUPPORTING">SUPPORTING</option>
          <option value="REFERENCE">REFERENCE</option>
        </select>
      </label>
      <label>
        Status
        <select value={status} onChange={(event) => setStatus(event.target.value)}>
          <option value="ACTIVE">ACTIVE</option>
          <option value="INACTIVE">INACTIVE</option>
        </select>
      </label>
      <label>
        Effective From
        <input onChange={(event) => setEffectiveFrom(event.target.value)} type="date" value={effectiveFrom} />
      </label>
      <label>
        Effective To
        <input onChange={(event) => setEffectiveTo(event.target.value)} type="date" value={effectiveTo} />
      </label>
      <label className="wide">
        Notes
        <input onChange={(event) => setNotes(event.target.value)} value={notes} />
      </label>
      <div className="form-actions">
        {onCancel ? (
          <button className="button" disabled={busy} onClick={onCancel} type="button">
            취소
          </button>
        ) : null}
        <button className="button primary" disabled={busy || !actId} type="submit">
          {busy ? <Loader2 className="spin" size={16} /> : <Plus size={16} />}
          {initialBinding ? "바인딩 수정" : "바인딩 추가"}
        </button>
      </div>
    </form>
  );
}

function LegalDigestDetail({
  aiDrafts,
  digest,
  loading,
  onApproveAiDraft,
  onRejectAiDraft,
  onApplyAiDraft
}: {
  aiDrafts: LegalDigestAiDraft[];
  digest: LegalChangeDigest | null;
  loading: boolean;
  onApproveAiDraft: (draftId: number) => void;
  onRejectAiDraft: (draftId: number) => void;
  onApplyAiDraft: (draftId: number) => void;
}) {
  if (!digest) {
    return <EmptyState message="선택된 법령 변경사항이 없습니다." />;
  }

  const articleDiffs = digest.articleDiffs ?? [];
  const added = articleDiffs.filter((diff) => diff.changeType === "ADDED").length;
  const modified = articleDiffs.filter((diff) => diff.changeType === "MODIFIED").length;
  const removed = articleDiffs.filter((diff) => diff.changeType === "REMOVED").length;
  const aiDraft = aiDrafts[0] ?? null;

  return (
    <article className="legal-digest-detail">
      <header>
        <div>
          <strong>{digest.title}</strong>
          <span>{formatDate(digest.publishedAt ?? digest.detectedAt)} / 시행일 {digest.effectiveDate ?? "미정"}</span>
        </div>
        <StatusBadge status={digest.status} />
      </header>

      <div className="metric-grid compact">
        <MetricCard icon={<FileText size={20} />} label="조문 diff" value={articleDiffs.length} detail="원천 변경 조문" tone="blue" />
        <MetricCard icon={<Plus size={20} />} label="신설" value={added} detail="ADDED" tone="green" />
        <MetricCard icon={<Activity size={20} />} label="수정" value={modified} detail="MODIFIED" tone="amber" />
        <MetricCard icon={<XCircle size={20} />} label="삭제" value={removed} detail="REMOVED" tone={removed > 0 ? "red" : "slate"} />
      </div>

      <section>
        <h3>변경 요약</h3>
        <p>{digest.summary}</p>
      </section>

      <section>
        <h3>업무 영향</h3>
        <p>{digest.impactSummary ?? "아직 업무 영향 요약이 작성되지 않았습니다."}</p>
      </section>

      {aiDraft ? (
        <section className="legal-ai-draft">
          <div className="legal-ai-draft-header">
            <div>
              <h3>AI 초안</h3>
              <strong>{legalAiDraftText(aiDraft.title)}</strong>
            </div>
            <div className="legal-ai-draft-badges">
              <StatusBadge status={aiDraft.status} />
              <StatusBadge status={aiDraft.digestDraftStatus} />
              <StatusBadge status={aiDraft.confidence} />
            </div>
          </div>
          <p>{legalAiDraftText(aiDraft.summary)}</p>
          <p>{legalAiDraftText(aiDraft.impactSummary)}</p>
          <div className="legal-ai-draft-meta">
            <span>하네스 {aiDraft.aiHarnessRunId || "-"}</span>
            <span>워커 {displayLabel(aiDraft.workerStatus)}</span>
            <span>생성 {formatDate(aiDraft.generatedAt)}</span>
            {aiDraft.reviewedAt ? <span>검토 {formatDate(aiDraft.reviewedAt)}</span> : null}
            <span>{aiDraft.appliedAt ? `게시 반영 ${formatDate(aiDraft.appliedAt)}` : "게시 미반영"}</span>
          </div>
          <LegalDigestDraftList label="관련 문서" values={aiDraft.affectedReportTypes} />
          <LegalDigestDraftList label="관련 카탈로그" values={aiDraft.affectedCatalogItems} />
          <LegalDigestDraftList label="근거 조문" values={aiDraft.keyArticles} />
          {aiDraft.reviewNotes ? <p>{legalAiDraftText(aiDraft.reviewNotes)}</p> : null}
          <LegalDigestAiDraftActions
            draft={aiDraft}
            loading={loading}
            onApply={onApplyAiDraft}
            onApprove={onApproveAiDraft}
            onReject={onRejectAiDraft}
          />
          <LegalDigestAiDraftHistory
            drafts={aiDrafts}
            loading={loading}
            onApply={onApplyAiDraft}
            onApprove={onApproveAiDraft}
            onReject={onRejectAiDraft}
          />
        </section>
      ) : null}

      <section>
        <h3>조문별 변경</h3>
        {articleDiffs.length === 0 ? (
          <InlineNotice message="이 변경사항에는 조문 단위 diff가 없습니다." />
        ) : (
          <div className="legal-diff-list">
            {articleDiffs.slice(0, 80).map((diff) => (
              <div className="legal-diff-item" key={diff.id}>
                <div className="legal-diff-title-row">
                  <StatusBadge status={diff.changeType} />
                  <strong>{legalArticleLabel(diff.articleNo, diff.articleTitle, diff.articleKey)}</strong>
                  {diff.effectiveDate ? <span>시행일 {diff.effectiveDate}</span> : null}
                  {diff.sourceVersionKey ? <span>버전 {diff.sourceVersionKey}</span> : null}
                  {diff.publicSourceUrl ? (
                    <a href={diff.publicSourceUrl} target="_blank" rel="noreferrer">
                      법령정보센터
                    </a>
                  ) : null}
                </div>
                <span>{diff.diffSummary}</span>
                {diff.beforeTextPreview || diff.afterTextPreview ? <AdminLegalTextComparison diff={diff} /> : null}
                <small>
                  before #{diff.beforeArticleVersionId ?? "-"} / after #{diff.afterArticleVersionId ?? "-"} / {shortHash(diff.beforeHash)} → {shortHash(diff.afterHash)}
                </small>
              </div>
            ))}
          </div>
        )}
      </section>
    </article>
  );
}

function LegalDigestAiDraftHistory({
  drafts,
  loading,
  onApply,
  onApprove,
  onReject
}: {
  drafts: LegalDigestAiDraft[];
  loading: boolean;
  onApply: (draftId: number) => void;
  onApprove: (draftId: number) => void;
  onReject: (draftId: number) => void;
}) {
  if (drafts.length <= 1) {
    return null;
  }
  return (
    <div className="legal-ai-draft-history">
      <strong>AI 초안 이력</strong>
      {drafts.slice(1).map((draft) => (
        <div className="legal-ai-draft-entry" key={draft.id}>
          <div className="legal-ai-draft-entry-head">
            <div>
              <span>초안 #{draft.id} / {formatDate(draft.generatedAt)}</span>
              <small>{legalAiDraftText(draft.title)}</small>
            </div>
            <div className="legal-ai-draft-badges">
              <StatusBadge status={draft.status} />
              <StatusBadge status={draft.digestDraftStatus} />
              <StatusBadge status={draft.confidence} />
            </div>
          </div>
          <p>{legalAiDraftText(draft.summary)}</p>
          <div className="legal-ai-draft-meta">
            <span>하네스 {draft.aiHarnessRunId || "-"}</span>
            {draft.reviewedAt ? <span>검토 {formatDate(draft.reviewedAt)}</span> : null}
            <span>{draft.appliedAt ? `게시 반영 ${formatDate(draft.appliedAt)}` : "게시 미반영"}</span>
          </div>
          <LegalDigestAiDraftActions
            draft={draft}
            loading={loading}
            onApply={onApply}
            onApprove={onApprove}
            onReject={onReject}
          />
        </div>
      ))}
    </div>
  );
}

function LegalDigestAiDraftActions({
  draft,
  loading,
  onApply,
  onApprove,
  onReject
}: {
  draft: LegalDigestAiDraft;
  loading: boolean;
  onApply: (draftId: number) => void;
  onApprove: (draftId: number) => void;
  onReject: (draftId: number) => void;
}) {
  const awaitingReview = draft.status === "NEEDS_HUMAN_REVIEW" || draft.status === "GENERATED";
  const approved = draft.status === "APPROVED";
  if (!awaitingReview && !approved) {
    return null;
  }
  return (
    <div className="legal-ai-draft-actions">
      {awaitingReview ? (
        <>
          <button className="button compact primary" disabled={loading} onClick={() => onApprove(draft.id)} type="button">
            {loading ? <Loader2 className="spin" size={15} /> : <CheckCircle2 size={15} />}
            승인
          </button>
          <button className="button compact" disabled={loading} onClick={() => onReject(draft.id)} type="button">
            <XCircle size={15} />
            반려
          </button>
        </>
      ) : null}
      {approved ? (
        <button className="button compact primary" disabled={loading} onClick={() => onApply(draft.id)} type="button">
          {loading ? <Loader2 className="spin" size={15} /> : <CheckCircle2 size={15} />}
          게시 요약에 반영
        </button>
      ) : null}
    </div>
  );
}

function LegalDigestDraftList({ label, values }: { label: string; values: string[] }) {
  if (!values.length) {
    return null;
  }
  return (
    <div className="legal-ai-draft-list">
      <span>{label}</span>
      <div>
        {values.map((value) => (
          <em key={value}>{displayLabel(value)}</em>
        ))}
      </div>
    </div>
  );
}

function legalAiDraftText(value?: string | null) {
  const text = value ?? "";
  const legacyFakeDraftText: Record<string, string> = {
    "Development legal change digest draft": "법령 변경 AI 초안(개발용)",
    "Development fake AI summarized the legal change set using only the provided legal corpus context.": "제공된 법령 변경 근거만 사용해 사용자용 변경사항 게시글 초안을 생성했습니다.",
    "Review whether construction supervision report templates, checklist catalog items, and site evidence guidance need an update.": "공사감리일지, 감리보고서 템플릿, 체크리스트 카탈로그, 현장 증빙 안내의 수정 필요 여부를 관리자가 검토해야 합니다.",
    "Development-only draft. A platform admin must review and approve before publishing.": "개발용 fake AI 초안입니다. 플랫폼 관리자가 검토한 뒤 게시 요약에 적용해야 사용자에게 반영됩니다."
  };
  return legacyFakeDraftText[text] ?? text;
}

type AdminLegalTextBlock =
  | { kind: "heading" | "bullet" | "paragraph"; text: string }
  | { kind: "row"; cells: string[] };

function AdminLegalTextComparison({ diff }: { diff: LegalArticleDiff }) {
  return (
    <div className="legal-text-comparison">
      <AdminLegalTextPanel label="이전" text={diff.beforeTextPreview ?? ""} emptyText="이전 조문 본문 없음" />
      <AdminLegalTextPanel label="이후" text={diff.afterTextPreview ?? ""} emptyText="이후 조문 본문 없음" />
    </div>
  );
}

function AdminLegalTextPanel({ label, text, emptyText }: { label: string; text: string; emptyText: string }) {
  const sourceText = text?.trim() ?? "";
  const blocks = formatAdminLegalTextBlocks(sourceText);
  return (
    <div className="legal-text-panel">
      <div className="legal-text-panel-header">
        <strong>{label}</strong>
        <span>읽기 보기</span>
      </div>
      <div className="legal-text-readable">
        {blocks.length === 0 ? (
          <p className="legal-text-empty">{emptyText}</p>
        ) : blocks.map((block, index) => renderAdminLegalTextBlock(block, `${label}-${index}`))}
      </div>
      {sourceText ? (
        <details className="legal-text-raw">
          <summary>원문 보기</summary>
          <pre>{sourceText}</pre>
        </details>
      ) : null}
    </div>
  );
}

function renderAdminLegalTextBlock(block: AdminLegalTextBlock, key: string) {
  if (block.kind === "row") {
    return (
      <div className="legal-text-row" key={key}>
        {block.cells.map((cell, index) => (
          <span className="legal-text-cell" key={`${key}-${index}`}>
            {cell}
          </span>
        ))}
      </div>
    );
  }
  return (
    <p className={`legal-text-block ${block.kind}`} key={key}>
      {block.text}
    </p>
  );
}

function formatAdminLegalTextBlocks(value: string): AdminLegalTextBlock[] {
  return splitAdminLegalText(value)
    .map((line) => line.trim())
    .filter((line) => line.length > 0 && !isAdminBoxRuleLine(line))
    .map((line) => {
      const cells = adminTableCells(line);
      if (cells.length >= 2) {
        return { kind: "row", cells } satisfies AdminLegalTextBlock;
      }
      if (isAdminLegalHeading(line)) {
        return { kind: "heading", text: line } satisfies AdminLegalTextBlock;
      }
      if (isAdminLegalBullet(line)) {
        return { kind: "bullet", text: line } satisfies AdminLegalTextBlock;
      }
      return { kind: "paragraph", text: line } satisfies AdminLegalTextBlock;
    })
    .slice(0, 120);
}

function splitAdminLegalText(value: string) {
  if (!value?.trim()) {
    return [];
  }
  const normalized = value
    .replace(/\r\n?/g, "\n")
    .replace(/([┌┬┐└┴┘├┼┤─━]{4,})/g, "\n$1\n")
    .replace(/\s+(■\s*)/g, "\n$1")
    .replace(/\s+(\[[^\]\n]{1,50}\])/g, "\n$1")
    .replace(/\s+(제\s*\d+조(?:의\d+)?(?:\([^)]*\))?)/g, "\n$1")
    .replace(/\s+(\d+\.\s+)/g, "\n$1")
    .replace(/\s+([가-하]\.\s+)/g, "\n$1")
    .replace(/\s+([①②③④⑤⑥⑦⑧⑨⑩])/g, "\n$1")
    .replace(/\s+(-\s+)/g, "\n$1");
  return normalized.split("\n").flatMap(splitAdminLongLegalLine);
}

function splitAdminLongLegalLine(line: string) {
  const trimmed = line.trim();
  if (trimmed.length < 260) {
    return [trimmed];
  }
  if (trimmed.includes(" - ")) {
    return trimmed.split(/\s+-\s+/).map((part, index) => (index === 0 ? part : `- ${part}`));
  }
  return trimmed.split(/(?=\s(?:\d+\.|[가-하]\.|[①②③④⑤⑥⑦⑧⑨⑩]))/g);
}

function adminTableCells(line: string) {
  if (!/[│|]/.test(line)) {
    return [];
  }
  return line
    .split(/[│|]/)
    .map((cell) => cell.replace(/[─━┬┴┼┌┐└┘├┤]+/g, " ").trim())
    .filter((cell) => cell.length > 0)
    .slice(0, 8);
}

function isAdminBoxRuleLine(line: string) {
  const stripped = line.replace(/\s/g, "");
  return stripped.length > 0 && /^[─━┬┴┼┌┐└┘├┤│|]+$/.test(stripped);
}

function isAdminLegalHeading(line: string) {
  return /^(\[[^\]]+\]|■|제\s*\d+조|별표|별지)/.test(line);
}

function isAdminLegalBullet(line: string) {
  return /^(\d+\.|[가-하]\.|[-ㆍ•]|[①②③④⑤⑥⑦⑧⑨⑩])/.test(line);
}

function PlatformView({
  view,
  accessToken,
  data,
  platformAdmin,
  loading,
  onRefresh,
  lastDetection,
  onDetectStuck,
  legalDigestAiDrafts,
  legalBindingAutoGenerateResult,
  onFlowerRuntimeRefresh,
  onLegalDigestAiDraft,
  onLoadLegalDigestAiDrafts,
  onApproveLegalDigestAiDraft,
  onRejectLegalDigestAiDraft,
  onApplyLegalDigestAiDraft,
  onLegalOpenDataSync,
  onLegalDigestRefresh,
  onCreateLegalDomainBinding,
  onUpdateLegalDomainBinding,
  onAutoGenerateConstructionSupervisionLegalBindings,
  onDeactivateLegalDomainBinding,
  issuedEngineApiKey,
  onCreateEngineApiKey,
  onRevokeEngineApiKey,
  onDismissIssuedEngineApiKey,
  onDiagnoseIncident,
  onApproveWorkerApproval,
  onRejectWorkerApproval
}: {
  view: PlatformViewKey;
  accessToken: string;
  data: PlatformOpsData;
  platformAdmin: PlatformAdminMe | null;
  loading: boolean;
  onRefresh: () => void;
  lastDetection: PlatformHealthDetection | null;
  onDetectStuck: () => void;
  legalDigestAiDrafts: Record<number, LegalDigestAiDraft[]>;
  legalBindingAutoGenerateResult: LegalDomainBindingAutoGenerateResponse | null;
  onFlowerRuntimeRefresh: (silent?: boolean) => Promise<void>;
  onLegalDigestAiDraft: (digestId: number) => void;
  onLoadLegalDigestAiDrafts: (digestId: number) => void;
  onApproveLegalDigestAiDraft: (digestId: number, draftId: number) => void;
  onRejectLegalDigestAiDraft: (digestId: number, draftId: number) => void;
  onApplyLegalDigestAiDraft: (digestId: number, draftId: number) => void;
  onLegalOpenDataSync: () => void;
  onLegalDigestRefresh: () => void;
  onCreateLegalDomainBinding: (body: LegalDomainBindingPayload) => Promise<void>;
  onUpdateLegalDomainBinding: (bindingId: number, body: LegalDomainBindingPayload) => Promise<void>;
  onAutoGenerateConstructionSupervisionLegalBindings: () => Promise<void>;
  onDeactivateLegalDomainBinding: (bindingId: number) => Promise<void>;
  issuedEngineApiKey: CreateEngineApiKeyResponse | null;
  onCreateEngineApiKey: (body: {
    displayName: string;
    ownerUserId: number;
    officeId?: number | null;
    scopes: string[];
    dailyRequestUnitLimit?: number | null;
    expiresAt?: string | null;
  }) => Promise<void>;
  onRevokeEngineApiKey: (apiKeyId: number) => Promise<void>;
  onDismissIssuedEngineApiKey: () => void;
  onDiagnoseIncident: (incidentId: number) => void;
  onApproveWorkerApproval: (approvalRequestId: number) => Promise<void>;
  onRejectWorkerApproval: (approvalRequestId: number) => Promise<void>;
}) {
  const summary = data.summary;
  const serverHealth = summary?.serverHealth ?? null;
  const failedJobs = summary?.documentJobs.FAILED ?? 0;
  const failedCommands = (summary?.agentCommands.FAILED ?? 0) + (summary?.agentCommands.EXPIRED ?? 0);
  const failedPickups = summary?.photoPickups.FAILED ?? 0;
  const failedDeliveries = summary?.deliveries.FAILED ?? 0;
  const attention = failedJobs + failedCommands + failedPickups + failedDeliveries;
  const opsRuns = data.opsRuns ?? [];
  const opsIncidents = data.opsIncidents ?? [];
  const opsFindings = data.opsFindings ?? [];
  const diagnosisRuns = useMemo(
    () => opsRuns.filter((run) => run.triggerType === "MANUAL_DIAGNOSIS"),
    [opsRuns]
  );
  const selectedDiagnosisRun = diagnosisRuns[0] ?? null;
  const selectedDiagnosisIncident = selectedDiagnosisRun?.incidentId
    ? opsIncidents.find((incident) => incident.id === selectedDiagnosisRun.incidentId) ?? null
    : null;
  const showOverview = view === "platform-overview";
  const showIncidents = view === "platform-incidents";
  const showOffices = view === "platform-offices";
  const showUsers = view === "platform-users";
  const showAgents = view === "platform-agents";
  const showCommands = view === "platform-commands";
  const showDocumentJobs = view === "platform-document-jobs";
  const showPhotoDelivery = view === "platform-photo-delivery";
  const showTemplates = view === "platform-templates";
  const showLegal = view === "platform-legal";
  const showEngineKeys = view === "platform-engine-keys";
  const showWorkerGovernance = view === "platform-worker-governance";
  const showWorkerApprovals = view === "platform-worker-approvals";
  const showFlowerRuntime = view === "platform-flower-runtime";
  const showEvents = view === "platform-events";

  return (
    <div className="view-stack platform-view">
      <div className="section-header">
        <div>
          <p className="eyebrow">플랫폼 관리자</p>
          <h2>전체 서비스 운영</h2>
          <p className="muted">
            {platformAdmin ? `${platformAdmin.email} / ${displayLabel(platformAdmin.role)}` : "플랫폼 권한이 필요합니다."}
          </p>
        </div>
        <div className="topbar-actions">
          <button className="button" onClick={onDetectStuck} type="button">
            <AlertTriangle size={16} />
            멈춤 감지
          </button>
          <button className="button" onClick={onRefresh} type="button">
            {loading ? <Loader2 className="spin" size={16} /> : <RefreshCcw size={16} />}
            새로고침
          </button>
        </div>
      </div>
      {lastDetection ? (
        <InlineNotice
          message={
            lastDetection.opsRunId
              ? `${formatDate(lastDetection.detectedAt)} 기준 운영 감지 Flow #${lastDetection.opsRunId}를 요청했습니다. 결과는 운영 이슈/진단 목록에서 갱신됩니다.`
              : `${formatDate(lastDetection.detectedAt)} 기준 멈춤 후보 ${lastDetection.total}건을 감지했습니다.`
          }
        />
      ) : null}

      {showLegal ? (
        <PlatformLegalAdminPanel
          data={data}
          accessToken={accessToken}
          legalDigestAiDrafts={legalDigestAiDrafts}
          legalBindingAutoGenerateResult={legalBindingAutoGenerateResult}
          loading={loading}
          onLegalDigestAiDraft={onLegalDigestAiDraft}
          onLoadLegalDigestAiDrafts={onLoadLegalDigestAiDrafts}
          onApproveLegalDigestAiDraft={onApproveLegalDigestAiDraft}
          onRejectLegalDigestAiDraft={onRejectLegalDigestAiDraft}
          onApplyLegalDigestAiDraft={onApplyLegalDigestAiDraft}
          onLegalDigestRefresh={onLegalDigestRefresh}
          onLegalOpenDataSync={onLegalOpenDataSync}
          onCreateLegalDomainBinding={onCreateLegalDomainBinding}
          onUpdateLegalDomainBinding={onUpdateLegalDomainBinding}
          onAutoGenerateConstructionSupervisionLegalBindings={onAutoGenerateConstructionSupervisionLegalBindings}
          onDeactivateLegalDomainBinding={onDeactivateLegalDomainBinding}
        />
      ) : null}

      {showEngineKeys ? (
        <EngineApiKeyManagementPanel
          busy={loading}
          keys={data.engineApiKeys}
          usageEvents={data.engineApiUsageEvents}
          usageSummary={data.engineApiUsageSummary}
          offices={data.offices}
          users={data.users}
          issuedKey={issuedEngineApiKey}
          onCreate={onCreateEngineApiKey}
          onDismissIssuedKey={onDismissIssuedEngineApiKey}
          onRevoke={onRevokeEngineApiKey}
        />
      ) : null}

      {showWorkerGovernance ? (
        <WorkerGovernancePanel summary={data.workerGovernance} />
      ) : null}

      {showWorkerApprovals ? (
        <WorkerApprovalPanel
          approvals={data.workerApprovals}
          busy={loading}
          onApprove={onApproveWorkerApproval}
          onReject={onRejectWorkerApproval}
        />
      ) : null}

      {showFlowerRuntime ? (
        <FlowerRuntimePanel
          dump={data.flowerRuntimeDump}
          loading={loading}
          onRefresh={onFlowerRuntimeRefresh}
        />
      ) : null}

      {showOverview ? (
        <>
          <div className="metric-grid">
            <MetricCard icon={<Users size={20} />} label="사용자" value={summary?.users ?? 0} detail="전체 계정" tone="blue" />
            <MetricCard icon={<HardDrive size={20} />} label="사무소" value={summary?.offices ?? 0} detail="전체 테넌트" tone="slate" />
            <MetricCard icon={<Server size={20} />} label="에이전트" value={sumCounts(summary?.agents)} detail={`${summary?.agents.ONLINE ?? 0} 온라인`} tone="green" />
            <MetricCard icon={<Wifi size={20} />} label="세션" value={summary?.activeAgentSessions ?? 0} detail="활성 WebSocket" tone="blue" />
            <MetricCard icon={<Command size={20} />} label="명령" value={summary?.agentCommands.IN_FLIGHT ?? 0} detail="진행 중" tone="amber" />
            <MetricCard icon={<AlertTriangle size={20} />} label="확인 필요" value={attention} detail="실패/멈춤 후보" tone={attention > 0 ? "red" : "green"} />
          </div>
          <div className="metric-grid compact">
            <MetricCard
              icon={<Gauge size={20} />}
              label="서버 CPU"
              value={formatPercent(serverHealth?.systemCpuLoadPercent ?? serverHealth?.processCpuLoadPercent)}
              detail={serverHealth ? `프로세스 ${formatPercent(serverHealth.processCpuLoadPercent)} · ${serverHealth.availableProcessors} cores` : "수집 대기"}
              tone={runtimeMetricTone(serverHealth?.systemCpuLoadPercent ?? serverHealth?.processCpuLoadPercent, serverHealth?.warnings.includes("CPU_LOAD_HIGH"))}
            />
            <MetricCard
              icon={<Activity size={20} />}
              label="서버 메모리"
              value={formatPercent(serverHealth?.systemMemoryUsedPercent)}
              detail={serverHealth ? `${formatBytes(serverHealth.systemMemoryUsedBytes)} / ${formatBytes(serverHealth.systemMemoryTotalBytes)}` : "수집 대기"}
              tone={runtimeMetricTone(serverHealth?.systemMemoryUsedPercent, serverHealth?.warnings.includes("SYSTEM_MEMORY_HIGH"))}
            />
            <MetricCard
              icon={<HardDrive size={20} />}
              label="JVM Heap"
              value={formatPercent(serverHealth?.jvmHeapUsedPercent)}
              detail={serverHealth ? `${formatBytes(serverHealth.jvmHeapUsedBytes)} / ${formatBytes(serverHealth.jvmHeapMaxBytes)}` : "수집 대기"}
              tone={runtimeMetricTone(serverHealth?.jvmHeapUsedPercent, serverHealth?.warnings.includes("JVM_HEAP_HIGH"))}
            />
            <MetricCard
              icon={<Clock3 size={20} />}
              label="수집 시각"
              value={serverHealth?.status ?? "-"}
              detail={serverHealth ? formatDate(serverHealth.capturedAt) : "5분 주기 monitoring flow"}
              tone={serverHealthTone(serverHealth?.status)}
            />
          </div>
          {serverHealth?.warnings.length ? (
            <InlineNotice message={`서버 부하 경고: ${serverHealth.warnings.map(displayLabel).join(", ")}`} />
          ) : null}
        </>
      ) : null}

      {showOverview || showIncidents ? (
        <OpsPriorityPanel
          failedCommands={failedCommands}
          failedDeliveries={failedDeliveries}
          failedJobs={failedJobs}
          failedPickups={failedPickups}
          incidents={opsIncidents}
        />
      ) : null}

      {showOverview || showIncidents ? (
        <PlatformOpsDiagnosisDetailPanel
          incident={selectedDiagnosisIncident}
          run={selectedDiagnosisRun}
        />
      ) : null}

      {showEvents ? (
        <div className="dashboard-grid">
        <Panel title="플랫폼 워크플로우 상태" icon={<Gauge size={18} />}>
          <StatusBars
            groups={[
              ["문서 작업", summary?.documentJobs ?? {}],
              ["사진 회수", summary?.photoPickups ?? {}],
              ["문서 전달", summary?.deliveries ?? {}],
              ["Agent 명령", summary?.agentCommands ?? {}]
            ]}
          />
        </Panel>
        <Panel title="최근 플랫폼 이벤트" icon={<Activity size={18} />}>
          <EventList events={data.events.slice(0, 8)} />
        </Panel>
        </div>
      ) : null}

      {showIncidents ? (
        <Panel title="운영 이슈" icon={<AlertTriangle size={18} />} count={opsIncidents.length}>
        <Table
          columns={["이슈", "심각도", "상태", "사무소", "대상", "최근 감지"]}
          empty="운영 이슈가 없습니다."
          rows={opsIncidents.slice(0, 20).map((incident) => [
            <div className="member-actions" key="incident">
              <CellTitle title={incident.title} subtitle={`${incident.category} / #${incident.id}`} />
              <button
                className="button"
                disabled={loading}
                onClick={() => onDiagnoseIncident(incident.id)}
                type="button"
              >
                진단
              </button>
            </div>,
            <StatusBadge key="severity" status={incident.severity} />,
            <StatusBadge key="status" status={incident.status} />,
            incident.officeId ? `#${incident.officeId}` : "-",
            incident.primaryResourceType ? `${incident.primaryResourceType} #${incident.primaryResourceId ?? "-"}` : "-",
            formatDate(incident.lastSeenAt)
          ])}
        />
        </Panel>
      ) : null}

      {showIncidents ? (
        <Panel title="운영 진단 Flow" icon={<Gauge size={18} />} count={opsRuns.length}>
        <Table
          columns={["Flow", "상태", "트리거", "요청자", "시작", "완료"]}
          empty="운영 진단 Flow 기록이 없습니다."
          rows={opsRuns.slice(0, 20).map((run) => [
            <CellTitle key="run" title={`Flow #${run.id}`} subtitle={run.incidentId ? `Incident #${run.incidentId}` : "incident 없음"} />,
            <StatusBadge key="status" status={run.status} />,
            displayLabel(run.triggerType),
            run.startedByUserId ? `#${run.startedByUserId}` : "-",
            formatDate(run.startedAt),
            formatDate(run.completedAt)
          ])}
        />
        </Panel>
      ) : null}

      {showIncidents ? (
        <Panel title="운영 Finding" icon={<Activity size={18} />} count={opsFindings.length}>
        <Table
          columns={["Finding", "심각도", "출처", "사무소", "대상", "생성"]}
          empty="운영 Finding이 없습니다."
          rows={opsFindings.slice(0, 20).map((finding) => [
            <CellTitle key="finding" title={finding.title} subtitle={`${finding.code} / run #${finding.runId}`} />,
            <StatusBadge key="severity" status={finding.severity} />,
            displayLabel(finding.source),
            finding.officeId ? `#${finding.officeId}` : "-",
            finding.resourceType ? `${finding.resourceType} #${finding.resourceId ?? "-"}` : "-",
            formatDate(finding.createdAt)
          ])}
        />
        </Panel>
      ) : null}

      {showOffices ? (
        <Panel title="사무소" icon={<HardDrive size={18} />} count={data.offices.length}>
        <Table
          columns={["사무소", "유형", "플랜", "상태"]}
          empty="사무소가 없습니다."
          rows={data.offices.slice(0, 20).map((office) => [
            <CellTitle key="office" title={office.displayName} subtitle={`${office.officeCode} / #${office.id}`} />,
            displayLabel(office.type),
            office.planCode,
            <StatusBadge key="status" status={office.status} />
          ])}
        />
        </Panel>
      ) : null}

      {showUsers ? (
        <Panel title="회원" icon={<Users size={18} />} count={data.users.length}>
        <Table
          columns={["회원", "상태", "생성"]}
          empty="회원이 없습니다."
          rows={data.users.slice(0, 50).map((user) => [
            <CellTitle key="user" title={user.name} subtitle={`${user.email} / #${user.id}`} />,
            <StatusBadge key="status" status={user.status} />,
            formatDate(user.createdAt)
          ])}
        />
        </Panel>
      ) : null}

      {showAgents ? (
        <Panel title="에이전트" icon={<Server size={18} />} count={data.agents.length}>
        <Table
          columns={["에이전트", "사무소", "상태", "모드", "버전", "최근 신호"]}
          empty="에이전트가 없습니다."
          rows={data.agents.slice(0, 20).map((agent) => [
            <CellTitle key="agent" title={agent.agentCode} subtitle={`#${agent.id}`} />,
            `#${agent.officeId}`,
            <StatusBadge key="status" status={agent.status} />,
            displayLabel(agent.deploymentMode),
            agent.version ?? "-",
            formatDate(agent.lastSeenAt)
          ])}
        />
        </Panel>
      ) : null}

      {showCommands ? (
        <Panel title="Agent 명령" icon={<Command size={18} />} count={data.commands.length}>
        <Table
          columns={["명령", "사무소", "Agent", "상태", "시도", "다음 시도", "오류"]}
          empty="Agent 명령이 없습니다."
          rows={data.commands.slice(0, 50).map((command) => [
            <CellTitle key="command" title={displayLabel(command.commandType)} subtitle={`#${command.id}`} />,
            `#${command.officeId}`,
            `${command.agentCode} / #${command.agentId}`,
            <StatusBadge key="status" status={command.status} />,
            `${command.attemptCount}/${command.maxAttempts}`,
            formatDate(command.nextAttemptAt ?? command.lastAttemptAt ?? command.createdAt),
            command.errorMessage ?? "-"
          ])}
        />
        </Panel>
      ) : null}

      {showDocumentJobs ? (
        <Panel title="실패 또는 최근 문서 작업" icon={<FileText size={18} />} count={data.documents.length}>
        <Table
          columns={["작업", "사무소", "상태", "진행", "처리 주체", "수정", "오류"]}
          empty="문서 작업이 없습니다."
          rows={data.documents.slice(0, 20).map((job) => [
            <CellTitle key="job" title={`작업 #${job.id}`} subtitle={`리포트 #${job.reportId} v${job.reportRevision}`} />,
            `#${job.officeId}`,
            <StatusBadge key="status" status={job.status} />,
            <Progress key="progress" value={job.progressPercent} />,
            displayLabel(job.workerType),
            formatDate(job.updatedAt),
            job.errorMessage ?? "-"
          ])}
        />
        </Panel>
      ) : null}

      {showPhotoDelivery ? (
        <Panel title="사진" icon={<Camera size={18} />} count={data.photos.length}>
        <Table
          columns={["사진", "사무소", "리포트", "상태", "회수", "저장", "오류"]}
          empty="사진 기록이 없습니다."
          rows={data.photos.slice(0, 50).map((photo) => [
            <CellTitle key="photo" title={`사진 #${photo.id}`} subtitle={photo.stepCode ?? "step 없음"} />,
            `#${photo.officeId}`,
            photo.reportId ? `#${photo.reportId}` : "-",
            <StatusBadge key="status" status={photo.status} />,
            <StatusBadge key="pickup" status={photo.originalPickupStatus} />,
            displayLabel(photo.storageKind),
            photo.pickupErrorMessage ?? "-"
          ])}
        />
        </Panel>
      ) : null}

      {showPhotoDelivery ? (
        <Panel title="문서 전달" icon={<Truck size={18} />} count={data.deliveries.length}>
        <Table
          columns={["전달", "사무소", "문서 작업", "채널", "상태", "수정", "오류"]}
          empty="문서 전달 기록이 없습니다."
          rows={data.deliveries.slice(0, 50).map((delivery) => [
            <CellTitle key="delivery" title={`전달 #${delivery.id}`} subtitle={delivery.artifactId ? `artifact #${delivery.artifactId}` : "artifact 없음"} />,
            `#${delivery.officeId}`,
            `#${delivery.documentJobId}`,
            displayLabel(delivery.channel),
            <StatusBadge key="status" status={delivery.status} />,
            formatDate(delivery.updatedAt),
            delivery.errorMessage ?? "-"
          ])}
        />
        </Panel>
      ) : null}

      {showTemplates ? (
        <div className="dashboard-grid">
          <Panel title="시스템 기본 템플릿/문서설정" icon={<Upload size={18} />}>
            <InlineNotice message="플랫폼 관리자는 ArchDox 기본 템플릿, 기본 workflow, rule set, output layout을 관리해야 합니다. 사무소별 수정본은 사무소 운영 > 템플릿에서 오버라이드로 관리합니다." />
            <div className="metric-grid compact">
              <MetricCard icon={<FileText size={20} />} label="관리 범위" value="시스템 기본" detail="office_id 없는 기본 설정" tone="blue" />
              <MetricCard icon={<HardDrive size={20} />} label="사무소 수정본" value={data.offices.length} detail="각 사무소 override 대상" tone="slate" />
              <MetricCard icon={<Upload size={20} />} label="현재 UI" value="분리 필요" detail="플랫폼 전용 템플릿 API/UI 보강 대상" tone="amber" />
            </div>
          </Panel>
          <Panel title="템플릿 계층 정책" icon={<ShieldCheck size={18} />}>
            <Table
              columns={["계층", "관리자", "용도"]}
              empty="템플릿 정책이 없습니다."
              rows={[
                ["시스템 기본 템플릿", "플랫폼 관리자", "공식 기본 양식과 기본 렌더링 정책"],
                ["사무소 오버라이드", "사무소 OWNER/ADMIN", "사무소별 양식 수정본과 문구/레이아웃 조정"],
                ["개인 워크스페이스 템플릿", "개인 사용자", "개인 테스트 또는 개인 업무용 템플릿"]
              ]}
            />
          </Panel>
        </div>
      ) : null}
    </div>
  );
}

function WorkerGovernancePanel({ summary }: { summary: WorkerGovernanceSummary | null }) {
  if (!summary) {
    return (
      <Panel title="Worker 통제 지표" icon={<ShieldCheck size={18} />}>
        <EmptyState message="Worker 통제 지표를 불러오지 못했습니다." />
      </Panel>
    );
  }
  const actionDefinitions = summary.actionDefinitions ?? [];

  return (
    <div className="view-stack">
      <Panel
        title="Worker 통제 지표"
        icon={<ShieldCheck size={18} />}
        action={<span className="panel-context">{summary.days}일 기준</span>}
      >
        <div className="metric-grid compact">
          <MetricCard icon={<Activity size={20} />} label="Trace" value={summary.totalTraceEvents} detail={`${formatDate(summary.from)} 이후`} tone="blue" />
          <MetricCard icon={<ShieldCheck size={20} />} label="Catch rate" value={`${summary.catchRate}%`} detail={`${summary.policyDenied + summary.actionRejected + summary.actionUnknown}건 차단`} tone={summary.catchRate > 0 ? "amber" : "green"} />
          <MetricCard icon={<Clock3 size={20} />} label="Approval" value={`${summary.approvalRequiredRate}%`} detail={`${summary.approvalRequired}건 승인 필요`} tone="slate" />
          <MetricCard icon={<XCircle size={20} />} label="Cancel" value={summary.actionCancelled} detail="executor 실행 전 취소" tone={summary.actionCancelled > 0 ? "amber" : "green"} />
          <MetricCard icon={<AlertTriangle size={20} />} label="Failure" value={`${summary.failureRate}%`} detail={`${summary.actionFailed}건 실패`} tone={summary.actionFailed > 0 ? "red" : "green"} />
        </div>
        <InlineNotice message={summary.dataPolicy} />
      </Panel>

      <Panel title="Worker Action Registry" icon={<Command size={18} />} count={actionDefinitions.length}>
        <Table
          columns={["액션", "Owner", "상태", "Executor", "위험도", "승인", "Dry-run", "Source", "필수 Context"]}
          empty="등록된 Worker action이 없습니다."
          rows={sortWorkerActionDefinitions(actionDefinitions).map((definition) => [
            <CellTitle key="action" title={definition.actionType} subtitle={definition.description} />,
            displayLabel(definition.owner),
            <StatusBadge key="enabled" status={definition.enabled ? "ACTIVE" : "DISABLED"} />,
            <CellTitle
              key="executor"
              title={definition.executorRegistered ? "등록됨" : "미등록"}
              subtitle={definition.executorName || "-"}
            />,
            <StatusBadge key="risk" status={definition.riskLevel} />,
            definition.requiresApprovalByDefault ? "필요" : "기본 허용",
            definition.supportsDryRun ? "지원" : "-",
            definition.allowedSources.length > 0 ? definition.allowedSources.map(displayLabel).join(", ") : "전체",
            definition.requiredContextFields.length > 0 ? definition.requiredContextFields.join(", ") : "-"
          ])}
        />
        <InlineNotice message="이 표는 실행 이력이 아니라 현재 Worker가 알고 있는 action 계약입니다. enabled=false 또는 executor 미등록 action은 trace에 나타나더라도 실제 실행 대상이 아닙니다." />
      </Panel>

      <div className="dashboard-grid">
        <Panel title="게이트 판단 분포" icon={<Gauge size={18} />} count={summary.eventTypes.length}>
          <Table
            columns={["이벤트", "건수"]}
            empty="Worker trace 이벤트가 없습니다."
            rows={summary.eventTypes.slice(0, 20).map((group) => [
              <StatusBadge key="event" status={group.eventType ?? "UNKNOWN"} />,
              group.count
            ])}
          />
        </Panel>

        <Panel title="차단/승인/실패 이유" icon={<AlertTriangle size={18} />} count={summary.reasons.length}>
          <Table
            columns={["판단", "이유", "건수"]}
            empty="차단, 승인요구, 실패 이유가 없습니다."
            rows={summary.reasons.slice(0, 20).map((group) => [
              <StatusBadge key="event" status={group.eventType ?? "UNKNOWN"} />,
              group.reasonCode ?? "-",
              group.count
            ])}
          />
        </Panel>
      </div>

      <Panel title="액션별 trace" icon={<Command size={18} />} count={summary.actionEvents.length}>
        <Table
          columns={["액션", "이벤트", "건수"]}
          empty="액션별 trace가 없습니다."
          rows={summary.actionEvents.slice(0, 30).map((group) => [
            group.actionType ?? "-",
            <StatusBadge key="event" status={group.eventType ?? "UNKNOWN"} />,
            group.count
          ])}
        />
      </Panel>

      <Panel title="최근 Worker trace 샘플" icon={<Activity size={18} />} count={summary.recentEvents.length}>
        <Table
          columns={["시간", "이벤트", "사무소", "액션", "메시지"]}
          empty="최근 Worker trace가 없습니다."
          rows={summary.recentEvents.slice(0, 30).map((event) => [
            formatDate(event.createdAt),
            <StatusBadge key="event" status={event.eventType.replace("ARCHDOX_WORKER_", "")} />,
            event.officeId ? `#${event.officeId}` : "-",
            stringValue(asPlainObject(event.payload).actionType) || "-",
            event.message
          ])}
        />
      </Panel>
    </div>
  );
}

function sortWorkerActionDefinitions(definitions: WorkerActionDefinition[]) {
  return [...definitions].sort((left, right) => {
    const ownerOrder = left.owner.localeCompare(right.owner);
    return ownerOrder !== 0 ? ownerOrder : left.actionType.localeCompare(right.actionType);
  });
}

function FlowerRuntimePanel({
  dump,
  loading,
  onRefresh
}: {
  dump: FlowerRuntimeDump | null;
  loading: boolean;
  onRefresh: (silent?: boolean) => Promise<void>;
}) {
  const [polling, setPolling] = useState(false);
  const [intervalMs, setIntervalMs] = useState(3000);
  const activeWorkers = dump?.workers.filter((worker) => worker.activeFlowCount > 0) ?? [];
  const activeFlows = activeWorkers.flatMap((worker) => worker.flows.map((flow) => ({ worker, flow })));
  const executors = dump?.executors ?? [];
  const overloadEvents = dump?.overloadEvents ?? [];
  const runtimeDecision = dump ? flowerRuntimeDecision(dump, executors, overloadEvents) : null;

  useEffect(() => {
    if (!polling) {
      return;
    }
    const id = window.setInterval(() => {
      void onRefresh(true);
    }, Math.max(500, intervalMs));
    return () => window.clearInterval(id);
  }, [intervalMs, onRefresh, polling]);

  return (
    <div className="view-stack flower-runtime-view">
      <Panel
        title="Flower Runtime"
        icon={<Activity size={18} />}
        action={
          <div className="panel-action-row">
            <label className="flower-runtime-interval">
              <span>Interval</span>
              <input
                min={500}
                step={500}
                type="number"
                value={intervalMs}
                onChange={(event) => setIntervalMs(Math.max(500, Number(event.target.value) || 3000))}
              />
            </label>
            <button
              className={`button compact${polling ? "" : " primary"}`}
              onClick={() => {
                setPolling((value) => !value);
                if (!polling) {
                  void onRefresh(true);
                }
              }}
              type="button"
            >
              {polling ? <XCircle size={15} /> : <Activity size={15} />}
              {polling ? "Stop" : "Start"}
            </button>
            <button className="button compact" disabled={loading} onClick={() => onRefresh(false)} type="button">
              {loading ? <Loader2 className="spin" size={15} /> : <RefreshCcw size={15} />}
              Refresh
            </button>
          </div>
        }
      >
        {dump ? (
          <>
            <div className="metric-grid compact">
              <MetricCard icon={<Gauge size={20} />} label="Engine" value={dump.engineState} detail={formatDate(dump.capturedAt)} tone={dump.engineState === "RUNNING" ? "green" : "amber"} />
              <MetricCard icon={<Server size={20} />} label="Workers" value={dump.workerCount} detail={`${activeWorkers.length} active`} tone="blue" />
              <MetricCard icon={<Activity size={20} />} label="Flows" value={dump.activeFlowCount} detail="current active flows" tone={dump.activeFlowCount > 0 ? "amber" : "green"} />
              <MetricCard icon={<Command size={20} />} label="Async executors" value={dump.executorCount ?? executors.length} detail={`${dump.queuedTaskCount ?? 0} queued`} tone={(dump.saturatedExecutorCount ?? 0) > 0 ? "red" : "green"} />
              <MetricCard icon={<Clock3 size={20} />} label="Polling" value={polling ? "ON" : "OFF"} detail={`${intervalMs}ms`} tone={polling ? "green" : "slate"} />
            </div>
            {runtimeDecision ? <FlowerRuntimeDecisionView decision={runtimeDecision} /> : null}
            <Table
              columns={["Worker", "State", "Interval", "Active flows"]}
              empty="Flower worker가 없습니다."
              rows={dump.workers.map((worker) => [
                <CellTitle key="worker" title={worker.name} subtitle={worker.flows.map((flow) => flow.flowType).join(", ") || "active flow 없음"} />,
                <StatusBadge key="state" status={worker.state} />,
                `${worker.intervalMillis}ms`,
                worker.activeFlowCount
              ])}
            />
          </>
        ) : (
          <EmptyState message="Flower Runtime dump를 아직 불러오지 않았습니다." />
        )}
      </Panel>

      <Panel title="비동기 실행 Executor" icon={<Gauge size={18} />} count={executors.length}>
        <Table
          columns={["작업 풀", "상태", "현재/생성 스레드", "대기열", "완료/전체", "생명주기"]}
          empty="Runtime executor 상태가 아직 없습니다."
          rows={executors.map((executor) => [
            <CellTitle key="executor" title={displayExecutorName(executor.beanName)} subtitle={`${executor.beanName} / ${executor.queueType}`} />,
            <StatusBadge key="state" status={executor.state} />,
            `${executor.activeCount}/${executor.poolSize} active · max ${executor.maximumPoolSize}`,
            executorQueueDetail(executor),
            `${executor.completedTaskCount}/${executor.taskCount}`,
            executor.terminated ? "terminated" : executor.shutdown ? "shutdown" : "running"
          ])}
        />
      </Panel>

      <Panel title="최근 과부하 이벤트" icon={<AlertTriangle size={18} />} count={overloadEvents.length}>
        <Table
          columns={["시간", "이벤트", "심각도", "Workflow", "메시지"]}
          empty="최근 runtime 과부하 이벤트가 없습니다."
          rows={overloadEvents.map((event) => [
            formatDate(event.createdAt),
            <StatusBadge key="event" status={event.eventType} />,
            <StatusBadge key="severity" status={event.severity} />,
            event.workflowType ?? "-",
            event.message
          ])}
        />
      </Panel>

      <Panel title="Active Flower Flows" icon={<Command size={18} />} count={activeFlows.length}>
        {activeFlows.length === 0 ? (
          <InlineNotice message="현재 실행 중인 Flower flow가 없습니다." />
        ) : (
          <div className="flower-flow-list">
            {activeFlows.map(({ worker, flow }) => (
              <FlowerRuntimeFlowItem flow={flow} key={`${worker.name}:${flow.flowType}:${flow.flowKey}`} worker={worker} />
            ))}
          </div>
        )}
      </Panel>
    </div>
  );
}

type FlowerRuntimeDecision = {
  tone: "green" | "amber" | "red" | "slate";
  title: string;
  summary: string;
  reason: string;
  action: string;
  checks: string[];
};

function FlowerRuntimeDecisionView({ decision }: { decision: FlowerRuntimeDecision }) {
  return (
    <div className={`flower-runtime-decision ${decision.tone}`}>
      <div className="flower-runtime-decision-main">
        <StatusIcon status={decision.tone === "red" ? "FAILED" : decision.tone === "amber" ? "WARN" : "PASS"} />
        <div>
          <strong>{decision.title}</strong>
          <span>{decision.summary}</span>
        </div>
      </div>
      <div className="flower-runtime-decision-detail">
        <div>
          <small>판정 이유</small>
          <p>{decision.reason}</p>
        </div>
        <div>
          <small>다음 조치</small>
          <p>{decision.action}</p>
        </div>
      </div>
      <div className="flower-runtime-checks">
        {decision.checks.map((check) => (
          <span key={check}>{check}</span>
        ))}
      </div>
    </div>
  );
}

function flowerRuntimeDecision(
  dump: FlowerRuntimeDump,
  executors: FlowerRuntimeExecutor[],
  overloadEvents: OperationEvent[]
): FlowerRuntimeDecision {
  const saturated = executors.filter((executor) => executor.state === "SATURATED");
  const active = executors.filter((executor) => executor.state === "ACTIVE" || executor.state === "SATURATED");
  const queuedTaskCount = dump.queuedTaskCount ?? executors.reduce((sum, executor) => sum + executor.queueSize, 0);
  const checks = [
    `workers ${dump.workerCount}`,
    `flows ${dump.activeFlowCount}`,
    `executors ${dump.executorCount ?? executors.length}`,
    `queued ${queuedTaskCount}`,
    `overload events ${overloadEvents.length}`
  ];

  if (dump.engineState !== "RUNNING") {
    return {
      tone: "red",
      title: "Flower 엔진 확인 필요",
      summary: `현재 engine 상태가 ${dump.engineState}입니다.`,
      reason: "Flower worker가 flow를 계속 진행하지 못할 수 있습니다.",
      action: "서버 런타임 상태와 최근 배포/재시작 로그를 먼저 확인하세요.",
      checks
    };
  }

  if (saturated.length > 0) {
    const pools = saturated.map((executor) => displayExecutorName(executor.beanName)).join(", ");
    return {
      tone: "red",
      title: "비동기 Executor 포화",
      summary: `${pools} 대기열이 가득 찼습니다.`,
      reason: "해당 작업 풀에 새 작업이 들어오면 거절되거나 지연될 수 있습니다.",
      action: "같은 시간대의 과부하 이벤트와 작업 요청량을 확인하고, 필요하면 executor limit 또는 호출 빈도를 조정하세요.",
      checks
    };
  }

  if (overloadEvents.length > 0) {
    return {
      tone: "amber",
      title: "최근 과부하 이벤트 있음",
      summary: `최근 runtime 과부하 이벤트 ${overloadEvents.length}건이 기록되었습니다.`,
      reason: "현재는 포화 상태가 아니더라도 짧은 시간 안에 queue full 또는 호출 제한이 발생했습니다.",
      action: "아래 이벤트의 workflow와 메시지를 보고 어떤 lane에서 반복되는지 확인하세요.",
      checks
    };
  }

  if (queuedTaskCount > 0) {
    return {
      tone: "amber",
      title: "처리 대기 있음",
      summary: `${queuedTaskCount}개 작업이 executor queue에서 대기 중입니다.`,
      reason: "작업은 접수됐고 실행 순서를 기다리는 상태입니다.",
      action: "대기 수가 계속 증가하는지 polling을 켜고 확인하세요. 일시적이면 정상 처리 중입니다.",
      checks
    };
  }

  if (active.length > 0 || dump.activeFlowCount > 0) {
    return {
      tone: "green",
      title: "정상 처리 중",
      summary: "실행 중인 flow 또는 executor 작업이 있지만 queue 포화는 없습니다.",
      reason: "현재 작업은 처리 중이고, 새 작업을 받을 여유가 남아 있습니다.",
      action: "특별한 조치는 필요 없습니다. 느려 보이면 active flow 상세에서 현재 step을 확인하세요.",
      checks
    };
  }

  return {
    tone: "green",
    title: "정상 대기",
    summary: "실행 중인 flow와 executor 대기열이 없습니다.",
    reason: "Flower runtime과 실행 lane이 idle 상태입니다.",
    action: "새 요청이 들어오면 worker flow와 executor 상태가 이 화면에 표시됩니다.",
    checks
  };
}

function displayExecutorName(beanName: string) {
  return beanName
    .replace(/Executor$/, "")
    .replace(/([a-z0-9])([A-Z])/g, "$1-$2")
    .toLowerCase();
}

function executorQueueDetail(executor: { queueSize: number; remainingQueueCapacity?: number | null }) {
  const remaining = executor.remainingQueueCapacity;
  if (remaining === null || remaining === undefined) {
    return `${executor.queueSize} queued`;
  }
  return `${executor.queueSize} queued · ${remaining} remaining`;
}

function FlowerRuntimeFlowItem({ flow, worker }: { flow: FlowerRuntimeFlow; worker: FlowerRuntimeWorker }) {
  const contextPairs = flowerContextPairs(flow);
  return (
    <div className="flower-flow-item">
      <div className="flower-flow-header">
        <div>
          <strong>{flow.flowType}</strong>
          <span>{flow.flowKey}</span>
        </div>
        <div className="flower-flow-badges">
          <StatusBadge status={flow.state} />
          <StatusBadge status={worker.name} />
        </div>
      </div>

      <div className="flower-flow-context">
        <span>current {flow.currentStepId ?? "-"}</span>
        <span>step #{flow.currentStepNo}</span>
        {contextPairs.map(([label, value]) => (
          <span key={label}>{label} {value}</span>
        ))}
      </div>

      {flow.failureType || flow.failureMessage ? (
        <InlineNotice message={`${flow.failureType || "Flow failure"} ${flow.failureMessage || ""}`.trim()} />
      ) : null}

      <div className="flower-step-list">
        {flow.steps.map((step) => (
          <div className={`flower-step-pill${step.current ? " current" : ""}`} key={`${flow.flowKey}:${step.stepId}`}>
            <span>{step.index + 1}</span>
            <strong>{step.stepId}</strong>
            <em>{step.stepType}</em>
            <small>
              {step.guarded ? "guarded" : "unguarded"} / {step.recoverable ? "recoverable" : "plain"}
              {step.recoveryPolicy ? ` / ${step.recoveryPolicy}` : ""}
            </small>
          </div>
        ))}
      </div>
    </div>
  );
}

function flowerContextPairs(flow: FlowerRuntimeFlow) {
  const context = flow.executionContext ?? {};
  return [
    ["tenant", context.tenantId],
    ["user", context.userId],
    ["session", context.sessionId],
    ["run", context.runId],
    ["trace", context.traceId],
    ["corr", context.correlationId]
  ].filter((entry): entry is [string, string] => Boolean(entry[1]));
}

function WorkerApprovalPanel({
  approvals,
  busy,
  onApprove,
  onReject
}: {
  approvals: WorkerApprovalRequest[];
  busy: boolean;
  onApprove: (approvalRequestId: number) => Promise<void>;
  onReject: (approvalRequestId: number) => Promise<void>;
}) {
  const pending = approvals.filter((approval) => approval.status === "PENDING").length;
  const approved = approvals.filter((approval) => approval.status === "APPROVED").length;
  const rejected = approvals.filter((approval) => approval.status === "REJECTED").length;
  const expired = approvals.filter((approval) => approval.status === "EXPIRED").length;

  return (
    <div className="view-stack">
      <Panel
        title="Worker 승인 대기"
        icon={<ShieldCheck size={18} />}
        action={<span className="panel-context">최근 {approvals.length}건</span>}
      >
        <div className="metric-grid compact">
          <MetricCard icon={<Clock3 size={20} />} label="대기" value={pending} detail="실행 전 승인 필요" tone={pending > 0 ? "amber" : "green"} />
          <MetricCard icon={<CheckCircle2 size={20} />} label="승인" value={approved} detail="승인 후 재실행" tone="green" />
          <MetricCard icon={<XCircle size={20} />} label="반려" value={rejected} detail="실행 차단" tone={rejected > 0 ? "red" : "slate"} />
          <MetricCard icon={<AlertTriangle size={20} />} label="만료" value={expired} detail="재요청 필요" tone={expired > 0 ? "amber" : "slate"} />
        </div>
        <InlineNotice message="승인 요청은 Worker trace에서 생성됩니다. 원문 trace와 실행 이력은 operation_events에 남고, 이 화면은 승인 상태와 재실행 연결만 관리합니다." />
      </Panel>

      <Panel title="Worker 승인 요청" icon={<Command size={18} />} count={approvals.length}>
        <Table
          columns={["요청", "상태", "사무소", "컨텍스트", "사유", "요청일", "결정", "작업"]}
          empty="승인이 필요한 Worker action이 없습니다."
          rows={approvals.map((approval) => [
            <CellTitle
              key="approval"
              title={approval.actionType}
              subtitle={`${approval.requestSource} / ${approval.actionOrigin} / #${approval.id}`}
            />,
            <StatusBadge key="status" status={approval.status} />,
            approval.officeId ? `#${approval.officeId}` : "-",
            contextSummary(approval),
            <CellTitle
              key="reason"
              title={approval.decisionCode ?? approval.actionReason ?? "-"}
              subtitle={approval.decisionMessage ?? payloadPreview(approval.actionPayload)}
            />,
            formatDate(approval.requestedAt),
            approval.decidedAt ? (
              <CellTitle key="decision" title={formatDate(approval.decidedAt)} subtitle={approval.decisionReason ?? "-"} />
            ) : (
              "-"
            ),
            approval.status === "PENDING" ? (
              <div className="card-actions compact" key="actions">
                <button className="button compact" disabled={busy} onClick={() => void onApprove(approval.id)} type="button">
                  승인
                </button>
                <button className="button compact danger" disabled={busy} onClick={() => void onReject(approval.id)} type="button">
                  반려
                </button>
              </div>
            ) : (
              <StatusBadge key="done" status={approval.status} />
            )
          ])}
        />
      </Panel>
    </div>
  );
}

function contextSummary(approval: WorkerApprovalRequest) {
  const parts = [
    approval.userId ? `user #${approval.userId}` : "",
    approval.projectId ? `project #${approval.projectId}` : "",
    approval.siteId ? `site #${approval.siteId}` : "",
    approval.reportId ? `report #${approval.reportId}` : "",
    approval.documentJobId ? `job #${approval.documentJobId}` : ""
  ].filter(Boolean);
  return parts.length ? parts.join(" / ") : "-";
}

function payloadPreview(payload: Record<string, unknown>) {
  const json = JSON.stringify(payload ?? {});
  return json.length > 120 ? `${json.slice(0, 120)}...` : json;
}

function EngineApiKeyManagementPanel({
  busy,
  keys,
  usageEvents,
  usageSummary,
  offices,
  users,
  issuedKey,
  onCreate,
  onDismissIssuedKey,
  onRevoke
}: {
  busy: boolean;
  keys: EngineApiKey[];
  usageEvents: EngineApiUsageEvent[];
  usageSummary: EngineApiUsageSummary | null;
  offices: PlatformOfficeOps[];
  users: PlatformUserOps[];
  issuedKey: CreateEngineApiKeyResponse | null;
  onCreate: (body: {
    displayName: string;
    ownerUserId: number;
    officeId?: number | null;
    scopes: string[];
    dailyRequestUnitLimit?: number | null;
    expiresAt?: string | null;
  }) => Promise<void>;
  onDismissIssuedKey: () => void;
  onRevoke: (apiKeyId: number) => Promise<void>;
}) {
  const [eventFilter, setEventFilter] = useState<EngineUsageEventFilter>("ALL");
  const [selectedEventId, setSelectedEventId] = useState<number | null>(null);
  const activeCount = keys.filter((key) => key.status === "ACTIVE").length;
  const revokedCount = keys.filter((key) => key.status === "REVOKED").length;
  const mcpEvents = usageEvents.filter(isMcpUsageEvent);
  const engineEvents = usageEvents.filter(isEngineUsageEvent);
  const legalEvents = usageEvents.filter(isLegalUsageEvent);
  const failedEvents = usageEvents.filter((event) => event.status !== "SUCCEEDED");
  const observableEvents = useMemo(
    () => usageEvents.filter((event) => usageEventMatchesFilter(event, eventFilter)),
    [eventFilter, usageEvents]
  );
  const selectedEvent = observableEvents.find((event) => event.id === selectedEventId) ?? observableEvents[0] ?? null;

  useEffect(() => {
    if (selectedEventId !== null && !observableEvents.some((event) => event.id === selectedEventId)) {
      setSelectedEventId(null);
    }
  }, [observableEvents, selectedEventId]);

  return (
    <div className="view-stack">
      <div className="engine-key-grid">
        <Panel title="Engine API Key 발급" icon={<KeyRound size={18} />}>
          <EngineApiKeyCreateForm busy={busy} users={users} offices={offices} onSubmit={onCreate} />
          {issuedKey ? (
            <div className="issued-key-card">
              <div>
                <strong>새 API Key가 발급되었습니다.</strong>
                <span>이 값은 지금 한 번만 표시됩니다. 외부 Agent나 MCP 테스트 설정에 보관하세요.</span>
              </div>
              <code>{issuedKey.apiKey}</code>
              <div className="card-actions">
                <button className="button compact" type="button" onClick={() => void navigator.clipboard.writeText(issuedKey.apiKey)}>
                  <Copy size={14} />
                  복사
                </button>
                <button className="button compact" type="button" onClick={onDismissIssuedKey}>
                  확인
                </button>
              </div>
            </div>
          ) : (
            <InlineNotice message="발급된 API Key 원문은 DB에 저장하지 않습니다. 잃어버리면 새 키를 발급하고 기존 키를 폐기하세요." />
          )}
        </Panel>

        <Panel title="Engine API Key 상태" icon={<Gauge size={18} />}>
          <div className="metric-grid compact">
            <MetricCard icon={<KeyRound size={20} />} label="전체 키" value={keys.length} detail="최근 200개" tone="blue" />
            <MetricCard icon={<CheckCircle2 size={20} />} label="활성" value={activeCount} detail="외부 호출 가능" tone="green" />
            <MetricCard icon={<XCircle size={20} />} label="폐기" value={revokedCount} detail="사용 불가" tone="red" />
          </div>
          <InlineAlert message="현재는 review-session 테스트용 Foundation입니다. 고객별 quota, billing, developer portal은 다음 단계에서 붙입니다." />
        </Panel>
      </div>

      <Panel title="발급된 Engine API Key" icon={<KeyRound size={18} />} count={keys.length}>
        <Table
          columns={["키", "상태", "소유자", "사무소", "스코프", "최근 사용", "만료", "작업"]}
          empty="발급된 Engine API Key가 없습니다."
          rows={keys.map((key) => [
            <CellTitle key="key" title={key.displayName} subtitle={`${key.maskedKey} / #${key.id}`} />,
            <StatusBadge key="status" status={key.status} />,
            userLabel(users, key.ownerUserId),
            key.officeId ? officeLabel(offices, key.officeId) : "-",
            key.scopes.join(", "),
            formatDate(key.lastUsedAt),
            formatDate(key.expiresAt),
            key.status === "ACTIVE" ? (
              <button className="button compact danger" disabled={busy} onClick={() => onRevoke(key.id)} type="button">
                폐기
              </button>
            ) : (
              <StatusBadge status="REVOKED" />
            )
          ])}
        />
      </Panel>

      <Panel title="Engine / MCP usage" icon={<Activity size={18} />} count={usageSummary?.groups.length ?? 0}>
        <div className="metric-grid compact">
          <MetricCard icon={<Activity size={20} />} label="Events" value={usageSummary?.totalEventCount ?? 0} detail="selected range" tone="blue" />
          <MetricCard icon={<Gauge size={20} />} label="Request units" value={usageSummary?.totalRequestUnits ?? 0} detail="quota units" tone="amber" />
          <MetricCard icon={<ShieldCheck size={20} />} label="Engine REST" value={engineEvents.length} detail="recent 100 events" tone="green" />
          <MetricCard icon={<Command size={20} />} label="MCP calls" value={mcpEvents.length} detail="recent 100 events" tone="slate" />
          <MetricCard icon={<FileText size={20} />} label="Legal-backed" value={legalEvents.length} detail="legal scope/reference" tone="blue" />
          <MetricCard icon={<AlertTriangle size={20} />} label="Failed" value={failedEvents.length} detail="FAILED or DENIED" tone={failedEvents.length > 0 ? "red" : "green"} />
        </div>
        <Table
          columns={["Capability", "Operation", "Key", "Events", "Units", "Last call"]}
          empty="Engine API usage summary is empty."
          rows={(usageSummary?.groups ?? []).slice(0, 30).map((group) => [
            <StatusBadge key="capability" status={group.capability} />,
            group.operation,
            <CellTitle key="key" title={group.keyId} subtitle={`API Key #${group.apiKeyId}`} />,
            group.eventCount.toLocaleString(),
            group.requestUnits.toLocaleString(),
            formatDate(group.lastCalledAt)
          ])}
        />
      </Panel>

      <Panel title="Recent Engine / MCP calls" icon={<Command size={18} />} count={observableEvents.length}>
        <div className="usage-filter-row">
          {engineUsageEventFilters.map((filter) => (
            <button
              className={`button compact${eventFilter === filter ? " primary" : ""}`}
              key={filter}
              onClick={() => setEventFilter(filter)}
              type="button"
            >
              {engineUsageEventFilterLabel(filter)}
              <span>{usageEvents.filter((event) => usageEventMatchesFilter(event, filter)).length}</span>
            </button>
          ))}
        </div>
        <Table
          columns={["Time", "Status", "Source", "Operation / Tool", "Key", "Trace / Legal", "상세"]}
          empty="Recent Engine / MCP call logs are empty."
          rows={observableEvents.slice(0, 50).map((event) => [
            formatDate(event.createdAt),
            <StatusBadge key="status" status={event.status} />,
            <CellTitle key="source" title={usageEventSource(event)} subtitle={event.capability} />,
            <CellTitle key="operation" title={usageEventTool(event)} subtitle={event.operation} />,
            <CellTitle key="key" title={event.keyId} subtitle={event.officeId ? officeLabel(offices, event.officeId) : "-"} />,
            <CellTitle
              key="trace"
              title={usageEventTraceTitle(event)}
              subtitle={usageEventTraceSubtitle(event)}
            />,
            <button className="button compact" key="detail" onClick={() => setSelectedEventId(event.id)} type="button">
              보기
            </button>
          ])}
        />
      </Panel>

      <Panel
        title="Engine / MCP call detail"
        icon={<Activity size={18} />}
        action={selectedEvent ? <span className="panel-context">Event #{selectedEvent.id}</span> : null}
      >
        <EngineUsageEventDetailPanel event={selectedEvent} offices={offices} users={users} />
      </Panel>
    </div>
  );
}

const engineUsageEventFilters: EngineUsageEventFilter[] = ["ALL", "ENGINE", "MCP", "LEGAL", "FAILED"];

function engineUsageEventFilterLabel(filter: EngineUsageEventFilter) {
  const labels: Record<EngineUsageEventFilter, string> = {
    ALL: "전체",
    ENGINE: "Engine REST",
    MCP: "MCP",
    LEGAL: "Legal",
    FAILED: "실패"
  };
  return labels[filter];
}

function usageEventMatchesFilter(event: EngineApiUsageEvent, filter: EngineUsageEventFilter) {
  switch (filter) {
    case "ENGINE":
      return isEngineUsageEvent(event);
    case "MCP":
      return isMcpUsageEvent(event);
    case "LEGAL":
      return isLegalUsageEvent(event);
    case "FAILED":
      return event.status !== "SUCCEEDED";
    default:
      return true;
  }
}

function isMcpUsageEvent(event: EngineApiUsageEvent) {
  return usageEventSource(event) === "MCP";
}

function isEngineUsageEvent(event: EngineApiUsageEvent) {
  return event.capability === "ENGINE_REVIEW_SESSION" && !isMcpUsageEvent(event);
}

function isLegalUsageEvent(event: EngineApiUsageEvent) {
  return (
    event.capability.startsWith("LEGAL_") ||
    metadataNumber(event.metadata, "legalReferenceCount") > 0 ||
    metadataList(event.metadata, "legalReferenceIds").length > 0
  );
}

function usageEventSource(event: EngineApiUsageEvent) {
  const source = metadataText(event.metadata, "source");
  if (source) {
    return source;
  }
  if (event.capability.startsWith("LEGAL_")) {
    return "LEGAL_API";
  }
  if (event.capability === "ENGINE_REVIEW_SESSION") {
    return "ENGINE_REST";
  }
  return "ENGINE_API";
}

function usageEventTool(event: EngineApiUsageEvent) {
  return metadataText(event.metadata, "toolName") || event.operation;
}

function usageEventTraceTitle(event: EngineApiUsageEvent) {
  return (
    metadataText(event.metadata, "correlationId") ||
    event.reviewSessionId ||
    metadataText(event.metadata, "jsonRpcId") ||
    "-"
  );
}

function usageEventTraceSubtitle(event: EngineApiUsageEvent) {
  const legalCount = metadataNumber(event.metadata, "legalReferenceCount");
  if (legalCount > 0) {
    const sources = metadataList(event.metadata, "legalReferenceSources").join(", ");
    return `${legalCount.toLocaleString()} legal refs${sources ? ` / ${sources}` : ""}`;
  }
  return (
    metadataText(event.metadata, "errorCode") ||
    metadataText(event.metadata, "accessMode") ||
    metadataText(event.metadata, "engineStatus") ||
    "-"
  );
}

function EngineUsageEventDetailPanel({
  event,
  offices,
  users
}: {
  event: EngineApiUsageEvent | null;
  offices: PlatformOfficeOps[];
  users: PlatformUserOps[];
}) {
  if (!event) {
    return <EmptyState message="표시할 Engine / MCP 호출 로그가 없습니다." />;
  }

  const metadata = event.metadata ?? {};
  const legalReferenceIds = metadataList(metadata, "legalReferenceIds");
  const legalReferenceSources = metadataList(metadata, "legalReferenceSources");
  const findingCodes = metadataList(metadata, "findingCodes");
  const legalReferenceCount = metadataNumber(metadata, "legalReferenceCount", legalReferenceIds.length);
  const errorCode = metadataText(metadata, "errorCode");
  const errorMessage = metadataText(metadata, "errorMessage");

  return (
    <div className="engine-event-detail">
      <div className="ops-detail-grid engine-event-metrics">
        <MetricCard icon={<Activity size={20} />} label="상태" value={displayLabel(event.status)} detail={usageEventSource(event)} tone={event.status === "SUCCEEDED" ? "green" : "red"} />
        <MetricCard icon={<Gauge size={20} />} label="Request units" value={event.requestUnits} detail={event.capability} tone="amber" />
        <MetricCard icon={<FileText size={20} />} label="Legal refs" value={legalReferenceCount} detail={legalReferenceSources.join(", ") || "근거 없음"} tone={legalReferenceCount > 0 ? "blue" : "slate"} />
        <MetricCard icon={<AlertTriangle size={20} />} label="Findings" value={metadataNumber(metadata, "findingCount", findingCodes.length)} detail={findingCodes.slice(0, 2).join(", ") || "없음"} tone={findingCodes.length > 0 ? "amber" : "green"} />
      </div>

      <dl className="ops-fact-list engine-event-facts">
        <div>
          <dt>호출</dt>
          <dd>{usageEventTool(event)} / {event.operation}</dd>
        </div>
        <div>
          <dt>API Key</dt>
          <dd>{event.keyId} / #{event.apiKeyId}</dd>
        </div>
        <div>
          <dt>소유자</dt>
          <dd>{userLabel(users, event.ownerUserId)}</dd>
        </div>
        <div>
          <dt>사무소</dt>
          <dd>{officeLabel(offices, event.officeId)}</dd>
        </div>
        <div>
          <dt>Review</dt>
          <dd>{event.reviewSessionId || "-"}</dd>
        </div>
        <div>
          <dt>Trace</dt>
          <dd>{usageEventTraceTitle(event)}</dd>
        </div>
        <div>
          <dt>Scope</dt>
          <dd>{metadataText(metadata, "requiredScope") || event.capability}</dd>
        </div>
        <div>
          <dt>Client</dt>
          <dd>{metadataText(metadata, "remoteIp") || "-"} / {metadataText(metadata, "userAgent") || "-"}</dd>
        </div>
        <div>
          <dt>오류</dt>
          <dd>{errorCode || "-"}{errorMessage ? ` / ${errorMessage}` : ""}</dd>
        </div>
        <div>
          <dt>생성일</dt>
          <dd>{formatDate(event.createdAt)}</dd>
        </div>
      </dl>

      {legalReferenceIds.length > 0 ? (
        <div className="engine-reference-list">
          <strong>법령 근거</strong>
          {legalReferenceIds.map((referenceId, index) => (
            <span key={`${referenceId}-${index}`}>
              {referenceId}
              {legalReferenceSources[index] ? ` / ${legalReferenceSources[index]}` : ""}
            </span>
          ))}
        </div>
      ) : (
        <InlineNotice message="이 호출에는 usage metadata 기준 법령 reference가 연결되지 않았습니다." />
      )}

      <pre className="metadata-json">{compactJson(metadata, 2600)}</pre>
    </div>
  );
}

function metadataText(metadata: Record<string, unknown> | undefined, key: string) {
  const value = metadata?.[key];
  if (value === undefined || value === null) {
    return "";
  }
  return String(value);
}

function metadataNumber(metadata: Record<string, unknown> | undefined, key: string, fallback = 0) {
  const value = metadata?.[key];
  if (typeof value === "number" && Number.isFinite(value)) {
    return value;
  }
  if (typeof value === "string") {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : fallback;
  }
  return fallback;
}

function metadataList(metadata: Record<string, unknown> | undefined, key: string) {
  const value = metadata?.[key];
  if (Array.isArray(value)) {
    return value.map((item) => stringValue(item)).filter(Boolean);
  }
  if (typeof value === "string" && value.trim()) {
    return value.split(",").map((item) => item.trim()).filter(Boolean);
  }
  return [];
}

function legalArticleLabel(articleNo?: string | null, articleTitle?: string | null, articleKey?: string | null) {
  const no = articleNo?.trim();
  const title = articleTitle?.trim();
  if (no && title) {
    return `${no} ${title}`;
  }
  return no || title || articleKey?.trim() || "조문";
}

function shortHash(value?: string | null) {
  if (!value) {
    return "-";
  }
  return value.length > 10 ? value.slice(0, 10) : value;
}

function compactJson(value: unknown, maxLength = 1200) {
  const text = JSON.stringify(value ?? {}, null, 2);
  return text.length > maxLength ? `${text.slice(0, maxLength)}\n...` : text;
}

function EngineApiKeyCreateForm({
  busy,
  offices,
  users,
  onSubmit
}: {
  busy: boolean;
  offices: PlatformOfficeOps[];
  users: PlatformUserOps[];
  onSubmit: (body: {
    displayName: string;
    ownerUserId: number;
    officeId?: number | null;
    scopes: string[];
    dailyRequestUnitLimit?: number | null;
    expiresAt?: string | null;
  }) => Promise<void>;
}) {
  const [displayName, setDisplayName] = useState("Codex Engine API Key");
  const [ownerUserId, setOwnerUserId] = useState<number | "">(users[0]?.id ?? "");
  const [officeId, setOfficeId] = useState<number | "">("");
  const [allScopes, setAllScopes] = useState(false);
  const [dailyRequestUnitLimit, setDailyRequestUnitLimit] = useState(1000);
  const [expiresAt, setExpiresAt] = useState("");

  useEffect(() => {
    if (ownerUserId === "" && users[0]) {
      setOwnerUserId(users[0].id);
    }
  }, [ownerUserId, users]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (ownerUserId === "") {
      return;
    }
    await onSubmit({
      displayName: normalizeFormValue(displayName) ?? "Engine API Key",
      ownerUserId,
      officeId: officeId === "" ? null : officeId,
      scopes: allScopes ? ["ALL"] : ["ENGINE_REVIEW_SESSION", "LEGAL_UPDATES", "LEGAL_SEARCH"],
      dailyRequestUnitLimit,
      expiresAt: expiresAt ? new Date(expiresAt).toISOString() : null
    });
  }

  if (users.length === 0) {
    return <EmptyState message="키를 발급할 사용자 계정이 없습니다." />;
  }

  return (
    <form className="engine-key-form" onSubmit={submit}>
      <label>
        표시명
        <input value={displayName} onChange={(event) => setDisplayName(event.target.value)} required />
      </label>
      <label>
        소유자
        <select value={ownerUserId} onChange={(event) => setOwnerUserId(Number(event.target.value))} required>
          {users.map((user) => (
            <option key={user.id} value={user.id}>
              {user.name} / {user.email} / #{user.id}
            </option>
          ))}
        </select>
      </label>
      <label>
        사무소 연결
        <select value={officeId} onChange={(event) => setOfficeId(event.target.value ? Number(event.target.value) : "")}>
          <option value="">없음</option>
          {offices.map((office) => (
            <option key={office.id} value={office.id}>
              {office.displayName} / {office.officeCode} / #{office.id}
            </option>
          ))}
        </select>
      </label>
      <label>
        만료일
        <input type="datetime-local" value={expiresAt} onChange={(event) => setExpiresAt(event.target.value)} />
      </label>
      <label>
        1??request unit ?쒕룄
        <input
          min={1}
          type="number"
          value={dailyRequestUnitLimit}
          onChange={(event) => setDailyRequestUnitLimit(Math.max(1, Number(event.target.value) || 1))}
        />
      </label>
      <label className="toggle-row">
        <input type="checkbox" checked={allScopes} onChange={(event) => setAllScopes(event.target.checked)} />
        ALL 스코프로 발급
      </label>
      <div className="policy-note">기본 스코프는 ENGINE_REVIEW_SESSION, LEGAL_UPDATES, LEGAL_SEARCH입니다. 외부 문서 검토와 법령 업데이트/법령 검색 MCP 조회 테스트에는 기본값을 권장합니다.</div>
      <button className="button primary" disabled={busy || ownerUserId === ""} type="submit">
        {busy ? <Loader2 className="spin" size={16} /> : <Plus size={16} />}
        API Key 발급
      </button>
    </form>
  );
}

function PlatformOpsDiagnosisDetailPanel({
  run,
  incident
}: {
  run: PlatformOpsRun | null;
  incident: PlatformOpsIncident | null;
}) {
  if (!run) {
    return (
      <Panel title="운영 진단 상세" icon={<Gauge size={18} />}>
        <EmptyState message="아직 운영 진단 Flow가 없습니다. 운영 이슈에서 진단을 실행하면 여기에 결과가 표시됩니다." />
      </Panel>
    );
  }

  const snapshot = asPlainObject(run.inputSnapshotJson);
  const recentFindings = asObjectArray(snapshot.recentFindings);
  const relatedEvents = asObjectArray(snapshot.relatedOperationEvents);
  const redactionPolicy = asPlainObject(snapshot.redactionPolicy);
  const nextAiHarness = asPlainObject(snapshot.nextAiHarness);

  return (
    <Panel
      title="운영 진단 상세"
      icon={<Gauge size={18} />}
      action={<span className="panel-context">Flow #{run.id}</span>}
    >
      <div className="ops-diagnosis-detail">
        <div className="ops-detail-grid">
          <MetricCard icon={<Gauge size={20} />} label="상태" value={displayLabel(run.status)} detail={stringValue(snapshot.state) || "-"} tone="blue" />
          <MetricCard icon={<AlertTriangle size={20} />} label="Incident" value={run.incidentId ? `#${run.incidentId}` : "-"} detail={incident?.title ?? "연결된 이슈 없음"} tone="amber" />
          <MetricCard icon={<Activity size={20} />} label="Finding" value={numberValue(snapshot.recentFindingCount, recentFindings.length)} detail="진단 입력에 포함" tone="slate" />
          <MetricCard icon={<Clock3 size={20} />} label="Event" value={numberValue(snapshot.relatedOperationEventCount, relatedEvents.length)} detail="관련 운영 이벤트" tone="green" />
        </div>

        <div className="dashboard-grid">
          <div className="ops-snapshot-card">
            <div className="ops-snapshot-heading">
              <strong>진단 입력 요약</strong>
              <span>{stringValue(snapshot.diagnosedAt) || formatDate(run.completedAt) || "준비 중"}</span>
            </div>
            <dl className="ops-fact-list">
              <div>
                <dt>진단 방식</dt>
                <dd>{displayLabel(stringValue(snapshot.diagnosisType) || "DETERMINISTIC_FIRST")}</dd>
              </div>
              <div>
                <dt>대상</dt>
                <dd>{incident ? `${incident.primaryResourceType ?? "RESOURCE"} #${incident.primaryResourceId ?? "-"}` : "-"}</dd>
              </div>
              <div>
                <dt>AI 하네스</dt>
                <dd>{stringValue(nextAiHarness.status) || "미연결"} / {stringValue(nextAiHarness.type) || "OpsDiagnosisHarness"}</dd>
              </div>
              <div>
                <dt>실행자</dt>
                <dd>{run.startedByUserId ? `#${run.startedByUserId}` : "-"}</dd>
              </div>
            </dl>
          </div>

          <div className="ops-snapshot-card">
            <div className="ops-snapshot-heading">
              <strong>Redaction 정책</strong>
              <span>AI/운영 분석 입력 제한</span>
            </div>
            <dl className="ops-fact-list">
              <div>
                <dt>Secrets</dt>
                <dd>{stringValue(redactionPolicy.secrets) || "excluded"}</dd>
              </div>
              <div>
                <dt>Raw files</dt>
                <dd>{stringValue(redactionPolicy.rawFiles) || "excluded"}</dd>
              </div>
              <div>
                <dt>Tokens</dt>
                <dd>{stringValue(redactionPolicy.tokens) || "excluded"}</dd>
              </div>
              <div>
                <dt>Scope</dt>
                <dd>{stringValue(redactionPolicy.scope) || "redacted operational snapshot only"}</dd>
              </div>
            </dl>
          </div>
        </div>

        <div className="dashboard-grid">
          <div className="ops-snapshot-card">
            <div className="ops-snapshot-heading">
              <strong>최근 Finding</strong>
              <span>{recentFindings.length}개</span>
            </div>
            <OpsSnapshotList
              empty="진단 스냅샷에 포함된 finding이 없습니다."
              items={recentFindings}
              render={(finding) => (
                <>
                  <strong>{stringValue(finding.title) || stringValue(finding.code) || "Finding"}</strong>
                  <span>{stringValue(finding.severity) || "-"} · {stringValue(finding.message) || "-"}</span>
                </>
              )}
            />
          </div>

          <div className="ops-snapshot-card">
            <div className="ops-snapshot-heading">
              <strong>관련 Operation Event</strong>
              <span>{relatedEvents.length}개</span>
            </div>
            <OpsSnapshotList
              empty="관련 operation event가 없습니다."
              items={relatedEvents}
              render={(event) => (
                <>
                  <strong>{stringValue(event.eventType) || "Operation Event"}</strong>
                  <span>{stringValue(event.severity) || "-"} · {stringValue(event.message) || "-"}</span>
                </>
              )}
            />
          </div>
        </div>
      </div>
    </Panel>
  );
}

function OpsPriorityPanel({
  failedCommands,
  failedDeliveries,
  failedJobs,
  failedPickups,
  incidents
}: {
  failedCommands: number;
  failedDeliveries: number;
  failedJobs: number;
  failedPickups: number;
  incidents: PlatformOpsIncident[];
}) {
  const openCritical = incidents.filter((incident) =>
    incident.status === "OPEN" && ["CRITICAL", "HIGH"].includes(incident.severity)
  );
  const items = [
    {
      label: "문서 생성 실패",
      value: failedJobs,
      description: "사용자 다운로드까지 직접 영향을 주는 항목입니다."
    },
    {
      label: "Agent 명령 실패/만료",
      value: failedCommands,
      description: "Agent 연결, 라우팅, 명령 재시도 상태를 확인하세요."
    },
    {
      label: "사진 회수 실패",
      value: failedPickups,
      description: "사무소 원본 저장/NAS 이관 흐름을 확인하세요."
    },
    {
      label: "문서 전달 실패",
      value: failedDeliveries,
      description: "다운로드 준비 또는 Agent delivery 상태를 확인하세요."
    }
  ];

  return (
    <section className="ops-priority-panel">
      <div>
        <p className="eyebrow">운영 우선순위</p>
        <h3>지금 먼저 볼 항목</h3>
        <span>
          긴급/높음 이슈 {openCritical.length}건, 실패 지표 {items.reduce((sum, item) => sum + item.value, 0)}건
        </span>
      </div>
      <div className="ops-priority-list">
        {items.map((item) => (
          <div className={item.value > 0 ? "ops-priority-item warning" : "ops-priority-item"} key={item.label}>
            <strong>{item.value.toLocaleString()}</strong>
            <span>{item.label}</span>
            <small>{item.description}</small>
          </div>
        ))}
      </div>
    </section>
  );
}

function OpsSnapshotList({
  items,
  empty,
  render
}: {
  items: Record<string, unknown>[];
  empty: string;
  render: (item: Record<string, unknown>) => ReactNode;
}) {
  if (items.length === 0) {
    return <EmptyState message={empty} />;
  }
  return (
    <div className="ops-snapshot-list">
      {items.slice(0, 5).map((item, index) => (
        <div className="ops-snapshot-item" key={stringValue(item.id) || index}>
          {render(item)}
        </div>
      ))}
    </div>
  );
}

function AiManagementView({
  view,
  loading,
  callLogs,
  harnessTraces,
  observationMode,
  observations,
  preflightFindings,
  pricingRules,
  providers,
  harnessPolicies,
  policies,
  usageSummary,
  budgetUsageSummary,
  userBudgetOverrides,
  users,
  workerEvaluationSummary,
  workerEvaluationRuns,
  onCreatePricingRule,
  onDisablePricingRule,
  onCreateProvider,
  onUpdateProvider,
  onPublishProvider,
  onTestProvider,
  onUpdateObservationMode,
  onClearObservations,
  onRefresh,
  onCreateWorkerEvaluationRun,
  onCreateWorkerRuntimeEvaluationRun,
  onCreateWorkerRuntimeScenarioRun,
  onSaveHarnessPolicy,
  providerTestResults,
  onSaveOfficePolicy,
  onCreateUserBudgetOverride,
  onDisableUserBudgetOverride
}: {
  view: AiViewKey;
  loading: boolean;
  callLogs: AiModelCallLog[];
  harnessTraces: AiHarnessTraceEvent[];
  observationMode: AiObservationMode | null;
  observations: AiObservation[];
  preflightFindings: PlatformReportPreflightFinding[];
  pricingRules: AiModelPricingRule[];
  providers: AiProviderCredential[];
  harnessPolicies: AiHarnessPolicy[];
  policies: OfficeAiPolicy[];
  usageSummary: AiUsageSummary | null;
  budgetUsageSummary: AiBudgetUsageSummary | null;
  userBudgetOverrides: AiUserBudgetOverride[];
  users: PlatformUserOps[];
  workerEvaluationSummary: AiWorkerEvaluationSummary | null;
  workerEvaluationRuns: AiWorkerEvaluationRun[];
  onCreatePricingRule: (body: {
    providerCode: string;
    modelName: string;
    currency: string;
    inputTokenPricePerMillion: number;
    outputTokenPricePerMillion: number;
  }) => Promise<void>;
  onDisablePricingRule: (pricingRuleId: number) => Promise<void>;
  onCreateProvider: (body: {
    providerCode: string;
    displayName: string;
    providerType: string;
    baseUrl?: string | null;
    defaultModel?: string | null;
    apiKey?: string | null;
  }) => Promise<void>;
  onUpdateProvider: (
    providerId: number,
    body: {
      displayName: string;
      providerType: string;
      baseUrl?: string | null;
      defaultModel?: string | null;
      apiKey?: string | null;
    }
  ) => Promise<void>;
  onPublishProvider: (providerId: number) => Promise<void>;
  onTestProvider: (providerId: number) => Promise<void>;
  onUpdateObservationMode: (enabled: boolean) => Promise<void>;
  onClearObservations: () => Promise<void>;
  onRefresh: () => Promise<void>;
  onCreateWorkerEvaluationRun: () => Promise<AiWorkerEvaluationRun | null>;
  onCreateWorkerRuntimeEvaluationRun: () => Promise<AiWorkerEvaluationRun | null>;
  onCreateWorkerRuntimeScenarioRun: () => Promise<AiWorkerEvaluationRun | null>;
  onSaveHarnessPolicy: (
    policyKey: string,
    body: {
      enabled: boolean;
      providerCredentialId?: number | null;
      modelName?: string | null;
      maxAttempts?: number | null;
      timeoutSeconds?: number | null;
      maxOutputTokens?: number | null;
      budgetEnforcementEnabled?: boolean;
      monthlyBudgetAmount?: number | null;
      budgetCurrency?: string | null;
      dailyCallLimit?: number | null;
      monthlyTokenLimit?: number | null;
    }
  ) => Promise<void>;
  providerTestResults: Record<number, AiProviderConnectionTestResult>;
  onSaveOfficePolicy: (
    officeId: number,
    body: {
      aiEnabled: boolean;
      documentReviewAiEnabled: boolean;
      documentGenerationAiEnabled: boolean;
      preferredProviderCredentialId?: number | null;
      credentialDeliveryMode: string;
      budgetEnforcementEnabled?: boolean;
      monthlyBudgetAmount?: number | null;
      budgetCurrency?: string | null;
      dailyCallLimit?: number | null;
      monthlyTokenLimit?: number | null;
      maxOutputTokens?: number | null;
      perUserDailyCallLimit?: number | null;
      perUserMonthlyTokenLimit?: number | null;
    }
  ) => Promise<void>;
  onCreateUserBudgetOverride: (body: {
    officeId: number;
    userId: number;
    dailyCallLimit?: number | null;
    monthlyTokenLimit?: number | null;
    monthlyBudgetAmount?: number | null;
    budgetCurrency?: string | null;
    expiresAt?: string | null;
    reason: string;
  }) => Promise<void>;
  onDisableUserBudgetOverride: (overrideId: number, reason?: string | null) => Promise<void>;
}) {
  const [findingResolutionFilter, setFindingResolutionFilter] = useState("ALL");
  const [findingSeverityFilter, setFindingSeverityFilter] = useState("ALL");
  const [observerTab, setObserverTab] = useState<AiObserverTabKey>("summary");
  const openBlockingFindings = preflightFindings.filter(isOpenBlockingAiFinding).length;
  const acceptedRiskFindings = preflightFindings.filter((finding) => finding.resolutionStatus === "ACCEPTED").length;
  const complianceFindings = preflightFindings.filter((finding) =>
    ["COMPLIANCE", "LEGAL_RISK"].includes(findingCategory(finding))
  ).length;
  const filteredPreflightFindings = preflightFindings.filter((finding) =>
    (findingResolutionFilter === "ALL" || finding.resolutionStatus === findingResolutionFilter)
    && (findingSeverityFilter === "ALL" || finding.severity === findingSeverityFilter)
  );
  const activeProviderCount = providers.filter((provider) => provider.status === "ACTIVE").length;
  const fakeProviderCount = providers.filter((provider) => isFakeAiProvider(provider.providerCode)).length;
  const aiEnabledOfficeCount = policies.filter((policy) => policy.effectiveAiEnabled).length;
  const runnableHarnessCount = harnessPolicies.filter((policy) => policy.effectiveEnabled).length;
  const showOverview = view === "ai-overview";
  const showProviders = view === "ai-providers";
  const showHarnesses = view === "ai-harnesses";
  const showEvaluation = view === "ai-evaluation";
  const showBudgets = view === "ai-budgets";
  const showPolicies = view === "ai-policies";
  const showObserver = view === "ai-observer";
  const [editingProviderId, setEditingProviderId] = useState<number | null>(null);
  const editingProvider = providers.find((provider) => provider.id === editingProviderId) ?? null;

  return (
    <div className="view-stack platform-view">
      <div className="section-header">
        <div>
          <p className="eyebrow">플랫폼 관리자</p>
          <h2>AI 관리</h2>
          <p className="muted">제공자 API 키는 Cloud API에만 보관하고, Agent에는 권한 정책만 내려갑니다.</p>
        </div>
      </div>

      {showOverview ? (
        <div className="metric-grid">
        <MetricCard label="활성 Provider" value={activeProviderCount} detail={`Fake ${fakeProviderCount}개`} icon={<KeyRound size={18} />} tone={activeProviderCount > 0 ? "green" : "amber"} />
        <MetricCard label="AI 하네스 실행 정책" value={runnableHarnessCount} detail={`${harnessPolicies.length}개 중 실행 가능`} icon={<Command size={18} />} tone={runnableHarnessCount > 0 ? "green" : "amber"} />
        <MetricCard label="AI 사용 사무소" value={aiEnabledOfficeCount} detail="정책상 활성" icon={<ShieldCheck size={18} />} tone={aiEnabledOfficeCount > 0 ? "green" : "slate"} />
        <MetricCard label="AI 호출" value={usageSummary?.callCount ?? 0} detail="이번 달" icon={<Activity size={18} />} tone="blue" />
        <MetricCard label="AI 토큰" value={(usageSummary?.inputTokens ?? 0) + (usageSummary?.outputTokens ?? 0)} detail="입력 + 출력" icon={<Gauge size={18} />} tone="amber" />
        <MetricCard label="AI 비용" value={Number(usageSummary?.estimatedTotalCost ?? 0)} detail={usageSummary?.currency ?? "혼합"} icon={<KeyRound size={18} />} tone="green" />
        <MetricCard label="미처리 차단" value={openBlockingFindings} detail="높음 / 긴급" icon={<AlertTriangle size={18} />} tone={openBlockingFindings > 0 ? "red" : "green"} />
        <MetricCard label="수용한 위험" value={acceptedRiskFindings} detail="사용자 확인" icon={<ShieldCheck size={18} />} tone={acceptedRiskFindings > 0 ? "amber" : "slate"} />
        <MetricCard label="법규/준수" value={complianceFindings} detail="법률/준수 분류" icon={<FileText size={18} />} tone={complianceFindings > 0 ? "amber" : "slate"} />
        </div>
      ) : null}

      {showOverview ? <AiProviderStatusPanel callLogs={callLogs} policies={policies} providers={providers} /> : null}
      {showEvaluation ? (
        <AiWorkerEvaluationControlPanel
          busy={loading}
          runs={workerEvaluationRuns}
          summary={workerEvaluationSummary}
          onCreateRun={onCreateWorkerEvaluationRun}
          onCreateRuntimeRun={onCreateWorkerRuntimeEvaluationRun}
          onCreateRuntimeScenarioRun={onCreateWorkerRuntimeScenarioRun}
        />
      ) : null}
      {showBudgets ? (
        <AiBudgetUsagePanel
          busy={loading}
          overrides={userBudgetOverrides}
          pricingRules={pricingRules}
          summary={budgetUsageSummary}
          users={users}
          onCreateOverride={onCreateUserBudgetOverride}
          onDisableOverride={onDisableUserBudgetOverride}
        />
      ) : null}
      {showObserver ? (
        <>
          <AiObserverTabBar
            busy={loading}
            tab={observerTab}
            onRefresh={onRefresh}
            onTabChange={setObserverTab}
          />
          {observerTab === "summary" ? (
            <AiObserverSummaryPanel
              callLogs={callLogs}
              findings={preflightFindings}
              harnessTraces={harnessTraces}
              mode={observationMode}
              observations={observations}
            />
          ) : null}
          {observerTab === "raw" ? (
            <AiObservationPanel
              busy={loading}
              mode={observationMode}
              observations={observations}
              onClear={onClearObservations}
              onSetEnabled={onUpdateObservationMode}
            />
          ) : null}
          {observerTab === "traces" ? <AiHarnessTracePanel traces={harnessTraces} /> : null}
        </>
      ) : null}

      <div className="dashboard-grid">
        {showProviders ? (
          <>
        <Panel title="AI 제공자 등록" icon={<KeyRound size={18} />}>
          <AiProviderForm busy={loading} onSubmit={onCreateProvider} />
          {editingProvider ? (
            <AiProviderEditForm
              busy={loading}
              provider={editingProvider}
              onCancel={() => setEditingProviderId(null)}
              onSubmit={async (providerId, body) => {
                await onUpdateProvider(providerId, body);
                setEditingProviderId(null);
              }}
            />
          ) : null}
        </Panel>
        <Panel title="AI 단가 규칙" icon={<Gauge size={18} />}>
          <AiPricingRuleForm busy={loading} providers={providers} onSubmit={onCreatePricingRule} />
        </Panel>
          </>
        ) : null}
        {showHarnesses ? (
          <Panel title="AI 하네스 실행 정책" icon={<Command size={18} />} count={harnessPolicies.length}>
            <AiHarnessPolicyPanel
              busy={loading}
              policies={harnessPolicies}
              providers={providers}
              onSubmit={onSaveHarnessPolicy}
            />
          </Panel>
        ) : null}
        {showPolicies ? (
          <>
        <Panel title="사무소 AI 권한" icon={<ShieldCheck size={18} />}>
          <OfficeAiPolicyForm
            busy={loading}
            policies={policies}
            providers={providers}
            onSubmit={onSaveOfficePolicy}
          />
        </Panel>
        <Panel title="AI 예산 제한" icon={<Gauge size={18} />}>
          <AiBudgetPolicyForm busy={loading} policies={policies} onSubmit={onSaveOfficePolicy} />
        </Panel>
          </>
        ) : null}
      </div>

      {showProviders ? (
        <Panel title="AI 제공자" icon={<KeyRound size={18} />} count={providers.length}>
        <Table
          columns={["제공자", "실행 모드", "유형", "상태", "모델", "키", "버전", "연결", "작업"]}
          empty="등록된 AI 제공자가 없습니다."
          rows={providers.map((provider) => [
            <CellTitle key="provider" title={provider.displayName} subtitle={`${provider.providerCode} / #${provider.id}`} />,
            <ProviderModeBadge key="mode" provider={provider} />,
            displayLabel(provider.providerType),
            <StatusBadge key="status" status={provider.status} />,
            provider.defaultModel ?? "-",
            provider.apiKeyConfigured ? provider.apiKeyMasked ?? "설정됨" : "미설정",
            `v${provider.credentialVersion}`,
            <AiProviderConnectionTestCell
              busy={loading}
              key="test"
              provider={provider}
              result={providerTestResults[provider.id]}
              onTest={onTestProvider}
            />,
            <button
              className="button"
              disabled={loading}
              key="edit"
              onClick={() => setEditingProviderId(provider.id)}
              type="button"
            >
              수정
            </button>,
            <button
              className="button"
              disabled={loading || provider.status === "ACTIVE"}
              key="publish"
              onClick={() => onPublishProvider(provider.id)}
              type="button"
            >
              <Send size={16} />
              게시
            </button>
          ])}
        />
        </Panel>
      ) : null}

      {showProviders ? (
        <Panel title="이번 달 AI 사용량" icon={<Activity size={18} />} count={usageSummary?.groups.length ?? 0}>
        <Table
          columns={["사무소", "기능", "호출", "성공 / 실패", "토큰", "예상 비용"]}
          empty="이번 달 AI 사용 기록이 없습니다."
          rows={(usageSummary?.groups ?? []).map((group) => [
            group.officeId ? `#${group.officeId}` : "-",
            displayLabel(group.feature),
            group.callCount,
            `${group.succeededCount} / ${group.failedCount}`,
            `${group.inputTokens} / ${group.outputTokens}`,
            formatMoney(group.estimatedTotalCost)
          ])}
        />
        </Panel>
      ) : null}

      {showProviders ? (
        <Panel title="AI 단가 규칙" icon={<Gauge size={18} />} count={pricingRules.length}>
        <Table
          columns={["제공자", "모델", "입력 / 100만", "출력 / 100만", "상태", "수정", "작업"]}
          empty="AI 단가 규칙이 없습니다."
          rows={pricingRules.map((rule) => [
            rule.providerCode,
            rule.modelName,
            `${rule.currency} ${formatMoney(rule.inputTokenPricePerMillion)}`,
            `${rule.currency} ${formatMoney(rule.outputTokenPricePerMillion)}`,
            <StatusBadge key="status" status={rule.status} />,
            formatDate(rule.updatedAt),
            <button
              className="button"
              disabled={loading || rule.status === "DISABLED"}
              key="disable"
              onClick={() => onDisablePricingRule(rule.id)}
              type="button"
            >
              <XCircle size={16} />
              비활성화
            </button>
          ])}
        />
        </Panel>
      ) : null}

      {showPolicies ? (
        <Panel title="사무소 AI 정책" icon={<ShieldCheck size={18} />} count={policies.length}>
        <Table
          columns={["사무소", "적용", "검토", "생성", "제공자", "전달", "버전", "메시지"]}
          empty="사무소 AI 정책이 없습니다."
          rows={policies.map((policy) => [
            <CellTitle key="office" title={policy.officeName} subtitle={`${policy.officeCode} / #${policy.officeId}`} />,
            <StatusBadge key="effective" status={policy.effectiveAiEnabled ? "ACTIVE" : "DISABLED"} />,
            displayLabel(policy.documentReviewAiEnabled ? "ON" : "OFF"),
            displayLabel(policy.documentGenerationAiEnabled ? "ON" : "OFF"),
            policy.preferredProviderCode ?? "-",
            displayLabel(policy.credentialDeliveryMode),
            `v${policy.policyVersion}`,
            policy.effectiveMessage ?? "-"
          ])}
        />
        </Panel>
      ) : null}

      {showObserver && observerTab === "findings" ? (
        <Panel
        title="생성 전 검토 결과"
        icon={<AlertTriangle size={18} />}
        count={filteredPreflightFindings.length}
        action={
          <div className="filter-row">
            <FilterSelect
              label="처리 상태"
              options={aiFindingResolutionOptions}
              value={findingResolutionFilter}
              onChange={setFindingResolutionFilter}
            />
            <FilterSelect
              label="심각도"
              options={aiFindingSeverityOptions}
              value={findingSeverityFilter}
              onChange={setFindingSeverityFilter}
            />
          </div>
        }
      >
        <Table
          columns={["생성", "리포트", "심각도", "처리 상태", "실행", "출처", "메시지", "처리"]}
          empty="생성 전 검토 결과가 없습니다."
          rows={filteredPreflightFindings.map((finding) => [
            formatDate(finding.createdAt),
            <CellTitle
              key="report"
              title={`리포트 #${finding.reportId}`}
              subtitle={`사무소 #${finding.officeId} / 실행 #${finding.reviewRunId}`}
            />,
            <StatusBadge key="severity" status={finding.severity} />,
            <FindingResolutionBadge key="resolution" status={finding.resolutionStatus} />,
            finding.reviewRunStatus ? <StatusBadge key="run" status={finding.reviewRunStatus} /> : "-",
            `${finding.source} / ${findingCategory(finding)}`,
            <CellTitle key="message" title={finding.message} subtitle={finding.location ?? finding.code} />,
            finding.resolvedAt ? `#${finding.resolvedBy ?? "-"} / ${formatDate(finding.resolvedAt)}` : "-"
          ])}
        />
        </Panel>
      ) : null}

      {showObserver && observerTab === "calls" ? (
        <Panel title="AI 호출 로그" icon={<Activity size={18} />} count={callLogs.length}>
        <Table
          columns={["시간", "사무소", "제공자", "모델", "기능", "상태", "토큰", "비용", "지연", "오류"]}
          empty="AI 호출 로그가 없습니다."
          rows={callLogs.map((log) => [
            formatDate(log.completedAt),
            log.officeId ? `#${log.officeId}` : "-",
            <CellTitle key="provider" title={log.providerCode} subtitle={aiProviderModeLabel(log.providerCode)} />,
            log.modelName,
            displayLabel(log.feature ?? log.workflowType ?? "-"),
            <StatusBadge key="status" status={log.status} />,
            tokenUsageLabel(log),
            costLabel(log),
            log.latencyMs == null ? "-" : `${log.latencyMs}ms`,
            log.errorMessage ? `${log.errorType ?? "ERROR"}: ${log.errorMessage}` : "-"
          ])}
        />
        </Panel>
      ) : null}
    </div>
  );
}

function AiUserBudgetOverrideForm({
  busy,
  offices,
  users,
  onSubmit
}: {
  busy: boolean;
  offices: AiBudgetUsageSummary["offices"];
  users: PlatformUserOps[];
  onSubmit: (body: {
    officeId: number;
    userId: number;
    dailyCallLimit?: number | null;
    monthlyTokenLimit?: number | null;
    monthlyBudgetAmount?: number | null;
    budgetCurrency?: string | null;
    expiresAt?: string | null;
    reason: string;
  }) => Promise<void>;
}) {
  const [officeId, setOfficeId] = useState(offices[0]?.officeId ?? 0);
  const [userId, setUserId] = useState(users[0]?.id ?? 0);
  const [dailyCallLimit, setDailyCallLimit] = useState("");
  const [monthlyTokenLimit, setMonthlyTokenLimit] = useState("");
  const [monthlyBudgetAmount, setMonthlyBudgetAmount] = useState("");
  const [budgetCurrency, setBudgetCurrency] = useState("USD");
  const [expiresAt, setExpiresAt] = useState("");
  const [reason, setReason] = useState("");

  useEffect(() => {
    if (!officeId && offices[0]?.officeId) {
      setOfficeId(offices[0].officeId);
    }
  }, [officeId, offices]);

  useEffect(() => {
    if (!userId && users[0]?.id) {
      setUserId(users[0].id);
    }
  }, [userId, users]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!officeId || !userId) {
      return;
    }
    await onSubmit({
      officeId,
      userId,
      dailyCallLimit: optionalNumber(dailyCallLimit),
      monthlyTokenLimit: optionalNumber(monthlyTokenLimit),
      monthlyBudgetAmount: optionalNumber(monthlyBudgetAmount),
      budgetCurrency: normalizeFormValue(budgetCurrency),
      expiresAt: expiresAt ? new Date(expiresAt).toISOString() : null,
      reason: normalizeFormValue(reason) ?? "Temporary AI usage limit override"
    });
    setDailyCallLimit("");
    setMonthlyTokenLimit("");
    setMonthlyBudgetAmount("");
    setExpiresAt("");
    setReason("");
  }

  return (
    <form className="ai-policy-form" onSubmit={submit}>
      <label>
        사무소
        <select value={officeId || ""} onChange={(event) => setOfficeId(Number(event.target.value))}>
          {offices.map((office) => (
            <option key={office.officeId} value={office.officeId}>
              {office.officeName} / {office.officeCode}
            </option>
          ))}
        </select>
      </label>
      <label>
        사용자
        <select value={userId || ""} onChange={(event) => setUserId(Number(event.target.value))}>
          {users.map((user) => (
            <option key={user.id} value={user.id}>
              {user.name} / {user.email}
            </option>
          ))}
        </select>
      </label>
      <label>
        일일 호출 한도
        <input min="0" step="1" type="number" value={dailyCallLimit} onChange={(event) => setDailyCallLimit(event.target.value)} />
      </label>
      <label>
        월간 토큰 한도
        <input min="0" step="1" type="number" value={monthlyTokenLimit} onChange={(event) => setMonthlyTokenLimit(event.target.value)} />
      </label>
      <label>
        월 예산
        <input min="0" step="0.00000001" type="number" value={monthlyBudgetAmount} onChange={(event) => setMonthlyBudgetAmount(event.target.value)} />
      </label>
      <label>
        통화
        <input value={budgetCurrency} onChange={(event) => setBudgetCurrency(event.target.value)} />
      </label>
      <label>
        만료
        <input type="datetime-local" value={expiresAt} onChange={(event) => setExpiresAt(event.target.value)} />
      </label>
      <label>
        사유
        <input value={reason} onChange={(event) => setReason(event.target.value)} placeholder="예: 이번 달 법령 검토 작업량 증가" />
      </label>
      <div className="policy-note">비워둔 한도는 사무소 기본 사용자 한도를 그대로 사용합니다. 선택한 사용자가 해당 사무소의 활성 멤버가 아니면 저장되지 않습니다.</div>
      <button className="button primary" disabled={busy || offices.length === 0 || users.length === 0} type="submit">
        {busy ? <Loader2 className="spin" size={16} /> : <UserPlus size={16} />}
        상향 저장
      </button>
    </form>
  );
}

function AiBudgetUsagePanel({
  busy,
  overrides,
  summary,
  pricingRules,
  users,
  onCreateOverride,
  onDisableOverride
}: {
  busy: boolean;
  overrides: AiUserBudgetOverride[];
  summary: AiBudgetUsageSummary | null;
  pricingRules: AiModelPricingRule[];
  users: PlatformUserOps[];
  onCreateOverride: (body: {
    officeId: number;
    userId: number;
    dailyCallLimit?: number | null;
    monthlyTokenLimit?: number | null;
    monthlyBudgetAmount?: number | null;
    budgetCurrency?: string | null;
    expiresAt?: string | null;
    reason: string;
  }) => Promise<void>;
  onDisableOverride: (overrideId: number, reason?: string | null) => Promise<void>;
}) {
  const blockedOffices = summary?.offices.filter((item) => item.status === "BLOCKED").length ?? 0;
  const warningOffices = summary?.offices.filter((item) => item.status === "WARN").length ?? 0;
  const blockedHarnesses = summary?.harnesses.filter((item) => item.status === "BLOCKED").length ?? 0;
  const missingPricing = summary?.missingPricingRuleCount ?? 0;
  const activePricingRules = pricingRules.filter((rule) => rule.status === "ACTIVE").length;
  const activeOverrides = overrides.filter((override) => override.active);

  if (!summary) {
    return <EmptyState message="AI 예산/사용량 정보를 불러오지 않았습니다." />;
  }

  return (
    <div className="view-stack">
      <div className="metric-grid">
        <MetricCard label="차단 상태" value={blockedOffices + blockedHarnesses} detail="사무소 + 하네스" icon={<AlertTriangle size={18} />} tone={blockedOffices + blockedHarnesses > 0 ? "red" : "green"} />
        <MetricCard label="주의 상태" value={warningOffices} detail="한도 80% 이상" icon={<Gauge size={18} />} tone={warningOffices > 0 ? "amber" : "green"} />
        <MetricCard label="사무소 Guard" value={`${summary.officesWithBudgetGuard}/${summary.officePolicyCount}`} detail="예산 통제 적용" icon={<ShieldCheck size={18} />} tone={summary.officesWithBudgetGuard === summary.officePolicyCount ? "green" : "amber"} />
        <MetricCard label="하네스 Guard" value={`${summary.harnessesWithBudgetGuard}/${summary.harnessPolicyCount}`} detail="플랫폼 하네스 통제" icon={<Command size={18} />} tone={summary.harnessesWithBudgetGuard === summary.harnessPolicyCount ? "green" : "amber"} />
        <MetricCard label="단가 누락" value={missingPricing} detail={`활성 규칙 ${activePricingRules}개`} icon={<KeyRound size={18} />} tone={missingPricing > 0 ? "amber" : "green"} />
        <MetricCard label="사용자 사용량" value={summary.userUsageCount} detail="이번 달 호출 사용자" icon={<Users size={18} />} tone="blue" />
      </div>

      <Panel title="사무소 예산/토큰 사용량" icon={<ShieldCheck size={18} />} count={summary.offices.length}>
        <Table
          columns={["사무소", "상태", "일일 호출", "월간 토큰", "월 예산", "출력 상한", "사용자 기본 한도", "메시지"]}
          empty="사무소 AI 예산 정책이 없습니다."
          rows={summary.offices.map((office) => [
            <CellTitle key="office" title={office.officeName} subtitle={`${office.officeCode} / #${office.officeId}`} />,
            <StatusBadge key="status" status={office.status} />,
            limitLabel(office.dailyCallCount, office.dailyCallLimit, "calls"),
            limitLabel(office.monthlyTokens, office.monthlyTokenLimit, "tokens"),
            moneyLimitLabel(office.monthlyEstimatedCost, office.monthlyBudgetAmount, office.budgetCurrency),
            `${office.maxOutputTokens ?? "-"} tokens`,
            `${office.perUserDailyCallLimit ?? "-"} calls/day · ${office.perUserMonthlyTokenLimit ?? "-"} tokens/month`,
            office.message
          ])}
        />
      </Panel>

      <Panel title="하네스 예산/토큰 사용량" icon={<Command size={18} />} count={summary.harnesses.length}>
        <Table
          columns={["하네스", "상태", "Provider / Model", "일일 호출", "월간 토큰", "월 예산", "출력 상한", "단가", "메시지"]}
          empty="AI 하네스 예산 정책이 없습니다."
          rows={summary.harnesses.map((harness) => [
            <CellTitle key="harness" title={harness.displayName} subtitle={displayLabel(harness.policyKey)} />,
            <StatusBadge key="status" status={harness.status} />,
            `${harness.providerCode ?? "-"} / ${harness.modelName ?? "-"}`,
            limitLabel(harness.dailyCallCount, harness.dailyCallLimit, "calls"),
            limitLabel(harness.monthlyTokens, harness.monthlyTokenLimit, "tokens"),
            moneyLimitLabel(harness.monthlyEstimatedCost, harness.monthlyBudgetAmount, harness.budgetCurrency),
            `${harness.maxOutputTokens ?? "-"} tokens`,
            <StatusBadge key="pricing" status={harness.pricingRuleConfigured ? "OK" : "WARN"} />,
            harness.message
          ])}
        />
      </Panel>

      <Panel title="사용자별 AI 사용량" icon={<Users size={18} />} count={summary.users.length}>
        <Table
          columns={["사용자", "상태", "사무소", "일일 호출", "월간 토큰", "메시지"]}
          empty="이번 달 사용자별 AI 사용량이 없습니다."
          rows={summary.users.map((user) => [
            <CellTitle key="user" title={user.userName ?? `User #${user.userId ?? "-"}`} subtitle={user.userEmail ?? `#${user.userId ?? "-"}`} />,
            <StatusBadge key="status" status={user.status} />,
            user.officeCode ?? (user.officeId ? `#${user.officeId}` : "-"),
            limitLabel(user.dailyCallCount, user.dailyCallLimit, "calls"),
            limitLabel(user.monthlyTokens, user.monthlyTokenLimit, "tokens"),
            user.message
          ])}
        />
      </Panel>

      <Panel title="모델 단가 Coverage" icon={<KeyRound size={18} />} count={summary.pricingCoverage.length}>
        <Table
          columns={["대상", "상태", "Provider", "Model", "매칭", "규칙", "메시지"]}
          empty="점검할 provider/model 조합이 없습니다."
          rows={summary.pricingCoverage.map((coverage) => [
            <CellTitle key="source" title={coverage.sourceType} subtitle={coverage.sourceKey} />,
            <StatusBadge key="status" status={coverage.status} />,
            coverage.providerCode ?? "-",
            coverage.modelName ?? "-",
            coverage.matchedBy,
            coverage.pricingRuleId ? `#${coverage.pricingRuleId}` : "-",
            coverage.message
          ])}
        />
      </Panel>
    </div>
  );
}

function limitLabel(used: number, limit?: number | null, unit = "") {
  if (limit == null) {
    return `${used.toLocaleString()}${unit ? ` ${unit}` : ""} / no limit`;
  }
  return `${used.toLocaleString()} / ${limit.toLocaleString()}${unit ? ` ${unit}` : ""} (${usagePercent(used, limit)})`;
}

function moneyLimitLabel(used: number, limit?: number | null, currency = "USD") {
  if (limit == null) {
    return `${currency} ${formatMoney(used)} / no cap`;
  }
  return `${currency} ${formatMoney(used)} / ${formatMoney(limit)} (${usagePercent(used, limit)})`;
}

function usagePercent(used: number, limit: number) {
  if (!Number.isFinite(limit) || limit <= 0) {
    return "100%";
  }
  return `${Math.min(999, Math.round((used / limit) * 100)).toLocaleString()}%`;
}

function AiWorkerEvaluationControlPanel({
  busy,
  runs,
  summary: currentSummary,
  onCreateRun,
  onCreateRuntimeRun,
  onCreateRuntimeScenarioRun
}: {
  busy: boolean;
  runs: AiWorkerEvaluationRun[];
  summary: AiWorkerEvaluationSummary | null;
  onCreateRun: () => Promise<AiWorkerEvaluationRun | null>;
  onCreateRuntimeRun: () => Promise<AiWorkerEvaluationRun | null>;
  onCreateRuntimeScenarioRun: () => Promise<AiWorkerEvaluationRun | null>;
}) {
  const [selectedGroupKey, setSelectedGroupKey] = useState<string | null>(null);
  const [selectedRunId, setSelectedRunId] = useState<number | null>(null);
  const selectedRun = runs.find((run) => run.id === selectedRunId) ?? null;
  const summary = selectedRun?.summary ?? currentSummary;

  useEffect(() => {
    if (selectedRunId != null && !runs.some((run) => run.id === selectedRunId)) {
      setSelectedRunId(null);
    }
  }, [runs, selectedRunId]);

  useEffect(() => {
    if (summary && selectedGroupKey && !summary.groups.some((group) => group.groupKey === selectedGroupKey)) {
      setSelectedGroupKey(null);
    }
  }, [selectedGroupKey, summary]);

  if (!summary) {
    return (
      <Panel title="AI/Worker 평가" icon={<ShieldCheck size={18} />}>
        <EmptyState message="평가 결과를 불러오지 못했습니다." />
      </Panel>
    );
  }

  const warningSignals = summary.signals.filter((signal) => signal.status === "WARN").length;
  const failedSignals = summary.signals.filter((signal) => signal.status === "FAILED").length;
  const signalPassCount = summary.signals.filter((signal) => signal.status === "PASS").length;
  const selectedGroup = summary.groups.find((group) => group.groupKey === selectedGroupKey) ?? summary.groups[0] ?? null;
  const groupsNeedingAttention = summary.groups.filter((group) => group.failedCases > 0 || group.warningCases > 0).length;
  const selectedSignals = selectedGroup
    ? summary.signals.filter((signal) => signal.layer === selectedGroup.layer || signal.status !== "PASS")
    : summary.signals.filter((signal) => signal.status !== "PASS");
  const verdict = evaluationVerdict(summary, failedSignals, warningSignals);
  const currentWarningSignals = currentSummary?.signals.filter((signal) => signal.status === "WARN").length ?? warningSignals;
  const currentFailedSignals = currentSummary?.signals.filter((signal) => signal.status === "FAILED").length ?? failedSignals;
  const currentVerdictStatus = currentSummary
    ? evaluationVerdict(currentSummary, currentFailedSignals, currentWarningSignals).status
    : verdict.status;
  const selectedRunTitle = selectedRun ? `${evaluationRunTriggerLabel(selectedRun.triggerType)} #${selectedRun.id}` : "현재 기준";
  const selectedRunSubtitle = selectedRun
    ? `${selectedRun.evaluationMode} · ${formatDate(selectedRun.completedAt)}`
    : `${summary.evaluationMode} · 아직 저장된 Run이 아닙니다`;
  const selectedRunDescription = selectedRun
    ? "아래 평가 그룹과 제어 신호는 이 Run에 저장된 결과입니다."
    : "현재 기준은 지금 코드 기준으로 계산한 상태입니다. 기록 버튼을 누르면 Run으로 저장됩니다.";
  const operatorBrief = evaluationOperatorBrief(summary, selectedRun);
  async function createAndSelectRun(creator: () => Promise<AiWorkerEvaluationRun | null>) {
    const run = await creator();
    if (run) {
      setSelectedRunId(run.id);
      setSelectedGroupKey(null);
    }
  }

  return (
    <Panel
      title="AI/Worker 평가"
      icon={<ShieldCheck size={18} />}
      count={summary.totalCases}
      action={
        <div className="panel-kpis compact">
          <span>Pass {summary.passedCases}</span>
          <span>Warn {summary.warningCases + warningSignals}</span>
          <span>Fail {summary.failedCases + failedSignals}</span>
          {selectedRun ? (
            <button className="button compact" disabled={busy} onClick={() => setSelectedRunId(null)} type="button">
              <ArrowLeft size={14} />
              현재 기준 보기
            </button>
          ) : null}
          <button className="button compact" disabled={busy} onClick={() => createAndSelectRun(onCreateRun)} type="button">
            {busy ? <Loader2 className="spin" size={14} /> : <Plus size={14} />}
            현재 기준 기록
          </button>
          <button className="button compact" disabled={busy} onClick={() => createAndSelectRun(onCreateRuntimeRun)} type="button">
            {busy ? <Loader2 className="spin" size={14} /> : <Activity size={14} />}
            런타임 연결 평가
          </button>
          <button className="button compact" disabled={busy} onClick={() => createAndSelectRun(onCreateRuntimeScenarioRun)} type="button">
            {busy ? <Loader2 className="spin" size={14} /> : <Command size={14} />}
            시나리오 평가
          </button>
        </div>
      }
    >
      <div className="evaluation-hero">
        <div>
          <p className="eyebrow">핵심 판단</p>
          <h3>{verdict.title}</h3>
          <span>{selectedRunTitle} · {verdict.description}</span>
        </div>
        <StatusBadge status={verdict.status} />
      </div>

      <section className="evaluation-section">
        <div className="evaluation-operator-brief">
          <div className="evaluation-operator-primary">
            <p className="eyebrow">운영 판정</p>
            <h3>{operatorBrief.decision}</h3>
            <span>{operatorBrief.description}</span>
            <StatusBadge status={operatorBrief.status} />
          </div>
          <div>
            <strong>확인할 일</strong>
            <ul>
              {operatorBrief.checks.map((item) => <li key={item}>{item}</li>)}
            </ul>
          </div>
          <div>
            <strong>문제 원인</strong>
            <ul>
              {operatorBrief.causes.map((item) => <li key={item}>{item}</li>)}
            </ul>
          </div>
          <div>
            <strong>다음 조치</strong>
            <ul>
              {operatorBrief.actions.map((item) => <li key={item}>{item}</li>)}
            </ul>
          </div>
        </div>
      </section>

      <div className="evaluation-decision-grid">
        <MetricCard
          label="자동 평가"
          value={`${summary.passedCases}/${summary.totalCases}`}
          detail={`${summary.automatedCases}개 자동 검증`}
          icon={<Command size={18} />}
          tone={summary.failedCases > 0 ? "red" : summary.warningCases > 0 ? "amber" : "green"}
        />
        <MetricCard
          label="통과율"
          value={`${summary.passRatePercent}%`}
          detail={`실패 ${summary.failedCases} / 주의 ${summary.warningCases}`}
          icon={<CheckCircle2 size={18} />}
          tone={summary.failedCases > 0 ? "red" : summary.warningCases > 0 ? "amber" : "green"}
        />
        <MetricCard
          label="문제 그룹"
          value={groupsNeedingAttention}
          detail={`${summary.groups.length}개 그룹 중`}
          icon={<AlertTriangle size={18} />}
          tone={groupsNeedingAttention > 0 ? "amber" : "green"}
        />
        <MetricCard
          label="제어 신호"
          value={summary.signals.length}
          detail={`PASS ${signalPassCount} / WARN ${warningSignals}`}
          icon={<Gauge size={18} />}
          tone={failedSignals > 0 ? "red" : warningSignals > 0 ? "amber" : "green"}
        />
        <MetricCard
          label="검증 범위"
          value={summary.evaluationMode}
          detail={selectedRun ? formatDate(selectedRun.completedAt) : formatDate(summary.generatedAt)}
          icon={<Activity size={18} />}
          tone="slate"
        />
      </div>

      <section className="evaluation-section">
        <div className="evaluation-selected-run">
          <div>
            <p className="eyebrow">표시 중인 평가</p>
            <h3>{selectedRunTitle}</h3>
            <span>{selectedRunSubtitle}</span>
          </div>
          <div>
            <strong>{summary.groups.length}개 그룹</strong>
            <span>{selectedRunDescription}</span>
          </div>
          <StatusBadge status={selectedRun ? selectedRun.status : currentVerdictStatus} />
        </div>
      </section>

      <section className="evaluation-section">
        <div className="evaluation-detail-head">
          <div>
            <h3>평가 Run 기록</h3>
            <span>Run은 한 번 저장된 평가 결과입니다. 최근 30개만 보관하며, Run을 선택하면 아래 상세 영역이 그 기록 기준으로 바뀝니다.</span>
          </div>
        </div>
        {runs.length === 0 ? <EmptyState message="저장된 평가 Run이 없습니다." /> : null}
        {runs.length > 0 ? (
          <div className="evaluation-run-list">
          {runs.map((run) => (
            <button
              className={selectedRunId === run.id ? "evaluation-run-button active" : "evaluation-run-button"}
              key={run.id}
              onClick={() => setSelectedRunId(run.id)}
              type="button"
            >
              <div>
                <strong>{evaluationRunTriggerLabel(run.triggerType)} #{run.id}</strong>
                <span>{formatDate(run.completedAt)} · {run.triggeredByEmail ?? `User #${run.triggeredByUserId ?? "-"}`}</span>
              </div>
              <small>{run.passedCases}/{run.totalCases} 통과 · 신호 WARN {run.warningSignalCount}</small>
              <StatusBadge status={run.status} />
            </button>
          ))}
          </div>
        ) : null}
      </section>

      <section className="evaluation-section">
        <h3>{selectedRunTitle}의 평가 그룹</h3>
        <div className="evaluation-group-list">
          {summary.groups.map((group) => (
            <button
              className={selectedGroup?.groupKey === group.groupKey ? "evaluation-group-button active" : "evaluation-group-button"}
              key={group.groupKey}
              onClick={() => setSelectedGroupKey(group.groupKey)}
              type="button"
            >
              <div>
                <strong>{evaluationGroupLabel(group.groupKey, group.displayName)}</strong>
                <span>{evaluationGroupDescription(group.groupKey)}</span>
              </div>
              <small>{group.passedCases}/{group.totalCases} 통과</small>
              <StatusBadge status={group.failedCases > 0 ? "FAILED" : group.warningCases > 0 ? "WARN" : "PASS"} />
            </button>
          ))}
        </div>
      </section>

      {selectedGroup ? (
        <section className="evaluation-section">
          <div className="evaluation-detail-head">
            <div>
              <p className="eyebrow">선택 그룹</p>
              <h3>{evaluationGroupLabel(selectedGroup.groupKey, selectedGroup.displayName)}</h3>
              <span>{evaluationGroupFocus(selectedGroup.groupKey)}</span>
            </div>
            <div className="panel-kpis compact">
              <span>{selectedGroup.passedCases}/{selectedGroup.totalCases} 통과</span>
              <span>{selectedGroup.passRatePercent}%</span>
            </div>
          </div>
          <Table
            columns={["ID", "검증 내용", "상태", "검증 방식", "근거"]}
            empty="선택한 그룹의 평가 케이스가 없습니다."
            rows={selectedGroup.cases.map((item) => [
              item.caseId,
              <CellTitle key="case" title={evaluationCaseLabel(item.caseId, item.name)} subtitle={item.name} />,
              <StatusBadge key="status" status={item.status} />,
              item.automated ? evaluationVerificationLabel(item.verification) : "수동 확인",
              evaluationEvidenceText(item)
            ])}
          />
        </section>
      ) : null}

      <section className="evaluation-section">
        <h3>관련 제어 신호</h3>
        <Table
          columns={["신호", "영역", "상태", "근거"]}
          empty="관련 제어 신호가 없습니다."
          rows={selectedSignals.map((signal) => [
            <CellTitle key="signal" title={evaluationSignalLabel(signal.signalKey, signal.displayName)} subtitle={signal.signalKey} />,
            evaluationLayerLabel(signal.layer),
            <StatusBadge key="status" status={signal.status} />,
            signal.evidence
          ])}
        />
      </section>

      <div className="inline-notice">{summary.dataPolicy}</div>
    </Panel>
  );
}

function evaluationVerdict(summary: AiWorkerEvaluationSummary, failedSignals: number, warningSignals: number) {
  if (summary.failedCases > 0 || failedSignals > 0) {
    return {
      status: "FAILED",
      title: "조치가 필요한 평가 실패가 있습니다.",
      description: "실패한 케이스 또는 제어 신호를 먼저 확인해야 합니다."
    };
  }
  if (summary.warningCases > 0 || warningSignals > 0) {
    return {
      status: "WARN",
      title: "기본 제어선은 통과했고, 남은 주의 항목이 있습니다.",
      description: "현재 WARN은 실제 모델 반복 평가가 아직 별도 단계라는 의미입니다."
    };
  }
  return {
    status: "PASS",
    title: "기본 제어선이 정상입니다.",
    description: "AI Harness, Worker, MCP, Legal, Governance 기준 평가가 모두 통과했습니다."
  };
}

function evaluationOperatorBrief(summary: AiWorkerEvaluationSummary, selectedRun: AiWorkerEvaluationRun | null) {
  const failedSignals = summary.signals.filter((signal) => signal.status === "FAILED");
  const warningSignals = summary.signals.filter((signal) => signal.status === "WARN");
  const failedGroups = summary.groups.filter((group) => group.failedCases > 0);
  const warningGroups = summary.groups.filter((group) => group.warningCases > 0);
  const realModelSignal = summary.signals.find((signal) => signal.signalKey === "REAL_MODEL_EVALUATION");
  const providerSignal = summary.signals.find((signal) => signal.signalKey === "RUNTIME_PROVIDER_CONNECTIVITY");
  const runtimeProviderGroup = summary.groups.find((group) => group.groupKey === "RUNTIME_AI_PROVIDER_PROBE");
  const runtimeWarnings = runtimeProviderGroup?.cases.filter((item) => item.status === "WARN") ?? [];
  const runtimeFailures = runtimeProviderGroup?.cases.filter((item) => item.status === "FAILED") ?? [];
  const selectedRunLabel = selectedRun ? evaluationRunTriggerLabel(selectedRun.triggerType) : "현재 기준";

  if (failedSignals.length > 0 || failedGroups.length > 0) {
    return {
      status: "FAILED",
      decision: "실행 전에 조치가 필요합니다.",
      description: `${selectedRunLabel}에 실패 항목이 있습니다. 실패 원인을 먼저 해결한 뒤 다시 평가해야 합니다.`,
      checks: unique([
        ...failedSignals.map((signal) => `${evaluationSignalLabel(signal.signalKey, signal.displayName)} 신호`),
        ...failedGroups.map((group) => `${evaluationGroupLabel(group.groupKey, group.displayName)} 그룹`),
        "실패 항목의 근거 문구"
      ]).slice(0, 4),
      causes: evidenceList([
        ...failedSignals.map((signal) => signal.evidence),
        ...runtimeFailures.map((item) => item.evidence)
      ], "실패 신호 또는 실패 케이스가 기록되었습니다."),
      actions: unique([
        providerSignal?.status === "FAILED" ? "AI 제공자 화면에서 실제 provider 연결 테스트를 먼저 통과시키세요." : null,
        "실패한 평가 그룹을 열어 케이스 근거를 확인하세요.",
        "설정 또는 정책을 수정한 뒤 런타임 연결 평가를 다시 실행하세요."
      ])
    };
  }

  if (warningSignals.length > 0 || warningGroups.length > 0) {
    const fakeProviderWarning = runtimeWarnings.some((item) => item.evidence.toLowerCase().includes("fake"));
    const realModelPassed = realModelSignal?.status === "PASS";
    return {
      status: "WARN",
      decision: "운영은 가능하지만 확인할 경고가 있습니다.",
      description: realModelPassed
        ? "실제 모델 연결은 통과했습니다. 남은 경고는 설정 정리나 fake provider 잔존 여부를 확인하는 성격입니다."
        : `${selectedRunLabel}에 주의 항목이 있습니다. 치명적 실패는 아니지만 운영 기준을 명확히 해야 합니다.`,
      checks: unique([
        realModelSignal ? `${evaluationSignalLabel(realModelSignal.signalKey, realModelSignal.displayName)}: ${realModelSignal.status}` : null,
        providerSignal ? `${evaluationSignalLabel(providerSignal.signalKey, providerSignal.displayName)}: ${providerSignal.status}` : null,
        ...warningGroups.map((group) => `${evaluationGroupLabel(group.groupKey, group.displayName)} 그룹`)
      ]).slice(0, 4),
      causes: evidenceList([
        fakeProviderWarning ? "일부 하네스 정책이 fake provider를 사용하거나 fake provider가 평가 대상에 남아 있습니다." : null,
        providerSignal?.evidence,
        realModelSignal?.status === "WARN" ? realModelSignal.evidence : null,
        ...runtimeWarnings.map((item) => item.evidence)
      ], "주의 신호가 남아 있습니다."),
      actions: unique([
        fakeProviderWarning ? "AI 하네스 관리에서 fake provider가 필요한 개발용 설정인지, 실제 provider로 바꿀 대상인지 결정하세요." : null,
        realModelPassed ? "실제 provider 연결은 성공했으므로 fake provider 잔존 항목만 정리하면 됩니다." : "AI 하네스 관리에서 실제 provider 배정을 확인하세요.",
        "정리 후 런타임 연결 평가를 다시 실행하세요."
      ])
    };
  }

  return {
    status: "PASS",
    decision: "현재 평가 기준에서 즉시 조치할 항목은 없습니다.",
    description: `${selectedRunLabel}의 실패와 경고가 없습니다. 다음에는 필요한 평가 케이스를 늘려 검증 범위를 넓히면 됩니다.`,
    checks: [
      "최근 런타임 평가 시각",
      "실제 provider 연결 신호",
      "새로 추가할 업무 평가 케이스"
    ],
    causes: [
      "실패/경고 신호가 없습니다.",
      "현재 평가 범위 안에서는 정책과 연결성이 정상입니다."
    ],
    actions: [
      "운영 전환 범위에 맞는 새 평가 케이스를 추가하세요.",
      "중요 설정 변경 후 런타임 연결 평가를 다시 실행하세요."
    ]
  };
}

function evidenceList(items: Array<string | null | undefined>, fallback: string) {
  const values = unique(items.map((item) => item?.trim()).filter(Boolean) as string[]);
  return values.length > 0 ? values.slice(0, 4) : [fallback];
}

function unique<T>(items: Array<T | null | undefined>) {
  return Array.from(new Set(items.filter((item): item is T => item != null && item !== "")));
}

function evaluationGroupLabel(groupKey: string, fallback: string) {
  const labels: Record<string, string> = {
    AI_HARNESS_BASELINE: "AI Harness 판단 품질",
    WORKER_CONTROL_BASELINE: "Worker 실행 제어",
    MCP_ENGINE_BOUNDARY: "MCP / Engine 외부 경계",
    LEGAL_DIGEST_PIPELINE: "법령 동기화 / 변경 게시글",
    WORKER_POLICY_GOVERNANCE: "Worker 정책 / 승인 / 관측",
    RUNTIME_AI_PROVIDER_PROBE: "런타임 AI provider 연결",
    RUNTIME_WORKER_SCENARIO: "Worker dry-run 시나리오",
    RUNTIME_DOCUMENT_LEGAL_REVIEW_SCENARIO: "문서 법령검토 실제 모델 시나리오",
    TOKEN_COST_CONTROL: "토큰 / 비용 통제"
  };
  return labels[groupKey] ?? fallback;
}

function evaluationGroupDescription(groupKey: string) {
  const descriptions: Record<string, string> = {
    AI_HARNESS_BASELINE: "AI가 근거 밖으로 나가지 않고, 모르면 묻고, 결과 DTO를 지키는지 봅니다.",
    WORKER_CONTROL_BASELINE: "Action 실행 전 정책, 승인, 취소, 실패 격리가 되는지 봅니다.",
    MCP_ENGINE_BOUNDARY: "외부 Agent가 쓰는 MCP/API 경계에서 scope, quota, 오류 계약을 봅니다.",
    LEGAL_DIGEST_PIPELINE: "법령 원문은 보존하고 AI는 초안까지만 만드는지 봅니다.",
    WORKER_POLICY_GOVERNANCE: "권한, 사전조건, 승인 요청, 운영 지표가 분리되는지 봅니다.",
    RUNTIME_AI_PROVIDER_PROBE: "운영 설정에서 하네스 정책과 provider 연결이 실제로 가능한지 봅니다.",
    RUNTIME_WORKER_SCENARIO: "실제 Worker 실행 통로를 dry-run으로 지나가며 통제와 출력 안전성을 봅니다.",
    RUNTIME_DOCUMENT_LEGAL_REVIEW_SCENARIO: "평가용 감리일지와 법령 근거를 실제 법령검토 하네스에 넣어 모델 응답 품질을 봅니다.",
    TOKEN_COST_CONTROL: "하네스 하드캡, 실제 토큰 사용량, 가격 규칙, 사무소/사용자/플랫폼 예산 제한, 최근 폭주 징후를 봅니다."
  };
  return descriptions[groupKey] ?? "선택한 영역의 자동 평가 결과입니다.";
}

function evaluationGroupFocus(groupKey: string) {
  const focus: Record<string, string> = {
    AI_HARNESS_BASELINE: "핵심은 hallucination 방지, 구조화된 출력, action 경계 준수입니다.",
    WORKER_CONTROL_BASELINE: "핵심은 executor가 실행되기 전에 멈출 수 있고, 실패가 격리되는지입니다.",
    MCP_ENGINE_BOUNDARY: "핵심은 외부 Agent가 실패 원인을 해석할 수 있는 응답 계약입니다.",
    LEGAL_DIGEST_PIPELINE: "핵심은 AI가 법령 corpus를 바꾸지 않고 관리자 승인 전 초안에 머무는지입니다.",
    WORKER_POLICY_GOVERNANCE: "핵심은 위험 action이 승인 없이 실행되지 않고 운영 지표가 왜곡되지 않는지입니다.",
    RUNTIME_AI_PROVIDER_PROBE: "핵심은 fake provider와 실제 provider를 구분하고, 실제 연결 실패를 분리해 보는 것입니다.",
    RUNTIME_WORKER_SCENARIO: "핵심은 Worker flow, policy gate, run-control, executor, output safety가 실제 dry-run에서도 지켜지는지입니다.",
    RUNTIME_DOCUMENT_LEGAL_REVIEW_SCENARIO: "핵심은 실제 모델이 제공된 법령 근거 안에서만 판단하고, 최종 법률판정처럼 말하지 않는지입니다.",
    TOKEN_COST_CONTROL: "핵심은 특정 하네스나 Worker가 반복 호출로 토큰을 과소비하지 않도록 실행 상한, 예산 Guard, 관측 지표가 함께 있는지입니다."
  };
  return focus[groupKey] ?? "이 그룹의 평가 케이스와 근거를 확인합니다.";
}

function evaluationRunTriggerLabel(triggerType: string) {
  const labels: Record<string, string> = {
    PLATFORM_ADMIN_SNAPSHOT: "기준 기록",
    PLATFORM_ADMIN_RUNTIME_PROBE: "런타임 연결 평가",
    PLATFORM_ADMIN_RUNTIME_SCENARIO: "시나리오 평가"
  };
  return labels[triggerType] ?? displayLabel(triggerType);
}

function evaluationLayerLabel(layer: string) {
  const labels: Record<string, string> = {
    AI_HARNESS: "AI Harness",
    WORKER_CONTROL: "Worker Control",
    MCP_ENGINE: "MCP / Engine",
    LEGAL_DIGEST: "Legal Digest",
    GOVERNANCE: "Governance",
    OBSERVABILITY: "관측",
    EVALUATION: "평가"
  };
  return labels[layer] ?? displayLabel(layer);
}

function evaluationSignalLabel(signalKey: string, fallback: string) {
  const labels: Record<string, string> = {
    MODEL_PROVIDER_SWITCHABLE: "하네스별 모델 교체 가능",
    OUTPUT_SCHEMA_VALIDATION: "출력 스키마 검증",
    REFINE_RETRY: "잘못된 응답 재시도",
    ACTION_REGISTRY: "Worker Action Registry",
    POLICY_GATE: "정책 게이트",
    RUN_CONTROL_CANCEL: "실행 직전 취소",
    MCP_SCOPE_AND_QUOTA: "MCP scope/quota 분리",
    LEGAL_DRY_RUN_DRAFT: "법령 AI 초안 dry-run",
    APPROVAL_INTERLOCK: "승인 interlock",
    TRACE_AUDIT: "Trace / audit 관측",
    REAL_MODEL_EVALUATION: "실제 모델 반복 평가",
    RUNTIME_HARNESS_POLICY: "런타임 하네스 정책 해석",
    RUNTIME_PROVIDER_CONNECTIVITY: "런타임 provider 연결성",
    RUNTIME_WORKER_SCENARIO: "Worker dry-run 시나리오",
    RUNTIME_DOCUMENT_LEGAL_REVIEW: "문서 법령검토 실제 모델 시나리오",
    TOKEN_COST_CONTROL: "토큰 / 비용 통제"
  };
  return labels[signalKey] ?? fallback;
}

function evaluationCaseLabel(caseId: string, fallback: string) {
  if (caseId.startsWith("RUN-AI-POLICY-")) {
    return "하네스 실행 정책 확인";
  }
  if (caseId.startsWith("RUN-AI-PROVIDER-")) {
    return "provider 연결 평가";
  }
  if (caseId.startsWith("RUN-SCENARIO-LEGAL-DIGEST-")) {
    const labels: Record<string, string> = {
      "RUN-SCENARIO-LEGAL-DIGEST-000": "법령 변경 게시글 시나리오 데이터 확인",
      "RUN-SCENARIO-LEGAL-DIGEST-001": "법령 게시글 AI Worker dry-run 실행",
      "RUN-SCENARIO-LEGAL-DIGEST-002": "Worker 출력 안전 플래그 확인",
      "RUN-SCENARIO-LEGAL-DIGEST-003": "AI Harness 실행 추적 가능성 확인",
      "RUN-SCENARIO-LEGAL-DIGEST-004": "평가 시나리오 저장 경계 확인"
    };
    return labels[caseId] ?? "법령 게시글 Worker 시나리오";
  }
  if (caseId.startsWith("RUN-SCENARIO-DOC-LEGAL-")) {
    const labels: Record<string, string> = {
      "RUN-SCENARIO-DOC-LEGAL-000": "문서 법령검토 실행 조건 확인",
      "RUN-SCENARIO-DOC-LEGAL-001": "실제 모델 법령검토 하네스 실행",
      "RUN-SCENARIO-DOC-LEGAL-002": "제공된 법령 근거 안에서만 인용",
      "RUN-SCENARIO-DOC-LEGAL-003": "법령검토 판정 품질 확인",
      "RUN-SCENARIO-DOC-LEGAL-004": "최종 법률판정 문구 차단",
      "RUN-SCENARIO-DOC-LEGAL-005": "PASS 가능 조건 계약 확인",
      "RUN-SCENARIO-DOC-LEGAL-006": "평가 데이터 저장/변경 없음"
    };
    return labels[caseId] ?? "문서 법령검토 실제 모델 시나리오";
  }
  if (caseId.startsWith("RUN-TOKEN-")) {
    const labels: Record<string, string> = {
      "RUN-TOKEN-001": "하네스 반복 호출 / timeout 상한",
      "RUN-TOKEN-002": "실제 provider 모델 가격 규칙",
      "RUN-TOKEN-003": "사무소 / 사용자 예산 한도",
      "RUN-TOKEN-004": "월간 토큰 사용량 관측",
      "RUN-TOKEN-005": "최근 1시간 반복 호출 / 토큰 폭주 징후",
      "RUN-TOKEN-006": "플랫폼 하네스 예산 Guard"
    };
    return labels[caseId] ?? "토큰 / 비용 통제 평가";
  }
  const labels: Record<string, string> = {
    "AI-H-001": "법령 요약이 입력된 근거 조문만 사용",
    "AI-H-002": "별표/서식 변경은 사람 검토로 분류",
    "AI-H-003": "대화 플래너가 허용된 action만 제안",
    "AI-H-004": "문맥 부족 시 억지 실행 대신 추가 확인",
    "AI-H-005": "삭제된 체크리스트 항목은 사람 검토로 분류",
    "AI-H-006": "복수 조문 변경에서도 모든 근거 key 유지",
    "AI-H-007": "문서 생성 action이 가능할 때만 제안",
    "AI-H-008": "단순 확인 응답은 action 없음으로 처리",
    "AI-H-009": "사진 근거가 있으면 preflight PASS",
    "AI-H-010": "사진 근거 누락 finding 유지",
    "AI-H-011": "서명 슬롯 누락은 생성 차단",
    "AI-H-012": "법령 근거 누락은 경고로 유지",
    "AI-H-013": "법령검토 PASS 문구를 신중하게 제한",
    "AI-H-014": "검색 후보만으로는 법령검토 PASS 금지",
    "AI-H-015": "업무 항목 법령근거와 검색 후보 구분",
    "AI-H-016": "모호한 감리 증빙은 사람 확인으로 분류",
    "WK-C-001": "허용된 action은 한 번만 실행",
    "WK-C-002": "정의 없는 action은 정책 전 거절",
    "WK-C-003": "정책 거절 시 executor 미실행",
    "WK-C-004": "승인 필요 시 pending으로 정지",
    "WK-C-005": "run-control cancel 시 실행 직전 취소",
    "WK-C-006": "executor 실패는 실패 결과로 격리",
    "WK-C-007": "executor 거절 결과를 거절 trace로 기록",
    "WK-C-008": "executor 승인 필요 결과를 승인 trace로 기록",
    "WK-C-009": "executor 취소 결과를 거절과 분리",
    "WK-C-010": "executor 예외를 실패 결과로 포착",
    "MCP-E-001": "Engine API Key 없이는 MCP 차단",
    "MCP-E-002": "MCP initialize 응답 계약 유지",
    "MCP-E-003": "알 수 없는 MCP method 구분",
    "MCP-E-004": "invalid params와 unknown tool 구분",
    "MCP-E-005": "get_legal_updates 사용량 기록",
    "MCP-E-006": "법령 검색/조문 조회 사용량 기록",
    "MCP-E-007": "scope 부족 오류 구분",
    "MCP-E-008": "quota 초과 오류 구분",
    "MCP-E-009": "Engine 응답 DTO 계약 유지",
    "LEG-D-001": "fake 법령 source를 운영 목록에서 제외",
    "LEG-D-002": "deterministic digest refresh 대상 분리",
    "LEG-D-003": "추가/수정/삭제 diff 분리",
    "LEG-D-004": "사용자 변경사항에서 fake source 제외",
    "LEG-D-005": "변경 상세에서 fake source 제외",
    "LEG-D-006": "법령 AI worker는 non-dry-run 거절",
    "LEG-D-007": "AI 초안 생성이 원문/digest를 변경하지 않음",
    "LEG-D-008": "관리자 AI 초안 생성은 Worker dry-run 경유",
    "GOV-W-001": "필수 문맥이 있는 UI action 허용",
    "GOV-W-002": "비활성 action 정의 차단",
    "GOV-W-003": "허용되지 않은 요청 source 차단",
    "GOV-W-004": "필수 context 누락 차단",
    "GOV-W-005": "승인 필요 action은 승인 전 차단",
    "GOV-W-006": "승인된 실행만 unlock",
    "GOV-W-007": "preflight 미통과 문서 생성 차단",
    "GOV-W-008": "취소/실패/차단/승인 지표 분리"
  };
  return labels[caseId] ?? fallback;
}

function evaluationVerificationLabel(verification: string) {
  const labels: Record<string, string> = {
    GRADLE_TEST: "자동 테스트",
    RUNTIME_PROBE: "런타임 설정 점검",
    PROVIDER_CONNECTION_TEST: "Provider 연결 테스트",
    WORKER_DRY_RUN: "Worker dry-run",
    WORKER_OUTPUT_SAFETY: "출력 안전성 점검",
    WORKER_TRACE_SCENARIO: "실행 추적 점검",
    REAL_MODEL_LEGAL_REVIEW: "실제 모델 법령검토",
    TOKEN_USAGE_POLICY: "토큰 사용 정책",
    TOKEN_COST_POLICY: "비용 / 예산 정책",
    TOKEN_USAGE_TELEMETRY: "토큰 사용량 관측"
  };
  return labels[verification] ?? displayLabel(verification);
}

function evaluationEvidenceText(item: AiWorkerEvaluationCase) {
  const evidence = item.evidence ?? "";
  if (item.caseId.startsWith("RUN-AI-PROVIDER-") && evidence.toLowerCase().includes("fake")) {
    return "개발용 Fake provider라 실제 외부 모델 호출은 하지 않았습니다. AI 하네스 관리에서 이 provider를 계속 개발용으로 둘지, 실제 provider로 바꿀지 확인하세요.";
  }
  if (item.caseId.startsWith("RUN-AI-POLICY-") && evidence.includes("Harness policy is not runnable")) {
    return evidence
      .replace("Harness policy is not runnable:", "하네스 정책이 아직 실행 가능 상태가 아닙니다:")
      .replace("PROVIDER_NOT_ASSIGNED", "provider 미배정")
      .replace("POLICY_NOT_CONFIGURED", "정책 미설정")
      .replace("POLICY_DISABLED", "정책 비활성화")
      .replace("PROVIDER_NOT_ACTIVE", "provider 비활성화")
      .replace("MODEL_NOT_CONFIGURED", "모델 미설정");
  }
  if (item.caseId.startsWith("RUN-AI-POLICY-") && evidence.includes("Harness policy resolves to provider")) {
    return evidence.replace("Harness policy resolves to provider", "하네스 정책이 다음 provider로 해석됐습니다:");
  }
  return evidence;
}

function AiObserverTabBar({
  busy,
  tab,
  onTabChange,
  onRefresh
}: {
  busy: boolean;
  tab: AiObserverTabKey;
  onTabChange: (tab: AiObserverTabKey) => void;
  onRefresh: () => Promise<void>;
}) {
  return (
    <div className="ai-observer-toolbar">
      <div className="ai-observer-tabs" role="tablist" aria-label="AI 관측 메뉴">
        {aiObserverTabs.map((item) => (
          <button
            aria-selected={tab === item.key}
            className={tab === item.key ? "ai-observer-tab active" : "ai-observer-tab"}
            key={item.key}
            onClick={() => onTabChange(item.key)}
            role="tab"
            type="button"
          >
            {item.label}
          </button>
        ))}
      </div>
      <button className="button" disabled={busy} onClick={onRefresh} type="button">
        {busy ? <Loader2 className="spin" size={16} /> : <RefreshCcw size={16} />}
        새로고침
      </button>
    </div>
  );
}

function AiObserverSummaryPanel({
  callLogs,
  findings,
  harnessTraces,
  mode,
  observations
}: {
  callLogs: AiModelCallLog[];
  findings: PlatformReportPreflightFinding[];
  harnessTraces: AiHarnessTraceEvent[];
  mode: AiObservationMode | null;
  observations: AiObservation[];
}) {
  const enabled = mode?.enabled ?? false;
  const openBlockingCount = findings.filter(isOpenBlockingAiFinding).length;
  const openFindingCount = findings.filter((finding) => finding.resolutionStatus === "OPEN").length;
  const harnessFailureCount = harnessTraces.filter((trace) => trace.eventType === "RUN_FAILED" || trace.eventType === "CALL_FAILED").length;
  const callFailureCount = callLogs.filter((log) => log.status === "FAILED").length;
  const latestObservation = observations[0] ?? null;
  const latestCall = callLogs[0] ?? null;
  const tokenTotal = callLogs.reduce((sum, log) => sum + (log.inputTokens ?? 0) + (log.outputTokens ?? 0), 0);
  const knownCostLogs = callLogs.filter((log) => log.estimatedTotalCost != null && log.costCurrency);
  const latestCostCurrency = knownCostLogs[0]?.costCurrency ?? "";
  const costTotal = knownCostLogs.reduce((sum, log) => sum + (log.estimatedTotalCost ?? 0), 0);

  return (
    <Panel
      title="AI 관측 요약"
      icon={<Activity size={18} />}
      count={observations.length + harnessTraces.length + findings.length + callLogs.length}
    >
      <div className="metric-grid ai-observer-metrics">
        <MetricCard
          label="원문 관측"
          value={enabled ? "켜짐" : "꺼짐"}
          detail={`${mode?.currentEntryCount ?? observations.length}건 임시 보관`}
          icon={<Activity size={18} />}
          tone={enabled ? "amber" : "slate"}
        />
        <MetricCard
          label="검토 미처리"
          value={openFindingCount}
          detail={`차단급 ${openBlockingCount}건`}
          icon={<AlertTriangle size={18} />}
          tone={openBlockingCount > 0 ? "red" : openFindingCount > 0 ? "amber" : "green"}
        />
        <MetricCard
          label="하네스 실패"
          value={harnessFailureCount}
          detail={`${harnessTraces.length}개 이벤트 기준`}
          icon={<ShieldCheck size={18} />}
          tone={harnessFailureCount > 0 ? "red" : "green"}
        />
        <MetricCard
          label="호출 실패"
          value={callFailureCount}
          detail={`${callLogs.length}개 최근 호출 기준`}
          icon={<Gauge size={18} />}
          tone={callFailureCount > 0 ? "red" : "green"}
        />
        <MetricCard
          label="최근 토큰"
          value={tokenTotal}
          detail="최근 호출 입력 + 출력"
          icon={<KeyRound size={18} />}
          tone="blue"
        />
        <MetricCard
          label="예상 비용"
          value={knownCostLogs.length === 0 ? "-" : `${latestCostCurrency} ${formatMoney(costTotal)}`}
          detail="최근 호출 합산"
          icon={<Gauge size={18} />}
          tone="green"
        />
      </div>

      <div className="ai-observer-guide">
        <div className="ai-observer-priority">
          <strong>먼저 볼 것</strong>
          <span>
            {openBlockingCount > 0
              ? "검토 결과 탭에서 높음/긴급 미처리 항목을 먼저 확인하세요."
              : callFailureCount > 0
                ? "호출/비용 탭에서 실패한 Provider 호출을 먼저 확인하세요."
                : harnessFailureCount > 0
                  ? "하네스 실행 탭에서 실패 이벤트와 실행 ID를 확인하세요."
                  : "현재는 치명적인 AI 관리 이슈가 보이지 않습니다."}
          </span>
        </div>
        <div className="ai-observer-guide-list">
          <div>
            <strong>원문 관측</strong>
            <span>테스트 중 모델이 실제로 받은 프롬프트와 응답을 확인합니다. 운영 중에는 잠깐만 켭니다.</span>
          </div>
          <div>
            <strong>검토 결과</strong>
            <span>사용자가 조치해야 하는 문서 생성 전 경고와 차단 항목을 봅니다.</span>
          </div>
          <div>
            <strong>하네스 실행</strong>
            <span>하네스가 어느 단계에서 성공/실패했는지 실행 단위로 추적합니다.</span>
          </div>
          <div>
            <strong>호출/비용</strong>
            <span>Provider, 모델, 토큰, 지연시간, 실패 메시지와 비용을 확인합니다.</span>
          </div>
        </div>
        <div className="ai-observer-latest">
          <div>
            <span>최근 원문 관측</span>
            <strong>
              {latestObservation
                ? `${latestObservation.providerCode} / ${latestObservation.modelName} / ${displayLabel(latestObservation.status)}`
                : "없음"}
            </strong>
          </div>
          <div>
            <span>최근 호출 로그</span>
            <strong>
              {latestCall
                ? `${latestCall.providerCode} / ${latestCall.modelName} / ${displayLabel(latestCall.status)}`
                : "없음"}
            </strong>
          </div>
        </div>
      </div>
    </Panel>
  );
}

function AiObservationPanel({
  busy,
  mode,
  observations,
  onSetEnabled,
  onClear
}: {
  busy: boolean;
  mode: AiObservationMode | null;
  observations: AiObservation[];
  onSetEnabled: (enabled: boolean) => Promise<void>;
  onClear: () => Promise<void>;
}) {
  const enabled = mode?.enabled ?? false;
  const latest = observations[0] ?? null;

  return (
    <Panel
      title="AI 원문 관측"
      icon={<Activity size={18} />}
      count={mode?.currentEntryCount ?? observations.length}
      action={
        <div className="card-actions compact">
          <button
            className={enabled ? "button danger" : "button primary"}
            disabled={busy}
            onClick={() => onSetEnabled(!enabled)}
            type="button"
          >
            {busy ? <Loader2 className="spin" size={16} /> : enabled ? <XCircle size={16} /> : <CheckCircle2 size={16} />}
            {enabled ? "관측 끄기" : "관측 켜기"}
          </button>
          <button className="button" disabled={busy || observations.length === 0} onClick={onClear} type="button">
            <XCircle size={16} />
            비우기
          </button>
        </div>
      }
    >
      <div className={enabled ? "ai-observation-mode enabled" : "ai-observation-mode"}>
        <div>
          <strong>{enabled ? "디버그 관측 모드가 켜져 있습니다." : "디버그 관측 모드가 꺼져 있습니다."}</strong>
          <span>
            {enabled
              ? `최근 ${mode?.maxEntries ?? 0}건, 최대 ${mode?.ttlMinutes ?? 0}분 동안 메모리에만 보관합니다. 서버 재시작 또는 모드 끄기 시 사라집니다.`
              : "원문 프롬프트와 모델 응답은 저장하지 않습니다. 모델 평가가 필요할 때만 잠깐 켜세요."}
          </span>
        </div>
        {latest ? (
          <small>
            최근 호출: {latest.providerCode} / {latest.modelName} / {displayLabel(latest.status)} / {formatDate(latest.updatedAt)}
          </small>
        ) : (
          <small>관측 모드를 켠 뒤 AI 검토나 Provider 테스트를 실행하면 여기에 원문이 표시됩니다.</small>
        )}
      </div>

      {observations.length === 0 ? (
        <EmptyState message={enabled ? "아직 관측된 AI 호출이 없습니다." : "관측 모드를 켜면 최근 AI 호출 원문을 볼 수 있습니다."} />
      ) : (
        <div className="ai-observation-list">
          {observations.map((observation) => (
            <details className="ai-observation-card" key={observation.callId} open={observation.callId === latest?.callId}>
              <summary>
                <div>
                  <strong>{displayLabel(observation.feature ?? observation.workflowType ?? "AI 호출")}</strong>
                  <span>{observation.providerCode} / {observation.modelName}</span>
                </div>
                <div className="ai-observation-summary-meta">
                  <StatusBadge status={observation.status} />
                  <span>{tokenUsageSummary(observation)}</span>
                  <span>{observation.latencyMs == null ? "-" : `${observation.latencyMs}ms`}</span>
                </div>
              </summary>

              <div className="ai-observation-meta-grid">
                <div>
                  <span>호출 ID</span>
                  <strong>{observation.callId}</strong>
                </div>
                <div>
                  <span>사무소</span>
                  <strong>{observation.officeId ? `#${observation.officeId}` : "-"}</strong>
                </div>
                <div>
                  <span>리소스</span>
                  <strong>{observation.resourceType ? `${displayLabel(observation.resourceType)} #${observation.resourceId ?? "-"}` : "-"}</strong>
                </div>
                <div>
                  <span>완료 사유</span>
                  <strong>{observation.finishReason ?? "-"}</strong>
                </div>
              </div>

              {Object.keys(observation.requestOptions ?? {}).length > 0 ? (
                <AiObservationCodeBlock
                  title="요청 메타데이터"
                  value={JSON.stringify(observation.requestOptions, null, 2)}
                />
              ) : null}

              <div className="ai-observation-section">
                <div className="ai-observation-section-title">
                  <strong>렌더링된 프롬프트</strong>
                  {observation.promptTruncated ? <span>일부 잘림</span> : null}
                </div>
                {observation.promptMessages.map((message, index) => (
                  <AiObservationCodeBlock
                    key={`${observation.callId}-${message.role}-${index}`}
                    title={message.role}
                    value={message.content}
                  />
                ))}
              </div>

              <div className="ai-observation-section">
                <div className="ai-observation-section-title">
                  <strong>모델 원문 응답</strong>
                  {observation.responseTruncated ? <span>일부 잘림</span> : null}
                </div>
                <AiObservationCodeBlock
                  title={observation.errorType ? `오류: ${observation.errorType}` : "response"}
                  value={observation.errorMessage ?? observation.responseText ?? "아직 응답을 받지 않았습니다."}
                />
              </div>
            </details>
          ))}
        </div>
      )}
    </Panel>
  );
}

function AiObservationCodeBlock({ title, value }: { title: string; value: string }) {
  async function copy() {
    if (!navigator.clipboard) {
      return;
    }
    await navigator.clipboard.writeText(value);
  }

  return (
    <div className="ai-observation-code-block">
      <div>
        <span>{title}</span>
        <button className="icon-button" onClick={copy} title="복사" type="button">
          <Copy size={14} />
        </button>
      </div>
      <pre>{value}</pre>
    </div>
  );
}

function AiHarnessTracePanel({ traces }: { traces: AiHarnessTraceEvent[] }) {
  const failed = traces.filter((trace) => trace.eventType === "RUN_FAILED" || trace.eventType === "CALL_FAILED").length;
  const completed = traces.filter((trace) => trace.eventType === "RUN_COMPLETED").length;
  const latestRunId = traces[0]?.harnessRunId;

  return (
    <Panel
      title="AI 하네스 관측"
      icon={<Activity size={18} />}
      count={traces.length}
      action={
        <div className="panel-kpis compact">
          <span>완료 {completed}</span>
          <span>실패 {failed}</span>
        </div>
      }
    >
      <div className="observer-summary">
        <div>
          <strong>{latestRunId ? "최근 실행 감지됨" : "최근 실행 없음"}</strong>
          <span>{latestRunId ?? "하네스가 실행되면 단계별 이벤트가 여기에 표시됩니다."}</span>
        </div>
        <small>프롬프트 본문과 응답 원문은 저장하지 않고 운영 메타데이터만 남깁니다.</small>
      </div>
      <Table
        columns={["시간", "하네스", "이벤트", "상태", "시도", "모델", "호출", "검증", "토큰", "메시지"]}
        empty="AI 하네스 관측 이벤트가 없습니다."
        rows={traces.map((trace) => [
          formatDate(trace.createdAt),
          <CellTitle key="harness" title={displayLabel(trace.harnessId)} subtitle={trace.harnessRunId} />,
          displayLabel(trace.eventType),
          trace.status ? <StatusBadge key="status" status={trace.status} /> : "-",
          trace.attempt ?? "-",
          trace.modelId ?? "-",
          trace.callId ?? "-",
          trace.validationValid == null
            ? "-"
            : trace.validationValid
              ? "통과"
              : `실패 ${trace.validationErrorCount ?? 0}`,
          trace.inputTokens == null && trace.outputTokens == null ? "-" : `${trace.inputTokens ?? 0} / ${trace.outputTokens ?? 0}`,
          trace.errorType ? `${trace.errorType}: ${trace.message ?? "-"}` : trace.message ?? "-"
        ])}
      />
    </Panel>
  );
}

function AiProviderStatusPanel({
  providers,
  policies,
  callLogs
}: {
  providers: AiProviderCredential[];
  policies: OfficeAiPolicy[];
  callLogs: AiModelCallLog[];
}) {
  const activeProviders = providers.filter((provider) => provider.status === "ACTIVE");
  const activeFakeProviders = activeProviders.filter((provider) => isFakeAiProvider(provider.providerCode));
  const activeRealProviders = activeProviders.filter((provider) => !isFakeAiProvider(provider.providerCode));
  const aiEnabledPolicies = policies.filter((policy) => policy.effectiveAiEnabled);
  const reviewEnabledPolicies = aiEnabledPolicies.filter((policy) => policy.documentReviewAiEnabled);
  const generationEnabledPolicies = aiEnabledPolicies.filter((policy) => policy.documentGenerationAiEnabled);
  const recentFailures = callLogs.filter((log) => log.status === "FAILED").slice(0, 3);
  const policyCountByProviderId = new Map<number, number>();
  policies.forEach((policy) => {
    if (policy.effectiveAiEnabled && policy.preferredProviderCredentialId != null) {
      policyCountByProviderId.set(
        policy.preferredProviderCredentialId,
        (policyCountByProviderId.get(policy.preferredProviderCredentialId) ?? 0) + 1
      );
    }
  });
  const latestCallByProvider = new Map<string, AiModelCallLog>();
  callLogs.forEach((log) => {
    if (!latestCallByProvider.has(log.providerCode)) {
      latestCallByProvider.set(log.providerCode, log);
    }
  });

  return (
    <Panel title="AI 연결 현황" icon={<KeyRound size={18} />} count={activeProviders.length}>
      <div className="ai-status-summary">
        <div>
          <strong>{activeProviders.length}</strong>
          <span>활성 Provider</span>
          <small>Fake {activeFakeProviders.length}개 / 실제 {activeRealProviders.length}개</small>
        </div>
        <div>
          <strong>{aiEnabledPolicies.length}</strong>
          <span>AI 사용 사무소</span>
          <small>검토 {reviewEnabledPolicies.length}개 / 생성 {generationEnabledPolicies.length}개</small>
        </div>
        <div>
          <strong>{recentFailures.length}</strong>
          <span>최근 실패</span>
          <small>{callLogs.length > 0 ? "최근 호출 로그 기준" : "호출 기록 없음"}</small>
        </div>
      </div>

      {providers.length === 0 ? (
        <EmptyState message="등록된 AI Provider가 없습니다." />
      ) : (
        <div className="ai-provider-status-list">
          {providers.map((provider) => {
            const latestCall = latestCallByProvider.get(provider.providerCode);
            const fake = isFakeAiProvider(provider.providerCode);
            const keyState = fake
              ? "키 불필요"
              : provider.apiKeyConfigured
                ? provider.apiKeyMasked ?? "키 설정됨"
                : "키 없음";
            return (
              <div className="ai-provider-status-row" key={provider.id}>
                <div className="ai-provider-status-main">
                  <ProviderModeBadge provider={provider} />
                  <div>
                    <strong>{provider.displayName}</strong>
                    <span>{provider.providerCode} / {displayLabel(provider.providerType)}</span>
                  </div>
                </div>
                <dl>
                  <div>
                    <dt>상태</dt>
                    <dd><StatusBadge status={provider.status} /></dd>
                  </div>
                  <div>
                    <dt>모델</dt>
                    <dd>{provider.defaultModel ?? "-"}</dd>
                  </div>
                  <div>
                    <dt>키</dt>
                    <dd>{keyState}</dd>
                  </div>
                  <div>
                    <dt>연결 사무소</dt>
                    <dd>{policyCountByProviderId.get(provider.id) ?? 0}개</dd>
                  </div>
                  <div>
                    <dt>최근 호출</dt>
                    <dd>{latestCall ? `${displayLabel(latestCall.status)} · ${formatDate(latestCall.completedAt)}` : "없음"}</dd>
                  </div>
                </dl>
              </div>
            );
          })}
        </div>
      )}

      {recentFailures.length > 0 ? (
        <div className="ai-status-failures">
          <strong>최근 실패</strong>
          {recentFailures.map((log) => (
            <span key={log.id}>
              {log.providerCode} / {log.modelName}: {log.errorMessage ?? log.errorType ?? "실패"}
            </span>
          ))}
        </div>
      ) : null}
    </Panel>
  );
}

function AiProviderForm({
  busy,
  onSubmit
}: {
  busy: boolean;
  onSubmit: (body: {
    providerCode: string;
    displayName: string;
    providerType: string;
    baseUrl?: string | null;
    defaultModel?: string | null;
    apiKey?: string | null;
  }) => Promise<void>;
}) {
  const [values, setValues] = useState({
    providerCode: "openai-main",
    displayName: "OpenAI Main",
    providerType: "OPENAI",
    baseUrl: "https://api.openai.com/v1",
    defaultModel: "gpt-4.1-mini",
    apiKey: ""
  });

  async function submit(event: FormEvent) {
    event.preventDefault();
    await onSubmit({
      providerCode: values.providerCode,
      displayName: values.displayName,
      providerType: values.providerType,
      baseUrl: normalizeFormValue(values.baseUrl),
      defaultModel: normalizeFormValue(values.defaultModel),
      apiKey: normalizeFormValue(values.apiKey)
    });
    setValues({ ...values, apiKey: "" });
  }

  return (
    <form className="ai-policy-form" onSubmit={submit}>
      <label>
        코드
        <input value={values.providerCode} onChange={(event) => setValues({ ...values, providerCode: event.target.value })} required />
      </label>
      <label>
        표시명
        <input value={values.displayName} onChange={(event) => setValues({ ...values, displayName: event.target.value })} required />
      </label>
      <label>
        제공자 유형
        <select value={values.providerType} onChange={(event) => setValues({ ...values, providerType: event.target.value })}>
          {aiProviderTypeOptions.map((option) => (
            <option key={option} value={option}>
              {option}
            </option>
          ))}
        </select>
      </label>
      <label>
        기본 URL
        <input value={values.baseUrl} onChange={(event) => setValues({ ...values, baseUrl: event.target.value })} />
      </label>
      <label>
        기본 모델
        <input value={values.defaultModel} onChange={(event) => setValues({ ...values, defaultModel: event.target.value })} />
      </label>
      <label>
        API 키
        <input
          autoComplete="off"
          type="password"
          value={values.apiKey}
          onChange={(event) => setValues({ ...values, apiKey: event.target.value })}
          placeholder="저장 후 원문은 다시 표시하지 않습니다"
        />
      </label>
      <button className="button primary" disabled={busy} type="submit">
        {busy ? <Loader2 className="spin" size={16} /> : <Plus size={16} />}
        등록
      </button>
    </form>
  );
}

function AiProviderEditForm({
  busy,
  provider,
  onSubmit,
  onCancel
}: {
  busy: boolean;
  provider: AiProviderCredential;
  onSubmit: (
    providerId: number,
    body: {
      displayName: string;
      providerType: string;
      baseUrl?: string | null;
      defaultModel?: string | null;
      apiKey?: string | null;
    }
  ) => Promise<void>;
  onCancel: () => void;
}) {
  const [values, setValues] = useState({
    displayName: provider.displayName,
    providerType: provider.providerType,
    baseUrl: provider.baseUrl ?? "",
    defaultModel: provider.defaultModel ?? "",
    apiKey: ""
  });

  useEffect(() => {
    setValues({
      displayName: provider.displayName,
      providerType: provider.providerType,
      baseUrl: provider.baseUrl ?? "",
      defaultModel: provider.defaultModel ?? "",
      apiKey: ""
    });
  }, [provider.id]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    await onSubmit(provider.id, {
      displayName: values.displayName,
      providerType: values.providerType,
      baseUrl: normalizeFormValue(values.baseUrl),
      defaultModel: normalizeFormValue(values.defaultModel),
      apiKey: normalizeFormValue(values.apiKey)
    });
  }

  return (
    <form className="ai-policy-form provider-edit-form" onSubmit={submit}>
      <label>
        표시명
        <input value={values.displayName} onChange={(event) => setValues({ ...values, displayName: event.target.value })} required />
      </label>
      <label>
        제공자 유형
        <select value={values.providerType} onChange={(event) => setValues({ ...values, providerType: event.target.value })}>
          {aiProviderTypeOptions.map((option) => (
            <option key={option} value={option}>
              {option}
            </option>
          ))}
        </select>
      </label>
      <label>
        기본 URL
        <input value={values.baseUrl} onChange={(event) => setValues({ ...values, baseUrl: event.target.value })} />
      </label>
      <label>
        기본 모델
        <input value={values.defaultModel} onChange={(event) => setValues({ ...values, defaultModel: event.target.value })} />
      </label>
      <label>
        새 API 키
        <input
          autoComplete="off"
          type="password"
          value={values.apiKey}
          onChange={(event) => setValues({ ...values, apiKey: event.target.value })}
          placeholder="비워두면 기존 키를 유지합니다"
        />
      </label>
      <div className="card-actions">
        <button className="button primary" disabled={busy} type="submit">
          {busy ? <Loader2 className="spin" size={16} /> : <KeyRound size={16} />}
          수정 저장
        </button>
        <button className="button" disabled={busy} onClick={onCancel} type="button">
          취소
        </button>
      </div>
    </form>
  );
}

function AiPricingRuleForm({
  busy,
  providers,
  onSubmit
}: {
  busy: boolean;
  providers: AiProviderCredential[];
  onSubmit: (body: {
    providerCode: string;
    modelName: string;
    currency: string;
    inputTokenPricePerMillion: number;
    outputTokenPricePerMillion: number;
  }) => Promise<void>;
}) {
  const defaultProvider = providers[0]?.providerCode ?? "openai-main";
  const [values, setValues] = useState({
    providerCode: defaultProvider,
    modelName: providers[0]?.defaultModel ?? "gpt-4.1-mini",
    currency: "USD",
    inputTokenPricePerMillion: "0.15",
    outputTokenPricePerMillion: "0.60"
  });

  useEffect(() => {
    if (providers.length === 0 || values.providerCode) {
      return;
    }
    setValues({
      ...values,
      providerCode: providers[0].providerCode,
      modelName: providers[0].defaultModel ?? values.modelName
    });
  }, [providers.length]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    await onSubmit({
      providerCode: values.providerCode,
      modelName: values.modelName,
      currency: values.currency,
      inputTokenPricePerMillion: Number(values.inputTokenPricePerMillion),
      outputTokenPricePerMillion: Number(values.outputTokenPricePerMillion)
    });
  }

  return (
    <form className="ai-policy-form" onSubmit={submit}>
      <label>
        제공자
        <select
          value={values.providerCode}
          onChange={(event) => {
            const provider = providers.find((item) => item.providerCode === event.target.value);
            setValues({
              ...values,
              providerCode: event.target.value,
              modelName: provider?.defaultModel ?? values.modelName
            });
          }}
        >
          {providers.length === 0 ? <option value={values.providerCode}>{values.providerCode}</option> : null}
          {providers.map((provider) => (
            <option key={provider.id} value={provider.providerCode}>
              {provider.displayName} / {provider.providerCode}
            </option>
          ))}
        </select>
      </label>
      <label>
        모델
        <input value={values.modelName} onChange={(event) => setValues({ ...values, modelName: event.target.value })} required />
      </label>
      <label>
        통화
        <input value={values.currency} onChange={(event) => setValues({ ...values, currency: event.target.value })} required />
      </label>
      <label>
        입력 토큰 / 100만
        <input
          min="0"
          step="0.00000001"
          type="number"
          value={values.inputTokenPricePerMillion}
          onChange={(event) => setValues({ ...values, inputTokenPricePerMillion: event.target.value })}
          required
        />
      </label>
      <label>
        출력 토큰 / 100만
        <input
          min="0"
          step="0.00000001"
          type="number"
          value={values.outputTokenPricePerMillion}
          onChange={(event) => setValues({ ...values, outputTokenPricePerMillion: event.target.value })}
          required
        />
      </label>
      <div className="policy-note">모델명을 "*"로 입력하면 해당 제공자의 기본 단가로 사용합니다.</div>
      <button className="button primary" disabled={busy} type="submit">
        {busy ? <Loader2 className="spin" size={16} /> : <Plus size={16} />}
        단가 저장
      </button>
    </form>
  );
}

function AiBudgetPolicyForm({
  busy,
  policies,
  onSubmit
}: {
  busy: boolean;
  policies: OfficeAiPolicy[];
  onSubmit: (
    officeId: number,
    body: {
      aiEnabled: boolean;
      documentReviewAiEnabled: boolean;
      documentGenerationAiEnabled: boolean;
      preferredProviderCredentialId?: number | null;
      credentialDeliveryMode: string;
      budgetEnforcementEnabled?: boolean;
      monthlyBudgetAmount?: number | null;
      budgetCurrency?: string | null;
      dailyCallLimit?: number | null;
      monthlyTokenLimit?: number | null;
      maxOutputTokens?: number | null;
      perUserDailyCallLimit?: number | null;
      perUserMonthlyTokenLimit?: number | null;
    }
  ) => Promise<void>;
}) {
  const [officeId, setOfficeId] = useState<number | null>(policies[0]?.officeId ?? null);
  const selectedPolicy = policies.find((policy) => policy.officeId === officeId) ?? policies[0] ?? null;
  const [budgetEnabled, setBudgetEnabled] = useState(false);
  const [monthlyBudgetAmount, setMonthlyBudgetAmount] = useState("");
  const [budgetCurrency, setBudgetCurrency] = useState("USD");
  const [dailyCallLimit, setDailyCallLimit] = useState("");
  const [monthlyTokenLimit, setMonthlyTokenLimit] = useState("");
  const [maxOutputTokens, setMaxOutputTokens] = useState("");
  const [perUserDailyCallLimit, setPerUserDailyCallLimit] = useState("");
  const [perUserMonthlyTokenLimit, setPerUserMonthlyTokenLimit] = useState("");

  useEffect(() => {
    if (!selectedPolicy) {
      return;
    }
    setOfficeId(selectedPolicy.officeId);
    setBudgetEnabled(selectedPolicy.budgetEnforcementEnabled);
    setMonthlyBudgetAmount(selectedPolicy.monthlyBudgetAmount == null ? "" : String(selectedPolicy.monthlyBudgetAmount));
    setBudgetCurrency(selectedPolicy.budgetCurrency ?? "USD");
    setDailyCallLimit(selectedPolicy.dailyCallLimit == null ? "" : String(selectedPolicy.dailyCallLimit));
    setMonthlyTokenLimit(selectedPolicy.monthlyTokenLimit == null ? "" : String(selectedPolicy.monthlyTokenLimit));
    setMaxOutputTokens(String(selectedPolicy.maxOutputTokens ?? 2000));
    setPerUserDailyCallLimit(String(selectedPolicy.perUserDailyCallLimit ?? 30));
    setPerUserMonthlyTokenLimit(String(selectedPolicy.perUserMonthlyTokenLimit ?? 500000));
  }, [selectedPolicy?.officeId]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!selectedPolicy) {
      return;
    }
    await onSubmit(selectedPolicy.officeId, {
      aiEnabled: selectedPolicy.aiEnabled,
      documentReviewAiEnabled: selectedPolicy.documentReviewAiEnabled,
      documentGenerationAiEnabled: selectedPolicy.documentGenerationAiEnabled,
      preferredProviderCredentialId: selectedPolicy.preferredProviderCredentialId ?? null,
      credentialDeliveryMode: selectedPolicy.credentialDeliveryMode,
      budgetEnforcementEnabled: budgetEnabled,
      monthlyBudgetAmount: optionalNumber(monthlyBudgetAmount),
      budgetCurrency: normalizeFormValue(budgetCurrency),
      dailyCallLimit: optionalNumber(dailyCallLimit),
      monthlyTokenLimit: optionalNumber(monthlyTokenLimit),
      maxOutputTokens: optionalNumber(maxOutputTokens),
      perUserDailyCallLimit: optionalNumber(perUserDailyCallLimit),
      perUserMonthlyTokenLimit: optionalNumber(perUserMonthlyTokenLimit)
    });
  }

  if (policies.length === 0) {
    return <EmptyState message="사무소가 없습니다." />;
  }

  return (
    <form className="ai-policy-form" onSubmit={submit}>
      <label>
        사무소
        <select value={officeId ?? ""} onChange={(event) => setOfficeId(Number(event.target.value))}>
          {policies.map((policy) => (
            <option key={policy.officeId} value={policy.officeId}>
              {policy.officeName} / {policy.officeCode}
            </option>
          ))}
        </select>
      </label>
      <label className="toggle-row">
        <input type="checkbox" checked={budgetEnabled} onChange={(event) => setBudgetEnabled(event.target.checked)} />
        AI 예산 제한 적용
      </label>
      <label>
        월 예산
        <input min="0" step="0.00000001" type="number" value={monthlyBudgetAmount} onChange={(event) => setMonthlyBudgetAmount(event.target.value)} />
      </label>
      <label>
        통화
        <input value={budgetCurrency} onChange={(event) => setBudgetCurrency(event.target.value)} />
      </label>
      <label>
        일 호출 한도
        <input min="0" step="1" type="number" value={dailyCallLimit} onChange={(event) => setDailyCallLimit(event.target.value)} />
      </label>
      <label>
        월 토큰 한도
        <input min="0" step="1" type="number" value={monthlyTokenLimit} onChange={(event) => setMonthlyTokenLimit(event.target.value)} />
      </label>
      <label>
        출력 토큰 상한
        <input min="1" step="1" type="number" value={maxOutputTokens} onChange={(event) => setMaxOutputTokens(event.target.value)} />
      </label>
      <label>
        사용자 일일 호출 한도
        <input min="0" step="1" type="number" value={perUserDailyCallLimit} onChange={(event) => setPerUserDailyCallLimit(event.target.value)} />
      </label>
      <label>
        사용자 월간 토큰 한도
        <input min="0" step="1" type="number" value={perUserMonthlyTokenLimit} onChange={(event) => setPerUserMonthlyTokenLimit(event.target.value)} />
      </label>
      <div className="policy-note">월 예산을 적용하려면 모델별 단가나 제공자 "*" 기본 단가가 활성 상태여야 합니다.</div>
      <button className="button primary" disabled={busy} type="submit">
        {busy ? <Loader2 className="spin" size={16} /> : <ShieldCheck size={16} />}
        예산 저장
      </button>
    </form>
  );
}

function AiHarnessPolicyPanel({
  busy,
  policies,
  providers,
  onSubmit
}: {
  busy: boolean;
  policies: AiHarnessPolicy[];
  providers: AiProviderCredential[];
  onSubmit: (
    policyKey: string,
    body: {
      enabled: boolean;
      providerCredentialId?: number | null;
      modelName?: string | null;
      maxAttempts?: number | null;
      timeoutSeconds?: number | null;
    }
  ) => Promise<void>;
}) {
  const [selectedPolicyKey, setSelectedPolicyKey] = useState(policies[0]?.policyKey ?? "");
  const selectedPolicy = policies.find((policy) => policy.policyKey === selectedPolicyKey) ?? policies[0] ?? null;

  useEffect(() => {
    if (policies.length === 0) {
      if (selectedPolicyKey) {
        setSelectedPolicyKey("");
      }
      return;
    }
    if (!policies.some((policy) => policy.policyKey === selectedPolicyKey)) {
      setSelectedPolicyKey(policies[0].policyKey);
    }
  }, [policies, selectedPolicyKey]);

  if (policies.length === 0) {
    return <EmptyState message="등록된 AI 하네스 실행 정책이 없습니다." />;
  }

  return (
    <div className="ai-harness-manager">
      <div className="ai-harness-list" role="listbox" aria-label="AI 하네스 실행 정책">
      {policies.map((policy) => (
        <button
          aria-selected={selectedPolicy?.policyKey === policy.policyKey}
          className={selectedPolicy?.policyKey === policy.policyKey ? "ai-harness-list-item active" : "ai-harness-list-item"}
          key={policy.policyKey}
          onClick={() => setSelectedPolicyKey(policy.policyKey)}
          role="option"
          type="button"
        >
          <span>
            <strong>{policy.displayName}</strong>
            <small>{displayLabel(policy.policyKey)}</small>
          </span>
          <em>{policy.providerCode ?? "제공자 미지정"} / {policy.effectiveModelName ?? "모델 미지정"}</em>
          <StatusBadge status={policy.effectiveEnabled ? "ACTIVE" : "WARN"} />
        </button>
      ))}
      </div>
      <div className="ai-harness-detail">
        {selectedPolicy ? (
          <AiHarnessPolicyForm
            busy={busy}
            policy={selectedPolicy}
            providers={providers}
            onSubmit={onSubmit}
          />
        ) : (
          <EmptyState message="선택된 AI 하네스 실행 정책이 없습니다." />
        )}
      </div>
    </div>
  );
}

function AiHarnessPolicyForm({
  busy,
  policy,
  providers,
  onSubmit
}: {
  busy: boolean;
  policy: AiHarnessPolicy;
  providers: AiProviderCredential[];
  onSubmit: (
    policyKey: string,
    body: {
      enabled: boolean;
      providerCredentialId?: number | null;
      modelName?: string | null;
      maxAttempts?: number | null;
      timeoutSeconds?: number | null;
    }
  ) => Promise<void>;
}) {
  const [enabled, setEnabled] = useState(policy.enabled);
  const [providerId, setProviderId] = useState<number | null>(policy.providerCredentialId ?? null);
  const [modelName, setModelName] = useState(policy.modelName ?? policy.effectiveModelName ?? "");
  const [maxAttempts, setMaxAttempts] = useState(String(policy.maxAttempts ?? 2));
  const [timeoutSeconds, setTimeoutSeconds] = useState(String(policy.timeoutSeconds ?? 90));
  const [maxOutputTokens, setMaxOutputTokens] = useState(String(policy.maxOutputTokens ?? 1200));
  const [budgetEnabled, setBudgetEnabled] = useState(policy.budgetEnforcementEnabled);
  const [monthlyBudgetAmount, setMonthlyBudgetAmount] = useState(policy.monthlyBudgetAmount == null ? "" : String(policy.monthlyBudgetAmount));
  const [budgetCurrency, setBudgetCurrency] = useState(policy.budgetCurrency ?? "USD");
  const [dailyCallLimit, setDailyCallLimit] = useState(String(policy.dailyCallLimit ?? 30));
  const [monthlyTokenLimit, setMonthlyTokenLimit] = useState(String(policy.monthlyTokenLimit ?? 500000));
  const selectedProvider = providers.find((provider) => provider.id === providerId) ?? null;

  useEffect(() => {
    setEnabled(policy.enabled);
    setProviderId(policy.providerCredentialId ?? null);
    setModelName(policy.modelName ?? policy.effectiveModelName ?? "");
    setMaxAttempts(String(policy.maxAttempts ?? 2));
    setTimeoutSeconds(String(policy.timeoutSeconds ?? 90));
    setMaxOutputTokens(String(policy.maxOutputTokens ?? 1200));
    setBudgetEnabled(policy.budgetEnforcementEnabled);
    setMonthlyBudgetAmount(policy.monthlyBudgetAmount == null ? "" : String(policy.monthlyBudgetAmount));
    setBudgetCurrency(policy.budgetCurrency ?? "USD");
    setDailyCallLimit(String(policy.dailyCallLimit ?? 30));
    setMonthlyTokenLimit(String(policy.monthlyTokenLimit ?? 500000));
  }, [policy.policyKey, policy.policyVersion]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    await onSubmit(policy.policyKey, {
      enabled,
      providerCredentialId: providerId,
      modelName: normalizeFormValue(modelName),
      maxAttempts: optionalNumber(maxAttempts),
      timeoutSeconds: optionalNumber(timeoutSeconds),
      maxOutputTokens: optionalNumber(maxOutputTokens),
      budgetEnforcementEnabled: budgetEnabled,
      monthlyBudgetAmount: optionalNumber(monthlyBudgetAmount),
      budgetCurrency: normalizeFormValue(budgetCurrency),
      dailyCallLimit: optionalNumber(dailyCallLimit),
      monthlyTokenLimit: optionalNumber(monthlyTokenLimit)
    });
  }

  return (
    <form className="ai-policy-form ai-harness-policy-form" onSubmit={submit}>
      <div className="ai-harness-policy-heading">
        <div>
          <strong>{policy.displayName}</strong>
          <span>{displayLabel(policy.policyKey)}</span>
          {policy.description ? <small>{policy.description}</small> : null}
        </div>
        <div className="ai-harness-policy-state">
          <StatusBadge status={policy.effectiveEnabled ? "ACTIVE" : "WARN"} />
          <span>{policy.effectiveMessage ?? "-"}</span>
        </div>
      </div>
      <label className="toggle-row">
        <input type="checkbox" checked={enabled} onChange={(event) => setEnabled(event.target.checked)} />
        이 AI 하네스 실행 허용
      </label>
      <label>
        제공자
        <select
          value={providerId ?? ""}
          onChange={(event) => {
            const nextProviderId = event.target.value ? Number(event.target.value) : null;
            const provider = providers.find((item) => item.id === nextProviderId);
            setProviderId(nextProviderId);
            if (provider?.defaultModel && !modelName.trim()) {
              setModelName(provider.defaultModel);
            }
          }}
        >
          <option value="">선택 안 함</option>
          {providers.map((provider) => (
            <option key={provider.id} value={provider.id}>
              {provider.displayName} / {provider.providerCode} / {displayLabel(provider.status)}
            </option>
          ))}
        </select>
      </label>
      <label>
        모델
        <input
          placeholder={selectedProvider?.defaultModel ?? "예: gpt-4.1-mini"}
          value={modelName}
          onChange={(event) => setModelName(event.target.value)}
        />
      </label>
      <label>
        재시도
        <input min={1} type="number" value={maxAttempts} onChange={(event) => setMaxAttempts(event.target.value)} />
      </label>
      <label>
        Timeout 초
        <input min={10} type="number" value={timeoutSeconds} onChange={(event) => setTimeoutSeconds(event.target.value)} />
      </label>
      <label>
        출력 토큰 상한
        <input min={1} step={1} type="number" value={maxOutputTokens} onChange={(event) => setMaxOutputTokens(event.target.value)} />
      </label>
      <label className="toggle-row">
        <input type="checkbox" checked={budgetEnabled} onChange={(event) => setBudgetEnabled(event.target.checked)} />
        하네스 예산 한도 적용
      </label>
      <label>
        월간 예산
        <input min="0" step="0.00000001" type="number" value={monthlyBudgetAmount} onChange={(event) => setMonthlyBudgetAmount(event.target.value)} />
      </label>
      <label>
        통화
        <input value={budgetCurrency} onChange={(event) => setBudgetCurrency(event.target.value)} />
      </label>
      <label>
        일일 호출 한도
        <input min={0} step={1} type="number" value={dailyCallLimit} onChange={(event) => setDailyCallLimit(event.target.value)} />
      </label>
      <label>
        월간 토큰 한도
        <input min={0} step={1} type="number" value={monthlyTokenLimit} onChange={(event) => setMonthlyTokenLimit(event.target.value)} />
      </label>
      <div className="policy-note">
        실제 실행 모델: {policy.providerCode ?? selectedProvider?.providerCode ?? "미지정"} / {modelName || policy.effectiveModelName || "미지정"}
      </div>
      <button className="button primary" disabled={busy} type="submit">
        {busy ? <Loader2 className="spin" size={16} /> : <CheckCircle2 size={16} />}
        하네스 정책 저장
      </button>
    </form>
  );
}

function OfficeAiPolicyForm({
  busy,
  policies,
  providers,
  onSubmit
}: {
  busy: boolean;
  policies: OfficeAiPolicy[];
  providers: AiProviderCredential[];
  onSubmit: (
    officeId: number,
    body: {
      aiEnabled: boolean;
      documentReviewAiEnabled: boolean;
      documentGenerationAiEnabled: boolean;
      preferredProviderCredentialId?: number | null;
      credentialDeliveryMode: string;
      budgetEnforcementEnabled?: boolean;
      monthlyBudgetAmount?: number | null;
      budgetCurrency?: string | null;
      dailyCallLimit?: number | null;
      monthlyTokenLimit?: number | null;
      maxOutputTokens?: number | null;
      perUserDailyCallLimit?: number | null;
      perUserMonthlyTokenLimit?: number | null;
    }
  ) => Promise<void>;
}) {
  const activeProviders = providers.filter((provider) => provider.status === "ACTIVE");
  const [officeId, setOfficeId] = useState<number | null>(policies[0]?.officeId ?? null);
  const selectedPolicy = policies.find((policy) => policy.officeId === officeId) ?? policies[0] ?? null;
  const [aiEnabled, setAiEnabled] = useState(false);
  const [reviewEnabled, setReviewEnabled] = useState(false);
  const [generationEnabled, setGenerationEnabled] = useState(false);
  const [providerId, setProviderId] = useState<number | null>(null);
  const [budgetEnabled, setBudgetEnabled] = useState(false);
  const [monthlyBudgetAmount, setMonthlyBudgetAmount] = useState("");
  const [budgetCurrency, setBudgetCurrency] = useState("USD");
  const [dailyCallLimit, setDailyCallLimit] = useState("");
  const [monthlyTokenLimit, setMonthlyTokenLimit] = useState("");

  useEffect(() => {
    if (!selectedPolicy) {
      return;
    }
    setOfficeId(selectedPolicy.officeId);
    setAiEnabled(selectedPolicy.aiEnabled);
    setReviewEnabled(selectedPolicy.documentReviewAiEnabled);
    setGenerationEnabled(selectedPolicy.documentGenerationAiEnabled);
    setProviderId(selectedPolicy.preferredProviderCredentialId ?? null);
    setBudgetEnabled(selectedPolicy.budgetEnforcementEnabled);
    setMonthlyBudgetAmount(selectedPolicy.monthlyBudgetAmount == null ? "" : String(selectedPolicy.monthlyBudgetAmount));
    setBudgetCurrency(selectedPolicy.budgetCurrency ?? "USD");
    setDailyCallLimit(selectedPolicy.dailyCallLimit == null ? "" : String(selectedPolicy.dailyCallLimit));
    setMonthlyTokenLimit(selectedPolicy.monthlyTokenLimit == null ? "" : String(selectedPolicy.monthlyTokenLimit));
  }, [selectedPolicy?.officeId]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!selectedPolicy) {
      return;
    }
    await onSubmit(selectedPolicy.officeId, {
      aiEnabled,
      documentReviewAiEnabled: reviewEnabled,
      documentGenerationAiEnabled: generationEnabled,
      preferredProviderCredentialId: providerId,
      credentialDeliveryMode: "PROXY_ONLY",
      budgetEnforcementEnabled: budgetEnabled,
      monthlyBudgetAmount: optionalNumber(monthlyBudgetAmount),
      budgetCurrency: normalizeFormValue(budgetCurrency),
      dailyCallLimit: optionalNumber(dailyCallLimit),
      monthlyTokenLimit: optionalNumber(monthlyTokenLimit),
      maxOutputTokens: selectedPolicy.maxOutputTokens,
      perUserDailyCallLimit: selectedPolicy.perUserDailyCallLimit,
      perUserMonthlyTokenLimit: selectedPolicy.perUserMonthlyTokenLimit
    });
  }

  if (policies.length === 0) {
    return <EmptyState message="사무소가 없습니다." />;
  }

  return (
    <form className="ai-policy-form" onSubmit={submit}>
      <label>
        사무소
        <select value={officeId ?? ""} onChange={(event) => setOfficeId(Number(event.target.value))}>
          {policies.map((policy) => (
            <option key={policy.officeId} value={policy.officeId}>
              {policy.officeName} / {policy.officeCode}
            </option>
          ))}
        </select>
      </label>
      <label>
        제공자
        <select value={providerId ?? ""} onChange={(event) => setProviderId(event.target.value ? Number(event.target.value) : null)}>
          <option value="">선택 안 함</option>
          {activeProviders.map((provider) => (
            <option key={provider.id} value={provider.id}>
              {provider.displayName} / {displayLabel(provider.providerType)}
            </option>
          ))}
        </select>
      </label>
      <label className="toggle-row">
        <input type="checkbox" checked={aiEnabled} onChange={(event) => setAiEnabled(event.target.checked)} />
        AI 사용
      </label>
      <label className="toggle-row">
        <input type="checkbox" checked={reviewEnabled} onChange={(event) => setReviewEnabled(event.target.checked)} />
        문서 검토 AI
      </label>
      <label className="toggle-row">
        <input type="checkbox" checked={generationEnabled} onChange={(event) => setGenerationEnabled(event.target.checked)} />
        문서 생성 AI
      </label>
      <div className="policy-note">전달 방식은 MVP에서 PROXY_ONLY로 고정됩니다. Agent에는 제공자 API 키를 내려보내지 않습니다.</div>
      <button className="button primary" disabled={busy} type="submit">
        {busy ? <Loader2 className="spin" size={16} /> : <ShieldCheck size={16} />}
        정책 저장
      </button>
    </form>
  );
}

function sumCounts(counts?: Record<string, number>) {
  return Object.values(counts ?? {}).reduce((sum, count) => sum + count, 0);
}

function isFakeAiProvider(providerCode?: string | null) {
  return Boolean(providerCode?.startsWith("fake-"));
}

function aiProviderModeLabel(providerCode?: string | null) {
  if (isFakeAiProvider(providerCode)) {
    return "개발용 Fake AI";
  }
  if (providerCode?.startsWith("spring-ai-")) {
    return "Spring AI adapter";
  }
  return "실제 Provider";
}

function asPlainObject(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    return {};
  }
  return value as Record<string, unknown>;
}

function asObjectArray(value: unknown): Record<string, unknown>[] {
  if (!Array.isArray(value)) {
    return [];
  }
  return value
    .filter((item) => item && typeof item === "object" && !Array.isArray(item))
    .map((item) => item as Record<string, unknown>);
}

function stringValue(value: unknown) {
  if (value == null) {
    return "";
  }
  if (typeof value === "string") {
    return value;
  }
  if (typeof value === "number" || typeof value === "boolean") {
    return String(value);
  }
  return "";
}

function numberValue(value: unknown, fallback = 0) {
  return typeof value === "number" && Number.isFinite(value) ? value : fallback;
}

function tokenUsageLabel(log: AiModelCallLog) {
  if (log.inputTokens == null && log.outputTokens == null) {
    return "-";
  }
  return `${log.inputTokens ?? 0} / ${log.outputTokens ?? 0}`;
}

function tokenUsageSummary(observation: AiObservation) {
  if (observation.inputTokens == null && observation.outputTokens == null) {
    return "토큰 -";
  }
  return `토큰 ${observation.inputTokens ?? 0} / ${observation.outputTokens ?? 0}`;
}

function costLabel(log: AiModelCallLog) {
  if (log.estimatedTotalCost == null || !log.costCurrency) {
    return "-";
  }
  return `${log.costCurrency} ${formatMoney(log.estimatedTotalCost)}`;
}

function findingCategory(finding: PlatformReportPreflightFinding) {
  return finding.attributes?.category ?? finding.code;
}

function isOpenBlockingAiFinding(finding: PlatformReportPreflightFinding) {
  return finding.resolutionStatus === "OPEN" && ["HIGH", "CRITICAL"].includes(finding.severity);
}

function formatMoney(value: number) {
  return value.toLocaleString(undefined, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 8
  });
}

function EventList({ events }: { events: OperationEvent[] }) {
  if (events.length === 0) {
    return <EmptyState message="최근 이벤트가 없습니다." />;
  }
  return (
    <div className="mini-list">
      {events.map((event) => (
        <div key={event.id}>
          <StatusIcon status={event.severity} />
          <div>
            <strong>{event.eventType}</strong>
            <span>{event.message}</span>
          </div>
          <time>{formatDate(event.createdAt)}</time>
        </div>
      ))}
    </div>
  );
}

function InlineAlert({ message }: { message: string }) {
  return (
    <div className="inline-alert">
      <AlertTriangle size={16} />
      <span>{message}</span>
    </div>
  );
}

function InlineNotice({ message }: { message: string }) {
  return (
    <div className="inline-notice">
      <CheckCircle2 size={16} />
      <span>{message}</span>
    </div>
  );
}

function EmptyState({ message }: { message: string }) {
  return (
    <div className="empty-state">
      <HardDrive size={22} />
      <span>{message}</span>
    </div>
  );
}

function FullScreenCenter({ children }: { children: ReactNode }) {
  return <div className="fullscreen-center">{children}</div>;
}

function viewTitle(view: ViewKey) {
  if (isAiView(view)) {
    const item = aiNavItems.find((candidate) => candidate.key === view);
    return item ? `AI 관리 / ${item.label}` : "AI 관리";
  }
  if (isPlatformView(view)) {
    const item = platformNavItems.find((candidate) => candidate.key === view);
    return item ? `플랫폼 관리 / ${item.label}` : "플랫폼 관리";
  }
  return navItems.find((item) => item.key === view)?.label ?? "대시보드";
}

const templatePolicyLabels: Record<string, string> = {
  BUNDLED_OFFICIAL_RENDERER: "번들 공식 렌더러",
  COPY_AND_OVERRIDE: "복사 후 오버라이드",
  DOCX_TEMPLATE_BINDING: "DOCX 템플릿 바인딩",
  OFFICE_EDITABLE: "사무소 편집 가능",
  OFFICE_INTERNAL: "사무소 내부용",
  OFFICIAL_SUBMISSION: "공식 제출용"
};

function displayLabel(value?: string | null) {
  if (!value) {
    return "-";
  }
  return statusLabels[value] ?? codeLabels[value] ?? templatePolicyLabels[value] ?? value;
}

function userLabel(users: PlatformUserOps[], userId?: number | null) {
  if (!userId) {
    return "-";
  }
  const user = users.find((candidate) => candidate.id === userId);
  return user ? `${user.name} / ${user.email} / #${user.id}` : `#${userId}`;
}

function officeLabel(offices: PlatformOfficeOps[], officeId?: number | null) {
  if (!officeId) {
    return "-";
  }
  const office = offices.find((candidate) => candidate.id === officeId);
  return office ? `${office.displayName} / ${office.officeCode} / #${office.id}` : `#${officeId}`;
}

function projectAssignmentRoleLabel(role: ProjectAssignmentRole) {
  const labels: Record<ProjectAssignmentRole, string> = {
    MANAGER: "총괄/프로젝트 관리자",
    REPORT_WRITER: "작성자/건축사보",
    VIEWER: "조회자"
  };
  return labels[role] ?? role;
}

function projectFormFromProject(project: Project): ProjectFormRequest {
  return {
    name: project.name,
    address: project.address ?? "",
    buildingType: project.buildingType ?? "CONSTRUCTION_SUPERVISION",
    startDate: project.startDate ?? "",
    endDate: project.endDate ?? ""
  };
}

function normalizeProjectForm(form: ProjectFormRequest): ProjectFormRequest {
  return {
    name: normalizeFormValue(form.name) ?? "",
    address: normalizeFormValue(form.address ?? ""),
    buildingType: normalizeFormValue(form.buildingType ?? ""),
    startDate: normalizeFormValue(form.startDate ?? ""),
    endDate: normalizeFormValue(form.endDate ?? "")
  };
}

function statusTone(status: string) {
  if (["ONLINE", "ACTIVE", "RUNNING", "COMPLETED", "GENERATED", "UPLOADED", "PICKED_UP", "OWNER", "ADMIN", "INFO", "PUBLISHED", "ACCEPTED", "RESOLVED", "APPROVED", "APPLIED", "PASS", "OK"].includes(status)) {
    return "green";
  }
  if (["CREATED", "READY", "PAUSED", "REQUESTED", "PENDING", "PENDING_UPLOAD", "DELIVERED", "ACKED", "SENDING", "GENERATING", "WARN", "DRAFT", "OPEN", "MEDIUM", "LOW", "NEEDS_HUMAN_REVIEW"].includes(status)) {
    return "amber";
  }
  if (["FAILED", "EXPIRED", "CANCELLED", "OFFLINE", "ERROR", "SUSPENDED", "LEFT", "DISABLED", "HIGH", "CRITICAL", "REJECTED", "BLOCKED"].includes(status)) {
    return "red";
  }
  return "slate";
}

function filterByStatus<T extends { status: string }>(items: T[], status: string) {
  return status === "ALL" ? items : items.filter((item) => item.status === status);
}

function filterPhotos(items: Photo[], status: string, pickupStatus: string) {
  return items.filter((photo) => {
    const statusMatches = status === "ALL" || photo.status === status;
    const pickupMatches = pickupStatus === "ALL" || photo.originalPickupStatus === pickupStatus;
    return statusMatches && pickupMatches;
  });
}

function formatDate(value?: string | null) {
  if (!value) {
    return "-";
  }
  return new Intl.DateTimeFormat("ko-KR", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit"
  }).format(new Date(value));
}

function formatBytes(value?: number | null) {
  if (!value) {
    return "-";
  }
  if (value < 1024) {
    return `${value} B`;
  }
  if (value < 1024 * 1024) {
    return `${(value / 1024).toFixed(1)} KB`;
  }
  return `${(value / 1024 / 1024).toFixed(1)} MB`;
}

function formatPercent(value?: number | null) {
  if (value === undefined || value === null || Number.isNaN(value)) {
    return "-";
  }
  return `${value >= 10 ? value.toFixed(0) : value.toFixed(1)}%`;
}

function runtimeMetricTone(value?: number | null, warning?: boolean): "green" | "blue" | "amber" | "red" | "slate" {
  if (warning) {
    return "red";
  }
  if (value === undefined || value === null || Number.isNaN(value)) {
    return "slate";
  }
  if (value >= 80) {
    return "amber";
  }
  return "green";
}

function serverHealthTone(status?: string | null): "green" | "blue" | "amber" | "red" | "slate" {
  if (status === "WARN") {
    return "red";
  }
  if (status === "OK") {
    return "green";
  }
  return "slate";
}

function normalizeFormValue(value: string) {
  const trimmed = value.trim();
  return trimmed.length === 0 ? null : trimmed;
}

function optionalNumber(value: string) {
  const normalized = normalizeFormValue(value);
  return normalized === null ? null : Number(normalized);
}

function parseJsonObject(value: string, label: string): Record<string, unknown> {
  const parsed = JSON.parse(value) as unknown;
  if (parsed === null || Array.isArray(parsed) || typeof parsed !== "object") {
    throw new Error(`${label}는 JSON 객체여야 합니다.`);
  }
  return parsed as Record<string, unknown>;
}

function filenameFromStorageRef(storageRef?: string | null) {
  if (!storageRef) {
    return null;
  }
  const normalized = storageRef.replace(/\\/g, "/");
  return normalized.split("/").filter(Boolean).at(-1) ?? null;
}

function downloadBlob(blob: Blob, filename: string) {
  const url = window.URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  window.URL.revokeObjectURL(url);
}

function invitationTokenFromPath() {
  const match = window.location.pathname.match(/^\/office-invitations\/([^/]+)$/);
  return match ? decodeURIComponent(match[1]) : null;
}

function invitationUrl(invitation: OfficeInvitation) {
  if (!invitation.acceptToken) {
    return null;
  }
  const clientBaseUrl =
    import.meta.env.VITE_CLIENT_APP_URL ??
    window.location.origin
      .replace("://localhost:5174", "://localhost:5173")
      .replace("://127.0.0.1:5174", "://127.0.0.1:5173");
  return `${clientBaseUrl}/invitations/${encodeURIComponent(invitation.acceptToken)}`;
}
