import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { getInspectionSteps } from "../../reports/api";
import {
  applyReportPreflightReviewFindingFix,
  applyDocumentNarrativeToReport,
  createReportPreflightReviewRun,
  createDocumentDeliveryRequest,
  createDocumentJob,
  defaultDownloadFileName,
  directArtifactDownloadUrl,
  downloadChecklistPrintDocx,
  downloadDocumentUrl,
  fetchChecklistPrintPreview,
  fetchDocumentTextUrl,
  listDocumentDeliveryRequestsByJob,
  listDocumentJobsByReport,
  listReportPreflightReviewFindings,
  listReportPreflightReviewRuns,
  polishDocumentNarrative,
  resolveReportPreflightReviewFinding
} from "../api";
import type {
  ChecklistPrintResponse,
  ChecklistPrintType,
  DocumentArtifactResponse,
  DocumentNarrativeApplyResponse,
  DocumentDeliveriesByJob,
  DocumentDeliveryRequestResponse,
  DocumentJobsByReport,
  DocumentJobResponse,
  DocumentNarrativePolishFieldInput,
  DocumentNarrativePolishResponse,
  DocumentOutputFormat,
  DocumentRenderOverrideInput,
  DocumentSignatureInput,
  InspectionReport,
  InspectionStep,
  ReportPreflightFindingResolutionStatus,
  ReportPreflightFindingsByRun,
  ReportPreflightReviewRunResponse,
  ReportPreflightRunsByReport
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
  renderOverrides?: DocumentRenderOverrideInput[];
  signature?: DocumentSignatureInput;
};

type ResolvePreflightFindingInput = {
  findingId: number;
  note?: string | null;
  reportId: number;
  runId: number;
  status: ReportPreflightFindingResolutionStatus;
};

type ApplyPreflightFindingFixInput = {
  findingId: number;
  reportId: number;
  runId: number;
};

type PolishNarrativeInput = {
  fields: DocumentNarrativePolishFieldInput[];
  reportId: number;
};

type ApplyNarrativeInput = {
  fields: DocumentNarrativePolishFieldInput[];
  reportId: number;
};

export type DocumentPreviewState = {
  artifact: DocumentArtifactResponse;
  html: string;
  job: DocumentJobResponse;
};

export type ChecklistPrintPreviewState = {
  html: string;
  response: ChecklistPrintResponse;
};

type InspectionStepsByReport = Record<number, InspectionStep[]>;

