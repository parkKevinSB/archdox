import type { Office } from "../types";

export function canManageProjects(office: Office | null | undefined) {
  if (!office) {
    return false;
  }
  if (office.permissions) {
    return office.permissions.manageProjects;
  }
  if (office.type === "PERSONAL") {
    return office.role === "OWNER";
  }
  return office.role === "OWNER" || office.role === "ADMIN";
}

export function canManageOfficeAssignments(office: Office | null | undefined) {
  if (!office) {
    return false;
  }
  if (office.permissions) {
    return office.permissions.manageProjectAssignments;
  }
  return office.type !== "PERSONAL" && (office.role === "OWNER" || office.role === "ADMIN");
}

export function canManageSites(office: Office | null | undefined) {
  if (!office) {
    return false;
  }
  if (office.permissions) {
    return office.permissions.manageSites;
  }
  return canManageProjects(office);
}

export function canWriteReports(office: Office | null | undefined) {
  if (!office) {
    return false;
  }
  if (office.permissions) {
    return office.permissions.writeReports;
  }
  if (office.type === "PERSONAL") {
    return office.role === "OWNER";
  }
  return office.role === "OWNER" || office.role === "ADMIN";
}
