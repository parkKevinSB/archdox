import { request } from "../../api/http";
import type { LegalChangeDigest } from "./types";

export function listLegalUpdates(token: string, days = 30, limit = 50) {
  return request<LegalChangeDigest[]>("/api/v1/legal-updates", {
    token,
    query: { days, limit }
  });
}

export function getLegalUpdate(token: string, id: number) {
  return request<LegalChangeDigest>(`/api/v1/legal-updates/${id}`, { token });
}
