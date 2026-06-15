import {
  Activity,
  Command,
  Copy,
  KeyRound,
  Loader2,
  PlugZap,
  XCircle
} from "lucide-react";
import { FormEvent, useEffect, useMemo, useState } from "react";
import { EmptyState, InlineAlert, InlineNotice, MetricTile, Panel, StatusBadge, ViewHeader } from "../../../components/common";
import type { Office } from "../../../types";
import {
  createEngineConnectBootstrap,
  getEngineConnectClients,
  getMcpToolCatalog,
  getMyEngineApiKeys,
  getMyEngineUsageEvents,
  getMyEngineUsageSummary,
  revokeMyEngineApiKey,
  runMyMcpLiveSmoke
} from "../api";
import type {
  EngineApiKey,
  EngineApiUsageEvent,
  EngineApiUsageSummary,
  EngineConnectBootstrapResponse,
  EngineConnectClient,
  EngineConnectClientType,
  McpLiveSmokeResult,
  McpToolCatalogItem
} from "../types";

type DeveloperPortalProps = {
  offices: Office[];
  selectedOfficeId: number | null;
  token: string;
};

export function DeveloperPortal({ offices, selectedOfficeId, token }: DeveloperPortalProps) {
  const [clients, setClients] = useState<EngineConnectClient[]>([]);
  const [keys, setKeys] = useState<EngineApiKey[]>([]);
  const [usageSummary, setUsageSummary] = useState<EngineApiUsageSummary | null>(null);
  const [usageEvents, setUsageEvents] = useState<EngineApiUsageEvent[]>([]);
  const [tools, setTools] = useState<McpToolCatalogItem[]>([]);
  const [bootstrap, setBootstrap] = useState<EngineConnectBootstrapResponse | null>(null);
  const [liveSmokeResult, setLiveSmokeResult] = useState<McpLiveSmokeResult | null>(null);
  const [selectedToolName, setSelectedToolName] = useState("");
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const activeKeys = keys.filter((key) => key.status === "ACTIVE");
  const mcpEvents = usageEvents.filter((event) => usageEventSource(event) === "MCP");
  const failedEvents = usageEvents.filter((event) => event.status !== "SUCCEEDED");
  const selectedTool = useMemo(
    () => tools.find((tool) => tool.name === selectedToolName) ?? tools[0] ?? null,
    [selectedToolName, tools]
  );

  useEffect(() => {
    void refresh();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

  useEffect(() => {
    if (!selectedToolName && tools[0]) {
      setSelectedToolName(tools[0].name);
    }
    if (selectedToolName && !tools.some((tool) => tool.name === selectedToolName)) {
      setSelectedToolName(tools[0]?.name ?? "");
    }
  }, [selectedToolName, tools]);

  async function refresh() {
    setLoading(true);
    setError(null);
    try {
      const [nextClients, nextKeys, nextUsageSummary, nextUsageEvents, nextTools] = await Promise.all([
        getEngineConnectClients(token),
        getMyEngineApiKeys(token),
        getMyEngineUsageSummary(token),
        getMyEngineUsageEvents(token),
        getMcpToolCatalog(token)
      ]);
      setClients(nextClients);
      setKeys(nextKeys);
      setUsageSummary(nextUsageSummary);
      setUsageEvents(nextUsageEvents);
      setTools(nextTools);
    } catch (err) {
      setError(err instanceof Error ? err.message : "개발자 포털 데이터를 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }

  async function handleCreate(body: {
    clientType: EngineConnectClientType;
    displayName?: string | null;
    officeId?: number | null;
    expiresAt?: string | null;
  }) {
    setBusy(true);
    setError(null);
    try {
      const created = await createEngineConnectBootstrap(token, body);
      setBootstrap(created);
      await refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : "MCP 연결 패키지를 생성하지 못했습니다.");
    } finally {
      setBusy(false);
    }
  }

  async function handleRevoke(key: EngineApiKey) {
    if (!window.confirm(`${key.displayName} 키를 폐기할까요? 폐기 후 외부 Agent/MCP 호출에 사용할 수 없습니다.`)) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await revokeMyEngineApiKey(token, key.id);
      await refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Engine API Key를 폐기하지 못했습니다.");
    } finally {
      setBusy(false);
    }
  }

  async function handleRunSmoke(apiKey: string) {
    setBusy(true);
    setError(null);
    try {
      const result = await runMyMcpLiveSmoke(token, apiKey);
      setLiveSmokeResult(result);
      await refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : "MCP 연결 테스트를 실행하지 못했습니다.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="view-stack developer-portal">
      <ViewHeader
        title="개발자 / MCP 연결"
        text="내 AI 도구나 외부 Agent가 ArchDox Engine/MCP를 안전하게 호출하도록 연결 키와 사용량을 관리합니다."
      />
      {error ? <InlineAlert message={error} /> : null}
      {loading ? (
        <Panel title="불러오는 중">
          <div className="developer-loading">
            <Loader2 className="spin" size={18} />
            <span>Engine/MCP 연결 정보를 준비하고 있습니다.</span>
          </div>
        </Panel>
      ) : (
        <>
          <div className="metric-row compact">
            <MetricTile label="내 API Key" value={keys.length} detail={`${activeKeys.length}개 활성`} />
            <MetricTile label="Request units" value={usageSummary?.totalRequestUnits ?? 0} detail="최근 30일 사용량" />
            <MetricTile label="MCP calls" value={mcpEvents.length} detail="최근 호출 로그 기준" />
            <MetricTile label="실패 호출" value={failedEvents.length} detail="quota/scope/auth 오류 포함" />
          </div>

          <Panel title="MCP 연결 만들기">
            <DeveloperConnectForm
              busy={busy}
              clients={clients}
              offices={offices}
              selectedOfficeId={selectedOfficeId}
              onSubmit={handleCreate}
            />
            {bootstrap ? (
              <EngineConnectResult bootstrap={bootstrap} onDismiss={() => setBootstrap(null)} />
            ) : (
              <InlineNotice message="연결 패키지를 만들면 새 Engine API Key와 MCP 설정이 함께 생성됩니다. API Key 원문은 이 화면에서 한 번만 표시됩니다." />
            )}
          </Panel>

          <Panel title="MCP 연결 테스트">
            <McpLiveSmokePanel
              busy={busy}
              defaultApiKey={bootstrap?.apiKey ?? ""}
              result={liveSmokeResult}
              onRun={handleRunSmoke}
            />
          </Panel>

          <Panel title="내 Engine API Key">
            <EngineKeyList busy={busy} keys={keys} offices={offices} onRevoke={handleRevoke} />
          </Panel>

          <Panel title="내 Engine / MCP 사용량">
            <UsagePanel summary={usageSummary} events={usageEvents} />
          </Panel>

          <Panel title="MCP Tool Catalog">
            <McpToolCatalog tools={tools} selectedTool={selectedTool} onSelect={setSelectedToolName} />
          </Panel>
        </>
      )}
    </div>
  );
}

function DeveloperConnectForm({
  busy,
  clients,
  offices,
  selectedOfficeId,
  onSubmit
}: {
  busy: boolean;
  clients: EngineConnectClient[];
  offices: Office[];
  selectedOfficeId: number | null;
  onSubmit: (body: {
    clientType: EngineConnectClientType;
    displayName?: string | null;
    officeId?: number | null;
    expiresAt?: string | null;
  }) => Promise<void>;
}) {
  const [clientType, setClientType] = useState<EngineConnectClientType>("CODEX");
  const [displayName, setDisplayName] = useState("");
  const [officeId, setOfficeId] = useState<number | "">(selectedOfficeId ?? "");
  const [expiresAt, setExpiresAt] = useState("");

  useEffect(() => {
    if (clients.length > 0 && !clients.some((client) => client.type === clientType)) {
      setClientType(clients[0].type);
    }
  }, [clientType, clients]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    await onSubmit({
      clientType,
      displayName: normalizeText(displayName),
      officeId: officeId === "" ? null : officeId,
      expiresAt: expiresAt ? new Date(expiresAt).toISOString() : null
    });
  }

  if (clients.length === 0) {
    return <EmptyState title="연결 대상 없음" text="사용 가능한 MCP 연결 클라이언트 목록을 불러오지 못했습니다." />;
  }

  return (
    <form className="developer-connect-form" onSubmit={submit}>
      <label>
        연결 대상
        <select value={clientType} onChange={(event) => setClientType(event.target.value as EngineConnectClientType)}>
          {clients.map((client) => (
            <option key={client.type} value={client.type}>
              {client.displayName}
            </option>
          ))}
        </select>
      </label>
      <label>
        표시 이름
        <input
          placeholder="Codex MCP connection"
          value={displayName}
          onChange={(event) => setDisplayName(event.target.value)}
        />
      </label>
      <label>
        사무소 연결
        <select value={officeId} onChange={(event) => setOfficeId(event.target.value ? Number(event.target.value) : "")}>
          <option value="">개인 / 사무소 미지정</option>
          {offices.map((office) => (
            <option key={office.id} value={office.id}>
              {office.displayName} / {office.officeCode}
            </option>
          ))}
        </select>
      </label>
      <label>
        만료일
        <input type="datetime-local" value={expiresAt} onChange={(event) => setExpiresAt(event.target.value)} />
      </label>
      <div className="developer-connect-note">
        기본 scope는 문서 검토, 법령 변경 조회, 법령 검색입니다. 더 넓은 scope나 quota는 운영 정책에 맞춰 별도 승인으로 확장합니다.
      </div>
      <button className="primary-button" disabled={busy} type="submit">
        {busy ? <Loader2 className="spin" size={17} /> : <PlugZap size={17} />}
        연결 패키지 생성
      </button>
    </form>
  );
}

function EngineConnectResult({
  bootstrap,
  onDismiss
}: {
  bootstrap: EngineConnectBootstrapResponse;
  onDismiss: () => void;
}) {
  return (
    <div className="developer-connect-result">
      <div className="developer-result-header">
        <div>
          <strong>{bootstrap.displayName}</strong>
          <span>{bootstrap.clientType} / {bootstrap.connectionId}</span>
        </div>
        <button className="secondary-button" onClick={onDismiss} type="button">
          확인
        </button>
      </div>
      <InlineAlert message="API Key 원문은 지금 한 번만 표시됩니다. 외부 AI 도구 설정에 넣은 뒤 안전한 곳에 보관하세요." />
      <CopyableCodeBlock title="Engine API Key" value={bootstrap.apiKey} />
      <CopyableCodeBlock title="MCP endpoint" value={bootstrap.mcpServerUrl} />
      <CopyableCodeBlock title="MCP client config" value={JSON.stringify(bootstrap.suggestedMcpConfig, null, 2)} />
      <CopyableCodeBlock title="REST smoke curl" value={bootstrap.curlExample} />
      <div className="developer-next-steps">
        <strong>다음 단계</strong>
        {bootstrap.nextSteps.map((step) => (
          <span key={step}>{step}</span>
        ))}
      </div>
    </div>
  );
}

function McpLiveSmokePanel({
  busy,
  defaultApiKey,
  result,
  onRun
}: {
  busy: boolean;
  defaultApiKey: string;
  result: McpLiveSmokeResult | null;
  onRun: (apiKey: string) => Promise<void>;
}) {
  const [apiKey, setApiKey] = useState(defaultApiKey);

  useEffect(() => {
    if (defaultApiKey) {
      setApiKey(defaultApiKey);
    }
  }, [defaultApiKey]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    await onRun(apiKey);
  }

  return (
    <div className="developer-smoke-panel">
      <form className="developer-smoke-form" onSubmit={submit}>
        <label>
          Engine API Key 원문
          <input
            autoComplete="off"
            onChange={(event) => setApiKey(event.target.value)}
            placeholder="adx_live_..."
            type="password"
            value={apiKey}
          />
        </label>
        <button className="primary-button" disabled={busy || !apiKey.trim()} type="submit">
          {busy ? <Loader2 className="spin" size={17} /> : <Activity size={17} />}
          연결 테스트
        </button>
        <div className="developer-connect-note">
          이 값은 저장하지 않고 MCP endpoint 호출에만 사용합니다. 현재 로그인 계정 소유의 키만 테스트할 수 있습니다.
        </div>
      </form>
      {result ? (
        <div className="developer-smoke-result">
          <div className="developer-result-header">
            <div>
              <strong>{result.success ? "MCP 연결 정상" : "MCP 연결 확인 필요"}</strong>
              <span>{result.endpoint} · {result.elapsedMs}ms</span>
            </div>
            <StatusBadge status={result.status} />
          </div>
          <div className="developer-smoke-steps">
            {result.steps.map((step) => (
              <div key={`${step.step}-${step.method}`}>
                <StatusBadge status={step.status} />
                <div>
                  <strong>{mcpSmokeStepLabel(step.step)}</strong>
                  <span>{step.summary}</span>
                  {step.errorCode ? <small>{step.errorCode}{step.errorCategory ? ` / ${step.errorCategory}` : ""}</small> : null}
                </div>
                <small>{step.httpStatus || "-"} · {step.elapsedMs}ms</small>
              </div>
            ))}
          </div>
        </div>
      ) : (
        <InlineNotice message="initialize, tools/list, get_legal_updates, search_law 순서로 실제 MCP endpoint를 호출합니다." />
      )}
    </div>
  );
}

function EngineKeyList({
  busy,
  keys,
  offices,
  onRevoke
}: {
  busy: boolean;
  keys: EngineApiKey[];
  offices: Office[];
  onRevoke: (key: EngineApiKey) => void;
}) {
  if (keys.length === 0) {
    return <EmptyState title="발급된 키 없음" text="MCP 연결 패키지를 만들면 내 API Key 목록에 표시됩니다." />;
  }

  return (
    <div className="developer-key-list">
      {keys.map((key) => (
        <article className="developer-list-row" key={key.id}>
          <div className="developer-row-icon">
            <KeyRound size={18} />
          </div>
          <div>
            <strong>{key.displayName}</strong>
            <span>{key.maskedKey} / #{key.id}</span>
            <small>{officeLabel(offices, key.officeId)} · {key.scopes.join(", ")}</small>
          </div>
          <StatusBadge status={key.status} />
          <div className="developer-row-meta">
            <span>최근 사용 {formatDate(key.lastUsedAt)}</span>
            <span>만료 {formatDate(key.expiresAt)}</span>
          </div>
          {key.status === "ACTIVE" ? (
            <button className="danger-button" disabled={busy} onClick={() => onRevoke(key)} type="button">
              <XCircle size={16} />
              폐기
            </button>
          ) : null}
        </article>
      ))}
    </div>
  );
}

function UsagePanel({ summary, events }: { summary: EngineApiUsageSummary | null; events: EngineApiUsageEvent[] }) {
  const groups = summary?.groups ?? [];
  return (
    <div className="developer-usage-grid">
      <div className="developer-table-card">
        <div className="developer-section-title">
          <Activity size={16} />
          <strong>Capability별 사용량</strong>
        </div>
        {groups.length === 0 ? (
          <EmptyState title="사용량 없음" text="아직 Engine/MCP 호출 기록이 없습니다." />
        ) : (
          <div className="developer-mini-table">
            {groups.slice(0, 12).map((group) => (
              <div key={`${group.apiKeyId}-${group.capability}-${group.operation}`}>
                <span>{capabilityLabel(group.capability)}</span>
                <strong>{group.operation}</strong>
                <small>{group.eventCount.toLocaleString()} calls · {group.requestUnits.toLocaleString()} units</small>
              </div>
            ))}
          </div>
        )}
      </div>
      <div className="developer-table-card">
        <div className="developer-section-title">
          <Command size={16} />
          <strong>최근 호출</strong>
        </div>
        {events.length === 0 ? (
          <EmptyState title="호출 로그 없음" text="외부 Agent가 MCP 또는 Engine API를 호출하면 여기에 표시됩니다." />
        ) : (
          <div className="developer-mini-table">
            {events.slice(0, 12).map((event) => (
              <div key={event.id}>
                <span>{formatDate(event.createdAt)} · {usageEventSource(event)}</span>
                <strong>{usageEventTool(event)}</strong>
                <small>{event.status} · {event.requestUnits} units · {event.reviewSessionId ?? event.keyId}</small>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

function McpToolCatalog({
  tools,
  selectedTool,
  onSelect
}: {
  tools: McpToolCatalogItem[];
  selectedTool: McpToolCatalogItem | null;
  onSelect: (name: string) => void;
}) {
  if (tools.length === 0) {
    return <EmptyState title="Tool 없음" text="MCP Tool Catalog를 불러오지 못했습니다." />;
  }

  return (
    <div className="developer-tool-layout">
      <div className="developer-tool-list">
        {tools.map((tool) => (
          <button
            className={selectedTool?.name === tool.name ? "developer-tool-item active" : "developer-tool-item"}
            key={tool.name}
            onClick={() => onSelect(tool.name)}
            type="button"
          >
            <div>
              <strong>{mcpToolTitle(tool)}</strong>
              <span>{tool.name}</span>
            </div>
            <small>{capabilityLabel(tool.capability)} · {tool.baseRequestUnits} unit</small>
          </button>
        ))}
      </div>
      <div className="developer-tool-detail">
        {selectedTool ? (
          <>
            <div className="developer-result-header">
              <div>
                <strong>{mcpToolTitle(selectedTool)}</strong>
                <span>{selectedTool.name}</span>
              </div>
              <StatusBadge status={selectedTool.status} />
            </div>
            <p>{mcpToolDescription(selectedTool)}</p>
            <div className="developer-tool-facts">
              <div>
                <span>필요 Scope</span>
                <strong>{selectedTool.requiredScope}</strong>
              </div>
              <div>
                <span>Access</span>
                <strong>{accessModeLabel(selectedTool.accessMode)}</strong>
              </div>
              <div>
                <span>Usage</span>
                <strong>{selectedTool.usageMetering}</strong>
              </div>
              <div>
                <span>Operation</span>
                <strong>{selectedTool.operation}</strong>
              </div>
            </div>
            <InlineNotice message={selectedTool.boundary} />
            <CopyableCodeBlock title="Example arguments" value={JSON.stringify(selectedTool.exampleArguments, null, 2)} />
          </>
        ) : null}
      </div>
    </div>
  );
}

function CopyableCodeBlock({ title, value }: { title: string; value: string }) {
  return (
    <div className="developer-copy-block">
      <div>
        <span>{title}</span>
        <button className="icon-button" onClick={() => void navigator.clipboard.writeText(value)} type="button" title={`${title} 복사`}>
          <Copy size={14} />
        </button>
      </div>
      <pre>{value}</pre>
    </div>
  );
}

const toolTitles: Record<string, string> = {
  create_review_session: "검토 세션 생성",
  submit_document: "문서 본문 제출",
  submit_context_facts: "문맥 사실 제출",
  normalize_context: "문맥 정규화",
  run_validation: "검증 실행",
  get_review_result: "검토 결과 조회",
  validate_inspection_report: "감리/검사 리포트 일괄 검증",
  get_legal_updates: "법령 변경사항 조회",
  explain_legal_change: "법령 변경 상세 설명",
  search_law: "법령 검색",
  get_law_article: "법령 조문 조회"
};

const toolDescriptions: Record<string, string> = {
  validate_inspection_report: "외부 Agent가 리포트 입력을 넘기면 ArchDox가 문서 검토, 근거 법령, 다음 조치를 같은 결과 형식으로 반환합니다.",
  get_legal_updates: "게시된 법령 변경 digest를 조회합니다. 법령 원문을 바꾸지 않고 사용자에게 보여줄 변경 목록만 읽습니다.",
  explain_legal_change: "특정 법령 변경 digest의 원천 diff와 요약을 상세히 읽습니다.",
  search_law: "ArchDox가 동기화한 법령 코퍼스에서 후보 조문을 검색합니다. 후보 검색만으로 최종 법률 판단을 내리지 않습니다.",
  get_law_article: "동기화된 법령 조문 버전을 source-backed 근거로 조회합니다."
};

function mcpToolTitle(tool: McpToolCatalogItem) {
  return toolTitles[tool.name] ?? tool.title ?? tool.name;
}

function mcpToolDescription(tool: McpToolCatalogItem) {
  return toolDescriptions[tool.name] ?? tool.description;
}

function accessModeLabel(value: string) {
  if (value === "READ") {
    return "조회";
  }
  if (value === "WRITE") {
    return "쓰기";
  }
  return value;
}

function mcpSmokeStepLabel(step: string) {
  const labels: Record<string, string> = {
    initialize: "MCP 초기화",
    "tools/list": "Tool 목록",
    get_legal_updates: "법령 변경 조회",
    search_law: "법령 검색"
  };
  return labels[step] ?? step;
}

function capabilityLabel(value: string) {
  const labels: Record<string, string> = {
    ENGINE_REVIEW_SESSION: "문서 검토",
    LEGAL_UPDATES: "법령 변경",
    LEGAL_SEARCH: "법령 검색"
  };
  return labels[value] ?? value;
}

function officeLabel(offices: Office[], officeId?: number | null) {
  if (!officeId) {
    return "사무소 미지정";
  }
  return offices.find((office) => office.id === officeId)?.displayName ?? `Office #${officeId}`;
}

function usageEventSource(event: EngineApiUsageEvent) {
  const source = metadataText(event.metadata, "source");
  if (source) {
    return source;
  }
  if (event.capability.startsWith("LEGAL_")) {
    return "LEGAL_API";
  }
  if (event.capability === "ENGINE_REVIEW_SESSION") {
    return "ENGINE_REST";
  }
  return "ENGINE_API";
}

function usageEventTool(event: EngineApiUsageEvent) {
  return metadataText(event.metadata, "toolName") || event.operation;
}

function metadataText(metadata: Record<string, unknown> | undefined, key: string) {
  const value = metadata?.[key];
  if (value === undefined || value === null) {
    return "";
  }
  return String(value);
}

function formatDate(value?: string | null) {
  if (!value) {
    return "-";
  }
  return new Intl.DateTimeFormat("ko-KR", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit"
  }).format(new Date(value));
}

function normalizeText(value: string) {
  const trimmed = value.trim();
  return trimmed.length === 0 ? null : trimmed;
}
