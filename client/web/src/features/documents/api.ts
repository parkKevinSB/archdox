import { request } from "../../api/http";
import type {
  ChecklistPrintResponse,
  ChecklistPrintType,
  DocumentArtifactResponse,
  DocumentDeliveryRequestResponse,
  DocumentJobResponse,
  DocumentNarrativeApplyResponse,
  DocumentNarrativePolishFieldInput,
  DocumentNarrativePolishResponse,
  DocumentOutputFormat,
  DocumentRenderOverrideInput,
  DocumentSignatureInput,
  DocumentWorkerType,
  ReportPreflightFindingResolutionStatus,
  ReportPreflightReviewFindingResponse,
  ReportPreflightReviewRunResponse
} from "./types";

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? "";

export function createDocumentJob(
  token: string,
  officeId: number,
  reportId: number,
  body: {
    outputFormat?: DocumentOutputFormat;
    renderOverrides?: DocumentRenderOverrideInput[];
    signature?: DocumentSignatureInput;
    workerType?: DocumentWorkerType;
  } = {}
) {
  return request<DocumentJobResponse>(`/api/v1/inspection-reports/${reportId}/document-jobs`, {
    token,
    officeId,
    method: "POST",
    body
  });
}

export function listDocumentJobsByReport(token: string, officeId: number, reportId: number) {
  return request<DocumentJobResponse[]>(`/api/v1/inspection-reports/${reportId}/document-jobs`, {
    token,
    officeId
  });
}

export function polishDocumentNarrative(
  token: string,
  officeId: number,
  reportId: number,
  fields: DocumentNarrativePolishFieldInput[]
) {
  return request<DocumentNarrativePolishResponse>(`/api/v1/inspection-reports/${reportId}/document-narrative-polish`, {
    token,
    officeId,
    method: "POST",
    body: { fields }
  });
}

export function applyDocumentNarrativeToReport(
  token: string,
  officeId: number,
  reportId: number,
  fields: DocumentNarrativePolishFieldInput[]
) {
  return request<DocumentNarrativeApplyResponse>(`/api/v1/inspection-reports/${reportId}/document-narrative-polish/apply`, {
    token,
    officeId,
    method: "POST",
    body: { fields }
  });
}

export function createDocumentDeliveryRequest(
  token: string,
  officeId: number,
  jobId: number,
  artifactId?: number
) {
  return request<DocumentDeliveryRequestResponse>(`/api/v1/document-jobs/${jobId}/delivery-requests`, {
    token,
    officeId,
    method: "POST",
    body: {
      artifactId,
      channel: "DOWNLOAD"
    }
  });
}

export function listDocumentDeliveryRequestsByJob(token: string, officeId: number, jobId: number) {
  return request<DocumentDeliveryRequestResponse[]>(`/api/v1/document-jobs/${jobId}/delivery-requests`, {
    token,
    officeId
  });
}

export function createReportPreflightReviewRun(token: string, officeId: number, reportId: number) {
  return request<ReportPreflightReviewRunResponse>(`/api/v1/inspection-reports/${reportId}/preflight-review-runs`, {
    token,
    officeId,
    method: "POST"
  });
}

export function listReportPreflightReviewRuns(token: string, officeId: number, reportId: number) {
  return request<ReportPreflightReviewRunResponse[]>(`/api/v1/inspection-reports/${reportId}/preflight-review-runs`, {
    token,
    officeId
  });
}

export function listReportPreflightReviewFindings(
  token: string,
  officeId: number,
  reportId: number,
  runId: number
) {
  return request<ReportPreflightReviewFindingResponse[]>(
    `/api/v1/inspection-reports/${reportId}/preflight-review-runs/${runId}/findings`,
    {
      token,
      officeId
    }
  );
}

export function resolveReportPreflightReviewFinding(
  token: string,
  officeId: number,
  reportId: number,
  runId: number,
  findingId: number,
  body: {
    resolutionStatus: ReportPreflightFindingResolutionStatus;
    resolutionNote?: string | null;
  }
) {
  return request<ReportPreflightReviewFindingResponse>(
    `/api/v1/inspection-reports/${reportId}/preflight-review-runs/${runId}/findings/${findingId}/resolution`,
    {
      token,
      officeId,
      method: "PATCH",
      body
    }
  );
}