type ChecklistPrintPreviewInput = {
  reportId: number;
  type: ChecklistPrintType;
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
  const [checklistPreview, setChecklistPreview] = useState<ChecklistPrintPreviewState | null>(null);
  const [autoPreviewHtmlJobId, setAutoPreviewHtmlJobId] = useState<number | null>(null);
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

  const preflightRunsQuery = useQuery({
    enabled: Boolean(token && officeId && documentReports.length > 0),
    queryKey: ["report-preflight-reviews", officeId, reportIds.join(",")],
    queryFn: async () => {
      if (!officeId) {
        return {};
      }
      const entries = await Promise.all(
        documentReports.map(async (report) => [
          report.id,
          await listReportPreflightReviewRuns(token, officeId, report.id)
        ] as const)
      );
      return Object.fromEntries(entries) as ReportPreflightRunsByReport;
    },
    refetchInterval: (query) => {
      const runsByReport = query.state.data as ReportPreflightRunsByReport | undefined;
      const hasActiveReview = Object.values(runsByReport ?? {}).some((runs) =>
        runs.some((run) => shouldPollPreflightRun(run))
      );
      return hasActiveReview ? 3000 : false;
    }
  });

  const generatedJobs = Object.values(jobsQuery.data ?? {})
    .flat()
    .filter((job) => job.status === "GENERATED");
  const generatedJobIds = generatedJobs.map((job) => job.id);
  const latestPreflightRuns = Object.values(preflightRunsQuery.data ?? {})
    .map((runs) => runs[0])
    .filter(Boolean) as ReportPreflightReviewRunResponse[];
  const latestPreflightRunIds = latestPreflightRuns.map((run) => run.id);

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

  const preflightFindingsQuery = useQuery({
    enabled: Boolean(token && officeId && latestPreflightRuns.length > 0),
    queryKey: ["report-preflight-findings", officeId, latestPreflightRunIds.join(",")],
    queryFn: async () => {
      if (!officeId) {
        return {};
      }
      const entries = await Promise.all(
        latestPreflightRuns.map(async (run) => [
          run.id,
          await listReportPreflightReviewFindings(token, officeId, run.reportId, run.id)
        ] as const)
      );
      return Object.fromEntries(entries) as ReportPreflightFindingsByRun;
    },
    refetchInterval: preflightRunsQuery.data && latestPreflightRuns.some(shouldPollPreflightFindings) ? 3000 : false
  });

  const stepsQuery = useQuery({
    enabled: Boolean(token && officeId && documentReports.length > 0),
    queryKey: ["document-report-steps", officeId, reportIds.join(",")],
    queryFn: async () => {
      if (!officeId) {
        return {};
      }
      const entries = await Promise.all(
        documentReports.map(async (report) => [
          report.id,
          await getInspectionSteps(token, officeId, report.id)
        ] as const)
      );
      return Object.fromEntries(entries) as InspectionStepsByReport;
    }
  });

  const createJobMutation = useMutation({
    mutationFn: async ({ outputFormat = "DOCX", renderOverrides, reportId, signature }: CreateDocumentJobInput) => {
      if (!officeId) {
        throw new Error("사무소 선택이 필요합니다.");
      }
      return createDocumentJob(token, officeId, reportId, { outputFormat, renderOverrides, signature });
    },
    onSuccess: async (job, variables) => {
      setAutoPreviewHtmlJobId((variables.outputFormat ?? "DOCX") === "HTML" ? job.id : null);
      await Promise.all([queryClient.invalidateQueries({ queryKey: ["document-jobs"] }), onRefreshWorkspace()]);
    }
  });

  const polishNarrativeMutation = useMutation({
    mutationFn: async ({ fields, reportId }: PolishNarrativeInput) => {
      if (!officeId) {
        throw new Error("Office selection is required.");
      }
      return polishDocumentNarrative(token, officeId, reportId, fields);
    }
  });

  const applyNarrativeMutation = useMutation({
    mutationFn: async ({ fields, reportId }: ApplyNarrativeInput) => {
      if (!officeId) {
        throw new Error("Office selection is required.");
      }
      return applyDocumentNarrativeToReport(token, officeId, reportId, fields);
    },
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["document-report-steps"] }),
        queryClient.invalidateQueries({ queryKey: ["document-jobs"] }),
        onRefreshWorkspace()
      ]);
    }
  });

  const createPreflightReviewMutation = useMutation({
    mutationFn: async (reportId: number) => {
      if (!officeId) {
        throw new Error("?щТ???좏깮???꾩슂?⑸땲??");
      }
      return createReportPreflightReviewRun(token, officeId, reportId);
    },
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["report-preflight-reviews"] }),
        queryClient.invalidateQueries({ queryKey: ["report-preflight-findings"] }),
        queryClient.invalidateQueries({ queryKey: ["document-report-steps"] }),
        onRefreshWorkspace()
      ]);
    }
  });

  const resolvePreflightFindingMutation = useMutation({
    mutationFn: async ({ findingId, note, reportId, runId, status }: ResolvePreflightFindingInput) => {
      if (!officeId) {
        throw new Error("Office selection is required.");
      }
      return resolveReportPreflightReviewFinding(token, officeId, reportId, runId, findingId, {
        resolutionNote: note,
        resolutionStatus: status
      });
    },
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["report-preflight-reviews"] }),
        queryClient.invalidateQueries({ queryKey: ["report-preflight-findings"] }),
        queryClient.invalidateQueries({ queryKey: ["document-report-steps"] }),
        onRefreshWorkspace()
      ]);
    }
  });

  const applyPreflightFindingFixMutation = useMutation({
    mutationFn: async ({ findingId, reportId, runId }: ApplyPreflightFindingFixInput) => {
      if (!officeId) {
        throw new Error("Office selection is required.");
      }
      return applyReportPreflightReviewFindingFix(token, officeId, reportId, runId, findingId);
    },
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["report-preflight-reviews"] }),
        queryClient.invalidateQueries({ queryKey: ["report-preflight-findings"] }),
        onRefreshWorkspace()
      ]);
    }
  });

  const checklistPreviewMutation = useMutation({
    mutationFn: async ({ reportId, type }: ChecklistPrintPreviewInput) => {
      if (!officeId) {
        throw new Error("Office selection is required.");
      }
      return fetchChecklistPrintPreview(token, officeId, reportId, type);
    },
    onSuccess: (response) => {
      setChecklistPreview({
        html: response.html,
        response
      });
    }
  });

  const checklistDocxDownloadMutation = useMutation({
    mutationFn: async ({ reportId, type }: ChecklistPrintPreviewInput) => {
      if (!officeId) {
        throw new Error("Office selection is required.");
      }
      return downloadChecklistPrintDocx(token, officeId, reportId, type);
    }
  });

  const requestDeliveryMutation = useMutation({
    mutationFn: async ({ artifact, job }: { artifact: DocumentArtifactResponse; job: DocumentJobResponse }) => {
      if (!officeId) {
        throw new Error("사무소 선택이 필요합니다.");
      }
      if (artifact.storageKind === "API_LOCAL") {
        await downloadDocumentUrl(token, officeId, directArtifactDownloadUrl(artifact), defaultDownloadFileName(artifact));
        return null;
      }
      const delivery = await createDocumentDeliveryRequest(token, officeId, job.id, artifact.id);
      const downloadableDelivery = delivery.downloadUrl
        ? delivery
        : await waitForDeliveryDownloadUrl({
            artifactId: artifact.id,
            jobId: job.id,
            officeId,
            token
          });
      if (downloadableDelivery?.downloadUrl) {
        await downloadDocumentUrl(token, officeId, downloadableDelivery.downloadUrl, defaultDownloadFileName(artifact));
        return downloadableDelivery;
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
        throw new Error("문서 파일이 아직 준비 중입니다.");
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
        if (!downloadUrl) {
          const downloadableDelivery = await waitForDeliveryDownloadUrl({
            artifactId: artifact.id,
            jobId: job.id,
            officeId,
            token
          });
          downloadUrl = downloadableDelivery?.downloadUrl ?? null;
        }
      }
      if (!downloadUrl) {
        throw new Error("HTML 미리보기 파일이 준비 중입니다. 잠시 후 다시 시도하세요.");
      }
      const html = await fetchDocumentTextUrl(token, officeId, downloadUrl);
      setPreview({ artifact, html, job });
      return { artifact, html, job };
    },
    onSettled: async () => {
      await queryClient.invalidateQueries({ queryKey: ["document-deliveries"] });
    }
  });

  useEffect(() => {
    if (!autoPreviewHtmlJobId || previewArtifactMutation.isPending) {
      return;
    }
    const job = Object.values(jobsQuery.data ?? {})
      .flat()
      .find((item) => item.id === autoPreviewHtmlJobId);
    if (!job) {
      return;
    }
    if (job.status === "FAILED" || job.status === "CANCELLED") {
      setAutoPreviewHtmlJobId(null);
      return;
    }
    if (job.status !== "GENERATED") {
      return;
    }
    const artifact = job.artifacts.find((item) => item.artifactType === "HTML");
    if (!artifact) {
      setAutoPreviewHtmlJobId(null);
      return;
    }
    void previewArtifactMutation.mutateAsync({ artifact, job })
      .then(() => setAutoPreviewHtmlJobId(null))
      .catch(() => setAutoPreviewHtmlJobId(null));
  }, [autoPreviewHtmlJobId, jobsQuery.data, jobsQuery.dataUpdatedAt, previewArtifactMutation.isPending]);

  return {
    checklistPreview,
    closeChecklistPreview: () => setChecklistPreview(null),
    closePreview: () => setPreview(null),
    createDocumentJob: createJobMutation.mutateAsync,
    creatingOutputFormat: createJobMutation.isPending ? createJobMutation.variables?.outputFormat ?? null : null,
    creatingReportId: createJobMutation.isPending ? createJobMutation.variables?.reportId ?? null : null,
    deliveriesByArtifact,
    documentReports,
    downloadPreparedArtifact: downloadPreparedMutation.mutateAsync,
    downloadingArtifactId: downloadPreparedMutation.isPending ? downloadPreparedMutation.variables?.artifact.id ?? null : null,
    error: jobsQuery.error ?? deliveriesQuery.error ?? preflightRunsQuery.error ?? preflightFindingsQuery.error ?? stepsQuery.error ?? createJobMutation.error ?? createPreflightReviewMutation.error ?? resolvePreflightFindingMutation.error ?? applyPreflightFindingFixMutation.error ?? checklistPreviewMutation.error ?? checklistDocxDownloadMutation.error ?? polishNarrativeMutation.error ?? applyNarrativeMutation.error ?? requestDeliveryMutation.error ?? downloadPreparedMutation.error ?? previewArtifactMutation.error,
    jobsByReport: jobsQuery.data ?? {},
    loading: jobsQuery.isLoading || deliveriesQuery.isLoading || preflightRunsQuery.isLoading || preflightFindingsQuery.isLoading || stepsQuery.isLoading,
    preview,
    previewArtifact: previewArtifactMutation.mutateAsync,
    previewChecklistPrint: checklistPreviewMutation.mutateAsync,
    downloadChecklistPrintDocx: checklistDocxDownloadMutation.mutateAsync,
    downloadingChecklist: checklistDocxDownloadMutation.isPending ? checklistDocxDownloadMutation.variables ?? null : null,
    previewingChecklist: checklistPreviewMutation.isPending ? checklistPreviewMutation.variables ?? null : null,
    previewingArtifactId: previewArtifactMutation.isPending ? previewArtifactMutation.variables?.artifact.id ?? null : null,
    applyDocumentNarrativeToReport: applyNarrativeMutation.mutateAsync as (input: ApplyNarrativeInput) => Promise<DocumentNarrativeApplyResponse>,
    applyingNarrativeReportId: applyNarrativeMutation.isPending ? applyNarrativeMutation.variables?.reportId ?? null : null,
    polishDocumentNarrative: polishNarrativeMutation.mutateAsync as (input: PolishNarrativeInput) => Promise<DocumentNarrativePolishResponse>,
    polishingNarrativeReportId: polishNarrativeMutation.isPending ? polishNarrativeMutation.variables?.reportId ?? null : null,
    preflightFindingsByRun: preflightFindingsQuery.data ?? {},
    preflightRunsByReport: preflightRunsQuery.data ?? {},
    preflightRunsLoading: preflightRunsQuery.isLoading || preflightRunsQuery.isFetching,
    refreshJobs: async () => {
      await Promise.all([
        jobsQuery.refetch(),
        deliveriesQuery.refetch(),
        preflightRunsQuery.refetch(),
        preflightFindingsQuery.refetch(),
        stepsQuery.refetch(),
        onRefreshWorkspace()
      ]);
    },
    requestPreflightReview: createPreflightReviewMutation.mutateAsync,
    resolvePreflightFinding: resolvePreflightFindingMutation.mutateAsync,
    applyPreflightFindingFix: applyPreflightFindingFixMutation.mutateAsync,
    applyingPreflightFindingId: applyPreflightFindingFixMutation.isPending
      ? applyPreflightFindingFixMutation.variables?.findingId ?? null
      : null,
    resolvingPreflightFindingId: resolvePreflightFindingMutation.isPending
      ? resolvePreflightFindingMutation.variables?.findingId ?? null
      : null,
    requestArtifactDelivery: requestDeliveryMutation.mutateAsync,
    stepsByReport: stepsQuery.data ?? {},
    reviewingReportId: createPreflightReviewMutation.isPending ? createPreflightReviewMutation.variables ?? null : null,
    requestingDeliveryArtifactId: requestDeliveryMutation.isPending ? requestDeliveryMutation.variables?.artifact.id ?? null : null
  };
}

