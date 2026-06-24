import type { ReactNode } from "react";
import type { UseFormRegister, UseFormReturn } from "react-hook-form";
import type { InspectionReport, InspectionStep, ReportStepDefinition, ReportWizardFormValues, Site } from "../../types";

const weatherQuickOptions = ["맑음", "구름 많음", "흐림", "비", "소나기", "눈", "안개", "강풍"];

export type ReportStepComponentProps = {
  canWriteReports: boolean;
  definition: ReportStepDefinition;
  officeId: number;
  form: UseFormReturn<ReportWizardFormValues>;
  register: UseFormRegister<ReportWizardFormValues>;
  report: InspectionReport;
  renderChecklistPanel?: () => ReactNode;
  revision?: number;
  savedStep?: InspectionStep;
  savedSteps?: Record<string, InspectionStep>;
  site?: Site | null;
  token: string;
};

export function ReportFormStep({ canWriteReports, definition, form, register, revision }: ReportStepComponentProps) {
  return (
    <>
      <div className="wizard-form-head">
        <div>
          <h3>{definition.title}</h3>
          <p>{definition.description}</p>
        </div>
        {revision ? <span className="panel-context">rev {revision}</span> : null}
      </div>

      <div className="wizard-fields">
        {definition.fields.map((field) => (
          field.type === "hidden" || field.type === "json" ? (
            <input key={field.key} type="hidden" {...register(field.key)} />
          ) : (
          <label className={field.type === "textarea" ? "wide" : undefined} key={field.key}>
            {field.label}
            {field.type === "textarea" ? (
              <textarea
                aria-required={field.required}
                disabled={!canWriteReports}
                placeholder={field.placeholder}
                {...register(field.key)}
              />
            ) : (
              <input
                aria-required={field.required}
                disabled={!canWriteReports}
                placeholder={field.placeholder}
                type={field.type ?? "text"}
                {...register(field.key)}
              />
            )}
            {field.key === "weather" ? (
              <div className="weather-quick-options" role="group" aria-label="날씨 빠른 선택">
                {weatherQuickOptions.map((option) => {
                  const active = form.watch(field.key) === option;
                  return (
                    <button
                      className={active ? "active" : ""}
                      disabled={!canWriteReports}
                      key={option}
                      onClick={() => {
                        form.setValue(field.key, option, { shouldDirty: true, shouldValidate: true });
                      }}
                      type="button"
                    >
                      {option}
                    </button>
                  );
                })}
              </div>
            ) : null}
          </label>
          )
        ))}
      </div>
    </>
  );
}
