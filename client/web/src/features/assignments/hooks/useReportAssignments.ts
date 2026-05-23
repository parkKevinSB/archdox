import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { getReportAssignments, removeReportAssignment, upsertReportAssignment } from "../api";
import type { UpsertReportAssignmentRequest } from "../types";

type UseReportAssignmentsOptions = {
  token: string;
  officeId: number | null;
  reportId: number | null;
};

export function useReportAssignments({ token, officeId, reportId }: UseReportAssignmentsOptions) {
  const queryClient = useQueryClient();
  const queryKey = ["reportAssignments", officeId, reportId] as const;
  const enabled = Boolean(token && officeId && reportId);

  const query = useQuery({
    queryKey,
    enabled,
    queryFn: () => getReportAssignments(token, officeId!, reportId!)
  });

  const upsert = useMutation({
    mutationFn: (body: UpsertReportAssignmentRequest) => {
      requireSelection(officeId, reportId, "리포트를 먼저 선택해주세요.");
      return upsertReportAssignment(token, officeId!, reportId!, body);
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey })
  });

  const remove = useMutation({
    mutationFn: (userId: number) => {
      requireSelection(officeId, reportId, "리포트를 먼저 선택해주세요.");
      return removeReportAssignment(token, officeId!, reportId!, userId);
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey })
  });

  return {
    assignments: query.data ?? [],
    error: query.error ?? upsert.error ?? remove.error,
    loading: query.isLoading,
    removeAssignment: remove.mutateAsync,
    saving: upsert.isPending || remove.isPending,
    upsertAssignment: upsert.mutateAsync
  };
}

function requireSelection(officeId: number | null, reportId: number | null, message: string) {
  if (!officeId || !reportId) {
    throw new Error(message);
  }
}
