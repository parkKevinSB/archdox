import { useEffect, useMemo, useState } from "react";
import { useForm } from "react-hook-form";
import { getInspectionSteps, getReportWorkflowDefinition } from "../api";
import {
  isReportStepCode,
  payloadFieldValue,
  payloadFromForm,
  reportStepDefinitions
} from "../reportSteps";
import type {
  InspectionReport,
  InspectionStep,
  ReportFlowDefinition,
  ReportStepCode,
  ReportStepDefinition,
  ReportWizardFormValues
} from "../types";

type UseReportWizardOptions = {
  canWriteReports: boolean;
  officeId: number;
  onReopenReport: (reportId: number) => Promise<void>;
  onSaveStep: (reportId: number, stepCode: string, payload: Record<string, unknown>) => Promise<InspectionStep>;
  onSubmitReport: (reportId: number) => Promise<void>;
  report: InspectionReport;
  token: string;
};

export type StepSaveStatus = "idle" | "dirty" | "saving" | "saved" | "failed";

export function useReportWizard({
  canWriteReports,
  officeId,
  onReopenReport,
  onSaveStep,
  onSubmitReport,
  report,
  token
}: UseReportWizardOptions) {
  const form = useForm<ReportWizardFormValues>({ defaultValues: {} });
  const [stepDefinitions, setStepDefinitions] = useState<ReportStepDefinition[]>(reportStepDefinitions);
  const [workflowDefinition, setWorkflowDefinition] = useState<ReportFlowDefinition | null>(null);
  const [activeStepCode, setActiveStepCode] = useState<ReportStepCode>(reportStepDefinitions[0].code);
  const [savedSteps, setSavedSteps] = useState<Record<string, InspectionStep>>({});
  const [loadingSteps, setLoadingSteps] = useState(false);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [stepError, setStepError] = useState<string | null>(null);
  const [stepSaveStatus, setStepSaveStatus] = useState<StepSaveStatus>("idle");
  const [lastSavedAt, setLastSavedAt] = useState<string | null>(null);
  const isDirty = form.formState.isDirty;

  const activeDefinition = useMemo(
    () => stepDefinitions.find((definition) => definition.code === activeStepCode) ?? stepDefinitions[0],
    [activeStepCode, stepDefinitions]
  );
  const completedCount = useMemo(
    () => stepDefinitions.filter((definition) => savedSteps[definition.code]).length,
    [savedSteps, stepDefinitions]
  );
  const reportWriteAllowed = report.writeAllowed ?? canWriteReports;
  const canEdit = reportWriteAllowed && ["DRAFT", "STEP_SAVED"].includes(report.status);
  const canReopen = report.reopenAllowed ?? (
    reportWriteAllowed && ["READY_TO_GENERATE", "GENERATED", "DELIVERED", "FAILED"].includes(report.status)
  );
  const canSubmit = canEdit;

  useEffect(() => {
    let cancelled = false;
    setLoadingSteps(true);
    setStepError(null);
    setNotice(null);
    Promise.all([
      getReportWorkflowDefinition(token, officeId, report.id).catch(() => null),
      getInspectionSteps(token, officeId, report.id)
    ])
      .then(([workflow, steps]) => {
        if (cancelled) {
          return;
        }
        const nextDefinitions = workflow?.steps?.length ? workflow.steps : reportStepDefinitions;
        const nextSteps = Object.fromEntries(steps.map((step) => [step.stepCode, step]));
        setWorkflowDefinition(workflow);
        setStepDefinitions(nextDefinitions);
        setSavedSteps(nextSteps);
        setActiveStepCode(isReportStepCode(report.currentStep, nextDefinitions) ? report.currentStep : nextDefinitions[0].code);
      })
      .catch((err) => {
        if (!cancelled) {
          setStepError(err instanceof Error ? err.message : "작성 단계를 불러오지 못했습니다.");
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoadingSteps(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [officeId, report.currentStep, report.id, token]);

  useEffect(() => {
    const savedStep = savedSteps[activeStepCode];
    const payload = savedStep?.payload ?? {};
    form.reset(Object.fromEntries(activeDefinition.fields.map((field) => [field.key, payloadFieldValue(payload, field.key)])));
    setLastSavedAt(savedStep?.savedAt ?? null);
    setStepSaveStatus(savedStep ? "saved" : "idle");
  }, [activeDefinition, activeStepCode, form, report.id, savedSteps]);

  useEffect(() => {
    if (isDirty && stepSaveStatus !== "saving") {
      setStepSaveStatus("dirty");
    }
  }, [isDirty, stepSaveStatus]);

  async function saveCurrentStep(values = form.getValues()) {
    if (!reportWriteAllowed) {
      setStepSaveStatus("failed");
      setStepError("리포트 작성 권한이 필요합니다.");
      return false;
    }
    if (!canEdit) {
      setStepSaveStatus("failed");
      setStepError("이미 제출 또는 생성된 리포트입니다. 수정하려면 먼저 수정본을 만드세요.");
      return false;
    }
    if (busy || loadingSteps) {
      return false;
    }
    if (!form.formState.isDirty && savedSteps[activeDefinition.code]) {
      setStepSaveStatus("saved");
      setNotice(`${activeDefinition.title} 단계가 이미 저장되어 있습니다.`);
      return true;
    }
    setBusy(true);
    setStepSaveStatus("saving");
    setStepError(null);
    setNotice(null);
    try {
      const savedStep = await onSaveStep(report.id, activeDefinition.code, payloadFromForm(activeDefinition, values));
      setSavedSteps((current) => ({ ...current, [savedStep.stepCode]: savedStep }));
      setLastSavedAt(savedStep.savedAt);
      setStepSaveStatus("saved");
      form.reset(values);
      setNotice(`${activeDefinition.title} 단계가 저장되었습니다.`);
      return true;
    } catch (err) {
      setStepSaveStatus("failed");
      setStepError(err instanceof Error ? err.message : "작성 단계를 저장하지 못했습니다.");
      return false;
    } finally {
      setBusy(false);
    }
  }

  async function saveActiveStep(values: ReportWizardFormValues) {
    return saveCurrentStep(values);
  }

  async function submitReport() {
    if (!(await saveCurrentStep())) {
      return;
    }
    setBusy(true);
    setStepError(null);
    setNotice(null);
    try {
      await onSubmitReport(report.id);
      setNotice("리포트가 문서 생성 대기 상태로 제출되었습니다.");
    } catch (err) {
      setStepError(err instanceof Error ? err.message : "리포트를 제출하지 못했습니다.");
    } finally {
      setBusy(false);
    }
  }

  async function reopenReport() {
    if (!canReopen || busy || loadingSteps) {
      if (!canReopen) {
        setStepError(
          reportWriteAllowed
            ? "현재 상태에서는 수정본을 만들 수 없습니다. 진행 중인 문서 생성 작업이 끝난 뒤 다시 시도하세요."
            : "이 리포트를 수정할 권한이 없습니다. 사무소 관리자에게 리포트 WRITER 권한 배정을 요청하세요."
        );
      }
      return;
    }
    setBusy(true);
    setStepError(null);
    setNotice(null);
    try {
      await onReopenReport(report.id);
      setNotice("수정본을 만들었습니다. 저장 후 다시 제출하면 새 revision으로 문서를 재생성할 수 있습니다.");
    } catch (err) {
      setStepError(err instanceof Error ? err.message : "수정본을 만들지 못했습니다.");
    } finally {
      setBusy(false);
    }
  }

  async function moveStep(offset: number) {
    const currentIndex = stepDefinitions.findIndex((definition) => definition.code === activeStepCode);
    const nextDefinition = stepDefinitions[currentIndex + offset];
    if (!nextDefinition) {
      return;
    }
    if (!canEdit) {
      setActiveStepCode(nextDefinition.code);
      return;
    }
    if (await saveCurrentStep()) {
      setActiveStepCode(nextDefinition.code);
    }
  }

  async function selectStep(stepCode: ReportStepCode) {
    if (stepCode === activeStepCode) {
      return;
    }
    if (!canEdit) {
      setActiveStepCode(stepCode);
      return;
    }
    if (await saveCurrentStep()) {
      setActiveStepCode(stepCode);
    }
  }

  return {
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
  };
}
