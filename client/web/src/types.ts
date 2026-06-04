export type MembershipRole = "OWNER" | "ADMIN" | "MEMBER" | "VIEWER";
export type AssignmentStatus = "ACTIVE" | "REMOVED";
export type ProjectAssignmentRole = "MANAGER" | "REPORT_WRITER" | "VIEWER";
export type ReportAssignmentRole = "WRITER" | "REVIEWER" | "VIEWER";

export type Office = {
  id: number;
  officeCode: string;
  displayName: string;
  type: string;
  planCode: string;
  role: MembershipRole;
  permissions?: OfficePermissions;
};

export type OfficePermissions = {
  manageOfficeMembers: boolean;
  manageProjects: boolean;
  manageProjectAssignments: boolean;
  manageSites: boolean;
  createReports: boolean;
  writeReports: boolean;
  deleteReports: boolean;
  generateDocuments: boolean;
  uploadPhotos: boolean;
  accessOfficeAdmin: boolean;
};

export type MeResponse = {
  id: number;
  email: string;
  name: string;
  offices: Office[];
};

export type AuthTokenResponse = {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresInSeconds: number;
};

export type Project = {
  id: number;
  officeId: number;
  name: string;
  address?: string | null;
  buildingType?: string | null;
  startDate?: string | null;
  endDate?: string | null;
  status: string;
  manageAllowed?: boolean;
  structureManageAllowed?: boolean;
  reportCreateAllowed?: boolean;
};

export type Site = {
  id: number;
  officeId: number;
  projectId: number;
  siteCode?: string | null;
  name: string;
  address?: string | null;
  siteType?: string | null;
  startDate?: string | null;
  endDate?: string | null;
  status: string;
};

export type InspectionTarget = {
  id: number;
  officeId: number;
  projectId: number;
  siteId: number;
  parentTargetId?: number | null;
  targetType: string;
  code?: string | null;
  name: string;
  address?: string | null;
  metadata: Record<string, unknown>;
  status: string;
};

export type InspectionReport = {
  id: number;
  officeId: number;
  projectId: number;
  siteId?: number | null;
  reportNo: string;
  reportType: string;
  title?: string | null;
  status: string;
  currentStep?: string | null;
  templateId?: number | null;
  contentRevision: number;
  submittedRevision?: number | null;
  generatedRevision?: number | null;
  lastDocumentJobId?: number | null;
  writeAllowed?: boolean;
  reopenAllowed?: boolean;
};

export type ProjectAssignment = {
  id: number;
  officeId: number;
  projectId: number;
  userId: number;
  email?: string | null;
  name?: string | null;
  role: ProjectAssignmentRole;
  status: AssignmentStatus;
  assignedBy?: number | null;
  assignedAt: string;
  updatedAt: string;
};

export type ReportAssignment = {
  id: number;
  officeId: number;
  reportId: number;
  userId: number;
  email?: string | null;
  name?: string | null;
  role: ReportAssignmentRole;
  status: AssignmentStatus;
  assignedBy?: number | null;
  assignedAt: string;
  updatedAt: string;
};

export type InspectionStep = {
  stepCode: string;
  payloadStorageMode: string;
  payload: Record<string, unknown>;
  clientRevision: number;
  savedAt: string;
};

export type ChecklistItem = {
  id: number;
  itemCode: string;
  label: string;
  description?: string | null;
  answerType: "YES_NO" | "SELECT" | "TEXT" | "NUMBER" | "CHECK";
  required: boolean;
  displayOrder: number;
  options: string[];
};

export type ChecklistSchema = {
  id: number;
  officeId?: number | null;
  reportType: string;
  siteType?: string | null;
  targetType?: string | null;
  code: string;
  name: string;
  version: number;
  schema: Record<string, unknown>;
  items: ChecklistItem[];
};

export type ChecklistAnswer = {
  id: number;
  reportId: number;
  checklistSchemaId: number;
  checklistItemId: number;
  itemCode: string;
  targetId?: number | null;
  answer: Record<string, unknown>;
  note?: string | null;
  clientRevision: number;
  savedAt: string;
};

export type ReportChecklist = {
  schema: ChecklistSchema;
  answers: ChecklistAnswer[];
};

export type OfficeMember = {
  membershipId: number;
  userId: number;
  officeId: number;
  email: string;
  name: string;
  role: MembershipRole;
  status: string;
  joinedAt: string;
};

export type OfficeInvitationPreview = {
  email: string;
  officeId: number;
  officeCode: string;
  officeDisplayName: string;
  role: MembershipRole;
  status: string;
  expiresAt: string;
};
