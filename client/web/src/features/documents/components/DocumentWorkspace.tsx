import { useEffect, useMemo, useRef, useState } from "react";
import type { PointerEvent } from "react";
import { AlertTriangle, ArrowLeft, CheckCircle2, ChevronRight, Download, Eye, FileClock, FileText, Loader2, PenLine, RefreshCw, ShieldCheck, Sparkles, Trash2, UploadCloud, X } from "lucide-react";
import {
  EmptyState,
  InlineAlert,
  Panel,
  StatusBadge,
  reportTypeLabel,
  statusLabel,
  ViewHeader
} from "../../../components/common";
import { useDocumentWorkspace } from "../hooks/useDocumentWorkspace";
import type { MeResponse, Office } from "../../../types";
import type {
  DocumentArtifactResponse,
  DocumentDeliveryRequestResponse,
  DocumentJobResponse,
  DocumentNarrativeApplyResponse,
  DocumentNarrativePolishFieldInput,
  DocumentNarrativePolishResponse,
  DocumentNarrativePolishSuggestionResponse,
  DocumentOutputFormat,
  DocumentRenderOverrideInput,
  DocumentSignatureInput,
  InspectionReport,
  InspectionStep,
  Project,
  ReportPreflightLegalReferenceResponse,
  ReportPreflightFindingResolutionStatus,
  ReportPreflightReviewFindingResponse,
  ReportPreflightReviewRunResponse
} from "../types";

type DocumentWorkspaceProps = {
  currentOffice: Office | null;
  currentUser: MeResponse;
  officeId: number | null;
  onRefreshWorkspace: () => Promise<void>;
  projects: Project[];
  reports: InspectionReport[];
  token: string;
};

type NarrativeRenderField = {
  label: string;
  path: string;
  value: string;
};

type LegalReferenceDisplayItem = {
  key: string;
  label: string;
  detail?: ReportPreflightLegalReferenceResponse;
};

type LegalReferenceDisplayGroup = {
  key: string;
  title: string;
  description: string;
  items: LegalReferenceDisplayItem[];
};

const EMPTY_NARRATIVE_VALUES: Record<string, string> = {};

export function DocumentWorkspace({
  currentOffice,
  currentUser,
  officeId,
  onRefreshWorkspace,
  projects,
  reports,
  token
}: DocumentWorkspaceProps) {
  const workspace = useDocumentWorkspace({ officeId, onRefreshWorkspace, reports, token });
  const [signatureRequest, setSignatureRequest] = useState<{
    outputFormat: DocumentOutputFormat;
    report: InspectionReport;
  } | null>(null);
  const [signatureError, setSignatureError] = useState<string | null>(null);
  const [narrativeDraftsByReport, setNarrativeDraftsByReport] = useState<Record<number, Record<string, string>>>({});
  const [selectedDocumentReportId, setSelectedDocumentReportId] = useState<number | null>(null);
  const documentReportIds = workspace.documentReports.map((report) => report.id).join(",");
  const documentContexts = workspace.documentReports.map((report) => {
    const project = projects.find((item) => item.id === report.projectId);
    const jobs = workspace.jobsByReport[report.id] ?? [];
    const preflightRun = workspace.preflightRunsByReport[report.id]?.[0] ?? null;
    const preflightFindings = preflightRun ? workspace.preflightFindingsByRun[preflightRun.id] ?? [] : [];
    return {
      jobs,
      preflightFindings,
      preflightRun,
      projectName: project?.name,
      report
    };
  });
  const selectedDocumentContext = selectedDocumentReportId
    ? documentContexts.find((context) => context.report.id === selectedDocumentReportId) ?? null
    : null;

  const requestSignedGeneration = (report: InspectionReport, outputFormat: DocumentOutputFormat) => {
    setSignatureError(null);
    setSignatureRequest({ outputFormat, report });
  };
  const rememberNarrativeDraft = (reportId: number, values: Record<string, string>) => {
    setNarrativeDraftsByReport((current) => ({
      ...current,
      [reportId]: values
    }));
  };

  useEffect(() => {
    if (selectedDocumentReportId && !workspace.documentReports.some((report) => report.id === selectedDocumentReportId)) {
      setSelectedDocumentReportId(null);
    }
  }, [documentReportIds, selectedDocumentReportId, workspace.documentReports]);

  const submitSignature = async (signature: DocumentSignatureInput, renderOverrides: DocumentRenderOverrideInput[]) => {
    if (!signatureRequest) {
      return;
    }
    const reportId = signatureRequest.report.id;
    try {
      await workspace.createDocumentJob({
        outputFormat: signatureRequest.outputFormat,
        renderOverrides,
        reportId,
        signature
      });
      setSignatureRequest(null);
      setSignatureError(null);
    } catch (error) {
      setSignatureError(error instanceof Error ? error.message : "문서 생성 요청에 실패했습니다.");
    }
  };
  const submitWithoutSignature = async (renderOverrides: DocumentRenderOverrideInput[]) => {
    if (!signatureRequest) {
      return;
    }
    const reportId = signatureRequest.report.id;
    try {
      await workspace.createDocumentJob({
        outputFormat: signatureRequest.outputFormat,
        renderOverrides,
        reportId
      });
      setSignatureRequest(null);
      setSignatureError(null);
    } catch (error) {
      setSignatureError(error instanceof Error ? error.message : "문서 생성 요청에 실패했습니다.");
    }
  };

  return (
    <div className="view-stack">
      <ViewHeader
        title="문서"
        text="문서별 생성 상태를 확인하고, 필요한 산출물만 생성하거나 다운로드합니다."
      />

      {workspace.error ? (
        <InlineAlert message={workspace.error instanceof Error ? workspace.error.message : "문서 작업을 불러오지 못했습니다."} />
      ) : null}

      <div className="document-workspace">
        <Panel
          title={selectedDocumentContext ? "문서 생성 상세" : "생성 가능한 문서"}
          action={
            <button className="text-button" onClick={() => workspace.refreshJobs()} type="button">
              {workspace.loading ? <Loader2 className="spin" size={16} /> : <RefreshCw size={16} />}
              새로고침
            </button>
          }
        >
          {workspace.documentReports.length === 0 ? (
            <EmptyState title="문서 작업 대상이 없습니다" text="리포트를 작성하고 제출하면 문서 탭에 표시됩니다." />
          ) : selectedDocumentContext ? (
            <div className="document-detail-view">
              <div className="document-detail-toolbar">
                <button className="secondary-button compact-button" onClick={() => setSelectedDocumentReportId(null)} type="button">
                  <ArrowLeft size={16} />
                  목록으로
                </button>
                <div>
                  <strong>{selectedDocumentContext.report.title || selectedDocumentContext.report.reportNo}</strong>
                  <span>{selectedDocumentContext.projectName ?? `project #${selectedDocumentContext.report.projectId}`} / {reportTypeLabel(selectedDocumentContext.report.reportType)}</span>
                </div>
              </div>
              <DocumentReportCard
                creating={workspace.creatingReportId === selectedDocumentContext.report.id}
                creatingOutputFormat={workspace.creatingOutputFormat}
                deliveriesByArtifact={workspace.deliveriesByArtifact}
                downloadingArtifactId={workspace.downloadingArtifactId}
                jobs={selectedDocumentContext.jobs}
                preflightFindings={selectedDocumentContext.preflightFindings}
                preflightRun={selectedDocumentContext.preflightRun}
                previewingArtifactId={workspace.previewingArtifactId}
                projectName={selectedDocumentContext.projectName}
                report={selectedDocumentContext.report}
                requestingDeliveryArtifactId={workspace.requestingDeliveryArtifactId}
                reviewing={workspace.reviewingReportId === selectedDocumentContext.report.id}
                applyingPreflightFindingId={workspace.applyingPreflightFindingId}
                resolvingPreflightFindingId={workspace.resolvingPreflightFindingId}
                onCreate={() => requestSignedGeneration(selectedDocumentContext.report, "DOCX")}
                onCreatePdf={() => requestSignedGeneration(selectedDocumentContext.report, "PDF")}
                onCreatePreview={() => requestSignedGeneration(selectedDocumentContext.report, "HTML")}
                onDownloadPrepared={(artifact, delivery) => workspace.downloadPreparedArtifact({ artifact, delivery })}
                onPreviewArtifact={(artifact, job) => workspace.previewArtifact({ artifact, job })}
                onRequestPreflightReview={() => workspace.requestPreflightReview(selectedDocumentContext.report.id)}
                onApplyPreflightFindingFix={(runId, findingId) =>
                  workspace.applyPreflightFindingFix({ reportId: selectedDocumentContext.report.id, runId, findingId })
                }
                onResolvePreflightFinding={(runId, findingId, status) =>
                  workspace.resolvePreflightFinding({ reportId: selectedDocumentContext.report.id, runId, findingId, status })
                }
                onRequestDelivery={(artifact, job) => workspace.requestArtifactDelivery({ artifact, job })}
              />
            </div>
          ) : (
            <div className="document-simple-list">
              {documentContexts.map((context) => (
                <DocumentReportListItem
                  jobs={context.jobs}
                  key={context.report.id}
                  preflightRun={context.preflightRun}
                  preflightRunLoading={workspace.preflightRunsLoading}
                  projectName={context.projectName}
                  report={context.report}
                  onOpen={() => setSelectedDocumentReportId(context.report.id)}
                />
              ))}
            </div>
          )}
        </Panel>
      </div>
      {workspace.preview ? (
        <DocumentPreviewDialog preview={workspace.preview} onClose={workspace.closePreview} />
      ) : null}
      {signatureRequest ? (
        <DocumentSignatureDialog
          currentOffice={currentOffice}
          currentUser={currentUser}
          error={signatureError}
          applyingNarrative={workspace.applyingNarrativeReportId === signatureRequest.report.id}
          initialNarrativeValues={narrativeDraftsByReport[signatureRequest.report.id] ?? EMPTY_NARRATIVE_VALUES}
          outputFormat={signatureRequest.outputFormat}
          report={signatureRequest.report}
          polishing={workspace.polishingNarrativeReportId === signatureRequest.report.id}
          steps={workspace.stepsByReport[signatureRequest.report.id] ?? []}
          submitting={workspace.creatingReportId === signatureRequest.report.id}
          onClose={() => {
            if (!workspace.creatingReportId) {
              setSignatureRequest(null);
              setSignatureError(null);
            }
          }}
          onSkip={submitWithoutSignature}
          onPolishNarrative={(fields) => workspace.polishDocumentNarrative({
            fields,
            reportId: signatureRequest.report.id
          })}
          onApplyNarrativeToReport={(fields) => workspace.applyDocumentNarrativeToReport({
            fields,
            reportId: signatureRequest.report.id
          })}
          onRememberNarrativeValues={(values) => rememberNarrativeDraft(signatureRequest.report.id, values)}
          onSubmit={submitSignature}
        />
      ) : null}
    </div>
  );
}

function DocumentReportListItem({
  jobs,
  preflightRun,
  preflightRunLoading,
  projectName,
  report,
  onOpen
}: {
  jobs: DocumentJobResponse[];
  preflightRun: ReportPreflightReviewRunResponse | null;
  preflightRunLoading: boolean;
  projectName?: string;
  report: InspectionReport;
  onOpen: () => void;
}) {
  const latestJob = jobs[0] ?? null;
  const generatedCount = jobs.filter((job) => job.status === "GENERATED").length;
  const status = documentListStatus(report, latestJob, preflightRun, preflightRunLoading);
  return (
    <button className="document-simple-row" onClick={onOpen} type="button">
      <span className="document-simple-icon">
        <FileText size={18} />
      </span>
      <span className="document-simple-main">
        <strong>{report.title || report.reportNo}</strong>
        <small>{projectName ?? `project #${report.projectId}`} / {reportTypeLabel(report.reportType)}</small>
      </span>
      <span className="document-simple-meta">
        <span className={`status-badge ${status.tone}`}>{status.label}</span>
        <small>{status.detail}</small>
      </span>
      <span className="document-simple-meta">
        <strong>{latestJob ? statusLabel(latestJob.status) : statusLabel(report.status)}</strong>
        <small>{generatedCount > 0 ? `완료 파일 ${generatedCount}건` : "생성 이력 없음"}</small>
      </span>
      <ChevronRight className="document-simple-arrow" size={18} />
    </button>
  );
}

