import { FileText, Loader2 } from "lucide-react";
import { useForm } from "react-hook-form";
import { reportTypeOptions } from "../reportTypes";
import type { Project, ReportFormValues, Site } from "../types";

type ReportStartFormProps = {
  busy: boolean;
  onSubmit: (values: ReportFormValues) => Promise<void>;
  projects: Project[];
  selectedProjectId: number | null;
  selectedSiteId: number | null;
  sites: Site[];
};

type ReportStartFormFields = {
  reportType: string;
  title: string;
};

export function ReportStartForm({
  busy,
  onSubmit,
  projects,
  selectedProjectId,
  selectedSiteId,
  sites
}: ReportStartFormProps) {
  const form = useForm<ReportStartFormFields>({
    defaultValues: {
      reportType: reportTypeOptions[0].value,
      title: ""
    }
  });
  const canStart = Boolean(selectedProjectId && selectedSiteId);

  async function submit(values: ReportStartFormFields) {
    if (!selectedProjectId || !selectedSiteId) {
      return;
    }
    await onSubmit({
      projectId: selectedProjectId,
      siteId: selectedSiteId,
      reportType: values.reportType,
      title: values.title
    });
    form.reset({ reportType: values.reportType, title: "" });
  }

  return (
    <form className="compact-form" onSubmit={form.handleSubmit(submit)}>
      <label className="wide">
        프로젝트
        <input
          readOnly
          value={projects.find((project) => project.id === selectedProjectId)?.name ?? "프로젝트를 선택하세요"}
        />
      </label>
      <label className="wide">
        현장
        <input readOnly value={sites.find((site) => site.id === selectedSiteId)?.name ?? "현장을 선택하세요"} />
      </label>
      <label>
        리포트 유형
        <select {...form.register("reportType", { required: true })}>
          {reportTypeOptions.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      </label>
      <label className="wide">
        제목
        <input placeholder="예: 3월 1주차 감리일지" {...form.register("title")} />
      </label>
      <button className="primary-button" disabled={busy || !canStart} type="submit">
        {busy ? <Loader2 className="spin" size={17} /> : <FileText size={17} />}
        리포트 시작
      </button>
    </form>
  );
}
