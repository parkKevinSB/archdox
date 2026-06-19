alter table site_supervision_entries
    add column if not exists sub_trade_code text,
    add column if not exists sub_trade_name text;