function DocumentReportCard({
  creating,
  creatingOutputFormat,
  deliveriesByArtifact,
  downloadingArtifactId,
  jobs,
  preflightFindings,
  preflightRun,
  projectName,
  report,
  previewingArtifactId,
  requestingDeliveryArtifactId,
  reviewing,
  applyingPreflightFindingId,
  resolvingPreflightFindingId,
  onCreate,
  onCreatePdf,
  onCreatePreview,
  onDownloadPrepared,
  onPreviewArtifact,
  onRequestPreflightReview,
  onApplyPreflightFindingFix,
  onResolvePreflightFinding,
  onRequestDelivery
}: {
  creating: boolean;
  creatingOutputFormat: string | null;
  deliveriesByArtifact: Record<number, DocumentDeliveryRequestResponse>;
  downloadingArtifactId: number | null;
  jobs: DocumentJobResponse[];
  preflightFindings: ReportPreflightReviewFindingResponse[];
  preflightRun: ReportPreflightReviewRunResponse | null;
  projectName?: string;
  report: InspectionReport;
  previewingArtifactId: number | null;
  requestingDeliveryArtifactId: number | null;
  reviewing: boolean;
  applyingPreflightFindingId: number | null;
  resolvingPreflightFindingId: number | null;
  onCreate: () => void;
  onCreatePdf: () => void;
  onCreatePreview: () => void;
  onDownloadPrepared: (artifact: DocumentArtifactResponse, delivery: DocumentDeliveryRequestResponse) => Promise<unknown>;
  onPreviewArtifact: (artifact: DocumentArtifactResponse, job: DocumentJobResponse) => Promise<unknown>;
  onRequestPreflightReview: () => Promise<ReportPreflightReviewRunResponse>;
  onApplyPreflightFindingFix: (
    runId: number,
    findingId: number
  ) => Promise<ReportPreflightReviewFindingResponse>;
  onResolvePreflightFinding: (
    runId: number,
    findingId: number,
    status: ReportPreflightFindingResolutionStatus
  ) => Promise<ReportPreflightReviewFindingResponse>;
  onRequestDelivery: (artifact: DocumentArtifactResponse, job: DocumentJobResponse) => Promise<unknown>;
}) {
  const latestJob = jobs[0] ?? null;
  const activeJob = jobs.find((job) => ["REQUESTED", "GENERATING"].includes(job.status)) ?? null;
  const generatedJobs = jobs.filter((job) => job.status === "GENERATED");
  const latestGeneratedJob = generatedJobs[0] ?? null;
  const active = Boolean(activeJob);
  const preflightActive = preflightRun ? isPreflightActive(preflightRun) : false;
  const preflightBlocking = preflightRun ? isPreflightBlocking(preflightRun) : false;
  const preflightCurrent = preflightRun ? preflightRun.reportRevision === report.contentRevision : false;
  const preflightReady = Boolean(preflightRun && preflightCurrent && preflightRun.status === "PASSED" && !isPreflightAiPending(preflightRun));
  const canCreate = ["READY_TO_GENERATE", "GENERATED", "FAILED"].includes(report.status)
    && !active
    && preflightReady
    && !preflightActive
    && !preflightBlocking;
  const action = documentAction(report, latestJob, active);
  const actionHint = preflightReady ? action.hint : preflightGateHint(preflightRun, report);
  const creatingDocx = creating && (creatingOutputFormat === null || creatingOutputFormat === "DOCX");
  const creatingHtml = creating && creatingOutputFormat === "HTML";
  const creatingPdf = creating && creatingOutputFormat === "PDF";
  const preflightStatus = preflightStatusLabel(preflightRun, Boolean(preflightRun && !preflightCurrent));
  const preflightBusy = reviewing || preflightActive;
  const canReview = ["READY_TO_GENERATE", "GENERATED", "FAILED", "STEP_SAVED"].includes(report.status);
  const generatedArtifactCount = latestGeneratedJob?.artifacts.length ?? 0;

  return (
    <article className="document-card">
      <div className="document-card-head">
        <div className="row-icon blue">
          <FileText size={18} />
        </div>
        <div>
          <strong>{report.title || report.reportNo}</strong>
          <span>{projectName ?? `project #${report.projectId}`} / {reportTypeLabel(report.reportType)}</span>
        </div>
        <StatusBadge status={latestJob?.status ?? report.status} />
      </div>

      {latestJob ? <JobProgress job={latestJob} /> : null}

      <div className="document-card-summary">
        <div className="document-summary-item">
          <span>문서 상태</span>
          <strong>{documentStatusSummary(latestJob, report)}</strong>
        </div>
        <div className="document-summary-item">
          <span>검토 상태</span>
          <strong>{preflightStatus}</strong>
        </div>
        <div className="document-summary-item">
          <span>완료 파일</span>
          <strong>{generatedArtifactCount > 0 ? `${generatedArtifactCount}개` : "-"}</strong>
        </div>
      </div>

      <InlineDocumentGuide report={report} latestGeneratedJob={latestGeneratedJob} />

      <details className="document-detail-section">
        <summary>
          <span>
            <strong>생성 전 검토</strong>
            <small>{preflightGateHint(preflightRun, report)}</small>
            {preflightBusy ? <PreflightInlineProgress run={preflightRun} reviewing={reviewing} /> : null}
          </span>
          <em>{preflightStatus}</em>
        </summary>
        <PreflightReviewPanel
          findings={preflightFindings}
          currentRevision={report.contentRevision}
          report={report}
          reviewing={reviewing || preflightActive}
          applyingFindingId={applyingPreflightFindingId}
          resolvingFindingId={resolvingPreflightFindingId}
          run={preflightRun}
          onApplyFindingFix={onApplyPreflightFindingFix}
          onResolveFinding={onResolvePreflightFinding}
        />
      </details>

      {latestGeneratedJob ? (
        <GeneratedJobArtifacts
          deliveriesByArtifact={deliveriesByArtifact}
          downloadingArtifactId={downloadingArtifactId}
          isLatest
          job={latestGeneratedJob}
          previewingArtifactId={previewingArtifactId}
          requestingDeliveryArtifactId={requestingDeliveryArtifactId}
          onDownloadPrepared={onDownloadPrepared}
          onPreviewArtifact={onPreviewArtifact}
          onRequestDelivery={onRequestDelivery}
        />
      ) : null}

      {latestGeneratedJob && generatedJobs.length === 1 ? (
        <p className="document-muted">
          아직 이전 생성본은 없습니다. 수정 후 다시 생성하면 기존 완료 문서는 이전본으로 남습니다.
        </p>
      ) : null}

      {generatedJobs.length > 1 ? (
        <details className="document-detail-section document-history" open={false}>
          <summary>
            <span>
              <strong>이전 생성본</strong>
              <small>{generatedJobs.length - 1}개 revision/job</small>
            </span>
            <em>펼쳐보기</em>
          </summary>
          {generatedJobs.slice(1).map((job) => (
            <GeneratedJobArtifacts
              deliveriesByArtifact={deliveriesByArtifact}
              downloadingArtifactId={downloadingArtifactId}
              isLatest={false}
              job={job}
              key={job.id}
              previewingArtifactId={previewingArtifactId}
              requestingDeliveryArtifactId={requestingDeliveryArtifactId}
              onDownloadPrepared={onDownloadPrepared}
              onPreviewArtifact={onPreviewArtifact}
              onRequestDelivery={onRequestDelivery}
            />
          ))}
        </details>
      ) : null}

      <details className="document-detail-section">
        <summary>
          <span>
            <strong>상세 정보</strong>
            <small>revision과 최근 job 상태를 확인합니다.</small>
          </span>
          <em>v{report.contentRevision}</em>
        </summary>
        <RevisionStrip report={report} latestJob={latestJob} latestGeneratedJob={latestGeneratedJob} />
        {!latestJob ? <p className="document-muted">아직 문서 생성 요청이 없습니다.</p> : null}
      </details>

      <div className="document-actions">
        <span className="document-action-hint">{actionHint}</span>
        <div className="document-action-buttons">
          <button className="primary-button document-review-action" disabled={!canReview || reviewing} onClick={onRequestPreflightReview} type="button">
            {reviewing ? <Loader2 className="spin" size={17} /> : <ShieldCheck size={17} />}
            생성 전 검토
          </button>
          <button className="secondary-button" disabled={!canCreate || creating} onClick={onCreatePreview} type="button">
            {creatingHtml ? <Loader2 className="spin" size={17} /> : <Eye size={17} />}
            HTML 생성
          </button>
          <button className="secondary-button" disabled={!canCreate || creating} onClick={onCreatePdf} type="button">
            {creatingPdf ? <Loader2 className="spin" size={17} /> : <FileText size={17} />}
            PDF 생성
          </button>
          <button className="primary-button" disabled={!canCreate || creating} onClick={onCreate} type="button">
            {creatingDocx || active ? <Loader2 className="spin" size={17} /> : <UploadCloud size={17} />}
            {action.label}
          </button>
        </div>
      </div>
    </article>
  );
}

