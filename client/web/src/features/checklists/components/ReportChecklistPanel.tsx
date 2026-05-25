import { Camera, CheckCircle2, FileImage, ImagePlus, Link2, Loader2, Save } from "lucide-react";
import { useEffect, useMemo, useState, type ChangeEvent } from "react";
import { useForm, type UseFormReturn } from "react-hook-form";
import { InlineAlert, InlineNotice } from "../../../components/common";
import { usePhotoAssetPreview } from "../../photos/hooks/usePhotoAssetPreview";
import { usePhotoWorkspace } from "../../photos/hooks/usePhotoWorkspace";
import type { PhotoAssetType, PhotoResponse } from "../../photos/types";
import { useReportChecklist } from "../hooks/useReportChecklist";
import type {
  ChecklistAnswer,
  ChecklistFormValues,
  ChecklistItem,
  InspectionReport,
  InspectionTarget
} from "../types";

type ReportChecklistPanelProps = {
  canWriteReports: boolean;
  officeId: number;
  report: InspectionReport;
  targets: InspectionTarget[];
  token: string;
};

type SaveOptions = {
  quiet?: boolean;
};

export function ReportChecklistPanel({
  canWriteReports,
  officeId,
  report,
  targets,
  token
}: ReportChecklistPanelProps) {
  const form = useForm<ChecklistFormValues>({ defaultValues: {} });
  const [selectedTargetId, setSelectedTargetId] = useState<number | null>(targets[0]?.id ?? null);
  const [busyItemCode, setBusyItemCode] = useState<string | null>(null);
  const [bulkSaving, setBulkSaving] = useState(false);
  const [uploadingPhotoItemId, setUploadingPhotoItemId] = useState<number | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [localError, setLocalError] = useState<string | null>(null);
  const values = form.watch();
  const {
    attachTarget,
    attachingTarget,
    checklist,
    loading,
    loadError,
    saveAnswer
  } = useReportChecklist({ token, officeId, reportId: report.id });
  const photoWorkspace = usePhotoWorkspace({
    officeId,
    report,
    token,
    uploadContext: { stepCode: "CHECKLIST" }
  });

  const visibleAnswers = useMemo(
    () => answersForTarget(checklist?.answers ?? [], selectedTargetId),
    [checklist?.answers, selectedTargetId]
  );
  const persistedValues = useMemo(() => formValuesFromAnswers(visibleAnswers), [visibleAnswers]);
  const answersByCode = useMemo(
    () => new Map(visibleAnswers.map((answer) => [answer.itemCode, answer])),
    [visibleAnswers]
  );
  const selectedTarget = selectedTargetId ? targets.find((target) => target.id === selectedTargetId) ?? null : null;
  const photosByChecklistItem = useMemo(
    () => groupPhotosByChecklistItem(photoWorkspace.allPhotos),
    [photoWorkspace.allPhotos]
  );
  const checklistPhotoCount = useMemo(
    () => photoWorkspace.allPhotos.filter((photo) => photo.checklistItemId !== null && photo.checklistItemId !== undefined).length,
    [photoWorkspace.allPhotos]
  );

  useEffect(() => {
    if (selectedTargetId && targets.some((target) => target.id === selectedTargetId)) {
      return;
    }
    setSelectedTargetId(targets[0]?.id ?? null);
  }, [selectedTargetId, targets]);

  useEffect(() => {
    if (!checklist) {
      return;
    }
    form.reset(formValuesFromAnswers(answersForTarget(checklist.answers, selectedTargetId)));
  }, [checklist?.schema.id, form, report.id, selectedTargetId]);

  const dirtyItemCodes = useMemo(() => {
    if (!checklist) {
      return [];
    }
    return checklist.schema.items
      .filter((item) => isItemDirty(item, values, persistedValues))
      .map((item) => item.itemCode);
  }, [checklist, persistedValues, values]);

  const summary = useMemo(() => {
    if (!checklist) {
      return { total: 0, answered: 0, issue: 0, dirty: 0 };
    }
    return checklist.schema.items.reduce(
      (current, item) => {
        const answer = answersByCode.get(item.itemCode);
        const value = answer?.answer?.value;
        return {
          total: current.total + 1,
          answered: current.answered + (isAnsweredValue(value) ? 1 : 0),
          issue: current.issue + (isIssueValue(value) ? 1 : 0),
          dirty: dirtyItemCodes.includes(item.itemCode) ? current.dirty + 1 : current.dirty
        };
      },
      { total: 0, answered: 0, issue: 0, dirty: 0 }
    );
  }, [answersByCode, checklist, dirtyItemCodes]);

  async function saveItem(item: ChecklistItem, options: SaveOptions = {}) {
    if (!canWriteReports) {
      setLocalError("체크리스트를 저장할 권한이 없습니다.");
      return false;
    }
    const answerValue = form.getValues(answerKey(item.itemCode)) ?? "";
    const noteValue = form.getValues(noteKey(item.itemCode)) ?? "";
    const existingAnswer = answersByCode.get(item.itemCode);
    if (!existingAnswer && !answerValue.trim() && !noteValue.trim()) {
      return true;
    }

    setBusyItemCode(item.itemCode);
    setNotice(null);
    setLocalError(null);
    try {
      await saveAnswer({
        itemCode: item.itemCode,
        body: {
          targetId: selectedTargetId,
          answer: { value: normalizedChecklistValue(item, answerValue) },
          note: normalizeFormValue(noteValue)
        }
      });
      if (!options.quiet) {
        setNotice(`${item.label} 항목을 저장했습니다.`);
      }
      return true;
    } catch (err) {
      setLocalError(err instanceof Error ? err.message : "체크리스트 항목을 저장하지 못했습니다.");
      return false;
    } finally {
      setBusyItemCode(null);
    }
  }

  async function saveDirtyItems() {
    if (!checklist || dirtyItemCodes.length === 0) {
      setNotice("저장할 변경 항목이 없습니다.");
      return;
    }
    setBulkSaving(true);
    setNotice(null);
    setLocalError(null);
    try {
      for (const item of checklist.schema.items.filter((current) => dirtyItemCodes.includes(current.itemCode))) {
        const saved = await saveItem(item, { quiet: true });
        if (!saved) {
          return;
        }
      }
      setNotice(`${dirtyItemCodes.length}개 변경 항목을 저장했습니다.`);
    } finally {
      setBulkSaving(false);
    }
  }

  async function attachSelectedTarget() {
    if (!canWriteReports) {
      setLocalError("리포트 대상을 연결할 권한이 없습니다.");
      return;
    }
    if (!selectedTargetId) {
      return;
    }
    setNotice(null);
    setLocalError(null);
    try {
      await attachTarget(selectedTargetId);
      setNotice("선택한 점검 대상을 리포트의 주 대상으로 연결했습니다.");
    } catch (err) {
      setLocalError(err instanceof Error ? err.message : "점검 대상을 리포트에 연결하지 못했습니다.");
    }
  }

  async function uploadChecklistPhotos(item: ChecklistItem, files: File[]) {
    if (!canWriteReports) {
      setLocalError("체크리스트 사진을 추가할 권한이 없습니다.");
      return;
    }
    if (files.length === 0) {
      return;
    }
    setUploadingPhotoItemId(item.id);
    setNotice(null);
    setLocalError(null);
    try {
      await photoWorkspace.uploadFiles(files, {
        checklistItemId: item.id,
        stepCode: "CHECKLIST"
      });
      setNotice(`${item.label} 항목에 사진 ${files.length}장을 연결했습니다.`);
    } catch (err) {
      setLocalError(err instanceof Error ? err.message : "체크리스트 항목 사진을 업로드하지 못했습니다.");
    } finally {
      setUploadingPhotoItemId(null);
    }
  }

  if (loading || !checklist) {
    return <InlineNotice message="체크리스트 스키마를 불러오는 중입니다." />;
  }

  const error = localError ?? errorMessage(loadError) ?? errorMessage(photoWorkspace.error);

  return (
    <div className="checklist-panel">
      <div className="checklist-head">
        <div>
          <strong>{checklist.schema.name}</strong>
          <span>
            {checklist.schema.code} v{checklist.schema.version}
          </span>
        </div>
        <div className="checklist-target-control">
          <select
            disabled={!canWriteReports}
            onChange={(event) => setSelectedTargetId(event.target.value ? Number(event.target.value) : null)}
            value={selectedTargetId ?? ""}
          >
            <option value="">리포트 공통</option>
            {targets.map((target) => (
              <option key={target.id} value={target.id}>
                {target.name}
              </option>
            ))}
          </select>
          <button
            className="secondary-button"
            disabled={attachingTarget || !selectedTargetId || !canWriteReports}
            onClick={attachSelectedTarget}
            type="button"
          >
            {attachingTarget ? <Loader2 className="spin" size={17} /> : <Link2 size={17} />}
            주 대상 연결
          </button>
        </div>
      </div>

      <div className="checklist-summary-bar">
        <SummaryTile label="대상" value={selectedTarget?.name ?? "리포트 공통"} />
        <SummaryTile label="응답" value={`${summary.answered}/${summary.total}`} />
        <SummaryTile label="확인 필요" value={summary.issue} tone={summary.issue > 0 ? "warning" : "normal"} />
        <SummaryTile label="미저장" value={summary.dirty} tone={summary.dirty > 0 ? "dirty" : "normal"} />
        <SummaryTile label="사진" value={`${checklistPhotoCount}장`} />
      </div>

      {error ? <InlineAlert message={error} /> : null}
      {!canWriteReports ? <InlineNotice message="이 계정은 체크리스트 쓰기 권한이 없습니다." /> : null}
      {notice ? <InlineNotice message={notice} /> : null}

      <div className="checklist-toolbar">
        <span>선택형 항목은 값을 누르면 바로 저장되고, 메모는 입력 후 변경 저장으로 반영합니다.</span>
        <button
          className="secondary-button"
          disabled={bulkSaving || dirtyItemCodes.length === 0 || !canWriteReports}
          onClick={saveDirtyItems}
          type="button"
        >
          {bulkSaving ? <Loader2 className="spin" size={17} /> : <Save size={17} />}
          변경 저장
        </button>
      </div>

      <div className="checklist-items">
        {checklist.schema.items.map((item) => (
          <ChecklistItemRow
            busy={busyItemCode === item.itemCode || bulkSaving}
            canWriteReports={canWriteReports}
            dirty={dirtyItemCodes.includes(item.itemCode)}
            form={form}
            item={item}
            key={item.id}
            linkedPhotos={photosByChecklistItem.get(item.id) ?? []}
            onSave={saveItem}
            onUploadPhotos={uploadChecklistPhotos}
            officeId={officeId}
            photoUploading={uploadingPhotoItemId === item.id && photoWorkspace.uploading}
            savedAnswer={answersByCode.get(item.itemCode)}
            token={token}
            value={values[answerKey(item.itemCode)] ?? ""}
          />
        ))}
      </div>
    </div>
  );
}

