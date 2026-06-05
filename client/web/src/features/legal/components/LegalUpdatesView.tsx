import { ArrowLeft, Bell, FileText, Loader2, RefreshCcw } from "lucide-react";
import { useEffect, useState } from "react";
import { EmptyState, InlineNotice, Panel, StatusBadge, ViewHeader } from "../../../components/common";
import { getLegalUpdate, listLegalUpdates } from "../api";
import type { LegalArticleDiff, LegalChangeDigest } from "../types";

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
  const detailMode = selectedDetail !== null || detailLoading;

  async function refresh() {
    setLoading(true);
    setError(null);
    try {
      const next = await listLegalUpdates(token, 30, 50);
      setUpdates(next);
      setSelectedId((current) => (next.some((update) => update.id === current) ? current : null));
      setSelectedDetail((current) => current == null ? null : next.find((update) => update.id === current.id) ?? current);
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

  function backToList() {
    setSelectedId(null);
    setSelectedDetail(null);
    setDetailLoading(false);
  }

  return (
    <div className="view-stack legal-updates-view">
      <ViewHeader
        title="법령 변경사항"
        text="최근 법령 변경과 ArchDox 업무 영향 요약을 게시글처럼 확인합니다."
      />
      <Panel
        title={detailMode ? "변경사항 상세" : "최근 변경"}
        action={
          detailMode ? (
            <button className="secondary-button" onClick={backToList} type="button">
              <ArrowLeft size={16} />
              목록으로
            </button>
          ) : (
            <button className="secondary-button" disabled={loading} onClick={refresh} type="button">
              {loading ? <Loader2 className="spin" size={16} /> : <RefreshCcw size={16} />}
              새로고침
            </button>
          )
        }
      >
        {error ? <InlineNotice message={error} /> : null}
        {detailMode ? (
          <LegalUpdateDetail loading={detailLoading} update={selectedDetail} />
        ) : updates.length === 0 && !loading ? (
          <EmptyState title="최근 변경사항이 없습니다" text="법령 동기화가 실행되면 최근 30일 변경사항이 표시됩니다." />
        ) : (
          <div className="legal-update-list">
            {updates.map((update) => (
              <button
                className={selectedId === update.id ? "legal-update-item active" : "legal-update-item"}
                key={update.id}
                onClick={() => void selectUpdate(update)}
                aria-pressed={selectedId === update.id}
                type="button"
              >
                <span>{formatDate(update.publishedAt ?? update.detectedAt)}</span>
                <strong>{update.title}</strong>
                <small>{update.summary}</small>
                <em>상세 보기</em>
              </button>
            ))}
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
        <EmptyState title="선택된 변경사항이 없습니다" text="목록에서 변경사항을 선택하세요." />
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
                  {diff.publicSourceUrl ? (
                    <a href={diff.publicSourceUrl} target="_blank" rel="noreferrer">
                      법령정보센터
                    </a>
                  ) : null}
                </div>
                <span>{diff.diffSummary}</span>
                {diff.beforeTextPreview || diff.afterTextPreview ? <LegalTextComparison diff={diff} /> : null}
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

type LegalTextBlock =
  | { kind: "heading" | "bullet" | "paragraph"; text: string }
  | { kind: "row"; cells: string[] };

function LegalTextComparison({ diff }: { diff: LegalArticleDiff }) {
  return (
    <div className="legal-text-comparison">
      <LegalTextPanel label="이전" text={diff.beforeTextPreview ?? ""} emptyText="이전 조문 본문 없음" />
      <LegalTextPanel label="이후" text={diff.afterTextPreview ?? ""} emptyText="이후 조문 본문 없음" />
    </div>
  );
}

function LegalTextPanel({ label, text, emptyText }: { label: string; text: string; emptyText: string }) {
  const sourceText = text?.trim() ?? "";
  const blocks = formatLegalTextBlocks(sourceText);
  return (
    <div className="legal-text-panel">
      <div className="legal-text-panel-header">
        <strong>{label}</strong>
        <span>읽기 보기</span>
      </div>
      <div className="legal-text-readable">
        {blocks.length === 0 ? (
          <p className="legal-text-empty">{emptyText}</p>
        ) : blocks.map((block, index) => renderLegalTextBlock(block, `${label}-${index}`))}
      </div>
      {sourceText ? (
        <details className="legal-text-raw">
          <summary>원문 보기</summary>
          <pre>{sourceText}</pre>
        </details>
      ) : null}
    </div>
  );
}

function renderLegalTextBlock(block: LegalTextBlock, key: string) {
  if (block.kind === "row") {
    return (
      <div className="legal-text-row" key={key}>
        {block.cells.map((cell, index) => (
          <span className="legal-text-cell" key={`${key}-${index}`}>
            {cell}
          </span>
        ))}
      </div>
    );
  }
  return (
    <p className={`legal-text-block ${block.kind}`} key={key}>
      {block.text}
    </p>
  );
}

function formatLegalTextBlocks(value: string): LegalTextBlock[] {
  return splitLegalText(value)
    .map((line) => line.trim())
    .filter((line) => line.length > 0 && !isBoxRuleLine(line))
    .map((line) => {
      const cells = tableCells(line);
      if (cells.length >= 2) {
        return { kind: "row", cells } satisfies LegalTextBlock;
      }
      if (isLegalHeading(line)) {
        return { kind: "heading", text: line } satisfies LegalTextBlock;
      }
      if (isLegalBullet(line)) {
        return { kind: "bullet", text: line } satisfies LegalTextBlock;
      }
      return { kind: "paragraph", text: line } satisfies LegalTextBlock;
    })
    .slice(0, 120);
}

function splitLegalText(value: string) {
  if (!value?.trim()) {
    return [];
  }
  const normalized = value
    .replace(/\r\n?/g, "\n")
    .replace(/([┌┬┐└┴┘├┼┤─━]{4,})/g, "\n$1\n")
    .replace(/\s+(■\s*)/g, "\n$1")
    .replace(/\s+(\[[^\]\n]{1,50}\])/g, "\n$1")
    .replace(/\s+(제\s*\d+조(?:의\d+)?(?:\([^)]*\))?)/g, "\n$1")
    .replace(/\s+(\d+\.\s+)/g, "\n$1")
    .replace(/\s+([가-하]\.\s+)/g, "\n$1")
    .replace(/\s+([①②③④⑤⑥⑦⑧⑨⑩])/g, "\n$1")
    .replace(/\s+(-\s+)/g, "\n$1");
  return normalized.split("\n").flatMap(splitLongLegalLine);
}

function splitLongLegalLine(line: string) {
  const trimmed = line.trim();
  if (trimmed.length < 260) {
    return [trimmed];
  }
  if (trimmed.includes(" - ")) {
    return trimmed.split(/\s+-\s+/).map((part, index) => (index === 0 ? part : `- ${part}`));
  }
  return trimmed.split(/(?=\s(?:\d+\.|[가-하]\.|[①②③④⑤⑥⑦⑧⑨⑩]))/g);
}

function tableCells(line: string) {
  if (!/[│|]/.test(line)) {
    return [];
  }
  return line
    .split(/[│|]/)
    .map((cell) => cell.replace(/[─━┬┴┼┌┐└┘├┤]+/g, " ").trim())
    .filter((cell) => cell.length > 0)
    .slice(0, 8);
}

function isBoxRuleLine(line: string) {
  const stripped = line.replace(/\s/g, "");
  return stripped.length > 0 && /^[─━┬┴┼┌┐└┘├┤│|]+$/.test(stripped);
}

function isLegalHeading(line: string) {
  return /^(\[[^\]]+\]|■|제\s*\d+조|별표|별지)/.test(line);
}

function isLegalBullet(line: string) {
  return /^(\d+\.|[가-하]\.|[-ㆍ•]|[①②③④⑤⑥⑦⑧⑨⑩])/.test(line);
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