function DocumentSignatureDialog({
  applyingNarrative,
  currentOffice,
  currentUser,
  error,
  initialNarrativeValues,
  outputFormat,
  polishing,
  report,
  steps,
  submitting,
  onClose,
  onApplyNarrativeToReport,
  onRememberNarrativeValues,
  onPolishNarrative,
  onSkip,
  onSubmit
}: {
  applyingNarrative: boolean;
  currentOffice: Office | null;
  currentUser: MeResponse;
  error: string | null;
  initialNarrativeValues: Record<string, string>;
  outputFormat: DocumentOutputFormat;
  polishing: boolean;
  report: InspectionReport;
  steps: InspectionStep[];
  submitting: boolean;
  onClose: () => void;
  onApplyNarrativeToReport: (fields: DocumentNarrativePolishFieldInput[]) => Promise<DocumentNarrativeApplyResponse>;
  onRememberNarrativeValues: (values: Record<string, string>) => void;
  onPolishNarrative: (fields: DocumentNarrativePolishFieldInput[]) => Promise<DocumentNarrativePolishResponse>;
  onSkip: (renderOverrides: DocumentRenderOverrideInput[]) => Promise<void>;
  onSubmit: (signature: DocumentSignatureInput, renderOverrides: DocumentRenderOverrideInput[]) => Promise<void>;
}) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const drawingRef = useRef(false);
  const [empty, setEmpty] = useState(true);
  const [localError, setLocalError] = useState<string | null>(null);
  const [screen, setScreen] = useState<"generate" | "polish">(() => outputFormat === "HTML" ? "polish" : "generate");
  const [name, setName] = useState(() => localStorage.getItem("archdox.signature.name") ?? defaultSignatureName(currentUser));
  const [role, setRole] = useState(() => {
    if (currentOffice?.type === "PERSONAL") {
      return defaultSignatureRole(report, currentOffice);
    }
    return localStorage.getItem("archdox.signature.role") ?? defaultSignatureRole(report, currentOffice);
  });
  const narrativeFields = useMemo(() => documentNarrativeRenderFields(steps), [steps]);
  const [narrativeValues, setNarrativeValues] = useState<Record<string, string>>({});
  const [polishSummary, setPolishSummary] = useState<string | null>(null);
  const [polishSuggestions, setPolishSuggestions] = useState<DocumentNarrativePolishSuggestionResponse[]>([]);

  useEffect(() => {
    setNarrativeValues(Object.fromEntries(narrativeFields.map((field) => [
      field.path,
      initialNarrativeValues[field.path] ?? field.value
    ])));
    setPolishSummary(null);
    setPolishSuggestions([]);
  }, [narrativeFields, initialNarrativeValues]);

  const polishSuggestionsByPath = useMemo(() => {
    const suggestions = new Map<string, DocumentNarrativePolishSuggestionResponse>();
    polishSuggestions.forEach((suggestion) => {
      if (suggestion.applicable && suggestion.polishedText.trim()) {
        suggestions.set(suggestion.path, suggestion);
      }
    });
    return suggestions;
  }, [polishSuggestions]);

  const narrativePolishFields = () => narrativeFields
    .map((field) => ({
      label: field.label,
      path: field.path,
      value: (narrativeValues[field.path] ?? field.value).trim()
    }))
    .filter((field) => field.value.length > 0);

  const changedNarrativeFields = () => narrativeFields
    .map((field) => ({
      field,
      value: (narrativeValues[field.path] ?? field.value).trim()
    }))
    .filter(({ field, value }) => value.length > 0 && value !== field.value.trim());

  const renderOverrides = () => changedNarrativeFields()
    .map(({ field, value }) => ({
      path: field.path,
      value,
      label: field.label,
      source: "DOCUMENT_GENERATION_DIALOG"
    }));
  const applyNarrativeFields = () => changedNarrativeFields()
    .map(({ field, value }) => ({
      label: field.label,
      path: field.path,
      value
    }));
  const rememberDraft = () => onRememberNarrativeValues(narrativeValues);
  const appliedNarrativeCount = renderOverrides().length;
  const applicableSuggestionCount = polishSuggestions.filter((suggestion) =>
    suggestion.applicable && suggestion.polishedText.trim()
  ).length;
  const applicableAiSuggestionCount = polishSuggestions.filter((suggestion) =>
    suggestion.applicable && suggestion.polishedText.trim() && suggestion.source === "AI_HARNESS"
  ).length;
  const applicableRuleBasedSuggestionCount = polishSuggestions.filter((suggestion) =>
    suggestion.applicable && suggestion.polishedText.trim() && suggestion.source === "RULE_BASED"
  ).length;
  const isHtmlOutput = outputFormat === "HTML";
  const outputLabel = isHtmlOutput ? "HTML 생성" : `${outputFormat} 생성`;

  const polishNarrative = async () => {
    const fields = narrativePolishFields();
    if (fields.length === 0) {
      setLocalError("다듬을 문장이 없습니다.");
      return;
    }
    try {
      setLocalError(null);
      const response = await onPolishNarrative(fields);
      const applicable = response.suggestions.filter((suggestion) =>
        suggestion.applicable && suggestion.polishedText.trim()
      );
      setPolishSuggestions(response.suggestions);
      setPolishSummary(response.summary || (applicable.length > 0
        ? `${applicable.length}개 문장 초안을 반영했습니다.`
        : "AI가 바꿀 만한 문장을 찾지 못했습니다. 이미 정돈된 문장이거나, 사실을 보존한 채 안전하게 바꿀 근거가 부족합니다."));
      if (applicable.length > 0) {
        setNarrativeValues((current) => {
          const next = { ...current };
          applicable.forEach((suggestion) => {
            next[suggestion.path] = suggestion.polishedText;
          });
          return next;
        });
      }
    } catch (polishError) {
      setLocalError(polishError instanceof Error ? polishError.message : "문장 다듬기 AI 요청에 실패했습니다.");
    }
  };

  const applyNarrativeToReport = async () => {
    const fields = applyNarrativeFields();
    if (fields.length === 0) {
      setLocalError("원본 리포트에 적용할 변경 문장이 없습니다.");
      return;
    }
    try {
      setLocalError(null);
      const response = await onApplyNarrativeToReport(fields);
      setPolishSummary(`${response.appliedCount}개 문장을 원본 리포트에 적용했습니다. 리포트 상태에 따라 다시 제출이 필요할 수 있습니다.`);
      rememberDraft();
    } catch (applyError) {
      setLocalError(applyError instanceof Error ? applyError.message : "원본 리포트 적용에 실패했습니다.");
    }
  };

  useEffect(() => {
    if (screen !== "generate" || isHtmlOutput) {
      return;
    }
    const canvas = canvasRef.current;
    if (!canvas) {
      return;
    }
    let frame = 0;
    const resizeCanvas = () => {
      const rect = canvas.getBoundingClientRect();
      const scale = window.devicePixelRatio || 1;
      canvas.width = Math.max(1, Math.floor(rect.width * scale));
      canvas.height = Math.max(1, Math.floor(rect.height * scale));
      const context = canvas.getContext("2d");
      if (!context) {
        return;
      }
      context.scale(scale, scale);
      context.lineCap = "round";
      context.lineJoin = "round";
      context.lineWidth = 2.2;
      context.strokeStyle = "#111827";
      setEmpty(true);
    };
    const scheduleResize = () => {
      window.cancelAnimationFrame(frame);
      frame = window.requestAnimationFrame(resizeCanvas);
    };
    scheduleResize();
    window.addEventListener("resize", scheduleResize);
    return () => {
      window.cancelAnimationFrame(frame);
      window.removeEventListener("resize", scheduleResize);
    };
  }, [isHtmlOutput, screen]);

  const point = (event: PointerEvent<HTMLCanvasElement>) => {
    const rect = event.currentTarget.getBoundingClientRect();
    return {
      x: event.clientX - rect.left,
      y: event.clientY - rect.top
    };
  };
  const startDrawing = (event: PointerEvent<HTMLCanvasElement>) => {
    const context = event.currentTarget.getContext("2d");
    if (!context) {
      return;
    }
    const current = point(event);
    drawingRef.current = true;
    event.currentTarget.setPointerCapture(event.pointerId);
    context.beginPath();
    context.moveTo(current.x, current.y);
  };
  const draw = (event: PointerEvent<HTMLCanvasElement>) => {
    if (!drawingRef.current) {
      return;
    }
    const context = event.currentTarget.getContext("2d");
    if (!context) {
      return;
    }
    const current = point(event);
    context.lineTo(current.x, current.y);
    context.stroke();
    setEmpty(false);
  };
  const stopDrawing = (event: PointerEvent<HTMLCanvasElement>) => {
    drawingRef.current = false;
    if (event.currentTarget.hasPointerCapture(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId);
    }
  };
  const clearSignature = () => {
    const canvas = canvasRef.current;
    const context = canvas?.getContext("2d");
    if (!canvas || !context) {
      return;
    }
    context.clearRect(0, 0, canvas.width, canvas.height);
    setEmpty(true);
    setLocalError(null);
  };
  const submit = async () => {
    const trimmedName = name.trim();
    if (!trimmedName) {
      setLocalError("서명자 이름을 입력해주세요.");
      return;
    }
    if (empty || !canvasRef.current) {
      setLocalError("서명을 입력해주세요.");
      return;
    }
    localStorage.setItem("archdox.signature.name", trimmedName);
    localStorage.setItem("archdox.signature.role", role.trim());
    rememberDraft();
    setLocalError(null);
    await onSubmit({
      signedByName: trimmedName,
      signedByRole: role.trim() || null,
      signatureImageDataUrl: signatureDataUrl(canvasRef.current),
      signatureImageMimeType: "image/png"
    }, renderOverrides());
  };
  const submitWithoutSignature = async () => {
    rememberDraft();
    await onSkip(renderOverrides());
  };
  const discardAndCloseDialog = () => {
    onClose();
  };
  const resetNarrativeValues = () => {
    setNarrativeValues(Object.fromEntries(narrativeFields.map((field) => [
      field.path,
      initialNarrativeValues[field.path] ?? field.value
    ])));
    setPolishSummary(null);
    setPolishSuggestions([]);
    setLocalError(null);
  };
  const cancelPolishScreen = () => {
    resetNarrativeValues();
    if (isHtmlOutput) {
      onClose();
      return;
    }
    setScreen("generate");
  };
  const polishSection = (
    <section className="document-narrative-polish" aria-label="문장 다듬기">
      <div className="document-narrative-polish-head">
        <span>
          <strong>문장 다듬기</strong>
          <small>{appliedNarrativeCount > 0 ? `${appliedNarrativeCount}개 문장 생성 문서에 반영됨` : "원본 문장으로 생성"}</small>
        </span>
        <em>{outputFormat}</em>
      </div>
      <div className="document-narrative-polish-status">
        <span>
          <strong>{appliedNarrativeCount}</strong>
          <small>적용 문장</small>
        </span>
        <span>
          <strong>{narrativeFields.length}</strong>
          <small>확인 대상</small>
        </span>
        <span>
          <strong>원본 저장 선택</strong>
          <small>저장하지 않아도 이번 문서 생성에는 반영</small>
        </span>
        {outputFormat === "HTML" ? (
          <span>
            <strong>자동 미리보기</strong>
            <small>생성 완료 후 HTML 열기</small>
          </span>
        ) : null}
      </div>
      <div className="document-narrative-polish-actions">
        <p>문장 제안은 아래 생성본 문장에 먼저 반영됩니다. AI 제안과 규칙 기반 제안을 구분해 표시하며, 원본 리포트 저장은 별도 선택입니다.</p>
        <button
          className="primary-button compact-button"
          disabled={submitting || polishing || applyingNarrative}
          onClick={polishNarrative}
          type="button"
        >
          {polishing ? <Loader2 className="spin" size={14} /> : <Sparkles size={14} />}
          AI로 다듬기
        </button>
      </div>
      {polishSummary ? <p className="document-narrative-polish-summary">{polishSummary}</p> : null}
      <div className="document-narrative-polish-list">
        {narrativeFields.map((field) => {
          const suggestion = polishSuggestionsByPath.get(field.path);
          const currentValue = narrativeValues[field.path] ?? field.value;
          const changed = currentValue.trim() !== field.value.trim();
          return (
            <div className={`document-narrative-polish-item${changed ? " changed" : ""}`} key={field.path}>
              <div className="document-narrative-polish-item-head">
                <span>{field.label}</span>
                <em>{changed ? "생성본 반영" : "원문 유지"}</em>
              </div>
              <div className="document-narrative-polish-compare">
                <div className="document-narrative-polish-original">
                  <small>원본 리포트</small>
                  <p>{field.value}</p>
                </div>
                <label>
                  <small>이번 생성본 문장</small>
                  <textarea
                    value={currentValue}
                    onChange={(event) => setNarrativeValues((current) => ({
                      ...current,
                      [field.path]: event.target.value
                    }))}
                    rows={3}
                  />
                </label>
              </div>
              {suggestion ? (
                <small className="document-narrative-polish-suggestion">
                  {narrativePolishSuggestionSourceLabel(suggestion.source)} 반영됨 · {suggestion.reason || suggestion.confidence}
                </small>
              ) : null}
              {changed ? (
                <button
                  className="text-button compact-text-button"
                  onClick={() => setNarrativeValues((current) => ({
                    ...current,
                    [field.path]: field.value
                  }))}
                  type="button"
                >
                  <RefreshCw size={13} />
                  원문으로 되돌리기
                </button>
              ) : null}
            </div>
          );
        })}
      </div>
      {applicableSuggestionCount > 0 ? (
        <p className="document-narrative-polish-footnote">
          {narrativePolishSuggestionCountLabel(applicableAiSuggestionCount, applicableRuleBasedSuggestionCount)}이 생성본 문장에 반영되었습니다. 문서 생성 전 최종 문장을 한 번 확인해주세요.
        </p>
      ) : null}
    </section>
  );

  if (screen === "polish" && narrativeFields.length > 0) {
    return (
      <div className="document-preview-backdrop" role="presentation">
        <section className="document-signature-dialog document-polish-dialog" role="dialog" aria-modal="true" aria-label="문장 다듬기">
          <header className="document-preview-header">
            <div>
              <span>문장 다듬기</span>
              <strong>{report.title || report.reportNo}</strong>
              <small>다듬은 문장은 생성본에만 반영하거나, 원하면 원본 리포트에도 적용할 수 있습니다.</small>
            </div>
            <button className="icon-button" disabled={submitting || applyingNarrative} onClick={discardAndCloseDialog} type="button" aria-label="문장 다듬기 닫기">
              <X size={18} />
            </button>
          </header>
          <div className="signature-dialog-body">
            {polishSection}
            {localError || error ? <InlineAlert message={localError ?? error ?? ""} /> : null}
          </div>
          <footer className="signature-dialog-actions">
            <button className="secondary-button" disabled={submitting || applyingNarrative} onClick={cancelPolishScreen} type="button">
              취소
            </button>
            <button className="secondary-button" disabled={submitting || applyingNarrative || appliedNarrativeCount === 0} onClick={applyNarrativeToReport} type="button">
              {applyingNarrative ? <Loader2 className="spin" size={17} /> : <UploadCloud size={17} />}
              선택: 원본에도 저장
            </button>
            {outputFormat === "HTML" ? (
              <button className="primary-button" disabled={submitting} onClick={submitWithoutSignature} type="button">
                {submitting ? <Loader2 className="spin" size={17} /> : <Eye size={17} />}
                HTML 생성
              </button>
            ) : (
              <button className="primary-button" disabled={submitting || applyingNarrative} onClick={() => {
                rememberDraft();
                setScreen("generate");
              }} type="button">
                이 문장으로 생성 설정 이동
              </button>
            )}
          </footer>
        </section>
      </div>
    );
  }

  return (
    <div className="document-preview-backdrop" role="presentation">
      <section className="document-signature-dialog" role="dialog" aria-modal="true" aria-label={outputLabel}>
        <header className="document-preview-header">
          <div>
            <span>{outputLabel}</span>
            <strong>{report.title || report.reportNo}</strong>
            <small>
              {isHtmlOutput
                ? "서명 없이 HTML을 생성하고 완료 후 미리보기를 엽니다. 다듬은 문장은 다른 출력에도 다시 사용할 수 있습니다."
                : `${outputFormat} 생성 전에 서명과 다듬은 문장을 확인합니다.`}
            </small>
          </div>
          <button className="icon-button" disabled={submitting} onClick={discardAndCloseDialog} type="button" aria-label={`${outputLabel} 창 닫기`}>
            <X size={18} />
          </button>
        </header>
        <div className="signature-dialog-body">
          {narrativeFields.length > 0 ? (
            <section className="document-narrative-polish-compact" aria-label="문장 다듬기 요약">
              <div>
                <span>
                  <Sparkles size={15} />
                  <strong>문장 다듬기</strong>
                </span>
                <p>
                  {appliedNarrativeCount > 0
                    ? `${appliedNarrativeCount}개 문장이 이번 생성 문서에 반영됩니다. 원본 리포트에 저장하지 않아도 DOCX/PDF/HTML 생성에는 같은 문장을 사용합니다.`
                    : `확인 대상 ${narrativeFields.length}개가 있습니다. 원본 문장 그대로 생성하거나 별도 화면에서 다듬을 수 있습니다.`}
                </p>
              </div>
              <button className="secondary-button compact-button" disabled={submitting} onClick={() => {
                rememberDraft();
                setScreen("polish");
              }} type="button">
                문장 확인/다듬기
              </button>
            </section>
          ) : null}
          {isHtmlOutput ? (
            <div className="signature-skipped-note">
              <strong>HTML 생성에는 서명을 요구하지 않습니다.</strong>
              <p>HTML은 화면 확인용 산출물이므로 현재 생성본 문장만 반영해 만들고, 생성 완료 후 미리보기를 엽니다.</p>
            </div>
          ) : (
            <>
              <div className="signature-form-grid">
                <label>
                  <span>서명자</span>
                  <input value={name} onChange={(event) => setName(event.target.value)} placeholder="이름" />
                </label>
                <label>
                  <span>역할</span>
                  <input value={role} onChange={(event) => setRole(event.target.value)} placeholder="작성자 / 점검자" />
                </label>
              </div>
              <div className="signature-pad-wrap">
                <div className="signature-pad-head">
                  <strong>서명</strong>
                  <button className="secondary-button compact-button" disabled={submitting} onClick={clearSignature} type="button">
                    <Trash2 size={14} />
                    지우기
                  </button>
                </div>
                <canvas
                  className="signature-pad"
                  ref={canvasRef}
                  onPointerCancel={stopDrawing}
                  onPointerDown={startDrawing}
                  onPointerLeave={stopDrawing}
                  onPointerMove={draw}
                  onPointerUp={stopDrawing}
                />
              </div>
            </>
          )}
          {localError || error ? <InlineAlert message={localError ?? error ?? ""} /> : null}
        </div>
        <footer className="signature-dialog-actions">
          <button className="secondary-button" disabled={submitting} onClick={discardAndCloseDialog} type="button">
            취소
          </button>
          {isHtmlOutput ? (
            <button className="primary-button" disabled={submitting} onClick={submitWithoutSignature} type="button">
              {submitting ? <Loader2 className="spin" size={17} /> : <Eye size={17} />}
              HTML 생성
            </button>
          ) : (
            <>
              <button className="secondary-button" disabled={submitting} onClick={submitWithoutSignature} type="button">
                {submitting ? <Loader2 className="spin" size={17} /> : <FileText size={17} />}
                서명 없이 생성
              </button>
              <button className="primary-button" disabled={submitting} onClick={submit} type="button">
                {submitting ? <Loader2 className="spin" size={17} /> : <PenLine size={17} />}
                서명 후 생성
              </button>
            </>
          )}
        </footer>
      </section>
    </div>
  );
}

