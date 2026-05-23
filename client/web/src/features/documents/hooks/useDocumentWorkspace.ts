import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import {
  createDocumentDeliveryRequest,
  createDocumentJob,
  defaultDownloadFileName,
  directArtifactDownloadUrl,
  downloadDocumentUrl,
  fetchDocumentTextUrl,
  listDocumentDeliveryRequestsByJob,
  listDocumentJobsByReport
} from "../api";
import type {
  DocumentArtifactResponse,
  DocumentDeliveriesByJob,
  DocumentDeliveryRequestResponse,
  DocumentJobsByReport,
  DocumentJobResponse,
  DocumentOutputFormat,
  InspectionReport
} from "../types";

type UseDocumentWorkspaceOptions = {
  officeId: number | null;
  onRefreshWorkspace: () => Promise<void>;
  reports: InspectionReport[];
  token: string;
};

type CreateDocumentJobInput = {
  outputFormat?: DocumentOutputFormat;
  reportId: number;
};

export type DocumentPreviewState = {
  artifact: DocumentArtifactResponse;
  html: string;
  job: DocumentJobResponse;
};

const documentReportStatuses = new Set([
  "STEP_SAVED",
  "READY_TO_GENERATE",
  "GENERATION_REQUESTED",
  "GENERATING",
  "GENERATED",
  "FAILED"
]);

