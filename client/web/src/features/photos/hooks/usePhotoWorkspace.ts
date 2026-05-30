import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useCallback, useEffect, useRef, useState } from "react";
import {
  cancelPhotoUpload,
  confirmPhotoUpload,
  createPhotoUploadIntent,
  listPhotosByReport,
  uploadPhotoContent
} from "../api";
import type {
  PhotoResponse,
  PhotoUploadFileResult,
  PhotoUploadInstructionResponse,
  PhotoWorkspaceReport
} from "../types";

type UsePhotoWorkspaceOptions = {
  officeId: number | null;
  report: PhotoWorkspaceReport | null;
  token: string;
  uploadContext?: PhotoUploadContext;
};

export type PhotoUploadContext = {
  checklistItemId?: number | null;
  stepCode?: string | null;
};

type PreparedPhotoFile = {
  bytes: number;
  file: File;
  hash: string;
  height?: number | null;
  mime: string;
  width?: number | null;
};

export type PhotoUploadTaskStatus = "QUEUED" | "PREPARING" | "UPLOADING" | "CONFIRMING" | "COMPLETED" | "FAILED" | "CANCELLED";

export type PhotoUploadTask = {
  checklistItemId?: number | null;
  error?: string | null;
  fileName: string;
  id: string;
  photoId?: number | null;
  previewUrl?: string | null;
  progress: number;
  status: PhotoUploadTaskStatus;
  stepCode?: string | null;
};

type PhotoUploadInput = {
  context?: PhotoUploadContext;
  files: File[];
};

type InternalPhotoUploadTask = PhotoUploadTask & {
  abortController?: AbortController;
  cancelled: boolean;
  file: File;
  reject: (reason: unknown) => void;
  resolve: (result: PhotoUploadFileResult | null) => void;
  settled: boolean;
};

const LIVE_UPLOAD_STATUSES: PhotoUploadTaskStatus[] = ["QUEUED", "PREPARING", "UPLOADING", "CONFIRMING"];

