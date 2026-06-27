with target_items(item_code, item_name, basis) as (
    values
        ('RC_REBAR_AFTER_INSPECTION', '배근검사 후 처리 확인', '배근검사 후 처리 확인'),
        ('RC_REBAR_REINFORCEMENT_PART', '보강부분 확인', '배근검사 후 보강부분 확인'),
        ('RC_REBAR_REINFORCEMENT_DETAIL', '보강근의 개수, 지름, 길이, 방법 확인', '배근검사 후 보강근의 개수, 지름, 길이, 방법 확인'),
        ('RC_REBAR_REINSPECTION_REQUIRED', '재검사 여부 확인', '배근검사 후 재검사 여부 확인')
),
template_bindings as (
    select *
    from legal_domain_bindings
    where binding_scope = 'CATALOG_ITEM'
      and catalog_code = 'CONSTRUCTION_SUPERVISION_CHECKLIST_2020_12_24'
      and catalog_version = 2
      and checklist_item_code = 'RC_REBAR_SPACING'
      and status = 'ACTIVE'
)
insert into legal_domain_bindings (
    binding_scope,
    binding_key,
    act_id,
    article_id,
    report_type,
    catalog_code,
    catalog_version,
    checklist_item_code,
    relevance,
    status,
    effective_from,
    effective_to,
    notes,
    metadata_json,
    created_at,
    updated_at
)
select
    template_bindings.binding_scope,
    replace(template_bindings.binding_key, 'RC_REBAR_SPACING', target_items.item_code),
    template_bindings.act_id,
    template_bindings.article_id,
    template_bindings.report_type,
    template_bindings.catalog_code,
    template_bindings.catalog_version,
    target_items.item_code,
    template_bindings.relevance,
    template_bindings.status,
    template_bindings.effective_from,
    template_bindings.effective_to,
    template_bindings.notes,
    template_bindings.metadata_json
        || jsonb_build_object(
            'inspectionItemName', target_items.item_name,
            'basis', target_items.basis,
            'backfillSourceChecklistItemCode', 'RC_REBAR_SPACING',
            'backfillReason', 'RC_REBAR_AFTER_INSPECTION_ROW_REFS'
        ),
    now(),
    now()
from target_items
cross join template_bindings
where not exists (
    select 1
    from legal_domain_bindings existing
    where existing.binding_scope = template_bindings.binding_scope
      and existing.catalog_code = template_bindings.catalog_code
      and existing.catalog_version = template_bindings.catalog_version
      and existing.checklist_item_code = target_items.item_code
      and existing.act_id = template_bindings.act_id
      and coalesce(existing.article_id, -1) = coalesce(template_bindings.article_id, -1)
      and existing.status = 'ACTIVE'
);
