import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
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
};

type PreparedPhotoFile = {
  bytes: number;
  file: File;
  hash: string;
  height?: number | null;
  mime: string;
  width?: number | null;
};

export function usePhotoWorkspace({ officeId, report, token }: UsePhotoWorkspaceOptions) {
  const queryClient = useQueryClient();
  const reportId = report?.id ?? null;
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

  const uploadMutation = useMutation({
    mutationFn: async (files: File[]) => {
      if (!officeId || !report) {
        throw new Error("사진을 연결할 리포트를 먼저 선택해야 합니다.");
      }
      const results: PhotoUploadFileResult[] = [];
      for (const file of files) {
        const prepared = await preparePhotoFile(file);
        const intent = await createPhotoUploadIntent(token, officeId, {
          projectId: report.projectId,
          reportId: report.id,
          stepCode: report.currentStep ?? "FIELD_PHOTOS",
          captureKind: "UPLOAD",
          mime: prepared.mime,
          bytes: prepared.bytes,
          hash: prepared.hash,
          width: prepared.width,
          height: prepared.height,
          wantsOriginal: true
        });
        if (!intent.uploadRequired) {
          results.push({ fileName: file.name, photo: intent.photo });
          continue;
        }
        const upload = selectUploadInstruction(intent.uploads);
        if (!upload) {
          throw new Error("사용 가능한 사진 업로드 경로가 없습니다.");
        }
        await uploadPhotoContent(token, officeId, upload, file);
        const confirmed = await confirmPhotoUpload(token, officeId, intent.photoId, {
          hash: prepared.hash,
          bytes: prepared.bytes,
          width: prepared.width,
          height: prepared.height
        });
        results.push({ fileName: file.name, photo: confirmed });
      }
      return results;
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["photos", officeId, reportId] });
    }
  });

  return {
    error: photosQuery.error ?? uploadMutation.error,
    loading: photosQuery.isLoading,
    photos: photosQuery.data ?? [],
    refreshPhotos: photosQuery.refetch,
    uploadFiles: uploadMutation.mutateAsync,
    uploading: uploadMutation.isPending
  };
}

export type PhotoWorkspaceState = ReturnType<typeof usePhotoWorkspace>;

function selectUploadInstruction(uploads: PhotoUploadInstructionResponse[]) {
  return (
    uploads.find((upload) => upload.kind === "ORIGINAL") ??
    uploads.find((upload) => upload.kind === "WORKING") ??
    uploads[0] ??
    null
  );
}

function isPhotoPipelineActive(photo: PhotoResponse) {
  return (
    photo.status === "PENDING_UPLOAD" ||
    photo.originalPickupStatus === "PENDING" ||
    photo.assets.some((asset) => asset.status === "PENDING_UPLOAD")
  );
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
  const digest = await crypto.subtle.digest("SHA-256", buffer);
  return [...new Uint8Array(digest)].map((value) => value.toString(16).padStart(2, "0")).join("");
}

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
