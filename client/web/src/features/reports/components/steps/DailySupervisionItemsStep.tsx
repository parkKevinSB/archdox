import { Camera, FileImage, Loader2, Plus, Trash2, UploadCloud, X } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { useEffect, useMemo, useState, type ChangeEvent } from "react";
import { usePhotoAssetPreview } from "../../../photos/hooks/usePhotoAssetPreview";
import { usePhotoWorkspace } from "../../../photos/hooks/usePhotoWorkspace";
import { PhotoUploadTaskStrip } from "../../../photos/components/PhotoPipelinePanel";
import type { PhotoAssetResponse, PhotoAssetType, PhotoResponse } from "../../../photos/types";
import { getSupervisionDomainCatalog } from "../../api";
import type {
  SupervisionCatalogCanonicalAtoms,
  SupervisionCatalogChecklistRow,
  SupervisionCatalogItem,
  SupervisionCatalogPhase,
  SupervisionCatalogPhaseChecklistGroup,
  SupervisionCatalogProcessGroup,
  SupervisionCatalogSubTrade,
  SupervisionCatalogTrade,
  SupervisionCatalogTradeGroup,
  SupervisionDomainCatalog,
  SupervisionWorkModeTradeRef,
  SupervisionWorkModeWorkCategoryRef
} from "../../types";
import type { ReportStepComponentProps } from "./ReportFormStep";

type DailyChecklistResult = "" | "NOT_APPLICABLE" | "COMPLIANT" | "NON_COMPLIANT";

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
  documentNarrativeText?: string;
  id: string;
  inspectionItemCode?: string;
  inspectionItemName: string;
};

