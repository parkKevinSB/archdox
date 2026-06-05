import {
  Activity,
  AlertTriangle,
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
  archiveProject,
  approvePlatformWorkerApproval,
  cancelOfficeInvitation,
  clearPlatformAiObservations,
  configureTokenRefresh,
  createPlatformEngineApiKey,
  createPlatformAiPricingRule,
  createPlatformAiProvider,
  createDocumentTemplate,
  createDocumentTemplateRevision,
  createOfficeInvitation,
  createProject,
  deactivateOfficeMember,
  deleteProject,
  diagnosePlatformOpsIncident,
  detectPlatformStuckHealth,
  disablePlatformAiPricingRule,
  downloadDocumentTemplateRevisionContent,
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
  getPlatformAiHarnessTraces,
  getPlatformAiObservationMode,
  getPlatformAiObservations,
  getPlatformAiPreflightFindings,
  getPlatformAiProviders,
  getPlatformAiPricingRules,
  getPlatformAiUsageSummary,
  getPlatformCommands,
  getPlatformDeliveries,
  getPlatformDocumentJobs,
  getPlatformEvents,
  getPlatformEngineApiKeys,
  getPlatformEngineUsageEvents,
  getPlatformEngineUsageSummary,
  getPlatformLegalChangeDigests,
  getPlatformLegalChangeSets,
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
  revokePlatformEngineApiKey,
  signup,
  startPlatformLegalFakeSync,
  startPlatformLegalOpenDataSync,
  testPlatformAiProvider,
  updateProject,
  updatePlatformAiObservationMode,
  updatePlatformAiProvider,
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
  AiModelCallLog,
  AiHarnessTraceEvent,
  AiModelPricingRule,
  AiObservation,
  AiObservationMode,
  AiProviderConnectionTestResult,
  AiProviderCredential,
  AiUsageSummary,
  ConfigDefinition,
  CreateEngineApiKeyResponse,
  DocumentDelivery,
  DocumentJob,
  DocumentTemplateRevision,
  EngineApiKey,
  EngineApiUsageEvent,
  EngineApiUsageSummary,
  LegalChangeDigest,
  LegalChangeSet,
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
  | "platform-events"
  | "ai-overview"
  | "ai-providers"
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
  | "platform-events"
>;
type AiViewKey = Extract<ViewKey, "ai-overview" | "ai-providers" | "ai-policies" | "ai-observer">;
type AiObserverTabKey = "summary" | "raw" | "findings" | "traces" | "calls";

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
  legalOpenApiStatus: LegalOpenApiStatus | null;
  engineApiKeys: EngineApiKey[];
  engineApiUsageSummary: EngineApiUsageSummary | null;
  engineApiUsageEvents: EngineApiUsageEvent[];
  workerGovernance: WorkerGovernanceSummary | null;
  workerApprovals: WorkerApprovalRequest[];
  aiProviders: AiProviderCredential[];
  officeAiPolicies: OfficeAiPolicy[];
  aiCallLogs: AiModelCallLog[];
  aiHarnessTraces: AiHarnessTraceEvent[];
  aiObservationMode: AiObservationMode | null;
  aiObservations: AiObservation[];
  aiPreflightFindings: PlatformReportPreflightFinding[];
  aiPricingRules: AiModelPricingRule[];
  aiUsageSummary: AiUsageSummary | null;
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
  legalOpenApiStatus: null,
  engineApiKeys: [],
  engineApiUsageSummary: null,
  engineApiUsageEvents: [],
  workerGovernance: null,
  workerApprovals: [],
  aiProviders: [],
  officeAiPolicies: [],
  aiCallLogs: [],
  aiHarnessTraces: [],
  aiObservationMode: null,
  aiObservations: [],
  aiPreflightFindings: [],
  aiPricingRules: [],
  aiUsageSummary: null
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
  { key: "platform-worker-governance", label: "Worker 통제" },
  { key: "platform-worker-approvals", label: "Worker 승인" }
];

