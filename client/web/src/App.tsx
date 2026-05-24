import {
  Bell,
  Camera,
  ClipboardList,
  FileText,
  Home,
  Loader2,
  LogOut,
  MapPin,
  MoreHorizontal,
  Plus,
  Search,
  Settings,
  Sparkles,
  UploadCloud
} from "lucide-react";
import { FormEvent, useEffect, useMemo, useState } from "react";
import { useLocation, useNavigate } from "react-router";
import {
  createInspectionReport,
  createInspectionTarget,
  createProject,
  createSite,
  getInspectionReports,
  getInspectionTargets,
  getOfficeMembers,
  getProjects,
  getSites,
  me,
  reopenInspectionReport,
  saveInspectionStep,
  submitInspectionReport
} from "./api";
import { invitationTokenFromPathname, viewFromPath, viewPaths } from "./appRoutes";
import type { AppState, ViewKey } from "./appTypes";
import {
  BrandLogo,
  EmptyState,
  FullScreenCenter,
  InlineAlert,
  InlineNotice,
  LogoMark,
  MetricTile,
  Panel,
  StatusBadge,
  ViewHeader
} from "./components/common";
import { canManageProjects, canWriteReports } from "./domain/permissions";
import { ProjectAssignmentPanel, ReportAssignmentPanel } from "./features/assignments/AssignmentPanels";
import { AuthScreen } from "./features/auth/AuthScreen";
import { ReportChecklistPanel } from "./features/checklists/components/ReportChecklistPanel";
import { DocumentWorkspace } from "./features/documents/components/DocumentWorkspace";
import { PhotoWorkspace } from "./features/photos/components/PhotoWorkspace";
import { getDocumentTypes } from "./features/reports/api";
import { ReportList } from "./features/reports/components/ReportList";
import { ReportStartForm } from "./features/reports/components/ReportStartForm";
import { ReportWizard } from "./features/reports/components/ReportWizard";
import type { DocumentTypeDefinition, ReportFormValues } from "./features/reports/types";
import type {
  InspectionReport,
  InspectionStep,
  InspectionTarget,
  MeResponse,
  Office,
  OfficeMember,
  Project,
  Site
} from "./types";

type WorkspaceData = {
  documentTypes: DocumentTypeDefinition[];
  projects: Project[];
  reports: InspectionReport[];
  sites: Site[];
  targets: InspectionTarget[];
};

type ProjectFormValues = {
  name: string;
  address: string;
  buildingType: string;
  startDate: string;
  endDate: string;
};

type SiteFormValues = {
  siteCode: string;
  name: string;
  address: string;
  siteType: string;
  startDate: string;
  endDate: string;
};

type TargetFormValues = {
  parentTargetId: number | null;
  targetType: string;
  code: string;
  name: string;
  address: string;
};

type CodeOption = {
  value: string;
  label: string;
};

const AUTH_STORAGE_KEY = "archdox.client.auth";
const LEGACY_OFFICE_STORAGE_KEY = "archdox.client.officeId";

const emptyWorkspace: WorkspaceData = {
  documentTypes: [],
  projects: [],
  reports: [],
  sites: [],
  targets: []
};

const navItems: Array<{ key: ViewKey; label: string; icon: typeof Home }> = [
  { key: "home", label: "홈", icon: Home },
  { key: "projects", label: "프로젝트", icon: ClipboardList },
  { key: "reports", label: "리포트", icon: FileText },
  { key: "photos", label: "사진", icon: Camera },
  { key: "jobs", label: "문서", icon: UploadCloud },
  { key: "more", label: "더보기", icon: MoreHorizontal }
];

const projectBusinessTypeOptions: CodeOption[] = [
  { value: "CONSTRUCTION_SUPERVISION", label: "건축 감리" },
  { value: "BUILDING_SAFETY_INSPECTION", label: "건축물 안전점검" },
  { value: "FACILITY_INSPECTION", label: "시설물 점검" },
  { value: "ASBESTOS_SUPERVISION", label: "석면 감리" },
  { value: "MAINTENANCE_INSPECTION", label: "유지관리 점검" },
  { value: "OTHER", label: "기타" }
];

const siteTypeOptions: CodeOption[] = [
  { value: "CONSTRUCTION_SITE", label: "공사 현장" },
  { value: "BUILDING", label: "건축물" },
  { value: "FACILITY", label: "시설" },
  { value: "PLANT", label: "플랜트/공장" },
  { value: "CAMPUS", label: "단지/캠퍼스" },
  { value: "WORK_AREA", label: "작업구역" },
  { value: "OTHER", label: "기타" }
];

const inspectionTargetTypeOptions: CodeOption[] = [
  { value: "BUILDING", label: "건축물" },
  { value: "FACILITY", label: "시설" },
  { value: "FLOOR", label: "층" },
  { value: "ROOM", label: "실/공간" },
  { value: "ZONE", label: "구역" },
  { value: "STRUCTURAL_ELEMENT", label: "구조 부재" },
  { value: "EQUIPMENT", label: "장비" },
  { value: "COMPONENT", label: "부품/구성품" },
  { value: "MATERIAL", label: "자재" },
  { value: "WORK_AREA", label: "작업구역" },
  { value: "OTHER", label: "기타" }
];



