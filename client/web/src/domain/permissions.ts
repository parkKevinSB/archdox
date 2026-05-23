import type { Office } from "../types";

export function canManageProjects(office: Office | null | undefined) {
  if (!office) {
    return false;
  }
  if (office.type === "PERSONAL") {
    return office.role === "OWNER";
  }
  return office.role === "OWNER" || office.role === "ADMIN";
}

export function canWriteReports(office: Office | null | undefined) {
  if (!office) {
    return false;
  }
  if (office.type === "PERSONAL") {
    return office.role === "OWNER";
  }
  return office.role === "OWNER" || office.role === "ADMIN" || office.role === "MEMBER";
}
