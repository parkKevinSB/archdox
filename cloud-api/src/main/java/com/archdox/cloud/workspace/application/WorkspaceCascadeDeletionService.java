package com.archdox.cloud.workspace.application;

import com.archdox.cloud.document.infra.DocumentLocalObjectStore;
import com.archdox.cloud.photo.application.PhotoStorageAdapterResolver;
import com.archdox.cloud.photo.domain.PhotoStorageKind;
import java.io.IOException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceCascadeDeletionService {
    private static final Logger log = LoggerFactory.getLogger(WorkspaceCascadeDeletionService.class);

    private final NamedParameterJdbcTemplate jdbc;
    private final PhotoStorageAdapterResolver photoStorageAdapterResolver;
    private final DocumentLocalObjectStore documentObjectStore;

    public WorkspaceCascadeDeletionService(
            NamedParameterJdbcTemplate jdbc,
            PhotoStorageAdapterResolver photoStorageAdapterResolver,
            DocumentLocalObjectStore documentObjectStore
    ) {
        this.jdbc = jdbc;
        this.photoStorageAdapterResolver = photoStorageAdapterResolver;
        this.documentObjectStore = documentObjectStore;
    }

    @Transactional
    public void deleteProject(Long officeId, Long projectId) {
        var params = Map.of("officeId", officeId, "projectId", projectId);
        deleteReportChildren("""
                select id
                from inspection_reports
                where office_id = :officeId and project_id = :projectId
                """, params);
        deletePhotoChildren("""
                select id
                from photos
                where office_id = :officeId and project_id = :projectId
                """, params);
        update("""
                delete from inspection_checklist_answers
                where office_id = :officeId
                  and target_id in (
                    select id from inspection_targets
                    where office_id = :officeId and project_id = :projectId
                  )
                """, params);
        update("""
                delete from inspection_report_targets
                where office_id = :officeId
                  and target_id in (
                    select id from inspection_targets
                    where office_id = :officeId and project_id = :projectId
                  )
                """, params);
        update("delete from inspection_targets where office_id = :officeId and project_id = :projectId", params);
        update("delete from project_assignments where office_id = :officeId and project_id = :projectId", params);
        update("delete from sites where office_id = :officeId and project_id = :projectId", params);
        update("delete from projects where office_id = :officeId and id = :projectId", params);
    }

    @Transactional
    public void deleteSite(Long officeId, Long projectId, Long siteId) {
        var params = Map.of("officeId", officeId, "projectId", projectId, "siteId", siteId);
        deleteReportChildren("""
                select id
                from inspection_reports
                where office_id = :officeId and project_id = :projectId and site_id = :siteId
                """, params);
        update("""
                delete from inspection_checklist_answers
                where office_id = :officeId
                  and target_id in (
                    select id from inspection_targets
                    where office_id = :officeId and project_id = :projectId and site_id = :siteId
                  )
                """, params);
        update("""
                delete from inspection_report_targets
                where office_id = :officeId
                  and target_id in (
                    select id from inspection_targets
                    where office_id = :officeId and project_id = :projectId and site_id = :siteId
                  )
                """, params);
        update("""
                delete from inspection_targets
                where office_id = :officeId and project_id = :projectId and site_id = :siteId
                """, params);
        update("""
                delete from sites
                where office_id = :officeId and project_id = :projectId and id = :siteId
                """, params);
    }

    @Transactional
    public void deleteReport(Long officeId, Long reportId) {
        var params = Map.of("officeId", officeId, "reportId", reportId);
        deleteReportChildren("""
                select id
                from inspection_reports
                where office_id = :officeId and id = :reportId
                """, params);
    }

    private void deleteReportChildren(String reportIdQuery, Map<String, ?> params) {
        deleteDocumentObjects(reportIdQuery, params);
        update("update inspection_reports set last_document_job_id = null where id in (" + reportIdQuery + ")", params);
        update("""
                delete from document_delivery_requests
                where document_job_id in (
                    select id from document_jobs where report_id in (
                """ + reportIdQuery + """
                    )
                )
                   or artifact_id in (
                    select id from document_artifacts where report_id in (
                """ + reportIdQuery + """
                    )
                )
                """, params);
        update("""
                delete from document_ai_review_findings where report_id in (
                """ + reportIdQuery + """
                )
                """, params);
        update("""
                delete from document_ai_review_runs where report_id in (
                """ + reportIdQuery + """
                )
                """, params);
        update("""
                delete from report_preflight_review_findings where report_id in (
                """ + reportIdQuery + """
                )
                """, params);
        update("""
                delete from report_preflight_review_runs where report_id in (
                """ + reportIdQuery + """
                )
                """, params);
        update("""
                delete from document_artifacts where report_id in (
                """ + reportIdQuery + """
                )
                """, params);
        update("""
                delete from document_jobs where report_id in (
                """ + reportIdQuery + """
                )
                """, params);
        deletePhotoChildren("""
                select id
                from photos
                where report_id in (
                """ + reportIdQuery + """
                )
                """, params);
        update("""
                delete from inspection_checklist_answers where report_id in (
                """ + reportIdQuery + """
                )
                """, params);
        update("""
                delete from inspection_report_targets where report_id in (
                """ + reportIdQuery + """
                )
                """, params);
        update("""
                delete from inspection_report_assignments where report_id in (
                """ + reportIdQuery + """
                )
                """, params);
        update("""
                delete from inspection_report_steps where report_id in (
                """ + reportIdQuery + """
                )
                """, params);
        update("""
                delete from inspection_reports where id in (
                """ + reportIdQuery + """
                )
                """, params);
    }

    private void deletePhotoChildren(String photoIdQuery, Map<String, ?> params) {
        deletePhotoObjects(photoIdQuery, params);
        update("""
                delete from photo_assets where photo_id in (
                """ + photoIdQuery + """
                )
                """, params);
        update("""
                delete from photos where id in (
                """ + photoIdQuery + """
                )
                """, params);
    }

    private void update(String sql, Map<String, ?> params) {
        jdbc.update(sql, params);
    }

    private void deleteDocumentObjects(String reportIdQuery, Map<String, ?> params) {
        var rows = jdbc.queryForList("""
                select storage_ref
                from document_artifacts
                where report_id in (
                """ + reportIdQuery + """
                )
                union all
                select prepared_storage_ref as storage_ref
                from document_delivery_requests
                where prepared_storage_ref is not null
                  and (
                    document_job_id in (
                        select id from document_jobs where report_id in (
                """ + reportIdQuery + """
                        )
                    )
                    or artifact_id in (
                        select id from document_artifacts where report_id in (
                """ + reportIdQuery + """
                        )
                    )
                  )
                """, params);
        rows.stream()
                .map(row -> stringValue(row.get("storage_ref")))
                .filter(ref -> ref != null && !ref.isBlank())
                .distinct()
                .forEach(this::deleteDocumentObjectIfExists);
    }

    private void deletePhotoObjects(String photoIdQuery, Map<String, ?> params) {
        var rows = jdbc.queryForList("""
                select storage_kind, storage_ref
                from photos
                where id in (
                """ + photoIdQuery + """
                )
                union all
                select storage_kind, thumbnail_storage_ref as storage_ref
                from photos
                where thumbnail_storage_ref is not null
                  and id in (
                """ + photoIdQuery + """
                  )
                union all
                select storage_kind, storage_ref
                from photo_assets
                where photo_id in (
                """ + photoIdQuery + """
                )
                """, params);
        rows.stream()
                .map(row -> new StorageRef(stringValue(row.get("storage_kind")), stringValue(row.get("storage_ref"))))
                .filter(ref -> ref.storageRef() != null && !ref.storageRef().isBlank())
                .distinct()
                .forEach(this::deletePhotoObjectIfExists);
    }

    private void deleteDocumentObjectIfExists(String storageRef) {
        try {
            documentObjectStore.deleteIfExists(storageRef);
        } catch (IOException | RuntimeException ex) {
            log.warn("Document object delete skipped. storageRef={}", storageRef, ex);
        }
    }

    private void deletePhotoObjectIfExists(StorageRef ref) {
        try {
            var storageKind = PhotoStorageKind.valueOf(ref.storageKind());
            if (storageKind == PhotoStorageKind.AGENT_MANAGED || storageKind == PhotoStorageKind.DELETED) {
                return;
            }
            photoStorageAdapterResolver.forStorageKind(storageKind).deleteIfExists(ref.storageRef());
        } catch (IOException | RuntimeException ex) {
            log.warn("Photo object delete skipped. storageKind={}, storageRef={}", ref.storageKind(), ref.storageRef(), ex);
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private record StorageRef(String storageKind, String storageRef) {
    }
}