export default function App() {
  const location = useLocation();
  const navigate = useNavigate();
  const [auth, setAuth] = useState<AppState | null>(null);
  const [selectedOfficeId, setSelectedOfficeId] = useState<number | null>(null);
  const [selectedProjectId, setSelectedProjectId] = useState<number | null>(null);
  const [selectedSiteId, setSelectedSiteId] = useState<number | null>(null);
  const [selectedTargetId, setSelectedTargetId] = useState<number | null>(null);
  const [selectedReportId, setSelectedReportId] = useState<number | null>(null);
  const [workspace, setWorkspace] = useState<WorkspaceData>(emptyWorkspace);
  const [officeMembers, setOfficeMembers] = useState<OfficeMember[]>([]);
  const [pendingInvitationToken, setPendingInvitationToken] = useState(() =>
    invitationTokenFromPathname(window.location.pathname)
  );
  const [booting, setBooting] = useState(true);
  const [loading, setLoading] = useState(false);
  const [loadingSites, setLoadingSites] = useState(false);
  const [loadingTargets, setLoadingTargets] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const selectedOffice = useMemo(
    () => {
      if (!auth) {
        return null;
      }
      return auth.user.offices.find((office) => office.id === selectedOfficeId) ?? defaultOffice(auth.user);
    },
    [auth, selectedOfficeId]
  );
  const canManageSelectedOffice = canManageProjects(selectedOffice);
  const canWriteSelectedOfficeReports = canWriteReports(selectedOffice);
  const selectedProject = useMemo(
    () => workspace.projects.find((project) => project.id === selectedProjectId) ?? null,
    [workspace.projects, selectedProjectId]
  );
  const selectedReport = useMemo(
    () => workspace.reports.find((report) => report.id === selectedReportId) ?? workspace.reports[0] ?? null,
    [workspace.reports, selectedReportId]
  );
  const activeView = viewFromPath(location.pathname);
  const activeNavItem = navItems.find((item) => item.key === activeView) ?? navItems[0];

  function navigateToView(view: ViewKey) {
    navigate(viewPaths[view]);
  }

  useEffect(() => {
    if (pendingInvitationToken) {
      setBooting(false);
      return;
    }
    const raw = window.localStorage.getItem(AUTH_STORAGE_KEY);
    if (!raw) {
      setBooting(false);
      return;
    }
    const stored = JSON.parse(raw) as Pick<AppState, "accessToken" | "refreshToken">;
    me(stored.accessToken)
      .then((user) => {
        const nextOfficeId = defaultOfficeId(user);
        window.localStorage.removeItem(LEGACY_OFFICE_STORAGE_KEY);
        setAuth({ ...stored, user });
        setSelectedOfficeId(nextOfficeId);
      })
      .catch(() => {
        window.localStorage.removeItem(AUTH_STORAGE_KEY);
        setAuth(null);
      })
      .finally(() => setBooting(false));
  }, [pendingInvitationToken]);

  useEffect(() => {
    if (!auth) {
      return;
    }
    const nextOfficeId = defaultOfficeId(auth.user);
    if (nextOfficeId === selectedOfficeId) {
      return;
    }
    setSelectedOfficeId(nextOfficeId);
  }, [auth, selectedOfficeId]);

  useEffect(() => {
    if (!auth || !selectedOfficeId) {
      return;
    }
    loadWorkspace(auth.accessToken, selectedOfficeId);
  }, [auth?.accessToken, selectedOfficeId]);

  useEffect(() => {
    if (!auth || !selectedOfficeId || !canManageSelectedOffice) {
      setOfficeMembers([]);
      return;
    }
    let cancelled = false;
    getOfficeMembers(auth.accessToken, selectedOfficeId)
      .then((members) => {
        if (!cancelled) {
          setOfficeMembers(members);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setOfficeMembers([]);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [auth?.accessToken, canManageSelectedOffice, selectedOfficeId]);

  useEffect(() => {
    if (workspace.projects.length === 0) {
      setSelectedProjectId(null);
      setSelectedSiteId(null);
      setSelectedTargetId(null);
      setWorkspace((current) => ({ ...current, sites: [] }));
      return;
    }
    if (!selectedProjectId || !workspace.projects.some((project) => project.id === selectedProjectId)) {
      setSelectedProjectId(workspace.projects[0].id);
    }
  }, [workspace.projects, selectedProjectId]);

  useEffect(() => {
    if (!auth || !selectedOfficeId || !selectedProjectId) {
      return;
    }
    loadProjectSites(selectedProjectId, auth.accessToken, selectedOfficeId);
  }, [auth?.accessToken, selectedOfficeId, selectedProjectId]);

  useEffect(() => {
    if (!auth || !selectedOfficeId || !selectedProjectId || !selectedSiteId) {
      setWorkspace((current) => ({ ...current, targets: [] }));
      setSelectedTargetId(null);
      return;
    }
    loadSiteTargets(selectedProjectId, selectedSiteId, auth.accessToken, selectedOfficeId);
  }, [auth?.accessToken, selectedOfficeId, selectedProjectId, selectedSiteId]);

  useEffect(() => {
    if (workspace.reports.length === 0) {
      setSelectedReportId(null);
      return;
    }
    if (!selectedReportId || !workspace.reports.some((report) => report.id === selectedReportId)) {
      setSelectedReportId(workspace.reports[0].id);
    }
  }, [workspace.reports, selectedReportId]);

  async function loadWorkspace(token = auth?.accessToken, officeId = selectedOfficeId) {
    if (!token || !officeId) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const [projects, reports, documentTypes] = await Promise.all([
        getProjects(token, officeId),
        getInspectionReports(token, officeId),
        getDocumentTypes(token, officeId)
      ]);
      setWorkspace((current) => ({ ...current, documentTypes, projects, reports }));
    } catch (err) {
      setError(err instanceof Error ? err.message : "업무 데이터를 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }

  async function loadProjectSites(projectId: number, token = auth?.accessToken, officeId = selectedOfficeId) {
    if (!token || !officeId) {
      return;
    }
    setLoadingSites(true);
    setError(null);
    try {
      const sites = await getSites(token, officeId, projectId);
      setWorkspace((current) => ({ ...current, sites }));
      setSelectedSiteId((current) => {
        if (current && sites.some((site) => site.id === current)) {
          return current;
        }
        return sites[0]?.id ?? null;
      });
    } catch (err) {
      setWorkspace((current) => ({ ...current, sites: [] }));
      setSelectedSiteId(null);
      setSelectedTargetId(null);
      setError(err instanceof Error ? err.message : "현장 데이터를 불러오지 못했습니다.");
    } finally {
      setLoadingSites(false);
    }
  }

  async function loadSiteTargets(
    projectId: number,
    siteId: number,
    token = auth?.accessToken,
    officeId = selectedOfficeId
  ) {
    if (!token || !officeId) {
      return;
    }
    setLoadingTargets(true);
    setError(null);
    try {
      const targets = await getInspectionTargets(token, officeId, projectId, siteId);
      setWorkspace((current) => ({ ...current, targets }));
      setSelectedTargetId((current) => {
        if (current && targets.some((target) => target.id === current)) {
          return current;
        }
        return targets[0]?.id ?? null;
      });
    } catch (err) {
      setWorkspace((current) => ({ ...current, targets: [] }));
      setSelectedTargetId(null);
      setError(err instanceof Error ? err.message : "점검 대상 데이터를 불러오지 못했습니다.");
    } finally {
      setLoadingTargets(false);
    }
  }

  function handleAuthenticated(nextAuth: AppState) {
    const nextOfficeId = defaultOfficeId(nextAuth.user);
    setAuth(nextAuth);
    setSelectedOfficeId(nextOfficeId);
    window.localStorage.removeItem(LEGACY_OFFICE_STORAGE_KEY);
    window.localStorage.setItem(
      AUTH_STORAGE_KEY,
      JSON.stringify({ accessToken: nextAuth.accessToken, refreshToken: nextAuth.refreshToken })
    );
  }

  function logout() {
    window.localStorage.removeItem(AUTH_STORAGE_KEY);
    window.localStorage.removeItem(LEGACY_OFFICE_STORAGE_KEY);
    setAuth(null);
    setSelectedOfficeId(null);
    setSelectedProjectId(null);
    setSelectedSiteId(null);
    setSelectedTargetId(null);
    setSelectedReportId(null);
    setWorkspace(emptyWorkspace);
    setOfficeMembers([]);
  }

  async function handleCreateProject(values: ProjectFormValues) {
    if (!auth || !selectedOfficeId) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const project = await createProject(auth.accessToken, selectedOfficeId, {
        name: values.name,
        address: normalizeFormValue(values.address),
        buildingType: normalizeFormValue(values.buildingType),
        startDate: normalizeFormValue(values.startDate),
        endDate: normalizeFormValue(values.endDate)
      });
      await loadWorkspace(auth.accessToken, selectedOfficeId);
      setSelectedProjectId(project.id);
      navigateToView("projects");
    } catch (err) {
      setError(err instanceof Error ? err.message : "프로젝트를 만들지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }

  function handleSelectProject(projectId: number) {
    setSelectedProjectId(projectId);
    setSelectedSiteId(null);
    setSelectedTargetId(null);
  }

  function handleSelectSite(siteId: number) {
    setSelectedSiteId(siteId);
    setSelectedTargetId(null);
  }

  function handleOpenReport(reportId: number) {
    const report = workspace.reports.find((item) => item.id === reportId);
    setSelectedReportId(reportId);
    if (report) {
      setSelectedProjectId(report.projectId);
      setSelectedSiteId(report.siteId ?? null);
    }
    navigateToView("reports");
  }

  function handleSelectReportContext(reportId: number) {
    const report = workspace.reports.find((item) => item.id === reportId);
    setSelectedReportId(reportId);
    if (report) {
      setSelectedProjectId(report.projectId);
      setSelectedSiteId(report.siteId ?? null);
    }
  }

  async function handleCreateSite(values: SiteFormValues) {
    if (!auth || !selectedOfficeId || !selectedProjectId) {
      return;
    }
    setLoadingSites(true);
    setError(null);
    try {
      const site = await createSite(auth.accessToken, selectedOfficeId, selectedProjectId, {
        siteCode: normalizeFormValue(values.siteCode),
        name: values.name,
        address: normalizeFormValue(values.address),
        siteType: normalizeFormValue(values.siteType),
        startDate: normalizeFormValue(values.startDate),
        endDate: normalizeFormValue(values.endDate)
      });
      await loadProjectSites(selectedProjectId, auth.accessToken, selectedOfficeId);
      setSelectedSiteId(site.id);
    } catch (err) {
      setError(err instanceof Error ? err.message : "현장을 만들지 못했습니다.");
    } finally {
      setLoadingSites(false);
    }
  }

  async function handleCreateTarget(values: TargetFormValues) {
    if (!auth || !selectedOfficeId || !selectedProjectId || !selectedSiteId) {
      return;
    }
    setLoadingTargets(true);
    setError(null);
    try {
      const target = await createInspectionTarget(auth.accessToken, selectedOfficeId, selectedProjectId, selectedSiteId, {
        parentTargetId: values.parentTargetId,
        targetType: values.targetType,
        code: normalizeFormValue(values.code),
        name: values.name,
        address: normalizeFormValue(values.address),
        metadata: {}
      });
      await loadSiteTargets(selectedProjectId, selectedSiteId, auth.accessToken, selectedOfficeId);
      setSelectedTargetId(target.id);
    } catch (err) {
      setError(err instanceof Error ? err.message : "점검 대상을 만들지 못했습니다.");
    } finally {
      setLoadingTargets(false);
    }
  }

  async function handleCreateReport(values: ReportFormValues) {
    if (!auth || !selectedOfficeId) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const report = await createInspectionReport(auth.accessToken, selectedOfficeId, {
        projectId: values.projectId,
        siteId: values.siteId,
        reportType: values.reportType,
        title: normalizeFormValue(values.title),
        templateId: null
      });
      await loadWorkspace(auth.accessToken, selectedOfficeId);
      setSelectedReportId(report.id);
      navigateToView("reports");
    } catch (err) {
      setError(err instanceof Error ? err.message : "리포트를 시작하지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }

  async function handleSaveReportStep(reportId: number, stepCode: string, payload: Record<string, unknown>) {
    if (!auth || !selectedOfficeId) {
      throw new Error("로그인이 필요합니다.");
    }
    setError(null);
    try {
      const step = await saveInspectionStep(auth.accessToken, selectedOfficeId, reportId, stepCode, payload);
      await loadWorkspace(auth.accessToken, selectedOfficeId);
      setSelectedReportId(reportId);
      return step;
    } catch (err) {
      const message = err instanceof Error ? err.message : "작성 단계를 저장하지 못했습니다.";
      setError(message);
      throw err;
    }
  }

  async function handleSubmitReport(reportId: number) {
    if (!auth || !selectedOfficeId) {
      throw new Error("로그인이 필요합니다.");
    }
    setError(null);
    try {
      await submitInspectionReport(auth.accessToken, selectedOfficeId, reportId);
      await loadWorkspace(auth.accessToken, selectedOfficeId);
      setSelectedReportId(reportId);
    } catch (err) {
      const message = err instanceof Error ? err.message : "리포트를 제출하지 못했습니다.";
      setError(message);
      throw err;
    }
  }

  async function handleReopenReport(reportId: number) {
    if (!auth || !selectedOfficeId) {
      throw new Error("로그인이 필요합니다.");
    }
    setError(null);
    try {
      await reopenInspectionReport(auth.accessToken, selectedOfficeId, reportId);
      await loadWorkspace(auth.accessToken, selectedOfficeId);
      setSelectedReportId(reportId);
    } catch (err) {
      const message = err instanceof Error ? err.message : "리포트를 수정본으로 열지 못했습니다.";
      setError(message);
      throw err;
    }
  }

  if (booting) {
    return (
      <FullScreenCenter>
        <LogoMark />
        <p>archdoX를 준비하는 중입니다.</p>
      </FullScreenCenter>
    );
  }

  if (pendingInvitationToken) {
    return (
      <AuthScreen
        invitationToken={pendingInvitationToken}
        onAuthenticated={(nextAuth, options) => {
          handleAuthenticated(nextAuth);
          if (options?.invitationAccepted) {
            setPendingInvitationToken(null);
            navigate(viewPaths.home, { replace: true });
          }
        }}
      />
    );
  }

  if (!auth) {
    return <AuthScreen onAuthenticated={handleAuthenticated} />;
  }

  return (
    <div className="client-shell">
      <aside className="side-nav">
        <BrandLogo subtitle="Client Workspace" />
        <nav aria-label="주요 메뉴">
          {navItems.map((item) => {
            const Icon = item.icon;
            return (
              <button
                className={activeView === item.key ? "nav-link active" : "nav-link"}
                key={item.key}
                onClick={() => navigateToView(item.key)}
                type="button"
              >
                <Icon size={18} />
                <span>{item.label}</span>
              </button>
            );
          })}
        </nav>
        <button className="nav-link logout-link" onClick={logout} type="button">
          <LogOut size={18} />
          <span>로그아웃</span>
        </button>
      </aside>

      <section className="workspace-shell">
        <header className="product-bar">
          <div className="topbar-title">
            <div className="mobile-brand">
              <BrandLogo />
            </div>
            <div>
              <p className="eyebrow">{workspaceLabel(selectedOffice)}</p>
              <h1>{activeNavItem.label}</h1>
            </div>
          </div>
          <label className="global-search">
            <Search size={18} />
            <input placeholder="프로젝트, 리포트 검색" type="search" />
          </label>
          <div className="product-actions">
            <button className="icon-button" type="button" aria-label="알림" title="알림">
              <Bell size={18} />
            </button>
            <button className="avatar-button" type="button" title={auth.user.email}>
              {initial(auth.user.name)}
            </button>
          </div>
        </header>

        <main className="client-main">
          {error ? <InlineAlert message={error} /> : null}
          {activeView === "home" && (
            <HomeView
              loading={loading}
              office={selectedOffice}
              projects={workspace.projects}
              reports={workspace.reports}
              onStartReport={() => navigateToView("projects")}
              onRefresh={() => loadWorkspace()}
            />
          )}
          {activeView === "projects" && (
            <ProjectsView
              documentTypes={workspace.documentTypes}
              loading={loading}
              loadingSites={loadingSites}
              loadingTargets={loadingTargets}
              projects={workspace.projects}
              reports={workspace.reports}
              selectedProject={selectedProject}
              selectedProjectId={selectedProjectId}
              selectedSiteId={selectedSiteId}
              selectedTargetId={selectedTargetId}
              sites={workspace.sites}
              targets={workspace.targets}
              canManageProjects={canManageSelectedOffice}
              canWriteReports={canWriteSelectedOfficeReports}
              officeId={selectedOfficeId}
              officeMembers={officeMembers}
              token={auth.accessToken}
              onCreateProject={handleCreateProject}
              onCreateReport={handleCreateReport}
              onCreateSite={handleCreateSite}
              onCreateTarget={handleCreateTarget}
              onOpenReport={handleOpenReport}
              onSelectProject={handleSelectProject}
              onSelectSite={handleSelectSite}
              onSelectTarget={setSelectedTargetId}
            />
          )}
          {activeView === "reports" && (
            <ReportsView
              officeId={selectedOfficeId}
              projects={workspace.projects}
              reports={workspace.reports}
              selectedReport={selectedReport}
              sites={workspace.sites}
              targets={workspace.targets}
              token={auth.accessToken}
              canManageAssignments={canManageSelectedOffice}
              canWriteReports={canWriteSelectedOfficeReports}
              officeMembers={officeMembers}
              onReopenReport={handleReopenReport}
              onSaveStep={handleSaveReportStep}
              onSelectReport={handleOpenReport}
              onSubmitReport={handleSubmitReport}
            />
          )}
          {activeView === "photos" && (
            <PhotoWorkspace
              officeId={selectedOfficeId}
              projects={workspace.projects}
              reports={workspace.reports}
              selectedReport={selectedReport}
              token={auth.accessToken}
              onSelectReport={handleSelectReportContext}
            />
          )}
          {activeView === "jobs" && (
            <DocumentWorkspace
              officeId={selectedOfficeId}
              projects={workspace.projects}
              reports={workspace.reports}
              token={auth.accessToken}
              onRefreshWorkspace={() => loadWorkspace()}
            />
          )}
          {activeView === "more" && <MoreView user={auth.user} onLogout={logout} />}
        </main>
      </section>

      <nav className="bottom-nav" aria-label="모바일 메뉴">
        {navItems.slice(0, 5).map((item) => {
          const Icon = item.icon;
          return (
            <button
              className={activeView === item.key ? "bottom-link active" : "bottom-link"}
              key={item.key}
              onClick={() => navigateToView(item.key)}
              type="button"
            >
              <Icon size={18} />
              <span>{item.label}</span>
            </button>
          );
        })}
      </nav>
    </div>
  );
}

function HomeView({
  loading,
  office,
  projects,
  reports,
  onStartReport,
  onRefresh
}: {
  loading: boolean;
  office: Office | null;
  projects: Project[];
  reports: InspectionReport[];
  onStartReport: () => void;
  onRefresh: () => void;
}) {
  const activeReports = reports.filter((report) => !["GENERATED", "CANCELLED"].includes(report.status));
  const generatedReports = reports.filter((report) => report.status === "GENERATED");

  return (
    <div className="view-stack">
      <section className="workspace-hero">
        <div>
          <p className="eyebrow">Workspace</p>
          <h1>{office?.displayName ?? "내 작업공간"}</h1>
          <p>오늘 처리할 감리/점검 리포트와 문서 생성 상태를 한 곳에서 확인합니다.</p>
        </div>
        <div className="hero-actions">
          <button className="secondary-button" onClick={onRefresh} type="button">
            {loading ? <Loader2 className="spin" size={17} /> : <Sparkles size={17} />}
            새로고침
          </button>
          <button className="primary-button" onClick={onStartReport} type="button">
            <Plus size={17} />
            리포트 시작
          </button>
        </div>
      </section>

      <div className="metric-row">
        <MetricTile label="프로젝트" value={projects.length} detail="업무 묶음" />
        <MetricTile label="작성 중 리포트" value={activeReports.length} detail="제출 전/생성 전" />
        <MetricTile label="생성 완료" value={generatedReports.length} detail="문서 완료" />
      </div>

      <div className="split-grid">
        <Panel title="최근 프로젝트" action={<button className="text-button" type="button">전체 보기</button>}>
          <ProjectList projects={projects.slice(0, 5)} />
        </Panel>
        <Panel title="최근 리포트" action={<button className="text-button" type="button">전체 보기</button>}>
          <ReportList reports={reports.slice(0, 6)} projects={projects} />
        </Panel>
      </div>
    </div>
  );
}

function ProjectsView({
  documentTypes,
  loading,
  loadingSites,
  loadingTargets,
  projects,
  reports,
  selectedProject,
  selectedProjectId,
  selectedSiteId,
  selectedTargetId,
  sites,
  targets,
  canManageProjects,
  canWriteReports,
  officeId,
  officeMembers,
  token,
  onCreateProject,
  onCreateReport,
  onCreateSite,
  onCreateTarget,
  onOpenReport,
  onSelectProject,
  onSelectSite,
  onSelectTarget
}: {
  documentTypes: DocumentTypeDefinition[];
  loading: boolean;
  loadingSites: boolean;
  loadingTargets: boolean;
  projects: Project[];
  reports: InspectionReport[];
  selectedProject: Project | null;
  selectedProjectId: number | null;
  selectedSiteId: number | null;
  selectedTargetId: number | null;
  sites: Site[];
  targets: InspectionTarget[];
  canManageProjects: boolean;
  canWriteReports: boolean;
  officeId: number | null;
  officeMembers: OfficeMember[];
  token: string;
  onCreateProject: (values: ProjectFormValues) => Promise<void>;
  onCreateReport: (values: ReportFormValues) => Promise<void>;
  onCreateSite: (values: SiteFormValues) => Promise<void>;
  onCreateTarget: (values: TargetFormValues) => Promise<void>;
  onOpenReport: (reportId: number) => void;
  onSelectProject: (projectId: number) => void;
  onSelectSite: (siteId: number) => void;
  onSelectTarget: (targetId: number) => void;
}) {
  const projectReports = selectedProjectId
    ? reports.filter((report) => report.projectId === selectedProjectId)
    : [];
  const selectedSite = selectedSiteId ? sites.find((site) => site.id === selectedSiteId) ?? null : null;
  const siteReports = selectedSiteId
    ? projectReports.filter((report) => report.siteId === selectedSiteId)
    : projectReports;

  return (
    <div className="view-stack">
      <ViewHeader
        title="프로젝트 / 현장"
        text="프로젝트를 고르고, 현장을 선택한 뒤, 해당 현장의 리포트를 만들거나 이어서 작성합니다."
      />
      <WorkflowPath
        steps={[
          { label: "프로젝트", active: true, complete: Boolean(selectedProject) },
          { label: "현장", active: Boolean(selectedProject), complete: Boolean(selectedSite) },
          { label: "리포트", active: Boolean(selectedSite), complete: siteReports.length > 0 },
          { label: "작성", active: false, complete: false }
        ]}
      />

      <div className="workflow-board">
        <Panel title="1. 프로젝트" action={<span className="panel-context">{projects.length}개</span>}>
          <div className="panel-body">
            {canManageProjects ? (
              <ProjectForm busy={loading} onSubmit={onCreateProject} />
            ) : (
              <InlineNotice message="프로젝트 생성은 사무소 OWNER 또는 ADMIN 권한이 필요합니다." />
            )}
          </div>
          <ProjectList projects={projects} selectedProjectId={selectedProjectId} onSelectProject={onSelectProject} />
        </Panel>

        <Panel
          title="2. 현장"
          action={loadingSites ? <Loader2 className="spin" size={17} /> : <span className="panel-context">{sites.length}개</span>}
        >
          {selectedProject ? (
            <>
              <div className="panel-body">
                <div className="flow-context">
                  <strong>{selectedProject.name}</strong>
                  <span>{selectedProject.address || optionLabel(projectBusinessTypeOptions, selectedProject.buildingType) || "프로젝트 정보 없음"}</span>
                </div>
                {canManageProjects ? (
                  <SiteForm busy={loadingSites} onSubmit={onCreateSite} />
                ) : (
                  <InlineNotice message="현장 생성은 사무소 OWNER 또는 ADMIN 권한이 필요합니다." />
                )}
              </div>
              <SiteList sites={sites} selectedSiteId={selectedSiteId} onSelectSite={onSelectSite} />
            </>
          ) : (
            <EmptyState title="프로젝트를 먼저 선택하세요" text="프로젝트 안에 현장을 만들고 리포트를 시작합니다." />
          )}
        </Panel>

        <Panel title="3. 리포트" action={<span className="panel-context">{siteReports.length}개</span>}>
          {selectedSite ? (
            <>
              <div className="panel-body">
                <div className="flow-context">
                  <strong>{selectedSite.name}</strong>
                  <span>{selectedSite.address || optionLabel(siteTypeOptions, selectedSite.siteType) || "현장 정보 없음"}</span>
                </div>
                {canWriteReports ? (
                  <ReportStartForm
                    busy={loading}
                    documentTypes={documentTypes}
                    projects={projects}
                    selectedProjectId={selectedProjectId}
                    selectedSiteId={selectedSiteId}
                    sites={sites}
                    onSubmit={onCreateReport}
                  />
                ) : (
                  <InlineNotice message="리포트 작성은 OWNER, ADMIN, MEMBER 권한이 필요합니다." />
                )}
              </div>
              <ReportList
                projects={projects}
                reports={siteReports}
                onSelectReport={onOpenReport}
              />
            </>
          ) : (
            <EmptyState title="현장을 먼저 선택하세요" text="현장을 선택하면 리포트를 만들거나 이어서 작성할 수 있습니다." />
          )}
        </Panel>
      </div>

      <div className="support-grid">
        <Panel
          title="점검 대상"
          action={loadingTargets ? <Loader2 className="spin" size={17} /> : <span className="panel-context">{targets.length}개</span>}
        >
          {selectedSiteId ? (
            <>
              <div className="panel-body">
                {canManageProjects ? (
                  <TargetForm busy={loadingTargets} targets={targets} onSubmit={onCreateTarget} />
                ) : (
                  <InlineNotice message="평가 대상 생성은 사무소 OWNER 또는 ADMIN 권한이 필요합니다." />
                )}
              </div>
              <InspectionTargetList selectedTargetId={selectedTargetId} targets={targets} onSelectTarget={onSelectTarget} />
            </>
          ) : (
            <EmptyState title="현장을 먼저 선택하세요" text="현장 아래에 건축물, 층, 실, 장비 같은 점검 대상을 만듭니다." />
          )}
        </Panel>

        <Panel
          title="프로젝트 담당자"
          action={<span className="panel-context">{selectedProject ? selectedProject.name : "선택 필요"}</span>}
        >
          <ProjectAssignmentPanel
            canManage={canManageProjects}
            members={officeMembers}
            officeId={officeId}
            project={selectedProject}
            token={token}
          />
        </Panel>
      </div>
    </div>
  );
}

function ReportsView({
  officeId,
  projects,
  reports,
  selectedReport,
  sites,
  targets,
  token,
  canManageAssignments,
  canWriteReports,
  officeMembers,
  onReopenReport,
  onSaveStep,
  onSelectReport,
  onSubmitReport
}: {
  officeId: number | null;
  projects: Project[];
  reports: InspectionReport[];
  selectedReport: InspectionReport | null;
  sites: Site[];
  targets: InspectionTarget[];
  token: string;
  canManageAssignments: boolean;
  canWriteReports: boolean;
  officeMembers: OfficeMember[];
  onReopenReport: (reportId: number) => Promise<void>;
  onSaveStep: (reportId: number, stepCode: string, payload: Record<string, unknown>) => Promise<InspectionStep>;
  onSelectReport: (reportId: number) => void;
  onSubmitReport: (reportId: number) => Promise<void>;
}) {
  const selectedProject = selectedReport
    ? projects.find((project) => project.id === selectedReport.projectId) ?? null
    : null;
  const selectedSite = selectedReport?.siteId
    ? sites.find((site) => site.id === selectedReport.siteId) ?? null
    : null;
  const draftReports = reports.filter((report) => ["DRAFT", "STEP_SAVED"].includes(report.status));
  const readyReports = reports.filter((report) => report.status === "READY_TO_GENERATE");
  const generatedReports = reports.filter((report) => report.status === "GENERATED");

  return (
    <div className="view-stack">
      <ViewHeader title="리포트 작성" text="작성할 리포트를 선택하고, 필요한 항목을 단계별로 저장하며 완료합니다." />
      <div className="metric-row compact">
        <MetricTile label="작성 중" value={draftReports.length} detail="단계 저장/초안" />
        <MetricTile label="문서 생성 가능" value={readyReports.length} detail="제출 완료" />
        <MetricTile label="문서 완료" value={generatedReports.length} detail="생성 완료" />
      </div>
      <div className="report-workbench">
        <Panel title="리포트 목록" action={<span className="panel-context">{reports.length}개</span>}>
          <ReportList
            projects={projects}
            reports={reports}
            selectedReportId={selectedReport?.id ?? null}
            onSelectReport={onSelectReport}
          />
        </Panel>
        {selectedReport && officeId ? (
          <ReportWizard
            officeId={officeId}
            project={selectedProject}
            report={selectedReport}
            site={selectedSite}
            token={token}
            canWriteReports={canWriteReports}
            onReopenReport={onReopenReport}
            onSaveStep={onSaveStep}
            onSubmitReport={onSubmitReport}
            renderChecklistPanel={() => (
              <ReportChecklistPanel
                officeId={officeId}
                report={selectedReport}
                targets={targets}
                token={token}
                canWriteReports={Boolean(selectedReport.writeAllowed ?? canWriteReports) && ["DRAFT", "STEP_SAVED"].includes(selectedReport.status)}
              />
            )}
          />
        ) : (
          <Panel title="작성 화면">
            <EmptyState title="작성할 리포트를 선택하세요" text="프로젝트 화면에서 현장별 리포트를 만든 뒤 여기서 이어서 작성합니다." />
          </Panel>
        )}
      </div>
      <div className="support-grid">
        <Panel
          title="리포트 담당자"
          action={<span className="panel-context">{selectedReport ? selectedReport.reportNo : "선택 필요"}</span>}
        >
          <ReportAssignmentPanel
            canManage={canManageAssignments}
            members={officeMembers}
            officeId={officeId}
            report={selectedReport}
            token={token}
          />
        </Panel>
        <Panel title="작성 기준">
          <div className="settings-list">
            <div>
              <strong>자동 저장</strong>
              <span>이전/다음 이동 시 현재 단계가 저장됩니다.</span>
            </div>
            <div>
              <strong>문서 생성</strong>
              <span>제출 후 문서 탭에서 생성 요청과 진행 상태를 확인합니다.</span>
            </div>
          </div>
        </Panel>
      </div>
    </div>
  );
}

function WorkflowPath({
  steps
}: {
  steps: Array<{
    active: boolean;
    complete: boolean;
    label: string;
  }>;
}) {
  return (
    <div className="workflow-path" aria-label="업무 진행 흐름">
      {steps.map((step, index) => (
        <div
          className={[
            "workflow-path-step",
            step.active ? "active" : "",
            step.complete ? "complete" : ""
          ].filter(Boolean).join(" ")}
          key={step.label}
        >
          <span>{index + 1}</span>
          <strong>{step.label}</strong>
        </div>
      ))}
    </div>
  );
}

function MoreView({ user, onLogout }: { user: MeResponse; onLogout: () => void }) {
  return (
    <div className="view-stack">
      <ViewHeader title="더보기" text="계정, 알림, 오프라인 동기화 설정이 들어갈 자리입니다." />
      <Panel title="계정">
        <div className="settings-list">
          <div>
            <strong>{user.name}</strong>
            <span>{user.email}</span>
          </div>
          <button className="secondary-button" onClick={onLogout} type="button">
            <LogOut size={17} />
            로그아웃
          </button>
        </div>
      </Panel>
      <Panel title="설정">
        <div className="settings-list">
          <div>
            <strong>동기화</strong>
            <span>오프라인 저장과 업로드 큐는 다음 단계에서 연결합니다.</span>
          </div>
          <Settings size={20} />
        </div>
      </Panel>
    </div>
  );
}

function ProjectList({
  projects,
  selectedProjectId,
  onSelectProject
}: {
  projects: Project[];
  selectedProjectId?: number | null;
  onSelectProject?: (projectId: number) => void;
}) {
  if (projects.length === 0) {
    return <EmptyState title="프로젝트가 없습니다" text="첫 프로젝트를 만들면 리포트 작성 흐름을 시작할 수 있습니다." />;
  }
  return (
    <div className="item-list">
      {projects.map((project) => (
        <button
          className={selectedProjectId === project.id ? "list-row selectable active" : "list-row selectable"}
          key={project.id}
          onClick={() => onSelectProject?.(project.id)}
          type="button"
        >
          <div className="row-icon">
            <ClipboardList size={18} />
          </div>
          <div>
            <strong>{project.name}</strong>
            <span>{project.address || optionLabel(projectBusinessTypeOptions, project.buildingType) || "프로젝트 정보 없음"}</span>
          </div>
          <StatusBadge status={project.status} />
        </button>
      ))}
    </div>
  );
}

function SiteList({
  sites,
  selectedSiteId,
  onSelectSite
}: {
  sites: Site[];
  selectedSiteId?: number | null;
  onSelectSite: (siteId: number) => void;
}) {
  if (sites.length === 0) {
    return <EmptyState title="현장이 없습니다" text="이 프로젝트에서 실제 점검할 현장을 먼저 만드세요." />;
  }
  return (
    <div className="item-list">
      {sites.map((site) => (
        <button
          className={selectedSiteId === site.id ? "list-row selectable active" : "list-row selectable"}
          key={site.id}
          onClick={() => onSelectSite(site.id)}
          type="button"
        >
          <div className="row-icon">
            <MapPin size={18} />
          </div>
          <div>
            <strong>{site.name}</strong>
            <span>{site.address || optionLabel(siteTypeOptions, site.siteType) || site.siteCode || "현장 정보 없음"}</span>
          </div>
          <StatusBadge status={site.status} />
        </button>
      ))}
    </div>
  );
}

function InspectionTargetList({
  targets,
  selectedTargetId,
  onSelectTarget
}: {
  targets: InspectionTarget[];
  selectedTargetId?: number | null;
  onSelectTarget?: (targetId: number) => void;
}) {
  if (targets.length === 0) {
    return <EmptyState title="점검 대상이 없습니다" text="건축물, 층, 실, 장비처럼 문서에 들어갈 대상을 먼저 만드세요." />;
  }
  return (
    <div className="item-list">
      {targets.map((target) => (
        <button
          className={selectedTargetId === target.id ? "list-row selectable active" : "list-row selectable"}
          key={target.id}
          onClick={() => onSelectTarget?.(target.id)}
          type="button"
        >
          <div className="row-icon">
            <MapPin size={18} />
          </div>
          <div>
            <strong>{target.name}</strong>
            <span>
              {optionLabel(inspectionTargetTypeOptions, target.targetType)}
              {target.code ? ` · ${target.code}` : ""}
            </span>
          </div>
          <StatusBadge status={target.status} />
        </button>
      ))}
    </div>
  );
}

function ProjectForm({ busy, onSubmit }: { busy: boolean; onSubmit: (values: ProjectFormValues) => Promise<void> }) {
  const [values, setValues] = useState<ProjectFormValues>({
    name: "",
    address: "",
    buildingType: projectBusinessTypeOptions[0].value,
    startDate: "",
    endDate: ""
  });

  async function submit(event: FormEvent) {
    event.preventDefault();
    await onSubmit(values);
    setValues({ name: "", address: "", buildingType: projectBusinessTypeOptions[0].value, startDate: "", endDate: "" });
  }

  return (
    <form className="compact-form" onSubmit={submit}>
      <label className="wide">
        프로젝트명
        <input
          onChange={(event) => setValues({ ...values, name: event.target.value })}
          placeholder="예: 2026 상반기 안전점검"
          required
          value={values.name}
        />
      </label>
      <label className="wide">
        기준 주소/설명
        <input
          onChange={(event) => setValues({ ...values, address: event.target.value })}
          placeholder="프로젝트 대표 주소 또는 설명"
          value={values.address}
        />
      </label>
      <label>
        업무 유형
        <select
          onChange={(event) => setValues({ ...values, buildingType: event.target.value })}
          required
          value={values.buildingType}
        >
          {projectBusinessTypeOptions.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      </label>
      <label>
        시작일
        <input
          onChange={(event) => setValues({ ...values, startDate: event.target.value })}
          type="date"
          value={values.startDate}
        />
      </label>
      <button className="primary-button" disabled={busy} type="submit">
        {busy ? <Loader2 className="spin" size={17} /> : <Plus size={17} />}
        프로젝트 생성
      </button>
    </form>
  );
}

function TargetForm({
  busy,
  targets,
  onSubmit
}: {
  busy: boolean;
  targets: InspectionTarget[];
  onSubmit: (values: TargetFormValues) => Promise<void>;
}) {
  const [values, setValues] = useState<TargetFormValues>({
    parentTargetId: null,
    targetType: inspectionTargetTypeOptions[0].value,
    code: "",
    name: "",
    address: ""
  });

  async function submit(event: FormEvent) {
    event.preventDefault();
    await onSubmit(values);
    setValues({
      parentTargetId: null,
      targetType: inspectionTargetTypeOptions[0].value,
      code: "",
      name: "",
      address: ""
    });
  }

  return (
    <form className="compact-form" onSubmit={submit}>
      <label>
        상위 대상
        <select
          onChange={(event) =>
            setValues({ ...values, parentTargetId: event.target.value ? Number(event.target.value) : null })
          }
          value={values.parentTargetId ?? ""}
        >
          <option value="">없음</option>
          {targets.map((target) => (
            <option key={target.id} value={target.id}>
              {target.name}
            </option>
          ))}
        </select>
      </label>
      <label>
        대상 유형
        <select onChange={(event) => setValues({ ...values, targetType: event.target.value })} value={values.targetType}>
          {inspectionTargetTypeOptions.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      </label>
      <label>
        코드
        <input
          onChange={(event) => setValues({ ...values, code: event.target.value })}
          placeholder="B1, 2F, RM-201"
          value={values.code}
        />
      </label>
      <label className="wide">
        대상명
        <input
          onChange={(event) => setValues({ ...values, name: event.target.value })}
          placeholder="예: 본관동, 2층 복도, 옥상 방수층"
          required
          value={values.name}
        />
      </label>
      <label className="wide">
        위치/설명
        <input
          onChange={(event) => setValues({ ...values, address: event.target.value })}
          placeholder="대상 위치 또는 설명"
          value={values.address}
        />
      </label>
      <button className="primary-button" disabled={busy} type="submit">
        {busy ? <Loader2 className="spin" size={17} /> : <Plus size={17} />}
        대상 추가
      </button>
    </form>
  );
}

function SiteForm({ busy, onSubmit }: { busy: boolean; onSubmit: (values: SiteFormValues) => Promise<void> }) {
  const [values, setValues] = useState<SiteFormValues>({
    siteCode: "",
    name: "",
    address: "",
    siteType: siteTypeOptions[0].value,
    startDate: "",
    endDate: ""
  });

  async function submit(event: FormEvent) {
    event.preventDefault();
    await onSubmit(values);
    setValues({ siteCode: "", name: "", address: "", siteType: siteTypeOptions[0].value, startDate: "", endDate: "" });
  }

  return (
    <form className="compact-form" onSubmit={submit}>
      <label>
        현장 코드
        <input
          onChange={(event) => setValues({ ...values, siteCode: event.target.value })}
          placeholder="A-01"
          value={values.siteCode}
        />
      </label>
      <label className="wide">
        현장명
        <input
          onChange={(event) => setValues({ ...values, name: event.target.value })}
          placeholder="예: 본관동 정기점검"
          required
          value={values.name}
        />
      </label>
      <label className="wide">
        주소
        <input
          onChange={(event) => setValues({ ...values, address: event.target.value })}
          placeholder="현장 주소"
          value={values.address}
        />
      </label>
      <label>
        현장 유형
        <select
          onChange={(event) => setValues({ ...values, siteType: event.target.value })}
          required
          value={values.siteType}
        >
          {siteTypeOptions.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      </label>
      <button className="primary-button" disabled={busy} type="submit">
        {busy ? <Loader2 className="spin" size={17} /> : <Plus size={17} />}
        현장 생성
      </button>
    </form>
  );
}


function optionLabel(options: CodeOption[], value?: string | null) {
  if (!value) {
    return null;
  }
  return options.find((option) => option.value === value)?.label ?? value;
}

function defaultOffice(user: MeResponse) {
  return (
    user.offices.find((office) => office.type === "PERSONAL" && office.role === "OWNER") ??
    user.offices.find((office) => office.role === "OWNER" || office.role === "ADMIN") ??
    user.offices[0] ??
    null
  );
}

function defaultOfficeId(user: MeResponse) {
  return defaultOffice(user)?.id ?? null;
}

function workspaceLabel(office: Office | null) {
  if (!office) {
    return "작업공간";
  }
  return office.type === "PERSONAL" ? "개인 작업공간" : office.displayName;
}

function initial(name: string) {
  return name.trim().charAt(0).toUpperCase() || "A";
}

function normalizeFormValue(value: string) {
  const trimmed = value.trim();
  return trimmed.length === 0 ? null : trimmed;
}
