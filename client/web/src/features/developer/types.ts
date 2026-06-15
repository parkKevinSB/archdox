export type EngineConnectClientType = "CODEX" | "CLAUDE" | "CURSOR" | "CHATGPT" | "CUSTOM_AGENT";

export type EngineConnectClient = {
  type: EngineConnectClientType;
  displayName: string;
  description: string;
};

export type EngineApiKey = {
  id: number;
  keyId: string;
  maskedKey: string;
  displayName: string;
  ownerUserId: number;
  officeId?: number | null;
  issuedByUserId: number;
  scopes: string[];
  dailyRequestUnitLimit: number;
  status: string;
  expiresAt?: string | null;
  lastUsedAt?: string | null;
  revokedAt?: string | null;
  createdAt: string;
  updatedAt: string;
};

export type EngineConnectBootstrapResponse = {
  connectionId: string;
  clientType: EngineConnectClientType;
  displayName: string;
  ownerUserId: number;
  officeId?: number | null;
  key: EngineApiKey;
  apiKey: string;
  engineApiBaseUrl: string;
  mcpServerUrl: string;
  headers: Record<string, string>;
  suggestedMcpConfig: Record<string, unknown>;
  curlExample: string;
  nextSteps: string[];
  createdAt: string;
};

export type EngineApiUsageGroup = {
  apiKeyId: number;
  keyId: string;
  ownerUserId: number;
  officeId?: number | null;
  capability: string;
  operation: string;
  eventCount: number;
  requestUnits: number;
  lastCalledAt?: string | null;
};

export type EngineApiUsageSummary = {
  from: string;
  to: string;
  totalEventCount: number;
  totalRequestUnits: number;
  groups: EngineApiUsageGroup[];
};

export type EngineApiUsageEvent = {
  id: number;
  apiKeyId: number;
  keyId: string;
  ownerUserId: number;
  officeId?: number | null;
  capability: string;
  operation: string;
  reviewSessionId?: string | null;
  status: string;
  requestUnits: number;
  metadata: Record<string, unknown>;
  createdAt: string;
};

export type McpToolCatalogItem = {
  name: string;
  title: string;
  description: string;
  capability: string;
  requiredScope: string;
  accessMode: string;
  operation: string;
  status: string;
  gatewayManagedUsage: boolean;
  usageMetering: string;
  baseRequestUnits: number;
  requestUnitPolicy: string;
  inputSchema: Record<string, unknown>;
  exampleArguments: Record<string, unknown>;
  errorCodes: string[];
  boundary: string;
};

export type McpLiveSmokeStep = {
  step: string;
  method: string;
  toolName?: string | null;
  httpStatus: number;
  status: string;
  success: boolean;
  elapsedMs: number;
  summary: string;
  errorCode?: string | null;
  errorCategory?: string | null;
  retryable?: boolean | null;
  responsePreview?: string | null;
};

export type McpLiveSmokeResult = {
  endpoint: string;
  status: string;
  success: boolean;
  stepCount: number;
  succeededCount: number;
  failedCount: number;
  elapsedMs: number;
  steps: McpLiveSmokeStep[];
  createdAt: string;
};
