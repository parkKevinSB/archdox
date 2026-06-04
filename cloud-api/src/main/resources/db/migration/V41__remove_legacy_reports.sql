-- ArchDox MVP scope is construction supervision only.
-- Remove already-created legacy/deferred definitions and reports so they do not
-- remain visible in report lists after document type cleanup.

delete from inspection_checklist_answers
where checklist_schema_id in (
    select id
    from checklist_schemas
    where office_id is not null
       or report_type not in (
        'CONSTRUCTION_DAILY_SUPERVISION_LOG',
        'CONSTRUCTION_SUPERVISION_REPORT'
    )
       or code not in (
        'CONSTRUCTION_DAILY_SUPERVISION_LOG_DEFAULT',
        'CONSTRUCTION_SUPERVISION_REPORT_DEFAULT'
    )
);

delete from checklist_items
where checklist_schema_id in (
    select id
    from checklist_schemas
    where office_id is not null
       or report_type not in (
        'CONSTRUCTION_DAILY_SUPERVISION_LOG',
        'CONSTRUCTION_SUPERVISION_REPORT'
    )
       or code not in (
        'CONSTRUCTION_DAILY_SUPERVISION_LOG_DEFAULT',
        'CONSTRUCTION_SUPERVISION_REPORT_DEFAULT'
    )
);

delete from checklist_schemas
where office_id is not null
   or report_type not in (
    'CONSTRUCTION_DAILY_SUPERVISION_LOG',
    'CONSTRUCTION_SUPERVISION_REPORT'
)
   or code not in (
    'CONSTRUCTION_DAILY_SUPERVISION_LOG_DEFAULT',
    'CONSTRUCTION_SUPERVISION_REPORT_DEFAULT'
);

delete from document_type_definitions
where office_id is not null
   or category <> 'CONSTRUCTION_SUPERVISION'
   or report_type not in (
    'CONSTRUCTION_DAILY_SUPERVISION_LOG',
    'CONSTRUCTION_SUPERVISION_REPORT'
)
   or code not in (
    'CONSTRUCTION_DAILY_SUPERVISION_LOG',
    'CONSTRUCTION_SUPERVISION_REPORT'
);

update inspection_reports
set last_document_job_id = null
where report_type not in (
    'CONSTRUCTION_DAILY_SUPERVISION_LOG',
    'CONSTRUCTION_SUPERVISION_REPORT'
);

delete from document_delivery_requests
where document_job_id in (
    select job.id
    from document_jobs job
    join inspection_reports report on report.id = job.report_id
    where report.report_type not in (
        'CONSTRUCTION_DAILY_SUPERVISION_LOG',
        'CONSTRUCTION_SUPERVISION_REPORT'
    )
)
   or artifact_id in (
    select artifact.id
    from document_artifacts artifact
    join inspection_reports report on report.id = artifact.report_id
    where report.report_type not in (
        'CONSTRUCTION_DAILY_SUPERVISION_LOG',
        'CONSTRUCTION_SUPERVISION_REPORT'
    )
);

delete from document_ai_review_findings
where report_id in (
    select id
    from inspection_reports
    where report_type not in (
        'CONSTRUCTION_DAILY_SUPERVISION_LOG',
        'CONSTRUCTION_SUPERVISION_REPORT'
    )
);

delete from document_ai_review_runs
where report_id in (
    select id
    from inspection_reports
    where report_type not in (
        'CONSTRUCTION_DAILY_SUPERVISION_LOG',
        'CONSTRUCTION_SUPERVISION_REPORT'
    )
);

delete from report_preflight_review_findings
where report_id in (
    select id
    from inspection_reports
    where report_type not in (
        'CONSTRUCTION_DAILY_SUPERVISION_LOG',
        'CONSTRUCTION_SUPERVISION_REPORT'
    )
);

delete from report_preflight_review_runs
where report_id in (
    select id
    from inspection_reports
    where report_type not in (
        'CONSTRUCTION_DAILY_SUPERVISION_LOG',
        'CONSTRUCTION_SUPERVISION_REPORT'
    )
);

delete from document_artifacts
where report_id in (
    select id
    from inspection_reports
    where report_type not in (
        'CONSTRUCTION_DAILY_SUPERVISION_LOG',
        'CONSTRUCTION_SUPERVISION_REPORT'
    )
);

delete from document_jobs
where report_id in (
    select id
    from inspection_reports
    where report_type not in (
        'CONSTRUCTION_DAILY_SUPERVISION_LOG',
        'CONSTRUCTION_SUPERVISION_REPORT'
    )
);

delete from photo_assets
where photo_id in (
    select photo.id
    from photos photo
    join inspection_reports report on report.id = photo.report_id
    where report.report_type not in (
        'CONSTRUCTION_DAILY_SUPERVISION_LOG',
        'CONSTRUCTION_SUPERVISION_REPORT'
    )
);

delete from photos
where report_id in (
    select id
    from inspection_reports
    where report_type not in (
        'CONSTRUCTION_DAILY_SUPERVISION_LOG',
        'CONSTRUCTION_SUPERVISION_REPORT'
    )
);

delete from site_supervision_entries
where source_report_id in (
    select id
    from inspection_reports
    where report_type not in (
        'CONSTRUCTION_DAILY_SUPERVISION_LOG',
        'CONSTRUCTION_SUPERVISION_REPORT'
    )
);

delete from inspection_checklist_answers
where report_id in (
    select id
    from inspection_reports
    where report_type not in (
        'CONSTRUCTION_DAILY_SUPERVISION_LOG',
        'CONSTRUCTION_SUPERVISION_REPORT'
    )
);

delete from inspection_report_targets
where report_id in (
    select id
    from inspection_reports
    where report_type not in (
        'CONSTRUCTION_DAILY_SUPERVISION_LOG',
        'CONSTRUCTION_SUPERVISION_REPORT'
    )
);

delete from inspection_report_assignments
where report_id in (
    select id
    from inspection_reports
    where report_type not in (
        'CONSTRUCTION_DAILY_SUPERVISION_LOG',
        'CONSTRUCTION_SUPERVISION_REPORT'
    )
);

delete from inspection_report_steps
where report_id in (
    select id
    from inspection_reports
    where report_type not in (
        'CONSTRUCTION_DAILY_SUPERVISION_LOG',
        'CONSTRUCTION_SUPERVISION_REPORT'
    )
);

delete from inspection_reports
where report_type not in (
    'CONSTRUCTION_DAILY_SUPERVISION_LOG',
    'CONSTRUCTION_SUPERVISION_REPORT'
);
