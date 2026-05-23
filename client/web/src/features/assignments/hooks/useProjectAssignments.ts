import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { getProjectAssignments, removeProjectAssignment, upsertProjectAssignment } from "../api";
import type { UpsertProjectAssignmentRequest } from "../types";

type UseProjectAssignmentsOptions = {
  token: string;
  officeId: number | null;
  projectId: number | null;
};

export function useProjectAssignments({ token, officeId, projectId }: UseProjectAssignmentsOptions) {
  const queryClient = useQueryClient();
  const queryKey = ["projectAssignments", officeId, projectId] as const;
  const enabled = Boolean(token && officeId && projectId);

  const query = useQuery({
    queryKey,
    enabled,
    queryFn: () => getProjectAssignments(token, officeId!, projectId!)
  });

  const upsert = useMutation({
    mutationFn: (body: UpsertProjectAssignmentRequest) => {
      requireSelection(officeId, projectId, "프로젝트를 먼저 선택해주세요.");
      return upsertProjectAssignment(token, officeId!, projectId!, body);
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey })
  });

  const remove = useMutation({
    mutationFn: (userId: number) => {
      requireSelection(officeId, projectId, "프로젝트를 먼저 선택해주세요.");
      return removeProjectAssignment(token, officeId!, projectId!, userId);
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

function requireSelection(officeId: number | null, projectId: number | null, message: string) {
  if (!officeId || !projectId) {
    throw new Error(message);
  }
}
