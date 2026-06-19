alter table site_supervision_entries
    add column if not exists trade_group_code text,
    add column if not exists trade_group_name text,
    add column if not exists phase_checklist_group_code text,
    add column if not exists phase_checklist_group_name text;

update site_supervision_entries
set trade_group_code = case
        when trade_code in (
             'TEMPORARY_WORKS',
             'EARTH_WORKS',
             'PILE_AND_FOUNDATION',
             'FORMWORK',
             'REINFORCED_CONCRETE',
             'STEEL_FRAME',
             'MASONRY_ALC_PANEL',
             'STONE_WORKS',
             'TILE_TERRACOTTA',
             'CARPENTRY',
             'INSULATION',
             'WATERPROOFING',
             'ROOF_GUTTER',
             'METAL_WORKS',
             'PLASTERING',
             'WINDOWS_DOORS',
             'GLASS_WORKS',
             'CURTAIN_WALL',
             'PAINTING',
             'INTERIOR_FINISH',
             'LANDSCAPE',
             'MISC_WORKS',
             'BUILDING_SURROUNDINGS') then 'ARCHITECTURE'
        when trade_code in (
             'ELEVATOR_MECHANICAL_PARKING',
             'PLUMBING_SANITARY',
             'HVAC',
             'PIPING',
             'DUCT',
             'MECHANICAL_AUTOMATION',
             'RENEWABLE_ENERGY',
             'REFRIGERATION',
             'CLEAN_ROOM',
             'GAS_FACILITY',
             'SOUND_VIBRATION_SEISMIC') then 'MECHANICAL'
        when trade_code in (
             'OUTDOOR_WORKS',
             'POWER_RECEIVING_LEAD_IN',
             'EMERGENCY_POWER',
             'INDOOR_WIRING',
             'LIGHTING',
             'POWER_FACILITY',
             'MONITORING_CONTROL',
             'LIGHTNING_GROUNDING') then 'ELECTRICAL'
        when trade_code in ('COMMUNICATION', 'LOW_VOLTAGE_SYSTEM') then 'COMMUNICATION'
        when trade_code in ('MECHANICAL_FIRE_FIGHTING', 'ELECTRICAL_FIRE_FIGHTING') then 'FIRE_FIGHTING'
        else trade_group_code
    end,
    trade_group_name = case
        when trade_code in (
             'TEMPORARY_WORKS',
             'EARTH_WORKS',
             'PILE_AND_FOUNDATION',
             'FORMWORK',
             'REINFORCED_CONCRETE',
             'STEEL_FRAME',
             'MASONRY_ALC_PANEL',
             'STONE_WORKS',
             'TILE_TERRACOTTA',
             'CARPENTRY',
             'INSULATION',
             'WATERPROOFING',
             'ROOF_GUTTER',
             'METAL_WORKS',
             'PLASTERING',
             'WINDOWS_DOORS',
             'GLASS_WORKS',
             'CURTAIN_WALL',
             'PAINTING',
             'INTERIOR_FINISH',
             'LANDSCAPE',
             'MISC_WORKS',
             'BUILDING_SURROUNDINGS') then '건축'
        when trade_code in (
             'ELEVATOR_MECHANICAL_PARKING',
             'PLUMBING_SANITARY',
             'HVAC',
             'PIPING',
             'DUCT',
             'MECHANICAL_AUTOMATION',
             'RENEWABLE_ENERGY',
             'REFRIGERATION',
             'CLEAN_ROOM',
             'GAS_FACILITY',
             'SOUND_VIBRATION_SEISMIC') then '기계설비'
        when trade_code in (
             'OUTDOOR_WORKS',
             'POWER_RECEIVING_LEAD_IN',
             'EMERGENCY_POWER',
             'INDOOR_WIRING',
             'LIGHTING',
             'POWER_FACILITY',
             'MONITORING_CONTROL',
             'LIGHTNING_GROUNDING') then '전기설비'
        when trade_code in ('COMMUNICATION', 'LOW_VOLTAGE_SYSTEM') then '통신 및 약전설비'
        when trade_code in ('MECHANICAL_FIRE_FIGHTING', 'ELECTRICAL_FIRE_FIGHTING') then '소화·소방 설비'
        else trade_group_name
    end
where trade_code is not null
  and trade_group_code is null;

update site_supervision_entries
set phase_checklist_group_code = 'PHASE_SUPERVISION',
    phase_checklist_group_name = '단계별 감리업무'
where group_type = 'PHASE'
  and phase_checklist_group_code is null;

create index if not exists ix_site_supervision_entries_trade_group
    on site_supervision_entries (office_id, site_id, trade_group_code, trade_code);
