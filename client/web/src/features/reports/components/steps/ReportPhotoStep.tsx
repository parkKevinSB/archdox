import { InlineAlert } from "../../../../components/common";
import { PhotoPipelinePanel } from "../../../photos/components/PhotoPipelinePanel";
import { usePhotoWorkspace } from "../../../photos/hooks/usePhotoWorkspace";
import type { ReportStepComponentProps } from "./ReportFormStep";

export function ReportPhotoStep({
  canWriteReports,
  definition,
  officeId,
  report,
  revision,
  token
}: ReportStepComponentProps) {
  const workspace = usePhotoWorkspace({ officeId, report, token });

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
          emptyText="이 단계에서 올린 사진은 작업본/썸네일 생성과 원본 이관 흐름으로 이어지고, 문서 생성에는 작업본이 사용됩니다."
          emptyTitle="이 리포트에 연결된 사진이 없습니다"
          officeId={officeId}
          report={report}
          token={token}
          workspace={workspace}
        />
      </div>
    </>
  );
}
