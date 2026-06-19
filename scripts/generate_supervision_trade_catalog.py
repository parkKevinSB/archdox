import hashlib
import json
import re
import subprocess
from collections import OrderedDict
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
TRANSCRIPTION_DIR = ROOT / "docs" / "reference-forms" / "korean" / "체크리스트json"
MANIFEST_PATH = ROOT / "cloud-api" / "src" / "main" / "resources" / "domain-catalogs" / "construction-supervision-checklist-2020-12-24.json"
PART_PATH = ROOT / "cloud-api" / "src" / "main" / "resources" / "domain-catalogs" / "construction-supervision-checklist-2020-12-24" / "trade-checklist.json"

K_MODE = "\uc5c5\ubb34\uad6c\ubd84\ucf54\ub4dc"
K_TRADES = "\uacf5\uc885\ubaa9\ub85d"
K_TRADE_GROUP = "\uacf5\uc885\uadf8\ub8f9"
K_TRADE = "\uacf5\uc885"
K_SUB_TRADE = "\uc138\ubd80\uacf5\uc885"
K_SOURCE_PAGE = "\ucd9c\ucc98\ud398\uc774\uc9c0"
K_WORK_CATEGORIES = "\uc5c5\ubb34\uad6c\ubd84\uad70"
K_WORK_CATEGORY = "\uad6c\ubd84"
K_PROCESS_GROUPS = "\uc138\ubd80\uacf5\uc815\ubaa9\ub85d"
K_PROCESS = "\uc138\ubd80\uacf5\uc815"
K_ITEMS = "\uac80\uc0ac\ud56d\ubaa9\ubaa9\ub85d"
K_ITEM = "\uac80\uc0ac\ud56d\ubaa9"
K_ROWS = "\uc138\ubd80\uac10\ub9ac\ud56d\ubaa9"

MODE_ORDER = ["NON_RESIDENT", "RESIDENT", "RESPONSIBLE_RESIDENT"]
SUB_TRADE_NONE_CODE = "NONE"
SUB_TRADE_NONE_NAME = "없음"
MODE_META = {
    "NON_RESIDENT": {
        "code": "NON_RESIDENT",
        "name": "비상주 감리",
        "referencePages": "22-73",
        "description": "비상주 공사감리 체크리스트 전사본입니다.",
        "message": "비상주 공사감리 체크리스트 기준으로 전사된 카탈로그입니다.",
    },
    "RESIDENT": {
        "code": "RESIDENT",
        "name": "상주 감리",
        "referencePages": "74-126",
        "description": "상주 공사감리 체크리스트 전사본입니다.",
        "message": "상주 공사감리 체크리스트 기준으로 전사된 카탈로그입니다.",
    },
    "RESPONSIBLE_RESIDENT": {
        "code": "RESPONSIBLE_RESIDENT",
        "name": "책임상주 감리",
        "referencePages": "127-178",
        "description": "책임상주 공사감리 체크리스트 전사본입니다.",
        "message": "책임상주 공사감리 체크리스트 기준으로 전사된 카탈로그입니다.",
    },
}

GROUP_NAME_TO_CODE = {
    "건축": "ARCHITECTURE",
    "기계설비": "MECHANICAL",
    "전기설비": "ELECTRICAL",
    "통신 및 약전설비": "COMMUNICATION",
    "소화·소방 설비": "FIRE_FIGHTING",
    "소화ㆍ소방 설비": "FIRE_FIGHTING",
}

TRADE_CODE_OVERRIDES = {
    "벽돌블록alc패널공사": "MASONRY_ALC_PANEL",
}


def clean(value):
    if value is None:
        return ""
    return re.sub(r"\s+", " ", str(value)).strip()


def normalize_code(value):
    return clean(value).upper()


def normalize_hash_text(value):
    return clean(value).casefold()


def trade_match_key(value):
    text = clean(value).casefold()
    text = re.sub(r"[·ㆍ•⦁\s()（）]+", "", text)
    text = text.replace("및", "")
    return text


