import type { ReportStepCode, ReportStepDefinition } from "./types";
import { reportStepDefinitions } from "./flow/reportFlowDefinition";

export { reportStepDefinitions } from "./flow/reportFlowDefinition";

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
    const rawValue = values[field.key]?.trim() ?? "";
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
