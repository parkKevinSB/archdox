import { Camera, Cloud, FileImage, HardDrive, Loader2, Trash2, UploadCloud, X } from "lucide-react";
import { useState, type ChangeEvent } from "react";
import { EmptyState, StatusBadge } from "../../../components/common";
import type { InspectionReport } from "../../../types";
import type { PhotoAssetResponse, PhotoAssetType, PhotoResponse } from "../types";
import { usePhotoAssetPreview } from "../hooks/usePhotoAssetPreview";
import type { PhotoUploadTask, PhotoWorkspaceState } from "../hooks/usePhotoWorkspace";

type PhotoPipelinePanelProps = {
  canUpload?: boolean;
  emptyText?: string;
  emptyTitle?: string;
  officeId: number | null;
  photoContexts?: Record<number, PhotoDisplayContext>;
  report: InspectionReport;
  token: string;
  workspace: PhotoWorkspaceState;
};

export type PhotoDisplayContext = {
  caption: string;
  detail?: string;
};

export function PhotoPipelinePanel({
  canUpload = true,
  emptyText = "현장 사진을 올리면 원본/작업본/썸네일 처리 상태를 여기에서 확인합니다.",
  emptyTitle = "아직 업로드된 사진이 없습니다",
  officeId,
  photoContexts = {},
  report,
  token,
  workspace
}: PhotoPipelinePanelProps) {
  async function handleFilesSelected(event: ChangeEvent<HTMLInputElement>) {
    const files = Array.from(event.target.files ?? []).filter((file) => file.type.startsWith("image/"));
    event.target.value = "";
    if (files.length === 0 || !canUpload) {
      return;
    }
    await workspace.uploadFiles(files);
  }

  return (
    <>
      <div className="panel-body">
        <div className="photo-upload-zone">
          <div>
            <strong>{report.title || report.reportNo}</strong>
            <span>현재 단계: {report.currentStep ?? "FIELD_PHOTOS"}</span>
          </div>
          <label className={!canUpload ? "primary-button disabled" : "primary-button"}>
            {workspace.uploading ? <Loader2 className="spin" size={17} /> : <Camera size={17} />}
            사진 촬영
            <input
              accept="image/*"
              capture="environment"
              disabled={!canUpload}
              onChange={handleFilesSelected}
              type="file"
            />
          </label>
          <label className={!canUpload ? "secondary-button disabled" : "secondary-button"}>
            {workspace.uploading ? <Loader2 className="spin" size={17} /> : <UploadCloud size={17} />}
            {workspace.uploading ? "대기열에 추가" : "사진 업로드"}
            <input
              accept="image/*"
              disabled={!canUpload}
              multiple
              onChange={handleFilesSelected}
              type="file"
            />
          </label>
        </div>
        <PhotoUploadTaskStrip onCancel={workspace.cancelUploadTask} tasks={workspace.uploadTasks} />
      </div>

      {workspace.photos.length === 0 ? (
        <EmptyState title={emptyTitle} text={emptyText} />
      ) : (
        <div className="photo-grid">
          {workspace.photos.map((photo) => (
            <PhotoCard
              key={photo.id}
              officeId={officeId}
              onCancelPendingUpload={workspace.cancelPendingPhotoUpload}
              onDeletePhoto={workspace.deletePhoto}
              photo={photo}
              photoContext={photoContexts[photo.id]}
              token={token}
            />
          ))}
        </div>
      )}
    </>
  );
}

