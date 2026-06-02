import { Camera, Plus, Trash2, X } from "lucide-react";
import { useEffect, useMemo, useState, type ChangeEvent } from "react";
import { usePhotoWorkspace } from "../../../photos/hooks/usePhotoWorkspace";
import { PhotoUploadTaskStrip } from "../../../photos/components/PhotoPipelinePanel";
import type { ReportStepComponentProps } from "./ReportFormStep";

type DailySupervisionItem = {
  content: string;
  id: string;
  item: string;
  photoIds: number[];
};

type DailySupervisionGroup = {
  floor: string;
  id: string;
  items: DailySupervisionItem[];
  process: string;
  trade: string;
};

type DailyItemsPayload = {
  groups: DailySupervisionGroup[];
};

const DAILY_ITEMS_FIELD = "dailyItems";

const floorOptions = ["전층", "기초층", "지하층", "지하층 바닥", "1층", "2층", "3층", "옥상"];

const tradeCatalog = [
  {
    trade: "가설공사",
    items: ["부지 상황 확인", "줄쳐보기", "벤치마크(BM)", "규준틀"]
  },
  {
    trade: "토공사",
    items: ["터파기", "흙막이", "배수상태", "바닥면 토질상태"]
  },
  {
    trade: "지정 및 기초공사",
    items: ["자갈 쇄석 지정", "밑창콘크리트", "기초저면 확인", "레벨 확인"]
  },
  {
    trade: "거푸집공사",
    items: ["먹매김", "레벨", "거푸집 설치상태", "박리제 도포상태"]
  },
  {
    trade: "철근 콘크리트 공사",
    items: ["철근 조립, 배근", "철근 규격 증명서", "피복 두께", "정착 길이", "이음 위치", "타설"]
  },
  {
    trade: "조적공사",
    items: ["자재 반입", "쌓기 상태", "줄눈 상태", "개구부 보강"]
  },
  {
    trade: "방수공사",
    items: ["바탕면 정리", "방수층 시공", "배수구 주변 처리", "누수 여부 확인"]
  },
  {
    trade: "마감공사",
    items: ["자재 반입", "시공면 상태", "마감 품질", "보양 상태"]
  }
];

const defaultContentByItem: Record<string, string> = {
  "부지 상황 확인": "대지의 고저차 설계도서 확인",
  "줄쳐보기": "대지경계 확인",
  "벤치마크(BM)": "기준점의 확인\nBM위치에 대한 변화 확인",
  "규준틀": "먹매김 확인",
  "터파기": "터파기 깊이 확인\n바닥면의 토질상태 확인",
  "자갈 쇄석 지정": "바닥면의 레벨 확인\n지정공사의 확인",
  "밑창콘크리트": "밑창콘크리트의 배합, 두께 확인",
  "먹매김": "각층 바닥 먹매김 확인",
  "레벨": "타설레벨 확인",
  "철근 조립, 배근": "철근배근의 확인\n- 개수, 철근지름, 피치 확인\n- 정착길이와 굽힘정착 깊이 확인\n- 이음위치와 이음길이 확인",
  "철근 규격 증명서": "KS마크 또는 시험성적증명서에 의한 KS규격제품인지 확인",
  "피복 두께": "철근 피복 두께 확인",
  "정착 길이": "정착 길이 및 굽힘정착 깊이 확인",
  "이음 위치": "이음위치와 이음길이 확인",
  "타설": "날씨 및 바탕면 확인"
};

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
    commit([
      ...groups,
      {
        floor: "",
        id: newId("group"),
        items: [emptyItem()],
        process: "",
        trade: tradeCatalog[0].trade
      }
    ]);
  }

  function updateGroup(groupId: string, patch: Partial<DailySupervisionGroup>) {
    commit(groups.map((group) => (group.id === groupId ? { ...group, ...patch } : group)));
  }

  function removeGroup(groupId: string) {
    commit(groups.filter((group) => group.id !== groupId));
  }

  function addItem(groupId: string, itemName?: string) {
    commit(groups.map((group) => {
      if (group.id !== groupId) {
        return group;
      }
      return {
        ...group,
        items: [
          ...group.items,
          {
            content: defaultContentByItem[itemName ?? ""] ?? "",
            id: newId("item"),
            item: itemName ?? "",
            photoIds: []
          }
        ]
      };
    }));
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
        <span>공정 그룹 {groups.length}개</span>
        <span>감리 항목 {totalItems}개</span>
        <span>연결 사진 {totalPhotos}장</span>
      </div>

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
                  <span>공정 그룹 {groupIndex + 1}</span>
                  <strong>{group.trade || "공종 미선택"}</strong>
                </div>
                <button
                  aria-label="공정 그룹 삭제"
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
                  공종
                  <select
                    disabled={!canWriteReports}
                    onChange={(event) => updateGroup(group.id, { trade: event.target.value })}
                    value={group.trade}
                  >
                    {tradeCatalog.map((entry) => (
                      <option key={entry.trade} value={entry.trade}>{entry.trade}</option>
                    ))}
                  </select>
                </label>
                <label>
                  세부공정
                  <input
                    disabled={!canWriteReports}
                    list="daily-process-options"
                    onChange={(event) => updateGroup(group.id, { process: event.target.value })}
                    placeholder="예: 기초, 지하층 바닥"
                    value={group.process}
                  />
                </label>
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
              </div>

              <DailyItemPicker
                canWriteReports={canWriteReports}
                group={group}
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
                        onChange={(event) => updateItem(group.id, item.id, {
                          content: item.content || defaultContentByItem[event.target.value] || "",
                          item: event.target.value
                        })}
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
                {suggestedItems(group.trade).map((item) => (
                  <option key={item} value={item} />
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
      <datalist id="daily-process-options">
        {["기초", "지하층 바닥", "기초, 지하층 바닥", "전층", "슬라브", "벽체", "옥상"].map((option) => (
          <option key={option} value={option} />
        ))}
      </datalist>
    </>
  );
}

function DailyItemPicker({
  canWriteReports,
  group,
  onAdd
}: {
  canWriteReports: boolean;
  group: DailySupervisionGroup;
  onAdd: (itemName?: string) => void;
}) {
  const [selected, setSelected] = useState("");
  const items = suggestedItems(group.trade);

  useEffect(() => {
    setSelected(items[0] ?? "");
  }, [group.trade, items]);

  return (
    <div className="daily-supervision-item-picker">
      <select disabled={!canWriteReports} onChange={(event) => setSelected(event.target.value)} value={selected}>
        {items.map((item) => (
          <option key={item} value={item}>{item}</option>
        ))}
      </select>
      <button className="secondary-button" disabled={!canWriteReports} onClick={() => onAdd(selected)} type="button">
        <Plus size={16} />
        감리 항목 추가
      </button>
      <button className="secondary-button" disabled={!canWriteReports} onClick={() => onAdd("")} type="button">
        직접 입력
      </button>
    </div>
  );
}

function suggestedItems(trade: string) {
  return tradeCatalog.find((entry) => entry.trade === trade)?.items ?? tradeCatalog[0].items;
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
    trade: text(raw.trade) || tradeCatalog[0].trade
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
    photoIds: uniqueNumbers(Array.isArray(raw.photoIds) ? raw.photoIds : [])
  };
}

function text(value: unknown) {
  return typeof value === "string" ? value : value == null ? "" : String(value);
}

function uniqueNumbers(values: unknown[]) {
  return [...new Set(values.map((value) => Number(value)).filter((value) => Number.isFinite(value) && value > 0))];
}

function newId(prefix: string) {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}
