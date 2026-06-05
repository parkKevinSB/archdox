import { Bell, FileText, Loader2, RefreshCcw } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { EmptyState, InlineNotice, Panel, StatusBadge, ViewHeader } from "../../../components/common";
import { getLegalUpdate, listLegalUpdates } from "../api";
import type { LegalChangeDigest } from "../types";

const REPORT_TYPE_LABELS: Record<string, string> = {
  CONSTRUCTION_DAILY_SUPERVISION_LOG: "공사감리일지",
  CONSTRUCTION_SUPERVISION_REPORT: "감리보고서"
};

const CATALOG_ITEM_LABELS: Record<string, string> = {
  CONSTRUCTION_SUPERVISION_CHECKLIST: "공사감리 체크리스트",
  CONSTRUCTION_SUPERVISION_LEGAL_CONTEXT: "공사감리 법령 근거"
};

export function LegalUpdatesView({ token }: { token: string }) {
  const [updates, setUpdates] = useState<LegalChangeDigest[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [selectedDetail, setSelectedDetail] = useState<LegalChangeDigest | null>(null);
  const [loading, setLoading] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const selected = useMemo(
    () => selectedDetail ?? updates.find((update) => update.id === selectedId) ?? updates[0] ?? null,
    [selectedDetail, updates, selectedId]
  );

  async function refresh() {
    setLoading(true);
    setError(null);
    try {
      const next = await listLegalUpdates(token, 30, 50);
      setUpdates(next);
      setSelectedId((current) => {
        const nextSelectedId = next.some((update) => update.id === current) ? current : next[0]?.id ?? null;
        return nextSelectedId;
      });
      setSelectedDetail((current) => next.find((update) => update.id === current?.id) ?? next[0] ?? null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "법령 변경사항을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void refresh();
  }, [token]);

  async function selectUpdate(update: LegalChangeDigest) {
    setSelectedId(update.id);
    setSelectedDetail(update);
    setDetailLoading(true);
    setError(null);
    try {
      setSelectedDetail(await getLegalUpdate(token, update.id));
    } catch (err) {
      setError(err instanceof Error ? err.message : "법령 변경사항 상세를 불러오지 못했습니다.");
    } finally {
      setDetailLoading(false);
    }
  }

  return (
    <div className="view-stack legal-updates-view">
      <ViewHeader
        title="법령 변경사항"
        text="최근 법령 변경과 ArchDox 업무 영향 요약을 게시글처럼 확인합니다."
      />
      <Panel
        title="최근 변경"
        action={
          <button className="secondary-button" disabled={loading} onClick={refresh} type="button">
            {loading ? <Loader2 className="spin" size={16} /> : <RefreshCcw size={16} />}
            새로고침
          </button>
        }
      >
        {error ? <InlineNotice message={error} /> : null}
        <InlineNotice message="현재 요약은 deterministic digest 기준입니다. AI 영향도 분석 worker가 붙으면 제목, 요약, 관련 업무가 더 정교해집니다." />
        {updates.length === 0 && !loading ? (
          <EmptyState title="최근 변경사항이 없습니다" text="법령 동기화가 실행되면 최근 30일 변경사항이 표시됩니다." />
        ) : (
          <div className="legal-update-layout">
            <div className="legal-update-list">
              {updates.map((update) => (
                <button
                  className={selected?.id === update.id ? "legal-update-item active" : "legal-update-item"}
                  key={update.id}
                  onClick={() => void selectUpdate(update)}
                  aria-pressed={selected?.id === update.id}
                  type="button"
                >
                  <span>{formatDate(update.publishedAt ?? update.detectedAt)}</span>
                  <strong>{update.title}</strong>
                  <small>{update.summary}</small>
                  <em>{selected?.id === update.id ? "선택됨" : "상세 보기"}</em>
                </button>
              ))}
            </div>
            <LegalUpdateDetail loading={detailLoading} update={selected} />
          </div>
        )}
      </Panel>
    </div>
  );
}

function LegalUpdateDetail({ loading, update }: { loading: boolean; update: LegalChangeDigest | null }) {
  if (!update) {
    return (
      <div className="legal-update-detail">
        <EmptyState title="선택된 변경사항이 없습니다" text="왼쪽 목록에서 변경사항을 선택하세요." />
      </div>
    );
  }

  const affectedReportTypes = update.affectedReportTypes.map(formatReportType);
  const affectedCatalogItems = update.affectedCatalogItems.map(formatCatalogItem);
  const hasAffectedItems = affectedReportTypes.length > 0 || affectedCatalogItems.length > 0;
  const articleDiffs = update.articleDiffs ?? [];
  const added = articleDiffs.filter((diff) => diff.changeType === "ADDED").length;
  const modified = articleDiffs.filter((diff) => diff.changeType === "MODIFIED").length;
  const removed = articleDiffs.filter((diff) => diff.changeType === "REMOVED").length;

  return (
    <article className="legal-update-detail">
      <header>
        <div className="row-icon">
          <FileText size={18} />
        </div>
        <div>
          <h2>{update.title}</h2>
          <p>
            {formatDate(update.publishedAt ?? update.detectedAt)} · 시행일 {update.effectiveDate ?? "미정"} · Change Set #{update.changeSetId}
          </p>
        </div>
        {loading ? <Loader2 className="spin" size={18} /> : <StatusBadge status={update.status} />}
      </header>
      <div className="legal-update-diff-summary">
        <span>조문 {articleDiffs.length}건</span>
        <span>신설 {added}건</span>
        <span>수정 {modified}건</span>
        <span>삭제 {removed}건</span>
      </div>
      <section>
        <h3>변경 요약</h3>
        <p>{update.summary}</p>
      </section>
      <section>
        <h3>업무 영향</h3>
        <p>{update.impactSummary ?? "아직 업무 영향 요약이 작성되지 않았습니다."}</p>
      </section>
      {hasAffectedItems ? (
        <section>
          <h3>관련 업무</h3>
          <div className="legal-update-tags">
            {[...affectedReportTypes, ...affectedCatalogItems].map((label) => (
              <span className="legal-update-tag" key={label}>
                {label}
              </span>
            ))}
          </div>
        </section>
      ) : null}
      <section>
        <h3>조문별 변경</h3>
        {articleDiffs.length === 0 ? (
          <p>조문 단위 변경 기록은 없습니다.</p>
        ) : (
          <div className="legal-update-diff-list">
            {articleDiffs.slice(0, 80).map((diff) => (
              <div className="legal-update-diff-item" key={diff.id}>
                <div className="legal-update-diff-heading">
                  <strong>{formatChangeType(diff.changeType)} · {legalArticleLabel(diff.articleNo, diff.articleTitle, diff.articleKey)}</strong>
                  {diff.effectiveDate ? <small>시행일 {diff.effectiveDate}</small> : null}
                  {diff.sourceUrl ? (
                    <a href={diff.sourceUrl} target="_blank" rel="noreferrer">
                      원문
                    </a>
                  ) : null}
                </div>
                <span>{diff.diffSummary}</span>
                {diff.beforeTextPreview || diff.afterTextPreview ? (
                  <div className="legal-update-diff-preview">
                    <div>
                      <strong>이전</strong>
                      <p>{diff.beforeTextPreview || "이전 조문 본문 없음"}</p>
                    </div>
                    <div>
                      <strong>이후</strong>
                      <p>{diff.afterTextPreview || "이후 조문 본문 없음"}</p>
                    </div>
                  </div>
                ) : null}
                {diff.sourceVersionKey ? <small className="legal-update-source-version">버전 {diff.sourceVersionKey}</small> : null}
              </div>
            ))}
          </div>
        )}
      </section>
      <footer>
        <Bell size={16} />
        <span>중요 변경 알림은 이후 추가됩니다. 지금은 최근 변경 목록에서 확인합니다.</span>
      </footer>
    </article>
  );
}

function formatReportType(value: string) {
  return REPORT_TYPE_LABELS[value] ?? value;
}

function formatCatalogItem(value: string) {
  return CATALOG_ITEM_LABELS[value] ?? value;
}

function formatChangeType(value: string) {
  const labels: Record<string, string> = {
    ADDED: "신설",
    MODIFIED: "수정",
    REMOVED: "삭제"
  };
  return labels[value] ?? value;
}

function legalArticleLabel(articleNo?: string | null, articleTitle?: string | null, articleKey?: string | null) {
  const no = articleNo?.trim();
  const title = articleTitle?.trim();
  if (no && title) {
    return `${no} ${title}`;
  }
  return no || title || articleKey?.trim() || "조문";
}

function formatDate(value?: string | null) {
  if (!value) {
    return "-";
  }
  return new Intl.DateTimeFormat("ko-KR", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit"
  }).format(new Date(value));
}
