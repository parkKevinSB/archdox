import { request } from "../../api/http";
import type {
  DocumentTypeDefinition,
  InspectionStep,
  ReportFlowDefinition,
  SupervisionDomainCatalog
} from "./types";

const ACTIVE_DOCUMENT_TYPES = new Set([
  "CONSTRUCTION_DAILY_SUPERVISION_LOG",
  "CONSTRUCTION_SUPERVISION_REPORT"
]);

export async function getDocumentTypes(token: string, officeId: number) {
  const documentTypes = await request<DocumentTypeDefinition[]>("/api/v1/document-types", { token, officeId });
  return documentTypes.filter((documentType) => ACTIVE_DOCUMENT_TYPES.has(documentType.code));
}

export function getInspectionSteps(token: string, officeId: number, reportId: number) {
  return request<InspectionStep[]>(`/api/v1/inspection-reports/${reportId}/steps`, { token, officeId });
}

export function getReportWorkflowDefinition(token: string, officeId: number, reportId: number) {
  return request<ReportFlowDefinition>(`/api/v1/inspection-reports/${reportId}/workflow-definition`, {
    token,
    officeId
  });
}

export function getSupervisionDomainCatalog(token: string, officeId: number, catalogCode: string, siteId?: number | null) {
  const query = siteId ? `?siteId=${encodeURIComponent(String(siteId))}` : "";
  return request<SupervisionDomainCatalog>(
    `/api/v1/supervision-domain-catalogs/${encodeURIComponent(catalogCode)}${query}`,
    { token, officeId }
  );
}