def parse_pages(value):
    text = clean(value)
    if not text or text == "-":
        return []
    pages = []
    for token in re.split(r"[,/]", text):
        token = token.strip()
        if not token:
            continue
        if "-" in token:
            start, end = token.split("-", 1)
            if start.strip().isdigit() and end.strip().isdigit():
                pages.extend(range(int(start), int(end) + 1))
            continue
        if token.isdigit():
            pages.append(int(token))
    return sorted(dict.fromkeys(pages))


def digest_code(*parts):
    payload = "|".join(normalize_hash_text(part) for part in parts)
    return hashlib.sha1(payload.encode("utf-8")).hexdigest()[:10].upper()


def generated_code(prefix, kind, *parts, used_codes):
    base = f"{prefix}_{kind}_{digest_code(prefix, kind, *parts)}"
    code = base
    i = 2
    while code in used_codes:
        code = f"{base}_{i}"
        i += 1
    used_codes.add(code)
    return code


def load_json(path):
    return json.loads(path.read_text(encoding="utf-8-sig"), object_pairs_hook=OrderedDict)


def load_head_json(path):
    relative = path.relative_to(ROOT).as_posix()
    try:
        content = subprocess.check_output(["git", "show", f"HEAD:{relative}"], cwd=ROOT)
    except (subprocess.CalledProcessError, FileNotFoundError):
        return None
    return json.loads(content.decode("utf-8"), object_pairs_hook=OrderedDict)


def write_json(path, data):
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def find_transcriptions():
    result = {}
    for path in TRANSCRIPTION_DIR.glob("공종별체크리스트_*.json"):
        data = load_json(path)
        mode = clean(data.get(K_MODE))
        if mode in MODE_ORDER:
            result[mode] = (path, data)
    missing = [mode for mode in MODE_ORDER if mode not in result]
    if missing:
        raise SystemExit(f"Missing trade checklist transcriptions: {missing}")
    return result


def build_seed_indexes(existing_part):
    existing_parts = []
    head_part = load_head_json(PART_PATH)
    if head_part:
        existing_parts.append(head_part)
    existing_parts.append(existing_part)

    atoms = existing_part.get("canonicalAtoms", {})
    trade_by_key = {}
    trade_group_by_code = {}
    for part in existing_parts:
        part_atoms = part.get("canonicalAtoms", {})
        for code, value in part_atoms.get("trades", {}).items():
            trade_by_key.setdefault(trade_match_key(value.get("name")), code)
        for trade in part.get("trades", []):
            if clean(trade.get("code")):
                trade_by_key.setdefault(trade_match_key(trade.get("name")), clean(trade.get("code")))
                trade_group_by_code.setdefault(clean(trade.get("code")), clean(trade.get("discipline")) or "ARCHITECTURE")
    trade_by_key.update(TRADE_CODE_OVERRIDES)

    process_by_key = {}
    for part in existing_parts:
        for code, process in part.get("canonicalAtoms", {}).get("processGroups", {}).items():
            trade_code = clean(process.get("tradeCode"))
            process_name = clean(process.get("name"))
            sub_trade_code = clean(process.get("subTradeCode")) or SUB_TRADE_NONE_CODE
            key = (trade_code, sub_trade_code, process_name)
            process_by_key.setdefault(key, code)
            if " / " in process_name:
                sub_trade_name, pure_process_name = process_name.split(" / ", 1)
                derived_sub_trade_code = sub_trade_code_for(trade_code, sub_trade_name)
                process_by_key.setdefault((trade_code, derived_sub_trade_code, clean(pure_process_name)), code)

    row_label_by_code = {}
    row_by_label = {}
    for part in existing_parts:
        for code, row in part.get("canonicalAtoms", {}).get("checklistRows", {}).items():
            label = clean(row.get("label"))
            row_label_by_code[code] = label
            row_by_label.setdefault(label, code)

    item_by_full_key = {}
    for part in existing_parts:
        for code, item in part.get("canonicalAtoms", {}).get("inspectionItems", {}).items():
            row_labels = tuple(clean(row_label_by_code.get(row_code)) for row_code in item.get("rowRefs", []))
            key = (clean(item.get("processGroupCode")), clean(item.get("name")), row_labels)
            item_by_full_key.setdefault(key, code)

    used = set()
    for part in existing_parts:
        part_atoms = part.get("canonicalAtoms", {})
        used.update(part_atoms.get("trades", {}).keys())
        used.update(part_atoms.get("subTrades", {}).keys())
        used.update(part_atoms.get("processGroups", {}).keys())
        used.update(part_atoms.get("inspectionItems", {}).keys())
        used.update(part_atoms.get("checklistRows", {}).keys())

    return {
        "trade_by_key": trade_by_key,
        "trade_group_by_code": trade_group_by_code,
        "process_by_key": process_by_key,
        "item_by_full_key": item_by_full_key,
        "row_by_label": row_by_label,
        "used_codes": used,
    }