export function PhotoUploadTaskStrip({
  onCancel,
  tasks
}: {
  onCancel: (taskId: string) => void;
  tasks: PhotoUploadTask[];
}) {
  if (tasks.length === 0) {
    return null;
  }
  return (
    <div className="photo-upload-task-strip">
      {tasks.map((task) => (
        <div className={`photo-upload-task ${task.status.toLowerCase()}`} key={task.id}>
          <div className="photo-upload-task-thumb">
            {task.previewUrl ? <img alt={task.fileName} src={task.previewUrl} /> : <FileImage size={16} />}
            {isUploadTaskLive(task) ? (
              <button
                aria-label={`${task.fileName} 업로드 취소`}
                className="photo-upload-task-cancel"
                onClick={() => onCancel(task.id)}
                type="button"
              >
                <X size={13} />
              </button>
            ) : null}
            <span className="photo-upload-task-progress" aria-hidden="true">
              <span style={{ width: `${Math.max(0, Math.min(100, task.progress))}%` }} />
            </span>
          </div>
          <div className="photo-upload-task-info">
            <strong>{task.fileName}</strong>
            <span>{task.error ?? uploadTaskLabel(task)}</span>
          </div>
        </div>
      ))}
    </div>
  );
}

function PhotoCard({
  officeId,
  onCancelPendingUpload,
  onDeletePhoto,
  photo,
  photoContext,
  token
}: {
  officeId: number | null;
  onCancelPendingUpload: (photoId: number) => Promise<void>;
  onDeletePhoto: (photoId: number) => Promise<void>;
  photo: PhotoResponse;
  photoContext?: PhotoDisplayContext;
  token: string;
}) {
  const [previewOpen, setPreviewOpen] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const original = photo.assets.find((asset) => asset.assetType === "ORIGINAL");
  const working = photo.assets.find((asset) => asset.assetType === "WORKING");
  const thumbnail = photo.assets.find((asset) => asset.assetType === "THUMBNAIL");
  const previewAssetType = selectPreviewAssetType(thumbnail, working);
  const pendingUpload = photo.status === "PENDING_UPLOAD";
  const displayCaption = photoContext?.caption || photo.caption || photo.stepCode || `Photo #${photo.id}`;
  const preview = usePhotoAssetPreview({
    assetType: previewAssetType,
    officeId,
    photoId: photo.id,
    token
  });

  return (
    <>
      <article className="photo-card">
        <div className="photo-card-head">
          <button
            aria-label={`Photo ${photo.id} 미리보기 열기`}
            className="photo-preview-button"
            disabled={!preview.url}
            onClick={() => setPreviewOpen(true)}
            type="button"
          >
            {preview.url ? (
              <img alt={`Photo ${photo.id}`} className="photo-preview-image" src={preview.url} />
            ) : (
              <span className="photo-preview-placeholder">
                <FileImage size={22} />
              </span>
            )}
          </button>
          <div>
            <strong>Photo #{photo.id}</strong>
            <span>
              {photo.stepCode ?? "FIELD_PHOTOS"} · {formatBytes(photo.bytes ?? 0)} · {photo.width ?? "-"}x
              {photo.height ?? "-"}
            </span>
            <span>{displayCaption}</span>
            {photoContext?.detail ? <span>{photoContext.detail}</span> : null}
          </div>
          <StatusBadge status={photo.status} />
        </div>

        <div className="photo-storage-row">
          <span>
            <Cloud size={15} />
            {photo.uploadTarget}
          </span>
          <span>
            <HardDrive size={15} />
            원본 {pickupLabel(photo.originalPickupStatus)}
          </span>
        </div>

        <div className="asset-chip-row">
          <AssetChip label="원본" asset={original} />
          <AssetChip label="작업본" asset={working} />
          <AssetChip label="썸네일" asset={thumbnail} />
        </div>

        {pendingUpload ? (
          <div className="photo-pending-alert">
            <span>업로드가 완료되지 않은 사진입니다. 문서에는 포함되지 않습니다.</span>
            <button
              className="text-button danger"
              disabled={deleting}
              onClick={async () => {
                setDeleting(true);
                try {
                  await onCancelPendingUpload(photo.id);
                } finally {
                  setDeleting(false);
                }
              }}
              type="button"
            >
              {deleting ? <Loader2 className="spin" size={15} /> : <X size={15} />}
              삭제
            </button>
          </div>
        ) : null}

        {!pendingUpload ? (
          <div className="photo-pending-alert">
            <span>리포트와 문서 출력에서 이 사진을 제거할 수 있습니다.</span>
            <button
              className="text-button danger"
              disabled={deleting}
              onClick={async () => {
                if (!window.confirm("사진을 삭제하시겠습니까? 연결된 감리 항목에서도 제거됩니다.")) {
                  return;
                }
                setDeleting(true);
                try {
                  await onDeletePhoto(photo.id);
                } finally {
                  setDeleting(false);
                }
              }}
              type="button"
            >
              {deleting ? <Loader2 className="spin" size={15} /> : <Trash2 size={15} />}
              사진 삭제
            </button>
          </div>
        ) : null}

        <div className="photo-card-foot">
          <span>{preview.loading ? "미리보기 로딩 중" : preview.error ? "미리보기 준비 전" : `storage: ${photo.storageKind}`}</span>
          {photo.originalTemporaryDeletedAt ? <strong>임시 원본 삭제 완료</strong> : null}
        </div>
      </article>

      {previewOpen && preview.url ? (
        <div className="photo-lightbox" onClick={() => setPreviewOpen(false)} role="dialog" aria-modal="true">
          <div className="photo-lightbox-panel" onClick={(event) => event.stopPropagation()}>
            <header>
              <div>
                <strong>Photo #{photo.id}</strong>
                <span>{previewAssetType === "WORKING" ? "작업본 미리보기" : "썸네일 미리보기"}</span>
              </div>
              <button className="icon-button" onClick={() => setPreviewOpen(false)} type="button" aria-label="미리보기 닫기">
                <X size={18} />
              </button>
            </header>
            <img alt={`Photo ${photo.id} larger preview`} src={preview.url} />
            <footer>
              {photo.stepCode ?? "FIELD_PHOTOS"} · {formatBytes(photo.bytes ?? 0)} · {photo.width ?? "-"}x{photo.height ?? "-"}
            </footer>
          </div>
        </div>
      ) : null}
    </>
  );
}

