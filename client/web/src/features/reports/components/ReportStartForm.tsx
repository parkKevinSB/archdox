import { FileText, Loader2 } from "lucide-react";
import { useEffect, useMemo } from "react";
import { useForm } from "react-hook-form";
import type { DocumentTypeDefinition, Project, ReportFormValues, Site } from "../types";

type ReportStartFormProps = {
  busy: boolean;
  documentTypes: DocumentTypeDefinition[];
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
  documentTypes,
  onSubmit,
  projects,
  selectedProjectId,
  selectedSiteId,
  sites
}: ReportStartFormProps) {
  const form = useForm<ReportStartFormFields>({
    defaultValues: {
      reportType: documentTypes[0]?.code ?? "",
      title: ""
    }
  });
  const selectedReportType = form.watch("reportType");
  const selectedDocumentType = useMemo(
    () => documentTypes.find((documentType) => documentType.code === selectedReportType) ?? null,
    [documentTypes, selectedReportType]
  );
  const selectedProject = projects.find((project) => project.id === selectedProjectId);
  const selectedSite = sites.find((site) => site.id === selectedSiteId);
  const canStart = Boolean(selectedProjectId && selectedSiteId && documentTypes.length > 0);

  useEffect(() => {
    const current = form.getValues("reportType");
    if (documentTypes.length === 0) {
      form.setValue("reportType", "");
      return;
    }
    if (!documentTypes.some((documentType) => documentType.code === current)) {
      form.setValue("reportType", documentTypes[0].code);
    }
  }, [documentTypes, form]);

  async function submit(values: ReportStartFormFields) {
    if (!selectedProjectId || !selectedSiteId || !values.reportType) {
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
        <input readOnly value={selectedProject?.name ?? "프로젝트를 선택하세요"} />
      </label>
      <label className="wide">
        현장
        <input readOnly value={selectedSite?.name ?? "현장을 선택하세요"} />
      </label>
      <label>
        감리업무 종류
        <input readOnly value={supervisionWorkModeLabel(selectedSite?.supervisionWorkMode)} />
      </label>
      <label>
        문서 유형
        <select disabled={documentTypes.length === 0} {...form.register("reportType", { required: true })}>
          {documentTypes.length === 0 ? <option value="">문서 유형을 불러오는 중입니다</option> : null}
          {documentTypes.map((documentType) => (
            <option key={documentType.code} value={documentType.code}>
              {documentType.name} / {categoryLabel(documentType.category)}
            </option>
          ))}
        </select>
      </label>
      <label className="wide">
        리포트 제목
        <input placeholder="비워두면 문서 유형 이름으로 생성됩니다" {...form.register("title")} />
      </label>
      {selectedDocumentType ? (
        <div className="document-type-summary wide">
          <div>
            <strong>{selectedDocumentType.name}</strong>
            <span>
              {selectedDocumentType.description
                ?? "선택한 문서 유형의 기본 작성 흐름, 체크리스트, 출력 포맷을 사용합니다."}
            </span>
          </div>
          <div className="document-type-meta">
            <span>출력 {selectedDocumentType.defaultOutputFormat}</span>
            {selectedDocumentType.checklistSchemaCode ? (
              <span>체크리스트 {selectedDocumentType.checklistSchemaCode}</span>
            ) : null}
          </div>
          <div className="document-type-steps" aria-label="리포트 작성 흐름">
            {selectedDocumentType.steps.map((step, index) => (
              <span key={step.code}>
                {index + 1}. {step.title}
              </span>
            ))}
          </div>
        </div>
      ) : null}
      <button className="primary-button" disabled={busy || !canStart} type="submit">
        {busy ? <Loader2 className="spin" size={17} /> : <FileText size={17} />}
        리포트 시작
      </button>
    </form>
  );
}

function supervisionWorkModeLabel(value?: string | null) {
  switch (value) {
    case "RESIDENT":
      return "상주 감리";
    case "RESPONSIBLE_RESIDENT":
      return "책임상주 감리";
    case "NON_RESIDENT":
    default:
      return "비상주 감리";
  }
}

function categoryLabel(category: string) {
  switch (category) {
    case "CONSTRUCTION_SUPERVISION":
      return "공사감리";
    default:
      return category;
  }
}
