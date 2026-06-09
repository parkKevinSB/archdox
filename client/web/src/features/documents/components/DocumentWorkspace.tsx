import { useEffect, useRef, useState } from "react";
import type { PointerEvent } from "react";
import { AlertTriangle, CheckCircle2, Download, Eye, FileClock, FileText, Loader2, PenLine, RefreshCw, ShieldCheck, Trash2, UploadCloud, X } from "lucide-react";
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
  DocumentOutputFormat,
  DocumentSignatureInput,
  InspectionReport,
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
  const requestSignedGeneration = (report: InspectionReport, outputFormat: DocumentOutputFormat) => {
    setSignatureError(null);
    setSignatureRequest({ outputFormat, report });
  };
  const submitSignature = async (signature: DocumentSignatureInput) => {
    if (!signatureRequest) {
      return;
    }
    try {
      await workspace.createDocumentJob({
        outputFormat: signatureRequest.outputFormat,
        reportId: signatureRequest.report.id,
        signature
      });
      setSignatureRequest(null);
      setSignatureError(null);
    } catch (error) {
      setSignatureError(error instanceof Error ? error.message : "문서 생성 요청에 실패했습니다.");
    }
  };
  const submitWithoutSignature = async () => {
    if (!signatureRequest) {
      return;
    }
    try {
      await workspace.createDocumentJob({
        outputFormat: signatureRequest.outputFormat,
        reportId: signatureRequest.report.id
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
          title="문서 생성과 이력"
          action={
            <button className="text-button" onClick={() => workspace.refreshJobs()} type="button">
              {workspace.loading ? <Loader2 className="spin" size={16} /> : <RefreshCw size={16} />}
              새로고침
            </button>
          }
        >
          {workspace.documentReports.length === 0 ? (
            <EmptyState title="문서 작업 대상이 없습니다" text="리포트를 작성하고 제출하면 문서 탭에 표시됩니다." />
          ) : (
            <div className="document-list">
              {workspace.documentReports.map((report) => {
                const project = projects.find((item) => item.id === report.projectId);
                const jobs = workspace.jobsByReport[report.id] ?? [];
                const preflightRun = workspace.preflightRunsByReport[report.id]?.[0] ?? null;
                const preflightFindings = preflightRun ? workspace.preflightFindingsByRun[preflightRun.id] ?? [] : [];
                return (
                  <DocumentReportCard
                    creating={workspace.creatingReportId === report.id}
                    creatingOutputFormat={workspace.creatingOutputFormat}
                    deliveriesByArtifact={workspace.deliveriesByArtifact}
                    downloadingArtifactId={workspace.downloadingArtifactId}
                    jobs={jobs}
                    key={report.id}
                    preflightFindings={preflightFindings}
                    preflightRun={preflightRun}
                    previewingArtifactId={workspace.previewingArtifactId}
                    projectName={project?.name}
                    report={report}
                    requestingDeliveryArtifactId={workspace.requestingDeliveryArtifactId}
                    reviewing={workspace.reviewingReportId === report.id}
                    resolvingPreflightFindingId={workspace.resolvingPreflightFindingId}
                    onCreate={() => requestSignedGeneration(report, "DOCX")}
                    onCreatePdf={() => requestSignedGeneration(report, "PDF")}
                    onCreatePreview={() => requestSignedGeneration(report, "HTML")}
                    onDownloadPrepared={(artifact, delivery) => workspace.downloadPreparedArtifact({ artifact, delivery })}
                    onPreviewArtifact={(artifact, job) => workspace.previewArtifact({ artifact, job })}
                    onRequestPreflightReview={() => workspace.requestPreflightReview(report.id)}
                    onResolvePreflightFinding={(runId, findingId, status) =>
                      workspace.resolvePreflightFinding({ reportId: report.id, runId, findingId, status })
                    }
                    onRequestDelivery={(artifact, job) => workspace.requestArtifactDelivery({ artifact, job })}
                  />
                );
              })}
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
          outputFormat={signatureRequest.outputFormat}
          report={signatureRequest.report}
          submitting={workspace.creatingReportId === signatureRequest.report.id}
          onClose={() => {
            if (!workspace.creatingReportId) {
              setSignatureRequest(null);
              setSignatureError(null);
            }
          }}
          onSkip={submitWithoutSignature}
          onSubmit={submitSignature}
        />
      ) : null}
    </div>
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
  resolvingPreflightFindingId,
  onCreate,
  onCreatePdf,
  onCreatePreview,
  onDownloadPrepared,
  onPreviewArtifact,
  onRequestPreflightReview,
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
  resolvingPreflightFindingId: number | null;
  onCreate: () => void;
  onCreatePdf: () => void;
  onCreatePreview: () => void;
  onDownloadPrepared: (artifact: DocumentArtifactResponse, delivery: DocumentDeliveryRequestResponse) => Promise<unknown>;
  onPreviewArtifact: (artifact: DocumentArtifactResponse, job: DocumentJobResponse) => Promise<unknown>;
  onRequestPreflightReview: () => Promise<ReportPreflightReviewRunResponse>;
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
  const preflightCurrent = preflightRun ? preflightRun.reportRevision === generationRevision(report) : false;
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
  const detailOpen = preflightBusy || (!preflightReady && ["READY_TO_GENERATE", "GENERATED", "FAILED"].includes(report.status));
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

      <details className="document-detail-section" open={detailOpen}>
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
          currentRevision={generationRevision(report)}
          report={report}
          reviewing={reviewing || preflightActive}
          resolvingFindingId={resolvingPreflightFindingId}
          run={preflightRun}
          onRequestReview={onRequestPreflightReview}
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
          <button className="secondary-button" disabled={!canCreate || creating} onClick={onCreatePreview} type="button">
            {creatingHtml ? <Loader2 className="spin" size={17} /> : <Eye size={17} />}
            HTML 미리보기
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
  currentOffice,
  currentUser,
  error,
  outputFormat,
  report,
  submitting,
  onClose,
  onSkip,
  onSubmit
}: {
  currentOffice: Office | null;
  currentUser: MeResponse;
  error: string | null;
  outputFormat: DocumentOutputFormat;
  report: InspectionReport;
  submitting: boolean;
  onClose: () => void;
  onSkip: () => Promise<void>;
  onSubmit: (signature: DocumentSignatureInput) => Promise<void>;
}) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const drawingRef = useRef(false);
  const [empty, setEmpty] = useState(true);
  const [localError, setLocalError] = useState<string | null>(null);
  const [name, setName] = useState(() => localStorage.getItem("archdox.signature.name") ?? defaultSignatureName(currentUser));
  const [role, setRole] = useState(() => {
    if (currentOffice?.type === "PERSONAL") {
      return defaultSignatureRole(report, currentOffice);
    }
    return localStorage.getItem("archdox.signature.role") ?? defaultSignatureRole(report, currentOffice);
  });

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) {
      return;
    }
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
    resizeCanvas();
    window.addEventListener("resize", resizeCanvas);
    return () => window.removeEventListener("resize", resizeCanvas);
  }, []);

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
    setLocalError(null);
    await onSubmit({
      signedByName: trimmedName,
      signedByRole: role.trim() || null,
      signatureImageDataUrl: signatureDataUrl(canvasRef.current),
      signatureImageMimeType: "image/png"
    });
  };

  return (
    <div className="document-preview-backdrop" role="presentation">
      <section className="document-signature-dialog" role="dialog" aria-modal="true" aria-label="문서 서명">
        <header className="document-preview-header">
          <div>
            <span>문서 생성 서명</span>
            <strong>{report.title || report.reportNo}</strong>
            <small>{outputFormat} 생성 전에 필요한 경우 서명을 남길 수 있습니다. 서명 없이 생성하면 서명란은 비워집니다.</small>
          </div>
          <button className="icon-button" disabled={submitting} onClick={onClose} type="button" aria-label="서명 창 닫기">
            <X size={18} />
          </button>
        </header>
        <div className="signature-dialog-body">
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
          {localError || error ? <InlineAlert message={localError ?? error ?? ""} /> : null}
        </div>
        <footer className="signature-dialog-actions">
          <button className="secondary-button" disabled={submitting} onClick={onClose} type="button">
            취소
          </button>
          <button className="secondary-button" disabled={submitting} onClick={onSkip} type="button">
            {submitting ? <Loader2 className="spin" size={17} /> : <FileText size={17} />}
            서명 없이 생성
          </button>
          <button className="primary-button" disabled={submitting} onClick={submit} type="button">
            {submitting ? <Loader2 className="spin" size={17} /> : <PenLine size={17} />}
            서명 후 생성
          </button>
        </footer>
      </section>
    </div>
  );
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
  resolvingFindingId,
  run,
  onRequestReview,
  onResolveFinding
}: {
  currentRevision: number;
  findings: ReportPreflightReviewFindingResponse[];
  report: InspectionReport;
  reviewing: boolean;
  resolvingFindingId: number | null;
  run: ReportPreflightReviewRunResponse | null;
  onRequestReview: () => Promise<ReportPreflightReviewRunResponse>;
  onResolveFinding: (
    runId: number,
    findingId: number,
    status: ReportPreflightFindingResolutionStatus
  ) => Promise<ReportPreflightReviewFindingResponse>;
}) {
  const blockingCount = findings.filter((finding) => isOpenBlockingFinding(finding)).length;
  const deterministicFindings = findings.filter((finding) => finding.source === "DETERMINISTIC");
  const aiFindings = findings.filter((finding) => finding.source !== "DETERMINISTIC");
  const deterministicBlockingCount = deterministicFindings.filter((finding) => isOpenBlockingFinding(finding)).length;
  const aiAttentionCount = aiFindings.filter((finding) => requiresPreflightFindingAction(finding)).length;
  const warningCount = findings.filter((finding) => !isOpenBlockingFinding(finding)).length;
  const canReview = ["READY_TO_GENERATE", "GENERATED", "FAILED", "STEP_SAVED"].includes(report.status);
  const stale = run ? run.reportRevision !== currentRevision : false;
  const warning = stale || (run && isPreflightBlocking(run));
  const aiMode = run ? preflightAiMode(run) : null;
  const progress = reviewing || (run && isPreflightActive(run))
    ? preflightProgress(run, reviewing)
    : null;

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

      {findings.length > 0 ? (
        <div className="preflight-finding-list">
          {findings.slice(0, 4).map((finding) => {
            const legalReferenceDetails = finding.legalReferenceDetails ?? [];
            const legalReferenceCount = legalReferenceDetails.length > 0
              ? legalReferenceDetails.length
              : finding.legalReferences.length;
            return (
            <div className={requiresPreflightFindingAction(finding) ? "preflight-finding blocking" : "preflight-finding"} key={finding.id}>
              {requiresPreflightFindingAction(finding) ? <AlertTriangle size={15} /> : <CheckCircle2 size={15} />}
              <span>
                <strong>{finding.message}</strong>
                <small>
                  {preflightSourceLabel(finding)} / {findingSeverityLabel(finding.severity)} / {findingMetaLabel(finding)}
                </small>
                <small className="preflight-resolution">{findingResolutionLabel(finding)}</small>
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
                  <button
                    className="secondary-button compact-button"
                    disabled={resolvingFindingId === finding.id}
                    onClick={() => onResolveFinding(run.id, finding.id, "RESOLVED")}
                    type="button"
                  >
                    {resolvingFindingId === finding.id ? <Loader2 className="spin" size={14} /> : <CheckCircle2 size={14} />}
                    수정 완료
                  </button>
                  <button
                    className="secondary-button compact-button"
                    disabled={resolvingFindingId === finding.id}
                    onClick={() => onResolveFinding(run.id, finding.id, "ACCEPTED")}
                    type="button"
                  >
                    리스크 수용
                  </button>
                </div>
              ) : null}
            </div>
            );
          })}
          {findings.length > 4 ? (
            <span className="preflight-more">외 {findings.length - 4}건</span>
          ) : null}
        </div>
      ) : null}

      <div className="preflight-actions">
        <button className="secondary-button compact-button" disabled={!canReview || reviewing} onClick={onRequestReview} type="button">
          {reviewing ? <Loader2 className="spin" size={15} /> : <ShieldCheck size={15} />}
          생성 전 검토
        </button>
      </div>
    </section>
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
        percent: 90,
        label: "결과 정리 중",
        detail: "AI 응답을 finding으로 변환하고 있습니다."
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
  if (!run.aiReviewPlanned) {
    return "정책상 AI 검토 생략";
  }
  if (attentionCount > 0) {
    return `AI 초안 확인 필요 ${attentionCount}건`;
  }
  const status = harnessStatusLabel(run.harnessStatus ?? "QUEUED");
  const model = run.aiModelId ?? run.aiProviderCode ?? "설정된 모델";
  const attempt = run.harnessAttempt > 0 ? ` / ${run.harnessAttempt}회 시도` : "";
  return `${status}${attempt} / ${model}`;
}

