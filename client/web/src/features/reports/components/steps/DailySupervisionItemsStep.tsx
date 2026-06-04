import { Camera, Plus, Trash2, UploadCloud, X } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { useEffect, useMemo, useState, type ChangeEvent } from "react";
import { usePhotoWorkspace } from "../../../photos/hooks/usePhotoWorkspace";
import { PhotoUploadTaskStrip } from "../../../photos/components/PhotoPipelinePanel";
import { getSupervisionDomainCatalog } from "../../api";
import type { SupervisionCatalogItem, SupervisionCatalogTrade } from "../../types";
import type { ReportStepComponentProps } from "./ReportFormStep";

type DailySupervisionEntry = {
  id: string;
  inspectionItemCode?: string;
  inspectionItemName: string;
  photoIds: number[];
  supervisionContent: string;
};

type DailySupervisionGroup = {
  entries: DailySupervisionEntry[];
  floor: string;
  id: string;
  processCode?: string;
  processName: string;
  tradeCode?: string;
  tradeName: string;
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
  savedStep,
  token
}: ReportStepComponentProps) {
  const [groups, setGroups] = useState<DailySupervisionGroup[]>([]);
  const [deletingPhotoIds, setDeletingPhotoIds] = useState<Set<number>>(() => new Set());
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

  const totalItems = useMemo(() => groups.reduce((sum, group) => sum + group.entries.length, 0), [groups]);
  const totalPhotos = useMemo(
    () => groups.reduce((sum, group) => sum + group.entries.reduce((itemSum, entry) => itemSum + entry.photoIds.length, 0), 0),
    [groups]
  );

  useEffect(() => {
    register(DAILY_ITEMS_FIELD);
  }, [register]);

  useEffect(() => {
    const payload = parsePayload(savedStep?.payload?.[DAILY_ITEMS_FIELD]);
    setGroups(payload.groups);
    form.setValue(DAILY_ITEMS_FIELD, JSON.stringify(payload), { shouldDirty: false });
  }, [definition.code, form, report.id, savedStep?.clientRevision, savedStep?.savedAt]);

  function updateGroups(updater: (current: DailySupervisionGroup[]) => DailySupervisionGroup[]) {
    setGroups((current) => {
      const nextGroups = updater(current);
      form.setValue(DAILY_ITEMS_FIELD, JSON.stringify({ groups: nextGroups }), {
        shouldDirty: true,
        shouldTouch: true,
        shouldValidate: false
      });
      return nextGroups;
    });
  }

  function addGroup() {
    const defaultTrade = trades[0] ?? null;
    updateGroups((current) => [
      ...current,
      {
        entries: [emptyEntry()],
        floor: "",
        id: newId("group"),
        processName: "",
        tradeCode: defaultTrade?.code,
        tradeName: defaultTrade?.name ?? ""
      }
    ]);
  }

  function updateGroup(groupId: string, patch: Partial<DailySupervisionGroup>) {
    updateGroups((current) => current.map((group) => (group.id === groupId ? { ...group, ...patch } : group)));
  }

  function removeGroup(groupId: string) {
    updateGroups((current) => current.filter((group) => group.id !== groupId));
  }

  function addEntry(groupId: string, catalogItem?: SupervisionCatalogItem) {
    updateGroups((current) => current.map((group) => {
      if (group.id !== groupId) {
        return group;
      }
      return {
        ...group,
        entries: [
          ...group.entries,
          {
            id: newId("entry"),
            inspectionItemCode: catalogItem?.code,
            inspectionItemName: catalogItem?.name ?? "",
            photoIds: [],
            supervisionContent: ""
          }
        ]
      };
    }));
  }

  function selectTrade(groupId: string, tradeCode: string) {
    const selected = trades.find((trade) => trade.code === tradeCode);
    updateGroup(groupId, {
      processCode: undefined,
      processName: "",
      tradeCode: selected?.code,
      tradeName: selected?.name ?? ""
    });
  }

  function updateEntry(groupId: string, entryId: string, patch: Partial<DailySupervisionEntry>) {
    updateGroups((current) => current.map((group) => {
      if (group.id !== groupId) {
        return group;
      }
      return {
        ...group,
        entries: group.entries.map((entry) => (entry.id === entryId ? { ...entry, ...patch } : entry))
      };
    }));
  }

  function removeEntry(groupId: string, entryId: string) {
    updateGroups((current) => current.map((group) => {
      if (group.id !== groupId) {
        return group;
      }
      return { ...group, entries: group.entries.filter((entry) => entry.id !== entryId) };
    }));
  }

  function unlinkPhoto(photoId: number) {
    updateGroups((current) => current.map((group) => ({
      ...group,
      entries: group.entries.map((entry) => ({
        ...entry,
        photoIds: entry.photoIds.filter((currentPhotoId) => currentPhotoId !== photoId)
      }))
    })));
  }

  async function deleteLinkedPhoto(photoId: number) {
    if (!canWriteReports || deletingPhotoIds.has(photoId)) {
      return;
    }
    setDeletingPhotoIds((current) => new Set(current).add(photoId));
    try {
      await workspace.deletePhoto(photoId);
      unlinkPhoto(photoId);
    } finally {
      setDeletingPhotoIds((current) => {
        const next = new Set(current);
        next.delete(photoId);
        return next;
      });
    }
  }

  async function attachPhotos(groupId: string, entryId: string, event: ChangeEvent<HTMLInputElement>) {
    const files = Array.from(event.target.files ?? []).filter((file) => file.type.startsWith("image/"));
    event.target.value = "";
    if (!canWriteReports || files.length === 0) {
      return;
    }
    const group = groups.find((current) => current.id === groupId);
    const entry = group?.entries.find((current) => current.id === entryId);
    const captionParts = [
      group?.tradeName || group?.tradeCode,
      group?.processName || group?.processCode,
      entry?.inspectionItemName || entry?.inspectionItemCode
    ].filter(Boolean);
    const results = await workspace.uploadFiles(files, {
      caption: captionParts.join(" - ") || undefined,
      inspectionItemCode: entry?.inspectionItemCode ?? null,
      locationNote: group?.floor ? `${group.floor}` : null,
      processCode: group?.processCode ?? null,
      stepCode: definition.code,
      tradeCode: group?.tradeCode ?? null
    });
    const photoIds = results.map((result) => result.photo.id);
    if (photoIds.length > 0) {
      updateGroups((currentGroups) => currentGroups.map((group) => {
        if (group.id !== groupId) {
          return group;
        }
        return {
          ...group,
          entries: group.entries.map((entry) => {
            if (entry.id !== entryId) {
              return entry;
            }
            return { ...entry, photoIds: uniqueNumbers([...entry.photoIds, ...photoIds]) };
          })
        };
      }));
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
        <span>검사항목 {totalItems}개</span>
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
          <strong>아직 입력한 검사항목이 없습니다.</strong>
          <span>공종을 추가한 뒤 검사항목을 선택하고 감리내용과 사진을 연결하세요.</span>
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
                  <strong>{group.tradeName || "공종 미선택"}</strong>
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
                      onChange={(event) => updateGroup(group.id, { tradeCode: undefined, tradeName: event.target.value })}
                      placeholder="예: 철근 콘크리트 공사"
                      value={group.tradeName}
                    />
                  )}
                </label>
                <label>
                  세부공정
                  <input
                    disabled={!canWriteReports}
                    list={`daily-process-options-${group.id}`}
                    onChange={(event) => {
                      const selectedProcess = processGroupByName(group, trades, event.target.value);
                      updateGroup(group.id, {
                        processCode: selectedProcess?.code,
                        processName: event.target.value
                      });
                    }}
                    placeholder="예: 기초, 지하층 바닥"
                    value={group.processName}
                  />
                </label>
              </div>

              <DailyItemPicker
                canWriteReports={canWriteReports}
                group={group}
                trades={trades}
                onAdd={(itemName) => addEntry(group.id, itemName)}
              />

              <div className="daily-supervision-items">
                {group.entries.length === 0 ? (
                  <p className="daily-supervision-muted">검사항목을 추가하세요.</p>
                ) : group.entries.map((entry, entryIndex) => (
                  <div className="daily-supervision-item" key={entry.id}>
                    <div className="daily-supervision-item-head">
                      <span>항목 {entryIndex + 1}</span>
                      <button
                        aria-label="검사항목 삭제"
                        className="icon-button"
                        disabled={!canWriteReports}
                        onClick={() => removeEntry(group.id, entry.id)}
                        type="button"
                      >
                        <X size={16} />
                      </button>
                    </div>
                    <label>
                      검사항목
                      <input
                        disabled={!canWriteReports}
                        list={`daily-item-options-${group.id}`}
                        onChange={(event) => {
                          const catalogItem = itemByName(group, trades, event.target.value);
                          updateEntry(group.id, entry.id, {
                            inspectionItemCode: catalogItem?.code,
                            inspectionItemName: event.target.value
                          });
                        }}
                        placeholder="예: 철근 개수·지름·피치"
                        value={entry.inspectionItemName}
                      />
                    </label>
                    {entry.inspectionItemCode ? (
                      <span className="daily-supervision-muted">검사항목 코드: {entry.inspectionItemCode}</span>
                    ) : null}
                    <label>
                      감리내용
                      <textarea
                        disabled={!canWriteReports}
                        onChange={(event) => updateEntry(group.id, entry.id, { supervisionContent: event.target.value })}
                        placeholder="검사항목에 대해 확인한 내용, 시험/입회 결과, 근거자료 등을 구체적으로 입력하세요."
                        value={entry.supervisionContent}
                      />
                    </label>
                    <div className="daily-supervision-photo-row">
                      <div className="daily-supervision-photo-chips">
                        {entry.photoIds.length === 0 ? (
                          <span className="daily-supervision-muted">연결 사진 없음</span>
                        ) : entry.photoIds.map((photoId) => (
                          <button
                            className="daily-photo-chip"
                            disabled={!canWriteReports || deletingPhotoIds.has(photoId)}
                            key={photoId}
                            onClick={() => void deleteLinkedPhoto(photoId)}
                            type="button"
                          >
                            Photo #{photoId}
                            <X size={12} />
                          </button>
                        ))}
                      </div>
                      <label className={!canWriteReports ? "secondary-button disabled" : "secondary-button"}>
                        <Camera size={16} />
                        사진 촬영
                        <input
                          accept="image/*"
                          capture="environment"
                          disabled={!canWriteReports}
                          onChange={(event) => void attachPhotos(group.id, entry.id, event)}
                          type="file"
                        />
                      </label>
                      <label className={!canWriteReports ? "secondary-button disabled" : "secondary-button"}>
                        <UploadCloud size={16} />
                        사진 추가
                        <input
                          accept="image/*"
                          disabled={!canWriteReports}
                          multiple
                          onChange={(event) => void attachPhotos(group.id, entry.id, event)}
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
        <option value="">검사항목 선택</option>
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
        검사항목 추가
      </button>
      <button className="secondary-button" disabled={!canWriteReports} onClick={() => onAdd(undefined)} type="button">
        직접 입력
      </button>
    </div>
  );
}

function suggestedItems(group: DailySupervisionGroup, trades: SupervisionCatalogTrade[]) {
  const trade = selectedTrade(group, trades);
  const processGroup = selectedProcessGroup(group, trade);
  return processGroup?.items?.length ? processGroup.items : trade?.items ?? [];
}

function itemByName(group: DailySupervisionGroup, trades: SupervisionCatalogTrade[], name: string) {
  return suggestedItems(group, trades).find((item) => item.name === name);
}

function suggestedProcesses(
  group: DailySupervisionGroup,
  trades: SupervisionCatalogTrade[],
  catalogProcessOptions: string[]
) {
  const trade = selectedTrade(group, trades);
  const processGroupNames = trade?.processGroups?.map((processGroup) => processGroup.name) ?? [];
  return uniqueStrings([...processGroupNames, ...(trade?.processes ?? []), ...catalogProcessOptions])
    .filter((option) => option && option !== "-");
}

function selectedTrade(group: DailySupervisionGroup, trades: SupervisionCatalogTrade[]) {
  return trades.find((trade) => trade.code === group.tradeCode)
    ?? trades.find((trade) => trade.name === group.tradeName)
    ?? null;
}

function processGroupByName(group: DailySupervisionGroup, trades: SupervisionCatalogTrade[], name: string) {
  return selectedProcessGroup({ ...group, processName: name }, selectedTrade(group, trades));
}

function selectedProcessGroup(group: DailySupervisionGroup, trade: SupervisionCatalogTrade | null) {
  if (!trade?.processGroups?.length) {
    return null;
  }
  return trade.processGroups.find((processGroup) => processGroup.code === group.processCode)
    ?? trade.processGroups.find((processGroup) => processGroup.name === group.processName)
    ?? null;
}

function emptyEntry(): DailySupervisionEntry {
  return {
    id: newId("entry"),
    inspectionItemName: "",
    photoIds: [],
    supervisionContent: ""
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
    entries: Array.isArray(raw.entries) ? raw.entries.map((entry, entryIndex) => normalizeEntry(entry, entryIndex)).filter(Boolean) as DailySupervisionEntry[] : [],
    floor: text(raw.floor),
    id: text(raw.id) || newId(`group-${index}`),
    processCode: optionalText(raw.processCode),
    processName: text(raw.processName),
    tradeCode: optionalText(raw.tradeCode),
    tradeName: text(raw.tradeName)
  };
}

function normalizeEntry(value: unknown, index: number): DailySupervisionEntry | null {
  if (!value || typeof value !== "object") {
    return null;
  }
  const raw = value as Partial<DailySupervisionEntry>;
  return {
    id: text(raw.id) || newId(`entry-${index}`),
    inspectionItemCode: optionalText(raw.inspectionItemCode),
    inspectionItemName: text(raw.inspectionItemName),
    photoIds: uniqueNumbers(Array.isArray(raw.photoIds) ? raw.photoIds : []),
    supervisionContent: text(raw.supervisionContent)
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
