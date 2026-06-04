-- ArchDox MVP scope is construction supervision only.
-- Remove legacy/deferred report type definitions from the active database
-- instead of merely hiding them at the API layer.

delete from document_type_definitions
where office_id is not null
   or code not in (
    'CONSTRUCTION_DAILY_SUPERVISION_LOG',
    'CONSTRUCTION_SUPERVISION_REPORT'
)
   or report_type not in (
    'CONSTRUCTION_DAILY_SUPERVISION_LOG',
    'CONSTRUCTION_SUPERVISION_REPORT'
)
   or category <> 'CONSTRUCTION_SUPERVISION';

delete from inspection_checklist_answers
where checklist_schema_id in (
    select id
    from checklist_schemas
    where office_id is not null
       or code not in (
        'CONSTRUCTION_DAILY_SUPERVISION_LOG_DEFAULT',
        'CONSTRUCTION_SUPERVISION_REPORT_DEFAULT'
    )
       or report_type not in (
        'CONSTRUCTION_DAILY_SUPERVISION_LOG',
        'CONSTRUCTION_SUPERVISION_REPORT'
    )
);

delete from checklist_items
where checklist_schema_id in (
    select id
    from checklist_schemas
    where office_id is not null
       or code not in (
        'CONSTRUCTION_DAILY_SUPERVISION_LOG_DEFAULT',
        'CONSTRUCTION_SUPERVISION_REPORT_DEFAULT'
    )
       or report_type not in (
        'CONSTRUCTION_DAILY_SUPERVISION_LOG',
        'CONSTRUCTION_SUPERVISION_REPORT'
    )
);

delete from checklist_schemas
where office_id is not null
   or code not in (
    'CONSTRUCTION_DAILY_SUPERVISION_LOG_DEFAULT',
    'CONSTRUCTION_SUPERVISION_REPORT_DEFAULT'
)
   or report_type not in (
    'CONSTRUCTION_DAILY_SUPERVISION_LOG',
    'CONSTRUCTION_SUPERVISION_REPORT'
);
