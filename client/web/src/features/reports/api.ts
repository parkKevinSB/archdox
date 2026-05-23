import { request } from "../../api/http";
import type { InspectionStep, ReportFlowDefinition } from "./types";

export function getInspectionSteps(token: string, officeId: number, reportId: number) {
  return request<InspectionStep[]>(`/api/v1/inspection-reports/${reportId}/steps`, { token, officeId });
}

export function getReportWorkflowDefinition(token: string, officeId: number, reportId: number) {
  return request<ReportFlowDefinition>(`/api/v1/inspection-reports/${reportId}/workflow-definition`, {
    token,
    officeId
  });
}
