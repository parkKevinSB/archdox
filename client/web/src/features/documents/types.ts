import type { InspectionReport, Project } from "../../types";

export type { InspectionReport, Project };

export type DocumentJobStatus = "REQUESTED" | "GENERATING" | "GENERATED" | "FAILED" | "CANCELLED";

export type DocumentJobProgressStep =
  | "QUEUED"
  | "VALIDATING"
  | "DISPATCHING"
  | "WAITING_FOR_AGENT"
  | "RENDERING"
  | "STORING_ARTIFACTS"
  | "GENERATED"
  | "FAILED";

export type DocumentWorkerType = "CLOUD" | "ARCHDOX_AGENT";
export type DocumentOutputFormat = "DOCX" | "PDF";
export type DocumentArtifactType = "DOCX" | "PDF";
export type DocumentArtifactStorageKind = "API_LOCAL" | "ARCHDOX_AGENT" | "S3_COMPATIBLE";
export type DocumentDeliveryChannel = "DOWNLOAD";
export type DocumentDeliveryStatus = "REQUESTED" | "SENDING" | "COMPLETED" | "FAILED" | "CANCELLED";

export type DocumentArtifactResponse = {
  id: number;
  artifactType: DocumentArtifactType;
  storageKind: DocumentArtifactStorageKind;
  storageRef: string;
  fileName: string;
  mimeType: string;
  bytes: number;
  hashSha256?: string | null;
  createdAt: string;
};

export type DocumentJobResponse = {
  id: number;
  officeId: number;
  reportId: number;
  projectId: number;
  reportRevision: number;
  status: DocumentJobStatus;
  progressStep: DocumentJobProgressStep;
  progressPercent: number;
  progressMessage?: string | null;
  workerType: DocumentWorkerType;
  outputFormat: DocumentOutputFormat;
  errorCode?: string | null;
  errorMessage?: string | null;
  requestedAt: string;
  startedAt?: string | null;
  completedAt?: string | null;
  artifacts: DocumentArtifactResponse[];
};

export type DocumentDeliveryRequestResponse = {
  id: number;
  officeId: number;
  documentJobId: number;
  artifactId?: number | null;
  channel: DocumentDeliveryChannel;
  status: DocumentDeliveryStatus;
  recipientRef?: string | null;
  errorMessage?: string | null;
  downloadUrl?: string | null;
  requestedAt: string;
  completedAt?: string | null;
  updatedAt: string;
};

export type DocumentJobsByReport = Record<number, DocumentJobResponse[]>;
export type DocumentDeliveriesByJob = Record<number, DocumentDeliveryRequestResponse[]>;
