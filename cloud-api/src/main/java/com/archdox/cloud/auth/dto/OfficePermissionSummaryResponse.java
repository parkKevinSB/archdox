package com.archdox.cloud.auth.dto;

public record OfficePermissionSummaryResponse(
        boolean manageOfficeMembers,
        boolean manageProjects,
        boolean manageProjectAssignments,
        boolean manageSites,
        boolean createReports,
        boolean writeReports,
        boolean deleteReports,
        boolean generateDocuments,
        boolean uploadPhotos,
        boolean accessOfficeAdmin
) {
}
