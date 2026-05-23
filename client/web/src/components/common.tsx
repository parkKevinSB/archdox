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
  return <span className={`status-badge ${statusTone(status)}`}>{status}</span>;
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
  if (["ACTIVE", "GENERATED", "COMPLETED", "READY_TO_GENERATE", "STEP_SAVED", "UPLOADED", "PICKED_UP", "NOT_REQUIRED"].includes(status)) {
    return "green";
  }
  if (["DRAFT", "REQUESTED", "GENERATING", "GENERATION_REQUESTED", "PENDING", "PENDING_UPLOAD"].includes(status)) {
    return "amber";
  }
  if (["FAILED", "CANCELLED", "ARCHIVED"].includes(status)) {
    return "red";
  }
  return "slate";
}
