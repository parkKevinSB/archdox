import type { ComponentType } from "react";
import { ReportChecklistStep } from "../components/steps/ReportChecklistStep";
import { ReportFormStep, type ReportStepComponentProps } from "../components/steps/ReportFormStep";
import { ReportPhotoStep } from "../components/steps/ReportPhotoStep";
import type { ReportStepType } from "../types";

export const reportStepRegistry: Partial<Record<ReportStepType, ComponentType<ReportStepComponentProps>>> = {
  FORM: ReportFormStep,
  CHECKLIST: ReportChecklistStep,
  PHOTO: ReportPhotoStep
};
