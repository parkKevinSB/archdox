import { CheckCircle2, Loader2 } from "lucide-react";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { InlineAlert, InlineNotice } from "../../../components/common";
import { useReportChecklist } from "../hooks/useReportChecklist";
import type {
  ChecklistAnswer,
  ChecklistFormValues,
  ChecklistItem,
  InspectionReport,
  InspectionTarget
} from "../types";

type ReportChecklistPanelProps = {
  canWriteReports: boolean;
  officeId: number;
  report: InspectionReport;
  targets: InspectionTarget[];
  token: string;
};

export function ReportChecklistPanel({
  canWriteReports,
  officeId,
  report,
  targets,
  token
}: ReportChecklistPanelProps) {
  const form = useForm<ChecklistFormValues>({ defaultValues: {} });
  const [selectedTargetId, setSelectedTargetId] = useState<number | null>(targets[0]?.id ?? null);
  const [busyItemCode, setBusyItemCode] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [localError, setLocalError] = useState<string | null>(null);
  const {
    attachTarget,
    attachingTarget,
    checklist,
    loading,
    loadError,
    saveAnswer
  } = useReportChecklist({ token, officeId, reportId: report.id });

  useEffect(() => {
    if (selectedTargetId && targets.some((target) => target.id === selectedTargetId)) {
      return;
    }
    setSelectedTargetId(targets[0]?.id ?? null);
  }, [selectedTargetId, targets]);

  useEffect(() => {
    if (!checklist) {
      return;
    }
    form.reset(formValuesFromAnswers(checklist.answers));
  }, [checklist, form]);

  async function saveItem(item: ChecklistItem) {
    if (!canWriteReports) {
      setLocalError("체크리스트 저장은 작성 권한이 필요합니다.");
      return;
    }
    setBusyItemCode(item.itemCode);
    setNotice(null);
    setLocalError(null);
    try {
      await saveAnswer({
        itemCode: item.itemCode,
        body: {
          targetId: selectedTargetId,
          answer: { value: normalizedChecklistValue(item, form.getValues(answerKey(item.itemCode)) ?? "") },
          note: normalizeFormValue(form.getValues(noteKey(item.itemCode)) ?? "")
        }
      });
      setNotice(`${item.label} 답변을 저장했습니다.`);
    } catch (err) {
      setLocalError(err instanceof Error ? err.message : "체크리스트 답변을 저장하지 못했습니다.");
    } finally {
      setBusyItemCode(null);
    }
  }

  async function attachSelectedTarget() {
    if (!canWriteReports) {
      setLocalError("리포트 대상 연결은 작성 권한이 필요합니다.");
      return;
    }
    if (!selectedTargetId) {
      return;
    }
    setNotice(null);
    setLocalError(null);
    try {
      await attachTarget(selectedTargetId);
      setNotice("선택한 점검 대상을 리포트의 주 대상으로 연결했습니다.");
    } catch (err) {
      setLocalError(err instanceof Error ? err.message : "점검 대상을 리포트에 연결하지 못했습니다.");
    }
  }

  if (loading || !checklist) {
    return <InlineNotice message="체크리스트 스키마를 불러오는 중입니다." />;
  }

  const error = localError ?? errorMessage(loadError);

  return (
    <div className="checklist-panel">
      <div className="checklist-head">
        <div>
          <strong>{checklist.schema.name}</strong>
          <span>
            {checklist.schema.code} v{checklist.schema.version}
          </span>
        </div>
        <div className="checklist-target-control">
          <select
            disabled={!canWriteReports}
            onChange={(event) => setSelectedTargetId(event.target.value ? Number(event.target.value) : null)}
            value={selectedTargetId ?? ""}
          >
            <option value="">리포트 공통</option>
            {targets.map((target) => (
              <option key={target.id} value={target.id}>
                {target.name}
              </option>
            ))}
          </select>
          <button
            className="secondary-button"
            disabled={attachingTarget || !selectedTargetId || !canWriteReports}
            onClick={attachSelectedTarget}
            type="button"
          >
            {attachingTarget ? <Loader2 className="spin" size={17} /> : <CheckCircle2 size={17} />}
            주 대상 연결
          </button>
        </div>
      </div>

      {error ? <InlineAlert message={error} /> : null}
      {!canWriteReports ? <InlineNotice message="이 계정은 체크리스트 쓰기 전용 권한이 없습니다." /> : null}
      {notice ? <InlineNotice message={notice} /> : null}

      <div className="checklist-items">
        {checklist.schema.items.map((item) => (
          <div className="checklist-item" key={item.id}>
            <div>
              <strong>{item.label}</strong>
              <span>{item.description ?? (item.required ? "필수 항목" : "선택 항목")}</span>
            </div>
            <ChecklistAnswerInput disabled={!canWriteReports} item={item} register={form.register} />
            <input disabled={!canWriteReports} placeholder="비고" {...form.register(noteKey(item.itemCode))} />
            <button
              className="secondary-button"
              disabled={busyItemCode === item.itemCode || !canWriteReports}
              onClick={() => saveItem(item)}
              type="button"
            >
              {busyItemCode === item.itemCode ? <Loader2 className="spin" size={17} /> : <CheckCircle2 size={17} />}
              저장
            </button>
          </div>
        ))}
      </div>
    </div>
  );
}

