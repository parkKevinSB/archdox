import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  attachInspectionReportTarget,
  getReportChecklist,
  saveChecklistAnswer
} from "../api";
import type {
  ChecklistAnswer,
  ReportChecklist,
  SaveChecklistAnswerRequest
} from "../types";

type UseReportChecklistOptions = {
  token: string;
  officeId: number;
  reportId: number;
};

export function useReportChecklist({ token, officeId, reportId }: UseReportChecklistOptions) {
  const queryClient = useQueryClient();
  const queryKey = ["reportChecklist", officeId, reportId] as const;

  const query = useQuery({
    queryKey,
    queryFn: () => getReportChecklist(token, officeId, reportId)
  });

  const saveAnswer = useMutation({
    mutationFn: ({ itemCode, body }: { itemCode: string; body: SaveChecklistAnswerRequest }) =>
      saveChecklistAnswer(token, officeId, reportId, itemCode, body),
    onSuccess: (answer) => {
      queryClient.setQueryData<ReportChecklist | undefined>(queryKey, (current) => mergeChecklistAnswer(current, answer));
    }
  });

  const attachTarget = useMutation({
    mutationFn: (targetId: number) => attachInspectionReportTarget(token, officeId, reportId, targetId)
  });

  return {
    attachTarget: attachTarget.mutateAsync,
    attachTargetError: attachTarget.error,
    attachingTarget: attachTarget.isPending,
    checklist: query.data ?? null,
    loading: query.isLoading,
    loadError: query.error,
    saveAnswer: saveAnswer.mutateAsync,
    saveError: saveAnswer.error,
    savingItem: saveAnswer.isPending
  };
}

function mergeChecklistAnswer(checklist: ReportChecklist | undefined, answer: ChecklistAnswer) {
  if (!checklist) {
    return checklist;
  }
  const answers = checklist.answers.filter((current) => current.id !== answer.id && current.itemCode !== answer.itemCode);
  return { ...checklist, answers: [...answers, answer] };
}
