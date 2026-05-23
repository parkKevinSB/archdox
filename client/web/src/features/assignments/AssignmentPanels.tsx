import { CheckCircle2, Loader2, Plus, Trash2, Users } from "lucide-react";
import { type ReactNode, useMemo, useState } from "react";
import { useForm } from "react-hook-form";
import { EmptyState, InlineAlert, InlineNotice, StatusBadge } from "../../components/common";
import { useProjectAssignments } from "./hooks/useProjectAssignments";
import { useReportAssignments } from "./hooks/useReportAssignments";
import type {
  AssignmentRoleOption,
  InspectionReport,
  OfficeMember,
  Project,
  ProjectAssignment,
  ProjectAssignmentFormValues,
  ProjectAssignmentRole,
  ReportAssignment,
  ReportAssignmentFormValues,
  ReportAssignmentRole
} from "./types";

const projectRoleOptions: Array<AssignmentRoleOption<ProjectAssignmentRole>> = [
  { value: "MANAGER", label: "관리자" },
  { value: "REPORT_WRITER", label: "작성자" },
  { value: "VIEWER", label: "조회자" }
];

const reportRoleOptions: Array<AssignmentRoleOption<ReportAssignmentRole>> = [
  { value: "WRITER", label: "작성자" },
  { value: "REVIEWER", label: "검토자" },
  { value: "VIEWER", label: "조회자" }
];

type AssignmentPanelProps = {
  canManage: boolean;
  members: OfficeMember[];
  officeId: number | null;
  token: string;
};