function documentNarrativeRenderFields(steps: InspectionStep[]): NarrativeRenderField[] {
  const stepsByCode = new Map(steps.map((step) => [step.stepCode, step]));
  const fields: NarrativeRenderField[] = [];
  const dailyPayload = recordValue(stepsByCode.get("DAILY_LOG")?.payload);
  const remarksPayload = recordValue(stepsByCode.get("REMARKS")?.payload);
  addNarrativeField(fields, "특기사항", "steps.DAILY_LOG.payload.specialNotes", dailyPayload.specialNotes);
  addNarrativeField(fields, "특기사항", "steps.REMARKS.payload.specialNotes", remarksPayload.specialNotes);
  addNarrativeField(fields, "특기사항", "steps.REMARKS.payload.remarks", remarksPayload.remarks);
  addNarrativeField(fields, "지적사항 및 처리결과", "steps.DAILY_LOG.payload.issueAndAction", dailyPayload.issueAndAction);
  addNarrativeField(fields, "지적사항 및 처리결과", "steps.DAILY_LOG.payload.issueAndActionResult", dailyPayload.issueAndActionResult);
  addNarrativeField(fields, "다음 조치", "steps.DAILY_LOG.payload.nextAction", dailyPayload.nextAction);
  addNarrativeField(fields, "지적사항 및 처리결과", "steps.REMARKS.payload.issueAndAction", remarksPayload.issueAndAction);
  addNarrativeField(fields, "다음 조치", "steps.REMARKS.payload.nextAction", remarksPayload.nextAction);
  return fields.slice(0, 30);
}

function addNarrativeField(
  fields: NarrativeRenderField[],
  label: string,
  path: string,
  rawValue: unknown
) {
  const value = stringValue(rawValue);
  if (!value.trim()) {
    return;
  }
  fields.push({ label, path, value });
}

function recordValue(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : {};
}

function listValue(value: unknown): unknown[] {
  return Array.isArray(value) ? value : [];
}

function stringValue(value: unknown) {
  return value == null ? "" : String(value);
}

function joinNonBlank(...values: string[]) {
  return values.map((value) => value.trim()).filter(Boolean).join(" / ");
}

function signatureDataUrl(canvas: HTMLCanvasElement) {
  const context = canvas.getContext("2d");
  if (!context) {
    return canvas.toDataURL("image/png");
  }
  const image = context.getImageData(0, 0, canvas.width, canvas.height);
  const data = image.data;
  let minX = canvas.width;
  let minY = canvas.height;
  let maxX = 0;
  let maxY = 0;
  for (let y = 0; y < canvas.height; y += 1) {
    for (let x = 0; x < canvas.width; x += 1) {
      const alpha = data[(y * canvas.width + x) * 4 + 3];
      if (alpha > 0) {
        minX = Math.min(minX, x);
        minY = Math.min(minY, y);
        maxX = Math.max(maxX, x);
        maxY = Math.max(maxY, y);
      }
    }
  }
  if (minX > maxX || minY > maxY) {
    return canvas.toDataURL("image/png");
  }
  const padding = Math.max(16, Math.round(Math.min(canvas.width, canvas.height) * 0.05));
  const cropX = Math.max(0, minX - padding);
  const cropY = Math.max(0, minY - padding);
  const cropWidth = Math.min(canvas.width - cropX, maxX - minX + 1 + padding * 2);
  const cropHeight = Math.min(canvas.height - cropY, maxY - minY + 1 + padding * 2);
  const cropped = document.createElement("canvas");
  cropped.width = cropWidth;
  cropped.height = cropHeight;
  const croppedContext = cropped.getContext("2d");
  if (!croppedContext) {
    return canvas.toDataURL("image/png");
  }
  croppedContext.drawImage(canvas, cropX, cropY, cropWidth, cropHeight, 0, 0, cropWidth, cropHeight);
  return cropped.toDataURL("image/png");
}

function defaultSignatureName(user: MeResponse) {
  return (user.name || user.email || "").trim();
}

function defaultSignatureRole(report: InspectionReport, office: Office | null) {
  if (office?.type === "PERSONAL") {
    if (report.reportType === "CONSTRUCTION_DAILY_SUPERVISION_LOG") {
      return "총괄감리책임자/건축사보";
    }
    return "작성자";
  }
  if (report.reportType === "CONSTRUCTION_DAILY_SUPERVISION_LOG") {
    return "배정 역할 기준";
  }
  return "작성자";
}

function PreflightReviewPanel({
  currentRevision,
  findings,
  report,
  reviewing,
  applyingFindingId,
  resolvingFindingId,
  run,
  onApplyFindingFix,
  onResolveFinding
}: {
  currentRevision: number;
  findings: ReportPreflightReviewFindingResponse[];
  report: InspectionReport;
  reviewing: boolean;
  applyingFindingId: number | null;
  resolvingFindingId: number | null;
  run: ReportPreflightReviewRunResponse | null;
  onApplyFindingFix: (
    runId: number,
    findingId: number
  ) => Promise<ReportPreflightReviewFindingResponse>;
  onResolveFinding: (
    runId: number,
    findingId: number,
    status: ReportPreflightFindingResolutionStatus
  ) => Promise<ReportPreflightReviewFindingResponse>;
}) {
  const listFindings = findings.filter((finding) => !isPreflightFindingCoveredByLegalSummary(finding));
  const blockingCount = listFindings.filter((finding) => requiresPreflightFindingAction(finding)).length;
  const deterministicFindings = listFindings.filter((finding) => finding.source === "DETERMINISTIC");
  const aiFindings = listFindings.filter((finding) => finding.source !== "DETERMINISTIC" && finding.source !== "LEGAL_REVIEW");
  const deterministicBlockingCount = deterministicFindings.filter((finding) => requiresPreflightFindingAction(finding)).length;
  const aiAttentionCount = aiFindings.filter((finding) => requiresPreflightFindingAction(finding)).length;
  const warningCount = listFindings.filter((finding) => !requiresPreflightFindingAction(finding)).length;
  const stale = run ? run.reportRevision !== currentRevision : false;
  const warning = stale || (run && isPreflightBlocking(run));
  const aiMode = run ? preflightAiMode(run) : null;
  const progress = reviewing || (run && isPreflightActive(run))
    ? preflightProgress(run, reviewing)
    : null;
  const actionRequiredFindings = listFindings.filter((finding) => requiresPreflightFindingAction(finding));
  const passiveFindings = listFindings.filter((finding) => !requiresPreflightFindingAction(finding));
  const passiveDisplayLimit = Math.max(0, 6 - actionRequiredFindings.length);
  const displayedPassiveFindings = passiveFindings.slice(0, passiveDisplayLimit);
  const displayedFindings = [...actionRequiredFindings, ...displayedPassiveFindings];
  const hiddenPassiveCount = Math.max(0, passiveFindings.length - displayedPassiveFindings.length);

  return (
    <section className={warning ? "preflight-panel warning" : "preflight-panel"}>
      <div className="preflight-summary">
        <div className={warning ? "row-icon amber" : "row-icon blue"}>
          {warning ? <AlertTriangle size={17} /> : <ShieldCheck size={17} />}
        </div>
        <div>
          <strong>{preflightTitle(run, stale)}</strong>
          <span className="preflight-copy">
            {preflightDescription(run, stale, currentRevision, blockingCount, warningCount)}
          </span>
        </div>
        <span className={`status-badge ${warning ? "amber" : run?.status === "PASSED" ? "green" : "slate"}`}>
          {preflightStatusLabel(run, stale)}
        </span>
      </div>

      {progress ? (
        <div className="preflight-progress-row" aria-label={`생성 전 검토 진행률 ${progress.percent}%`}>
          <div>
            <strong>{progress.label}</strong>
            <span>{progress.detail}</span>
          </div>
          <em>{progress.percent}%</em>
          <div className="preflight-progress-track">
            <div style={{ width: `${progress.percent}%` }} />
          </div>
        </div>
      ) : null}

      <div className="preflight-gate-grid">
        <div className={deterministicBlockingCount > 0 ? "preflight-gate-item blocking" : "preflight-gate-item pass"}>
          <strong>코드 검증</strong>
          <span>
            {run
              ? deterministicBlockingCount > 0
                ? `수정 필요 ${deterministicBlockingCount}건`
                : deterministicFindings.length > 0
                  ? `경고 ${deterministicFindings.length}건`
                  : "필수값/사진/상태 통과"
              : "검토 실행 전"}
          </span>
        </div>
        <div className={aiAttentionCount > 0 ? "preflight-gate-item blocking" : "preflight-gate-item pass"}>
          <strong>AI 검토</strong>
          <span>{run ? preflightAiDescription(run, aiAttentionCount) : "검토 실행 전"}</span>
        </div>
        <div className={stale ? "preflight-gate-item blocking" : run?.status === "PASSED" ? "preflight-gate-item pass" : "preflight-gate-item"}>
          <strong>생성 가능 여부</strong>
          <span>{preflightGateHint(run, report)}</span>
        </div>
      </div>

      {aiMode ? (
        <div className={`preflight-ai-banner ${aiMode.kind}`}>
          <strong>{aiMode.title}</strong>
          <span>{aiMode.description}</span>
        </div>
      ) : null}

      <PreflightLegalReviewSummary findings={findings} run={run} stale={stale} />

      {displayedFindings.length > 0 ? (
        <div className="preflight-finding-list">
          {displayedFindings.map((finding) => {
            const legalReferenceDetails = finding.legalReferenceDetails ?? [];
            const legalReferenceCount = legalReferenceDetails.length > 0
              ? legalReferenceDetails.length
              : finding.legalReferences.length;
            const fixReplacement = preflightFixReplacement(finding);
            const fixSuggestion = fixReplacement || preflightFixSuggestion(finding);
            const fixTarget = preflightFixTargetLabel(finding);
            const fixAvailable = Boolean(fixReplacement && fixTarget && canApplyPreflightFindingFix(finding));
            const fixing = applyingFindingId === finding.id;
            const resolving = resolvingFindingId === finding.id;
            return (
            <div className={requiresPreflightFindingAction(finding) ? "preflight-finding blocking" : "preflight-finding"} key={finding.id}>
              {requiresPreflightFindingAction(finding) ? <AlertTriangle size={15} /> : <CheckCircle2 size={15} />}
              <span>
                <strong>{finding.message}</strong>
                <small>
                  {preflightSourceLabelV2(finding)} / {findingSeverityLabel(finding.severity)} / {findingMetaLabel(finding)}
                </small>
                <small className="preflight-resolution">{findingResolutionLabel(finding)}</small>
                {fixSuggestion ? (
                  <span className="preflight-ai-suggestion">
                    <strong>{fixReplacement && fixTarget ? `자동 적용 문장 · ${fixTarget}` : fixTarget ? `AI 수정안 · ${fixTarget}` : "AI 수정안"}</strong>
                    <em>{fixSuggestion}</em>
                  </span>
                ) : null}
                {legalReferenceCount > 0 ? (
                  <span className="preflight-legal-context">
                    <strong>법령 근거</strong>
                    {legalReferenceDetails.length > 0
                      ? legalReferenceDetails.slice(0, 3).map((reference) => (
                        <em key={reference.referenceId}>{legalReferenceDetailDisplay(reference)}</em>
                      ))
                      : finding.legalReferences.slice(0, 3).map((reference) => (
                        <em key={reference}>{legalReferenceDisplay(reference)}</em>
                      ))}
                    {legalReferenceCount > 3 ? <em>외 {legalReferenceCount - 3}건</em> : null}
                  </span>
                ) : null}
                {finding.nextActions.length > 0 ? (
                  <span className="preflight-next-actions">
                    <strong>다음 조치</strong>
                    {finding.nextActions.map((action) => (
                      <em key={action}>{preflightNextActionLabel(action)}</em>
                    ))}
                  </span>
                ) : null}
              </span>
              {run && !stale && requiresPreflightFindingAction(finding) ? (
                <div className="preflight-finding-actions">
                  {fixAvailable ? (
                    <button
                      className="secondary-button compact-button"
                      disabled={fixing || resolving}
                      onClick={() => onApplyFindingFix(run.id, finding.id)}
                      title="수정안을 보고서 입력값에 적용합니다. 승인된 보정은 다시 제출하지 않아도 생성 흐름을 유지합니다."
                      type="button"
                    >
                      {fixing ? <Loader2 className="spin" size={14} /> : <PenLine size={14} />}
                      수정안 적용
                    </button>
                  ) : null}
                  <button
                    className="secondary-button compact-button"
                    disabled={fixing || resolving}
                    onClick={() => onResolveFinding(run.id, finding.id, "RESOLVED")}
                    title="내용을 수정했거나 검토해서 더 이상 문서 생성을 막을 문제가 아니라고 확인합니다."
                    type="button"
                  >
                    {resolving ? <Loader2 className="spin" size={14} /> : <CheckCircle2 size={14} />}
                    수정 완료
                  </button>
                  <button
                    className="secondary-button compact-button"
                    disabled={fixing || resolving}
                    onClick={() => onResolveFinding(run.id, finding.id, "ACCEPTED")}
                    title="경고가 남아 있음을 알고도 이 리포트에서는 진행 가능한 리스크로 수용합니다."
                    type="button"
                  >
                    리스크 수용
                  </button>
                </div>
              ) : null}
            </div>
            );
          })}
          {hiddenPassiveCount > 0 ? (
            <span className="preflight-more">조치 필요 항목은 모두 표시됨 / 참고·완료 항목 {hiddenPassiveCount}건 접힘</span>
          ) : null}
        </div>
      ) : null}

    </section>
  );
}

