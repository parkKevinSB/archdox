import { FileText } from "lucide-react";
import { EmptyState, StatusBadge } from "../../../components/common";
import type { InspectionReport, Project } from "../types";

type ReportListProps = {
  onSelectReport?: (reportId: number) => void;
  projects: Project[];
  reports: InspectionReport[];
  selectedReportId?: number | null;
};

export function ReportList({ reports, projects, selectedReportId, onSelectReport }: ReportListProps) {
  if (reports.length === 0) {
    return <EmptyState title="리포트가 없습니다" text="현장을 선택하고 감리/점검 리포트 작성을 시작하세요." />;
  }
  return (
    <div className="item-list">
      {reports.map((report) => {
        const project = projects.find((item) => item.id === report.projectId);
        const rowContent = (
          <>
            <div className="row-icon blue">
              <FileText size={18} />
            </div>
            <div>
              <strong>{report.title || report.reportNo}</strong>
              <span>
                {project?.name ?? `project #${report.projectId}`} · {report.reportType}
              </span>
              <span className="revision-summary">
                작성 v{report.contentRevision} · 제출 v{report.submittedRevision ?? "-"} · 생성 v{report.generatedRevision ?? "-"}
              </span>
            </div>
            <StatusBadge status={report.status} />
          </>
        );
        if (onSelectReport) {
          return (
            <button
              className={selectedReportId === report.id ? "list-row selectable active" : "list-row selectable"}
              key={report.id}
              onClick={() => onSelectReport(report.id)}
              type="button"
            >
              {rowContent}
            </button>
          );
        }
        return (
          <article className="list-row" key={report.id}>
            {rowContent}
          </article>
        );
      })}
    </div>
  );
}
