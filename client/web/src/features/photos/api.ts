import { request } from "../../api/http";
import type {
  ConfirmPhotoUploadRequest,
  CreatePhotoUploadIntentRequest,
  PhotoAssetType,
  PhotoResponse,
  PhotoUploadInstructionResponse,
  PhotoUploadIntentResponse
} from "./types";

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? "";

export function listPhotosByReport(token: string, officeId: number, reportId: number) {
  return request<PhotoResponse[]>("/api/v1/photos", {
    token,
    officeId,
    query: { reportId }
  });
}

export function createPhotoUploadIntent(
  token: string,
  officeId: number,
  body: CreatePhotoUploadIntentRequest
) {
  return request<PhotoUploadIntentResponse>("/api/v1/photos/intent", {
    token,
    officeId,
    method: "POST",
    body
  });
}

export function confirmPhotoUpload(
  token: string,
  officeId: number,
  photoId: number,
  body: ConfirmPhotoUploadRequest
) {
  return request<PhotoResponse>(`/api/v1/photos/${photoId}/confirm`, {
    token,
    officeId,
    method: "POST",
    body
  });
}

export function cancelPhotoUpload(token: string, officeId: number, photoId: number) {
  return request<void>(`/api/v1/photos/${photoId}/cancel-upload`, {
    token,
    officeId,
    method: "POST"
  });
}

export async function uploadPhotoContent(
  token: string,
  officeId: number,
  instruction: PhotoUploadInstructionResponse,
  file: File
) {
  const uploadUrl = resolveUploadUrl(instruction.url);
  const headers = new Headers(instruction.headers ?? {});
  if (!headers.has("Content-Type")) {
    headers.set("Content-Type", file.type || "application/octet-stream");
  }
  if (isArchDoxApiUrl(uploadUrl)) {
    headers.set("Authorization", `Bearer ${token}`);
    headers.set("X-Office-Id", String(officeId));
  }

  const response = await fetch(uploadUrl, {
    method: instruction.method || "PUT",
    headers,
    body: file
  });
  if (!response.ok) {
    throw new Error(`사진 업로드에 실패했습니다. (${response.status})`);
  }
}

export async function fetchPhotoAssetBlob(
  token: string,
  officeId: number,
  photoId: number,
  assetType: Exclude<PhotoAssetType, "ORIGINAL">
) {
  const response = await fetch(
    new URL(`${API_BASE}/api/v1/photos/${photoId}/assets/${assetType}/content`, window.location.origin),
    {
      headers: {
        Authorization: `Bearer ${token}`,
        "X-Office-Id": String(officeId)
      }
    }
  );
  if (!response.ok) {
    throw new Error(`사진 미리보기를 불러오지 못했습니다. (${response.status})`);
  }
  return response.blob();
}

function resolveUploadUrl(url: string) {
  if (/^https?:\/\//i.test(url)) {
    return url;
  }
  return new URL(`${API_BASE}${url}`, window.location.origin).toString();
}

function isArchDoxApiUrl(url: string) {
  const resolved = new URL(url);
  return resolved.origin === window.location.origin || (API_BASE !== "" && url.startsWith(API_BASE));
}
