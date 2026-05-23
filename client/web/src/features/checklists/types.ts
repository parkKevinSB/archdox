import type {
  ChecklistAnswer,
  ChecklistItem,
  InspectionReport,
  InspectionTarget,
  ReportChecklist
} from "../../types";

export type {
  ChecklistAnswer,
  ChecklistItem,
  InspectionReport,
  InspectionTarget,
  ReportChecklist
};

export type ChecklistFormValues = Record<string, string>;

export type SaveChecklistAnswerRequest = {
  targetId?: number | null;
  answer: Record<string, unknown>;
  note?: string | null;
};
