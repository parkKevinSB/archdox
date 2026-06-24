import type { ReactNode } from "react";
import {
  InlineAlert,
  InlineNotice,
  Panel,
  StatusBadge,
  reportTypeLabel
} from "../../../components/common";
import type {
  InspectionReport,
  InspectionStep,
  Project,
  Site
} from "../../../types";
import { useReportWizard } from "../hooks/useReportWizard";
import { ReportStepRunner } from "./ReportStepRunner";

type ReportWizardProps = {
  canWriteReports: boolean;
  officeId: number;
  onReopenReport: (reportId: number) => Promise<void>;
  onSaveStep: (reportId: number, stepCode: string, payload: Record<string, unknown>) => Promise<InspectionStep>;
  onSubmitReport: (reportId: number) => Promise<void>;
  project: Project | null;
  renderChecklistPanel?: () => ReactNode;
  report: InspectionReport;
  site: Site | null;
  token: string;
};

export function ReportWizard({
  canWriteReports,
  officeId,
  onReopenReport,
  onSaveStep,
  onSubmitReport,
  project,
  renderChecklistPanel,
  report,
  site,
  token
}: ReportWizardProps) {
  const wizard = useReportWizard({
    canWriteReports,
    officeId,
    onReopenReport,
    onSaveStep,
    onSubmitReport,
    report,
    token
  });
  const {
    activeDefinition,
    activeStepCode,
    busy,
    canEdit,
    canReopen,
    canSubmit,
    completedCount,
    form,
    lastSavedAt,
    loadingSteps,
    moveStep,
    notice,
    reopenReport,
    savedSteps,
    saveActiveStep,
    selectStep,
    stepSaveStatus,
    stepDefinitions,
    stepError,
    submitReport,
    workflowDefinition
  } = wizard;

  return (
    <Panel title="리포트 작성" action={<StatusBadge status={report.status} />}>
      <div className="wizard-shell">
        <div className="wizard-title-row">
          <div>
            <p className="eyebrow">{report.reportNo}</p>
            <h2>{report.title || report.reportType}</h2>
          </div>
          <span className="panel-context">
            {completedCount}/{stepDefinitions.length} 단계 저장
          </span>
        </div>

        <div className="context-strip">
          <span>프로젝트: {project?.name ?? `project #${report.projectId}`}</span>
          <span>현장: {site?.name ?? (report.siteId ? `site #${report.siteId}` : "미지정")}</span>
          <span>감리업무: {supervisionWorkModeLabel(site?.supervisionWorkMode ?? workflowDefinition?.supervisionWorkMode)}</span>
          <span>유형: {reportTypeLabel(report.reportType)}</span>
          <span>흐름: {workflowDefinition?.title ?? "기본 작성 흐름"}</span>
        </div>

        {loadingSteps ? <InlineNotice message="작성 단계를 불러오는 중입니다." /> : null}
        {!canWriteReports ? <InlineNotice message="이 계정은 리포트 작성 권한이 없습니다." /> : null}
        {!canEdit && canReopen ? (
          <InlineNotice message="이미 제출 또는 생성된 리포트입니다. 내용을 바꾸려면 수정본 만들기를 눌러 새 revision을 작성하세요." />
        ) : null}
        {stepError ? <InlineAlert message={stepError} /> : null}
        {notice ? <InlineNotice message={notice} /> : null}

        <ReportStepRunner
          activeDefinition={activeDefinition}
          activeStepCode={activeStepCode}
          busy={busy}
          canEdit={canEdit}
          canReopen={canReopen}
          canSubmit={canSubmit}
          canWriteReports={canWriteReports}
          form={form}
          lastSavedAt={lastSavedAt}
          loadingSteps={loadingSteps}
          moveStep={moveStep}
          officeId={officeId}
          report={report}
          renderChecklistPanel={renderChecklistPanel}
          reopenReport={reopenReport}
          savedSteps={savedSteps}
          saveActiveStep={saveActiveStep}
          selectStep={selectStep}
          site={site}
          stepDefinitions={stepDefinitions}
          stepSaveStatus={stepSaveStatus}
          submitReport={submitReport}
          token={token}
        />
      </div>
    </Panel>
  );
}

function supervisionWorkModeLabel(value?: string | null) {
  switch (value) {
    case "RESIDENT":
      return "상주 감리";
    case "RESPONSIBLE_RESIDENT":
      return "책임상주 감리";
    case "NON_RESIDENT":
    default:
      return "비상주 감리";
  }
}
