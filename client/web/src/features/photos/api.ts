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

type UploadPhotoContentOptions = {
  onProgress?: (progress: number) => void;
  signal?: AbortSignal;
};

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

export function deletePhoto(token: string, officeId: number, photoId: number) {
  return request<void>(`/api/v1/photos/${photoId}`, {
    token,
    officeId,
    method: "DELETE"
  });
}

export async function uploadPhotoContent(
  token: string,
  officeId: number,
  instruction: PhotoUploadInstructionResponse,
  file: File,
  options: UploadPhotoContentOptions = {}
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

  await new Promise<void>((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    const abort = () => xhr.abort();

    xhr.open(instruction.method || "PUT", uploadUrl, true);
    headers.forEach((value, key) => xhr.setRequestHeader(key, value));
    xhr.upload.onprogress = (event) => {
      if (!event.lengthComputable || event.total <= 0) {
        options.onProgress?.(50);
        return;
      }
      options.onProgress?.(Math.round((event.loaded / event.total) * 100));
    };
    xhr.onload = () => {
      options.signal?.removeEventListener("abort", abort);
      if (xhr.status >= 200 && xhr.status < 300) {
        options.onProgress?.(100);
        resolve();
        return;
      }
      reject(new Error(`사진 업로드에 실패했습니다. (${xhr.status})`));
    };
    xhr.onerror = () => {
      options.signal?.removeEventListener("abort", abort);
      reject(new Error("사진 업로드 중 네트워크 연결이 끊겼습니다."));
    };
    xhr.onabort = () => {
      options.signal?.removeEventListener("abort", abort);
      reject(new DOMException("사진 업로드를 취소했습니다.", "AbortError"));
    };

    if (options.signal?.aborted) {
      abort();
      return;
    }
    options.signal?.addEventListener("abort", abort, { once: true });
    xhr.send(file);
  });
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
