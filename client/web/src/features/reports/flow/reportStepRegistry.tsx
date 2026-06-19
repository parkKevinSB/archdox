import type { ComponentType } from "react";
import { ChecklistSourceStep } from "../components/steps/ChecklistSourceStep";
import { DailySupervisionItemsStep } from "../components/steps/DailySupervisionItemsStep";
import { ReportChecklistStep } from "../components/steps/ReportChecklistStep";
import { ReportFormStep, type ReportStepComponentProps } from "../components/steps/ReportFormStep";
import { ReportPhotoStep } from "../components/steps/ReportPhotoStep";
import type { ReportStepType } from "../types";

export const reportStepRegistry: Partial<Record<ReportStepType, ComponentType<ReportStepComponentProps>>> = {
  FORM: ReportFormStep,
  CHECKLIST: ReportChecklistStep,
  CHECKLIST_SOURCE: ChecklistSourceStep,
  DAILY_SUPERVISION_ITEMS: DailySupervisionItemsStep,
  PHOTO: ReportPhotoStep
};
