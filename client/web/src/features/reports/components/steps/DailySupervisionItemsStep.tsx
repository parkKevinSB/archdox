import { Camera, Plus, Trash2, X } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { useEffect, useMemo, useState, type ChangeEvent } from "react";
import { usePhotoWorkspace } from "../../../photos/hooks/usePhotoWorkspace";
import { PhotoUploadTaskStrip } from "../../../photos/components/PhotoPipelinePanel";
import { getSupervisionDomainCatalog } from "../../api";
import type { SupervisionCatalogItem, SupervisionCatalogTrade } from "../../types";
import type { ReportStepComponentProps } from "./ReportFormStep";

type DailySupervisionItem = {
  content: string;
  id: string;
  item: string;
  itemCode?: string;
  photoIds: number[];
};

type DailySupervisionGroup = {
  floor: string;
  id: string;
  items: DailySupervisionItem[];
  process: string;
  processCode?: string;
  trade: string;
  tradeCode?: string;
};

type DailyItemsPayload = {
  groups: DailySupervisionGroup[];
};

const DAILY_ITEMS_FIELD = "dailyItems";
const CATALOG_CODE = "CONSTRUCTION_SUPERVISION_CHECKLIST_2020_12_24";

export function DailySupervisionItemsStep({
  canWriteReports,
  definition,
  form,
  officeId,
  register,
  report,
  revision,
  token
}: ReportStepComponentProps) {
  const [groups, setGroups] = useState<DailySupervisionGroup[]>([]);
  const workspace = usePhotoWorkspace({
    officeId,
    report,
    token,
    uploadContext: { stepCode: definition.code }
  });
  const catalogQuery = useQuery({
    queryKey: ["supervisionDomainCatalog", officeId, CATALOG_CODE],
    queryFn: () => getSupervisionDomainCatalog(token, officeId, CATALOG_CODE)
  });
  const catalog = catalogQuery.data ?? null;
  const trades = catalog?.trades ?? [];
  const floorOptions = catalog?.floorOptions ?? [];
  const processOptions = catalog?.processOptions ?? [];

  const totalItems = useMemo(() => groups.reduce((sum, group) => sum + group.items.length, 0), [groups]);
  const totalPhotos = useMemo(
    () => groups.reduce((sum, group) => sum + group.items.reduce((itemSum, item) => itemSum + item.photoIds.length, 0), 0),
    [groups]
  );

  useEffect(() => {
    register(DAILY_ITEMS_FIELD);
  }, [register]);

  useEffect(() => {
    const payload = parsePayload(form.getValues(DAILY_ITEMS_FIELD));
    setGroups(payload.groups);
    form.setValue(DAILY_ITEMS_FIELD, JSON.stringify(payload), { shouldDirty: false });
  }, [definition.code, form, report.id, revision]);

  function commit(nextGroups: DailySupervisionGroup[]) {
    setGroups(nextGroups);
    form.setValue(DAILY_ITEMS_FIELD, JSON.stringify({ groups: nextGroups }), {
      shouldDirty: true,
      shouldTouch: true,
      shouldValidate: false
    });
  }

  function addGroup() {
    const defaultTrade = trades[0] ?? null;
    commit([
      ...groups,
      {
        floor: "",
        id: newId("group"),
        items: [emptyItem()],
        process: "",
        trade: defaultTrade?.name ?? "",
        tradeCode: defaultTrade?.code
      }
    ]);
  }

  function updateGroup(groupId: string, patch: Partial<DailySupervisionGroup>) {
    commit(groups.map((group) => (group.id === groupId ? { ...group, ...patch } : group)));
  }

  function removeGroup(groupId: string) {
    commit(groups.filter((group) => group.id !== groupId));
  }

  function addItem(groupId: string, catalogItem?: SupervisionCatalogItem) {
    commit(groups.map((group) => {
      if (group.id !== groupId) {
        return group;
      }
      return {
        ...group,
        items: [
          ...group.items,
          {
            content: "",
            id: newId("item"),
            item: catalogItem?.name ?? "",
            itemCode: catalogItem?.code,
            photoIds: []
          }
        ]
      };
    }));
  }

  function selectTrade(groupId: string, tradeCode: string) {
    const selected = trades.find((trade) => trade.code === tradeCode);
    updateGroup(groupId, {
      process: "",
      processCode: undefined,
      trade: selected?.name ?? "",
      tradeCode: selected?.code
    });
  }

  function updateItem(groupId: string, itemId: string, patch: Partial<DailySupervisionItem>) {
    commit(groups.map((group) => {
      if (group.id !== groupId) {
        return group;
      }
      return {
        ...group,
        items: group.items.map((item) => (item.id === itemId ? { ...item, ...patch } : item))
      };
    }));
  }

  function removeItem(groupId: string, itemId: string) {
    commit(groups.map((group) => {
      if (group.id !== groupId) {
        return group;
      }
      return { ...group, items: group.items.filter((item) => item.id !== itemId) };
    }));
  }

  async function attachPhotos(groupId: string, itemId: string, event: ChangeEvent<HTMLInputElement>) {
    const files = Array.from(event.target.files ?? []).filter((file) => file.type.startsWith("image/"));
    event.target.value = "";
    if (!canWriteReports || files.length === 0) {
      return;
    }
    const results = await workspace.uploadFiles(files, { stepCode: definition.code });
    const photoIds = results.map((result) => result.photo.id);
    if (photoIds.length > 0) {
      const current = groups
        .find((group) => group.id === groupId)
        ?.items.find((item) => item.id === itemId)?.photoIds ?? [];
      updateItem(groupId, itemId, { photoIds: uniqueNumbers([...current, ...photoIds]) });
    }
  }

  return (
    <>
      <div className="wizard-form-head">
        <div>
          <h3>{definition.title}</h3>
          <p>{definition.description}</p>
        </div>
        {revision ? <span className="panel-context">rev {revision}</span> : null}
      </div>

      <input type="hidden" {...register(DAILY_ITEMS_FIELD)} />

      <div className="daily-supervision-summary">
        <span>공종 그룹 {groups.length}개</span>
        <span>감리 항목 {totalItems}개</span>
        <span>연결 사진 {totalPhotos}장</span>
        {catalog ? <span>카탈로그 v{catalog.version}</span> : null}
      </div>

      {catalogQuery.isLoading ? (
        <p className="daily-supervision-muted">감리 도메인 카탈로그를 불러오는 중입니다.</p>
      ) : null}
      {catalogQuery.error ? (
        <p className="daily-supervision-muted">감리 도메인 카탈로그를 불러오지 못했습니다. 직접 입력은 가능합니다.</p>
      ) : null}
      {catalog?.source ? (
        <p className="daily-supervision-muted">
          기준: {catalog.source.documentTitle} {catalog.source.revisionLabel ? `· ${catalog.source.revisionLabel}` : ""}
        </p>
      ) : null}

      {groups.length === 0 ? (
        <div className="daily-supervision-empty">
          <strong>아직 입력한 감리 항목이 없습니다.</strong>
          <span>공종을 추가한 뒤 감리항목을 선택하고 감리내용과 사진을 연결하세요.</span>
          <button className="primary-button" disabled={!canWriteReports} onClick={addGroup} type="button">
            <Plus size={17} />
            공종 추가
          </button>
        </div>
      ) : (
        <div className="daily-supervision-groups">
          {groups.map((group, groupIndex) => (
            <article className="daily-supervision-group" key={group.id}>
              <header className="daily-supervision-group-head">
                <div>
                  <span>공종 그룹 {groupIndex + 1}</span>
                  <strong>{group.trade || "공종 미선택"}</strong>
                </div>
                <button
                  aria-label="공종 그룹 삭제"
                  className="icon-button danger"
                  disabled={!canWriteReports}
                  onClick={() => removeGroup(group.id)}
                  type="button"
                >
                  <Trash2 size={17} />
                </button>
              </header>

              <div className="daily-supervision-group-fields">
                <label>
                  층/구역
                  <input
                    disabled={!canWriteReports}
                    list="daily-floor-options"
                    onChange={(event) => updateGroup(group.id, { floor: event.target.value })}
                    placeholder="예: 전층, 기초층, 3층"
                    value={group.floor}
                  />
                </label>
                <label>
                  공종
                  {trades.length > 0 ? (
                    <select
                      disabled={!canWriteReports}
                      onChange={(event) => selectTrade(group.id, event.target.value)}
                      value={group.tradeCode ?? ""}
                    >
                      <option value="">공종 선택</option>
                      {trades.map((entry) => (
                        <option key={entry.code} value={entry.code}>{entry.name}</option>
                      ))}
                    </select>
                  ) : (
                    <input
                      disabled={!canWriteReports}
                      onChange={(event) => updateGroup(group.id, { trade: event.target.value, tradeCode: undefined })}
                      placeholder="예: 철근 콘크리트 공사"
                      value={group.trade}
                    />
                  )}
                </label>
                <label>
                  세부공정
                  <input
                    disabled={!canWriteReports}
                    list={`daily-process-options-${group.id}`}
                    onChange={(event) => updateGroup(group.id, { process: event.target.value, processCode: undefined })}
                    placeholder="예: 기초, 지하층 바닥"
                    value={group.process}
                  />
                </label>
              </div>

              <DailyItemPicker
                canWriteReports={canWriteReports}
                group={group}
                trades={trades}
                onAdd={(itemName) => addItem(group.id, itemName)}
              />

              <div className="daily-supervision-items">
                {group.items.length === 0 ? (
                  <p className="daily-supervision-muted">감리 항목을 추가하세요.</p>
                ) : group.items.map((item, itemIndex) => (
                  <div className="daily-supervision-item" key={item.id}>
                    <div className="daily-supervision-item-head">
                      <span>항목 {itemIndex + 1}</span>
                      <button
                        aria-label="감리 항목 삭제"
                        className="icon-button"
                        disabled={!canWriteReports}
                        onClick={() => removeItem(group.id, item.id)}
                        type="button"
                      >
                        <X size={16} />
                      </button>
                    </div>
                    <label>
                      감리 항목
                      <input
                        disabled={!canWriteReports}
                        list={`daily-item-options-${group.id}`}
                        onChange={(event) => {
                          const catalogItem = itemByName(group, trades, event.target.value);
                          updateItem(group.id, item.id, {
                            item: event.target.value,
                            itemCode: catalogItem?.code
                          });
                        }}
                        placeholder="예: 철근 조립, 배근"
                        value={item.item}
                      />
                    </label>
                    <label>
                      감리내용
                      <textarea
                        disabled={!canWriteReports}
                        onChange={(event) => updateItem(group.id, item.id, { content: event.target.value })}
                        placeholder="확인한 내용, 시험/입회 결과, 근거자료 등을 구체적으로 입력하세요."
                        value={item.content}
                      />
                    </label>
                    <div className="daily-supervision-photo-row">
                      <div className="daily-supervision-photo-chips">
                        {item.photoIds.length === 0 ? (
                          <span className="daily-supervision-muted">연결 사진 없음</span>
                        ) : item.photoIds.map((photoId) => (
                          <button
                            className="daily-photo-chip"
                            disabled={!canWriteReports}
                            key={photoId}
                            onClick={() => updateItem(group.id, item.id, {
                              photoIds: item.photoIds.filter((current) => current !== photoId)
                            })}
                            type="button"
                          >
                            Photo #{photoId}
                            <X size={12} />
                          </button>
                        ))}
                      </div>
                      <label className={!canWriteReports ? "secondary-button disabled" : "secondary-button"}>
                        <Camera size={16} />
                        사진 추가
                        <input
                          accept="image/*"
                          disabled={!canWriteReports}
                          multiple
                          onChange={(event) => void attachPhotos(group.id, item.id, event)}
                          type="file"
                        />
                      </label>
                    </div>
                  </div>
                ))}
              </div>

              <datalist id={`daily-item-options-${group.id}`}>
                {suggestedItems(group, trades).map((item) => (
                  <option key={item.code} value={item.name} />
                ))}
              </datalist>
              <datalist id={`daily-process-options-${group.id}`}>
                {suggestedProcesses(group, trades, processOptions).map((option) => (
                  <option key={option} value={option} />
                ))}
              </datalist>
            </article>
          ))}
        </div>
      )}

      <div className="daily-supervision-add-row">
        <button className="secondary-button" disabled={!canWriteReports} onClick={addGroup} type="button">
          <Plus size={17} />
          공종 그룹 추가
        </button>
      </div>

      <PhotoUploadTaskStrip onCancel={workspace.cancelUploadTask} tasks={workspace.uploadTasks} />

      <datalist id="daily-floor-options">
        {floorOptions.map((option) => <option key={option} value={option} />)}
      </datalist>
    </>
  );
}

