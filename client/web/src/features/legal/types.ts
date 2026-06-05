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
  articleDiffs: LegalArticleDiff[];
};

export type LegalArticleDiff = {
  id: number;
  articleId?: number | null;
  articleKey: string;
  articleNo?: string | null;
  articleTitle?: string | null;
  changeType: string;
  beforeArticleVersionId?: number | null;
  afterArticleVersionId?: number | null;
  beforeHash?: string | null;
  afterHash?: string | null;
  beforeTextPreview?: string | null;
  afterTextPreview?: string | null;
  legalVersionId?: number | null;
  sourceVersionKey?: string | null;
  effectiveDate?: string | null;
  sourceUrl?: string | null;
  diffSummary: string;
  createdAt: string;
};
