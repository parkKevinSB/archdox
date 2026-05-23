import { Download, Eye, FileClock, FileText, History, Loader2, RefreshCw, UploadCloud, X } from "lucide-react";
import {
  EmptyState,
  InlineAlert,
  MetricTile,
  Panel,
  StatusBadge,
  ViewHeader
} from "../../../components/common";
import { useDocumentWorkspace } from "../hooks/useDocumentWorkspace";
import type {
  DocumentArtifactResponse,
  DocumentDeliveryRequestResponse,
  DocumentJobResponse,
  InspectionReport,
  Project
} from "../types";

type DocumentWorkspaceProps = {
  officeId: number | null;
  onRefreshWorkspace: () => Promise<void>;
  projects: Project[];
  reports: InspectionReport[];
  token: string;
};

export function DocumentWorkspace({
  officeId,
  onRefreshWorkspace,
  projects,
  reports,
  token
}: DocumentWorkspaceProps) {
  const workspace = useDocumentWorkspace({ officeId, onRefreshWorkspace, reports, token });
  const readyReports = workspace.documentReports.filter((report) => report.status === "READY_TO_GENERATE");
  const activeReports = workspace.documentReports.filter((report) =>
    ["GENERATION_REQUESTED", "GENERATING"].includes(report.status)
  );
  const staleDraftReports = workspace.documentReports.filter((report) => needsSubmitBeforeGeneration(report));
  const generatedReports = workspace.documentReports.filter((report) => report.status === "GENERATED");

  return (
    <div className="view-stack">
      <ViewHeader title="문서" text="제출된 리포트의 문서를 생성하고, revision별 생성 이력과 다운로드 상태를 확인합니다." />

      <div className="metric-row compact">
        <MetricTile label="생성 가능" value={readyReports.length} detail="제출 완료" />
        <MetricTile label="재제출 필요" value={staleDraftReports.length} detail="수정본 작성중" />
        <MetricTile label="진행 중" value={activeReports.length} detail="생성 요청/진행" />
        <MetricTile label="완료" value={generatedReports.length} detail="다운로드 가능" />
      </div>

      {workspace.error ? <InlineAlert message={workspace.error instanceof Error ? workspace.error.message : "문서 작업을 불러오지 못했습니다."} /> : null}

      <div className="document-workspace">
        <Panel
          title="문서 생성 및 이력"
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
                return (
                  <DocumentReportCard
                    creating={workspace.creatingReportId === report.id}
                    creatingOutputFormat={workspace.creatingOutputFormat}
                    deliveriesByArtifact={workspace.deliveriesByArtifact}
                    downloadingArtifactId={workspace.downloadingArtifactId}
                    jobs={jobs}
                    key={report.id}
                    projectName={project?.name}
                    report={report}
                    previewingArtifactId={workspace.previewingArtifactId}
                    requestingDeliveryArtifactId={workspace.requestingDeliveryArtifactId}
                    onCreate={() => workspace.createDocumentJob({ reportId: report.id, outputFormat: "DOCX" })}
                    onCreatePreview={() => workspace.createDocumentJob({ reportId: report.id, outputFormat: "HTML" })}
                    onDownloadPrepared={(artifact, delivery) => workspace.downloadPreparedArtifact({ artifact, delivery })}
                    onPreviewArtifact={(artifact, job) => workspace.previewArtifact({ artifact, job })}
                    onRequestDelivery={(artifact, job) => workspace.requestArtifactDelivery({ artifact, job })}
                  />
                );
              })}
            </div>
          )}
        </Panel>

        <Panel title="문서 정책">
          <div className="settings-list">
            <div>
              <strong>revision 기준 생성</strong>
              <span>문서 job은 생성 당시의 report revision을 저장합니다. 수정본을 만들면 이전 문서는 이력으로 남습니다.</span>
            </div>
            <div>
              <strong>수정 후 재제출</strong>
              <span>생성 완료 리포트를 수정하면 먼저 다시 제출해야 새 문서를 생성할 수 있습니다.</span>
            </div>
            <div>
              <strong>다운로드 이력</strong>
              <span>최신 생성본과 이전 생성본을 구분해서 보여주고, 필요한 산출물만 다운로드 준비합니다.</span>
            </div>
          </div>
        </Panel>
      </div>
      {workspace.preview ? (
        <DocumentPreviewDialog preview={workspace.preview} onClose={workspace.closePreview} />
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
  projectName,
  report,
  previewingArtifactId,
  requestingDeliveryArtifactId,
  onCreate,
  onCreatePreview,
  onDownloadPrepared,
  onPreviewArtifact,
  onRequestDelivery
}: {
  creating: boolean;
  creatingOutputFormat: string | null;
  deliveriesByArtifact: Record<number, DocumentDeliveryRequestResponse>;
  downloadingArtifactId: number | null;
  jobs: DocumentJobResponse[];
  projectName?: string;
  report: InspectionReport;
  previewingArtifactId: number | null;
  requestingDeliveryArtifactId: number | null;
  onCreate: () => Promise<DocumentJobResponse>;
  onCreatePreview: () => Promise<DocumentJobResponse>;
  onDownloadPrepared: (artifact: DocumentArtifactResponse, delivery: DocumentDeliveryRequestResponse) => Promise<unknown>;
  onPreviewArtifact: (artifact: DocumentArtifactResponse, job: DocumentJobResponse) => Promise<unknown>;
  onRequestDelivery: (artifact: DocumentArtifactResponse, job: DocumentJobResponse) => Promise<unknown>;
}) {
  const latestJob = jobs[0] ?? null;
  const activeJob = jobs.find((job) => ["REQUESTED", "GENERATING"].includes(job.status)) ?? null;
  const generatedJobs = jobs.filter((job) => job.status === "GENERATED");
  const latestGeneratedJob = generatedJobs[0] ?? null;
  const active = Boolean(activeJob);
  const canCreate = ["READY_TO_GENERATE", "GENERATED", "FAILED"].includes(report.status) && !active;
  const action = documentAction(report, latestJob, active);
  const creatingDocx = creating && creatingOutputFormat !== "HTML";
  const creatingHtml = creating && creatingOutputFormat === "HTML";

  return (
    <article className="document-card">
      <div className="document-card-head">
        <div className="row-icon blue">
          <FileText size={18} />
        </div>
        <div>
          <strong>{report.title || report.reportNo}</strong>
          <span>{projectName ?? `project #${report.projectId}`} · {report.reportType}</span>
        </div>
        <StatusBadge status={latestJob?.status ?? report.status} />
      </div>

      <RevisionStrip report={report} latestJob={latestJob} latestGeneratedJob={latestGeneratedJob} />

      {activeJob || latestJob ? (
        <JobProgress job={activeJob ?? latestJob!} />
      ) : (
        <p className="document-muted">아직 문서 생성 요청이 없습니다.</p>
      )}

      <InlineDocumentGuide report={report} latestGeneratedJob={latestGeneratedJob} />

      {generatedJobs.length > 0 ? (
        <div className="document-history">
          <div className="document-history-title">
            <History size={15} />
            <strong>생성 이력</strong>
            <span>{generatedJobs.length}개 revision/job</span>
          </div>
          {generatedJobs.map((job, index) => (
            <GeneratedJobArtifacts
              deliveriesByArtifact={deliveriesByArtifact}
              downloadingArtifactId={downloadingArtifactId}
              isLatest={index === 0}
              job={job}
              key={job.id}
              previewingArtifactId={previewingArtifactId}
              requestingDeliveryArtifactId={requestingDeliveryArtifactId}
              onDownloadPrepared={onDownloadPrepared}
              onPreviewArtifact={onPreviewArtifact}
              onRequestDelivery={onRequestDelivery}
            />
          ))}
          {generatedJobs.length === 1 ? (
            <p className="document-muted">아직 이전 생성본은 없습니다. 수정 전에 생성 완료된 문서가 있어야 이전본으로 남습니다.</p>
          ) : null}
        </div>
      ) : null}

      <div className="document-actions">
        <span className="document-action-hint">{action.hint}</span>
        <button className="secondary-button" disabled={!canCreate || creating} onClick={onCreatePreview} type="button">
          {creatingHtml ? <Loader2 className="spin" size={17} /> : <Eye size={17} />}
          HTML Preview
        </button>
        <button className="primary-button" disabled={!canCreate || creating} onClick={onCreate} type="button">
          {creatingDocx || active ? <Loader2 className="spin" size={17} /> : <UploadCloud size={17} />}
          {action.label}
        </button>
      </div>
    </article>
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
      <section className="document-preview-dialog" role="dialog" aria-modal="true" aria-label="HTML document preview">
        <header className="document-preview-header">
          <div>
            <span>HTML Preview</span>
            <strong>{preview.artifact.fileName}</strong>
            <small>job #{preview.job.id} · revision v{preview.job.reportRevision}</small>
          </div>
          <button className="icon-button" onClick={onClose} type="button" aria-label="Close preview">
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
        <span>{job.progressStep} · v{job.reportRevision}</span>
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
            <div
              className="artifact-row"
              key={artifact.id}
            >
              <span>
                <strong>{artifact.fileName}</strong>
                <small>
                  {artifact.artifactType} · {formatBytes(artifact.bytes)} · {deliveryLabel(delivery)}
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
                    Preview
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
                  Download
                </button>
              </div>
            </div>
          );
        })}
      </div>
    </details>
  );
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
    return { label: "재생성", hint: "같은 revision 기준으로 새 문서를 만들 수 있습니다." };
  }
  if (report.status === "READY_TO_GENERATE") {
    return { label: "문서 생성", hint: "제출된 revision으로 문서를 생성합니다." };
  }
  return { label: "문서 생성", hint: "문서 생성 가능한 상태가 아닙니다." };
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