function DailyItemPicker({
  canWriteReports,
  group,
  onAdd,
  trades
}: {
  canWriteReports: boolean;
  group: DailySupervisionGroup;
  onAdd: (item?: SupervisionCatalogItem) => void;
  trades: SupervisionCatalogTrade[];
}) {
  const [selected, setSelected] = useState("");
  const items = suggestedItems(group, trades);
  const selectedItem = items.find((item) => item.code === selected);

  useEffect(() => {
    setSelected(items[0]?.code ?? "");
  }, [group.tradeCode, items]);

  return (
    <div className="daily-supervision-item-picker">
      <select disabled={!canWriteReports} onChange={(event) => setSelected(event.target.value)} value={selected}>
        <option value="">감리 항목 선택</option>
        {items.map((item) => (
          <option key={item.code} value={item.code}>{item.name}</option>
        ))}
      </select>
      <button
        className="secondary-button"
        disabled={!canWriteReports || !selectedItem}
        onClick={() => onAdd(selectedItem)}
        type="button"
      >
        <Plus size={16} />
        감리 항목 추가
      </button>
      <button className="secondary-button" disabled={!canWriteReports} onClick={() => onAdd(undefined)} type="button">
        직접 입력
      </button>
    </div>
  );
}

function suggestedItems(group: DailySupervisionGroup, trades: SupervisionCatalogTrade[]) {
  return selectedTrade(group, trades)?.items ?? [];
}