export function usePhotoWorkspace({ officeId, report, token, uploadContext }: UsePhotoWorkspaceOptions) {
  const queryClient = useQueryClient();
  const reportId = report?.id ?? null;
  const [uploadTasks, setUploadTasks] = useState<PhotoUploadTask[]>([]);
  const [uploadError, setUploadError] = useState<Error | null>(null);
  const tasksRef = useRef<InternalPhotoUploadTask[]>([]);
  const processingRef = useRef(false);
  const sequenceRef = useRef(0);

  const photosQuery = useQuery({
    enabled: Boolean(token && officeId && reportId),
    queryKey: ["photos", officeId, reportId],
    queryFn: async () => {
      if (!officeId || !reportId) {
        return [];
      }
      return listPhotosByReport(token, officeId, reportId);
    },
    refetchInterval: (query) => {
      const photos = query.state.data as PhotoResponse[] | undefined;
      const active = (photos ?? []).some((photo) => isPhotoPipelineActive(photo));
      return active ? 3000 : false;
    }
  });

  const syncUploadTasks = useCallback(() => {
    setUploadTasks(tasksRef.current.map(toPublicTask));
  }, []);

  const updateUploadTask = useCallback((taskId: string, patch: Partial<PhotoUploadTask>) => {
    const task = tasksRef.current.find((current) => current.id === taskId);
    if (!task) {
      return;
    }
    Object.assign(task, patch);
    syncUploadTasks();
  }, [syncUploadTasks]);

  const removeUploadTaskLater = useCallback((taskId: string, delayMs: number) => {
    window.setTimeout(() => {
      const task = tasksRef.current.find((current) => current.id === taskId);
      if (task?.previewUrl) {
        URL.revokeObjectURL(task.previewUrl);
      }
      tasksRef.current = tasksRef.current.filter((current) => current.id !== taskId);
      syncUploadTasks();
    }, delayMs);
  }, [syncUploadTasks]);

  const settleUploadTask = useCallback((
    task: InternalPhotoUploadTask,
    result: PhotoUploadFileResult | null,
    reason?: unknown
  ) => {
    if (task.settled) {
      return;
    }
    task.settled = true;
    if (reason) {
      task.reject(reason);
      return;
    }
    task.resolve(result);
  }, []);

  const cancelPendingServerUpload = useCallback(async (task: InternalPhotoUploadTask) => {
    if (!officeId || !task.photoId) {
      return;
    }
    await cancelPhotoUpload(token, officeId, task.photoId).catch(() => undefined);
  }, [officeId, token]);

  const finishCancelledTask = useCallback(async (task: InternalPhotoUploadTask) => {
    task.cancelled = true;
    await cancelPendingServerUpload(task);
    updateUploadTask(task.id, {
      error: null,
      progress: 0,
      status: "CANCELLED"
    });
    settleUploadTask(task, null);
    removeUploadTaskLater(task.id, 1400);
  }, [cancelPendingServerUpload, removeUploadTaskLater, settleUploadTask, updateUploadTask]);

  const processUploadQueue = useCallback(async () => {
    if (processingRef.current || !officeId || !report) {
      return;
    }
    processingRef.current = true;
    try {
      let task = nextQueuedTask(tasksRef.current);
      while (task) {
        if (task.cancelled) {
          await finishCancelledTask(task);
          task = nextQueuedTask(tasksRef.current);
          continue;
        }

        try {
          setUploadError(null);
          updateUploadTask(task.id, { progress: 3, status: "PREPARING" });
          const prepared = await preparePhotoFile(task.file);
          if (task.cancelled) {
            await finishCancelledTask(task);
            task = nextQueuedTask(tasksRef.current);
            continue;
          }

          const resolvedContext = {
            checklistItemId: task.checklistItemId ?? uploadContext?.checklistItemId ?? null,
            stepCode: task.stepCode ?? uploadContext?.stepCode ?? report.currentStep ?? "FIELD_PHOTOS"
          };
          const intent = await createPhotoUploadIntent(token, officeId, {
            projectId: report.projectId,
            reportId: report.id,
            stepCode: resolvedContext.stepCode,
            checklistItemId: resolvedContext.checklistItemId,
            captureKind: "UPLOAD",
            mime: prepared.mime,
            bytes: prepared.bytes,
            hash: prepared.hash,
            width: prepared.width,
            height: prepared.height,
            wantsOriginal: true
          });
          updateUploadTask(task.id, { photoId: intent.photoId, progress: 12 });

          if (!intent.uploadRequired) {
            updateUploadTask(task.id, { progress: 100, status: "COMPLETED" });
            settleUploadTask(task, { fileName: task.fileName, photo: intent.photo });
            removeUploadTaskLater(task.id, 1200);
            await queryClient.invalidateQueries({ queryKey: ["photos", officeId, reportId] });
            task = nextQueuedTask(tasksRef.current);
            continue;
          }

          task.photoId = intent.photoId;
          if (task.cancelled) {
            await finishCancelledTask(task);
            task = nextQueuedTask(tasksRef.current);
            continue;
          }

          const upload = selectUploadInstruction(intent.uploads);
          if (!upload) {
            throw new Error("사용 가능한 사진 업로드 경로가 없습니다.");
          }

          const abortController = new AbortController();
          task.abortController = abortController;
          updateUploadTask(task.id, { progress: 16, status: "UPLOADING" });
          await uploadPhotoContent(token, officeId, upload, task.file, {
            signal: abortController.signal,
            onProgress: (progress) => {
              updateUploadTask(task.id, {
                progress: Math.min(90, 16 + Math.round(progress * 0.72))
              });
            }
          });
          task.abortController = undefined;

          if (task.cancelled) {
            await finishCancelledTask(task);
            task = nextQueuedTask(tasksRef.current);
            continue;
          }

          updateUploadTask(task.id, { progress: 94, status: "CONFIRMING" });
          const confirmed = await confirmPhotoUpload(token, officeId, intent.photoId, {
            hash: prepared.hash,
            bytes: prepared.bytes,
            width: prepared.width,
            height: prepared.height
          });
          updateUploadTask(task.id, { progress: 100, status: "COMPLETED" });
          settleUploadTask(task, { fileName: task.fileName, photo: confirmed });
          removeUploadTaskLater(task.id, 1200);
          await queryClient.invalidateQueries({ queryKey: ["photos", officeId, reportId] });
        } catch (error) {
          if (task.cancelled || isAbortError(error)) {
            await finishCancelledTask(task);
          } else {
            const message = error instanceof Error ? error.message : "사진 업로드에 실패했습니다.";
            const uploadError = new Error(message);
            setUploadError(uploadError);
            updateUploadTask(task.id, {
              error: message,
              progress: 0,
              status: "FAILED"
            });
            settleUploadTask(task, null, uploadError);
            removeUploadTaskLater(task.id, 6500);
          }
          await queryClient.invalidateQueries({ queryKey: ["photos", officeId, reportId] });
        }
        task = nextQueuedTask(tasksRef.current);
      }
    } finally {
      processingRef.current = false;
    }
  }, [
    cancelPendingServerUpload,
    finishCancelledTask,
    officeId,
    queryClient,
    removeUploadTaskLater,
    report,
    reportId,
    settleUploadTask,
    token,
    updateUploadTask,
    uploadContext?.checklistItemId,
    uploadContext?.stepCode
  ]);

  const uploadFiles = useCallback(async (files: File[], context?: PhotoUploadContext) => {
    if (!officeId || !report) {
      throw new Error("사진을 연결할 리포트를 먼저 선택해야 합니다.");
    }
    const tasks = files.map((file) => {
      let resolveTask: (result: PhotoUploadFileResult | null) => void = () => undefined;
      let rejectTask: (reason: unknown) => void = () => undefined;
      const promise = new Promise<PhotoUploadFileResult | null>((resolve, reject) => {
        resolveTask = resolve;
        rejectTask = reject;
      });
      const task: InternalPhotoUploadTask & { promise: Promise<PhotoUploadFileResult | null> } = {
        cancelled: false,
        checklistItemId: context?.checklistItemId ?? null,
        error: null,
        file,
        fileName: file.name,
        id: `photo-upload-${Date.now()}-${sequenceRef.current++}`,
        photoId: null,
        previewUrl: file.type.startsWith("image/") ? URL.createObjectURL(file) : null,
        progress: 0,
        reject: rejectTask,
        resolve: resolveTask,
        settled: false,
        status: "QUEUED",
        stepCode: context?.stepCode ?? null,
        promise
      };
      return task;
    });
    tasksRef.current.push(...tasks);
    syncUploadTasks();
    void processUploadQueue();
    const results = await Promise.all(tasks.map((task) => task.promise));
    return results.filter((result): result is PhotoUploadFileResult => result !== null);
  }, [officeId, processUploadQueue, report, syncUploadTasks]);

  const cancelUploadTask = useCallback((taskId: string) => {
    const task = tasksRef.current.find((current) => current.id === taskId);
    if (!task || !LIVE_UPLOAD_STATUSES.includes(task.status)) {
      return;
    }
    task.cancelled = true;
    if (task.abortController) {
      task.abortController.abort();
      return;
    }
    void finishCancelledTask(task).then(() => {
      void queryClient.invalidateQueries({ queryKey: ["photos", officeId, reportId] });
    });
  }, [finishCancelledTask, officeId, queryClient, reportId]);

  const cancelPendingPhotoUpload = useCallback(async (photoId: number) => {
    if (!officeId) {
      return;
    }
    await cancelPhotoUpload(token, officeId, photoId);
    await queryClient.invalidateQueries({ queryKey: ["photos", officeId, reportId] });
  }, [officeId, queryClient, reportId, token]);

  useEffect(() => {
    return () => {
      for (const task of tasksRef.current) {
        task.abortController?.abort();
        if (task.previewUrl) {
          URL.revokeObjectURL(task.previewUrl);
        }
      }
      tasksRef.current = [];
    };
  }, []);

  const uploading = uploadTasks.some((task) => LIVE_UPLOAD_STATUSES.includes(task.status));

  return {
    error: photosQuery.error ?? uploadError,
    allPhotos: photosQuery.data ?? [],
    cancelPendingPhotoUpload,
    cancelUploadTask,
    loading: photosQuery.isLoading,
    photos: photosQuery.data ?? [],
    refreshPhotos: photosQuery.refetch,
    uploadFiles,
    uploading,
    uploadTasks
  };
}

