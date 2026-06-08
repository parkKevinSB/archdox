alter table legal_digest_ai_drafts
    add column reviewed_by_user_id bigint,
    add column reviewed_at timestamptz;

update legal_digest_ai_drafts
set status = 'NEEDS_HUMAN_REVIEW'
where status = 'GENERATED';

create index idx_legal_digest_ai_drafts_review
    on legal_digest_ai_drafts (status, reviewed_at desc, id desc);