export function ProjectAssignmentPanel({
  canManage,
  members,
  officeId,
  project,
  token
}: AssignmentPanelProps & {
  project: Project | null;
}) {
  const [notice, setNotice] = useState<string | null>(null);
  const form = useForm<ProjectAssignmentFormValues>({
    defaultValues: { userId: "", role: "REPORT_WRITER" }
  });
  const selectedUserId = form.watch("userId");
  const assignableMembers = useAssignableMembers(members);
  const { assignments, error, loading, removeAssignment, saving, upsertAssignment } = useProjectAssignments({
    token,
    officeId,
    projectId: project?.id ?? null
  });

  async function submit(values: ProjectAssignmentFormValues) {
    if (!values.userId) {
      return;
    }
    setNotice(null);
    await upsertAssignment({ userId: Number(values.userId), role: values.role });
    form.reset({ userId: "", role: "REPORT_WRITER" });
    setNotice("프로젝트 담당자가 저장되었습니다.");
  }

  async function remove(userId: number) {
    setNotice(null);
    await removeAssignment(userId);
    setNotice("프로젝트 담당자가 해제되었습니다.");
  }

  if (!project) {
    return <EmptyState title="프로젝트를 선택하세요" text="담당자 배정은 선택한 프로젝트 단위로 관리합니다." />;
  }

  return (
    <AssignmentPanelShell
      assignments={assignments}
      canManage={canManage}
      emptyText="아직 이 프로젝트에 지정된 담당자가 없습니다."
      error={errorMessage(error)}
      loading={loading}
      notice={notice}
      onRemove={remove}
      renderForm={
        canManage ? (
          <form className="assignment-form" onSubmit={form.handleSubmit(submit)}>
            <label>
              담당자
              <select {...form.register("userId", { required: true })}>
                <option value="">선택</option>
                {assignableMembers.map((member) => (
                  <option key={member.userId} value={member.userId}>
                    {member.name} · {member.role}
                  </option>
                ))}
              </select>
            </label>
            <label>
              역할
              <select {...form.register("role", { required: true })}>
                {projectRoleOptions.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </label>
            <button className="primary-button" disabled={saving || !selectedUserId} type="submit">
              {saving ? <Loader2 className="spin" size={17} /> : <Plus size={17} />}
              배정
            </button>
          </form>
        ) : (
          <InlineNotice message="프로젝트 담당자 변경은 OWNER 또는 ADMIN 권한이 필요합니다." />
        )
      }
      roleLabel={(value) => projectAssignmentRoleLabel(value as ProjectAssignmentRole)}
      saving={saving}
    />
  );
}

export function ReportAssignmentPanel({
  canManage,
  members,
  officeId,
  report,
  token
}: AssignmentPanelProps & {
  report: InspectionReport;
}) {
  const [notice, setNotice] = useState<string | null>(null);
  const form = useForm<ReportAssignmentFormValues>({
    defaultValues: { userId: "", role: "WRITER" }
  });
  const selectedUserId = form.watch("userId");
  const assignableMembers = useAssignableMembers(members);
  const { assignments, error, loading, removeAssignment, saving, upsertAssignment } = useReportAssignments({
    token,
    officeId,
    reportId: report.id
  });

  async function submit(values: ReportAssignmentFormValues) {
    if (!values.userId) {
      return;
    }
    setNotice(null);
    await upsertAssignment({ userId: Number(values.userId), role: values.role });
    form.reset({ userId: "", role: "WRITER" });
    setNotice("리포트 담당자가 저장되었습니다.");
  }

  async function remove(userId: number) {
    setNotice(null);
    await removeAssignment(userId);
    setNotice("리포트 담당자가 해제되었습니다.");
  }

  return (
    <AssignmentPanelShell
      assignments={assignments}
      canManage={canManage}
      emptyText="아직 이 리포트에 지정된 담당자가 없습니다."
      error={errorMessage(error)}
      loading={loading}
      notice={notice}
      onRemove={remove}
      renderForm={
        canManage ? (
          <form className="assignment-form" onSubmit={form.handleSubmit(submit)}>
            <label>
              담당자
              <select {...form.register("userId", { required: true })}>
                <option value="">선택</option>
                {assignableMembers.map((member) => (
                  <option key={member.userId} value={member.userId}>
                    {member.name} · {member.role}
                  </option>
                ))}
              </select>
            </label>
            <label>
              역할
              <select {...form.register("role", { required: true })}>
                {reportRoleOptions.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </label>
            <button className="primary-button" disabled={saving || !selectedUserId} type="submit">
              {saving ? <Loader2 className="spin" size={17} /> : <Plus size={17} />}
              배정
            </button>
          </form>
        ) : (
          <InlineNotice message="리포트 담당자 변경은 OWNER 또는 ADMIN 권한이 필요합니다." />
        )
      }
      roleLabel={(value) => reportAssignmentRoleLabel(value as ReportAssignmentRole)}
      saving={saving}
    />
  );
}

function AssignmentPanelShell<T extends { userId: number; name?: string | null; email?: string | null; role: string }>({
  assignments,
  canManage,
  emptyText,
  error,
  loading,
  notice,
  onRemove,
  renderForm,
  roleLabel,
  saving
}: {
  assignments: T[];
  canManage: boolean;
  emptyText: string;
  error: string | null;
  loading: boolean;
  notice: string | null;
  onRemove: (userId: number) => Promise<void>;
  renderForm: ReactNode;
  roleLabel: (role: string) => string;
  saving: boolean;
}) {
  return (
    <div className="assignment-panel">
      {renderForm}
      {error ? <InlineAlert message={error} /> : null}
      {notice ? <InlineNotice message={notice} /> : null}
      {loading ? <InlineNotice message="담당자 정보를 불러오는 중입니다." /> : null}
      <div className="assignment-list">
        {assignments.length === 0 && !loading ? (
          <div className="assignment-empty">
            <Users size={19} />
            <span>{emptyText}</span>
          </div>
        ) : (
          assignments.map((assignment) => (
            <div className="assignment-row" key={assignment.userId}>
              <div className="row-icon blue">
                <Users size={18} />
              </div>
              <div>
                <strong>{assignment.name ?? assignment.email ?? `user #${assignment.userId}`}</strong>
                <span>{assignment.email ?? `user #${assignment.userId}`}</span>
              </div>
              <StatusBadge status={roleLabel(assignment.role)} />
              {canManage ? (
                <button
                  className="icon-button"
                  disabled={saving}
                  onClick={() => onRemove(assignment.userId)}
                  title="배정 해제"
                  type="button"
                >
                  <Trash2 size={16} />
                </button>
              ) : (
                <CheckCircle2 size={18} />
              )}
            </div>
          ))
        )}
      </div>
    </div>
  );
}

function useAssignableMembers(members: OfficeMember[]) {
  return useMemo(
    () => members.filter((member) => member.status === "ACTIVE" && member.role !== "VIEWER"),
    [members]
  );
}

function errorMessage(error: unknown) {
  if (!error) {
    return null;
  }
  return error instanceof Error ? error.message : "담당자 정보를 처리하지 못했습니다.";
}

function projectAssignmentRoleLabel(role: ProjectAssignmentRole) {
  if (role === "MANAGER") {
    return "관리자";
  }
  if (role === "REPORT_WRITER") {
    return "작성자";
  }
  return "조회자";
}

function reportAssignmentRoleLabel(role: ReportAssignmentRole) {
  if (role === "WRITER") {
    return "작성자";
  }
  if (role === "REVIEWER") {
    return "검토자";
  }
  return "조회자";
}