def work_category_code(name):
    normalized = clean(name)
    if "외" in normalized:
        return "ADDITIONAL", "기본 외 업무"
    return "BASIC", "기본 업무"


def process_display_name(sub_trade_name, process_name):
    return clean(process_name)


def normalize_sub_trade_name(value):
    normalized = clean(value)
    return SUB_TRADE_NONE_NAME if not normalized or normalized == "-" else normalized


def sub_trade_code_for(trade_code, sub_trade_name):
    normalized = normalize_sub_trade_name(sub_trade_name)
    if normalized == SUB_TRADE_NONE_NAME:
        return SUB_TRADE_NONE_CODE
    return f"{trade_code}_ST_{digest_code(trade_code, normalized)}"


def ensure_trade(trade_atoms, trades_projection, seed, trade_name, group_name, source_pages):
    trade_name = clean(trade_name)
    trade_code = seed["trade_by_key"].get(trade_match_key(trade_name))
    if not trade_code:
        trade_code = generated_code("TRADE", "ATOM", trade_name, used_codes=seed["used_codes"])
        seed["trade_by_key"][trade_match_key(trade_name)] = trade_code
    group_code = GROUP_NAME_TO_CODE.get(clean(group_name)) or seed["trade_group_by_code"].get(trade_code) or "ARCHITECTURE"
    trade_atoms.setdefault(trade_code, OrderedDict([
        ("code", trade_code),
        ("name", trade_name),
        ("discipline", group_code),
    ]))
    trade_atoms[trade_code]["discipline"] = group_code
    if trade_code not in trades_projection:
        trades_projection[trade_code] = OrderedDict([
            ("code", trade_code),
            ("name", trade_name),
            ("discipline", group_code),
            ("sourcePages", []),
            ("processes", []),
            ("processGroups", []),
        ])
    trades_projection[trade_code]["sourcePages"] = union_list(trades_projection[trade_code]["sourcePages"], source_pages)
    return trade_code


def ensure_sub_trade(sub_trade_atoms, trade_code, sub_trade_name):
    normalized_name = normalize_sub_trade_name(sub_trade_name)
    code = sub_trade_code_for(trade_code, normalized_name)
    sub_trade_atoms.setdefault(code, OrderedDict([
        ("code", code),
        ("tradeCode", "" if code == SUB_TRADE_NONE_CODE else trade_code),
        ("name", normalized_name),
    ]))
    return code, normalized_name


def union_list(left, right):
    result = list(left or [])
    for value in right or []:
        if value not in result:
            result.append(value)
    return result


