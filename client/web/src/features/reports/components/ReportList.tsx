import { FileText, Trash2 } from "lucide-react";
import { EmptyState, StatusBadge } from "../../../components/common";
import type { InspectionReport, Project, Site } from "../types";

type ReportListProps = {
  canDelete?: boolean;
  canDeleteReport?: (report: InspectionReport) => boolean;
  deleting?: boolean;
  onDeleteReport?: (report: InspectionReport) => void;
  onSelectReport?: (reportId: number) => void;
  projects: Project[];
  reports: InspectionReport[];
  selectedReportId?: number | null;
  sites?: Site[];
};

export function ReportList({
  canDelete = false,
  canDeleteReport,
  deleting = false,
  reports,
  projects,
  selectedReportId,
  sites = [],
  onDeleteReport,
  onSelectReport
}: ReportListProps) {
  if (reports.length === 0) {
    return <EmptyState title="리포트가 없습니다" text="리포트 작성을 시작하세요." />;
  }
  return (
    <div className="item-list">
      {reports.map((report) => {
        const project = projects.find((item) => item.id === report.projectId);
        const site = sites.find((item) => item.id === report.siteId);
        const locationLabel = [project?.name ?? `project #${report.projectId}`, site?.name].filter(Boolean).join(" · ");
        const rowContent = (
          <>
            <div className="row-icon blue">
              <FileText size={18} />
            </div>
            <div>
              <strong>{report.title || report.reportNo}</strong>
              <span>
                {locationLabel} · {report.reportType}
              </span>
              <span className="revision-summary">
                작성 v{report.contentRevision} · 제출 v{report.submittedRevision ?? "-"} · 생성 v{report.generatedRevision ?? "-"}
              </span>
            </div>
            <StatusBadge status={report.status} />
          </>
        );
        return (
          <article
            className={selectedReportId === report.id ? "list-row list-row-shell selectable active" : "list-row list-row-shell selectable"}
            key={report.id}
          >
            {onSelectReport ? (
              <button className="list-row-main" onClick={() => onSelectReport(report.id)} type="button">
                {rowContent}
              </button>
            ) : (
              <div className="list-row-main static">
                {rowContent}
              </div>
            )}
            {canDelete && (canDeleteReport?.(report) ?? true) ? (
              <button
                className="icon-button danger"
                disabled={deleting}
                onClick={() => onDeleteReport?.(report)}
                title="리포트 삭제"
                type="button"
                aria-label={`${report.title || report.reportNo} 리포트 삭제`}
              >
                <Trash2 size={16} />
              </button>
            ) : null}
          </article>
        );
      })}
    </div>
  );
}
