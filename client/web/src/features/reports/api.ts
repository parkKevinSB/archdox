import { request } from "../../api/http";
import type { InspectionStep } from "./types";

export function getInspectionSteps(token: string, officeId: number, reportId: number) {
  return request<InspectionStep[]>(`/api/v1/inspection-reports/${reportId}/steps`, { token, officeId });
}