export function applyReportPreflightReviewFindingFix(
  token: string,
  officeId: number,
  reportId: number,
  runId: number,
  findingId: number
) {
  return request<ReportPreflightReviewFindingResponse>(
    `/api/v1/inspection-reports/${reportId}/preflight-review-runs/${runId}/findings/${findingId}/fix`,
    {
      token,
      officeId,
      method: "POST"
    }
  );
}

export function fetchChecklistPrintPreview(
  token: string,
  officeId: number,
  reportId: number,
  type: ChecklistPrintType
) {
  return request<ChecklistPrintResponse>(`/api/v1/inspection-reports/${reportId}/checklist-print-preview?type=${type}`, {
    token,
    officeId
  });
}

export async function downloadChecklistPrintDocx(
  token: string,
  officeId: number,
  reportId: number,
  type: ChecklistPrintType
) {
  const downloadUrl = `/api/v1/inspection-reports/${reportId}/checklist-print-docx?type=${type}`;
  const response = await fetch(new URL(`${API_BASE}${downloadUrl}`, window.location.origin), {
    headers: {
      Authorization: `Bearer ${token}`,
      "X-Office-Id": String(officeId)
    }
  });
  if (!response.ok) {
    throw new Error(`체크리스트 DOCX 다운로드에 실패했습니다. (${response.status})`);
  }
  const fileName = contentDispositionFileName(response.headers.get("Content-Disposition"))
    ?? `archdox-checklist-${reportId}-${type.toLowerCase()}.docx`;
  await saveDownloadBlob(await response.blob(), fileName);
}

export async function downloadDocumentUrl(
  token: string,
  officeId: number,
  downloadUrl: string,
  fileName: string
) {
  const response = await fetch(new URL(`${API_BASE}${downloadUrl}`, window.location.origin), {
    headers: {
      Authorization: `Bearer ${token}`,
      "X-Office-Id": String(officeId)
    }
  });
  if (!response.ok) {
    throw new Error(`다운로드 요청에 실패했습니다. (${response.status})`);
  }
  await saveDownloadBlob(await response.blob(), fileName);
}

async function saveDownloadBlob(blob: Blob, fileName: string) {
  const objectUrl = window.URL.createObjectURL(blob);
  try {
    const link = document.createElement("a");
    link.href = objectUrl;
    link.download = fileName;
    document.body.appendChild(link);
    link.click();
    link.remove();
  } finally {
    window.URL.revokeObjectURL(objectUrl);
  }
}

function contentDispositionFileName(value: string | null) {
  if (!value) {
    return null;
  }
  const encodedMatch = value.match(/filename\*=UTF-8''([^;]+)/i);
  if (encodedMatch?.[1]) {
    try {
      return decodeURIComponent(encodedMatch[1].trim());
    } catch {
      return encodedMatch[1].trim();
    }
  }
  const quotedMatch = value.match(/filename="([^"]+)"/i);
  if (quotedMatch?.[1]) {
    return quotedMatch[1].trim();
  }
  const plainMatch = value.match(/filename=([^;]+)/i);
  return plainMatch?.[1]?.trim() ?? null;
}

export async function fetchDocumentTextUrl(
  token: string,
  officeId: number,
  downloadUrl: string
) {
  const response = await fetch(new URL(`${API_BASE}${downloadUrl}`, window.location.origin), {
    headers: {
      Accept: "text/html,*/*",
      Authorization: `Bearer ${token}`,
      "X-Office-Id": String(officeId)
    }
  });
  if (!response.ok) {
    throw new Error(`문서 미리보기 요청에 실패했습니다. (${response.status})`);
  }
  return response.text();
}

export function directArtifactDownloadUrl(artifact: DocumentArtifactResponse) {
  return `/api/v1/document-artifacts/${artifact.id}/download`;
}

export function defaultDownloadFileName(artifact: DocumentArtifactResponse) {
  return artifact.fileName || `archdox-document-${artifact.id}.${artifact.artifactType.toLowerCase()}`;
}
