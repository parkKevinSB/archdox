import { CalendarDays, CheckSquare, FileText } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { useEffect, useMemo } from "react";
import { getInspectionReports } from "../../../../api";
import { getInspectionSteps } from "../../api";
import {
  CHECKLIST_SOURCE_FIELD_KEY,
  DEFAULT_CHECKLIST_SOURCE_SELECTION,
  type ChecklistSourceOutputType,
  type ChecklistSourceSelection,
  type ChecklistSourceSelectionMode
} from "../../reportSteps";
import type { InspectionReport, InspectionStep } from "../../types";
import type { ReportStepComponentProps } from "./ReportFormStep";

type SourceReport = {
  inspectionDate: string;
  report: InspectionReport;
};

const FIELD_KEY = CHECKLIST_SOURCE_FIELD_KEY;
const DAILY_REPORT_TYPE = "CONSTRUCTION_DAILY_SUPERVISION_LOG";

export function ChecklistSourceStep({
  canWriteReports,
  definition,
  form,
  officeId,
  register,
  report,
  savedStep,
  token
}: ReportStepComponentProps) {
  const rawSelection = form.watch(FIELD_KEY);
  const selection = useMemo(() => parseSelection(rawSelection), [rawSelection]);

  useEffect(() => {
    if (!form.getValues(FIELD_KEY)) {
      form.setValue(FIELD_KEY, JSON.stringify(DEFAULT_CHECKLIST_SOURCE_SELECTION), { shouldDirty: Boolean(savedStep) });
    }
  }, [form, report.id, savedStep]);

  const sourceReportsQuery = useQuery({
    queryKey: ["checklistSourceReports", officeId, report.projectId, report.siteId],
    queryFn: async () => {
      const reports = await getInspectionReports(token, officeId);
      const candidates = reports.filter((candidate) => (
        candidate.id !== report.id
        && candidate.projectId === report.projectId
        && candidate.siteId === report.siteId
        && candidate.reportType === DAILY_REPORT_TYPE
        && candidate.status !== "CANCELLED"
      ));
      const enriched = await Promise.all(candidates.map(async (candidate) => ({
        report: candidate,
        inspectionDate: await loadInspectionDate(token, officeId, candidate.id)
      })));
      return enriched.sort(compareSourceReports);
    },
    enabled: Boolean(token && officeId && report.projectId)
  });

  const sourceReports = sourceReportsQuery.data ?? [];
  const includedReports = useMemo(
    () => sourceReports.filter((source) => selectionIncludes(source, selection)),
    [selection, sourceReports]
  );

  function updateSelection(patch: Partial<ChecklistSelection>) {
    const next = { ...selection, ...patch };
    form.setValue(FIELD_KEY, JSON.stringify(next), { shouldDirty: true, shouldValidate: true });
  }

  function toggleReport(reportId: number) {
    const selected = new Set(selection.selectedReportIds);
    if (selected.has(reportId)) {
      selected.delete(reportId);
    } else {
      selected.add(reportId);
    }
    updateSelection({ selectedReportIds: Array.from(selected).sort((a, b) => a - b) });
  }

  return (
    <>
      <div className="wizard-form-head">
        <div>
          <h3>{definition.title}</h3>
          <p>{definition.description}</p>
        </div>
      </div>
      <input type="hidden" {...register(FIELD_KEY)} />

      <section className="checklist-source-panel">
        <div className="checklist-source-section">
          <div className="checklist-source-section-head">
            <FileText size={18} />
            <div>
              <strong>출력할 체크리스트</strong>
              <span>감리일지에서 체크된 항목을 공식 체크리스트 양식에 반영합니다.</span>
            </div>
          </div>
          <div className="checklist-source-segmented">
            {[
              ["TRADE", "공종별"],
              ["PHASE", "단계별"],
              ["ALL", "전체"]
            ].map(([value, label]) => (
              <button
                className={selection.outputType === value ? "active" : ""}
                disabled={!canWriteReports}
                key={value}
                onClick={() => updateSelection({ outputType: value as ChecklistSourceOutputType })}
                type="button"
              >
                {label}
              </button>
            ))}
          </div>
        </div>

        <div className="checklist-source-section">
          <div className="checklist-source-section-head">
            <CalendarDays size={18} />
            <div>
              <strong>대상 감리일지</strong>
              <span>기본은 현재 현장에 작성된 전체 감리일지입니다.</span>
            </div>
          </div>
          <div className="checklist-source-segmented">
            {[
              ["ALL_SITE", "현장 전체"],
              ["DATE_RANGE", "기간 선택"],
              ["SELECTED_REPORTS", "직접 선택"]
            ].map(([value, label]) => (
              <button
                className={selection.selectionMode === value ? "active" : ""}
                disabled={!canWriteReports}
                key={value}
                onClick={() => updateSelection({ selectionMode: value as ChecklistSourceSelectionMode })}
                type="button"
              >
                {label}
              </button>
            ))}
          </div>

          {selection.selectionMode === "DATE_RANGE" ? (
            <div className="checklist-source-date-range">
              <label>
                시작일
                <input
                  disabled={!canWriteReports}
                  onChange={(event) => updateSelection({ dateFrom: event.target.value })}
                  type="date"
                  value={selection.dateFrom}
                />
              </label>
              <label>
                종료일
                <input
                  disabled={!canWriteReports}
                  onChange={(event) => updateSelection({ dateTo: event.target.value })}
                  type="date"
                  value={selection.dateTo}
                />
              </label>
            </div>
          ) : null}
        </div>

        <div className="checklist-source-summary">
          <CheckSquare size={18} />
          <span>
            대상 감리일지 <strong>{includedReports.length}</strong>건 / 현장 전체 감리일지 {sourceReports.length}건
          </span>
        </div>

        <div className="checklist-source-list">
          {sourceReportsQuery.isLoading ? (
            <p className="checklist-source-empty">감리일지 목록을 불러오는 중입니다.</p>
          ) : sourceReports.length === 0 ? (
            <p className="checklist-source-empty">현재 현장에 작성된 공사감리일지가 없습니다.</p>
          ) : (
            sourceReports.map((source) => {
              const checked = selectionIncludes(source, selection);
              const selectable = selection.selectionMode === "SELECTED_REPORTS";
              return (
                <label className={checked ? "checklist-source-row active" : "checklist-source-row"} key={source.report.id}>
                  <input
                    checked={checked}
                    disabled={!canWriteReports || !selectable}
                    onChange={() => toggleReport(source.report.id)}
                    type="checkbox"
                  />
                  <span>
                    <strong>{source.report.title || source.report.reportNo}</strong>
                    <small>
                      {source.inspectionDate || "일자 없음"} · {source.report.reportNo} · {source.report.status}
                    </small>
                  </span>
                </label>
              );
            })
          )}
        </div>
      </section>
    </>
  );
}