function PreflightLegalReviewSummary({
  findings,
  run,
  stale
}: {
  findings: ReportPreflightReviewFindingResponse[];
  run: ReportPreflightReviewRunResponse | null;
  stale: boolean;
}) {
  const summary = preflightLegalSummaryV2(findings, run, stale);
  const referenceGroups = legalReviewReferenceGroups(summary.references);
  const constraintNotes = legalReviewConstraintNotes(findings);

  return (
    <div className={`preflight-legal-review ${summary.kind}`}>
      <div className="preflight-legal-review-head">
        <div>
          <strong>법령검토</strong>
          <span>{summary.status}</span>
        </div>
        <em>{summary.badge}</em>
      </div>
      <div className="preflight-legal-review-grid">
        <div>
          <strong>법령 근거</strong>
          <span>{summary.evidence}</span>
        </div>
        <div>
          <strong>법률 리스크</strong>
          <span>{summary.risk}</span>
        </div>
        <div>
          <strong>판정 이유</strong>
          <span>{summary.reason}</span>
        </div>
      </div>
      <div className="preflight-legal-review-refs">
        <strong>검토된 조문</strong>
        {referenceGroups.length > 0 ? (
          <div className="preflight-legal-review-ref-groups">
            {referenceGroups.map((group) => {
              const visibleItems = group.items.slice(0, 4);
              return (
                <div className="preflight-legal-review-ref-group" key={group.key}>
                  <div>
                    <b>{group.title}</b>
                    <small>{group.description}</small>
                  </div>
                  <span>
                    {visibleItems.map((reference) => (
                      <em key={reference.key}>{reference.label}</em>
                    ))}
                    {group.items.length > visibleItems.length ? (
                      <em>외 {group.items.length - visibleItems.length}건</em>
                    ) : null}
                  </span>
                </div>
              );
            })}
          </div>
        ) : (
          <small>표시된 조문 근거가 없습니다.</small>
        )}
      </div>
      {constraintNotes.length > 0 ? (
        <div className="preflight-legal-review-notes">
          <strong>PASS 차단/검토 제한</strong>
          <ul>
            {constraintNotes.map((note) => (
              <li key={note}>{note}</li>
            ))}
          </ul>
        </div>
      ) : null}
    </div>
  );
}

function PreflightInlineProgress({
  reviewing,
  run
}: {
  reviewing: boolean;
  run: ReportPreflightReviewRunResponse | null;
}) {
  const progress = preflightProgress(run, reviewing);
  return (
    <span className="preflight-inline-progress" aria-label={`생성 전 검토 진행률 ${progress.percent}%`}>
      <span>
        <i style={{ width: `${progress.percent}%` }} />
      </span>
      <small>{progress.label}</small>
    </span>
  );
}

function RevisionStrip({
  latestGeneratedJob,
  latestJob,
  report
}: {
  latestGeneratedJob: DocumentJobResponse | null;
  latestJob: DocumentJobResponse | null;
  report: InspectionReport;
}) {
  return (
    <div className="revision-strip">
      <span>작성본 v{report.contentRevision}</span>
      <span>제출 v{report.submittedRevision ?? "-"}</span>
      <span>생성 v{report.generatedRevision ?? "-"}</span>
      {latestJob ? <span>최근 job v{latestJob.reportRevision}</span> : null}
      {latestGeneratedJob && latestGeneratedJob.reportRevision !== report.contentRevision ? (
        <span className="revision-warning">최신 문서는 v{latestGeneratedJob.reportRevision}</span>
      ) : null}
    </div>
  );
}

function DocumentPreviewDialog({
  onClose,
  preview
}: {
  onClose: () => void;
  preview: {
    artifact: DocumentArtifactResponse;
    html: string;
    job: DocumentJobResponse;
  };
}) {
  return (
    <div className="document-preview-backdrop" role="presentation">
      <section className="document-preview-dialog" role="dialog" aria-modal="true" aria-label="HTML 문서 미리보기">
        <header className="document-preview-header">
          <div>
            <span>HTML 미리보기</span>
            <strong>{preview.artifact.fileName}</strong>
            <small>job #{preview.job.id} / revision v{preview.job.reportRevision}</small>
          </div>
          <button className="icon-button" onClick={onClose} type="button" aria-label="미리보기 닫기">
            <X size={18} />
          </button>
        </header>
        <iframe
          className="document-preview-frame"
          sandbox=""
          srcDoc={preview.html}
          title={preview.artifact.fileName}
        />
      </section>
    </div>
  );
}

function JobProgress({ job }: { job: DocumentJobResponse }) {
  return (
    <div className="document-progress">
      <div>
        <span>{statusLabel(job.progressStep)} / v{job.reportRevision}</span>
        <strong>{job.progressPercent}%</strong>
      </div>
      <div className="progress-track" aria-hidden="true">
        <span style={{ width: `${Math.max(0, Math.min(100, job.progressPercent))}%` }} />
      </div>
      <p>{job.errorMessage || job.progressMessage || "문서 작업 상태를 확인하는 중입니다."}</p>
    </div>
  );
}

function InlineDocumentGuide({
  latestGeneratedJob,
  report
}: {
  latestGeneratedJob: DocumentJobResponse | null;
  report: InspectionReport;
}) {
  if (needsSubmitBeforeGeneration(report)) {
    return (
      <div className="document-guide warning">
        <FileClock size={16} />
        <span>작성본 v{report.contentRevision}은 아직 제출되지 않았습니다. 리포트 작성 화면에서 제출한 뒤 문서를 생성하세요.</span>
      </div>
    );
  }
  if (report.status === "READY_TO_GENERATE") {
    return (
      <div className="document-guide">
        <UploadCloud size={16} />
        <span>제출본 v{report.submittedRevision ?? report.contentRevision} 문서를 생성할 수 있습니다.</span>
      </div>
    );
  }
  if (latestGeneratedJob && latestGeneratedJob.reportRevision === report.contentRevision) {
    return (
      <div className="document-guide">
        <Download size={16} />
        <span>작성본 v{report.contentRevision} 기준 문서가 생성되었습니다. 필요하면 같은 revision으로 재생성할 수 있습니다.</span>
      </div>
    );
  }
  return null;
}

function GeneratedJobArtifacts({
  deliveriesByArtifact,
  downloadingArtifactId,
  isLatest,
  job,
  previewingArtifactId,
  requestingDeliveryArtifactId,
  onDownloadPrepared,
  onPreviewArtifact,
  onRequestDelivery
}: {
  deliveriesByArtifact: Record<number, DocumentDeliveryRequestResponse>;
  downloadingArtifactId: number | null;
  isLatest: boolean;
  job: DocumentJobResponse;
  previewingArtifactId: number | null;
  requestingDeliveryArtifactId: number | null;
  onDownloadPrepared: (artifact: DocumentArtifactResponse, delivery: DocumentDeliveryRequestResponse) => Promise<unknown>;
  onPreviewArtifact: (artifact: DocumentArtifactResponse, job: DocumentJobResponse) => Promise<unknown>;
  onRequestDelivery: (artifact: DocumentArtifactResponse, job: DocumentJobResponse) => Promise<unknown>;
}) {
  return (
    <details className={isLatest ? "document-revision-section latest" : "document-revision-section"} open={isLatest}>
      <summary>
        <span>
          <strong>{isLatest ? "최신 생성본" : "이전 생성본"} v{job.reportRevision}</strong>
          <small>{job.artifacts.length}개 파일</small>
        </span>
        <em>{job.completedAt ? new Date(job.completedAt).toLocaleString() : "완료 시간 없음"}</em>
      </summary>
      <div className="artifact-list">
        {job.artifacts.map((artifact) => {
          const delivery = deliveriesByArtifact[artifact.id];
          const deliveryActive = delivery ? ["REQUESTED", "SENDING"].includes(delivery.status) : false;
          const deliveryReady = delivery?.status === "COMPLETED" && Boolean(delivery.downloadUrl);
          const downloadBusy = downloadingArtifactId === artifact.id || requestingDeliveryArtifactId === artifact.id;
          const previewBusy = previewingArtifactId === artifact.id;
          return (
            <div className="artifact-row" key={artifact.id}>
              <span>
                <strong>{artifact.fileName}</strong>
                <small>
                  {artifact.artifactType} / {formatBytes(artifact.bytes)} / {deliveryLabel(delivery)}
                </small>
              </span>
              <div className="artifact-actions">
                {artifact.artifactType === "HTML" ? (
                  <button
                    className="secondary-button compact-button"
                    disabled={previewBusy || deliveryActive}
                    onClick={() => onPreviewArtifact(artifact, job)}
                    type="button"
                  >
                    {previewBusy ? <Loader2 className="spin" size={15} /> : <Eye size={15} />}
                    미리보기
                  </button>
                ) : null}
                <button
                  className="secondary-button compact-button"
                  disabled={downloadBusy || deliveryActive}
                  onClick={() =>
                    deliveryReady && delivery
                      ? onDownloadPrepared(artifact, delivery)
                      : onRequestDelivery(artifact, job)
                  }
                  type="button"
                >
                  {downloadBusy || deliveryActive ? <Loader2 className="spin" size={15} /> : <Download size={15} />}
                  다운로드
                </button>
              </div>
            </div>
          );
        })}
      </div>
    </details>
  );
}

function preflightTitle(run: ReportPreflightReviewRunResponse | null, stale: boolean) {
  if (!run) {
    return "생성 전 검토 전";
  }
  if (isPreflightAiPending(run)) {
    return "생성 전 검토 중";
  }
  if (stale) {
    return "최신 revision 재검토 필요";
  }
  if (run.status === "PASSED") {
    return "생성 전 검토 통과";
  }
  if (run.status === "NEEDS_ATTENTION") {
    return "생성 전 확인 필요";
  }
  if (run.status === "FAILED") {
    return "생성 전 검토 실패";
  }
  return "생성 전 검토 중";
}

function preflightDescription(
  run: ReportPreflightReviewRunResponse | null,
  stale: boolean,
  currentRevision: number,
  blockingCount: number,
  warningCount: number
) {
  if (!run) {
    return "문서 생성 전에 누락된 항목과 사진 상태를 확인할 수 있습니다.";
  }
  if (isPreflightAiPending(run)) {
    return run.harnessStatus === "RUNNING"
      ? "코드 검증은 반영됐고 AI 검토 응답을 기다리고 있습니다."
      : "코드 검증 후 AI 검토를 준비하고 있습니다.";
  }
  if (run.status === "RUNNING" && run.aiReviewPlanned && isPreflightHarnessTerminal(run)) {
    return "AI 응답은 완료됐고 선택된 검사항목 기준으로 법령 근거 검토를 정리하고 있습니다.";
  }
  if (run.status === "PASSED") {
    if (stale) {
      return `검토는 v${run.reportRevision} 기준입니다. 현재 제출 v${currentRevision} 기준으로 다시 검토해 주세요.`;
    }
    return warningCount > 0 ? `경고 ${warningCount}건이 있지만 생성은 가능합니다.` : "막히는 문제가 없습니다.";
  }
  if (stale) {
    return `검토는 v${run.reportRevision} 기준입니다. 현재 제출 v${currentRevision} 기준으로 다시 검토해 주세요.`;
  }
  if (run.status === "NEEDS_ATTENTION") {
    if (blockingCount === 0 && warningCount === 0) {
      return "확인 필요 항목을 불러오는 중입니다.";
    }
    if (blockingCount === 0) {
      return "표시된 경고 또는 이전 미해결 항목을 확인해야 합니다.";
    }
    return `수정이 필요한 항목 ${blockingCount}건이 있습니다.`;
  }
  if (run.status === "FAILED") {
    return "검토 중 오류가 발생했습니다. 다시 실행해 주세요.";
  }
  return "검토 결과를 준비하고 있습니다.";
}

