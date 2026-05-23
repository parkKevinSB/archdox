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
import { FormEvent, ReactNode, useEffect, useMemo, useState } from "react";
import {
  acceptOfficeInvitation,
  addOfficeMember,
  cancelOfficeInvitation,
  createDocumentTemplate,
  createDocumentTemplateRevision,
  createOfficeInvitation,
  deactivateOfficeMember,
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
  getSummary,
  login,
  me,
  publishDocumentTemplateRevision,
  signup,
  updateOfficeMemberRole,
  updateOfficeConfigOverride,
  uploadDocumentTemplateRevisionContent
} from "./api";
import type {
  Agent,
  AgentCommand,
  AgentSession,
  ConfigDefinition,
  DocumentDelivery,
  DocumentJob,
  DocumentTemplateRevision,
  MeResponse,
  MembershipRole,
  Office,
  OfficeInvitation,
  OfficeMember,
  OfficeConfigOverride,
  OfficeOpsSummary,
  OperationEvent,
  Photo,
  TemplateFieldCatalog,
  TemplateFieldDefinition
} from "./types";

type ViewKey =
  | "dashboard"
  | "agents"
  | "commands"
  | "documents"
  | "members"
  | "templates"
  | "photos"
  | "deliveries"
  | "events";

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

const navItems: Array<{ key: ViewKey; label: string; icon: typeof LayoutDashboard }> = [
  { key: "dashboard", label: "대시보드", icon: LayoutDashboard },
  { key: "agents", label: "에이전트", icon: Server },
  { key: "commands", label: "명령", icon: Command },
  { key: "documents", label: "문서 작업", icon: FileText },
  { key: "members", label: "멤버", icon: Users },
  { key: "templates", label: "템플릿", icon: Upload },
  { key: "photos", label: "사진", icon: Camera },
  { key: "deliveries", label: "전달", icon: Truck },
  { key: "events", label: "이벤트", icon: Activity }
];

const adminRoles = new Set(["OWNER", "ADMIN"]);
const memberRoleOptions: MembershipRole[] = ["OWNER", "ADMIN", "MEMBER", "VIEWER"];
const commandFilterOptions = ["ALL", "PENDING", "DELIVERED", "ACKED", "COMPLETED", "FAILED", "EXPIRED"];
const documentFilterOptions = ["ALL", "REQUESTED", "GENERATING", "GENERATED", "FAILED"];
const photoFilterOptions = ["ALL", "PENDING_UPLOAD", "UPLOADED"];
const pickupFilterOptions = ["ALL", "PENDING", "PICKED_UP", "FAILED", "NOT_REQUIRED"];
const deliveryFilterOptions = ["ALL", "REQUESTED", "SENDING", "COMPLETED", "FAILED"];

