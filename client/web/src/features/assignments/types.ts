import type {
  InspectionReport,
  OfficeMember,
  Project,
  ProjectAssignment,
  ProjectAssignmentRole,
  ReportAssignment,
  ReportAssignmentRole
} from "../../types";

export type {
  InspectionReport,
  OfficeMember,
  Project,
  ProjectAssignment,
  ProjectAssignmentRole,
  ReportAssignment,
  ReportAssignmentRole
};

export type ProjectAssignmentFormValues = {
  userId: string;
  role: ProjectAssignmentRole;
};

export type ReportAssignmentFormValues = {
  userId: string;
  role: ReportAssignmentRole;
};

export type AssignmentRoleOption<Role extends string> = {
  value: Role;
  label: string;
};

export type UpsertProjectAssignmentRequest = {
  userId: number;
  role: ProjectAssignmentRole;
};

export type UpsertReportAssignmentRequest = {
  userId: number;
  role: ReportAssignmentRole;
};
