import { Bell, FileText, Loader2, RefreshCcw } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { EmptyState, InlineNotice, Panel, StatusBadge, ViewHeader } from "../../../components/common";
import { listLegalUpdates } from "../api";
import type { LegalChangeDigest } from "../types";

export function LegalUpdatesView({ token }: { token: string }) {
  const [updates, setUpdates] = useState<LegalChangeDigest[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const selected = useMemo(
    () => updates.find((update) => update.id === selectedId) ?? updates[0] ?? null,
    [updates, selectedId]
  );

  async function refresh() {
    setLoading(true);
    setError(null);
    try {
      const next = await listLegalUpdates(token, 30, 50);
      setUpdates(next);
      setSelectedId((current) => current ?? next[0]?.id ?? null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "법령 변경사항을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void refresh();
  }, [token]);

  return (
    <div className="view-stack legal-updates-view">
      <ViewHeader
        title="법령 변경사항"
        text="최근 법령 변경사항을 게시글처럼 확인합니다. 나중에는 중요한 변경이 추가되면 알림으로도 알려줄 수 있습니다."
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
        <InlineNotice message="현재는 변경사항 요약을 deterministic digest로 표시합니다. 실제 법령 API와 AI 요약 worker가 붙으면 제목/영향 설명이 더 정교해집니다." />
        {updates.length === 0 && !loading ? (
          <EmptyState title="최근 변경사항이 없습니다" text="법령 동기화가 실행되면 최근 30일 변경사항이 여기에 표시됩니다." />
        ) : (
          <div className="legal-update-layout">
            <div className="legal-update-list">
              {updates.map((update) => (
                <button
                  className={selected?.id === update.id ? "legal-update-item active" : "legal-update-item"}
                  key={update.id}
                  onClick={() => setSelectedId(update.id)}
                  type="button"
                >
                  <span>{formatDate(update.publishedAt ?? update.detectedAt)}</span>
                  <strong>{update.title}</strong>
                  <small>{update.summary}</small>
                </button>
              ))}
            </div>
            <LegalUpdateDetail update={selected} />
          </div>
        )}
      </Panel>
    </div>
  );
}

function LegalUpdateDetail({ update }: { update: LegalChangeDigest | null }) {
  if (!update) {
    return (
      <div className="legal-update-detail">
        <EmptyState title="선택된 변경사항이 없습니다" text="왼쪽 목록에서 변경사항을 선택하세요." />
      </div>
    );
  }

  return (
    <article className="legal-update-detail">
      <header>
        <div className="row-icon">
          <FileText size={18} />
        </div>
        <div>
          <h2>{update.title}</h2>
          <p>{formatDate(update.publishedAt ?? update.detectedAt)} · 시행일 {update.effectiveDate ?? "미정"}</p>
        </div>
        <StatusBadge status={update.status} />
      </header>
      <section>
        <h3>변경 요약</h3>
        <p>{update.summary}</p>
      </section>
      <section>
        <h3>업무 영향</h3>
        <p>{update.impactSummary ?? "아직 업무 영향 요약이 작성되지 않았습니다."}</p>
      </section>
      <footer>
        <Bell size={16} />
        <span>알림 기능은 추후 추가됩니다. 지금은 최근 변경 목록에서 확인합니다.</span>
      </footer>
    </article>
  );
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
