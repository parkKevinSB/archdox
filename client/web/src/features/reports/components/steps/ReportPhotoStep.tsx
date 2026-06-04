import { InlineAlert } from "../../../../components/common";
import { PhotoPipelinePanel, type PhotoDisplayContext } from "../../../photos/components/PhotoPipelinePanel";
import { usePhotoWorkspace } from "../../../photos/hooks/usePhotoWorkspace";
import type { ReportStepComponentProps } from "./ReportFormStep";

export function ReportPhotoStep({
  canWriteReports,
  definition,
  officeId,
  report,
  revision,
  savedSteps,
  token
}: ReportStepComponentProps) {
  const workspace = usePhotoWorkspace({ officeId, report, token });
  const photoContexts = dailyLogPhotoContexts(savedSteps?.DAILY_LOG?.payload);

  return (
    <>
      <div className="wizard-form-head">
        <div>
          <h3>{definition.title}</h3>
          <p>{definition.description}</p>
        </div>
        {revision ? <span className="panel-context">rev {revision}</span> : null}
      </div>

      {workspace.error ? (
        <InlineAlert message={workspace.error instanceof Error ? workspace.error.message : "사진 상태를 불러오지 못했습니다."} />
      ) : null}

      <div className="report-photo-step">
        <PhotoPipelinePanel
          canUpload={canWriteReports}
          emptyText="이 단계에서 올린 사진은 작업본과 썸네일 생성 상태를 함께 확인합니다. 문서 생성에는 작업본 이미지가 사용됩니다."
          emptyTitle="리포트에 연결된 사진이 없습니다"
          officeId={officeId}
          photoContexts={photoContexts}
          report={report}
          token={token}
          workspace={workspace}
        />
      </div>
    </>
  );
}

function dailyLogPhotoContexts(payload?: Record<string, unknown>): Record<number, PhotoDisplayContext> {
  const groups = listValue(mapValue(payload?.dailyItems).groups);
  const contexts: Record<number, PhotoDisplayContext> = {};
  for (const rawGroup of groups) {
    const group = mapValue(rawGroup);
    const groupLabel = [text(group.tradeName), text(group.processName), text(group.floor)]
      .filter(Boolean)
      .join(" / ");
    for (const rawEntry of listValue(group.entries)) {
      const entry = mapValue(rawEntry);
      const itemName = text(entry.inspectionItemName);
      const content = text(entry.supervisionContent);
      for (const rawPhotoId of listValue(entry.photoIds)) {
        const photoId = Number(rawPhotoId);
        if (!Number.isFinite(photoId) || photoId <= 0) {
          continue;
        }
        contexts[photoId] = {
          caption: [groupLabel, itemName].filter(Boolean).join(" - ") || `Photo #${photoId}`,
          detail: content
        };
      }
    }
  }
  return contexts;
}

function mapValue(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : {};
}

function listValue(value: unknown): unknown[] {
  return Array.isArray(value) ? value : [];
}

function text(value: unknown): string {
  return typeof value === "string" ? value.trim() : value == null ? "" : String(value).trim();
}
