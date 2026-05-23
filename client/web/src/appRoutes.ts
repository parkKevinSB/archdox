import type { ViewKey } from "./appTypes";

export const viewPaths: Record<ViewKey, string> = {
  home: "/",
  projects: "/projects",
  reports: "/reports",
  photos: "/photos",
  jobs: "/documents",
  more: "/more"
};

export function viewFromPath(pathname: string): ViewKey {
  if (pathname.startsWith("/projects")) {
    return "projects";
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
  if (pathname.startsWith("/more")) {
    return "more";
  }
  return "home";
}

export function invitationTokenFromPathname(pathname: string) {
  const match = pathname.match(/^\/(?:invitations|office-invitations)\/([^/]+)/);
  return match ? decodeURIComponent(match[1]) : null;
}