function preflightProgress(run: ReportPreflightReviewRunResponse | null, reviewing: boolean) {
  if (reviewing) {
    return {
      percent: 12,
      label: "검토 요청 중",
      detail: "서버에 검토 작업을 등록하고 있습니다."
    };
  }
  if (!run) {
    return {
      percent: 8,
      label: "검토 준비",
      detail: "검토를 시작할 수 있습니다."
    };
  }
  if (run.status === "REQUESTED") {
    return {
      percent: 28,
      label: "검토 대기",
      detail: "검토 작업이 등록되어 실행을 기다립니다."
    };
  }
  if (isPreflightAiPending(run)) {
    if (run.harnessStatus === "RUNNING") {
      return {
        percent: 78,
        label: "AI 검토 중",
        detail: `${run.aiModelId ?? run.aiProviderCode ?? "설정된 모델"} 응답을 기다리고 있습니다.`
      };
    }
    return {
      percent: 56,
      label: "AI 검토 준비",
      detail: "코드 검증 결과를 반영했고 AI 하네스 완료를 기다립니다."
    };
  }
  if (run.status === "RUNNING") {
    if (!run.aiReviewPlanned || run.harnessStatus === "SKIPPED") {
      return {
        percent: 62,
        label: "코드 검증 중",
        detail: "필수값, 상태, 제출 revision을 확인하고 있습니다."
      };
    }
    if (run.harnessStatus === "RUNNING") {
      return {
        percent: 78,
        label: "AI 검토 중",
        detail: `${run.aiModelId ?? run.aiProviderCode ?? "설정된 모델"} 응답을 기다리고 있습니다.`
      };
    }
    if (run.harnessStatus === "COMPLETED" || run.harnessStatus === "SUCCEEDED") {
      return {
        percent: 88,
        label: "법령 근거 검토 중",
        detail: "선택된 검사항목과 연결된 법령 근거를 기준으로 최종 검토를 정리하고 있습니다."
      };
    }
    return {
      percent: 48,
      label: "검토 실행 중",
      detail: run.aiReviewPlanned ? "코드 검증 후 AI 검토를 준비하고 있습니다." : "코드 검증을 진행하고 있습니다."
    };
  }
  if (run.status === "PASSED") {
    return {
      percent: 100,
      label: "검토 완료",
      detail: "문서 생성이 가능합니다."
    };
  }
  if (run.status === "NEEDS_ATTENTION") {
    return {
      percent: 100,
      label: "확인 필요",
      detail: "수정 또는 리스크 수용이 필요한 항목이 있습니다."
    };
  }
  if (run.status === "FAILED") {
    return {
      percent: 100,
      label: "검토 실패",
      detail: "오류 확인 후 다시 실행해 주세요."
    };
  }
  return {
    percent: 40,
    label: "검토 진행 중",
    detail: "검토 상태를 갱신하고 있습니다."
  };
}

function preflightAiDescription(run: ReportPreflightReviewRunResponse, attentionCount = 0) {
  if (attentionCount > 0) {
    return run.harnessRunId
      ? `AI 초안 확인 필요 ${attentionCount}건`
      : `이전 AI 미해결 항목 ${attentionCount}건 유지`;
  }
  if (!run.aiReviewPlanned) {
    return "새 AI 호출 없음";
  }
  const status = harnessStatusLabel(run.harnessStatus ?? "QUEUED");
  const model = run.aiModelId ?? run.aiProviderCode ?? "설정된 모델";
  const attempt = run.harnessAttempt > 0 ? ` / ${run.harnessAttempt}회 시도` : "";
  return `${status}${attempt} / ${model}`;
}

function preflightAiMode(run: ReportPreflightReviewRunResponse) {
  if (!run.aiReviewPlanned) {
    if (run.terminalReason === "DETERMINISTIC_PREFLIGHT_BLOCKED") {
      return {
        kind: "skipped",
        title: "AI 호출 전 확인 필요",
        description: "구조화 항목 검증에서 먼저 처리할 항목이 있어 새 AI 호출 없이 코드 검증 결과만 표시했습니다."
      };
    }
    return {
      kind: "skipped",
      title: "새 AI 호출 없음",
      description: "사용자/사무소 AI 한도 또는 정책 설정 때문에 이번 run에서 새 AI 호출은 실행되지 않았습니다. 이전 미해결 AI 항목은 그대로 유지됩니다."
    };
  }
  if (run.aiProviderCode?.startsWith("fake-") || run.aiModelId?.startsWith("fake-")) {
    return {
      kind: "fake",
      title: "개발용 Fake AI",
      description: "외부 AI API를 호출하지 않고 로컬 테스트용 응답으로 검토 흐름만 검증했습니다."
    };
  }
  return {
    kind: "real",
    title: "AI 법령검토 초안",
    description: `${run.aiProviderCode ?? "provider"} 설정을 통해 source-backed 법령 근거 안에서 dry-run 검토 초안을 생성합니다.`
  };
}

function preflightGateHint(run: ReportPreflightReviewRunResponse | null, report: InspectionReport) {
  if (needsSubmitBeforeGeneration(report)) {
    return "수정본을 먼저 제출해야 합니다.";
  }
  if (!run) {
    return "최신 제출본 기준 생성 전 검토가 필요합니다.";
  }
  if (isPreflightAiPending(run)) {
    return "AI 검토 진행 중";
  }
  if (run.status === "RUNNING" && run.aiReviewPlanned && isPreflightHarnessTerminal(run)) {
    return "법령 근거 검토 중";
  }
  const currentRevision = generationRevision(report);
  if (run.reportRevision !== currentRevision) {
    return `검토 기준 v${run.reportRevision}, 현재 제출본 v${currentRevision}`;
  }
  if (run.status === "PASSED") {
    return "문서 생성 가능";
  }
  if (run.status === "NEEDS_ATTENTION") {
    return "수정 필요 항목 처리 후 생성 가능";
  }
  if (run.status === "FAILED") {
    return "검토 실패, 다시 실행 필요";
  }
  return "검토 진행 중";
}

function preflightStatusLabel(run: ReportPreflightReviewRunResponse | null, stale: boolean) {
  if (!run) {
    return "검토 전";
  }
  if (isPreflightAiPending(run)) {
    return "검토 중";
  }
  if (stale) {
    return "재검토 필요";
  }
  if (run.status === "PASSED") {
    return "생성 가능";
  }
  if (run.status === "NEEDS_ATTENTION") {
    return "확인 필요";
  }
  if (run.status === "FAILED") {
    return "검토 실패";
  }
  return "검토 중";
}

function preflightLegalSummaryV2(
  findings: ReportPreflightReviewFindingResponse[],
  run: ReportPreflightReviewRunResponse | null,
  stale: boolean
) {
  const legalFindings = findings.filter(isLegalPreflightFinding);
  const resultFinding = legalReviewResultFinding(findings);
  const legalReviewStatus = resultFinding?.attributes?.legalReviewStatus ?? "";
  const passReason = resultFinding?.attributes?.passReason?.trim() ?? "";
  const limitations = resultFinding?.attributes?.limitations?.trim() ?? "";
  const legalReviewScope = resultFinding?.attributes?.legalReviewScope?.trim() ?? "";
  const references = legalReviewReferences(findings);
  const openAttention = legalFindings.filter((finding) => requiresPreflightFindingAction(finding)).length;
  const evidence = references.length > 0 ? `법령 근거 ${references.length}건 사용됨` : "표시된 법령 근거 없음";

  if (!run) {
    return {
      kind: "pending",
      badge: "검토 전",
      status: "생성 전 검토를 실행하면 법령 근거와 리스크를 함께 확인합니다.",
      evidence: "검토 전",
      risk: "검토 전",
      reason: "아직 Engine/AI 검토 결과가 없습니다.",
      references
    };
  }
  if (stale) {
    return {
      kind: "warning",
      badge: "재검토 필요",
      status: "현재 리포트 내용이 이전 검토 revision과 다릅니다.",
      evidence,
      risk: "다시 검토 필요",
      reason: "리포트가 변경되어 이전 법령검토 결과를 그대로 신뢰할 수 없습니다.",
      references
    };
  }
  if (isPreflightActive(run) || isPreflightAiPending(run)) {
    return {
      kind: "pending",
      badge: "검토 중",
      status: "법령 근거와 전용 법령검토 결과를 정리하는 중입니다.",
      evidence,
      risk: "검토 중",
      reason: "검토 run이 아직 완료되지 않았습니다.",
      references
    };
  }
  if (run.status === "FAILED") {
    return {
      kind: "warning",
      badge: "실패",
      status: "법령검토 결과를 확정하지 못했습니다.",
      evidence,
      risk: "확인 불가",
      reason: run.terminalReason ?? "생성 전 검토 run이 실패했습니다.",
      references
    };
  }
  if (openAttention > 0) {
    return {
      kind: "warning",
      badge: "확인 필요",
      status: `법령/준법 관련 확인 필요 항목 ${openAttention}건이 있습니다.`,
      evidence,
      risk: `확인 필요 ${openAttention}건`,
      reason: resultFinding?.message || "열린 법령/준법 finding을 수정 완료하거나 리스크 수용해야 생성할 수 있습니다.",
      references
    };
  }
  if (resultFinding && legalReviewStatus === "PASS") {
    return {
      kind: "pass",
      badge: "근거범위 확인",
      status: resultFinding.message || "제공된 법령 근거 범위에서 추가 확인 필요 항목이 표시되지 않았습니다.",
      evidence,
      risk: "표시된 추가 법률 리스크 없음",
      reason: passReason || legalReviewScope || "전용 법령검토 AI가 제공된 근거와 리포트 입력 범위 안에서만 확인했습니다.",
      references
    };
  }
  if (resultFinding && legalReviewStatus === "INSUFFICIENT_CONTEXT") {
    return {
      kind: "warning",
      badge: "근거 부족",
      status: resultFinding.message || "법령 근거 기반 검토에 필요한 근거가 충분하지 않습니다.",
      evidence,
      risk: "사람 확인 필요",
      reason: limitations || "업무-법령 매핑이나 리포트 증빙 맥락을 보강한 뒤 다시 검토해야 합니다.",
      references
    };
  }
  if (resultFinding && legalReviewStatus === "SKIPPED") {
    return {
      kind: "warning",
      badge: "설정 필요",
      status: resultFinding.message || "법령검토 AI 설정이 없어 전용 법령검토가 생략되었습니다.",
      evidence,
      risk: "전용 검토 생략",
      reason: resultFinding.attributes?.skipReason || "AI 하네스 정책에서 법령 근거 기반 검토 AI를 설정해야 합니다.",
      references
    };
  }
  if (resultFinding && ["WARN", "FAIL", "FAILED"].includes(legalReviewStatus)) {
    return {
      kind: "warning",
      badge: "확인 필요",
      status: resultFinding.message,
      evidence,
      risk: legalReviewStatus === "FAIL" ? "생성 전 처리 필요" : "사람 확인 필요",
      reason: limitations || "법령검토 finding을 확인하고 처리해야 합니다.",
      references
    };
  }
  if (run.status === "NEEDS_ATTENTION" && references.length > 0) {
    return {
      kind: "warning",
      badge: "근거 확보",
      status: "법령 근거는 확보됐지만 전용 법령검토 결과는 생성되지 않았습니다.",
      evidence,
      risk: "사람 확인 필요",
      reason: "AI 한도/정책 또는 선행 검증 항목 때문에 전용 법령검토 AI가 실행되지 않았을 수 있습니다. 표시된 미해결 항목을 먼저 확인하세요.",
      references
    };
  }
  if (run.status === "NEEDS_ATTENTION") {
    return {
      kind: "warning",
      badge: "확인 필요",
      status: "생성 전 확인이 필요한 항목이 있습니다.",
      evidence,
      risk: "사람 확인 필요",
      reason: run.terminalReason ? preflightTerminalReasonLabel(run.terminalReason) : "미해결 항목을 확인해야 합니다.",
      references
    };
  }
  if (run.status === "PASSED" && references.length > 0) {
    return {
      kind: "pass",
      badge: "근거범위 확인",
      status: "제공된 법령 근거 범위에서 열린 확인 필요 항목이 없습니다.",
      evidence,
      risk: "표시된 추가 법률 리스크 없음",
      reason: "Engine이 법령 근거를 찾았고, 열린 법령/준법 finding이 남아 있지 않습니다. 최종 법률 적합 판정은 아닙니다.",
      references
    };
  }
  if (run.status === "PASSED") {
    return {
      kind: "pass",
      badge: "검토 완료",
      status: "현재 검토 결과에 열린 법령/준법 finding이 없습니다.",
      evidence,
      risk: "표시된 추가 법률 리스크 없음",
      reason: "현재 응답에는 별도로 표시할 법령 근거 조문이 없습니다. 업무-법령 매핑이 없는 항목일 수 있습니다.",
      references
    };
  }
  return {
    kind: "pending",
    badge: "확인 중",
    status: "법령검토 결과를 정리하는 중입니다.",
    evidence,
    risk: "확인 중",
    reason: run.terminalReason ? preflightTerminalReasonLabel(run.terminalReason) : "검토 상태가 아직 최종 통과/확인 필요로 정리되지 않았습니다.",
    references
  };
}

function preflightTerminalReasonLabel(reason: string) {
  if (reason === "DETERMINISTIC_PREFLIGHT_BLOCKED") {
    return "필수 구조화 항목 또는 코드 검증에서 먼저 확인할 항목이 있습니다.";
  }
  if (reason === "AI_PREFLIGHT_NEEDS_HUMAN_REVIEW") {
    return "AI 또는 이전 미해결 검토 항목 중 사람이 확인해야 할 항목이 남아 있습니다.";
  }
  if (reason === "AI_PREFLIGHT_PASSED") {
    return "AI 검토가 완료되었고 생성 차단 항목이 없습니다.";
  }
  if (reason === "PREFLIGHT_FINDINGS_RESOLVED") {
    return "미해결 검토 항목이 처리되어 생성 가능한 상태입니다.";
  }
  return reason;
}

