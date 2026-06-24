import type { ReportStepCode, ReportStepDefinition } from "./types";
import { reportStepDefinitions } from "./flow/reportFlowDefinition";

export { reportStepDefinitions } from "./flow/reportFlowDefinition";

export const CHECKLIST_SOURCE_FIELD_KEY = "checklistSelection";

export type ChecklistSourceOutputType = "TRADE" | "PHASE" | "ALL";
export type ChecklistSourceSelectionMode = "ALL_SITE" | "DATE_RANGE" | "SELECTED_REPORTS";

export type ChecklistSourceSelection = {
  dateFrom: string;
  dateTo: string;
  outputType: ChecklistSourceOutputType;
  selectedReportIds: number[];
  selectionMode: ChecklistSourceSelectionMode;
};

export const DEFAULT_CHECKLIST_SOURCE_SELECTION: ChecklistSourceSelection = {
  dateFrom: "",
  dateTo: "",
  outputType: "ALL",
  selectedReportIds: [],
  selectionMode: "ALL_SITE"
};

export function isReportStepCode(
  value?: string | null,
  definitions: ReportStepDefinition[] = reportStepDefinitions
): value is ReportStepCode {
  return Boolean(value && definitions.some((definition) => definition.code === value));
}

export function payloadFieldValue(payload: Record<string, unknown>, key: string) {
  const value = payload[key];
  if (typeof value === "number" || typeof value === "boolean") {
    return String(value);
  }
  if (typeof value === "string") {
    return value;
  }
  if (Array.isArray(value) || (value && typeof value === "object")) {
    return JSON.stringify(value);
  }
  return "";
}

export function payloadFromForm(definition: ReportStepDefinition, values: Record<string, string>) {
  return definition.fields.reduce<Record<string, unknown>>((payload, field) => {
    const formValue = values[field.key]?.trim() ?? "";
    const rawValue = formValue.length > 0 ? formValue : defaultFieldValue(definition, field.key);
    if (rawValue.length === 0) {
      return payload;
    }
    if (field.type === "number") {
      const numericValue = Number(rawValue);
      payload[field.key] = Number.isFinite(numericValue) ? numericValue : rawValue;
      return payload;
    }
    if (field.type === "json" || field.key.endsWith("Json") || field.key === "dailyItems") {
      try {
        payload[field.key] = JSON.parse(rawValue);
      } catch {
        payload[field.key] = rawValue;
      }
      return payload;
    }
    payload[field.key] = rawValue;
    return payload;
  }, {});
}

export function stepPayloadSatisfiesRequiredFields(definition: ReportStepDefinition, payload: Record<string, unknown>) {
  return definition.fields
    .filter((field) => field.required)
    .every((field) => !isEmptyPayloadValue(payload[field.key], field.type));
}

function defaultFieldValue(definition: ReportStepDefinition, fieldKey: string) {
  if (definition.stepType === "CHECKLIST_SOURCE" && fieldKey === CHECKLIST_SOURCE_FIELD_KEY) {
    return JSON.stringify(DEFAULT_CHECKLIST_SOURCE_SELECTION);
  }
  return "";
}

function isEmptyPayloadValue(value: unknown, fieldType?: string) {
  if (value === null || value === undefined) {
    return true;
  }
  if (typeof value === "string") {
    const text = value.trim();
    if (text.length === 0) {
      return true;
    }
    if (fieldType === "json") {
      try {
        return isEmptyPayloadValue(JSON.parse(text));
      } catch {
        return false;
      }
    }
    return false;
  }
  if (Array.isArray(value)) {
    return value.length === 0;
  }
  if (typeof value === "object") {
    return Object.keys(value).length === 0;
  }
  return false;
}