export function useDocumentWorkspace({ officeId, onRefreshWorkspace, reports, token }: UseDocumentWorkspaceOptions) {
  const queryClient = useQueryClient();
  const [preview, setPreview] = useState<DocumentPreviewState | null>(null);
  const documentReports = reports.filter((report) =>
    documentReportStatuses.has(report.status) && (report.status !== "STEP_SAVED" || report.contentRevision > (report.submittedRevision ?? 0))
  );
  const reportIds = documentReports.map((report) => report.id);
  const jobsQueryKey = ["document-jobs", officeId, reportIds.join(",")];

  const jobsQuery = useQuery({
    enabled: Boolean(token && officeId && documentReports.length > 0),
    queryKey: jobsQueryKey,
    queryFn: async () => {
      if (!officeId) {
        return {};
      }
      const entries = await Promise.all(
        documentReports.map(async (report) => [
          report.id,
          await listDocumentJobsByReport(token, officeId, report.id)
        ] as const)
      );
      return Object.fromEntries(entries) as DocumentJobsByReport;
    },
    refetchInterval: (query) => {
      const jobsByReport = query.state.data as DocumentJobsByReport | undefined;
      const hasActiveJob = Object.values(jobsByReport ?? {}).some((jobs) =>
        jobs.some((job) => ["REQUESTED", "GENERATING"].includes(job.status))
      );
      return hasActiveJob ? 3000 : false;
    }
  });

  const generatedJobs = Object.values(jobsQuery.data ?? {})
    .flat()
    .filter((job) => job.status === "GENERATED");
  const generatedJobIds = generatedJobs.map((job) => job.id);

  useEffect(() => {
    if (!jobsQuery.data || !hasReportStateDrift(documentReports, jobsQuery.data)) {
      return;
    }
    void onRefreshWorkspace();
  }, [jobsQuery.dataUpdatedAt]);

  const deliveriesQuery = useQuery({
    enabled: Boolean(token && officeId && generatedJobIds.length > 0),
    queryKey: ["document-deliveries", officeId, generatedJobIds.join(",")],
    queryFn: async () => {
      if (!officeId) {
        return {};
      }
      const entries = await Promise.all(
        generatedJobs.map(async (job) => [
          job.id,
          await listDocumentDeliveryRequestsByJob(token, officeId, job.id)
        ] as const)
      );
      return Object.fromEntries(entries) as DocumentDeliveriesByJob;
    },
    refetchInterval: (query) => {
      const deliveriesByJob = query.state.data as DocumentDeliveriesByJob | undefined;
      const hasActiveDelivery = Object.values(deliveriesByJob ?? {}).some((deliveries) =>
        deliveries.some((delivery) => ["REQUESTED", "SENDING"].includes(delivery.status))
      );
      return hasActiveDelivery ? 3000 : false;
    }
  });

  const createJobMutation = useMutation({
    mutationFn: async ({ outputFormat = "DOCX", reportId }: CreateDocumentJobInput) => {
      if (!officeId) {
        throw new Error("사무소 선택이 필요합니다.");
      }
      return createDocumentJob(token, officeId, reportId, {
        outputFormat
      });
    },
    onSuccess: async () => {
      await Promise.all([queryClient.invalidateQueries({ queryKey: ["document-jobs"] }), onRefreshWorkspace()]);
    }
  });

  const requestDeliveryMutation = useMutation({
    mutationFn: async ({ artifact, job }: { artifact: DocumentArtifactResponse; job: DocumentJobResponse }) => {
      if (!officeId) {
        throw new Error("사무소 선택이 필요합니다.");
      }
      const delivery = await createDocumentDeliveryRequest(token, officeId, job.id, artifact.id);
      if (delivery.downloadUrl) {
        await downloadDocumentUrl(token, officeId, delivery.downloadUrl, defaultDownloadFileName(artifact));
      }
      return delivery;
    },
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["document-deliveries"] }),
        onRefreshWorkspace()
      ]);
    }
  });

  const downloadPreparedMutation = useMutation({
    mutationFn: async ({ artifact, delivery }: { artifact: DocumentArtifactResponse; delivery: DocumentDeliveryRequestResponse }) => {
      if (!officeId) {
        throw new Error("사무소 선택이 필요합니다.");
      }
      if (!delivery.downloadUrl) {
        throw new Error("문서 파일을 아직 준비 중입니다.");
      }
      await downloadDocumentUrl(token, officeId, delivery.downloadUrl, defaultDownloadFileName(artifact));
      return delivery;
    },
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["document-deliveries"] }),
        onRefreshWorkspace()
      ]);
    }
  });

  const deliveriesByArtifact = Object.values(deliveriesQuery.data ?? {})
    .flat()
    .filter((delivery) => delivery.artifactId != null)
    .reduce<Record<number, DocumentDeliveryRequestResponse>>((acc, delivery) => {
      const artifactId = delivery.artifactId;
      if (artifactId == null) {
        return acc;
      }
      const current = acc[artifactId];
      if (!current || new Date(delivery.updatedAt).getTime() >= new Date(current.updatedAt).getTime()) {
        acc[artifactId] = delivery;
      }
      return acc;
    }, {});

  const previewArtifactMutation = useMutation({
    mutationFn: async ({ artifact, job }: { artifact: DocumentArtifactResponse; job: DocumentJobResponse }) => {
      if (!officeId) {
        throw new Error("사무소 선택이 필요합니다.");
      }
      if (artifact.artifactType !== "HTML") {
        throw new Error("HTML 문서만 브라우저 미리보기를 지원합니다.");
      }
      let downloadUrl = artifact.storageKind === "API_LOCAL" ? directArtifactDownloadUrl(artifact) : null;
      const existingDelivery = deliveriesByArtifact[artifact.id];
      if (!downloadUrl && existingDelivery?.status === "COMPLETED" && existingDelivery.downloadUrl) {
        downloadUrl = existingDelivery.downloadUrl;
      }
      if (!downloadUrl) {
        const delivery = await createDocumentDeliveryRequest(token, officeId, job.id, artifact.id);
        downloadUrl = delivery.downloadUrl ?? null;
      }
      if (!downloadUrl) {
        throw new Error("HTML 미리보기 파일을 준비 중입니다. 잠시 후 다시 시도해주세요.");
      }
      const html = await fetchDocumentTextUrl(token, officeId, downloadUrl);
      setPreview({ artifact, html, job });
      return { artifact, html, job };
    },
    onSettled: async () => {
      await queryClient.invalidateQueries({ queryKey: ["document-deliveries"] });
    }
  });

  return {
    closePreview: () => setPreview(null),
    createDocumentJob: createJobMutation.mutateAsync,
    creatingOutputFormat: createJobMutation.isPending ? createJobMutation.variables?.outputFormat ?? null : null,
    creatingReportId: createJobMutation.isPending ? createJobMutation.variables?.reportId ?? null : null,
    deliveriesByArtifact,
    documentReports,
    downloadPreparedArtifact: downloadPreparedMutation.mutateAsync,
    downloadingArtifactId: downloadPreparedMutation.variables?.artifact.id ?? null,
    error: jobsQuery.error ?? deliveriesQuery.error ?? createJobMutation.error ?? requestDeliveryMutation.error ?? downloadPreparedMutation.error ?? previewArtifactMutation.error,
    jobsByReport: jobsQuery.data ?? {},
    loading: jobsQuery.isLoading || deliveriesQuery.isLoading,
    preview,
    previewArtifact: previewArtifactMutation.mutateAsync,
    previewingArtifactId: previewArtifactMutation.isPending ? previewArtifactMutation.variables?.artifact.id ?? null : null,
    refreshJobs: async () => {
      await Promise.all([jobsQuery.refetch(), deliveriesQuery.refetch(), onRefreshWorkspace()]);
    },
    requestArtifactDelivery: requestDeliveryMutation.mutateAsync,
    requestingDeliveryArtifactId: requestDeliveryMutation.variables?.artifact.id ?? null
  };
}

function hasReportStateDrift(reports: InspectionReport[], jobsByReport: DocumentJobsByReport) {
  return reports.some((report) => {
    const latestJob = jobsByReport[report.id]?.[0];
    if (!latestJob) {
      return false;
    }
    if (latestJob.status === "GENERATED") {
      return report.status !== "GENERATED"
        || report.generatedRevision !== latestJob.reportRevision
        || report.lastDocumentJobId !== latestJob.id;
    }
    if (latestJob.status === "FAILED") {
      return report.status !== "FAILED" || report.lastDocumentJobId !== latestJob.id;
    }
    if (latestJob.status === "GENERATING") {
      return report.status !== "GENERATING";
    }
    if (latestJob.status === "REQUESTED") {
      return report.status !== "GENERATION_REQUESTED";
    }
    return false;
  });
}