function legalReviewResultFinding(findings: ReportPreflightReviewFindingResponse[]) {
  return findings.find((finding) =>
    finding.source === "LEGAL_REVIEW"
    && LEGAL_REVIEW_RESULT_CODES.has(finding.code)
  ) ?? null;
}

const LEGAL_REVIEW_RESULT_CODES = new Set([
  "LEGAL_REVIEW_PASSED",
  "LEGAL_REVIEW_NEEDS_HUMAN_REVIEW",
  "LEGAL_REVIEW_BLOCKED",
  "LEGAL_REVIEW_INSUFFICIENT_CONTEXT",
  "LEGAL_REVIEW_SKIPPED",
  "LEGAL_REVIEW_AI_FAILED",
  "LEGAL_REVIEW_AI_TIMEOUT",
  "LEGAL_REVIEW_RESULT_INVALID"
]);

const LEGAL_REVIEW_DISPLAY_ONLY_CODES = new Set([
  "LEGAL_REVIEW_PASSED",
  "LEGAL_REVIEW_SKIPPED"
]);

function isPreflightFindingCoveredByLegalSummary(finding: ReportPreflightReviewFindingResponse) {
  const category = finding.attributes?.category ?? "";
  if (finding.code === "LEGAL_EVIDENCE_CONTEXT_USED") {
    return true;
  }
  if (finding.source === "LEGAL_REVIEW" && LEGAL_REVIEW_DISPLAY_ONLY_CODES.has(finding.code)) {
    return true;
  }
  return finding.severity === "INFO"
    && ["LEGAL_CONTEXT", "LEGAL_REVIEW", "LEGAL_RISK"].includes(category)
    && (finding.legalReferences.length > 0 || finding.legalReferenceDetails.length > 0);
}

function preflightLegalSummary(
  findings: ReportPreflightReviewFindingResponse[],
  run: ReportPreflightReviewRunResponse | null,
  stale: boolean
) {
  const legalFindings = findings.filter(isLegalPreflightFinding);
  const references = legalReviewReferences(findings);
  const openAttention = legalFindings.filter((finding) => requiresPreflightFindingAction(finding)).length;
  const evidence = references.length > 0
    ? `법령 근거 ${references.length}건 사용됨`
    : "표시된 법령 근거 없음";

  if (!run) {
    return {
      kind: "pending",
      badge: "검토 전",
      status: "생성 전 검토를 실행하면 법령 근거와 리스크를 함께 확인합니다.",
      evidence: "검토 전",
      risk: "검토 전",
      reason: "아직 Engine/AI 검토 결과가 없습니다.",
      references
    };
  }
  if (stale) {
    return {
      kind: "warning",
      badge: "재검토 필요",
      status: "현재 리포트 내용이 이전 검토 revision과 다릅니다.",
      evidence,
      risk: "다시 검토 필요",
      reason: "리포트가 변경되어 이전 법령검토 결과를 그대로 신뢰할 수 없습니다.",
      references
    };
  }
  if (isPreflightActive(run) || isPreflightAiPending(run)) {
    return {
      kind: "pending",
      badge: "검토 중",
      status: "법령 근거와 AI dry-run 검토 결과를 정리하는 중입니다.",
      evidence,
      risk: "검토 중",
      reason: "검토 run이 아직 완료되지 않았습니다.",
      references
    };
  }
  if (run.status === "FAILED") {
    return {
      kind: "warning",
      badge: "실패",
      status: "법령검토 결과를 확정하지 못했습니다.",
      evidence,
      risk: "확인 불가",
      reason: run.terminalReason ?? "생성 전 검토 run이 실패했습니다.",
      references
    };
  }
  if (openAttention > 0) {
    return {
      kind: "warning",
      badge: "확인 필요",
      status: `법령/준법 관련 확인 필요 항목 ${openAttention}건이 있습니다.`,
      evidence,
      risk: `확인 필요 ${openAttention}건`,
      reason: "열린 법령/준법 finding을 수정 완료하거나 리스크 수용해야 생성할 수 있습니다.",
      references
    };
  }
  if (run.status === "PASSED" && references.length > 0) {
    return {
      kind: "pass",
      badge: "근거범위 확인",
      status: "제공된 법령 근거 범위에서 열린 확인 필요 항목이 없습니다.",
      evidence,
      risk: "표시된 추가 법률 리스크 없음",
      reason: "Engine이 법령 근거를 찾았고, 열린 법령/준법 finding이 남아 있지 않습니다. 최종 법률 적합 판정은 아닙니다.",
      references
    };
  }
  if (run.status === "PASSED") {
    return {
      kind: "pass",
      badge: "검토 완료",
      status: "현재 검토 결과에 열린 법령/준법 finding이 없습니다.",
      evidence,
      risk: "표시된 추가 법률 리스크 없음",
      reason: "현재 응답에는 별도로 표시할 법령 근거 조문이 없습니다. 업무-법령 매핑이 없는 항목일 수 있습니다.",
      references
    };
  }
  return {
    kind: "pending",
    badge: "확인 중",
    status: "법령검토 결과를 정리하는 중입니다.",
    evidence,
    risk: "확인 중",
    reason: run.terminalReason ?? "검토 상태가 아직 최종 통과/확인 필요로 정리되지 않았습니다.",
    references
  };
}

function legalReviewReferences(findings: ReportPreflightReviewFindingResponse[]): LegalReferenceDisplayItem[] {
  const references = new Map<string, LegalReferenceDisplayItem>();
  findings.forEach((finding) => {
    finding.legalReferenceDetails.forEach((reference) => {
      const key = reference.referenceId || reference.label;
      if (key && !references.has(key)) {
        references.set(key, { key, label: legalReferenceDetailDisplay(reference), detail: reference });
      }
    });
    finding.legalReferences.forEach((reference) => {
      if (reference && !references.has(reference)) {
        references.set(reference, { key: reference, label: legalReferenceDisplay(reference) });
      }
    });
  });
  return [...references.values()];
}

function legalReviewReferenceGroups(references: LegalReferenceDisplayItem[]): LegalReferenceDisplayGroup[] {
  const groups: LegalReferenceDisplayGroup[] = [
    {
      key: "primary",
      title: "기본 감리 근거",
      description: "감리일지 형식과 공통 감리 책임을 보는 1차 근거입니다.",
      items: []
    },
    {
      key: "secondary",
      title: "공종별 2차 근거",
      description: "선택한 공종·검사항목과 직접 연결된 추가 기준입니다.",
      items: []
    },
    {
      key: "candidate",
      title: "검색 후보",
      description: "참고용 후보입니다. 이것만으로는 PASS 판정을 내리지 않습니다.",
      items: []
    },
    {
      key: "other",
      title: "기타 근거",
      description: "분류 규칙에 걸리지 않은 보조 근거입니다.",
      items: []
    }
  ];

  references.forEach((reference) => {
    if (isLegalReferenceCandidate(reference)) {
      groups[2].items.push(reference);
    } else if (isBasicLegalReference(reference)) {
      groups[0].items.push(reference);
    } else if (isSecondaryLegalReference(reference)) {
      groups[1].items.push(reference);
    } else {
      groups[3].items.push(reference);
    }
  });

  return groups.filter((group) => group.items.length > 0);
}

function isLegalReferenceCandidate(reference: LegalReferenceDisplayItem) {
  const detail = reference.detail;
  return detail?.resolutionSource === "LEGAL_CORPUS_SEARCH"
    || detail?.bindingScope === "LEGAL_CORPUS_SEARCH"
    || detail?.relevance === "CANDIDATE";
}

function isBasicLegalReference(reference: LegalReferenceDisplayItem) {
  const detail = reference.detail;
  const referenceId = detail?.referenceId || reference.key;
  const actCode = referenceId.split(":")[0];
  return detail?.bindingScope === "REPORT_TYPE"
    || [
      "BUILDING_ACT",
      "BUILDING_ACT_ENFORCEMENT_DECREE",
      "BUILDING_ACT_ENFORCEMENT_RULE",
      "CONSTRUCTION_SUPERVISION_DETAILED_STANDARD"
    ].includes(actCode);
}

function isSecondaryLegalReference(reference: LegalReferenceDisplayItem) {
  const detail = reference.detail;
  return detail?.resolutionSource === "LEGAL_DOMAIN_BINDING"
    && detail?.bindingScope === "CATALOG_ITEM";
}

function legalReviewConstraintNotes(findings: ReportPreflightReviewFindingResponse[]) {
  const resultFinding = legalReviewResultFinding(findings);
  if (!resultFinding) {
    return [];
  }
  const attributes = resultFinding.attributes ?? {};
  const notes = splitCsv(attributes.passBlockerCodes).map(legalPassBlockerLabel);
  const limitations = attributes.referenceCoverageLimitations?.trim();
  if (limitations) {
    notes.push(limitations);
  }
  if (attributes.technicalCriteriaPassEligible === "false") {
    notes.push("성능·규격 적합성은 시방서, 시험성적서, 자재승인서 같은 별도 증빙이 있어야 확정할 수 있습니다.");
  }
  if (attributes.finalPassEligible === "false" && notes.length === 0) {
    notes.push("현재 근거 강도 또는 증빙 범위가 PASS 판정 기준에 충분하지 않습니다.");
  }
  return [...new Set(notes)].slice(0, 5);
}

function splitCsv(value?: string | null) {
  return (value ?? "")
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
}

function legalPassBlockerLabel(code: string) {
  const labels: Record<string, string> = {
    PASS_BLOCKED_NO_LEGAL_REFERENCE: "PASS 판정에 사용할 업무-법령 근거가 없습니다.",
    PASS_BLOCKED_SEARCH_CANDIDATE_ONLY: "검색 후보 근거만으로는 PASS 판정을 내리지 않습니다.",
    PASS_BLOCKED_NO_PRIMARY_REFERENCE: "PASS 판정에는 주요 근거 조문이 필요합니다.",
    PASS_BLOCKED_LEGAL_REFERENCE_TOO_WEAK: "업무-법령 근거 강도가 PASS 판정에 충분하지 않습니다.",
    PASS_BLOCKED_REPORT_TYPE_ANCHOR_ONLY: "리포트 유형 공통 근거만으로는 개별 검사항목을 통과 처리하지 않습니다.",
    PASS_BLOCKED_NO_BUSINESS_ITEM_ANCHOR: "선택한 검사항목 단위의 업무-법령 바인딩 근거가 부족합니다.",
    PASS_BLOCKED_NO_DAILY_LOG_ENTRY: "감리일지 검토 항목이 없습니다.",
    PASS_BLOCKED_MISSING_SUPERVISION_CONTENT: "감리내용이 있는 검토 항목이 없습니다.",
    PASS_BLOCKED_MISSING_CHECKLIST_ITEM: "검사항목 코드가 연결된 감리 항목이 없습니다.",
    PASS_BLOCKED_MISSING_PHOTO_EVIDENCE: "검토 항목에 연결된 사진 증거가 없습니다.",
    PASS_BLOCKED_UNRESOLVED_PHOTO_REFERENCE: "감리 항목의 사진 참조가 업로드 사진과 일치하지 않습니다.",
    PASS_BLOCKED_GENERATION_BLOCKING_PHOTO_ISSUE: "문서 생성을 막는 사진 증거 문제가 있습니다."
  };
  return labels[code] ?? code;
}

function isLegalPreflightFinding(finding: ReportPreflightReviewFindingResponse) {
  const category = finding.attributes?.category ?? "";
  const code = finding.code ?? "";
  return finding.source === "LEGAL_REVIEW"
    || code.includes("LEGAL")
    || ["LEGAL_CONTEXT", "LEGAL_RISK", "COMPLIANCE"].includes(category)
    || finding.legalReferences.length > 0
    || finding.legalReferenceDetails.length > 0
    || finding.nextActions.some((action) => action.includes("LEGAL"));
}

function legalReferenceDisplay(reference: string) {
  const [left, version] = reference.split("@");
  const [actCode, articleKey] = left.split(":");
  const actLabel: Record<string, string> = {
    BUILDING_ACT: "건축법",
    BUILDING_ACT_ENFORCEMENT_DECREE: "건축법 시행령",
    BUILDING_ACT_ENFORCEMENT_RULE: "건축법 시행규칙",
    CONSTRUCTION_SUPERVISION_DETAILED_STANDARD: "건축공사 감리세부기준"
  };
  const readableAct = actLabel[actCode] ?? actCode;
  return version ? `${readableAct} ${articleKey} / ${version}` : `${readableAct} ${articleKey ?? ""}`.trim();
}

function legalReferenceDetailDisplay(reference: ReportPreflightLegalReferenceResponse) {
  const label = reference.label || legalReferenceDisplay(reference.referenceId);
  const source = legalReferenceSourceLabel(reference);
  const relevance = reference.relevance ? ` / ${legalReferenceRelevanceLabel(reference.relevance)}` : "";
  return `${label} · ${source}${relevance}`;
}

function legalReferenceSourceLabel(reference: ReportPreflightLegalReferenceResponse) {
  const source = reference.resolutionSource || reference.bindingScope;
  if (source === "LEGAL_DOMAIN_BINDING") {
    return "도메인 바인딩";
  }
  if (source === "LEGAL_CORPUS_SEARCH") {
    return "법령 검색 후보";
  }
  if (source === "CATALOG_ITEM") {
    return "카탈로그 항목 연결";
  }
  return source || "근거 후보";
}

