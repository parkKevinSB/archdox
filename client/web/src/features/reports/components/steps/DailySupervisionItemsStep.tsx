import { Camera, FileImage, Loader2, Plus, Trash2, UploadCloud, X } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { useEffect, useMemo, useState, type ChangeEvent } from "react";
import { usePhotoAssetPreview } from "../../../photos/hooks/usePhotoAssetPreview";
import { usePhotoWorkspace } from "../../../photos/hooks/usePhotoWorkspace";
import { PhotoUploadTaskStrip } from "../../../photos/components/PhotoPipelinePanel";
import type { PhotoAssetResponse, PhotoAssetType, PhotoResponse } from "../../../photos/types";
import { getSupervisionDomainCatalog } from "../../api";
import type { SupervisionCatalogChecklistRow, SupervisionCatalogItem, SupervisionCatalogTrade } from "../../types";
import type { ReportStepComponentProps } from "./ReportFormStep";

type DailyChecklistResult = "" | "COMPLIANT" | "NON_COMPLIANT";

type DailyChecklistRow = {
  actionNote: string;
  basis?: string;
  code?: string;
  id: string;
  label: string;
  photoIds: number[];
  referenceNote: string;
  result: DailyChecklistResult;
};

type DailySupervisionEntry = {
  checklistRows: DailyChecklistRow[];
  id: string;
  inspectionItemCode?: string;
  inspectionItemName: string;
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
  workCategory?: string;
  workCategoryName?: string;
};

type DailyItemsPayload = {
  groups: DailySupervisionGroup[];
};