function isPreflightActive(run: ReportPreflightReviewRunResponse) {
  return run.status === "REQUESTED" || run.status === "RUNNING" || isPreflightAiPending(run);
}

function shouldPollPreflightRun(run: ReportPreflightReviewRunResponse) {
  return isPreflightActive(run) || shouldPollRecentlyCompletedAiRun(run);
}

function shouldPollPreflightFindings(run: ReportPreflightReviewRunResponse) {
  return isPreflightActive(run) || shouldPollRecentlyCompletedAiRun(run);
}

function shouldPollRecentlyCompletedAiRun(run: ReportPreflightReviewRunResponse) {
  if (!run.aiReviewPlanned || !isPreflightHarnessTerminal(run)) {
    return false;
  }
  const updatedAt = new Date(run.updatedAt).getTime();
  if (!Number.isFinite(updatedAt)) {
    return false;
  }
  return Date.now() - updatedAt < 20_000;
}

function isPreflightAiPending(run: ReportPreflightReviewRunResponse) {
  if (isPreflightTerminalStatus(run.status)) {
    return false;
  }
  if (!run.aiReviewPlanned) {
    return false;
  }
  if (run.terminalReason === "DETERMINISTIC_PREFLIGHT_BLOCKED") {
    return false;
  }
  return !isPreflightHarnessTerminal(run);
}

function isPreflightHarnessTerminal(run: ReportPreflightReviewRunResponse) {
  return ["SUCCEEDED", "COMPLETED", "FAILED", "CANCELLED", "SKIPPED"].includes(run.harnessStatus ?? "");
}

function isPreflightTerminalStatus(status: string) {
  return status === "PASSED" || status === "NEEDS_ATTENTION" || status === "FAILED";
}

async function waitForDeliveryDownloadUrl({
  artifactId,
  jobId,
  officeId,
  token
}: {
  artifactId: number;
  jobId: number;
  officeId: number;
  token: string;
}) {
  for (let attempt = 0; attempt < 20; attempt += 1) {
    await delay(1000);
    const deliveries = await listDocumentDeliveryRequestsByJob(token, officeId, jobId);
    const delivery = deliveries.find((item) => item.artifactId === artifactId);
    if (delivery?.status === "COMPLETED" && delivery.downloadUrl) {
      return delivery;
    }
    if (delivery?.status === "FAILED") {
      throw new Error(delivery.errorMessage ?? "문서 다운로드 준비에 실패했습니다.");
    }
  }
  return null;
}

function delay(ms: number) {
  return new Promise((resolve) => window.setTimeout(resolve, ms));
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