function legalReferenceRelevanceLabel(relevance: string) {
  const labels: Record<string, string> = {
    PRIMARY: "주요 근거",
    SUPPORTING: "보조 근거",
    REFERENCE: "참고 근거",
    CANDIDATE: "후보"
  };
  return labels[relevance] ?? relevance;
}

function preflightNextActionLabel(action: string) {
  const labels: Record<string, string> = {
    ADD_SUPERVISION_EVIDENCE_CONTEXT: "감리 내용, 작업 위치, 사진/증빙 맥락을 보강",
    FIX_CATALOG_SELECTION: "공종/공정/검사항목 선택값 수정",
    REVIEW_LEGAL_EVIDENCE_CONTEXT: "법령 근거와 현장 증빙 연결 검토"
  };
  return labels[action] ?? action;
}

function preflightFixSuggestion(finding: ReportPreflightReviewFindingResponse) {
  return finding.attributes?.suggestion?.trim() ?? "";
}

function preflightFixReplacement(finding: ReportPreflightReviewFindingResponse) {
  return finding.attributes?.replacement?.trim() ?? "";
}

function preflightFixTargetLabel(finding: ReportPreflightReviewFindingResponse) {
  const location = finding.attributes?.relatedFieldPath?.trim() || finding.location?.trim() || "";
  if (location.endsWith("REMARKS.issueAndAction") || location.endsWith("REMARKS.payload.issueAndAction") || location === "REMARKS.issueAndAction") {
    return "지적사항 및 처리결과";
  }
  if (location.endsWith("REMARKS.nextAction") || location.endsWith("REMARKS.payload.nextAction") || location === "REMARKS.nextAction") {
    return "다음 조치";
  }
  const flatDaily = null as RegExpMatchArray | null;
  if (flatDaily) {
    return `감리내용 ${Number(flatDaily[1]) + 1}`;
  }
  const groupedDailyRow = location.match(/groups\[(\d+)]\.entries\[(\d+)]\.checklistRows\[(\d+)]\.(referenceNote|actionNote)$/);
  if (groupedDailyRow) {
    const rowLabel = groupedDailyRow[4] === "actionNote" ? "조치사항" : "기준·참고";
    return `세부 감리항목 ${Number(groupedDailyRow[1]) + 1}-${Number(groupedDailyRow[2]) + 1}-${Number(groupedDailyRow[3]) + 1} ${rowLabel}`;
  }
  return "";
}

function canApplyPreflightFindingFix(finding: ReportPreflightReviewFindingResponse) {
  if (finding.resolutionStatus !== "OPEN") {
    return false;
  }
  if (!["AI", "DETERMINISTIC", "LEGAL_REVIEW"].includes(finding.source) || !["LOW", "MEDIUM"].includes(finding.severity)) {
    return false;
  }
  if (finding.source === "LEGAL_REVIEW") {
    return ["COMPLIANCE", "LEGAL_RISK", "EVIDENCE"].includes(finding.attributes?.category ?? "");
  }
  return finding.attributes?.category === "WORDING";
}

function preflightSourceLabelV2(finding: ReportPreflightReviewFindingResponse) {
  if (finding.source === "DETERMINISTIC") {
    return "코드 검증";
  }
  if (finding.source === "AI") {
    return "AI 검토";
  }
  if (finding.source === "LEGAL_REVIEW") {
    return "법령검토";
  }
  return finding.source;
}

function preflightSourceLabel(finding: ReportPreflightReviewFindingResponse) {
  if (finding.source === "DETERMINISTIC") {
    return "코드 검증";
  }
  if (finding.source === "AI") {
    return "AI 검토";
  }
  return finding.source;
}

function narrativePolishSuggestionSourceLabel(source: DocumentNarrativePolishSuggestionResponse["source"] | string | null | undefined) {
  if (source === "RULE_BASED") {
    return "규칙 기반 제안";
  }
  return "AI 제안";
}

function narrativePolishSuggestionCountLabel(aiCount: number, ruleBasedCount: number) {
  const parts: string[] = [];
  if (aiCount > 0) {
    parts.push(`AI 제안 ${aiCount}건`);
  }
  if (ruleBasedCount > 0) {
    parts.push(`규칙 기반 제안 ${ruleBasedCount}건`);
  }
  return parts.length > 0 ? parts.join(", ") : "문장 제안 0건";
}

function findingSeverityLabel(severity: string) {
  if (severity === "CRITICAL") {
    return "긴급";
  }
  if (severity === "HIGH") {
    return "높음";
  }
  if (severity === "MEDIUM") {
    return "보통";
  }
  if (severity === "LOW") {
    return "낮음";
  }
  if (severity === "INFO") {
    return "정보";
  }
  return severity;
}

function harnessStatusLabel(status: string) {
  if (status === "COMPLETED" || status === "SUCCEEDED" || status === "READY") {
    return "AI 완료";
  }
  if (status === "FAILED") {
    return "AI 실패";
  }
  if (status === "RUNNING") {
    return "AI 실행 중";
  }
  if (status === "SKIPPED") {
    return "AI 생략";
  }
  return "AI 대기";
}

function isPreflightActive(run: ReportPreflightReviewRunResponse) {
  return run.status === "REQUESTED" || run.status === "RUNNING" || isPreflightAiPending(run);
}

function isPreflightAiPending(run: ReportPreflightReviewRunResponse) {
  if (isPreflightTerminalStatus(run.status)) {
    return false;
  }
  if (!run.aiReviewPlanned) {
    return false;
  }
  if (run.terminalReason === "DETERMINISTIC_PREFLIGHT_BLOCKED") {
    return false;
  }
  return !isPreflightHarnessTerminal(run);
}

function isPreflightHarnessTerminal(run: ReportPreflightReviewRunResponse) {
  return ["SUCCEEDED", "COMPLETED", "FAILED", "CANCELLED", "SKIPPED"].includes(run.harnessStatus ?? "");
}

function isPreflightTerminalStatus(status: string) {
  return status === "PASSED" || status === "NEEDS_ATTENTION" || status === "FAILED";
}

function isPreflightBlocking(run: ReportPreflightReviewRunResponse) {
  return run.status === "NEEDS_ATTENTION" || run.status === "FAILED";
}

function generationRevision(report: InspectionReport) {
  return report.submittedRevision ?? report.contentRevision;
}

function isBlockingFinding(finding: ReportPreflightReviewFindingResponse) {
  return finding.severity === "HIGH"
    || finding.severity === "CRITICAL"
    || finding.source === "AI"
    || finding.attributes?.approvalRequired === "true";
}

function isOpenBlockingFinding(finding: ReportPreflightReviewFindingResponse) {
  return isBlockingFinding(finding) && finding.resolutionStatus === "OPEN";
}

function requiresPreflightFindingAction(finding: ReportPreflightReviewFindingResponse) {
  if (finding.resolutionStatus !== "OPEN") {
    return false;
  }
  if (finding.attributes?.approvalRequired === "true") {
    return true;
  }
  if (finding.source === "LEGAL_REVIEW") {
    return false;
  }
  if (isBlockingFinding(finding)) {
    return true;
  }
  if (finding.source !== "DETERMINISTIC") {
    return true;
  }
  return finding.attributes?.approvalRequired === "true";
}

function findingMetaLabel(finding: ReportPreflightReviewFindingResponse) {
  const category = finding.attributes?.category;
  const location = finding.location || finding.code;
  return category ? `${category} / ${location}` : location;
}

function findingResolutionLabel(finding: ReportPreflightReviewFindingResponse) {
  if (finding.resolutionStatus === "RESOLVED") {
    return finding.resolvedAt ? `수정 완료 / ${new Date(finding.resolvedAt).toLocaleString()}` : "수정 완료";
  }
  if (finding.resolutionStatus === "ACCEPTED") {
    return finding.resolvedAt ? `리스크 수용 / ${new Date(finding.resolvedAt).toLocaleString()}` : "리스크 수용";
  }
  return "미처리";
}

function documentStatusSummary(latestJob: DocumentJobResponse | null, report: InspectionReport) {
  if (!latestJob) {
    return reportStatusLabel(report.status);
  }
  if (latestJob.status === "GENERATED") {
    return `완료 v${latestJob.reportRevision}`;
  }
  if (latestJob.status === "FAILED") {
    return `실패 v${latestJob.reportRevision}`;
  }
  if (latestJob.status === "REQUESTED" || latestJob.status === "GENERATING") {
    return `진행 중 ${latestJob.progressPercent}%`;
  }
  return jobStatusLabel(latestJob.status);
}

function documentListStatus(
  report: InspectionReport,
  latestJob: DocumentJobResponse | null,
  preflightRun: ReportPreflightReviewRunResponse | null,
  preflightRunLoading = false
) {
  if (latestJob && ["REQUESTED", "GENERATING"].includes(latestJob.status)) {
    return {
      detail: `문서 생성 ${latestJob.progressPercent}%`,
      label: "생성 중",
      tone: "slate"
    };
  }
  if (needsSubmitBeforeGeneration(report)) {
    return {
      detail: "수정본 제출 후 검토/생성 가능",
      label: "제출 필요",
      tone: "amber"
    };
  }
  if (!preflightRun && preflightRunLoading) {
    return {
      detail: "최근 검토 상태를 불러오는 중",
      label: "확인 중",
      tone: "slate"
    };
  }
  const current = preflightRun ? preflightRun.reportRevision === report.contentRevision : false;
  if (!preflightRun || !current) {
    return {
      detail: "상세에서 생성 전 검토 필요",
      label: "검토 필요",
      tone: "amber"
    };
  }
  if (isPreflightActive(preflightRun)) {
    return {
      detail: "검토 결과 정리 중",
      label: "검토 중",
      tone: "slate"
    };
  }
  if (preflightRun.status === "FAILED") {
    return {
      detail: "상세에서 다시 검토 필요",
      label: "검토 실패",
      tone: "red"
    };
  }
  if (isPreflightBlocking(preflightRun)) {
    return {
      detail: "수정/수용 후 생성 가능",
      label: "확인 필요",
      tone: "amber"
    };
  }
  if (preflightRun.status === "PASSED" && !isPreflightAiPending(preflightRun)) {
    return {
      detail: "DOCX/PDF/HTML 생성 가능",
      label: "생성 가능",
      tone: "green"
    };
  }
  return {
    detail: preflightStatusLabel(preflightRun, false),
    label: "확인 필요",
    tone: "amber"
  };
}

function jobStatusLabel(status: string) {
  if (status === "REQUESTED") {
    return "요청됨";
  }
  if (status === "GENERATING") {
    return "생성 중";
  }
  if (status === "GENERATED") {
    return "생성 완료";
  }
  if (status === "FAILED") {
    return "생성 실패";
  }
  if (status === "CANCELLED") {
    return "취소됨";
  }
  return status;
}

function reportStatusLabel(status: string) {
  if (status === "DRAFT") {
    return "초안";
  }
  if (status === "STEP_SAVED") {
    return "작성 중";
  }
  if (status === "READY_TO_GENERATE") {
    return "생성 대기";
  }
  if (status === "GENERATION_REQUESTED") {
    return "생성 요청";
  }
  if (status === "GENERATING") {
    return "생성 중";
  }
  if (status === "GENERATED") {
    return "생성 완료";
  }
  if (status === "FAILED") {
    return "생성 실패";
  }
  if (status === "DELIVERED") {
    return "전달 완료";
  }
  return status;
}

function documentAction(report: InspectionReport, latestJob: DocumentJobResponse | null, active: boolean) {
  if (active) {
    return { label: "생성 중", hint: "문서 생성이 진행 중입니다." };
  }
  if (needsSubmitBeforeGeneration(report)) {
    return { label: "제출 필요", hint: "수정본을 먼저 제출해야 합니다." };
  }
  if (latestJob?.status === "FAILED" || report.status === "FAILED") {
    return { label: "다시 생성", hint: "실패한 문서 생성을 다시 요청할 수 있습니다." };
  }
  if (report.status === "GENERATED") {
    return { label: "DOCX 재생성", hint: "같은 revision 기준으로 새 문서를 만들 수 있습니다." };
  }
  if (report.status === "READY_TO_GENERATE") {
    return { label: "DOCX 생성", hint: "제출된 revision으로 문서를 생성합니다." };
  }
  return { label: "DOCX 생성", hint: "문서 생성 가능한 상태가 아닙니다." };
}

function needsSubmitBeforeGeneration(report: InspectionReport) {
  if (report.status !== "STEP_SAVED") {
    return false;
  }
  const submittedRevision = report.submittedRevision ?? 0;
  return report.contentRevision > submittedRevision;
}

function deliveryLabel(delivery?: DocumentDeliveryRequestResponse) {
  if (!delivery) {
    return "다운로드 준비";
  }
  if (delivery.status === "COMPLETED" && delivery.downloadUrl) {
    return "다운로드 가능";
  }
  if (delivery.status === "REQUESTED" || delivery.status === "SENDING") {
    return "준비 중";
  }
  if (delivery.status === "FAILED") {
    return "준비 실패";
  }
  if (delivery.status === "CANCELLED") {
    return "취소됨";
  }
  return delivery.status;
}

function formatBytes(bytes: number) {
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`;
  }
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}