def ensure_process(process_atoms, seed, trade_code, sub_trade_code, sub_trade_name, process_name):
    key = (trade_code, sub_trade_code, process_name)
    code = seed["process_by_key"].get(key)
    if not code:
        code = generated_code(trade_code, "PG", sub_trade_code, process_name, used_codes=seed["used_codes"])
        seed["process_by_key"][key] = code
    process_atoms.setdefault(code, OrderedDict([
        ("code", code),
        ("tradeCode", trade_code),
        ("subTradeCode", sub_trade_code),
        ("subTradeName", sub_trade_name),
        ("name", process_name),
    ]))
    process_atoms[code]["subTradeCode"] = sub_trade_code
    process_atoms[code]["subTradeName"] = sub_trade_name
    return code


def ensure_rows(row_atoms, seed, item_code, labels):
    refs = []
    for label in labels:
        label = clean(label)
        if not label:
            continue
        code = seed["row_by_label"].get(label)
        if not code:
            code = generated_code(item_code, "ROW", label, used_codes=seed["used_codes"])
            seed["row_by_label"][label] = code
        row_atoms.setdefault(code, OrderedDict([
            ("code", code),
            ("label", label),
        ]))
        refs.append(code)
    return refs


def ensure_item(item_atoms, row_atoms, seed, trade_code, process_code, item_name, row_labels):
    row_label_tuple = tuple(clean(label) for label in row_labels if clean(label))
    key = (process_code, clean(item_name), row_label_tuple)
    code = seed["item_by_full_key"].get(key)
    if not code:
        code = generated_code(trade_code, "IT", process_code, item_name, *row_label_tuple, used_codes=seed["used_codes"])
        seed["item_by_full_key"][key] = code
    row_refs = ensure_rows(row_atoms, seed, code, row_label_tuple)
    item_atoms.setdefault(code, OrderedDict([
        ("code", code),
        ("processGroupCode", process_code),
        ("name", clean(item_name)),
        ("rowRefs", row_refs),
    ]))
    item_atoms[code]["rowRefs"] = union_list(item_atoms[code].get("rowRefs", []), row_refs)
    return code


def add_process_ref(work_category, process_code, item_refs):
    refs = work_category.setdefault("_processRefs", OrderedDict())
    entry = refs.setdefault(process_code, OrderedDict([
        ("code", process_code),
        ("itemRefs", []),
    ]))
    entry["itemRefs"] = union_list(entry["itemRefs"], item_refs)


def new_work_category_ref(category_code, category_name):
    return OrderedDict([
        ("code", category_code),
        ("name", category_name),
        ("_processRefs", OrderedDict()),
    ])


def materialize_work_categories(work_categories):
    categories = []
    for category in work_categories.values():
        process_refs = list(category.pop("_processRefs").values())
        if process_refs:
            category["processGroupRefs"] = process_refs
            categories.append(category)
    return categories