export default function App() {
  const [auth, setAuth] = useState<AdminState | null>(null);
  const [selectedOfficeId, setSelectedOfficeId] = useState<number | null>(null);
  const [activeView, setActiveView] = useState<ViewKey>("dashboard");
  const [opsData, setOpsData] = useState<OpsData>(emptyOpsData);
  const [loading, setLoading] = useState(false);
  const [booting, setBooting] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [commandStatus, setCommandStatus] = useState("ALL");
  const [documentStatus, setDocumentStatus] = useState("ALL");
  const [photoStatus, setPhotoStatus] = useState("ALL");
  const [pickupStatus, setPickupStatus] = useState("ALL");
  const [deliveryStatus, setDeliveryStatus] = useState("ALL");
  const [pendingInvitationToken, setPendingInvitationToken] = useState(() => invitationTokenFromPath());

  const selectedOffice = useMemo(
    () => auth?.user.offices.find((office) => office.id === selectedOfficeId) ?? null,
    [auth, selectedOfficeId]
  );

  const adminOffices = useMemo(
    () => auth?.user.offices.filter((office) => adminRoles.has(office.role)) ?? [],
    [auth]
  );

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
        const firstAdminOffice = user.offices.find((office) => adminRoles.has(office.role));
        setAuth({ ...stored, user });
        setSelectedOfficeId(
          user.offices.some((office) => office.id === savedOfficeId) ? savedOfficeId : firstAdminOffice?.id ?? null
        );
      })
      .catch(() => {
        window.localStorage.removeItem(AUTH_STORAGE_KEY);
        setAuth(null);
      })
      .finally(() => setBooting(false));
  }, []);

  useEffect(() => {
    if (!auth || !selectedOfficeId) {
      return;
    }
    window.localStorage.setItem(OFFICE_STORAGE_KEY, String(selectedOfficeId));
    refresh();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [auth?.accessToken, selectedOfficeId]);

  async function refresh() {
    if (!auth || !selectedOfficeId) {
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

  function handleAuthenticated(nextAuth: AdminState) {
    const firstAdminOffice = nextAuth.user.offices.find((office) => adminRoles.has(office.role));
    setAuth(nextAuth);
    setSelectedOfficeId(firstAdminOffice?.id ?? nextAuth.user.offices[0]?.id ?? null);
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

  if (adminOffices.length === 0) {
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
            <span>Operations Console</span>
          </div>
        </div>

        <nav className="main-nav" aria-label="운영 메뉴">
          {navItems.map((item) => {
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
        </nav>

        <div className="sidebar-footer">
          <span>{auth.user.name}</span>
          <small>{auth.user.email}</small>
        </div>
      </aside>

      <main className="workspace">
        <header className="topbar">
          <div>
            <p className="eyebrow">Office Ops</p>
            <h1>{viewTitle(activeView)}</h1>
          </div>

          <div className="topbar-actions">
            <label className="office-select">
              <span>사무소</span>
              <select
                value={selectedOfficeId ?? ""}
                onChange={(event) => setSelectedOfficeId(Number(event.target.value))}
              >
                {adminOffices.map((office) => (
                  <option key={office.id} value={office.id}>
                    {office.displayName} · {office.role}
                  </option>
                ))}
              </select>
              <ChevronDown size={16} />
            </label>
            <button className="icon-button" onClick={refresh} type="button" title="새로고침" aria-label="새로고침">
              {loading ? <Loader2 className="spin" size={18} /> : <RefreshCcw size={18} />}
            </button>
            <button className="icon-button" onClick={logout} type="button" title="로그아웃" aria-label="로그아웃">
              <LogOut size={18} />
            </button>
          </div>
        </header>

        {selectedOffice ? <OfficeStrip office={selectedOffice} /> : null}
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
            <strong>{isInvitationFlow ? "ArchDox Invitation" : "ArchDox Admin"}</strong>
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
            <strong>ArchDox Invitation</strong>
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
        <MetricCard icon={<Server size={20} />} label="Agents" value={summary?.agents.total ?? 0} detail={`${summary?.agents.byStatus.ONLINE ?? 0} online`} tone="green" />
        <MetricCard icon={<Wifi size={20} />} label="Active sessions" value={summary?.activeAgentSessions ?? 0} detail="WebSocket" tone="blue" />
        <MetricCard icon={<Command size={20} />} label="In-flight commands" value={summary?.inFlightAgentCommands ?? 0} detail="pending · acked" tone="amber" />
        <MetricCard icon={<FileText size={20} />} label="Document jobs" value={summary?.documentJobs.total ?? 0} detail={`${summary?.documentJobs.byStatus.GENERATING ?? 0} generating`} tone="blue" />
        <MetricCard icon={<Camera size={20} />} label="Photos" value={summary?.photos.total ?? 0} detail={`${summary?.photoOriginalPickups.byStatus.PENDING ?? 0} pickup pending`} tone="slate" />
        <MetricCard icon={<AlertTriangle size={20} />} label="Attention" value={activeWarnings} detail="commands + failures" tone={activeWarnings > 0 ? "red" : "green"} />
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
      <Panel title="Agent 목록" icon={<Server size={18} />} count={agents.length}>
        <Table
          columns={["Agent", "상태", "모드", "세션", "명령", "버전", "최근 접속"]}
          empty="등록된 Agent가 없습니다."
          rows={agents.map((agent) => [
            <CellTitle key="agent" title={agent.agentCode} subtitle={`#${agent.id}`} />,
            <StatusBadge key="status" status={agent.status} />,
            agent.deploymentMode,
            `${agent.activeSessionCount}`,
            `${agent.inFlightCommandCount} 진행 · ${agent.failedCommandCount} 실패`,
            agent.version ?? "-",
            formatDate(agent.lastSeenAt)
          ])}
        />
      </Panel>
      <Panel title="최근 세션" icon={<Wifi size={18} />} count={sessions.length}>
        <Table
          columns={["Session", "Agent", "상태", "API 인스턴스", "연결", "최근 신호", "종료 사유"]}
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
      title="Agent 명령"
      icon={<Command size={18} />}
      count={commands.length}
      action={<FilterSelect label="상태" options={commandFilterOptions} value={status} onChange={setStatus} />}
    >
      <Table
        columns={["Command", "Agent", "상태", "시도", "생성", "ACK", "완료/실패", "오류"]}
        empty="표시할 명령이 없습니다."
        rows={commands.map((command) => [
          <CellTitle key="command" title={command.commandType} subtitle={`#${command.id}`} />,
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
        columns={["Job", "상태", "진행", "Worker", "Report", "Artifacts", "요청", "오류"]}
        empty="표시할 문서 작업이 없습니다."
        rows={documents.map((job) => [
          <CellTitle key="job" title={`문서 작업 #${job.id}`} subtitle={job.progressStep} />,
          <StatusBadge key="status" status={job.status} />,
          <Progress key="progress" value={job.progressPercent} />,
          job.workerType,
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
    setNotice(`Copied \${${field.key}}`);
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
                  placeholder="DAILY_SUPERVISION"
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
                  placeholder="DAILY_SUPERVISION"
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
                          <CellTitle title={`v${revision.version}`} subtitle={`revision #${revision.id}`} />
                          <StatusBadge status={revision.status} />
                        </div>
                        <dl className="revision-meta">
                          <div>
                            <dt>Storage</dt>
                            <dd>{revision.templateStorageKind ?? "-"}</dd>
                          </div>
                          <div>
                            <dt>Published</dt>
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
        title="Template field catalog"
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
                  <code>{preset.recommendedFields.map((field) => `\${${field}}`).join(" ")}</code>
                </article>
              ))}
            </div>
          ) : null}

          <div className="template-field-grid">
            {fieldCatalog?.fields.length ? null : <EmptyState message="No template fields found." />}
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
                    title="Copy placeholder"
                    type="button"
                    aria-label={`Copy ${field.key}`}
                  >
                    <Copy size={16} />
                  </button>
                </div>
                <p>{field.description}</p>
                <dl>
                  <div>
                    <dt>Category</dt>
                    <dd>{field.category}</dd>
                  </div>
                  <div>
                    <dt>Source</dt>
                    <dd>{field.source}</dd>
                  </div>
                  <div>
                    <dt>Example</dt>
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
                placeholder="DAILY_SUPERVISION"
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
              override.template.source,
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
          <FilterSelect label="Pickup" options={pickupFilterOptions} value={pickupStatus} onChange={setPickupStatus} />
        </div>
      }
    >
      <Table
        columns={["Photo", "상태", "Pickup", "Report", "크기", "Assets", "GPS", "생성"]}
        empty="표시할 사진이 없습니다."
        rows={photos.map((photo) => [
          <CellTitle key="photo" title={`사진 #${photo.id}`} subtitle={photo.stepCode ?? "step 없음"} />,
          <StatusBadge key="status" status={photo.status} />,
          <StatusBadge key="pickup" status={photo.originalPickupStatus} />,
          photo.reportId ? `#${photo.reportId}` : "-",
          photo.width && photo.height ? `${photo.width} x ${photo.height}` : formatBytes(photo.bytes),
          photo.assets.map((asset) => `${asset.assetType}:${asset.status}`).join(", ") || "-",
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
        columns={["Delivery", "상태", "채널", "Job", "Artifact", "Agent Command", "요청", "오류"]}
        empty="표시할 전달 요청이 없습니다."
        rows={deliveries.map((delivery) => [
          <CellTitle key="delivery" title={`전달 #${delivery.id}`} subtitle={delivery.preparedStorageKind ?? "not prepared"} />,
          <StatusBadge key="status" status={delivery.status} />,
          delivery.channel,
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
                {event.workflowType ?? "workflow 없음"} · {event.resourceType ?? "resource 없음"}{" "}
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
      <span>{office.type}</span>
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
  value: number;
  detail: string;
  tone: "green" | "blue" | "amber" | "red" | "slate";
}) {
  return (
    <div className={`metric-card ${tone}`}>
      <div className="metric-icon">{icon}</div>
      <span>{label}</span>
      <strong>{value.toLocaleString()}</strong>
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
  return <span className={`status-badge ${statusTone(status)}`}>{status}</span>;
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
            {option}
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
                    title={`${status}: ${count}`}
                  />
                ))}
            </div>
            <div className="status-chip-row">
              {Object.entries(counts).map(([status, count]) => (
                <span key={status}>
                  {status} {count}
                </span>
              ))}
            </div>
          </div>
        );
      })}
    </div>
  );
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
  return navItems.find((item) => item.key === view)?.label ?? "대시보드";
}

function statusTone(status: string) {
  if (["ONLINE", "ACTIVE", "COMPLETED", "GENERATED", "UPLOADED", "PICKED_UP", "OWNER", "ADMIN", "INFO", "PUBLISHED", "ACCEPTED"].includes(status)) {
    return "green";
  }
  if (["REQUESTED", "PENDING", "PENDING_UPLOAD", "DELIVERED", "ACKED", "SENDING", "GENERATING", "WARN", "DRAFT"].includes(status)) {
    return "amber";
  }
  if (["FAILED", "EXPIRED", "CANCELLED", "OFFLINE", "ERROR", "SUSPENDED", "LEFT"].includes(status)) {
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
