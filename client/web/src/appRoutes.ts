import type { ViewKey } from "./appTypes";

export const viewPaths: Record<ViewKey, string> = {
  home: "/",
  projects: "/projects",
  sites: "/sites",
  reports: "/reports",
  photos: "/photos",
  jobs: "/documents",
  workChat: "/work-chat",
  insightChat: "/insight-chat",
  more: "/more"
};

export function viewFromPath(pathname: string): ViewKey {
  if (pathname.startsWith("/projects")) {
    return "projects";
  }
  if (pathname.startsWith("/sites")) {
    return "sites";
  }
  if (pathname.startsWith("/reports")) {
    return "reports";
  }
  if (pathname.startsWith("/photos")) {
    return "photos";
  }
  if (pathname.startsWith("/documents")) {
    return "jobs";
  }
  if (pathname.startsWith("/insight-chat")) {
    return "insightChat";
  }
  if (pathname.startsWith("/work-chat") || pathname.startsWith("/chat")) {
    return "workChat";
  }
  if (pathname.startsWith("/more")) {
    return "more";
  }
  return "projects";
}

export function invitationTokenFromPathname(pathname: string) {
  const match = pathname.match(/^\/(?:invitations|office-invitations)\/([^/]+)/);
  return match ? decodeURIComponent(match[1]) : null;
}