export type PhotoWorkspaceState = ReturnType<typeof usePhotoWorkspace>;

function nextQueuedTask(tasks: InternalPhotoUploadTask[]) {
  return tasks.find((task) => task.status === "QUEUED");
}

function toPublicTask(task: InternalPhotoUploadTask): PhotoUploadTask {
  return {
    checklistItemId: task.checklistItemId,
    error: task.error,
    fileName: task.fileName,
    id: task.id,
    photoId: task.photoId,
    previewUrl: task.previewUrl,
    progress: task.progress,
    status: task.status,
    stepCode: task.stepCode
  };
}

function selectUploadInstruction(uploads: PhotoUploadInstructionResponse[]) {
  return (
    uploads.find((upload) => upload.kind === "ORIGINAL") ??
    uploads.find((upload) => upload.kind === "WORKING") ??
    uploads[0] ??
    null
  );
}

function isPhotoPipelineActive(photo: PhotoResponse) {
  if (photo.status === "PENDING_UPLOAD") {
    return false;
  }
  return (
    photo.originalPickupStatus === "PENDING" ||
    photo.assets.some((asset) => asset.status === "PENDING_UPLOAD")
  );
}

function isAbortError(error: unknown) {
  return error instanceof DOMException && error.name === "AbortError";
}

async function preparePhotoFile(file: File): Promise<PreparedPhotoFile> {
  const [hash, dimensions] = await Promise.all([sha256(file), readImageDimensions(file)]);
  return {
    bytes: file.size,
    file,
    hash,
    height: dimensions?.height ?? null,
    mime: file.type || "application/octet-stream",
    width: dimensions?.width ?? null
  };
}