function SummaryTile({
  label,
  tone = "normal",
  value
}: {
  label: string;
  tone?: "dirty" | "normal" | "warning";
  value: number | string;
}) {
  return (
    <div className={`checklist-summary-tile ${tone}`}>
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function ChecklistItemRow({
  busy,
  canWriteReports,
  dirty,
  form,
  item,
  linkedPhotos,
  onSave,
  onUploadPhotos,
  officeId,
  photoUploading,
  savedAnswer,
  token,
  value
}: {
  busy: boolean;
  canWriteReports: boolean;
  dirty: boolean;
  form: UseFormReturn<ChecklistFormValues>;
  item: ChecklistItem;
  linkedPhotos: PhotoResponse[];
  onSave: (item: ChecklistItem, options?: SaveOptions) => Promise<boolean>;
  onUploadPhotos: (item: ChecklistItem, files: File[]) => Promise<void>;
  officeId: number;
  photoUploading: boolean;
  savedAnswer?: ChecklistAnswer;
  token: string;
  value: string;
}) {
  const noteRegistration = form.register(noteKey(item.itemCode));
  const visiblePhotos = linkedPhotos.slice(0, 4);

  async function handlePhotoSelection(event: ChangeEvent<HTMLInputElement>) {
    const files = Array.from(event.target.files ?? []).filter((file) => file.type.startsWith("image/"));
    event.target.value = "";
    if (files.length === 0) {
      return;
    }
    await onUploadPhotos(item, files);
  }

  return (
    <div className={["checklist-item", dirty ? "dirty" : "", isIssueValue(value) ? "issue" : ""].filter(Boolean).join(" ")}>
      <div className="checklist-item-main">
        <div>
          <strong>{item.label}</strong>
          <span>{item.description ?? (item.required ? "필수 항목" : "선택 항목")}</span>
        </div>
        <div className="checklist-item-status">
          {item.required ? <span className="required">필수</span> : null}
          {dirty ? <span className="dirty">미저장</span> : null}
          {savedAnswer && !dirty ? <span className="saved">저장됨</span> : null}
        </div>
      </div>
      <ChecklistAnswerInput
        disabled={!canWriteReports || busy}
        form={form}
        item={item}
        onQuickSave={() => onSave(item, { quiet: true })}
        value={value}
      />
      <input
        disabled={!canWriteReports || busy}
        placeholder="메모 또는 조치사항"
        {...noteRegistration}
        onBlur={(event) => {
          noteRegistration.onBlur(event);
          if (dirty) {
            void onSave(item, { quiet: true });
          }
        }}
      />
      {busy ? <Loader2 className="spin checklist-row-spinner" size={17} /> : <CheckCircle2 className="checklist-row-icon" size={17} />}
      <div className="checklist-item-photo-row">
        <div className="checklist-photo-actions">
          <span>
            <Camera size={15} />
            연결 사진 {linkedPhotos.length}장
          </span>
          <label className={photoUploading || !canWriteReports ? "secondary-button disabled" : "secondary-button"}>
            {photoUploading ? <Loader2 className="spin" size={16} /> : <ImagePlus size={16} />}
            사진 추가
            <input
              accept="image/*"
              disabled={photoUploading || !canWriteReports}
              multiple
              onChange={handlePhotoSelection}
              type="file"
            />
          </label>
        </div>
        {visiblePhotos.length > 0 ? (
          <div className="checklist-photo-strip">
            {visiblePhotos.map((photo) => (
              <ChecklistPhotoThumb key={photo.id} officeId={officeId} photo={photo} token={token} />
            ))}
            {linkedPhotos.length > visiblePhotos.length ? (
              <span className="checklist-photo-more">+{linkedPhotos.length - visiblePhotos.length}</span>
            ) : null}
          </div>
        ) : (
          <span className="checklist-photo-empty">이 항목에 연결된 사진이 없습니다.</span>
        )}
      </div>
    </div>
  );
}

function ChecklistPhotoThumb({
  officeId,
  photo,
  token
}: {
  officeId: number;
  photo: PhotoResponse;
  token: string;
}) {
  const previewAssetType = selectChecklistPreviewAssetType(photo);
  const preview = usePhotoAssetPreview({
    assetType: previewAssetType,
    officeId,
    photoId: photo.id,
    token
  });

  return (
    <span className="checklist-photo-thumb">
      {preview.url ? (
        <img alt={`Photo ${photo.id}`} src={preview.url} />
      ) : (
        <span>
          <FileImage size={15} />
        </span>
      )}
      <small>#{photo.id}</small>
    </span>
  );
}

function ChecklistAnswerInput({
  disabled,
  form,
  item,
  onQuickSave,
  value
}: {
  disabled: boolean;
  form: UseFormReturn<ChecklistFormValues>;
  item: ChecklistItem;
  onQuickSave: () => Promise<boolean>;
  value: string;
}) {
  const key = answerKey(item.itemCode);
  const registration = form.register(key);

  if (item.answerType === "TEXT") {
    return (
      <textarea
        disabled={disabled}
        placeholder="응답"
        {...registration}
        onBlur={(event) => {
          registration.onBlur(event);
          void onQuickSave();
        }}
      />
    );
  }
  if (item.answerType === "NUMBER") {
    return (
      <input
        disabled={disabled}
        placeholder="0"
        type="number"
        {...registration}
        onBlur={(event) => {
          registration.onBlur(event);
          void onQuickSave();
        }}
      />
    );
  }

  const options = answerOptions(item);
  return (
    <div className="checklist-segmented" role="group" aria-label={`${item.label} 응답`}>
      {options.map((option) => (
        <button
          className={[
            "checklist-answer-button",
            value === option.value ? "active" : "",
            answerTone(option.value)
          ].filter(Boolean).join(" ")}
          disabled={disabled}
          key={option.value}
          onClick={() => {
            form.setValue(key, option.value, { shouldDirty: true });
            void onQuickSave();
          }}
          type="button"
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}

function answerOptions(item: ChecklistItem) {
  if (item.answerType === "YES_NO" || item.answerType === "CHECK") {
    return [
      { label: "예", value: "true" },
      { label: "아니오", value: "false" }
    ];
  }
  return item.options.length > 0
    ? item.options.map((option) => ({ label: option, value: option }))
    : [
        { label: "적합", value: "적합" },
        { label: "보완필요", value: "보완필요" },
        { label: "부적합", value: "부적합" }
      ];
}

function answersForTarget(answers: ChecklistAnswer[], targetId: number | null) {
  return answers.filter((answer) => (answer.targetId ?? null) === targetId);
}

function formValuesFromAnswers(answers: ChecklistAnswer[]) {
  return answers.reduce<ChecklistFormValues>((values, answer) => {
    values[answerKey(answer.itemCode)] = checklistAnswerValue(answer.answer);
    values[noteKey(answer.itemCode)] = answer.note ?? "";
    return values;
  }, {});
}

function isItemDirty(
  item: ChecklistItem,
  values: ChecklistFormValues,
  persistedValues: ChecklistFormValues
) {
  return (values[answerKey(item.itemCode)] ?? "") !== (persistedValues[answerKey(item.itemCode)] ?? "")
    || (values[noteKey(item.itemCode)] ?? "") !== (persistedValues[noteKey(item.itemCode)] ?? "");
}

function checklistAnswerValue(answer: Record<string, unknown>) {
  const value = answer.value;
  if (typeof value === "boolean" || typeof value === "number") {
    return String(value);
  }
  if (typeof value === "string") {
    return value;
  }
  return "";
}

function normalizedChecklistValue(item: ChecklistItem, value: string) {
  if (item.answerType === "YES_NO" || item.answerType === "CHECK") {
    if (value === "true") {
      return true;
    }
    if (value === "false") {
      return false;
    }
  }
  if (item.answerType === "NUMBER") {
    const numericValue = Number(value);
    return Number.isFinite(numericValue) ? numericValue : value;
  }
  return value;
}

function isAnsweredValue(value: unknown) {
  return value !== null && value !== undefined && String(value).trim() !== "";
}

function isIssueValue(value: unknown) {
  const normalized = String(value ?? "").trim().toUpperCase();
  return ["FALSE", "NO", "NEEDS_ACTION", "보완필요", "부적합", "미흡"].includes(normalized);
}

function answerTone(value: string) {
  if (isIssueValue(value)) {
    return "warning";
  }
  if (value === "적합" || value === "true") {
    return "ok";
  }
  return "";
}

function answerKey(itemCode: string) {
  return `answer:${itemCode}`;
}

function noteKey(itemCode: string) {
  return `note:${itemCode}`;
}

function normalizeFormValue(value: string) {
  const trimmed = value.trim();
  return trimmed.length === 0 ? null : trimmed;
}

function groupPhotosByChecklistItem(photos: PhotoResponse[]) {
  return photos.reduce<Map<number, PhotoResponse[]>>((groups, photo) => {
    if (photo.checklistItemId === null || photo.checklistItemId === undefined) {
      return groups;
    }
    const photosForItem = groups.get(photo.checklistItemId) ?? [];
    photosForItem.push(photo);
    groups.set(photo.checklistItemId, photosForItem);
    return groups;
  }, new Map());
}

function selectChecklistPreviewAssetType(photo: PhotoResponse): Exclude<PhotoAssetType, "ORIGINAL"> | null {
  const thumbnail = photo.assets.find((asset) => asset.assetType === "THUMBNAIL");
  const working = photo.assets.find((asset) => asset.assetType === "WORKING");
  if (thumbnail?.status === "UPLOADED") {
    return "THUMBNAIL";
  }
  if (working?.status === "UPLOADED") {
    return "WORKING";
  }
  return null;
}

function errorMessage(error: unknown) {
  if (!error) {
    return null;
  }
  return error instanceof Error ? error.message : "체크리스트 정보를 처리하지 못했습니다.";
}
