import type { MeResponse } from "./types";

export type AppState = {
  accessToken: string;
  refreshToken: string;
  user: MeResponse;
};

export type ViewKey =
  | "home"
  | "projects"
  | "sites"
  | "reports"
  | "photos"
  | "jobs"
  | "legalUpdates"
  | "developer"
  | "workChat"
  | "insightChat"
  | "more";