async function sha256(file: File) {
  const buffer = await file.arrayBuffer();
  const subtle = globalThis.crypto?.subtle;
  if (subtle) {
    const digest = await subtle.digest("SHA-256", buffer);
    return hex(new Uint8Array(digest));
  }
  return sha256Fallback(new Uint8Array(buffer));
}

function sha256Fallback(bytes: Uint8Array) {
  const words: number[] = [];
  const bitLength = bytes.length * 8;
  for (let i = 0; i < bytes.length; i += 1) {
    words[i >> 2] = (words[i >> 2] ?? 0) | (bytes[i] << (24 - (i % 4) * 8));
  }
  words[bytes.length >> 2] = (words[bytes.length >> 2] ?? 0) | (0x80 << (24 - (bytes.length % 4) * 8));
  const lengthWordIndex = (((bytes.length + 8) >> 6) << 4);
  words[lengthWordIndex + 14] = Math.floor(bitLength / 0x100000000);
  words[lengthWordIndex + 15] = bitLength >>> 0;

  let h0 = 0x6a09e667;
  let h1 = 0xbb67ae85;
  let h2 = 0x3c6ef372;
  let h3 = 0xa54ff53a;
  let h4 = 0x510e527f;
  let h5 = 0x9b05688c;
  let h6 = 0x1f83d9ab;
  let h7 = 0x5be0cd19;
  const schedule = new Array<number>(64);

  for (let i = 0; i < words.length; i += 16) {
    let a = h0;
    let b = h1;
    let c = h2;
    let d = h3;
    let e = h4;
    let f = h5;
    let g = h6;
    let hh = h7;

    for (let j = 0; j < 64; j += 1) {
      if (j < 16) {
        schedule[j] = words[i + j] ?? 0;
      } else {
        const s0 = rightRotate(schedule[j - 15], 7) ^ rightRotate(schedule[j - 15], 18) ^ (schedule[j - 15] >>> 3);
        const s1 = rightRotate(schedule[j - 2], 17) ^ rightRotate(schedule[j - 2], 19) ^ (schedule[j - 2] >>> 10);
        schedule[j] = add32(schedule[j - 16], s0, schedule[j - 7], s1);
      }
      const ch = (e & f) ^ (~e & g);
      const maj = (a & b) ^ (a & c) ^ (b & c);
      const sigma0 = rightRotate(a, 2) ^ rightRotate(a, 13) ^ rightRotate(a, 22);
      const sigma1 = rightRotate(e, 6) ^ rightRotate(e, 11) ^ rightRotate(e, 25);
      const t1 = add32(hh, sigma1, ch, SHA256_K[j], schedule[j]);
      const t2 = add32(sigma0, maj);
      hh = g;
      g = f;
      f = e;
      e = add32(d, t1);
      d = c;
      c = b;
      b = a;
      a = add32(t1, t2);
    }

    h0 = add32(h0, a);
    h1 = add32(h1, b);
    h2 = add32(h2, c);
    h3 = add32(h3, d);
    h4 = add32(h4, e);
    h5 = add32(h5, f);
    h6 = add32(h6, g);
    h7 = add32(h7, hh);
  }

  return [h0, h1, h2, h3, h4, h5, h6, h7]
    .map((value) => value.toString(16).padStart(8, "0"))
    .join("");
}

function hex(bytes: Uint8Array) {
  return [...bytes].map((value) => value.toString(16).padStart(2, "0")).join("");
}

function rightRotate(value: number, amount: number) {
  return (value >>> amount) | (value << (32 - amount));
}

function add32(...values: number[]) {
  return values.reduce((sum, value) => (sum + value) >>> 0, 0);
}

const SHA256_K = [
  0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
  0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
  0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
  0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
  0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
  0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
  0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
  0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
];

async function readImageDimensions(file: File): Promise<{ width: number; height: number } | null> {
  if (!file.type.startsWith("image/")) {
    return null;
  }
  if ("createImageBitmap" in window) {
    try {
      const bitmap = await createImageBitmap(file);
      const dimensions = { width: bitmap.width, height: bitmap.height };
      bitmap.close();
      return dimensions;
    } catch {
      return null;
    }
  }
  return new Promise((resolve) => {
    const image = new Image();
    const objectUrl = window.URL.createObjectURL(file);
    image.onload = () => {
      window.URL.revokeObjectURL(objectUrl);
      resolve({ width: image.naturalWidth, height: image.naturalHeight });
    };
    image.onerror = () => {
      window.URL.revokeObjectURL(objectUrl);
      resolve(null);
    };
    image.src = objectUrl;
  });
}
