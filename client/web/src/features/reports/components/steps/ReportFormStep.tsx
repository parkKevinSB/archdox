import type { ReactNode } from "react";
import type { UseFormRegister, UseFormReturn } from "react-hook-form";
import type { InspectionReport, ReportStepDefinition, ReportWizardFormValues } from "../../types";

export type ReportStepComponentProps = {
  canWriteReports: boolean;
  definition: ReportStepDefinition;
  officeId: number;
  form: UseFormReturn<ReportWizardFormValues>;
  register: UseFormRegister<ReportWizardFormValues>;
  report: InspectionReport;
  renderChecklistPanel?: () => ReactNode;
  revision?: number;
  token: string;
};

export function ReportFormStep({ canWriteReports, definition, register, revision }: ReportStepComponentProps) {
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
          </label>
          )
        ))}
      </div>
    </>
  );
}