def build_mode(mode, source, seed, atoms, trades_projection):
    mode_catalog = OrderedDict([
        ("tradeRefs", []),
    ])
    trade_refs = OrderedDict()
    for trade_source in source.get(K_TRADES, []):
        trade_name = trade_source.get(K_TRADE)
        group_name = trade_source.get(K_TRADE_GROUP)
        sub_trade_name = trade_source.get(K_SUB_TRADE)
        source_pages = parse_pages(trade_source.get(K_SOURCE_PAGE))
        trade_code = ensure_trade(atoms["trades"], trades_projection, seed, trade_name, group_name, source_pages)
        sub_trade_code, sub_trade_name = ensure_sub_trade(atoms["subTrades"], trade_code, sub_trade_name)
        trade_ref = trade_refs.setdefault(trade_code, OrderedDict([
            ("tradeCode", trade_code),
            ("sourcePages", []),
            ("subTradeRefs", OrderedDict()),
        ]))
        trade_ref["sourcePages"] = union_list(trade_ref["sourcePages"], source_pages)
        sub_trade_ref = trade_ref["subTradeRefs"].setdefault(sub_trade_code, OrderedDict([
            ("subTradeCode", sub_trade_code),
            ("subTradeName", sub_trade_name),
            ("sourcePages", []),
            ("workCategories", OrderedDict()),
        ]))
        sub_trade_ref["sourcePages"] = union_list(sub_trade_ref["sourcePages"], source_pages)

        for category_source in trade_source.get(K_WORK_CATEGORIES, []):
            category_code, category_name = work_category_code(category_source.get(K_WORK_CATEGORY))
            sub_trade_category = sub_trade_ref["workCategories"].setdefault(
                category_code,
                new_work_category_ref(category_code, category_name)
            )
            for process_source in category_source.get(K_PROCESS_GROUPS, []):
                process_name = process_display_name(sub_trade_name, process_source.get(K_PROCESS))
                if not process_name:
                    continue
                process_code = ensure_process(atoms["processGroups"], seed, trade_code, sub_trade_code, sub_trade_name, process_name)
                if process_name not in trades_projection[trade_code]["processes"]:
                    trades_projection[trade_code]["processes"].append(process_name)

                item_refs = []
                for item_source in process_source.get(K_ITEMS, []):
                    item_name = clean(item_source.get(K_ITEM))
                    if not item_name:
                        continue
                    row_labels = [clean(label) for label in item_source.get(K_ROWS, []) if clean(label)]
                    item_code = ensure_item(atoms["inspectionItems"], atoms["checklistRows"], seed, trade_code, process_code, item_name, row_labels)
                    item_refs.append(item_code)
                if item_refs:
                    add_process_ref(sub_trade_category, process_code, item_refs)
                    add_trade_projection_process(trades_projection[trade_code], atoms, process_code, item_refs, source_pages, category_code, category_name)

    for trade_ref in trade_refs.values():
        sub_trade_refs = []
        for sub_trade_ref in trade_ref["subTradeRefs"].values():
            sub_trade_categories = materialize_work_categories(sub_trade_ref.pop("workCategories"))
            if sub_trade_categories:
                sub_trade_ref["sourcePages"] = sorted(sub_trade_ref["sourcePages"])
                sub_trade_ref["workCategories"] = sub_trade_categories
                sub_trade_refs.append(sub_trade_ref)
        trade_ref["subTradeRefs"] = sub_trade_refs
        if sub_trade_refs:
            trade_ref["sourcePages"] = sorted(trade_ref["sourcePages"])
            mode_catalog["tradeRefs"].append(trade_ref)
    return mode_catalog


def add_trade_projection_process(trade_projection, atoms, process_code, item_refs, source_pages, category_code, category_name):
    for process in trade_projection["processGroups"]:
        if process["code"] == process_code and process.get("workCategory") == category_code:
            process["items"] = union_items(process["items"], [projection_item(atoms, code) for code in item_refs])
            process["sourcePages"] = union_list(process["sourcePages"], source_pages)
            return
    trade_projection["processGroups"].append(OrderedDict([
        ("code", process_code),
        ("items", [projection_item(atoms, code) for code in item_refs]),
        ("name", atoms["processGroups"][process_code]["name"]),
        ("sourcePages", list(source_pages)),
        ("subTradeCode", atoms["processGroups"][process_code].get("subTradeCode", SUB_TRADE_NONE_CODE)),
        ("subTradeName", atoms["processGroups"][process_code].get("subTradeName", SUB_TRADE_NONE_NAME)),
        ("tradeCode", atoms["processGroups"][process_code]["tradeCode"]),
        ("workCategory", category_code),
        ("workCategoryName", category_name),
    ]))


def projection_item(atoms, item_code):
    item = OrderedDict(atoms["inspectionItems"][item_code])
    item["checklistRows"] = [
        atoms["checklistRows"][row_code]
        for row_code in item.get("rowRefs", [])
        if row_code in atoms["checklistRows"]
    ]
    return item


def union_items(left, right):
    result = list(left or [])
    seen = {item["code"] for item in result}
    for item in right:
        if item["code"] not in seen:
            result.append(item)
            seen.add(item["code"])
    return result


