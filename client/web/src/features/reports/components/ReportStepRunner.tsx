import { PencilLine, Save, Send } from "lucide-react";
import type { ReactNode } from "react";
import type { UseFormReturn } from "react-hook-form";
import { reportStepRegistry } from "../flow/reportStepRegistry";
import type { StepSaveStatus } from "../hooks/useReportWizard";
import type {
  InspectionReport,
  InspectionStep,
  ReportStepCode,
  ReportStepDefinition,
  ReportWizardFormValues
} from "../types";

type ReportStepRunnerProps = {
  activeDefinition: ReportStepDefinition;
  activeStepCode: ReportStepCode;
  busy: boolean;
  canEdit: boolean;
  canReopen: boolean;
  canSubmit: boolean;
  canWriteReports: boolean;
  form: UseFormReturn<ReportWizardFormValues>;
  lastSavedAt: string | null;
  loadingSteps: boolean;
  moveStep: (offset: number) => Promise<void>;
  officeId: number;
  reopenReport: () => Promise<void>;
  report: InspectionReport;
  renderChecklistPanel?: () => ReactNode;
  savedSteps: Record<string, InspectionStep>;
  saveActiveStep: (values: ReportWizardFormValues) => Promise<boolean>;
  selectStep: (stepCode: ReportStepCode) => Promise<void>;
  stepSaveStatus: StepSaveStatus;
  stepDefinitions: ReportStepDefinition[];
  submitReport: () => Promise<void>;
  token: string;
};

export function ReportStepRunner({
  activeDefinition,
  activeStepCode,
  busy,
  canEdit,
  canReopen,
  canSubmit,
  canWriteReports,
  form,
  lastSavedAt,
  loadingSteps,
  moveStep,
  officeId,
  reopenReport,
  report,
  renderChecklistPanel,
  savedSteps,
  saveActiveStep,
  selectStep,
  stepSaveStatus,
  stepDefinitions,
  submitReport,
  token
}: ReportStepRunnerProps) {
  const StepComponent = reportStepRegistry[activeDefinition.stepType] ?? reportStepRegistry.FORM;
  const activeIndex = stepDefinitions.findIndex((definition) => definition.code === activeStepCode);
  const firstStep = activeIndex <= 0;
  const lastStep = activeIndex >= stepDefinitions.length - 1;

  return (
    <div className="wizard-grid">
      <div className="step-rail" aria-label="리포트 작성 단계">
        {stepDefinitions.map((definition, index) => (
          <button
            className={definition.code === activeStepCode ? "step-button active" : "step-button"}
            disabled={busy || loadingSteps}
            key={definition.code}
            onClick={() => selectStep(definition.code)}
            type="button"
          >
            <span>{index + 1}</span>
            <strong>{definition.title}</strong>
            <small>{savedSteps[definition.code] ? "저장됨" : "작성 전"}</small>
          </button>
        ))}
      </div>

      <form className="wizard-form" onSubmit={form.handleSubmit(() => undefined)}>
        <StepComponent
          canWriteReports={canWriteReports && canEdit}
          definition={activeDefinition}
          officeId={officeId}
          register={form.register}
          report={report}
          renderChecklistPanel={renderChecklistPanel}
          revision={savedSteps[activeDefinition.code]?.clientRevision}
          token={token}
        />

        <div className="wizard-actions">
          <span className={`save-state ${stepSaveStatus}`}>{saveStateLabel(stepSaveStatus, lastSavedAt)}</span>
          <button className="secondary-button" disabled={busy || firstStep} onClick={() => moveStep(-1)} type="button">
            이전
          </button>
          <button className="secondary-button" disabled={busy || lastStep} onClick={() => moveStep(1)} type="button">
            다음
          </button>
          <button
            className="secondary-button"
            disabled={busy || loadingSteps || !canEdit}
            onClick={form.handleSubmit(saveActiveStep)}
            type="button"
          >
            <Save size={17} />
            저장
          </button>
          {canReopen && !canEdit ? (
            <button className="secondary-button" disabled={busy} onClick={reopenReport} type="button">
              <PencilLine size={17} />
              수정본 만들기
            </button>
          ) : null}
          <button className="primary-button" disabled={busy || !canSubmit} onClick={submitReport} type="button">
            <Send size={17} />
            제출
          </button>
        </div>
      </form>
    </div>
  );
}

function saveStateLabel(status: StepSaveStatus, lastSavedAt: string | null) {
  if (status === "saving") {
    return "저장 중";
  }
  if (status === "dirty") {
    return "변경사항 있음";
  }
  if (status === "failed") {
    return "저장 실패";
  }
  if (status === "saved") {
    return lastSavedAt ? `저장됨 ${formatTime(lastSavedAt)}` : "저장됨";
  }
  return "자동저장 대기";
}

function formatTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "";
  }
  return date.toLocaleTimeString("ko-KR", { hour: "2-digit", minute: "2-digit" });
}
