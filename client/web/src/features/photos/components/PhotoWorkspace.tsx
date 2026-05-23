import { Camera, Loader2, RefreshCw } from "lucide-react";
import { useMemo } from "react";
import {
  EmptyState,
  InlineAlert,
  MetricTile,
  Panel,
  StatusBadge,
  ViewHeader
} from "../../../components/common";
import type { InspectionReport, Project } from "../../../types";
import { usePhotoWorkspace } from "../hooks/usePhotoWorkspace";
import { hasWorkingAndThumbnail, PhotoPipelinePanel } from "./PhotoPipelinePanel";

type PhotoWorkspaceProps = {
  officeId: number | null;
  onSelectReport: (reportId: number) => void;
  projects: Project[];
  reports: InspectionReport[];
  selectedReport: InspectionReport | null;
  token: string;
};

const photoReportStatuses = new Set([
  "DRAFT",
  "STEP_SAVED",
  "READY_TO_GENERATE",
  "GENERATION_REQUESTED",
  "GENERATING",
  "GENERATED",
  "DELIVERED",
  "FAILED"
]);

export function PhotoWorkspace({
  officeId,
  onSelectReport,
  projects,
  reports,
  selectedReport,
  token
}: PhotoWorkspaceProps) {
  const photoReports = useMemo(
    () => reports.filter((report) => photoReportStatuses.has(report.status)),
    [reports]
  );
  const workspace = usePhotoWorkspace({ officeId, report: selectedReport, token });
  const pickupPending = workspace.photos.filter((photo) => photo.originalPickupStatus === "PENDING").length;
  const documentReady = workspace.photos.filter(hasWorkingAndThumbnail).length;

  return (
    <div className="view-stack">
      <ViewHeader
        title="사진"
        text="리포트에 들어갈 현장 사진을 올리고, 원본 보관/작업본/썸네일 상태를 한 화면에서 확인합니다."
      />

      <div className="metric-row compact">
        <MetricTile label="선택 리포트 사진" value={workspace.photos.length} detail="업로드된 사진" />
        <MetricTile label="원본 이관 대기" value={pickupPending} detail="ArchDox Agent/NAS pickup" />
        <MetricTile label="문서용 준비" value={documentReady} detail="작업본 + 썸네일" />
      </div>

      {workspace.error ? (
        <InlineAlert message={workspace.error instanceof Error ? workspace.error.message : "사진 작업을 처리하지 못했습니다."} />
      ) : null}

      <div className="photo-workspace">
        <Panel title="리포트 선택" action={<span className="panel-context">{photoReports.length}개</span>}>
          {photoReports.length === 0 ? (
            <EmptyState title="사진을 연결할 리포트가 없습니다" text="프로젝트 화면에서 리포트를 먼저 만든 뒤 사진을 업로드할 수 있습니다." />
          ) : (
            <div className="item-list">
              {photoReports.map((report) => {
                const project = projects.find((item) => item.id === report.projectId);
                return (
                  <button
                    className={selectedReport?.id === report.id ? "list-row selectable active" : "list-row selectable"}
                    key={report.id}
                    onClick={() => onSelectReport(report.id)}
                    type="button"
                  >
                    <div className="row-icon">
                      <Camera size={18} />
                    </div>
                    <div>
                      <strong>{report.title || report.reportNo}</strong>
                      <span>{project?.name ?? `project #${report.projectId}`} · {report.reportType}</span>
                    </div>
                    <StatusBadge status={report.status} />
                  </button>
                );
              })}
            </div>
          )}
        </Panel>

        <Panel
          title="사진 파이프라인"
          action={
            <button className="text-button" onClick={() => workspace.refreshPhotos()} type="button">
              {workspace.loading ? <Loader2 className="spin" size={16} /> : <RefreshCw size={16} />}
              새로고침
            </button>
          }
        >
          {selectedReport ? (
            <PhotoPipelinePanel officeId={officeId} report={selectedReport} token={token} workspace={workspace} />
          ) : (
            <EmptyState title="리포트를 먼저 선택하세요" text="사진은 프로젝트보다 리포트 작성 흐름에 붙어서 문서 생성까지 이어집니다." />
          )}
        </Panel>

        <Panel title="보관 정책">
          <div className="settings-list">
            <div>
              <strong>원본</strong>
              <span>사무소 플랜은 원본을 ArchDox Agent/NAS로 이관하고 클라우드 임시 원본은 삭제하는 방향입니다.</span>
            </div>
            <div>
              <strong>작업본</strong>
              <span>문서 생성과 미리보기에 쓰는 이미지입니다. 원본보다 작고 민감 EXIF 제거 대상입니다.</span>
            </div>
            <div>
              <strong>썸네일</strong>
              <span>목록과 빠른 확인에 쓰는 가벼운 이미지입니다. 실제 화면 미리보기 API는 다음 단계에서 붙입니다.</span>
            </div>
          </div>
        </Panel>
      </div>
    </div>
  );
}