function itemByName(group: DailySupervisionGroup, trades: SupervisionCatalogTrade[], name: string) {
  return suggestedItems(group, trades).find((item) => item.name === name);
}

function suggestedProcesses(
  group: DailySupervisionGroup,
  trades: SupervisionCatalogTrade[],
  catalogProcessOptions: string[]
) {
  return uniqueStrings([...(selectedTrade(group, trades)?.processes ?? []), ...catalogProcessOptions])
    .filter((option) => option && option !== "-");
}

function selectedTrade(group: DailySupervisionGroup, trades: SupervisionCatalogTrade[]) {
  return trades.find((trade) => trade.code === group.tradeCode)
    ?? trades.find((trade) => trade.name === group.trade)
    ?? null;
}

function emptyItem(): DailySupervisionItem {
  return {
    content: "",
    id: newId("item"),
    item: "",
    photoIds: []
  };
}

function parsePayload(value: unknown): DailyItemsPayload {
  if (typeof value === "string" && value.trim()) {
    try {
      return normalizePayload(JSON.parse(value));
    } catch {
      return { groups: [] };
    }
  }
  return normalizePayload(value);
}

function normalizePayload(value: unknown): DailyItemsPayload {
  const raw = value && typeof value === "object" ? value as { groups?: unknown } : {};
  if (!Array.isArray(raw.groups)) {
    return { groups: [] };
  }
  return {
    groups: raw.groups.map((group, index) => normalizeGroup(group, index)).filter(Boolean) as DailySupervisionGroup[]
  };
}