type ChecklistNoteDialogState = {
  entryId: string;
  field: "referenceNote" | "actionNote";
  groupId: string;
  rowId: string;
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
  const [noteDialog, setNoteDialog] = useState<ChecklistNoteDialogState | null>(null);
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
  const totalChecklistRows = useMemo(
    () => groups.reduce((sum, group) => sum + group.entries.reduce((itemSum, entry) => itemSum + entry.checklistRows.length, 0), 0),
    [groups]
  );
  const checkedChecklistRows = useMemo(
    () => groups.reduce((sum, group) => sum + group.entries.reduce(
      (itemSum, entry) => itemSum + entry.checklistRows.filter((row) => row.result).length,
      0
    ), 0),
    [groups]
  );
  const totalPhotos = useMemo(
    () => groups.reduce((sum, group) => sum + group.entries.reduce((itemSum, entry) => itemSum + rowPhotoIds(entry).length, 0), 0),
    [groups]
  );
  const photosById = useMemo(
    () => new Map(workspace.allPhotos.map((photo) => [photo.id, photo])),
    [workspace.allPhotos]
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
      const nextGroups = updater(current).map(syncGroupGeneratedContent);
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
    const defaultCategory = defaultTrade ? workCategoryOptions(defaultTrade)[0] : null;
    updateGroups((current) => [
      ...current,
      {
        entries: [emptyEntry()],
        floor: "",
        id: newId("group"),
        processName: "",
        tradeCode: defaultTrade?.code,
        tradeName: defaultTrade?.name ?? "",
        workCategory: defaultCategory?.code,
        workCategoryName: defaultCategory?.name
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
            entryFromCatalogItem(catalogItem)
          ]
        };
      }));
  }

  function selectTrade(groupId: string, tradeCode: string) {
    const selected = trades.find((trade) => trade.code === tradeCode);
    const defaultCategory = selected ? workCategoryOptions(selected)[0] : null;
    updateGroup(groupId, {
      processCode: undefined,
      processName: "",
      tradeCode: selected?.code,
      tradeName: selected?.name ?? "",
      workCategory: defaultCategory?.code,
      workCategoryName: defaultCategory?.name
    });
  }

  function selectWorkCategory(groupId: string, categoryCode: string) {
    const group = groups.find((current) => current.id === groupId);
    const category = workCategoryOptions(selectedTrade(group, trades)).find((option) => option.code === categoryCode);
    updateGroup(groupId, {
      processCode: undefined,
      processName: "",
      workCategory: category?.code,
      workCategoryName: category?.name
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

  function updateChecklistRow(
    groupId: string,
    entryId: string,
    rowId: string,
    patch: Partial<DailyChecklistRow>
  ) {
    updateGroups((current) => current.map((group) => {
      if (group.id !== groupId) {
        return group;
      }
      return {
        ...group,
        entries: group.entries.map((entry) => {
          if (entry.id !== entryId) {
            return entry;
          }
          return {
            ...entry,
            checklistRows: entry.checklistRows.map((row) => (row.id === rowId ? { ...row, ...patch } : row))
          };
        })
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
        checklistRows: entry.checklistRows.map((row) => ({
          ...row,
          photoIds: row.photoIds.filter((currentPhotoId) => currentPhotoId !== photoId)
        }))
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

  async function attachRowPhotos(groupId: string, entryId: string, rowId: string, event: ChangeEvent<HTMLInputElement>) {
    const files = Array.from(event.target.files ?? []).filter((file) => file.type.startsWith("image/"));
    event.target.value = "";
    if (!canWriteReports || files.length === 0) {
      return;
    }
    const group = groups.find((current) => current.id === groupId);
    const entry = group?.entries.find((current) => current.id === entryId);
    const row = entry?.checklistRows.find((current) => current.id === rowId);
    const captionParts = [
      group?.tradeName || group?.tradeCode,
      group?.processName || group?.processCode,
      entry?.inspectionItemName || entry?.inspectionItemCode,
      row?.label || row?.code
    ].filter(Boolean);
    const results = await workspace.uploadFiles(files, {
      caption: captionParts.join(" - ") || undefined,
      inspectionItemCode: row?.code ?? entry?.inspectionItemCode ?? null,
      locationNote: group?.floor ? `${group.floor}` : null,
      processCode: group?.processCode ?? null,
      stepCode: definition.code,
      tradeCode: group?.tradeCode ?? null
    });
    const photoIds = results.map((result) => result.photo.id);
    if (photoIds.length > 0) {
      updateChecklistRow(groupId, entryId, rowId, {
        photoIds: uniqueNumbers([...(row?.photoIds ?? []), ...photoIds])
      });
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
        <span>세부 감리항목 {checkedChecklistRows}/{totalChecklistRows}개 확인</span>
        <span>연결 사진 {totalPhotos}장</span>
        {catalog ? <span>카탈로그 v{catalog.version}</span> : null}
      </div>

      {catalogQuery.isLoading ? (
        <p className="daily-supervision-muted">감리 도메인 카탈로그를 불러오는 중입니다.</p>
      ) : null}
      {catalogQuery.error ? (
        <p className="daily-supervision-muted">감리 도메인 카탈로그를 불러오지 못했습니다. 법령 근거 연결을 위해 카탈로그가 필요합니다.</p>
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
                  구역
                  <input
                    disabled={!canWriteReports}
                    list="daily-floor-options"
                    onChange={(event) => updateGroup(group.id, { floor: event.target.value })}
                    placeholder="예: 3층, 기초, 전층"
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
                  업무구분
                  {workCategoryOptions(selectedTrade(group, trades)).length > 0 ? (
                    <select
                      disabled={!canWriteReports || !group.tradeCode}
                      onChange={(event) => selectWorkCategory(group.id, event.target.value)}
                      value={group.workCategory ?? ""}
                    >
                      <option value="">업무구분 선택</option>
                      {workCategoryOptions(selectedTrade(group, trades)).map((option) => (
                        <option key={option.code} value={option.code}>{option.name}</option>
                      ))}
                    </select>
                  ) : (
                    <input disabled placeholder="공종 선택 필요" value="" />
                  )}
                </label>
                <label>
                  세부공정
                  {selectedProcessGroups(group, selectedTrade(group, trades)).length ? (
                    <select
                      disabled={!canWriteReports || !group.tradeCode || !group.workCategory}
                      onChange={(event) => {
                        const selectedProcess = selectedProcessGroups(group, selectedTrade(group, trades)).find((processGroup) => processGroup.code === event.target.value);
                        updateGroup(group.id, {
                          processCode: selectedProcess?.code,
                          processName: selectedProcess?.name ?? "",
                          workCategory: selectedProcess?.workCategory ?? group.workCategory,
                          workCategoryName: selectedProcess?.workCategoryName ?? group.workCategoryName
                        });
                      }}
                      value={group.processCode ?? ""}
                    >
                      <option value="">세부공정 선택</option>
                      {selectedProcessGroups(group, selectedTrade(group, trades)).map((processGroup) => (
                        <option key={processGroup.code} value={processGroup.code}>
                          {processGroup.name}
                        </option>
                      ))}
                    </select>
                  ) : (
                    <input
                      disabled={!canWriteReports}
                      list={`daily-process-options-${group.id}`}
                      onChange={(event) => {
                        const selectedProcess = processGroupByName(group, trades, event.target.value);
                        updateGroup(group.id, {
                          processCode: selectedProcess?.code,
                          processName: event.target.value,
                          workCategory: selectedProcess?.workCategory ?? group.workCategory,
                          workCategoryName: selectedProcess?.workCategoryName ?? group.workCategoryName
                        });
                      }}
                      placeholder="예: 기초, 지하층 바닥"
                      value={group.processName}
                    />
                  )}
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
                ) : group.entries.map((entry, entryIndex) => {
                  const itemOptions = suggestedItems(group, trades);
                  return (
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
                      {itemOptions.length > 0 ? (
                        <select
                          disabled={!canWriteReports}
                          onChange={(event) => {
                            const catalogItem = itemOptions.find((item) => item.code === event.target.value);
                            updateEntry(group.id, entry.id, {
                              checklistRows: checklistRowsFromCatalogItem(catalogItem),
                              inspectionItemCode: catalogItem?.code,
                              inspectionItemName: catalogItem?.name ?? "",
                              supervisionContent: ""
                            });
                          }}
                          value={entry.inspectionItemCode ?? ""}
                        >
                          <option value="">검사항목 선택</option>
                          {itemOptions.map((item) => (
                            <option key={item.code} value={item.code}>{item.name}</option>
                          ))}
                        </select>
                      ) : (
                        <input
                          disabled
                          placeholder="공종과 세부공정을 먼저 선택하세요."
                          value={entry.inspectionItemName}
                        />
                      )}
                    </label>
                    {entry.inspectionItemCode ? (
                      <span className="daily-supervision-muted">검사항목 코드: {entry.inspectionItemCode}</span>
                    ) : (
                      <span className="daily-supervision-warning">카탈로그 검사항목을 선택해야 법령 근거가 연결됩니다.</span>
                    )}
                    {entry.checklistRows.length > 0 ? (
                      <DailyChecklistRowsEditor
                        canWriteReports={canWriteReports}
                        deletingPhotoIds={deletingPhotoIds}
                        entry={entry}
                        officeId={officeId}
                        onAttachPhotos={(rowId, event) => attachRowPhotos(group.id, entry.id, rowId, event)}
                        onDeletePhoto={deleteLinkedPhoto}
                        onOpenNote={(rowId, field) => setNoteDialog({ entryId: entry.id, field, groupId: group.id, rowId })}
                        onUpdateRow={(rowId, patch) => updateChecklistRow(group.id, entry.id, rowId, patch)}
                        photosById={photosById}
                        token={token}
                      />
                    ) : entry.inspectionItemCode ? (
                      <p className="daily-supervision-muted">
                        이 검사항목은 아직 세부 감리항목 전사가 필요합니다. 새 감리일지는 세부 감리항목 단위의 적합/부적합, 기준·참고사항, 조치사항, 사진만 원천 데이터로 저장합니다.
                      </p>
                    ) : null}
                  </div>
                );})}
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

      {noteDialog ? (
        <ChecklistNoteDialog
          canWriteReports={canWriteReports}
          dialog={noteDialog}
          groups={groups}
          onClose={() => setNoteDialog(null)}
          onUpdateRow={updateChecklistRow}
        />
      ) : null}

      <datalist id="daily-floor-options">
        {floorOptions.map((option) => <option key={option} value={option} />)}
      </datalist>
    </>
  );
}

function DailyChecklistRowsEditor({
  canWriteReports,
  deletingPhotoIds,
  entry,
  officeId,
  onAttachPhotos,
  onDeletePhoto,
  onOpenNote,
  onUpdateRow,
  photosById,
  token
}: {
  canWriteReports: boolean;
  deletingPhotoIds: Set<number>;
  entry: DailySupervisionEntry;
  officeId: number;
  onAttachPhotos: (rowId: string, event: ChangeEvent<HTMLInputElement>) => Promise<void>;
  onDeletePhoto: (photoId: number) => Promise<void>;
  onOpenNote: (rowId: string, field: "referenceNote" | "actionNote") => void;
  onUpdateRow: (rowId: string, patch: Partial<DailyChecklistRow>) => void;
  photosById: Map<number, PhotoResponse>;
  token: string;
}) {
  const generatedContent = buildSupervisionContent(entry);
  return (
    <div className="daily-checklist-rows">
      <div className="daily-checklist-rows-head">
        <strong>세부 감리항목</strong>
        <span>각 항목별로 적합/부적합과 기준·참고사항, 조치사항을 남깁니다.</span>
      </div>
      {entry.checklistRows.map((row, index) => (
        <div className="daily-checklist-row" key={row.id}>
          <div className="daily-checklist-row-main">
            <span className="daily-checklist-row-index">{index + 1}</span>
            <div>
              <strong>{row.label}</strong>
              {row.code ? <span>코드: {row.code}</span> : null}
              {row.basis ? <span>기본 기준: {row.basis}</span> : null}
            </div>
          </div>
          <div className="daily-checklist-row-controls">
            <label className={row.result === "COMPLIANT" ? "daily-check selected" : "daily-check"}>
              <input
                checked={row.result === "COMPLIANT"}
                disabled={!canWriteReports}
                onChange={() => onUpdateRow(row.id, { result: row.result === "COMPLIANT" ? "" : "COMPLIANT" })}
                type="checkbox"
              />
              적합
            </label>
            <label className={row.result === "NON_COMPLIANT" ? "daily-check selected danger" : "daily-check danger"}>
              <input
                checked={row.result === "NON_COMPLIANT"}
                disabled={!canWriteReports}
                onChange={() => onUpdateRow(row.id, { result: row.result === "NON_COMPLIANT" ? "" : "NON_COMPLIANT" })}
                type="checkbox"
              />
              부적합
            </label>
            <button
              className={row.referenceNote ? "secondary-button compact active" : "secondary-button compact"}
              disabled={!canWriteReports}
              onClick={() => onOpenNote(row.id, "referenceNote")}
              type="button"
            >
              기준·참고사항
            </button>
            <button
              className={row.actionNote ? "secondary-button compact active" : "secondary-button compact"}
              disabled={!canWriteReports}
              onClick={() => onOpenNote(row.id, "actionNote")}
              type="button"
            >
              조치사항
            </button>
          </div>
          <div className="daily-checklist-row-photos">
            <DailyPhotoThumbStrip
              canWriteReports={canWriteReports}
              deletingPhotoIds={deletingPhotoIds}
              officeId={officeId}
              onDeletePhoto={onDeletePhoto}
              photoIds={row.photoIds}
              photosById={photosById}
              token={token}
            />
            <div className="daily-checklist-row-photo-actions">
              <label className={!canWriteReports ? "secondary-button compact disabled" : "secondary-button compact"}>
                <Camera size={14} />
                사진 촬영
                <input
                  accept="image/*"
                  capture="environment"
                  disabled={!canWriteReports}
                  onChange={(event) => void onAttachPhotos(row.id, event)}
                  type="file"
                />
              </label>
              <label className={!canWriteReports ? "secondary-button compact disabled" : "secondary-button compact"}>
                <UploadCloud size={14} />
                사진 추가
                <input
                  accept="image/*"
                  disabled={!canWriteReports}
                  multiple
                  onChange={(event) => void onAttachPhotos(row.id, event)}
                  type="file"
                />
              </label>
            </div>
          </div>
        </div>
      ))}
      <div className="daily-generated-content">
        <span>문서 반영 감리내용</span>
        <p>{generatedContent || "아직 적합/부적합 또는 메모가 입력된 세부 감리항목이 없습니다."}</p>
      </div>
    </div>
  );
}

function ChecklistNoteDialog({
  canWriteReports,
  dialog,
  groups,
  onClose,
  onUpdateRow
}: {
  canWriteReports: boolean;
  dialog: ChecklistNoteDialogState;
  groups: DailySupervisionGroup[];
  onClose: () => void;
  onUpdateRow: (groupId: string, entryId: string, rowId: string, patch: Partial<DailyChecklistRow>) => void;
}) {
  const group = groups.find((current) => current.id === dialog.groupId);
  const entry = group?.entries.find((current) => current.id === dialog.entryId);
  const row = entry?.checklistRows.find((current) => current.id === dialog.rowId);
  if (!row) {
    return null;
  }
  const title = dialog.field === "referenceNote" ? "기준·참고사항" : "조치사항";
  return (
    <div className="modal-backdrop" role="presentation">
      <section className="modal-panel daily-note-dialog" role="dialog" aria-modal="true" aria-label={title}>
        <header className="modal-header">
          <div>
            <strong>{title}</strong>
            <span>{row.label}</span>
          </div>
          <button className="icon-button" onClick={onClose} type="button" aria-label="닫기">
            <X size={18} />
          </button>
        </header>
        <div className="modal-body">
          <textarea
            autoFocus
            disabled={!canWriteReports}
            onChange={(event) => onUpdateRow(dialog.groupId, dialog.entryId, dialog.rowId, { [dialog.field]: event.target.value })}
            placeholder={dialog.field === "referenceNote" ? "기준, 참고사항, 확인한 도면/시방/서류 등을 입력하세요." : "부적합 또는 보완이 필요한 경우 조치사항을 입력하세요."}
            value={row[dialog.field]}
          />
        </div>
        <footer className="modal-actions">
          <button className="primary-button" onClick={onClose} type="button">확인</button>
        </footer>
      </section>
    </div>
  );
}

function DailyPhotoThumbStrip({
  canWriteReports,
  deletingPhotoIds,
  officeId,
  onDeletePhoto,
  photoIds,
  photosById,
  token
}: {
  canWriteReports: boolean;
  deletingPhotoIds: Set<number>;
  officeId: number;
  onDeletePhoto: (photoId: number) => Promise<void>;
  photoIds: number[];
  photosById: Map<number, PhotoResponse>;
  token: string;
}) {
  if (photoIds.length === 0) {
    return (
      <div className="daily-supervision-photo-chips">
        <span className="daily-supervision-muted">연결 사진 없음</span>
      </div>
    );
  }

  return (
    <div className="daily-photo-thumb-strip">
      {photoIds.map((photoId) => (
        <DailyPhotoThumb
          canWriteReports={canWriteReports}
          deleting={deletingPhotoIds.has(photoId)}
          key={photoId}
          officeId={officeId}
          onDeletePhoto={onDeletePhoto}
          photo={photosById.get(photoId)}
          photoId={photoId}
          token={token}
        />
      ))}
    </div>
  );
}

function DailyPhotoThumb({
  canWriteReports,
  deleting,
  officeId,
  onDeletePhoto,
  photo,
  photoId,
  token
}: {
  canWriteReports: boolean;
  deleting: boolean;
  officeId: number;
  onDeletePhoto: (photoId: number) => Promise<void>;
  photo?: PhotoResponse;
  photoId: number;
  token: string;
}) {
  const [previewOpen, setPreviewOpen] = useState(false);
  const previewAssetType = selectDailyPreviewAssetType(photo);
  const preview = usePhotoAssetPreview({
    assetType: previewAssetType,
    officeId,
    photoId,
    token
  });
  const label = photo?.caption || photo?.inspectionItemCode || `Photo #${photoId}`;
  const disabled = !preview.url || deleting;

  return (
    <>
      <div className={deleting ? "daily-photo-thumb deleting" : "daily-photo-thumb"}>
        <button
          aria-label={`Photo ${photoId} 크게 보기`}
          className="daily-photo-thumb-preview"
          disabled={disabled}
          onClick={() => setPreviewOpen(true)}
          type="button"
        >
          {preview.url ? (
            <img alt={`Photo ${photoId}`} src={preview.url} />
          ) : (
            <span className="daily-photo-thumb-placeholder">
              {preview.loading ? <Loader2 className="spin" size={16} /> : <FileImage size={18} />}
            </span>
          )}
        </button>
        <button
          aria-label={`Photo ${photoId} 삭제`}
          className="daily-photo-thumb-remove"
          disabled={!canWriteReports || deleting}
          onClick={() => void onDeletePhoto(photoId)}
          type="button"
        >
          {deleting ? <Loader2 className="spin" size={12} /> : <X size={12} />}
        </button>
        <span>{photo ? `#${photoId}` : `#${photoId} 준비 중`}</span>
      </div>

      {previewOpen && preview.url ? (
        <div className="photo-lightbox" onClick={() => setPreviewOpen(false)} role="dialog" aria-modal="true">
          <div className="photo-lightbox-panel" onClick={(event) => event.stopPropagation()}>
            <header>
              <div>
                <strong>Photo #{photoId}</strong>
                <span>{label}</span>
              </div>
              <button className="icon-button" onClick={() => setPreviewOpen(false)} type="button" aria-label="미리보기 닫기">
                <X size={18} />
              </button>
            </header>
            <img alt={`Photo ${photoId} larger preview`} src={preview.url} />
            <footer>
              {photo?.stepCode ?? "DAILY_LOG"} · {photo?.width ?? "-"}x{photo?.height ?? "-"}
            </footer>
          </div>
        </div>
      ) : null}
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
  const emptyLabel = group.tradeCode && group.processCode
    ? "세부 감리항목 전사 필요"
    : "공종/세부공정 선택 필요";

  useEffect(() => {
    setSelected(items[0]?.code ?? "");
  }, [group.tradeCode, group.processCode, items]);

  return (
    <div className="daily-supervision-item-picker">
      <select disabled={!canWriteReports || items.length === 0} onChange={(event) => setSelected(event.target.value)} value={selected}>
        <option value="">{items.length === 0 ? emptyLabel : "검사항목 선택"}</option>
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
    </div>
  );
}

function suggestedItems(group: DailySupervisionGroup, trades: SupervisionCatalogTrade[]) {
  const trade = selectedTrade(group, trades);
  const processGroup = selectedProcessGroup(group, trade);
  const items = processGroup?.items?.length ? processGroup.items : trade?.items ?? [];
  return items.filter((item) => (item.checklistRows?.length ?? 0) > 0);
}

function workCategoryOptions(trade: SupervisionCatalogTrade | null | undefined) {
  const processGroups = trade?.processGroups ?? [];
  const options = processGroups
    .map((processGroup) => ({
      code: processGroup.workCategory ?? "GENERAL",
      name: processGroup.workCategoryName ?? "일반"
    }))
    .filter((option) => option.code && option.name);
  const byCode = new Map<string, { code: string; name: string }>();
  options.forEach((option) => {
    if (!byCode.has(option.code)) {
      byCode.set(option.code, option);
    }
  });
  return [...byCode.values()];
}

function selectedProcessGroups(group: DailySupervisionGroup, trade: SupervisionCatalogTrade | null | undefined) {
  const processGroups = trade?.processGroups ?? [];
  if (!processGroups.length) {
    return [];
  }
  if (!group.workCategory) {
    return processGroups;
  }
  return processGroups.filter((processGroup) => (processGroup.workCategory ?? "GENERAL") === group.workCategory);
}

function suggestedProcesses(
  group: DailySupervisionGroup,
  trades: SupervisionCatalogTrade[],
  catalogProcessOptions: string[]
) {
  const trade = selectedTrade(group, trades);
  const processGroupNames = selectedProcessGroups(group, trade).map((processGroup) => processGroup.name);
  return uniqueStrings([...processGroupNames, ...(trade?.processes ?? []), ...catalogProcessOptions])
    .filter((option) => option && option !== "-");
}

function selectedTrade(group: DailySupervisionGroup | null | undefined, trades: SupervisionCatalogTrade[]) {
  return trades.find((trade) => trade.code === group?.tradeCode)
    ?? trades.find((trade) => trade.name === group?.tradeName)
    ?? null;
}

function processGroupByName(group: DailySupervisionGroup, trades: SupervisionCatalogTrade[], name: string) {
  return selectedProcessGroup({ ...group, processName: name }, selectedTrade(group, trades));
}

function selectedProcessGroup(group: DailySupervisionGroup, trade: SupervisionCatalogTrade | null) {
  if (!trade?.processGroups?.length) {
    return null;
  }
  const processGroups = selectedProcessGroups(group, trade);
  return processGroups.find((processGroup) => processGroup.code === group.processCode)
    ?? processGroups.find((processGroup) => processGroup.name === group.processName)
    ?? null;
}

function selectDailyPreviewAssetType(photo?: PhotoResponse): Exclude<PhotoAssetType, "ORIGINAL"> | null {
  if (!photo) {
    return null;
  }
  const working = photo.assets.find((asset: PhotoAssetResponse) => asset.assetType === "WORKING");
  const thumbnail = photo.assets.find((asset: PhotoAssetResponse) => asset.assetType === "THUMBNAIL");
  if (working?.status === "UPLOADED") {
    return "WORKING";
  }
  if (thumbnail?.status === "UPLOADED") {
    return "THUMBNAIL";
  }
  return null;
}

function emptyEntry(): DailySupervisionEntry {
  return {
    checklistRows: [],
    id: newId("entry"),
    inspectionItemName: "",
    supervisionContent: ""
  };
}

function entryFromCatalogItem(catalogItem?: SupervisionCatalogItem): DailySupervisionEntry {
  const entry = {
    checklistRows: checklistRowsFromCatalogItem(catalogItem),
    id: newId("entry"),
    inspectionItemCode: catalogItem?.code,
    inspectionItemName: catalogItem?.name ?? "",
    supervisionContent: ""
  };
  return syncEntryGeneratedContent(entry);
}

function checklistRowsFromCatalogItem(catalogItem?: SupervisionCatalogItem): DailyChecklistRow[] {
  const rows = catalogItem?.checklistRows ?? [];
  return rows.map((row, index) => checklistRowFromCatalogRow(row, index));
}

function checklistRowFromCatalogRow(row: SupervisionCatalogChecklistRow, index: number): DailyChecklistRow {
  return {
    actionNote: "",
    basis: optionalText(row.basis),
    code: optionalText(row.code),
    id: newId(`row-${index}`),
    label: text(row.label),
    photoIds: [],
    referenceNote: "",
    result: ""
  };
}

function syncGroupGeneratedContent(group: DailySupervisionGroup): DailySupervisionGroup {
  return {
    ...group,
    entries: group.entries.map(syncEntryGeneratedContent)
  };
}

function syncEntryGeneratedContent(entry: DailySupervisionEntry): DailySupervisionEntry {
  if (entry.checklistRows.length === 0) {
    return entry;
  }
  return {
    ...entry,
    supervisionContent: buildSupervisionContent(entry)
  };
}

function buildSupervisionContent(entry: DailySupervisionEntry) {
  if (entry.checklistRows.length === 0) {
    return "";
  }
  const rows = entry.checklistRows
    .map((row) => checklistRowContent(row))
    .filter(Boolean);
  if (rows.length === 0) {
    return "";
  }
  const title = entry.inspectionItemName ? `${entry.inspectionItemName}` : "";
  return [title, ...rows.map((row) => `- ${row}`)].filter(Boolean).join("\n");
}

function checklistRowContent(row: DailyChecklistRow) {
  const parts = [row.label.trim()];
  const result = resultLabel(row.result);
  if (result) {
    parts.push(result);
  }
  if (row.referenceNote.trim()) {
    parts.push(`기준·참고: ${row.referenceNote.trim()}`);
  }
  if (row.actionNote.trim()) {
    parts.push(`조치사항: ${row.actionNote.trim()}`);
  }
  return parts.length > 1 ? parts.join(" / ") : "";
}

function resultLabel(result: DailyChecklistResult) {
  if (result === "COMPLIANT") {
    return "적합";
  }
  if (result === "NON_COMPLIANT") {
    return "부적합";
  }
  return "";
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
    groups: (raw.groups.map((group, index) => normalizeGroup(group, index)).filter(Boolean) as DailySupervisionGroup[])
      .map(syncGroupGeneratedContent)
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
    tradeName: text(raw.tradeName),
    workCategory: optionalText(raw.workCategory),
    workCategoryName: optionalText(raw.workCategoryName)
  };
}

function normalizeEntry(value: unknown, index: number): DailySupervisionEntry | null {
  if (!value || typeof value !== "object") {
    return null;
  }
  const raw = value as Partial<DailySupervisionEntry>;
  return {
    checklistRows: Array.isArray(raw.checklistRows)
      ? raw.checklistRows.map((row, rowIndex) => normalizeChecklistRow(row, rowIndex)).filter(Boolean) as DailyChecklistRow[]
      : [],
    id: text(raw.id) || newId(`entry-${index}`),
    inspectionItemCode: optionalText(raw.inspectionItemCode),
    inspectionItemName: text(raw.inspectionItemName),
    supervisionContent: text(raw.supervisionContent)
  };
}

function normalizeChecklistRow(value: unknown, index: number): DailyChecklistRow | null {
  if (!value || typeof value !== "object") {
    return null;
  }
  const raw = value as Partial<DailyChecklistRow>;
  return {
    actionNote: text(raw.actionNote),
    basis: optionalText(raw.basis),
    code: optionalText(raw.code),
    id: text(raw.id) || newId(`row-${index}`),
    label: text(raw.label),
    photoIds: uniqueNumbers(Array.isArray(raw.photoIds) ? raw.photoIds : []),
    referenceNote: text(raw.referenceNote),
    result: normalizeChecklistResult(raw.result)
  };
}

function rowPhotoIds(entry: DailySupervisionEntry) {
  return uniqueNumbers(entry.checklistRows.flatMap((row) => row.photoIds));
}

function normalizeChecklistResult(value: unknown): DailyChecklistResult {
  const normalized = text(value).trim().toUpperCase();
  return normalized === "COMPLIANT" || normalized === "NON_COMPLIANT" ? normalized : "";
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