def ordered_mode_catalog(mode, generated):
    meta = MODE_META[mode]
    result = OrderedDict()
    result.update(meta)
    result["status"] = "READY"
    result["catalogDataSource"] = "MODE_SPECIFIC_TRANSCRIPTION"
    result["dataPolicy"] = "CANONICAL_ATOMS_WITH_MODE_REFS"
    result["canWriteReports"] = True
    result["tradeRefs"] = generated["tradeRefs"]
    return result


def main():
    existing_part = load_json(PART_PATH)
    manifest = load_json(MANIFEST_PATH)
    sources = find_transcriptions()
    seed = build_seed_indexes(existing_part)

    atoms = OrderedDict([
        ("trades", OrderedDict()),
        ("subTrades", OrderedDict([
            (SUB_TRADE_NONE_CODE, OrderedDict([
                ("code", SUB_TRADE_NONE_CODE),
                ("tradeCode", ""),
                ("name", SUB_TRADE_NONE_NAME),
            ]))
        ])),
        ("processGroups", OrderedDict()),
        ("inspectionItems", OrderedDict()),
        ("checklistRows", OrderedDict()),
    ])
    trades_projection = OrderedDict()
    modes = OrderedDict()
    source_paths = {}
    for mode in MODE_ORDER:
        path, source = sources[mode]
        source_paths[mode] = str(path.relative_to(ROOT)).replace("\\", "/")
        modes[mode] = ordered_mode_catalog(mode, build_mode(mode, source, seed, atoms, trades_projection))

    part = OrderedDict([
        ("partType", "TRADE_CHECKLIST"),
        ("source", OrderedDict([
            ("documentTitle", "[별표 1] 단계별 감리 체크리스트 대장(건축공사 감리세부기준)"),
            ("revisionLabel", "개정 2020. 12. 24."),
            ("localReferencePath", "docs/reference-forms/korean/[별표 1] 단계별 감리 체크리스트 대장(건축공사 감리세부기준).pdf"),
            ("note", "This catalog is a code-managed ArchDox domain catalog. The PDF is a reference source, not runtime data."),
            ("transcriptionJsonPaths", source_paths),
        ])),
        ("supervisionWorkModeCatalogs", modes),
        ("canonicalAtoms", atoms),
        ("trades", list(trades_projection.values())),
    ])
    write_json(PART_PATH, part)

    coverage = manifest.setdefault("coverage", OrderedDict())
    coverage["referencePages"] = "22-178"
    coverage["tradeCount"] = len(atoms["trades"])
    coverage["detailGroupCount"] = len(atoms["processGroups"])
    coverage["level"] = "FULL_TRADE_CHECKLIST_BY_WORK_MODE"
    coverage["note"] = "NON_RESIDENT, RESIDENT, and RESPONSIBLE_RESIDENT tradeRefs are generated from checked JSON transcriptions. Shared canonical atoms are reused only when process, item, and row structure match."
    coverage["subTradeCount"] = len(atoms["subTrades"])
    coverage["inspectionItemCount"] = len(atoms["inspectionItems"])
    coverage["checklistRowCount"] = len(atoms["checklistRows"])

    mode_catalogs = manifest.setdefault("supervisionWorkModeCatalogs", OrderedDict())
    for mode in MODE_ORDER:
        previous = mode_catalogs.setdefault(mode, OrderedDict())
        previous.update(MODE_META[mode])
        previous["status"] = "READY"
        previous["catalogDataSource"] = "MODE_SPECIFIC_TRANSCRIPTION"
        previous["dataPolicy"] = "CANONICAL_ATOMS_WITH_MODE_REFS"
        previous["canWriteReports"] = True
    write_json(MANIFEST_PATH, manifest)

    print("Generated trade checklist catalog")
    for mode in MODE_ORDER:
        print(f"{mode}: tradeRefs={len(modes[mode]['tradeRefs'])}")
    print(f"canonical trades={len(atoms['trades'])}, processGroups={len(atoms['processGroups'])}, inspectionItems={len(atoms['inspectionItems'])}, checklistRows={len(atoms['checklistRows'])}")


if __name__ == "__main__":
    main()
