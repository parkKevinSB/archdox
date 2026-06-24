import { describe, expect, it } from "vitest";
import {
  DEFAULT_CHECKLIST_SOURCE_SELECTION,
  payloadFromForm,
  stepPayloadSatisfiesRequiredFields
} from "./reportSteps";
import type { ReportStepDefinition } from "./types";

const checklistSourceDefinition: ReportStepDefinition = {
  code: "CHECKLIST_SOURCE",
  description: "Select checklist source reports.",
  fields: [
    {
      key: "checklistSelection",
      label: "Checklist selection",
      required: true,
      type: "json"
    }
  ],
  savePolicy: "ON_NAVIGATE",
  stepType: "CHECKLIST_SOURCE",
  title: "Checklist source"
};

describe("report step payload helpers", () => {
  it("saves the default checklist source selection when the form value is absent", () => {
    expect(payloadFromForm(checklistSourceDefinition, {})).toEqual({
      checklistSelection: DEFAULT_CHECKLIST_SOURCE_SELECTION
    });
  });

  it("saves the default checklist source selection when the form value is blank", () => {
    expect(payloadFromForm(checklistSourceDefinition, { checklistSelection: "" })).toEqual({
      checklistSelection: DEFAULT_CHECKLIST_SOURCE_SELECTION
    });
  });

  it("does not treat an empty saved checklist source payload as complete", () => {
    expect(stepPayloadSatisfiesRequiredFields(checklistSourceDefinition, {})).toBe(false);
  });

  it("treats a default checklist source payload as complete", () => {
    expect(stepPayloadSatisfiesRequiredFields(checklistSourceDefinition, {
      checklistSelection: DEFAULT_CHECKLIST_SOURCE_SELECTION
    })).toBe(true);
  });
});
