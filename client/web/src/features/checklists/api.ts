import { request } from "../../api/http";
import type {
  ChecklistAnswer,
  ReportChecklist,
  SaveChecklistAnswerRequest
} from "./types";

export function attachInspectionReportTarget(
  token: string,
  officeId: number,
  reportId: number,
  targetId: number,
  role = "PRIMARY"
) {
  return request(`/api/v1/inspection-reports/${reportId}/targets`, {
    token,
    officeId,
    method: "POST",
    body: { targetId, role }
  });
}

export function getReportChecklist(token: string, officeId: number, reportId: number) {
  return request<ReportChecklist>(`/api/v1/inspection-reports/${reportId}/checklist`, { token, officeId });
}

export function saveChecklistAnswer(
  token: string,
  officeId: number,
  reportId: number,
  itemCode: string,
  body: SaveChecklistAnswerRequest
) {
  return request<ChecklistAnswer>(
    `/api/v1/inspection-reports/${reportId}/checklist/answers/${encodeURIComponent(itemCode)}`,
    {
      token,
      officeId,
      method: "PUT",
      body
    }
  );
}