function AssetChip({ asset, label }: { asset?: PhotoAssetResponse; label: string }) {
  return (
    <span className={asset?.status === "UPLOADED" || asset?.status === "PICKED_UP" ? "asset-chip ready" : "asset-chip"}>
      <strong>{label}</strong>
      <small>{asset?.status ?? "없음"}</small>
    </span>
  );
}

export function hasWorkingAndThumbnail(photo: PhotoResponse) {
  return ["WORKING", "THUMBNAIL"].every((assetType) =>
    photo.assets.some((asset) => asset.assetType === assetType && asset.status === "UPLOADED")
  );
}

function selectPreviewAssetType(
  thumbnail?: PhotoAssetResponse,
  working?: PhotoAssetResponse
): Exclude<PhotoAssetType, "ORIGINAL"> | null {
  if (working?.status === "UPLOADED") {
    return "WORKING";
  }
  if (thumbnail?.status === "UPLOADED") {
    return "THUMBNAIL";
  }
  return null;
}

function isUploadTaskLive(task: PhotoUploadTask) {
  return ["QUEUED", "PREPARING", "UPLOADING", "CONFIRMING"].includes(task.status);
}

function uploadTaskLabel(task: PhotoUploadTask) {
  if (task.status === "QUEUED") {
    return "대기 중";
  }
  if (task.status === "PREPARING") {
    return "준비 중";
  }
  if (task.status === "UPLOADING") {
    return `업로드 중 ${task.progress}%`;
  }
  if (task.status === "CONFIRMING") {
    return "저장 확인 중";
  }
  if (task.status === "COMPLETED") {
    return "완료";
  }
  if (task.status === "CANCELLED") {
    return "취소됨";
  }
  return "실패";
}

function pickupLabel(status: string) {
  if (status === "PICKED_UP") {
    return "가져감 완료";
  }
  if (status === "PENDING") {
    return "가져가기 대기";
  }
  if (status === "NOT_REQUIRED") {
    return "불필요";
  }
  return status;
}

function formatBytes(bytes: number) {
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`;
  }
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}