function preflightAiMode(run: ReportPreflightReviewRunResponse) {
  if (!run.aiReviewPlanned) {
    return {
      kind: "skipped",
      title: "AI 검토 생략",
      description: "사무소 정책 또는 서버 설정에 따라 코드 검증만 실행했습니다."
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

function preflightSourceLabel(finding: ReportPreflightReviewFindingResponse) {
  if (finding.source === "DETERMINISTIC") {
    return "코드 검증";
  }
  if (finding.source === "AI") {
    return "AI 검토";
  }
  return finding.source;
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
  if (!run.aiReviewPlanned) {
    return false;
  }
  if (run.terminalReason === "DETERMINISTIC_PREFLIGHT_BLOCKED") {
    return false;
  }
  return !isPreflightHarnessTerminal(run);
}

function isPreflightHarnessTerminal(run: ReportPreflightReviewRunResponse) {
  return ["SUCCEEDED", "FAILED", "CANCELLED", "SKIPPED"].includes(run.harnessStatus ?? "");
}

function isPreflightBlocking(run: ReportPreflightReviewRunResponse) {
  return run.status === "NEEDS_ATTENTION" || run.status === "FAILED";
}

function generationRevision(report: InspectionReport) {
  return report.submittedRevision ?? report.contentRevision;
}

function isBlockingFinding(finding: ReportPreflightReviewFindingResponse) {
  return finding.severity === "HIGH" || finding.severity === "CRITICAL";
}

function isOpenBlockingFinding(finding: ReportPreflightReviewFindingResponse) {
  return isBlockingFinding(finding) && finding.resolutionStatus === "OPEN";
}

function requiresPreflightFindingAction(finding: ReportPreflightReviewFindingResponse) {
  if (finding.resolutionStatus !== "OPEN") {
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
