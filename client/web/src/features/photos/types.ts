import type { InspectionReport } from "../../types";

export type PhotoStatus = "PENDING_UPLOAD" | "UPLOADED" | "DELETED";
export type PhotoAssetStatus = "PENDING_UPLOAD" | "UPLOADED" | "PICKED_UP" | "DELETED";
export type PhotoPickupStatus = "NOT_REQUIRED" | "PENDING" | "PICKED_UP" | "FAILED";
export type PhotoCaptureKind = "CAMERA" | "UPLOAD";
export type PhotoStorageKind = "API_LOCAL" | "S3" | "S3_TEMP" | "AGENT_MANAGED";
export type PhotoUploadTarget = "API_LOCAL" | "S3" | "CLOUD_MEDIATED";
export type PhotoUploadKind = "ORIGINAL" | "WORKING" | "THUMBNAIL";
export type PhotoAssetType = "ORIGINAL" | "WORKING" | "THUMBNAIL";

export type PhotoAssetResponse = {
  assetType: PhotoAssetType;
  status: PhotoAssetStatus;
  storageKind: PhotoStorageKind;
  storageRef: string;
  mime: string;
  bytes?: number | null;
  width?: number | null;
  height?: number | null;
  hash?: string | null;
  temporary: boolean;
};

export type PhotoResponse = {
  id: number;
  officeId: number;
  projectId: number;
  siteId?: number | null;
  reportId?: number | null;
  stepCode?: string | null;
  checklistItemId?: number | null;
  siteSupervisionEntryId?: number | null;
  tradeCode?: string | null;
  processCode?: string | null;
  inspectionItemCode?: string | null;
  caption?: string | null;
  locationNote?: string | null;
  drawingRef?: string | null;
  contextLabel?: string | null;
  contextDescription?: string | null;
  captureKind: PhotoCaptureKind;
  status: PhotoStatus;
  mime: string;
  width?: number | null;
  height?: number | null;
  bytes?: number | null;
  hash?: string | null;
  storageKind: PhotoStorageKind;
  storageRef: string;
  thumbnailStorageRef?: string | null;
  uploadTarget: PhotoUploadTarget;
  originalPickupStatus: PhotoPickupStatus;
  originalPickedUpAt?: string | null;
  originalTemporaryDeletedAt?: string | null;
  assets: PhotoAssetResponse[];
};

export type PhotoUploadInstructionResponse = {
  kind: PhotoUploadKind;
  method: string;
  url: string;
  fields: Record<string, string>;
  headers: Record<string, string>;
  token?: string | null;
  expiresAt: string;
};

export type PhotoUploadIntentResponse = {
  photoId: number;
  target: PhotoUploadTarget;
  uploadRequired: boolean;
  uploads: PhotoUploadInstructionResponse[];
  mediationJobId?: number | null;
  expiresAt?: string | null;
  photo: PhotoResponse;
};

export type CreatePhotoUploadIntentRequest = {
  projectId?: number | null;
  siteId?: number | null;
  reportId?: number | null;
  stepCode?: string | null;
  checklistItemId?: number | null;
  siteSupervisionEntryId?: number | null;
  tradeCode?: string | null;
  processCode?: string | null;
  inspectionItemCode?: string | null;
  caption?: string | null;
  locationNote?: string | null;
  drawingRef?: string | null;
  captureKind?: PhotoCaptureKind;
  mime: string;
  bytes: number;
  hash: string;
  width?: number | null;
  height?: number | null;
  takenAt?: string | null;
  gpsLat?: number | null;
  gpsLng?: number | null;
  wantsOriginal?: boolean;
};

export type ConfirmPhotoUploadRequest = {
  hash: string;
  bytes: number;
  width?: number | null;
  height?: number | null;
};

export type PhotoUploadFileResult = {
  fileName: string;
  photo: PhotoResponse;
};

export type PhotoWorkspaceReport = InspectionReport;
