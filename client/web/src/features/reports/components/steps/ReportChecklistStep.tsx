import { ReportFormStep, type ReportStepComponentProps } from "./ReportFormStep";

export function ReportChecklistStep(props: ReportStepComponentProps) {
  return (
    <>
      <ReportFormStep {...props} />
      {props.renderChecklistPanel?.()}
    </>
  );
}
