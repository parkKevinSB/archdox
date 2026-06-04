export type LegalChangeDigest = {
  id: number;
  changeSetId: number;
  status: string;
  source: string;
  title: string;
  summary: string;
  impactSummary?: string | null;
  affectedReportTypes: string[];
  affectedCatalogItems: string[];
  aiHarnessRunId?: string | null;
  effectiveDate?: string | null;
  detectedAt: string;
  publishedAt?: string | null;
  createdAt: string;
  updatedAt: string;
};