function ChecklistAnswerInput({
  disabled,
  item,
  register
}: {
  disabled: boolean;
  item: ChecklistItem;
  register: ReturnType<typeof useForm<ChecklistFormValues>>["register"];
}) {
  const registration = register(answerKey(item.itemCode));
  if (item.answerType === "TEXT") {
    return <textarea disabled={disabled} placeholder="답변" {...registration} />;
  }
  if (item.answerType === "NUMBER") {
    return <input disabled={disabled} placeholder="0" type="number" {...registration} />;
  }
  if (item.answerType === "YES_NO" || item.answerType === "CHECK") {
    return (
      <select disabled={disabled} {...registration}>
        <option value="">선택</option>
        <option value="true">예</option>
        <option value="false">아니오</option>
      </select>
    );
  }
  return (
    <select disabled={disabled} {...registration}>
      <option value="">선택</option>
      {item.options.map((option) => (
        <option key={option} value={option}>
          {option}
        </option>
      ))}
    </select>
  );
}

function formValuesFromAnswers(answers: ChecklistAnswer[]) {
  return answers.reduce<ChecklistFormValues>((values, answer) => {
    values[answerKey(answer.itemCode)] = checklistAnswerValue(answer.answer);
    values[noteKey(answer.itemCode)] = answer.note ?? "";
    return values;
  }, {});
}

function checklistAnswerValue(answer: Record<string, unknown>) {
  const value = answer.value;
  if (typeof value === "boolean" || typeof value === "number") {
    return String(value);
  }
  if (typeof value === "string") {
    return value;
  }
  return "";
}

function normalizedChecklistValue(item: ChecklistItem, value: string) {
  if (item.answerType === "YES_NO" || item.answerType === "CHECK") {
    if (value === "true") {
      return true;
    }
    if (value === "false") {
      return false;
    }
  }
  if (item.answerType === "NUMBER") {
    const numericValue = Number(value);
    return Number.isFinite(numericValue) ? numericValue : value;
  }
  return value;
}

function answerKey(itemCode: string) {
  return `answer:${itemCode}`;
}

function noteKey(itemCode: string) {
  return `note:${itemCode}`;
}

function normalizeFormValue(value: string) {
  const trimmed = value.trim();
  return trimmed.length === 0 ? null : trimmed;
}

function errorMessage(error: unknown) {
  if (!error) {
    return null;
  }
  return error instanceof Error ? error.message : "체크리스트 정보를 처리하지 못했습니다.";
}
