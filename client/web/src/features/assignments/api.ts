import { request } from "../../api/http";
import type {
  ProjectAssignment,
  ReportAssignment,
  UpsertProjectAssignmentRequest,
  UpsertReportAssignmentRequest
} from "./types";

export function getProjectAssignments(token: string, officeId: number, projectId: number) {
  return request<ProjectAssignment[]>(`/api/v1/projects/${projectId}/assignments`, { token, officeId });
}

export function upsertProjectAssignment(
  token: string,
  officeId: number,
  projectId: number,
  body: UpsertProjectAssignmentRequest
) {
  return request<ProjectAssignment>(`/api/v1/projects/${projectId}/assignments`, {
    token,
    officeId,
    method: "PUT",
    body
  });
}

export function removeProjectAssignment(token: string, officeId: number, projectId: number, userId: number) {
  return request<ProjectAssignment>(`/api/v1/projects/${projectId}/assignments/${userId}`, {
    token,
    officeId,
    method: "DELETE"
  });
}

export function getReportAssignments(token: string, officeId: number, reportId: number) {
  return request<ReportAssignment[]>(`/api/v1/inspection-reports/${reportId}/assignments`, { token, officeId });
}

export function upsertReportAssignment(
  token: string,
  officeId: number,
  reportId: number,
  body: UpsertReportAssignmentRequest
) {
  return request<ReportAssignment>(`/api/v1/inspection-reports/${reportId}/assignments`, {
    token,
    officeId,
    method: "PUT",
    body
  });
}

export function removeReportAssignment(token: string, officeId: number, reportId: number, userId: number) {
  return request<ReportAssignment>(`/api/v1/inspection-reports/${reportId}/assignments/${userId}`, {
    token,
    officeId,
    method: "DELETE"
  });
}