type DailySupervisionGroup = {
  entries: DailySupervisionEntry[];
  floor: string;
  groupType?: "TRADE" | "PHASE";
  id: string;
  phaseChecklistGroupCode?: string;
  phaseChecklistGroupName?: string;
  phaseCode?: string;
  phaseName?: string;
  processCode?: string;
  processName: string;
  subTradeCode?: string;
  subTradeName?: string;
  tradeGroupCode?: string;
  tradeGroupName?: string;
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
const SUB_TRADE_NONE_CODE = "NONE";
const koreanCollator = new Intl.Collator("ko-KR", { numeric: true, sensitivity: "base" });
const SUB_TRADE_NONE_NAME = "없음";

export function DailySupervisionItemsStep({
  canWriteReports,
  definition,
  form,
  officeId,
  register,
  report,
  revision,
  savedStep,
  site,
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
    queryKey: ["supervisionDomainCatalog", officeId, CATALOG_CODE, report.siteId, site?.supervisionWorkMode],
    queryFn: () => getSupervisionDomainCatalog(token, officeId, CATALOG_CODE, report.siteId)
  });
  const catalog = catalogQuery.data ?? null;
  const selectedCatalogCoverage = catalog?.selectedSupervisionWorkModeCatalogCoverage;
  const shouldShowCatalogCoverageNotice = Boolean(
    selectedCatalogCoverage?.status && selectedCatalogCoverage.status !== "READY"
  );
  const trades = useMemo(() => tradeOptions(catalog), [catalog]);
  const tradeGroups = useMemo(() => tradeGroupOptions(catalog, trades), [catalog, trades]);
  const phases = useMemo(() => phaseOptions(catalog), [catalog]);
  const phaseChecklistGroups = useMemo(() => phaseChecklistGroupOptions(catalog), [catalog]);
  const floorOptions = catalog?.floorOptions ?? [];
  const processOptions = catalog?.processOptions ?? [];

  const totalItems = useMemo(() => groups.reduce((sum, group) => sum + group.entries.length, 0), [groups]);
  const totalChecklistRows = useMemo(
    () => groups.reduce((sum, group) => sum + group.entries.reduce((itemSum, entry) => itemSum + entry.checklistRows.length, 0), 0),
    [groups]
  );
  const checkedChecklistRows = useMemo(
    () => groups.reduce((sum, group) => sum + group.entries.reduce(
      (itemSum, entry) => itemSum + entry.checklistRows.filter((row) => row.result !== "NOT_APPLICABLE").length,
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
      const nextGroups = updater(current);
      form.setValue(DAILY_ITEMS_FIELD, JSON.stringify({ groups: nextGroups }), {
        shouldDirty: true,
        shouldTouch: true,
        shouldValidate: false
      });
      return nextGroups;
    });
  }

  function addTradeGroup() {
    const defaultTradeGroup = tradeGroups[0] ?? null;
    updateGroups((current) => [
      ...current,
      {
        entries: [],
        floor: "",
        groupType: "TRADE",
        id: newId("group"),
        processName: "",
        tradeGroupCode: defaultTradeGroup?.code,
        tradeGroupName: defaultTradeGroup?.name ?? "",
        phaseCode: undefined,
        phaseName: undefined,
        phaseChecklistGroupCode: undefined,
        phaseChecklistGroupName: undefined,
        subTradeCode: undefined,
        subTradeName: undefined,
        tradeCode: undefined,
        tradeName: "",
        workCategory: undefined,
        workCategoryName: undefined
      }
    ]);
  }

  function addPhaseGroup() {
    const defaultPhase = phases[0] ?? null;
    const defaultPhaseChecklistGroup = phaseChecklistGroups[0] ?? null;
    const defaultCategory = defaultPhase ? workCategoryOptions(defaultPhase)[0] : null;
    updateGroups((current) => [
      ...current,
      {
        entries: [],
        floor: "-",
        groupType: "PHASE",
        id: newId("group"),
        phaseChecklistGroupCode: defaultPhaseChecklistGroup?.code,
        phaseChecklistGroupName: defaultPhaseChecklistGroup?.name ?? "",
        phaseCode: defaultPhase?.code,
        phaseName: defaultPhase?.name,
        processName: "",
        subTradeCode: SUB_TRADE_NONE_CODE,
        subTradeName: SUB_TRADE_NONE_NAME,
        tradeGroupCode: undefined,
        tradeGroupName: undefined,
        tradeCode: undefined,
        tradeName: "",
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

  function selectTradeGroup(groupId: string, tradeGroupCode: string) {
    const selected = tradeGroups.find((group) => group.code === tradeGroupCode);
    updateGroup(groupId, {
      groupType: "TRADE",
      phaseChecklistGroupCode: undefined,
      phaseChecklistGroupName: undefined,
      phaseCode: undefined,
      phaseName: undefined,
      processCode: undefined,
      processName: "",
      tradeCode: undefined,
      tradeGroupCode: selected?.code,
      tradeGroupName: selected?.name ?? "",
      tradeName: "",
      subTradeCode: undefined,
      subTradeName: undefined,
      workCategory: undefined,
      workCategoryName: undefined
    });
  }

  function selectTrade(groupId: string, tradeCode: string) {
    const selected = trades.find((trade) => trade.code === tradeCode);
    const subTradeOptions = selected ? subTradeOptionsForTrade(selected) : [];
    const defaultSubTrade = subTradeOptions.length === 1 ? subTradeOptions[0] : null;
    const defaultCategory = selected ? workCategoryOptions(selected)[0] : null;
    updateGroup(groupId, {
      groupType: "TRADE",
      phaseChecklistGroupCode: undefined,
      phaseChecklistGroupName: undefined,
      phaseCode: undefined,
      phaseName: undefined,
      processCode: undefined,
      processName: "",
      subTradeCode: defaultSubTrade?.code,
      subTradeName: defaultSubTrade?.name,
      tradeCode: selected?.code,
      tradeGroupCode: selected?.tradeGroupCode,
      tradeGroupName: selected?.tradeGroupName ?? "",
      tradeName: selected?.name ?? "",
      workCategory: defaultCategory?.code,
      workCategoryName: defaultCategory?.name
    });
  }

  function selectSubTrade(groupId: string, subTradeCode: string) {
    const group = groups.find((current) => current.id === groupId);
    const trade = selectedTrade(group, trades);
    const selected = subTradeOptionsForTrade(trade).find((option) => option.code === subTradeCode);
    updateGroup(groupId, {
      processCode: undefined,
      processName: "",
      subTradeCode: selected?.code,
      subTradeName: selected?.name
    });
  }

  function selectPhase(groupId: string, phaseCode: string) {
    const selected = phases.find((phase) => phase.code === phaseCode);
    const defaultCategory = selected ? workCategoryOptions(selected)[0] : null;
    updateGroup(groupId, {
      groupType: "PHASE",
      phaseChecklistGroupCode: phaseChecklistGroups[0]?.code,
      phaseChecklistGroupName: phaseChecklistGroups[0]?.name ?? "",
      phaseCode: selected?.code,
      phaseName: selected?.name ?? "",
      processCode: undefined,
      processName: "",
      subTradeCode: SUB_TRADE_NONE_CODE,
      subTradeName: SUB_TRADE_NONE_NAME,
      tradeGroupCode: undefined,
      tradeGroupName: undefined,
      tradeCode: undefined,
      tradeName: "",
      workCategory: defaultCategory?.code,
      workCategoryName: defaultCategory?.name
    });
  }

  function selectWorkCategory(groupId: string, categoryCode: string) {
    const group = groups.find((current) => current.id === groupId);
    const category = workCategoryOptions(selectedCatalogGroup(group, trades, phases)).find((option) => option.code === categoryCode);
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
            documentNarrativeText: undefined,
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
      entries: group.entries.map((entry) => {
        let removedFromEntry = false;
        const checklistRows = entry.checklistRows.map((row) => {
          if (!row.photoIds.includes(photoId)) {
            return row;
          }
          removedFromEntry = true;
          return {
            ...row,
            photoIds: row.photoIds.filter((currentPhotoId) => currentPhotoId !== photoId)
          };
        });
        return {
          ...entry,
          documentNarrativeText: removedFromEntry ? undefined : entry.documentNarrativeText,
          checklistRows
        };
      })
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
      groupDisplayName(group),
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
        <span>감리업무 {catalog?.selectedSupervisionWorkModeName ?? supervisionWorkModeLabel(site?.supervisionWorkMode)}</span>
        <span>감리 그룹 {groups.length}개</span>
        <span>검사항목 {totalItems}개</span>
        <span>검사 대상 세부항목 {checkedChecklistRows}/{totalChecklistRows}개</span>
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
      {shouldShowCatalogCoverageNotice ? (
        <p className="daily-supervision-warning">
          {selectedCatalogCoverage?.message
            ?? `${catalog?.selectedSupervisionWorkModeName ?? "선택한 감리업무"} 기준은 현장에 저장되었습니다. 상세 체크리스트 전사는 다음 단계에서 확장됩니다.`}
        </p>
      ) : null}

      {groups.length === 0 ? (
        <div className="daily-supervision-empty">
          <strong>아직 입력한 검사항목이 없습니다.</strong>
          <span>공종별 또는 단계별 그룹을 추가한 뒤 세부업무와 검사항목을 선택하고 결과와 사진을 연결하세요.</span>
          <button className="primary-button" disabled={!canWriteReports} onClick={addTradeGroup} type="button">
            <Plus size={17} />
            공종 추가
          </button>
          <button className="secondary-button" disabled={!canWriteReports || phases.length === 0} onClick={addPhaseGroup} type="button">
            <Plus size={17} />
            단계별 그룹 추가
          </button>
        </div>
      ) : (
        <div className="daily-supervision-groups">
          {groups.map((group, groupIndex) => (
            <article className="daily-supervision-group" key={group.id}>
              <header className="daily-supervision-group-head">
                <div>
                  <span>{isPhaseGroup(group) ? "단계별 그룹" : "공종 그룹"} {groupIndex + 1}</span>
                  <strong>{groupDisplayName(group) || (isPhaseGroup(group) ? "공사단계 미선택" : "공종 미선택")}</strong>
                </div>
                <button
                  aria-label={isPhaseGroup(group) ? "단계별 그룹 삭제" : "공종 그룹 삭제"}
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
                {isPhaseGroup(group) ? (
                  <label>
                    공사단계
                    <select
                      disabled={!canWriteReports || phases.length === 0}
                      onChange={(event) => selectPhase(group.id, event.target.value)}
                      value={group.phaseCode ?? ""}
                    >
                      <option value="">공사단계 선택</option>
                      {sortByKoreanName(phases).map((entry) => (
                        <option key={entry.code} value={entry.code}>{entry.name}</option>
                      ))}
                    </select>
                  </label>
                ) : (
                  <>
                  {tradeGroups.length > 0 ? (
                    <label>
                      공종그룹
                      <select
                        disabled={!canWriteReports}
                        onChange={(event) => selectTradeGroup(group.id, event.target.value)}
                        value={group.tradeGroupCode ?? ""}
                      >
                        <option value="">공종그룹 선택</option>
                        {sortByKoreanName(tradeGroups).map((entry) => (
                          <option key={entry.code} value={entry.code}>{entry.name}</option>
                        ))}
                      </select>
                    </label>
                  ) : null}
                  <label>
                    공종
                    {trades.length > 0 ? (
                      <select
                        disabled={!canWriteReports || (tradeGroups.length > 0 && !group.tradeGroupCode)}
                        onChange={(event) => selectTrade(group.id, event.target.value)}
                        value={group.tradeCode ?? ""}
                      >
                        <option value="">공종 선택</option>
                        {sortByKoreanName(tradesForGroup(group, trades)).map((entry) => (
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
                  {shouldShowSubTradeSelector(selectedTrade(group, trades)) ? (
                    <label>
                      세부공종
                      <select
                        disabled={!canWriteReports || !group.tradeCode}
                        onChange={(event) => selectSubTrade(group.id, event.target.value)}
                        value={group.subTradeCode ?? ""}
                      >
                        <option value="">세부공종 선택</option>
                        {subTradeOptionsForTrade(selectedTrade(group, trades))
                          .filter((entry) => entry.code !== SUB_TRADE_NONE_CODE)
                          .sort(compareByKoreanName)
                          .map((entry) => (
                            <option key={entry.code} value={entry.code}>{entry.name}</option>
                          ))}
                      </select>
                    </label>
                  ) : null}
                  </>
                )}
                <div className="daily-work-category-field">
                  <span>업무구분</span>
                  {workCategoryOptions(selectedCatalogGroup(group, trades, phases)).length > 0 ? (
                    <div className="daily-work-category-segment" role="group" aria-label="업무구분">
                      {workCategoryOptions(selectedCatalogGroup(group, trades, phases)).map((option) => (
                        <button
                          className={group.workCategory === option.code ? "selected" : ""}
                          disabled={!canWriteReports || !groupRootCode(group)}
                          key={option.code}
                          onClick={() => selectWorkCategory(group.id, option.code)}
                          type="button"
                        >
                          {option.name}
                        </button>
                      ))}
                    </div>
                  ) : (
                    <span className="daily-supervision-muted">{isPhaseGroup(group) ? "공사단계 선택 필요" : "공종 선택 필요"}</span>
                  )}
                </div>
                <label>
                  {isPhaseGroup(group) ? "세부업무" : "세부공정"}
                  {selectedProcessGroups(group, selectedCatalogGroup(group, trades, phases)).length ? (
                    <select
                        disabled={!canWriteReports || !groupRootCode(group) || !group.workCategory || requiresSubTradeSelection(group, selectedTrade(group, trades))}
                      onChange={(event) => {
                        const selectedProcess = selectedProcessGroups(group, selectedCatalogGroup(group, trades, phases)).find((processGroup) => processGroup.code === event.target.value);
                        updateGroup(group.id, {
                          processCode: selectedProcess?.code,
                          processName: selectedProcess?.name ?? "",
                          subTradeCode: selectedProcess?.subTradeCode ?? group.subTradeCode,
                          subTradeName: selectedProcess?.subTradeName ?? group.subTradeName,
                          workCategory: selectedProcess?.workCategory ?? group.workCategory,
                          workCategoryName: selectedProcess?.workCategoryName ?? group.workCategoryName
                        });
                      }}
                      value={group.processCode ?? ""}
                    >
                      <option value="">{requiresSubTradeSelection(group, selectedTrade(group, trades)) ? "세부공종을 먼저 선택" : isPhaseGroup(group) ? "세부업무 선택" : "세부공정 선택"}</option>
                      {sortByKoreanName(selectedProcessGroups(group, selectedCatalogGroup(group, trades, phases))).map((processGroup) => (
                        <option key={processGroup.code} value={processGroup.code}>
                          {processGroup.name}
                        </option>
                      ))}
                    </select>
                  ) : (
                    <input
                      disabled={!canWriteReports || isPhaseGroup(group) || requiresSubTradeSelection(group, selectedTrade(group, trades))}
                      list={`daily-process-options-${group.id}`}
                      onChange={(event) => {
                        const selectedProcess = processGroupByName(group, trades, phases, event.target.value);
                        updateGroup(group.id, {
                          processCode: selectedProcess?.code,
                          processName: event.target.value,
                          subTradeCode: selectedProcess?.subTradeCode ?? group.subTradeCode,
                          subTradeName: selectedProcess?.subTradeName ?? group.subTradeName,
                          workCategory: selectedProcess?.workCategory ?? group.workCategory,
                          workCategoryName: selectedProcess?.workCategoryName ?? group.workCategoryName
                        });
                      }}
                      placeholder={isPhaseGroup(group) ? "공사단계를 먼저 선택하세요" : "예: 기초, 지하층 바닥"}
                      value={group.processName}
                    />
                  )}
                </label>
              </div>

              <DailyItemPicker
                canWriteReports={canWriteReports}
                group={group}
                phases={phases}
                trades={trades}
                onAdd={(itemName) => addEntry(group.id, itemName)}
              />

              <div className="daily-supervision-items">
                {group.entries.length === 0 ? (
                  <p className="daily-supervision-muted">검사항목을 추가하세요.</p>
                ) : group.entries.map((entry, entryIndex) => {
                  const itemOptions = sortByKoreanName(suggestedItems(group, trades, phases));
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
                              documentNarrativeText: undefined,
                              inspectionItemCode: catalogItem?.code,
                              inspectionItemName: catalogItem?.name ?? ""
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
                    {!entry.inspectionItemCode ? (
                      <span className="daily-supervision-warning">카탈로그 검사항목을 선택해야 법령 근거가 연결됩니다.</span>
                    ) : null}
                    {entry.checklistRows.length > 0 ? (
                      <DailyChecklistRowsEditor
                        canWriteReports={canWriteReports}
                        deletingPhotoIds={deletingPhotoIds}
                        entry={entry}
                        officeId={officeId}
                        onAttachPhotos={(rowId, event) => attachRowPhotos(group.id, entry.id, rowId, event)}
                        onDeletePhoto={deleteLinkedPhoto}
                        onOpenNote={(rowId, field) => setNoteDialog({ entryId: entry.id, field, groupId: group.id, rowId })}
                        onUpdateEntry={(patch) => updateEntry(group.id, entry.id, patch)}
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
                {sortByKoreanName(suggestedItems(group, trades, phases)).map((item) => (
                  <option key={item.code} value={item.name} />
                ))}
              </datalist>
              <datalist id={`daily-process-options-${group.id}`}>
                {sortKoreanStrings(suggestedProcesses(group, trades, phases, processOptions)).map((option) => (
                  <option key={option} value={option} />
                ))}
              </datalist>
            </article>
          ))}
        </div>
      )}

      {groups.length > 0 ? (
        <div className="daily-supervision-add-row">
          <button className="secondary-button" disabled={!canWriteReports} onClick={addTradeGroup} type="button">
            <Plus size={17} />
            공종 그룹 추가
          </button>
          <button className="secondary-button" disabled={!canWriteReports || phases.length === 0} onClick={addPhaseGroup} type="button">
            <Plus size={17} />
            단계별 그룹 추가
          </button>
        </div>
      ) : null}

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
  onUpdateEntry,
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
  onUpdateEntry: (patch: Partial<DailySupervisionEntry>) => void;
  onUpdateRow: (rowId: string, patch: Partial<DailyChecklistRow>) => void;
  photosById: Map<number, PhotoResponse>;
  token: string;
}) {
  const automaticContent = buildSupervisionContent(entry);
  const customDocumentText = entry.documentNarrativeText?.trim() ? entry.documentNarrativeText : "";
  const documentContent = customDocumentText || automaticContent;
  return (
    <div className="daily-checklist-rows">
      <div className="daily-checklist-rows-head">
        <strong>세부 감리항목</strong>
      </div>
      {entry.checklistRows.map((row, index) => (
        <div className="daily-checklist-row" key={row.id}>
          <div className="daily-checklist-row-main">
            <span className="daily-checklist-row-index">{index + 1}</span>
            <div>
              <strong>{row.label}</strong>
              {row.basis ? <span>기본 기준: {row.basis}</span> : null}
            </div>
          </div>
          <div className="daily-checklist-row-controls">
            <label className={row.result === "NOT_APPLICABLE" ? "daily-check neutral selected" : "daily-check neutral"}>
              <input
                checked={row.result === "NOT_APPLICABLE"}
                disabled={!canWriteReports}
                onChange={() => onUpdateRow(row.id, { result: "NOT_APPLICABLE" })}
                type="radio"
              />
              해당없음
            </label>
            <label className={row.result === "COMPLIANT" ? "daily-check selected" : "daily-check"}>
              <input
                checked={row.result === "COMPLIANT"}
                disabled={!canWriteReports}
                onChange={() => onUpdateRow(row.id, { result: "COMPLIANT" })}
                type="radio"
              />
              적합
            </label>
            <label className={row.result === "NON_COMPLIANT" ? "daily-check selected danger" : "daily-check danger"}>
              <input
                checked={row.result === "NON_COMPLIANT"}
                disabled={!canWriteReports}
                onChange={() => onUpdateRow(row.id, { result: "NON_COMPLIANT" })}
                type="radio"
              />
              부적합
            </label>
            <button
              className={row.referenceNote ? "secondary-button compact daily-row-action active" : "secondary-button compact daily-row-action"}
              disabled={!canWriteReports}
              onClick={() => onOpenNote(row.id, "referenceNote")}
              type="button"
            >
              기준·참고사항
            </button>
            <button
              className={row.actionNote ? "secondary-button compact daily-row-action active" : "secondary-button compact daily-row-action"}
              disabled={!canWriteReports}
              onClick={() => onOpenNote(row.id, "actionNote")}
              type="button"
            >
              조치사항
            </button>
            <label className={!canWriteReports ? "secondary-button compact daily-row-action disabled" : "secondary-button compact daily-row-action"}>
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
            <label className={!canWriteReports ? "secondary-button compact daily-row-action disabled" : "secondary-button compact daily-row-action"}>
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
          </div>
        </div>
      ))}
      {documentContent ? (
        <div className={customDocumentText ? "daily-generated-content custom" : "daily-generated-content"}>
          <div className="daily-generated-content-head">
            <span>{customDocumentText ? "문서용 문장" : "자동 생성 문장"}</span>
            <div>
              {!customDocumentText && automaticContent ? (
                <button
                  className="secondary-button compact"
                  disabled={!canWriteReports}
                  onClick={() => onUpdateEntry({ documentNarrativeText: automaticContent })}
                  type="button"
                >
                  문서용 문장 직접 수정
                </button>
              ) : null}
              {customDocumentText ? (
                <button
                  className="secondary-button compact"
                  disabled={!canWriteReports}
                  onClick={() => onUpdateEntry({ documentNarrativeText: undefined })}
                  type="button"
                >
                  기본 문장으로 되돌리기
                </button>
              ) : null}
            </div>
          </div>
          {customDocumentText ? (
            <textarea
              disabled={!canWriteReports}
              onChange={(event) => onUpdateEntry({ documentNarrativeText: event.target.value.trim() ? event.target.value : undefined })}
              rows={4}
              value={entry.documentNarrativeText ?? ""}
            />
          ) : (
            <p>{automaticContent}</p>
          )}
        </div>
      ) : null}
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
  const label = photo?.caption || `Photo #${photoId}`;
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
  phases,
  trades
}: {
  canWriteReports: boolean;
  group: DailySupervisionGroup;
  onAdd: (item?: SupervisionCatalogItem) => void;
  phases: SupervisionCatalogPhase[];
  trades: SupervisionCatalogTrade[];
}) {
  const [selected, setSelected] = useState("");
  const items = sortByKoreanName(suggestedItems(group, trades, phases));
  const itemsKey = items.map((item) => item.code).join("|");
  const selectedItem = items.find((item) => item.code === selected);
  const emptyLabel = groupRootCode(group) && group.processCode
    ? "세부 감리항목 전사 필요"
    : isPhaseGroup(group)
      ? "공사단계/세부업무 선택 필요"
      : "공종/세부공정 선택 필요";

  useEffect(() => {
    setSelected("");
  }, [itemsKey]);

  return (
    <div className="daily-supervision-item-picker">
      <select disabled={!canWriteReports || items.length === 0} onChange={(event) => setSelected(event.target.value)} value={selected}>
        <option value="">{items.length === 0 ? emptyLabel : isPhaseGroup(group) ? "세부검토 사항 선택" : "검사항목 선택"}</option>
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
        {isPhaseGroup(group) ? "세부검토 사항 추가" : "검사항목 추가"}
      </button>
    </div>
  );
}

function sortByKoreanName<T extends { code?: string; name?: string | null }>(items: T[]) {
  return [...items].sort(compareByKoreanName);
}

function compareByKoreanName<T extends { code?: string; name?: string | null }>(left: T, right: T) {
  return koreanCollator.compare(left.name || left.code || "", right.name || right.code || "");
}

function sortKoreanStrings(items: string[]) {
  return [...items].sort((left, right) => koreanCollator.compare(left, right));
}

function suggestedItems(group: DailySupervisionGroup, trades: SupervisionCatalogTrade[], phases: SupervisionCatalogPhase[]) {
  const catalogGroup = selectedCatalogGroup(group, trades, phases);
  const processGroup = selectedProcessGroup(group, catalogGroup);
  if (!processGroup?.items?.length) {
    return [];
  }
  return processGroup.items.filter((item) => item.code && item.name);
}

function workCategoryOptions(catalogGroup: SupervisionCatalogTrade | SupervisionCatalogPhase | null | undefined) {
  const processGroups = catalogGroup?.processGroups ?? [];
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

function selectedProcessGroups(
  group: DailySupervisionGroup,
  catalogGroup: SupervisionCatalogTrade | SupervisionCatalogPhase | null | undefined
) {
  let processGroups = catalogGroup?.processGroups ?? [];
  if (!processGroups.length) {
    return [];
  }
  if (!isPhaseGroup(group) && "subTrades" in (catalogGroup ?? {}) && shouldShowSubTradeSelector(catalogGroup as SupervisionCatalogTrade)) {
    if (!group.subTradeCode) {
      return [];
    }
    processGroups = processGroups.filter((processGroup) => (processGroup.subTradeCode ?? SUB_TRADE_NONE_CODE) === group.subTradeCode);
  }
  if (!group.workCategory) {
    return processGroups;
  }
  return processGroups.filter((processGroup) => (processGroup.workCategory ?? "GENERAL") === group.workCategory);
}

function uniqueSubTrades(processGroups: SupervisionCatalogProcessGroup[]): SupervisionCatalogSubTrade[] {
  const byCode = new Map<string, SupervisionCatalogSubTrade>();
  processGroups.forEach((processGroup) => {
    const code = processGroup.subTradeCode ?? SUB_TRADE_NONE_CODE;
    const name = processGroup.subTradeName ?? SUB_TRADE_NONE_NAME;
    if (!byCode.has(code)) {
      byCode.set(code, { code, name });
    }
  });
  return [...byCode.values()];
}

function subTradeOptionsForTrade(trade: SupervisionCatalogTrade | null | undefined): SupervisionCatalogSubTrade[] {
  if (!trade) {
    return [];
  }
  if (trade.subTrades?.length) {
    return trade.subTrades;
  }
  return uniqueSubTrades(trade.processGroups ?? []);
}

function shouldShowSubTradeSelector(trade: SupervisionCatalogTrade | null | undefined) {
  return subTradeOptionsForTrade(trade).some((option) => option.code !== SUB_TRADE_NONE_CODE);
}

function requiresSubTradeSelection(group: DailySupervisionGroup, trade: SupervisionCatalogTrade | null | undefined) {
  return shouldShowSubTradeSelector(trade) && !group.subTradeCode;
}

function suggestedProcesses(
  group: DailySupervisionGroup,
  trades: SupervisionCatalogTrade[],
  phases: SupervisionCatalogPhase[],
  catalogProcessOptions: string[]
) {
  const catalogGroup = selectedCatalogGroup(group, trades, phases);
  const processGroupNames = selectedProcessGroups(group, catalogGroup).map((processGroup) => processGroup.name);
  const tradeProcesses = !isPhaseGroup(group) && "processes" in (catalogGroup ?? {}) ? (catalogGroup as SupervisionCatalogTrade).processes ?? [] : [];
  return uniqueStrings([...processGroupNames, ...tradeProcesses, ...catalogProcessOptions])
    .filter((option) => option && option !== "-");
}

function selectedCatalogGroup(
  group: DailySupervisionGroup | null | undefined,
  trades: SupervisionCatalogTrade[],
  phases: SupervisionCatalogPhase[]
) {
  return isPhaseGroup(group)
    ? selectedPhase(group, phases)
    : selectedTrade(group, trades);
}

function selectedTrade(group: DailySupervisionGroup | null | undefined, trades: SupervisionCatalogTrade[]) {
  return trades.find((trade) => trade.code === group?.tradeCode)
    ?? trades.find((trade) => trade.name === group?.tradeName)
    ?? null;
}

function selectedPhase(group: DailySupervisionGroup | null | undefined, phases: SupervisionCatalogPhase[]) {
  return phases.find((phase) => phase.code === group?.phaseCode)
    ?? phases.find((phase) => phase.name === group?.phaseName)
    ?? null;
}

function processGroupByName(
  group: DailySupervisionGroup,
  trades: SupervisionCatalogTrade[],
  phases: SupervisionCatalogPhase[],
  name: string
) {
  return selectedProcessGroup({ ...group, processName: name }, selectedCatalogGroup(group, trades, phases));
}

function selectedProcessGroup(
  group: DailySupervisionGroup,
  catalogGroup: SupervisionCatalogTrade | SupervisionCatalogPhase | null
) {
  if (!catalogGroup?.processGroups?.length) {
    return null;
  }
  const processGroups = selectedProcessGroups(group, catalogGroup);
  return processGroups.find((processGroup) => processGroup.code === group.processCode)
    ?? processGroups.find((processGroup) => processGroup.name === group.processName)
    ?? null;
}

function tradeOptions(catalog: SupervisionDomainCatalog | null): SupervisionCatalogTrade[] {
  const modeTradeRefs = catalog?.selectedSupervisionWorkModeCatalog?.tradeRefs;
  const atoms = catalog?.canonicalAtoms;
  const baseTrades = catalog?.trades ?? [];
  if (!modeTradeRefs?.length || !atoms) {
    return baseTrades;
  }
  const baseByCode = new Map(baseTrades.map((trade) => [trade.code, trade]));
  return modeTradeRefs
    .map((tradeRef): SupervisionCatalogTrade | null => {
      const baseTrade = baseByCode.get(tradeRef.tradeCode);
      const tradeAtom = atoms.trades?.[tradeRef.tradeCode];
      if (!baseTrade && !tradeAtom) {
        return null;
      }
      const processGroups = tradeProcessCategorySources(tradeRef).flatMap((source) => (
        (source.category.processGroupRefs ?? []).map((processRef): SupervisionCatalogProcessGroup | null => {
          const processAtom = atoms.processGroups?.[processRef.code];
          if (!processAtom) {
            return null;
          }
          const items = (processRef.itemRefs ?? [])
            .map((itemRef) => catalogItemFromAtoms(atoms, itemRef))
            .filter(Boolean) as SupervisionCatalogItem[];
          return {
            code: processRef.code,
            items,
            name: processAtom.name,
            sourcePages: source.sourcePages ?? tradeRef.sourcePages,
            subTradeCode: source.subTradeCode ?? processAtom.subTradeCode ?? SUB_TRADE_NONE_CODE,
            subTradeName: source.subTradeName ?? processAtom.subTradeName,
            tradeCode: tradeRef.tradeCode,
            workCategory: source.category.code,
            workCategoryName: source.category.name
          };
        }).filter(Boolean) as SupervisionCatalogProcessGroup[]
      ));
      const subTrades = uniqueSubTrades(processGroups);
      return {
        ...(baseTrade ?? {}),
        code: tradeRef.tradeCode,
        discipline: tradeAtom?.discipline ?? baseTrade?.discipline,
        items: processGroups.flatMap((processGroup) => processGroup.items),
        name: tradeAtom?.name ?? baseTrade?.name ?? tradeRef.tradeCode,
        processGroups,
        processes: processGroups.map((processGroup) => processGroup.name),
        sourcePages: tradeRef.sourcePages,
        subTrades,
        tradeGroupCode: tradeAtom?.tradeGroupCode ?? baseTrade?.tradeGroupCode,
        tradeGroupName: tradeAtom?.tradeGroupName ?? baseTrade?.tradeGroupName
      };
    })
    .filter(Boolean) as SupervisionCatalogTrade[];
}

function tradeProcessCategorySources(tradeRef: SupervisionWorkModeTradeRef): Array<{
  category: SupervisionWorkModeWorkCategoryRef;
  sourcePages?: number[];
  subTradeCode?: string;
  subTradeName?: string;
}> {
  return tradeRef.subTradeRefs.flatMap((subTradeRef) => (
    (subTradeRef.workCategories ?? []).map((category) => ({
      category,
      sourcePages: subTradeRef.sourcePages ?? tradeRef.sourcePages,
      subTradeCode: subTradeRef.subTradeCode,
      subTradeName: subTradeRef.subTradeName
    }))
  ));
}

function tradeGroupOptions(
  catalog: SupervisionDomainCatalog | null,
  trades: SupervisionCatalogTrade[]
): SupervisionCatalogTradeGroup[] {
  const modeGroupRefs = catalog?.selectedSupervisionWorkModeCatalog?.tradeGroupRefs;
  if (modeGroupRefs?.length) {
    return modeGroupRefs
      .map((groupRef) => ({
        code: groupRef.tradeGroupCode,
        name: groupRef.tradeGroupName
          ?? catalog?.canonicalAtoms?.tradeGroups?.[groupRef.tradeGroupCode]?.name
          ?? groupRef.tradeGroupCode,
        tradeRefs: groupRef.tradeRefs ?? []
      }))
      .filter((group) => group.code && group.name);
  }
  const byCode = new Map<string, SupervisionCatalogTradeGroup>();
  trades.forEach((trade) => {
    const code = trade.tradeGroupCode ?? trade.discipline ?? "";
    if (!code || byCode.has(code)) {
      return;
    }
    byCode.set(code, {
      code,
      name: trade.tradeGroupName
        ?? catalog?.canonicalAtoms?.tradeGroups?.[code]?.name
        ?? code,
      tradeRefs: trades
        .filter((candidate) => (candidate.tradeGroupCode ?? candidate.discipline) === code)
        .map((candidate) => ({ tradeCode: candidate.code }))
    });
  });
  return [...byCode.values()];
}

function tradesForGroup(group: DailySupervisionGroup, trades: SupervisionCatalogTrade[]) {
  if (!group.tradeGroupCode) {
    return trades;
  }
  return trades.filter((trade) => (trade.tradeGroupCode ?? trade.discipline) === group.tradeGroupCode);
}

function phaseChecklistGroupOptions(catalog: SupervisionDomainCatalog | null): SupervisionCatalogPhaseChecklistGroup[] {
  const modeGroupRefs = catalog?.selectedSupervisionWorkModeCatalog?.phaseChecklistGroupRefs;
  if (modeGroupRefs?.length) {
    return modeGroupRefs
      .map((groupRef) => ({
        code: groupRef.phaseChecklistGroupCode,
        name: groupRef.phaseChecklistGroupName
          ?? catalog?.canonicalAtoms?.phaseChecklistGroups?.[groupRef.phaseChecklistGroupCode]?.name
          ?? groupRef.phaseChecklistGroupCode,
        phaseRefs: groupRef.phaseRefs ?? []
      }))
      .filter((group) => group.code && group.name);
  }
  const atoms = catalog?.canonicalAtoms?.phaseChecklistGroups;
  if (!atoms) {
    return [];
  }
  return Object.values(atoms)
    .map((atom) => ({
      code: atom.code,
      name: atom.name,
      phaseRefs: catalog?.selectedSupervisionWorkModeCatalog?.phaseRefs ?? []
    }))
    .filter((group) => group.code && group.name);
}

function phaseOptions(catalog: SupervisionDomainCatalog | null): SupervisionCatalogPhase[] {
  const phaseRefs = catalog?.selectedSupervisionWorkModeCatalog?.phaseRefs ?? [];
  const atoms = catalog?.canonicalAtoms;
  if (!atoms || phaseRefs.length === 0) {
    return [];
  }
  return phaseRefs.map((phaseRef) => {
    const phaseAtom = atoms.constructionPhases?.[phaseRef.phaseCode];
    const processGroups = (phaseRef.workCategories ?? []).flatMap((category) => (
      (category.processGroupRefs ?? []).map((processRef): SupervisionCatalogProcessGroup => {
        const processAtom = atoms.processGroups?.[processRef.code];
        const items = (processRef.itemRefs ?? [])
          .map((itemRef) => catalogItemFromAtoms(atoms, itemRef))
          .filter(Boolean) as SupervisionCatalogItem[];
        return {
          code: processRef.code,
          items,
          name: processAtom?.name ?? processRef.code,
          phaseCode: phaseRef.phaseCode,
          sourcePages: phaseRef.sourcePages,
          subTradeCode: processAtom?.subTradeCode ?? SUB_TRADE_NONE_CODE,
          subTradeName: processAtom?.subTradeName ?? SUB_TRADE_NONE_NAME,
          workCategory: category.code,
          workCategoryName: category.name
        };
      })
    ));
    return {
      code: phaseRef.phaseCode,
      items: processGroups.flatMap((processGroup) => processGroup.items),
      name: phaseAtom?.name ?? phaseRef.phaseCode,
      processGroups,
      sourcePages: phaseRef.sourcePages
    };
  });
}

function catalogItemFromAtoms(
  atoms: SupervisionCatalogCanonicalAtoms,
  itemCode: string
): SupervisionCatalogItem | null {
  const item = atoms.inspectionItems?.[itemCode];
  if (!item) {
    return null;
  }
  return {
    basis: item.basis,
    checklistRows: (item.rowRefs ?? [])
      .map((rowRef) => atoms.checklistRows?.[rowRef] ?? { code: rowRef, label: rowRef }),
    code: item.code,
    name: item.name
  };
}

function isPhaseGroup(group: DailySupervisionGroup | null | undefined) {
  return group?.groupType === "PHASE";
}

function groupRootCode(group: DailySupervisionGroup | null | undefined) {
  return isPhaseGroup(group) ? group?.phaseCode : group?.tradeCode;
}

function groupDisplayName(group: DailySupervisionGroup | null | undefined) {
  return isPhaseGroup(group)
    ? group?.phaseName || group?.phaseCode || ""
    : group?.tradeName || group?.tradeCode || "";
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

function entryFromCatalogItem(catalogItem?: SupervisionCatalogItem): DailySupervisionEntry {
  return {
    checklistRows: checklistRowsFromCatalogItem(catalogItem),
    documentNarrativeText: undefined,
    id: newId("entry"),
    inspectionItemCode: catalogItem?.code,
    inspectionItemName: catalogItem?.name ?? ""
  };
}

function checklistRowsFromCatalogItem(catalogItem?: SupervisionCatalogItem): DailyChecklistRow[] {
  if (!catalogItem) {
    return [];
  }
  const parentRow: SupervisionCatalogChecklistRow = {
    basis: catalogItem.basis,
    code: catalogItem.code,
    label: catalogItem.name
  };
  const rows = [parentRow, ...(catalogItem.checklistRows ?? []).filter((row) => row.code !== catalogItem.code)];
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
    result: "NOT_APPLICABLE"
  };
}

function buildSupervisionContent(entry: DailySupervisionEntry) {
  if (entry.checklistRows.length === 0) {
    return "";
  }
  const title = entry.inspectionItemName ? `${entry.inspectionItemName}` : "";
  const parentRow = entry.checklistRows.find((row) => isParentChecklistRow(entry, row));
  const parentInspected = parentRow?.result === "COMPLIANT" || parentRow?.result === "NON_COMPLIANT";
  const titleLine = title ? checklistTitleContent(title, parentRow) : "";
  const rows = entry.checklistRows
    .filter((row) => row !== parentRow)
    .map((row) => checklistRowContent(row))
    .filter(Boolean);
  if (rows.length === 0 && !parentInspected) {
    return "";
  }
  if (!titleLine && rows.length === 0) {
    return "";
  }
  return [titleLine, ...rows.map((row) => `- ${row}`)].filter(Boolean).join("\n");
}

function checklistTitleContent(title: string, parentRow?: DailyChecklistRow) {
  if (!parentRow || (parentRow.result !== "COMPLIANT" && parentRow.result !== "NON_COMPLIANT")) {
    return title;
  }
  const parts = [title];
  const result = resultLabel(parentRow.result);
  if (result) {
    parts.push(result);
  }
  if (parentRow.referenceNote.trim()) {
    parts.push(`기준·참고: ${parentRow.referenceNote.trim()}`);
  }
  if (parentRow.actionNote.trim()) {
    parts.push(`조치사항: ${parentRow.actionNote.trim()}`);
  }
  return parts.join(" / ");
}

function isParentChecklistRow(entry: DailySupervisionEntry, row: DailyChecklistRow) {
  return Boolean(
    (entry.inspectionItemCode && row.code === entry.inspectionItemCode)
      || (entry.inspectionItemName && row.label === entry.inspectionItemName)
  );
}

function checklistRowContent(row: DailyChecklistRow) {
  if (row.result !== "COMPLIANT" && row.result !== "NON_COMPLIANT") {
    return "";
  }
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
  if (!result) {
    return "";
  }
  if (result === "NOT_APPLICABLE") {
    return "해당없음";
  }
  if (result === "COMPLIANT") {
    return "적합";
  }
  if (result === "NON_COMPLIANT") {
    return "부적합";
  }
  return "";
}

function supervisionWorkModeLabel(value?: string | null) {
  switch (value) {
    case "RESIDENT":
      return "상주 감리";
    case "RESPONSIBLE_RESIDENT":
      return "책임상주 감리";
    case "NON_RESIDENT":
    default:
      return "비상주 감리";
  }
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
    groupType: raw.groupType === "PHASE" ? "PHASE" : "TRADE",
    id: text(raw.id) || newId(`group-${index}`),
    phaseChecklistGroupCode: optionalText(raw.phaseChecklistGroupCode),
    phaseChecklistGroupName: optionalText(raw.phaseChecklistGroupName),
    phaseCode: optionalText(raw.phaseCode),
    phaseName: optionalText(raw.phaseName),
    processCode: optionalText(raw.processCode),
    processName: text(raw.processName),
    subTradeCode: optionalText(raw.subTradeCode),
    subTradeName: optionalText(raw.subTradeName),
    tradeGroupCode: optionalText(raw.tradeGroupCode),
    tradeGroupName: optionalText(raw.tradeGroupName),
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
    documentNarrativeText: optionalText(raw.documentNarrativeText),
    id: text(raw.id) || newId(`entry-${index}`),
    inspectionItemCode: optionalText(raw.inspectionItemCode),
    inspectionItemName: text(raw.inspectionItemName)
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
  return normalized === "COMPLIANT" || normalized === "NON_COMPLIANT" || normalized === "NOT_APPLICABLE"
    ? normalized
    : "NOT_APPLICABLE";
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
