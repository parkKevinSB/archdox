import { request } from "../../api/http";
import type {
  EngineApiKey,
  EngineApiUsageEvent,
  EngineApiUsageSummary,
  EngineConnectBootstrapResponse,
  EngineConnectClient,
  EngineConnectClientType,
  McpToolCatalogItem
} from "./types";

export function getEngineConnectClients(token: string) {
  return request<EngineConnectClient[]>("/api/v1/engine/connect/clients", { token });
}

export function createEngineConnectBootstrap(
  token: string,
  body: {
    clientType: EngineConnectClientType;
    displayName?: string | null;
    officeId?: number | null;
    expiresAt?: string | null;
  }
) {
  return request<EngineConnectBootstrapResponse>("/api/v1/engine/connect/bootstrap", {
    token,
    method: "POST",
    body
  });
}

export function getMyEngineApiKeys(token: string) {
  return request<EngineApiKey[]>("/api/v1/engine/api-keys", { token });
}

export function revokeMyEngineApiKey(token: string, apiKeyId: number) {
  return request<EngineApiKey>(`/api/v1/engine/api-keys/${apiKeyId}/revoke`, {
    token,
    method: "POST"
  });
}

export function getMyEngineUsageSummary(token: string) {
  return request<EngineApiUsageSummary>("/api/v1/engine/usage/summary", { token });
}

export function getMyEngineUsageEvents(token: string, limit = 50) {
  return request<EngineApiUsageEvent[]>("/api/v1/engine/usage/events", {
    token,
    query: { limit }
  });
}

export function getMcpToolCatalog(token: string) {
  return request<McpToolCatalogItem[]>("/api/v1/engine/mcp-tools", { token });
}