const aiNavItems: Array<{ key: AiViewKey; label: string }> = [
  { key: "ai-overview", label: "개요" },
  { key: "ai-providers", label: "Provider/요금" },
  { key: "ai-policies", label: "사무소 정책" },
  { key: "ai-observer", label: "관측/검토" }
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
  GENERATING: "생성 중",
  SUCCEEDED: "성공",
  HIGH: "높음",
  INFO: "정보",
  LEFT: "탈퇴",
  LOW: "낮음",
  MEDIUM: "보통",
  MEMBER: "멤버",
  NEEDS_ATTENTION: "확인 필요",
  NOT_REQUIRED: "불필요",
  OFF: "꺼짐",
  OFFLINE: "오프라인",
  ON: "켜짐",
  ONLINE: "온라인",
  OPEN: "미처리",
  OWNER: "소유자",
  PASSED: "통과",
  PENDING: "대기",
  PENDING_UPLOAD: "업로드 대기",
  PICKED_UP: "회수 완료",
  PUBLISHED: "게시됨",
  REQUESTED: "요청됨",
  RESOLVED: "해결",
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
  AI: "AI 검토",
  AI_REVIEW: "AI 검토",
  API_LOCAL: "API 로컬 저장소",
  ARCHDOX_AGENT: "ArchDox Agent",
  CLOUD_MANAGED: "클라우드 관리형",
  CLOUD_OFFICE: "클라우드 사무소",
  CUSTOM_HTTP: "사용자 HTTP",
  CONSTRUCTION_DAILY_SUPERVISION_LOG: "공사감리일지",
  DETERMINISTIC: "코드 검증",
  DOCUMENT_GENERATION: "문서 생성 AI",
  DOCUMENT_REVIEW: "문서 검토 AI",
  DOCUMENT_DELIVERY_REQUEST: "문서 전달 요청",
  DOCUMENT_JOB: "문서 생성 작업",
  DOCUMENT_RENDER: "문서 생성",
  DOWNLOAD: "다운로드",
  IN_FLIGHT: "진행 중",
  LOCAL_OFFICE: "사무소 로컬",
  MANUAL_DIAGNOSIS: "수동 진단",
  PERSONAL: "개인",
  PHOTO_PICKUP: "사진 회수",
  PLATFORM: "플랫폼",
  PROXY_ONLY: "API 서버 경유",
  REPORT_PREFLIGHT_REVIEW: "생성 전 검토",
  OPS_AI_DIAGNOSIS: "운영 AI 진단",
  TEMPLATE: "템플릿",
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
        const [legalOpenApiStatus, legalSyncRuns, legalChangeSets, legalChangeDigests] = await Promise.all([
          getPlatformLegalOpenApiStatus(token),
          getPlatformLegalSyncRuns(token, 50),
          getPlatformLegalChangeSets(token, 50),
          getPlatformLegalChangeDigests(token, 50)
        ]);
        Object.assign(next, { legalOpenApiStatus, legalSyncRuns, legalChangeSets, legalChangeDigests });
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
      } else if (view === "ai-overview") {
        const [aiProviders, officeAiPolicies, aiUsageSummary, aiCallLogs, aiPreflightFindings] = await Promise.all([
          getPlatformAiProviders(token),
          getPlatformOfficeAiPolicies(token, 100),
          getPlatformAiUsageSummary(token),
          getPlatformAiCallLogs(token, 100),
          getPlatformAiPreflightFindings(token, 100)
        ]);
        Object.assign(next, { aiProviders, officeAiPolicies, aiUsageSummary, aiCallLogs, aiPreflightFindings });
      } else if (view === "ai-providers") {
        const [aiProviders, aiPricingRules, aiUsageSummary] = await Promise.all([
          getPlatformAiProviders(token),
          getPlatformAiPricingRules(token, 100),
          getPlatformAiUsageSummary(token)
        ]);
        Object.assign(next, { aiProviders, aiPricingRules, aiUsageSummary });
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

  async function runPlatformLegalFakeSync() {
    if (!auth || !platformAdmin) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      await startPlatformLegalFakeSync(auth.accessToken);
      await refreshPlatform();
    } catch (err) {
      setError(err instanceof Error ? err.message : "법령 동기화를 시작하지 못했습니다.");
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
                <optgroup label="AI 운영">
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
                  AI 운영
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
              data={platformData}
              platformAdmin={platformAdmin}
              loading={loading}
              onRefresh={refreshPlatform}
              lastDetection={lastPlatformDetection}
              onDetectStuck={runPlatformDetection}
              onLegalFakeSync={runPlatformLegalFakeSync}
              onLegalOpenDataSync={runPlatformLegalOpenDataSync}
              onLegalDigestRefresh={runPlatformLegalDigestRefresh}
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
              policies={platformData.officeAiPolicies}
              usageSummary={platformData.aiUsageSummary}
              onCreatePricingRule={handleCreateAiPricingRule}
              onDisablePricingRule={handleDisableAiPricingRule}
              onCreateProvider={handleCreateAiProvider}
              onUpdateProvider={handleUpdateAiProvider}
              onPublishProvider={handlePublishAiProvider}
              onTestProvider={handleTestAiProvider}
              onUpdateObservationMode={handleUpdateAiObservationMode}
              onClearObservations={handleClearAiObservations}
              onRefresh={refreshPlatform}
              onSaveOfficePolicy={handleSaveOfficeAiPolicy}
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

function PlatformView({
  view,
  data,
  platformAdmin,
  loading,
  onRefresh,
  lastDetection,
  onDetectStuck,
  onLegalFakeSync,
  onLegalOpenDataSync,
  onLegalDigestRefresh,
  issuedEngineApiKey,
  onCreateEngineApiKey,
  onRevokeEngineApiKey,
  onDismissIssuedEngineApiKey,
  onDiagnoseIncident,
  onApproveWorkerApproval,
  onRejectWorkerApproval
}: {
  view: PlatformViewKey;
  data: PlatformOpsData;
  platformAdmin: PlatformAdminMe | null;
  loading: boolean;
  onRefresh: () => void;
  lastDetection: PlatformHealthDetection | null;
  onDetectStuck: () => void;
  onLegalFakeSync: () => void;
  onLegalOpenDataSync: () => void;
  onLegalDigestRefresh: () => void;
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
        <div className="dashboard-grid">
          <Panel
            title="법령 동기화"
            icon={<ShieldCheck size={18} />}
            action={<>
              <button className="button primary" disabled={loading} onClick={onLegalOpenDataSync} type="button">
                {loading ? <Loader2 className="spin" size={16} /> : <RefreshCcw size={16} />}
                실제 동기화
              </button>
              <button className="button" disabled={loading} onClick={onLegalFakeSync} type="button">
                {loading ? <Loader2 className="spin" size={16} /> : <RefreshCcw size={16} />}
                Fake 동기화
              </button>
              <button className="button" disabled={loading} onClick={onLegalDigestRefresh} type="button">
                {loading ? <Loader2 className="spin" size={16} /> : <RefreshCcw size={16} />}
                Digest 재생성
              </button>
              </>}
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

          <Panel title="사용자용 법령 변경사항" icon={<FileText size={18} />} count={data.legalChangeDigests.length}>
            <Table
              columns={["제목", "상태", "출처", "시행일", "게시", "요약"]}
              empty="게시된 법령 변경사항이 없습니다."
              rows={data.legalChangeDigests.slice(0, 20).map((digest) => [
                <CellTitle key="digest" title={digest.title} subtitle={`Digest #${digest.id} / Change Set #${digest.changeSetId}`} />,
                <StatusBadge key="status" status={digest.status} />,
                displayLabel(digest.source),
                digest.effectiveDate ?? "-",
                formatDate(digest.publishedAt),
                digest.summary
              ])}
            />
          </Panel>

          <Panel title="법령 변경 원천 기록" icon={<Activity size={18} />} count={data.legalChangeSets.length}>
            <Table
              columns={["Change Set", "상태", "Act", "시행일", "감지", "요약"]}
              empty="법령 변경 원천 기록이 없습니다."
              rows={data.legalChangeSets.slice(0, 20).map((changeSet) => [
                <CellTitle key="change-set" title={`Change Set #${changeSet.id}`} subtitle={`run #${changeSet.syncRunId ?? "-"} / version #${changeSet.newVersionId}`} />,
                <StatusBadge key="status" status={changeSet.status} />,
                `#${changeSet.actId}`,
                changeSet.effectiveDate ?? "-",
                formatDate(changeSet.detectedAt),
                changeSet.summary
              ])}
            />
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

      {showOverview ? (
        <div className="metric-grid">
        <MetricCard icon={<Users size={20} />} label="사용자" value={summary?.users ?? 0} detail="전체 계정" tone="blue" />
        <MetricCard icon={<HardDrive size={20} />} label="사무소" value={summary?.offices ?? 0} detail="전체 테넌트" tone="slate" />
        <MetricCard icon={<Server size={20} />} label="에이전트" value={sumCounts(summary?.agents)} detail={`${summary?.agents.ONLINE ?? 0} 온라인`} tone="green" />
        <MetricCard icon={<Wifi size={20} />} label="세션" value={summary?.activeAgentSessions ?? 0} detail="활성 WebSocket" tone="blue" />
        <MetricCard icon={<Command size={20} />} label="명령" value={summary?.agentCommands.IN_FLIGHT ?? 0} detail="진행 중" tone="amber" />
        <MetricCard icon={<AlertTriangle size={20} />} label="확인 필요" value={attention} detail="실패/멈춤 후보" tone={attention > 0 ? "red" : "green"} />
        </div>
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
          <MetricCard icon={<AlertTriangle size={20} />} label="Failure" value={`${summary.failureRate}%`} detail={`${summary.actionFailed}건 실패`} tone={summary.actionFailed > 0 ? "red" : "green"} />
        </div>
        <InlineNotice message={summary.dataPolicy} />
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
  const activeCount = keys.filter((key) => key.status === "ACTIVE").length;
  const revokedCount = keys.filter((key) => key.status === "REVOKED").length;
  const mcpEvents = usageEvents.filter((event) => metadataText(event.metadata, "source") === "MCP");
  const failedMcpEvents = mcpEvents.filter((event) => event.status !== "SUCCEEDED").length;

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
          <MetricCard icon={<Command size={20} />} label="MCP calls" value={mcpEvents.length} detail="recent 100 events" tone="slate" />
          <MetricCard icon={<AlertTriangle size={20} />} label="MCP failed" value={failedMcpEvents} detail="FAILED or DENIED" tone={failedMcpEvents > 0 ? "red" : "green"} />
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

      <Panel title="Recent MCP calls" icon={<Command size={18} />} count={mcpEvents.length}>
        <Table
          columns={["Time", "Status", "Tool", "Capability", "Key", "Trace / Error"]}
          empty="Recent MCP call logs are empty."
          rows={mcpEvents.slice(0, 30).map((event) => [
            formatDate(event.createdAt),
            <StatusBadge key="status" status={event.status} />,
            metadataText(event.metadata, "toolName") || event.operation,
            event.capability,
            <CellTitle key="key" title={event.keyId} subtitle={event.officeId ? officeLabel(offices, event.officeId) : "-"} />,
            <CellTitle
              key="trace"
              title={metadataText(event.metadata, "correlationId") || "-"}
              subtitle={metadataText(event.metadata, "errorCode") || metadataText(event.metadata, "accessMode") || "-"}
            />
          ])}
        />
      </Panel>
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
  policies,
  usageSummary,
  onCreatePricingRule,
  onDisablePricingRule,
  onCreateProvider,
  onUpdateProvider,
  onPublishProvider,
  onTestProvider,
  onUpdateObservationMode,
  onClearObservations,
  onRefresh,
  providerTestResults,
  onSaveOfficePolicy
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
  policies: OfficeAiPolicy[];
  usageSummary: AiUsageSummary | null;
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
    }
  ) => Promise<void>;
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
  const showOverview = view === "ai-overview";
  const showProviders = view === "ai-providers";
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
                  : "현재는 치명적인 AI 운영 이슈가 보이지 않습니다."}
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
      <div className="policy-note">
        {provider.providerCode} / #{provider.id} 수정 중입니다. API 키를 비워두면 기존 키를 유지합니다.
      </div>
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
      monthlyTokenLimit: optionalNumber(monthlyTokenLimit)
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
      <div className="policy-note">월 예산을 적용하려면 모델별 단가나 제공자 "*" 기본 단가가 활성 상태여야 합니다.</div>
      <button className="button primary" disabled={busy} type="submit">
        {busy ? <Loader2 className="spin" size={16} /> : <ShieldCheck size={16} />}
        예산 저장
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
      monthlyTokenLimit: optionalNumber(monthlyTokenLimit)
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
    return item ? `AI 운영 / ${item.label}` : "AI 운영";
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
  if (["ONLINE", "ACTIVE", "COMPLETED", "GENERATED", "UPLOADED", "PICKED_UP", "OWNER", "ADMIN", "INFO", "PUBLISHED", "ACCEPTED", "RESOLVED"].includes(status)) {
    return "green";
  }
  if (["REQUESTED", "PENDING", "PENDING_UPLOAD", "DELIVERED", "ACKED", "SENDING", "GENERATING", "WARN", "DRAFT", "OPEN", "MEDIUM", "LOW"].includes(status)) {
    return "amber";
  }
  if (["FAILED", "EXPIRED", "CANCELLED", "OFFLINE", "ERROR", "SUSPENDED", "LEFT", "DISABLED", "HIGH", "CRITICAL"].includes(status)) {
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