function normalizeGroup(value: unknown, index: number): DailySupervisionGroup | null {
  if (!value || typeof value !== "object") {
    return null;
  }
  const raw = value as Partial<DailySupervisionGroup>;
  return {
    floor: text(raw.floor),
    id: text(raw.id) || newId(`group-${index}`),
    items: Array.isArray(raw.items) ? raw.items.map((item, itemIndex) => normalizeItem(item, itemIndex)).filter(Boolean) as DailySupervisionItem[] : [],
    process: text(raw.process),
    processCode: optionalText(raw.processCode),
    trade: text(raw.trade),
    tradeCode: optionalText(raw.tradeCode)
  };
}

function normalizeItem(value: unknown, index: number): DailySupervisionItem | null {
  if (!value || typeof value !== "object") {
    return null;
  }
  const raw = value as Partial<DailySupervisionItem>;
  return {
    content: text(raw.content),
    id: text(raw.id) || newId(`item-${index}`),
    item: text(raw.item),
    itemCode: optionalText(raw.itemCode),
    photoIds: uniqueNumbers(Array.isArray(raw.photoIds) ? raw.photoIds : [])
  };
}

function text(value: unknown) {
  return typeof value === "string" ? value : value == null ? "" : String(value);
}

function optionalText(value: unknown) {
  const normalized = text(value).trim();
  return normalized ? normalized : undefined;
}

function uniqueNumbers(values: unknown[]) {
  return [...new Set(values.map((value) => Number(value)).filter((value) => Number.isFinite(value) && value > 0))];
}

function uniqueStrings(values: string[]) {
  return [...new Set(values.map((value) => value.trim()).filter(Boolean))];
}

function newId(prefix: string) {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}