async function loadInspectionDate(token: string, officeId: number, reportId: number) {
  try {
    const steps = await getInspectionSteps(token, officeId, reportId);
    return inspectionDateOf(steps);
  } catch {
    return "";
  }
}

function inspectionDateOf(steps: InspectionStep[]) {
  const value = steps.find((step) => step.stepCode === "BASIC_INFO")?.payload?.inspectionDate;
  return typeof value === "string" ? value : "";
}

function parseSelection(rawValue: string | undefined): ChecklistSelection {
  if (!rawValue) {
    return DEFAULT_CHECKLIST_SOURCE_SELECTION;
  }
  try {
    const parsed = JSON.parse(rawValue) as Partial<ChecklistSelection>;
    return {
      dateFrom: typeof parsed.dateFrom === "string" ? parsed.dateFrom : "",
      dateTo: typeof parsed.dateTo === "string" ? parsed.dateTo : "",
      outputType: normalizeOutputType(parsed.outputType),
      selectedReportIds: Array.isArray(parsed.selectedReportIds)
        ? parsed.selectedReportIds.map(Number).filter(Number.isFinite)
        : [],
      selectionMode: normalizeSelectionMode(parsed.selectionMode)
    };
  } catch {
    return DEFAULT_SELECTION;
  }
}

function normalizeOutputType(value: unknown): ChecklistOutputType {
  return value === "TRADE" || value === "PHASE" || value === "ALL" ? value : "ALL";
}

function normalizeSelectionMode(value: unknown): ChecklistSelectionMode {
  return value === "DATE_RANGE" || value === "SELECTED_REPORTS" || value === "ALL_SITE" ? value : "ALL_SITE";
}

function selectionIncludes(source: SourceReport, selection: ChecklistSelection) {
  if (selection.selectionMode === "SELECTED_REPORTS") {
    return selection.selectedReportIds.includes(source.report.id);
  }
  if (selection.selectionMode === "DATE_RANGE") {
    if (!source.inspectionDate) {
      return false;
    }
    return (!selection.dateFrom || source.inspectionDate >= selection.dateFrom)
      && (!selection.dateTo || source.inspectionDate <= selection.dateTo);
  }
  return true;
}

function compareSourceReports(left: SourceReport, right: SourceReport) {
  const dateCompare = right.inspectionDate.localeCompare(left.inspectionDate);
  if (dateCompare !== 0) {
    return dateCompare;
  }
  return right.report.id - left.report.id;
}
