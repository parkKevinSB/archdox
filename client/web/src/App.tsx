import {
  Bell,
  Camera,
  BarChart3,
  ClipboardList,
  FileText,
  Loader2,
  LogOut,
  MapPin,
  MessageSquare,
  Plus,
  Search,
  Settings,
  Sparkles,
  Trash2,
  UploadCloud
} from "lucide-react";
import { FormEvent, type ReactNode, useEffect, useMemo, useRef, useState } from "react";
import { useLocation, useNavigate } from "react-router";
import {
  createInspectionReport,
  createInspectionTarget,
  createProject,
  createSite,
  deleteInspectionReport,
  deleteProject,
  deleteSite,
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
import { canManageOfficeAssignments, canManageProjects, canManageSites, canWriteReports } from "./domain/permissions";
import { ReportAssignmentPanel } from "./features/assignments/AssignmentPanels";
import { AuthScreen } from "./features/auth/AuthScreen";
import { ReportChecklistPanel } from "./features/checklists/components/ReportChecklistPanel";
import { DocumentWorkspace } from "./features/documents/components/DocumentWorkspace";
import { LegalUpdatesView } from "./features/legal/components/LegalUpdatesView";
import { PhotoWorkspace } from "./features/photos/components/PhotoWorkspace";
import { getDocumentTypes } from "./features/reports/api";
import { ReportList } from "./features/reports/components/ReportList";
import { ReportStartForm } from "./features/reports/components/ReportStartForm";
import { ReportWizard } from "./features/reports/components/ReportWizard";
import type { DocumentTypeDefinition, ReportFormValues } from "./features/reports/types";
import { ProjectWorkerChat } from "./features/workerchat/components/ProjectWorkerChat";
import type { WorkerChatSession } from "./features/workerchat/types";
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

const navItems: Array<{ key: ViewKey; label: string; icon: typeof ClipboardList }> = [
  { key: "projects", label: "프로젝트", icon: ClipboardList },
  { key: "sites", label: "현장", icon: MapPin },
  { key: "reports", label: "리포트", icon: FileText },
  { key: "jobs", label: "문서", icon: UploadCloud },
  { key: "photos", label: "사진", icon: Camera },
  { key: "legalUpdates", label: "법령 변경", icon: Bell },
  { key: "workChat", label: "작업 채팅", icon: MessageSquare },
  { key: "insightChat", label: "분석 채팅", icon: BarChart3 }
];

const bottomNavItems = navItems.filter((item) =>
  ["projects", "sites", "reports", "jobs", "photos"].includes(item.key)
);

const projectBusinessTypeOptions: CodeOption[] = [
  { value: "CONSTRUCTION_SUPERVISION", label: "공사감리" }
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
  const [chatLauncherOpen, setChatLauncherOpen] = useState(false);
  const lastWorkerChatSyncKeyRef = useRef("");

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
  const canManageSelectedProjectStructure = selectedProject?.structureManageAllowed ?? canManageSites(selectedOffice);
  const canCreateReportInSelectedProject = selectedProject?.reportCreateAllowed ?? canWriteSelectedOfficeReports;
  const canManageSelectedOfficeAssignments = canManageOfficeAssignments(selectedOffice);
  const selectedSite = useMemo(
    () =>
      selectedSiteId
        ? workspace.sites.find(
            (site) => site.id === selectedSiteId && (!selectedProjectId || site.projectId === selectedProjectId)
          ) ?? null
        : null,
    [workspace.sites, selectedProjectId, selectedSiteId]
  );
  const selectedReport = useMemo(
    () => (selectedReportId ? workspace.reports.find((report) => report.id === selectedReportId) ?? null : null),
    [workspace.reports, selectedReportId]
  );
  const activeView = viewFromPath(location.pathname);
  const activeNavItem = activeView === "more"
    ? { label: "프로필" }
    : navItems.find((item) => item.key === activeView) ?? navItems[0];

  function navigateToView(view: ViewKey) {
    navigate(viewPaths[view]);
  }

  function handlePrimaryNavigation(view: ViewKey) {
    setChatLauncherOpen(false);
    if (view === "reports") {
      setSelectedReportId(null);
    } else if (view === "projects" || view === "sites") {
      setSelectedReportId(null);
    }
    navigateToView(view);
  }

  function handleChatNavigation(view: Extract<ViewKey, "workChat" | "insightChat">) {
    setChatLauncherOpen(false);
    handlePrimaryNavigation(view);
  }

  useEffect(() => {
    window.requestAnimationFrame(() => {
      window.scrollTo({ top: 0, left: 0 });
      document.querySelector(".client-main")?.scrollTo({ top: 0, left: 0 });
    });
  }, [activeView]);

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
    if (selectedProjectId && !workspace.projects.some((project) => project.id === selectedProjectId)) {
      setSelectedProjectId(null);
      setSelectedSiteId(null);
      setSelectedTargetId(null);
      setWorkspace((current) => ({ ...current, sites: [], targets: [] }));
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
    if (selectedReportId && !workspace.reports.some((report) => report.id === selectedReportId)) {
      setSelectedReportId(null);
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
        return null;
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
        return null;
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
      setSelectedSiteId(null);
      setSelectedReportId(null);
      navigateToView("sites");
    } catch (err) {
      setError(err instanceof Error ? err.message : "프로젝트를 만들지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }

  function handleSelectProject(projectId: number) {
    const isSameProject = selectedProjectId === projectId;
    setSelectedProjectId(projectId);
    setSelectedSiteId(null);
    setSelectedTargetId(null);
    setSelectedReportId(null);
    if (isSameProject) {
      if (auth && selectedOfficeId) {
        void loadProjectSites(projectId, auth.accessToken, selectedOfficeId);
      }
    } else {
      setWorkspace((current) => ({ ...current, sites: [], targets: [] }));
    }
    navigateToView("sites");
  }

  function handleSelectSite(siteId: number) {
    const site = workspace.sites.find((item) => item.id === siteId);
    if (site) {
      setSelectedProjectId(site.projectId);
    }
    setSelectedSiteId(siteId);
    setSelectedTargetId(null);
    setSelectedReportId(null);
    navigateToView("reports");
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

  function handleCloseReport() {
    setSelectedReportId(null);
  }

  function handleSelectReportContext(reportId: number) {
    const report = workspace.reports.find((item) => item.id === reportId);
    setSelectedReportId(reportId);
    if (report) {
      setSelectedProjectId(report.projectId);
      setSelectedSiteId(report.siteId ?? null);
    }
  }

  function handleWorkerChatSessionSync(session: WorkerChatSession) {
    if (!auth || !selectedOfficeId) {
      return;
    }
    const latestAssistant = [...session.messages]
      .reverse()
      .find((message) => message.role === "ASSISTANT");
    const syncKey = [
      session.id,
      session.projectId,
      session.siteId ?? "",
      session.reportId ?? "",
      latestAssistant?.id ?? "",
      latestAssistant?.status ?? "",
      latestAssistant?.workerActionType ?? "",
      latestAssistant?.updatedAt ?? ""
    ].join(":");
    if (lastWorkerChatSyncKeyRef.current === syncKey) {
      return;
    }
    lastWorkerChatSyncKeyRef.current = syncKey;

    setSelectedProjectId(session.projectId);
    if (session.siteId) {
      setSelectedSiteId(session.siteId);
    } else if (session.stage === "AWAITING_SITE") {
      setSelectedSiteId(null);
      setSelectedReportId(null);
    }
    if (session.reportId) {
      setSelectedReportId(session.reportId);
    } else if (session.stage === "AWAITING_SITE" || session.stage === "AWAITING_REPORT") {
      setSelectedReportId(null);
    }

    const actionType = latestAssistant?.workerActionType ?? latestAssistant?.metadata?.actionType;
    const shouldRefreshWorkspace =
      latestAssistant?.status === "COMPLETED"
      && [
        "CREATE_SITE",
        "CREATE_REPORT",
        "UPDATE_REPORT_STEP",
        "SUBMIT_REPORT",
        "RUN_PREFLIGHT_REVIEW",
        "REQUEST_DOCUMENT_GENERATION"
      ].includes(String(actionType));
    if (shouldRefreshWorkspace) {
      void loadWorkspace(auth.accessToken, selectedOfficeId);
      if (session.projectId) {
        void loadProjectSites(session.projectId, auth.accessToken, selectedOfficeId);
      }
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
      setSelectedReportId(null);
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
      setSelectedReportId(null);
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
      setSelectedReportId(null);
      navigateToView("jobs");
    } catch (err) {
      const message = err instanceof Error ? err.message : "리포트를 제출하지 못했습니다.";
      setError(message);
      throw err;
    }
  }

  async function handleDeleteProject(projectId: number) {
    if (!auth || !selectedOfficeId) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      await deleteProject(auth.accessToken, selectedOfficeId, projectId);
      if (selectedProjectId === projectId) {
        setSelectedProjectId(null);
        setSelectedSiteId(null);
        setSelectedTargetId(null);
        setSelectedReportId(null);
        setWorkspace((current) => ({ ...current, sites: [], targets: [] }));
      }
      await loadWorkspace(auth.accessToken, selectedOfficeId);
      navigateToView("projects");
    } catch (err) {
      setError(err instanceof Error ? err.message : "프로젝트를 삭제하지 못했습니다.");
      throw err;
    } finally {
      setLoading(false);
    }
  }

  async function handleDeleteSite(siteId: number) {
    if (!auth || !selectedOfficeId || !selectedProjectId) {
      return;
    }
    setLoadingSites(true);
    setError(null);
    try {
      await deleteSite(auth.accessToken, selectedOfficeId, selectedProjectId, siteId);
      if (selectedSiteId === siteId) {
        setSelectedSiteId(null);
        setSelectedTargetId(null);
        setSelectedReportId(null);
        setWorkspace((current) => ({ ...current, targets: [] }));
      }
      await Promise.all([
        loadWorkspace(auth.accessToken, selectedOfficeId),
        loadProjectSites(selectedProjectId, auth.accessToken, selectedOfficeId)
      ]);
      navigateToView("sites");
    } catch (err) {
      setError(err instanceof Error ? err.message : "현장을 삭제하지 못했습니다.");
      throw err;
    } finally {
      setLoadingSites(false);
    }
  }

  async function handleDeleteReport(reportId: number) {
    if (!auth || !selectedOfficeId) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      await deleteInspectionReport(auth.accessToken, selectedOfficeId, reportId);
      if (selectedReportId === reportId) {
        setSelectedReportId(null);
      }
      await loadWorkspace(auth.accessToken, selectedOfficeId);
      navigateToView("reports");
    } catch (err) {
      setError(err instanceof Error ? err.message : "리포트를 삭제하지 못했습니다.");
      throw err;
    } finally {
      setLoading(false);
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
            navigate(viewPaths.projects, { replace: true });
          }
        }}
      />
    );
  }

  if (!auth) {
    return (
      <AuthScreen
        initialMode={location.pathname.startsWith("/signup") ? "signup" : "login"}
        onAuthenticated={(nextAuth) => {
          handleAuthenticated(nextAuth);
          navigate(viewPaths.projects, { replace: true });
        }}
      />
    );
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
                onClick={() => handlePrimaryNavigation(item.key)}
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
            <button
              className={activeView === "legalUpdates" ? "topbar-legal-button active" : "topbar-legal-button"}
              onClick={() => navigateToView("legalUpdates")}
              type="button"
              aria-label="법령 변경사항"
              title="법령 변경사항"
            >
              <FileText size={18} />
            </button>
            <div className="topbar-chat-control">
              {chatLauncherOpen ? (
                <div className="topbar-chat-menu" role="menu" aria-label="채팅 선택">
                  <button
                    className={activeView === "workChat" ? "active" : ""}
                    onClick={() => handleChatNavigation("workChat")}
                    type="button"
                  >
                    <MessageSquare size={17} />
                    <span>작업 채팅</span>
                  </button>
                  <button
                    className={activeView === "insightChat" ? "active" : ""}
                    onClick={() => handleChatNavigation("insightChat")}
                    type="button"
                  >
                    <BarChart3 size={17} />
                    <span>분석 채팅</span>
                  </button>
                </div>
              ) : null}
              <button
                aria-expanded={chatLauncherOpen}
                aria-label="채팅 열기"
                className={activeView === "workChat" || activeView === "insightChat" ? "topbar-chat-button active" : "topbar-chat-button"}
                onClick={() => setChatLauncherOpen((open) => !open)}
                type="button"
              >
                <MessageSquare size={18} />
              </button>
            </div>
            <button
              className="avatar-button"
              onClick={() => navigateToView("more")}
              type="button"
              title={`${auth.user.email} 프로필`}
              aria-label="프로필 열기"
            >
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
              onSelectProject={handleSelectProject}
              onRefresh={() => loadWorkspace()}
            />
          )}
          {activeView === "projects" && (
            <ProjectsView
              loading={loading}
              projects={workspace.projects}
              selectedProjectId={selectedProjectId}
              canManageProjects={canManageSelectedOffice}
              onCreateProject={handleCreateProject}
              onDeleteProject={handleDeleteProject}
              onSelectProject={handleSelectProject}
            />
          )}
          {activeView === "sites" && (
            <SitesView
              loadingSites={loadingSites}
              selectedProject={selectedProject}
              selectedSiteId={selectedSiteId}
              sites={workspace.sites}
              canManageSites={canManageSelectedProjectStructure}
              onCreateSite={handleCreateSite}
              onDeleteSite={handleDeleteSite}
              onBackToProjects={() => navigateToView("projects")}
              onSelectSite={handleSelectSite}
            />
          )}
          {activeView === "reports" && (
            <ReportsView
              documentTypes={workspace.documentTypes}
              loading={loading}
              officeId={selectedOfficeId}
              projects={workspace.projects}
              reports={workspace.reports}
              selectedProject={selectedProject}
              selectedReport={selectedReport}
              selectedSite={selectedSite}
              selectedSiteId={selectedSiteId}
              sites={workspace.sites}
              targets={workspace.targets}
              token={auth.accessToken}
              canManageAssignments={canManageSelectedOfficeAssignments}
              canWriteReports={canCreateReportInSelectedProject}
              officeMembers={officeMembers}
              onCreateReport={handleCreateReport}
              onDeleteReport={handleDeleteReport}
              onBackToSites={() => navigateToView("sites")}
              onCloseReport={handleCloseReport}
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
              currentOffice={selectedOffice}
              currentUser={auth.user}
              officeId={selectedOfficeId}
              projects={workspace.projects}
              reports={workspace.reports}
              token={auth.accessToken}
              onRefreshWorkspace={() => loadWorkspace()}
            />
          )}
          {activeView === "legalUpdates" && <LegalUpdatesView token={auth.accessToken} />}
          {activeView === "workChat" && (
            <ProjectWorkerChat
              officeId={selectedOfficeId}
              project={selectedProject}
              token={auth.accessToken}
              onOpenDocuments={() => navigateToView("jobs")}
              onSelectProject={() => navigateToView("projects")}
              onSessionSync={handleWorkerChatSessionSync}
            />
          )}
          {activeView === "insightChat" && <InsightChatView office={selectedOffice} />}
          {activeView === "more" && (
            <MoreView
              user={auth.user}
              onLogout={logout}
              onOpenLegalUpdates={() => navigateToView("legalUpdates")}
            />
          )}
        </main>
      </section>

      <div className={chatLauncherOpen ? "mobile-chat-launcher open" : "mobile-chat-launcher"}>
        {chatLauncherOpen ? (
          <div className="mobile-chat-menu" role="menu" aria-label="채팅 선택">
            <button
              className={activeView === "workChat" ? "active" : ""}
              onClick={() => handleChatNavigation("workChat")}
              type="button"
            >
              <MessageSquare size={17} />
              <span>작업 채팅</span>
            </button>
            <button
              className={activeView === "insightChat" ? "active" : ""}
              onClick={() => handleChatNavigation("insightChat")}
              type="button"
            >
              <BarChart3 size={17} />
              <span>분석 채팅</span>
            </button>
          </div>
        ) : null}
        <button
          aria-expanded={chatLauncherOpen}
          aria-label="채팅 열기"
          className={activeView === "workChat" || activeView === "insightChat" ? "mobile-chat-fab active" : "mobile-chat-fab"}
          onClick={() => setChatLauncherOpen((open) => !open)}
          type="button"
        >
          <MessageSquare size={21} />
        </button>
      </div>

      <nav className="bottom-nav" aria-label="모바일 메뉴">
        {bottomNavItems.map((item) => {
          const Icon = item.icon;
          return (
            <button
              className={activeView === item.key ? "bottom-link active" : "bottom-link"}
              key={item.key}
              onClick={() => handlePrimaryNavigation(item.key)}
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
  onSelectProject,
  onRefresh
}: {
  loading: boolean;
  office: Office | null;
  projects: Project[];
  reports: InspectionReport[];
  onStartReport: () => void;
  onSelectProject: (projectId: number) => void;
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
          <ProjectList projects={projects.slice(0, 5)} onSelectProject={onSelectProject} />
        </Panel>
        <Panel title="최근 리포트" action={<button className="text-button" type="button">전체 보기</button>}>
          <ReportList reports={reports.slice(0, 6)} projects={projects} />
        </Panel>
      </div>
    </div>
  );
}

function ProjectsView({
  loading,
  projects,
  selectedProjectId,
  canManageProjects,
  onCreateProject,
  onDeleteProject,
  onSelectProject
}: {
  loading: boolean;
  projects: Project[];
  selectedProjectId: number | null;
  canManageProjects: boolean;
  onCreateProject: (values: ProjectFormValues) => Promise<void>;
  onDeleteProject: (projectId: number) => Promise<void>;
  onSelectProject: (projectId: number) => void;
}) {
  const [creating, setCreating] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<Project | null>(null);

  async function create(values: ProjectFormValues) {
    await onCreateProject(values);
    setCreating(false);
  }

  return (
    <div className="view-stack">
      <ViewHeader
        title="프로젝트"
        text="프로젝트를 선택하면 해당 프로젝트의 현장 목록으로 이동합니다."
      />
      <Panel
        title="프로젝트 목록"
        action={canManageProjects ? (
          <button className="primary-button" disabled={loading} onClick={() => setCreating(true)} type="button">
            <Plus size={17} />
            프로젝트 생성
          </button>
        ) : <span className="panel-context">생성 권한 없음</span>}
      >
        <ProjectList
          canDelete={canManageProjects}
          deleting={loading}
          projects={projects}
          selectedProjectId={selectedProjectId}
          onDeleteProject={(project) => setDeleteTarget(project)}
          onSelectProject={onSelectProject}
        />
      </Panel>

      {creating ? (
        <ModalShell title="프로젝트 생성" onClose={() => setCreating(false)}>
          <ProjectForm busy={loading} onSubmit={create} />
        </ModalShell>
      ) : null}
      {deleteTarget ? (
        <ConfirmDeleteDialog
          busy={loading}
          title="프로젝트 삭제"
          targetName={deleteTarget.name}
          warning="프로젝트에 연결된 현장, 리포트, 사진, 문서 생성 이력이 모두 삭제됩니다."
          onCancel={() => setDeleteTarget(null)}
          onConfirm={async () => {
            await onDeleteProject(deleteTarget.id);
            setDeleteTarget(null);
          }}
        />
      ) : null}
    </div>
  );
}

function SitesView({
  loadingSites,
  selectedProject,
  selectedSiteId,
  sites,
  canManageSites,
  onCreateSite,
  onDeleteSite,
  onBackToProjects,
  onSelectSite
}: {
  loadingSites: boolean;
  selectedProject: Project | null;
  selectedSiteId: number | null;
  sites: Site[];
  canManageSites: boolean;
  onCreateSite: (values: SiteFormValues) => Promise<void>;
  onDeleteSite: (siteId: number) => Promise<void>;
  onBackToProjects: () => void;
  onSelectSite: (siteId: number) => void;
}) {
  const [creating, setCreating] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<Site | null>(null);
  const projectSites = selectedProject ? sites.filter((site) => site.projectId === selectedProject.id) : [];

  async function create(values: SiteFormValues) {
    await onCreateSite(values);
    setCreating(false);
  }

  return (
    <div className="view-stack">
      <ViewHeader
        title="현장"
        text="프로젝트 안의 현장을 선택하면 해당 현장의 리포트 목록으로 이동합니다."
      />
      <Panel
        title="현장 목록"
        action={selectedProject && canManageSites ? (
          <button className="primary-button" disabled={loadingSites} onClick={() => setCreating(true)} type="button">
            <Plus size={17} />
            현장 생성
          </button>
        ) : <span className="panel-context">{selectedProject ? "생성 권한 없음" : "프로젝트 선택 필요"}</span>}
      >
        {selectedProject ? (
          <>
            <div className="panel-body compact-context-row">
              <div className="flow-context">
                <strong>{selectedProject.name}</strong>
                <span>{selectedProject.address || optionLabel(projectBusinessTypeOptions, selectedProject.buildingType) || "프로젝트 정보 없음"}</span>
              </div>
              <button className="secondary-button compact-button" onClick={onBackToProjects} type="button">
                프로젝트 다시 선택
              </button>
            </div>
            <SiteList
              canDelete={canManageSites}
              deleting={loadingSites}
              sites={projectSites}
              selectedSiteId={selectedSiteId}
              onDeleteSite={(site) => setDeleteTarget(site)}
              onSelectSite={onSelectSite}
            />
          </>
        ) : (
          <EmptyState title="프로젝트를 먼저 선택하세요" text="프로젝트 탭에서 프로젝트를 선택하면 현장 목록이 열립니다." />
        )}
      </Panel>

      {creating && selectedProject ? (
        <ModalShell title="현장 생성" onClose={() => setCreating(false)}>
          <SiteForm busy={loadingSites} onSubmit={create} />
        </ModalShell>
      ) : null}
      {deleteTarget ? (
        <ConfirmDeleteDialog
          busy={loadingSites}
          title="현장 삭제"
          targetName={deleteTarget.name}
          warning="현장에 연결된 리포트, 점검 대상, 사진, 문서 생성 이력이 모두 삭제됩니다."
          onCancel={() => setDeleteTarget(null)}
          onConfirm={async () => {
            await onDeleteSite(deleteTarget.id);
            setDeleteTarget(null);
          }}
        />
      ) : null}
    </div>
  );
}

function ReportsView({
  documentTypes,
  loading,
  officeId,
  projects,
  reports,
  selectedProject,
  selectedReport,
  selectedSite,
  selectedSiteId,
  sites,
  targets,
  token,
  canManageAssignments,
  canWriteReports,
  officeMembers,
  onCreateReport,
  onDeleteReport,
  onBackToSites,
  onCloseReport,
  onReopenReport,
  onSaveStep,
  onSelectReport,
  onSubmitReport
}: {
  documentTypes: DocumentTypeDefinition[];
  loading: boolean;
  officeId: number | null;
  projects: Project[];
  reports: InspectionReport[];
  selectedProject: Project | null;
  selectedReport: InspectionReport | null;
  selectedSite: Site | null;
  selectedSiteId: number | null;
  sites: Site[];
  targets: InspectionTarget[];
  token: string;
  canManageAssignments: boolean;
  canWriteReports: boolean;
  officeMembers: OfficeMember[];
  onCreateReport: (values: ReportFormValues) => Promise<void>;
  onDeleteReport: (reportId: number) => Promise<void>;
  onBackToSites: () => void;
  onCloseReport: () => void;
  onReopenReport: (reportId: number) => Promise<void>;
  onSaveStep: (reportId: number, stepCode: string, payload: Record<string, unknown>) => Promise<InspectionStep>;
  onSelectReport: (reportId: number) => void;
  onSubmitReport: (reportId: number) => Promise<void>;
}) {
  const [creating, setCreating] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<InspectionReport | null>(null);
  const reportProject = selectedReport
    ? projects.find((project) => project.id === selectedReport.projectId) ?? null
    : null;
  const reportSite = selectedReport?.siteId
    ? sites.find((site) => site.id === selectedReport.siteId) ?? null
    : null;
  const activeProject = selectedReport ? reportProject : selectedProject;
  const activeSite = selectedReport ? reportSite : selectedSite;
  const scopedReports = selectedProject
    ? reports.filter((report) => report.projectId === selectedProject.id && (!selectedSiteId || report.siteId === selectedSiteId))
    : selectedSiteId
      ? reports.filter((report) => report.siteId === selectedSiteId)
      : reports;

  async function create(values: ReportFormValues) {
    await onCreateReport(values);
    setCreating(false);
  }

  return (
    <div className="view-stack">
      <ViewHeader title={selectedReport ? "리포트 작성" : "리포트"} text={selectedReport ? "선택한 리포트를 단계별로 작성하고 제출합니다." : "리포트를 선택하면 작성 화면으로 이동합니다."} />
      {selectedReport && officeId ? (
        <div className="report-detail-stack">
          <Panel
            title="리포트 담당자"
            action={
              <div className="panel-action-group">
                <button className="secondary-button compact-button" onClick={onCloseReport} type="button">
                  이전 목록으로
                </button>
              </div>
            }
          >
            <ReportAssignmentPanel
              canManage={canManageAssignments}
              members={officeMembers}
              officeId={officeId}
              report={selectedReport}
              token={token}
            />
          </Panel>
          <ReportWizard
            officeId={officeId}
            project={reportProject}
            report={selectedReport}
            site={reportSite}
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
        </div>
      ) : (
        <Panel
          title="리포트 목록"
          action={!selectedProject ? (
            <span className="panel-context">프로젝트 선택 필요</span>
          ) : !selectedSite ? (
            <button className="secondary-button compact-button" onClick={onBackToSites} type="button">
              현장 선택
            </button>
          ) : canWriteReports ? (
            <button className="primary-button" disabled={loading} onClick={() => setCreating(true)} type="button">
              <Plus size={17} />
              리포트 생성
            </button>
          ) : (
            <span className="panel-context">생성 권한 없음</span>
          )}
        >
          {!selectedProject ? (
            <EmptyState
              title="프로젝트를 먼저 선택하세요"
              text="프로젝트 탭에서 프로젝트를 선택하면 현장 목록이 열립니다."
            />
          ) : !selectedSite ? (
            <EmptyState
              title="현장을 먼저 선택하세요"
              text="현장 탭에서 현장을 선택하면 해당 현장의 리포트 목록이 열립니다."
            />
          ) : (
            <>
              <div className="panel-body compact-context-row">
                <div className="flow-context">
                  <strong>{selectedSite.name}</strong>
                  <span>{selectedProject.name}</span>
                </div>
                <button className="secondary-button compact-button" onClick={onBackToSites} type="button">
                  현장 다시 선택
                </button>
              </div>
              <ReportList
                canDelete={canWriteReports}
                canDeleteReport={(report) => Boolean(report.writeAllowed ?? canWriteReports)}
                deleting={loading}
                projects={projects}
                reports={scopedReports}
                selectedReportId={null}
                sites={sites}
                onDeleteReport={(report) => setDeleteTarget(report)}
                onSelectReport={onSelectReport}
              />
            </>
          )}
        </Panel>
      )}

      {creating && activeProject && activeSite ? (
        <ModalShell title="리포트 생성" onClose={() => setCreating(false)}>
          <ReportStartForm
            busy={loading}
            documentTypes={documentTypes}
            projects={projects}
            selectedProjectId={activeProject.id}
            selectedSiteId={activeSite.id}
            sites={sites}
            onSubmit={create}
          />
        </ModalShell>
      ) : null}
      {deleteTarget ? (
        <ConfirmDeleteDialog
          busy={loading}
          title="리포트 삭제"
          targetName={deleteTarget.title || deleteTarget.reportNo}
          warning="리포트에 연결된 작성 단계, 체크리스트, 사진, 문서 생성 이력이 모두 삭제됩니다."
          onCancel={() => setDeleteTarget(null)}
          onConfirm={async () => {
            await onDeleteReport(deleteTarget.id);
            setDeleteTarget(null);
          }}
        />
      ) : null}
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

function InsightChatView({ office }: { office: Office | null }) {
  const examples = [
    "최근 2년간 지적사항이 많았던 현장 알려줘",
    "우리 사무소 감리 실적 요약해줘",
    "사진 증거가 부족했던 리포트 찾아줘",
    "반복되는 안전 지적사항 유형 정리해줘"
  ];
  return (
    <div className="view-stack insight-chat-view">
      <ViewHeader
        title="분석 채팅"
        text={`${office?.displayName ?? "현재 사무소"}의 프로젝트, 현장, 리포트, 사진, 문서 데이터를 바탕으로 묻는 사무소 분석 채팅입니다.`}
      />
      <Panel title="사무소 데이터 분석">
        <div className="insight-chat-shell">
          <div className="insight-chat-intro">
            <div className="row-icon">
              <BarChart3 size={19} />
            </div>
            <div>
              <strong>분석 채팅은 작업 채팅과 분리됩니다.</strong>
              <p>
                작업 채팅은 특정 리포트 작성과 문서 생성을 돕고, 분석 채팅은 사무소 전체 데이터를 조회하고 요약하는 용도입니다.
                질문 원문을 길게 보관하기보다 참조 데이터와 요약 결과 중심으로 남기는 방향으로 확장합니다.
              </p>
            </div>
          </div>
          <div className="insight-chat-retention">
            <strong>임시 채팅</strong>
            <span>
              분석 채팅 원문은 장기 보관하지 않는 방향입니다. 실제 기능이 연결되면 화면을 나가거나 새로고침하기 전에 확인창을 띄우고,
              필요한 경우 질문 요약, 참조한 데이터, 분석 결과만 운영 기록으로 남깁니다.
            </span>
          </div>
          <div className="insight-chat-example-grid">
            {examples.map((example) => (
              <button disabled key={example} type="button">
                {example}
              </button>
            ))}
          </div>
          <form className="insight-chat-composer">
            <textarea disabled rows={2} placeholder="분석 Worker 연결 후 사용할 수 있습니다." />
            <button disabled type="button" className="worker-chat-send-button">
              <MessageSquare size={17} />
            </button>
          </form>
        </div>
      </Panel>
    </div>
  );
}

function MoreView({
  user,
  onLogout,
  onOpenLegalUpdates
}: {
  user: MeResponse;
  onLogout: () => void;
  onOpenLegalUpdates: () => void;
}) {
  return (
    <div className="view-stack">
      <ViewHeader title="프로필" text="계정 정보와 기본 설정을 확인합니다." />
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
      <Panel title="플랫폼 정보">
        <div className="settings-list">
          <div>
            <strong>법령 변경사항</strong>
            <span>최근 법령 변경과 업무 영향 요약을 확인합니다.</span>
          </div>
          <button className="secondary-button" onClick={onOpenLegalUpdates} type="button">
            <Bell size={17} />
            열기
          </button>
        </div>
      </Panel>
    </div>
  );
}

function ModalShell({
  children,
  onClose,
  title
}: {
  children: ReactNode;
  onClose: () => void;
  title: string;
}) {
  return (
    <div className="modal-backdrop" role="presentation">
      <section className="modal-panel" role="dialog" aria-modal="true" aria-label={title}>
        <header className="modal-header">
          <strong>{title}</strong>
          <button className="icon-button" onClick={onClose} type="button" aria-label="닫기">
            ×
          </button>
        </header>
        <div className="modal-body">{children}</div>
      </section>
    </div>
  );
}

function ConfirmDeleteDialog({
  busy,
  onCancel,
  onConfirm,
  targetName,
  title,
  warning
}: {
  busy: boolean;
  onCancel: () => void;
  onConfirm: () => Promise<void>;
  targetName: string;
  title: string;
  warning: string;
}) {
  return (
    <ModalShell title={title} onClose={busy ? () => undefined : onCancel}>
      <div className="delete-confirm">
        <div className="row-icon danger">
          <Trash2 size={18} />
        </div>
        <div>
          <strong>{targetName}</strong>
          <p>{warning}</p>
          <span>이 작업은 되돌릴 수 없습니다.</span>
        </div>
      </div>
      <footer className="modal-actions">
        <button className="secondary-button" disabled={busy} onClick={onCancel} type="button">
          취소
        </button>
        <button className="danger-button" disabled={busy} onClick={onConfirm} type="button">
          {busy ? <Loader2 className="spin" size={17} /> : <Trash2 size={17} />}
          삭제
        </button>
      </footer>
    </ModalShell>
  );
}

function ProjectList({
  canDelete = false,
  deleting = false,
  projects,
  selectedProjectId,
  onDeleteProject,
  onSelectProject
}: {
  canDelete?: boolean;
  deleting?: boolean;
  projects: Project[];
  selectedProjectId?: number | null;
  onDeleteProject?: (project: Project) => void;
  onSelectProject?: (projectId: number) => void;
}) {
  if (projects.length === 0) {
    return <EmptyState title="프로젝트가 없습니다" text="첫 프로젝트를 만들면 리포트 작성 흐름을 시작할 수 있습니다." />;
  }
  return (
    <div className="item-list">
      {projects.map((project) => (
        <article
          className={selectedProjectId === project.id ? "list-row list-row-shell selectable active" : "list-row list-row-shell selectable"}
          key={project.id}
        >
          <button className="list-row-main" onClick={() => onSelectProject?.(project.id)} type="button">
            <div className="row-icon">
              <ClipboardList size={18} />
            </div>
            <div>
              <strong>{project.name}</strong>
              <span>{project.address || optionLabel(projectBusinessTypeOptions, project.buildingType) || "프로젝트 정보 없음"}</span>
            </div>
            <StatusBadge status={project.status} />
          </button>
          {canDelete ? (
            <button
              className="icon-button danger"
              disabled={deleting}
              onClick={() => onDeleteProject?.(project)}
              title="프로젝트 삭제"
              type="button"
              aria-label={`${project.name} 프로젝트 삭제`}
            >
              <Trash2 size={16} />
            </button>
          ) : null}
        </article>
      ))}
    </div>
  );
}

function SiteList({
  canDelete = false,
  deleting = false,
  sites,
  selectedSiteId,
  onDeleteSite,
  onSelectSite
}: {
  canDelete?: boolean;
  deleting?: boolean;
  sites: Site[];
  selectedSiteId?: number | null;
  onDeleteSite?: (site: Site) => void;
  onSelectSite: (siteId: number) => void;
}) {
  if (sites.length === 0) {
    return <EmptyState title="현장이 없습니다" text="이 프로젝트에서 실제 점검할 현장을 먼저 만드세요." />;
  }
  return (
    <div className="item-list">
      {sites.map((site) => (
        <article
          className={selectedSiteId === site.id ? "list-row list-row-shell selectable active" : "list-row list-row-shell selectable"}
          key={site.id}
        >
          <button className="list-row-main" onClick={() => onSelectSite(site.id)} type="button">
            <div className="row-icon">
              <MapPin size={18} />
            </div>
            <div>
              <strong>{site.name}</strong>
              <span>{site.address || optionLabel(siteTypeOptions, site.siteType) || site.siteCode || "현장 정보 없음"}</span>
            </div>
            <StatusBadge status={site.status} />
          </button>
          {canDelete ? (
            <button
              className="icon-button danger"
              disabled={deleting}
              onClick={() => onDeleteSite?.(site)}
              title="현장 삭제"
              type="button"
              aria-label={`${site.name} 현장 삭제`}
            >
              <Trash2 size={16} />
            </button>
          ) : null}
        </article>
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
          placeholder="예: 2026 상반기 공사감리"
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
