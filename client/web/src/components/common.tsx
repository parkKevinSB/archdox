import { CheckCircle2, FileText, Plus, Sparkles } from "lucide-react";
import type { ReactNode } from "react";

export function ViewHeader({ title, text, action }: { title: string; text: string; action?: string }) {
  return (
    <header className="view-header">
      <div>
        <p className="eyebrow">archdoX</p>
        <h1>{title}</h1>
        <p>{text}</p>
      </div>
      {action ? (
        <button className="primary-button" type="button">
          <Plus size={17} />
          {action}
        </button>
      ) : null}
    </header>
  );
}

export function Panel({ title, action, children }: { title: string; action?: ReactNode; children: ReactNode }) {
  return (
    <section className="panel">
      <header className="panel-header">
        <h2>{title}</h2>
        {action}
      </header>
      {children}
    </section>
  );
}

export function MetricTile({ label, value, detail }: { label: string; value: number; detail: string }) {
  return (
    <div className="metric-tile">
      <span>{label}</span>
      <strong>{value.toLocaleString()}</strong>
      <small>{detail}</small>
    </div>
  );
}

export function PlaceholderView({ icon, title, text }: { icon: ReactNode; title: string; text: string }) {
  return (
    <div className="placeholder-view">
      <div className="placeholder-icon">{icon}</div>
      <h1>{title}</h1>
      <p>{text}</p>
    </div>
  );
}

export function EmptyState({ title, text }: { title: string; text: string }) {
  return (
    <div className="empty-state">
      <Sparkles size={22} />
      <strong>{title}</strong>
      <span>{text}</span>
    </div>
  );
}

export function StatusBadge({ status }: { status: string }) {
  return <span className={`status-badge ${statusTone(status)}`}>{statusLabel(status)}</span>;
}

export function InlineAlert({ message }: { message: string }) {
  return (
    <div className="inline-alert">
      <span>{message}</span>
    </div>
  );
}

export function InlineNotice({ message }: { message: string }) {
  return (
    <div className="inline-notice">
      <CheckCircle2 size={16} />
      <span>{message}</span>
    </div>
  );
}

export function BrandLogo({ large = false, subtitle }: { large?: boolean; subtitle?: string }) {
  return (
    <div className={large ? "brand-logo large" : "brand-logo"}>
      <LogoMark />
      <div>
        <strong>
          archdo<span>X</span>
        </strong>
        {subtitle ? <span>{subtitle}</span> : null}
      </div>
    </div>
  );
}

export function LogoMark() {
  return (
    <div className="logo-mark" aria-hidden="true">
      <FileText size={18} />
    </div>
  );
}

export function FullScreenCenter({ children }: { children: ReactNode }) {
  return <div className="fullscreen-center">{children}</div>;
}

function statusTone(status: string) {
  if (["ACTIVE", "GENERATED", "COMPLETED", "READY_TO_GENERATE", "STEP_SAVED", "UPLOADED", "PICKED_UP", "NOT_REQUIRED", "PASSED", "PUBLISHED"].includes(status)) {
    return "green";
  }
  if (["DRAFT", "REQUESTED", "GENERATING", "GENERATION_REQUESTED", "PENDING", "PENDING_UPLOAD", "RUNNING", "STALE", "NEEDS_ATTENTION"].includes(status)) {
    return "amber";
  }
  if (["FAILED", "CANCELLED", "ARCHIVED"].includes(status)) {
    return "red";
  }
  return "slate";
}

export function statusLabel(status: string) {
  const labels: Record<string, string> = {
    ACCEPTED: "위험 수용",
    ACTIVE: "활성",
    ARCHIVED: "보관됨",
    CANCELLED: "취소됨",
    COMPLETED: "완료",
    CRITICAL: "긴급",
    DELIVERED: "전달 완료",
    DISPATCHING: "Agent 전송 중",
    DRAFT: "작성 전",
    FAILED: "실패",
    GENERATED: "생성 완료",
    GENERATING: "생성 중",
    GENERATION_REQUESTED: "생성 요청됨",
    HIGH: "높음",
    INFO: "정보",
    LOW: "낮음",
    MEDIUM: "보통",
    NEEDS_ATTENTION: "확인 필요",
    NOT_REQUIRED: "불필요",
    OPEN: "미처리",
    PASSED: "통과",
    PENDING: "대기 중",
    PENDING_UPLOAD: "업로드 대기",
    PICKED_UP: "수거 완료",
    PUBLISHED: "게시됨",
    QUEUED: "대기 중",
    READY_TO_GENERATE: "문서 생성 가능",
    RENDERING: "문서 렌더링 중",
    REQUESTED: "요청됨",
    RESOLVED: "해결됨",
    RUNNING: "진행 중",
    SENDING: "전송 중",
    STALE: "갱신 필요",
    STEP_SAVED: "작성 중",
    STORING_ARTIFACTS: "파일 저장 중",
    UPLOADED: "업로드 완료",
    VALIDATING: "검증 중",
    WAITING_FOR_AGENT: "Agent 응답 대기"
  };
  return labels[status] ?? status;
}

export function reportTypeLabel(reportType: string) {
  const labels: Record<string, string> = {
    CONSTRUCTION_DAILY_LOG: "공사감리일지",
    CONSTRUCTION_DAILY_SUPERVISION_LOG: "공사감리일지",
    CONSTRUCTION_SUPERVISION_REPORT: "감리보고서",
    DAILY_SUPERVISION: "공사감리일지"
  };
  return labels[reportType] ?? reportType;
}
