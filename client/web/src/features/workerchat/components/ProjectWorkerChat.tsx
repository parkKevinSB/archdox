import { Bot, CheckCircle2, Loader2, MessageSquare, SendHorizontal, Sparkles, Square } from "lucide-react";
import { FormEvent, useEffect, useMemo, useRef, useState } from "react";
import { EmptyState, InlineAlert, Panel, StatusBadge, ViewHeader } from "../../../components/common";
import type { Project } from "../../../types";
import { cancelWorkerChatAction, openWorkerChatSession, sendWorkerChatMessage } from "../api";
import type {
  WorkerChatChoice,
  WorkerChatMessage,
  WorkerChatPlannerProposal,
  WorkerChatSession,
  WorkerChatWorkflowState,
  WorkerChatWorkflowStep
} from "../types";

type ProjectWorkerChatProps = {
  officeId: number | null;
  project: Project | null;
  token: string;
  onOpenDocuments?: () => void;
  onSelectProject: () => void;
  onSessionSync?: (session: WorkerChatSession) => void;
};

const stageLabels: Record<string, string> = {
  AWAITING_SITE: "현장 선택",
  AWAITING_REPORT: "리포트 선택",
  REPORT_WORKING: "리포트 작성",
  REVIEWING: "검토",
  SIGNING: "서명",
  GENERATING_DOCUMENT: "문서 생성",
  COMPLETED: "완료"
};

export function ProjectWorkerChat({ officeId, project, token, onOpenDocuments, onSelectProject, onSessionSync }: ProjectWorkerChatProps) {
  const [session, setSession] = useState<WorkerChatSession | null>(null);
  const [message, setMessage] = useState("");
  const [siteName, setSiteName] = useState("");
  const [siteAddress, setSiteAddress] = useState("");
  const [reportTitle, setReportTitle] = useState("");
  const [selectedStepCode, setSelectedStepCode] = useState("");
  const [loading, setLoading] = useState(false);
  const [sending, setSending] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const bottomRef = useRef<HTMLDivElement | null>(null);
  const composerTextareaRef = useRef<HTMLTextAreaElement | null>(null);
  const hasPendingReply = useMemo(
    () => session?.messages.some((item) => item.role === "ASSISTANT" && item.status === "PENDING") ?? false,
    [session]
  );
  const latestAssistant = useMemo(
    () =>
      [...(session?.messages ?? [])]
        .reverse()
        .find((item) => item.role === "ASSISTANT" && item.status === "COMPLETED") ?? null,
    [session]
  );
  const latestPendingAssistant = useMemo(
    () =>
      [...(session?.messages ?? [])]
        .reverse()
        .find((item) => item.role === "ASSISTANT" && item.status === "PENDING") ?? null,
    [session]
  );
  const nextAction = typeof latestAssistant?.metadata?.nextAction === "string" ? latestAssistant.metadata.nextAction : null;
  const workflowSteps = useMemo(() => extractWorkflowSteps(latestAssistant?.metadata ?? {}), [latestAssistant]);
  const nextStepCode = typeof latestAssistant?.metadata?.nextStepCode === "string" ? latestAssistant.metadata.nextStepCode : "";
  const plannerSuggestedPayload = useMemo(
    () => recordValue(latestAssistant?.metadata?.plannerSuggestedPayload),
    [latestAssistant]
  );
  const selectedWorkflowStep = workflowSteps.find((step) => step.code === selectedStepCode) ?? workflowSteps[0] ?? null;
  const processingText = processingStatusText({ cancelling, loading, sending, pendingMessage: latestPendingAssistant });
  const documentTabAvailable = latestAssistant?.metadata?.documentTabAvailable === true;
  const workflowState = session?.workflowState ?? {};
  const effectiveNextAction = workflowState.documentJobActive || workflowState.documentGenerated
    ? "OPEN_DOCUMENTS"
    : workflowState.canRequestDocumentGeneration
      ? "REQUEST_DOCUMENT_GENERATION"
      : nextAction;
  const shouldPollSession = hasPendingReply
    || workflowState.preflightActive === true
    || workflowState.documentJobActive === true;

  function syncSession(nextSession: WorkerChatSession) {
    setSession(nextSession);
    onSessionSync?.(nextSession);
  }

  useEffect(() => {
    resizeComposerTextarea(composerTextareaRef.current);
  }, [message]);

  useEffect(() => {
    if (!plannerSuggestedPayload) {
      return;
    }
    if (nextAction === "CREATE_SITE") {
      const suggestedName = typeof plannerSuggestedPayload.name === "string" ? plannerSuggestedPayload.name : "";
      const suggestedAddress = typeof plannerSuggestedPayload.address === "string" ? plannerSuggestedPayload.address : "";
      if (suggestedName && !siteName) {
        setSiteName(suggestedName);
      }
      if (suggestedAddress && !siteAddress) {
        setSiteAddress(suggestedAddress);
      }
    }
    if (nextAction === "CREATE_REPORT") {
      const suggestedTitle = typeof plannerSuggestedPayload.title === "string" ? plannerSuggestedPayload.title : "";
      if (suggestedTitle && !reportTitle) {
        setReportTitle(suggestedTitle);
      }
    }
  }, [nextAction, plannerSuggestedPayload, reportTitle, siteAddress, siteName]);

  useEffect(() => {
    if (workflowSteps.length === 0) {
      setSelectedStepCode("");
      return;
    }
    if (!selectedStepCode || !workflowSteps.some((step) => step.code === selectedStepCode)) {
      setSelectedStepCode(nextStepCode || workflowSteps[0].code);
    }
  }, [nextStepCode, selectedStepCode, workflowSteps]);

  useEffect(() => {
    if (!officeId || !project) {
      setSession(null);
      return;
    }
    let cancelled = false;
    setLoading(true);
    setError(null);
    openWorkerChatSession(token, officeId, project.id)
      .then((nextSession) => {
        if (!cancelled) {
          syncSession(nextSession);
        }
      })
      .catch((err) => {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "채팅을 불러오지 못했습니다.");
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [officeId, project?.id, token]);

  useEffect(() => {
    if (!officeId || !project || !shouldPollSession) {
      return;
    }
    const timer = window.setInterval(() => {
      openWorkerChatSession(token, officeId, project.id)
        .then(syncSession)
        .catch(() => undefined);
    }, 1200);
    return () => window.clearInterval(timer);
  }, [officeId, project?.id, shouldPollSession, token]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ block: "end" });
  }, [session?.messages.length, hasPendingReply]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!officeId || !project || !message.trim()) {
      return;
    }
    const content = message.trim();
    setSending(true);
    setError(null);
    setMessage("");
    try {
      const payload =
        session?.stage === "REPORT_WORKING"
          ? {
              content,
              updateReportStep: {
                stepCode: selectedWorkflowStep?.code,
                payload: workerStepPayload(selectedWorkflowStep, content)
              }
            }
          : content;
      const nextSession = await sendWorkerChatMessage(token, officeId, project.id, payload);
      syncSession(nextSession);
    } catch (err) {
      setMessage(content);
      setError(err instanceof Error ? err.message : "메시지를 보내지 못했습니다.");
    } finally {
      setSending(false);
    }
  }

  async function cancelActiveAction() {
    if (!officeId || !project || !hasPendingReply) {
      return;
    }
    setCancelling(true);
    setError(null);
    try {
      const nextSession = await cancelWorkerChatAction(token, officeId, project.id);
      syncSession(nextSession);
    } catch (err) {
      setError(err instanceof Error ? err.message : "진행 중인 작업을 취소하지 못했습니다.");
    } finally {
      setCancelling(false);
    }
  }

  async function selectChoice(choice: WorkerChatChoice) {
    if (!officeId || !project) {
      return;
    }
    setSending(true);
    setError(null);
    try {
      const nextSession = await sendWorkerChatMessage(token, officeId, project.id, {
        content: `${choice.kind === "SITE" ? "현장" : "리포트"} 선택: ${choice.label}`,
        siteId: choice.kind === "SITE" ? choice.id : undefined,
        reportId: choice.kind === "REPORT" ? choice.id : undefined
      });
      syncSession(nextSession);
    } catch (err) {
      setError(err instanceof Error ? err.message : "선택을 처리하지 못했습니다.");
    } finally {
      setSending(false);
    }
  }

  async function createSite(event: FormEvent) {
    event.preventDefault();
    if (!officeId || !project || !siteName.trim()) {
      return;
    }
    setSending(true);
    setError(null);
    try {
      const nextSession = await sendWorkerChatMessage(token, officeId, project.id, {
        content: `현장 생성: ${siteName.trim()}`,
        createSite: {
          name: siteName.trim(),
          address: siteAddress.trim() || undefined,
          siteType: "CONSTRUCTION_SITE"
        }
      });
      syncSession(nextSession);
      setSiteName("");
      setSiteAddress("");
    } catch (err) {
      setError(err instanceof Error ? err.message : "현장을 생성하지 못했습니다.");
    } finally {
      setSending(false);
    }
  }

  async function createReport(event: FormEvent) {
    event.preventDefault();
    if (!officeId || !project || !reportTitle.trim()) {
      return;
    }
    setSending(true);
    setError(null);
    try {
      const nextSession = await sendWorkerChatMessage(token, officeId, project.id, {
        content: `리포트 생성: ${reportTitle.trim()}`,
        createReport: {
          title: reportTitle.trim(),
          reportType: "CONSTRUCTION_DAILY_SUPERVISION_LOG"
        }
      });
      syncSession(nextSession);
      setReportTitle("");
    } catch (err) {
      setError(err instanceof Error ? err.message : "리포트를 생성하지 못했습니다.");
    } finally {
      setSending(false);
    }
  }

  async function submitReport() {
    if (!officeId || !project || !session?.reportId) {
      setError("제출할 리포트를 먼저 선택해주세요.");
      return;
    }
    setSending(true);
    setError(null);
    try {
      const nextSession = await sendWorkerChatMessage(token, officeId, project.id, {
        content: "리포트 제출",
        submitReport: {
          reportId: session.reportId
        }
      });
      syncSession(nextSession);
    } catch (err) {
      setError(err instanceof Error ? err.message : "리포트를 제출하지 못했습니다.");
    } finally {
      setSending(false);
    }
  }

  async function runPreflightReview() {
    if (!officeId || !project || !session?.reportId) {
      setError("검토할 리포트를 먼저 선택해주세요.");
      return;
    }
    setSending(true);
    setError(null);
    try {
      const nextSession = await sendWorkerChatMessage(token, officeId, project.id, {
        content: "문서 생성 전 검토 실행",
        runPreflightReview: {
          reportId: session.reportId
        }
      });
      syncSession(nextSession);
    } catch (err) {
      setError(err instanceof Error ? err.message : "문서 생성 전 검토를 실행하지 못했습니다.");
    } finally {
      setSending(false);
    }
  }

  async function requestDocumentGeneration() {
    if (!officeId || !project || !session?.reportId) {
      setError("문서를 생성할 리포트를 먼저 선택해주세요.");
      return;
    }
    setSending(true);
    setError(null);
    try {
      const nextSession = await sendWorkerChatMessage(token, officeId, project.id, {
        content: "문서 생성 요청",
        requestDocumentGeneration: {
          reportId: session.reportId,
          outputFormat: "DOCX"
        }
      });
      syncSession(nextSession);
    } catch (err) {
      setError(err instanceof Error ? err.message : "문서 생성을 요청하지 못했습니다.");
    } finally {
      setSending(false);
    }
  }

  async function executePlannerProposal(proposal: WorkerChatPlannerProposal) {
    if (!officeId || !project || proposal.decision !== "PROPOSE_ACTION") {
      return;
    }
    const payload = plannerExecutionPayload(
      proposal,
      {
        siteName,
        siteAddress,
        reportTitle,
        selectedStep: selectedWorkflowStep
      }
    );
    if (!payload) {
      setError("제안을 실행하기 위해 필요한 정보가 부족합니다. 입력값을 확인해주세요.");
      return;
    }
    setSending(true);
    setError(null);
    try {
      const nextSession = await sendWorkerChatMessage(token, officeId, project.id, payload);
      syncSession(nextSession);
      if (proposal.actionType === "CREATE_SITE") {
        setSiteName("");
        setSiteAddress("");
      }
      if (proposal.actionType === "CREATE_REPORT") {
        setReportTitle("");
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "제안을 실행하지 못했습니다.");
    } finally {
      setSending(false);
    }
  }

  if (!project) {
    return (
      <div className="view-stack">
        <ViewHeader title="채팅" text="ArchDox Worker는 선택한 프로젝트의 실제 업무 흐름 안에서만 동작합니다." />
        <Panel
          title="프로젝트 선택 필요"
          action={
            <button className="primary-button" onClick={onSelectProject} type="button">
              프로젝트 선택
            </button>
          }
        >
          <EmptyState
            title="먼저 프로젝트를 선택하세요"
            text="채팅은 프로젝트 안에서 현장, 리포트, 검토, 문서 생성 순서로 진행되는 임시 작업 세션입니다."
          />
        </Panel>
      </div>
    );
  }

  return (
    <div className="view-stack worker-chat-view">
      <ViewHeader title="채팅" text={`${project.name} 프로젝트의 리포트 작업을 대화형으로 진행합니다.`} />
      {error ? <InlineAlert message={error} /> : null}
      <Panel
        title="리포트 작업 세션"
        action={
          <div className="worker-chat-context">
            <StatusBadge status={session?.status ?? "ACTIVE"} />
            {session?.stage ? <span>{stageLabels[session.stage] ?? session.stage}</span> : null}
          </div>
        }
      >
        <div className="worker-chat-shell">
          <WorkerChatProcessingBar text={processingText} />
          <div className="worker-chat-messages" aria-live="polite">
            {loading ? (
              <div className="worker-chat-loading">
                <Loader2 className="spin" size={18} />
                채팅을 불러오는 중입니다.
              </div>
            ) : session && session.messages.length > 0 ? (
              session.messages.map((item) => (
                <article className={`worker-chat-message ${item.role.toLowerCase()}`} key={item.id}>
                  <div className="worker-chat-avatar">
                    {item.role === "ASSISTANT" ? <Bot size={16} /> : <MessageSquare size={16} />}
                  </div>
                  <div className="worker-chat-bubble">
                    <div className="worker-chat-meta">
                      <strong>{item.role === "ASSISTANT" ? "ArchDox Worker" : "나"}</strong>
                      {item.status === "PENDING" ? <span>작업 중</span> : null}
                      {item.status === "FAILED" ? <span>실패</span> : null}
                      {item.status === "CANCELLED" ? <span>취소됨</span> : null}
                    </div>
                    <p>{item.content}</p>
                    <WorkerChatChoices disabled={sending || hasPendingReply} message={item} onSelect={selectChoice} />
                    <WorkerChatPlannerProposalCard
                      active={latestAssistant?.id === item.id}
                      disabled={sending || hasPendingReply}
                      message={item}
                      onExecute={executePlannerProposal}
                    />
                  </div>
                </article>
              ))
            ) : (
              <EmptyState
                title="아직 대화가 없습니다"
                text="현재 프로젝트에서 진행할 현장과 리포트를 차례로 선택하면서 작업을 시작합니다."
              />
            )}
            <div ref={bottomRef} />
          </div>
          <WorkerChatActionPanel
            disabled={sending || hasPendingReply}
            documentTabAvailable={documentTabAvailable}
            nextAction={effectiveNextAction}
            onCreateReport={createReport}
            onCreateSite={createSite}
            onOpenDocuments={onOpenDocuments}
            onRequestDocumentGeneration={requestDocumentGeneration}
            onRunPreflightReview={runPreflightReview}
            onSubmitReport={submitReport}
            reportTitle={reportTitle}
            session={session}
            selectedStepCode={selectedStepCode}
            setReportTitle={setReportTitle}
            setSelectedStepCode={setSelectedStepCode}
            setSiteAddress={setSiteAddress}
            setSiteName={setSiteName}
            siteAddress={siteAddress}
            siteName={siteName}
            workflowState={workflowState}
            workflowSteps={workflowSteps}
          />
          <form className="worker-chat-composer" onSubmit={submit}>
            <textarea
              ref={composerTextareaRef}
              disabled={sending || hasPendingReply}
              onChange={(event) => {
                setMessage(event.target.value);
                resizeComposerTextarea(event.currentTarget);
              }}
              placeholder={session?.stage === "REPORT_WORKING" ? "선택한 작성 단계에 저장할 내용을 입력하세요." : "예: 오늘 감리일지 작성을 시작하고 싶습니다."}
              rows={2}
              value={message}
            />
            <button
              aria-label={hasPendingReply ? "정지" : "보내기"}
              className={hasPendingReply ? "worker-chat-send-button cancel" : "worker-chat-send-button"}
              disabled={hasPendingReply ? cancelling : sending || !message.trim()}
              onClick={hasPendingReply ? cancelActiveAction : undefined}
              title={hasPendingReply ? "정지" : "보내기"}
              type={hasPendingReply ? "button" : "submit"}
            >
              {hasPendingReply
                ? cancelling
                  ? <Loader2 className="spin" size={17} />
                  : <Square size={15} />
                : sending
                  ? <Loader2 className="spin" size={17} />
                  : <SendHorizontal size={17} />}
            </button>
          </form>
        </div>
      </Panel>
    </div>
  );
}

function resizeComposerTextarea(element: HTMLTextAreaElement | null) {
  if (!element) {
    return;
  }
  const style = window.getComputedStyle(element);
  const lineHeight = Number.parseFloat(style.lineHeight) || 20;
  const paddingTop = Number.parseFloat(style.paddingTop) || 0;
  const paddingBottom = Number.parseFloat(style.paddingBottom) || 0;
  const borderTop = Number.parseFloat(style.borderTopWidth) || 0;
  const borderBottom = Number.parseFloat(style.borderBottomWidth) || 0;
  const minHeight = Math.ceil(lineHeight * 2 + paddingTop + paddingBottom + borderTop + borderBottom);
  const maxHeight = Math.ceil(lineHeight * 4 + paddingTop + paddingBottom + borderTop + borderBottom);
  element.style.height = "auto";
  element.style.height = `${Math.min(Math.max(element.scrollHeight, minHeight), maxHeight)}px`;
  element.style.overflowY = element.scrollHeight > maxHeight ? "auto" : "hidden";
}

function WorkerChatActionPanel({
  disabled,
  documentTabAvailable,
  nextAction,
  onCreateReport,
  onCreateSite,
  onOpenDocuments,
  onRequestDocumentGeneration,
  onRunPreflightReview,
  onSubmitReport,
  reportTitle,
  session,
  selectedStepCode,
  setReportTitle,
  setSelectedStepCode,
  setSiteAddress,
  setSiteName,
  siteAddress,
  siteName,
  workflowState,
  workflowSteps
}: {
  disabled: boolean;
  documentTabAvailable: boolean;
  nextAction: string | null;
  onCreateReport: (event: FormEvent) => void;
  onCreateSite: (event: FormEvent) => void;
  onOpenDocuments?: () => void;
  onRequestDocumentGeneration: () => void;
  onRunPreflightReview: () => void;
  onSubmitReport: () => void;
  reportTitle: string;
  session: WorkerChatSession | null;
  selectedStepCode: string;
  setReportTitle: (value: string) => void;
  setSelectedStepCode: (value: string) => void;
  setSiteAddress: (value: string) => void;
  setSiteName: (value: string) => void;
  siteAddress: string;
  siteName: string;
  workflowState: WorkerChatWorkflowState;
  workflowSteps: WorkerChatWorkflowStep[];
}) {
  if (nextAction === "CREATE_SITE") {
    return (
      <form className="worker-chat-action-panel" onSubmit={onCreateSite}>
        <input disabled={disabled} onChange={(event) => setSiteName(event.target.value)} placeholder="현장명" value={siteName} />
        <input disabled={disabled} onChange={(event) => setSiteAddress(event.target.value)} placeholder="주소 선택 입력" value={siteAddress} />
        <button className="secondary-button" disabled={disabled || !siteName.trim()} type="submit">
          현장 생성
        </button>
      </form>
    );
  }
  if (nextAction === "CREATE_REPORT") {
    return (
      <form className="worker-chat-action-panel" onSubmit={onCreateReport}>
        <input disabled={disabled} onChange={(event) => setReportTitle(event.target.value)} placeholder="리포트 제목" value={reportTitle} />
        <button className="secondary-button" disabled={disabled || !reportTitle.trim()} type="submit">
          리포트 생성
        </button>
      </form>
    );
  }
  if (nextAction === "UPDATE_REPORT_STEP" && workflowSteps.length > 0) {
    const selected = workflowSteps.find((step) => step.code === selectedStepCode) ?? workflowSteps[0];
    return (
      <div className="worker-chat-action-panel worker-chat-step-panel">
        <label>
          <span>작성 단계</span>
          <select disabled={disabled} onChange={(event) => setSelectedStepCode(event.target.value)} value={selectedStepCode || selected.code}>
            {workflowSteps.map((step) => (
              <option key={step.code} value={step.code}>
                {step.saved ? "저장됨 - " : ""}
                {step.title}
              </option>
            ))}
          </select>
        </label>
        <p>{selected.description || "선택한 단계에 채팅 내용을 저장합니다."}</p>
        {session?.reportId ? (
          <button className="secondary-button" disabled={disabled} onClick={onSubmitReport} type="button">
            리포트 제출
          </button>
        ) : null}
      </div>
    );
  }
  if (nextAction === "RUN_PREFLIGHT_REVIEW") {
    const canRunReview = workflowState.canRunPreflightReview !== false;
    return (
      <div className="worker-chat-action-panel worker-chat-finish-panel">
        <p>리포트가 제출되었습니다. 문서 생성 전에 검토를 실행하거나 문서 탭에서 같은 작업을 이어갈 수 있습니다.</p>
        <div className="worker-chat-inline-actions">
          <button className="secondary-button" disabled={disabled || !session?.reportId || !canRunReview} onClick={onRunPreflightReview} type="button">
            검토 실행
          </button>
          {onOpenDocuments ? (
            <button className="primary-button" disabled={disabled} onClick={onOpenDocuments} type="button">
              문서 탭으로 이동
            </button>
          ) : null}
        </div>
      </div>
    );
  }
  if (nextAction === "REQUEST_DOCUMENT_GENERATION") {
    const canGenerate = workflowState.canRequestDocumentGeneration === true;
    const reviewActive = workflowState.preflightActive === true;
    const reviewStatus = workflowState.latestPreflightRun?.status;
    return (
      <div className="worker-chat-action-panel worker-chat-finish-panel">
        <p>
          {canGenerate
            ? "검토 통과. 문서 생성을 요청할 수 있습니다."
            : reviewActive
              ? "검토가 진행 중입니다. 완료되면 문서 생성 버튼이 활성화됩니다."
              : reviewStatus === "NEEDS_ATTENTION"
                ? "검토에서 확인할 항목이 있습니다. 내용을 보완하거나 문서 탭에서 확인해주세요."
                : "최신 리포트 revision의 검토 통과가 필요합니다."}
        </p>
        <div className="worker-chat-inline-actions">
          <button className="secondary-button" disabled={disabled || !session?.reportId} onClick={onRunPreflightReview} type="button">
            검토 다시 실행
          </button>
          <button className="primary-button" disabled={disabled || !session?.reportId || !canGenerate} onClick={onRequestDocumentGeneration} type="button">
            DOCX 생성 요청
          </button>
          {onOpenDocuments ? (
            <button className="secondary-button" disabled={disabled} onClick={onOpenDocuments} type="button">
              문서 탭
            </button>
          ) : null}
        </div>
      </div>
    );
  }
  if (documentTabAvailable || nextAction === "OPEN_DOCUMENTS") {
    return (
      <div className="worker-chat-action-panel worker-chat-finish-panel">
        <p>제출된 리포트는 문서 탭에서 검토하고 문서를 생성할 수 있습니다.</p>
        {onOpenDocuments ? (
          <button className="primary-button" disabled={disabled} onClick={onOpenDocuments} type="button">
            문서 탭으로 이동
          </button>
        ) : null}
      </div>
    );
  }
  return null;
}

function WorkerChatProcessingBar({ text }: { text: string | null }) {
  if (!text) {
    return null;
  }
  return (
    <div className="worker-chat-processing-bar" role="status" aria-live="polite">
      <span />
      <strong>{text}</strong>
    </div>
  );
}

function WorkerChatChoices({
  disabled,
  message,
  onSelect
}: {
  disabled: boolean;
  message: WorkerChatMessage;
  onSelect: (choice: WorkerChatChoice) => void;
}) {
  if (message.role !== "ASSISTANT" || message.status !== "COMPLETED") {
    return null;
  }
  const choices = extractChoices(message.metadata);
  if (choices.length === 0) {
    return null;
  }
  return (
    <div className="worker-chat-choice-list">
      {choices.map((choice) => (
        <button disabled={disabled} key={`${choice.kind}-${choice.id}`} onClick={() => onSelect(choice)} type="button">
          <span>{choice.label}</span>
          {choice.description ? <small>{choice.description}</small> : null}
        </button>
      ))}
    </div>
  );
}

function WorkerChatPlannerProposalCard({
  active,
  disabled,
  message,
  onExecute
}: {
  active: boolean;
  disabled: boolean;
  message: WorkerChatMessage;
  onExecute: (proposal: WorkerChatPlannerProposal) => void;
}) {
  if (message.role !== "ASSISTANT" || message.status !== "COMPLETED") {
    return null;
  }
  const proposal = extractPlannerProposal(message.metadata);
  if (!proposal) {
    return null;
  }
  const executable = proposal.decision === "PROPOSE_ACTION" && Boolean(actionLabel(proposal.actionType));
  const payloadSummary = proposalPayloadSummary(proposal);
  return (
    <div className="worker-chat-planner-card">
      <div className="worker-chat-planner-heading">
        <Sparkles size={15} />
        <span>AI 제안</span>
      </div>
      <div className="worker-chat-planner-main">
        <strong>{proposalTitle(proposal)}</strong>
        <small>{proposal.requiresConfirmation ? "확인 후 실행" : "사용자 실행 필요"}</small>
      </div>
      {proposal.rationale ? <p>{proposal.rationale}</p> : null}
      {payloadSummary ? <small className="worker-chat-planner-summary">{payloadSummary}</small> : null}
      <div className="worker-chat-planner-meta">
        <span>{actionLabel(proposal.actionType) || proposal.actionType || plannerDecisionLabel(proposal.decision)}</span>
        <span>{Math.round(proposal.confidence * 100)}%</span>
      </div>
      {executable && active ? (
        <button className="secondary-button" disabled={disabled} onClick={() => onExecute(proposal)} type="button">
          <CheckCircle2 size={16} />
          확인하고 실행
        </button>
      ) : null}
      {executable && !active ? <small className="worker-chat-planner-note">이전 제안은 다시 실행하지 않습니다.</small> : null}
    </div>
  );
}

function processingStatusText({
  cancelling,
  loading,
  pendingMessage,
  sending
}: {
  cancelling: boolean;
  loading: boolean;
  pendingMessage: WorkerChatMessage | null;
  sending: boolean;
}) {
  if (cancelling) {
    return "작업 취소 중...";
  }
  if (loading) {
    return "채팅을 불러오는 중...";
  }
  if (sending) {
    return "요청을 보내는 중...";
  }
  if (!pendingMessage) {
    return null;
  }
  const actionType = firstText(pendingMessage.workerActionType, pendingMessage.metadata?.actionType);
  switch (actionType) {
    case "CREATE_SITE":
      return "현장을 생성하는 중...";
    case "CREATE_REPORT":
      return "리포트를 생성하는 중...";
    case "UPDATE_REPORT_STEP":
      return "리포트 내용을 저장하는 중...";
    case "SUBMIT_REPORT":
      return "리포트를 제출하는 중...";
    case "RUN_PREFLIGHT_REVIEW":
      return "문서 생성 전 검토를 실행하는 중...";
    case "REQUEST_DOCUMENT_GENERATION":
      return "문서 생성을 요청하는 중...";
    case "WORKER_CHAT_ADVANCE":
      return "다음 작업 흐름을 확인하는 중...";
    default:
      return pendingMessage.content || "작업을 처리하는 중...";
  }
}

function extractChoices(metadata: Record<string, unknown>): WorkerChatChoice[] {
  const value = metadata?.choices;
  if (!Array.isArray(value)) {
    return [];
  }
  return value
    .map((item) => {
      if (!item || typeof item !== "object") {
        return null;
      }
      const entry = item as Record<string, unknown>;
      const kind = entry.kind === "SITE" || entry.kind === "REPORT" ? entry.kind : null;
      const id = typeof entry.id === "number" ? entry.id : Number(entry.id);
      const label = typeof entry.label === "string" ? entry.label : "";
      const description = typeof entry.description === "string" ? entry.description : null;
      if (!kind || !Number.isFinite(id) || !label) {
        return null;
      }
      return { kind, id, label, description } satisfies WorkerChatChoice;
    })
    .filter((item): item is WorkerChatChoice => item !== null);
}

function extractWorkflowSteps(metadata: Record<string, unknown>): WorkerChatWorkflowStep[] {
  const value = metadata?.workflowSteps;
  if (!Array.isArray(value)) {
    return [];
  }
  return value
    .map((item) => {
      if (!item || typeof item !== "object") {
        return null;
      }
      const entry = item as Record<string, unknown>;
      const code = typeof entry.code === "string" ? entry.code : "";
      const title = typeof entry.title === "string" ? entry.title : code;
      if (!code || !title) {
        return null;
      }
      const fieldsValue = entry.fields;
      const fields = Array.isArray(fieldsValue)
        ? fieldsValue
            .map((field) => {
              if (!field || typeof field !== "object") {
                return null;
              }
              const fieldEntry = field as Record<string, unknown>;
              const key = typeof fieldEntry.key === "string" ? fieldEntry.key : "";
              const label = typeof fieldEntry.label === "string" ? fieldEntry.label : key;
              if (!key || !label) {
                return null;
              }
              return {
                key,
                label,
                type: typeof fieldEntry.type === "string" ? fieldEntry.type : null,
                placeholder: typeof fieldEntry.placeholder === "string" ? fieldEntry.placeholder : null,
                required: fieldEntry.required === true
              };
            })
            .filter((field): field is NonNullable<typeof field> => field !== null)
        : [];
      return {
        code,
        title,
        description: typeof entry.description === "string" ? entry.description : null,
        stepType: typeof entry.stepType === "string" ? entry.stepType : null,
        saved: entry.saved === true,
        fields
      } satisfies WorkerChatWorkflowStep;
    })
    .filter((item): item is WorkerChatWorkflowStep => item !== null);
}

function extractPlannerProposal(metadata: Record<string, unknown>): WorkerChatPlannerProposal | null {
  const value = recordValue(metadata?.plannerProposal);
  if (!value) {
    return null;
  }
  const decision = value.decision;
  if (decision !== "PROPOSE_ACTION" && decision !== "ASK_CLARIFICATION" && decision !== "NO_ACTION") {
    return null;
  }
  const payload = recordValue(value.payload) ?? {};
  const confidence = typeof value.confidence === "number" ? value.confidence : Number(value.confidence);
  return {
    decision,
    actionType: typeof value.actionType === "string" ? value.actionType : "",
    requiresConfirmation: value.requiresConfirmation === true,
    confidence: Number.isFinite(confidence) ? Math.max(0, Math.min(1, confidence)) : 0,
    userMessage: typeof value.userMessage === "string" ? value.userMessage : null,
    rationale: typeof value.rationale === "string" ? value.rationale : null,
    payload
  };
}

function plannerExecutionPayload(
  proposal: WorkerChatPlannerProposal,
  context: {
    siteName: string;
    siteAddress: string;
    reportTitle: string;
    selectedStep: WorkerChatWorkflowStep | null;
  }
) {
  if (proposal.actionType === "CREATE_SITE") {
    const name = firstText(context.siteName, proposal.payload.name);
    if (!name) {
      return null;
    }
    return {
      content: `현장 생성: ${name}`,
      createSite: {
        name,
        address: firstText(context.siteAddress, proposal.payload.address) || undefined,
        siteType: firstText(proposal.payload.siteType) || "CONSTRUCTION_SITE"
      }
    };
  }
  if (proposal.actionType === "CREATE_REPORT") {
    const title = firstText(context.reportTitle, proposal.payload.title);
    if (!title) {
      return null;
    }
    const siteId = numberValue(proposal.payload.siteId);
    const templateId = numberValue(proposal.payload.templateId);
    return {
      content: `리포트 생성: ${title}`,
      createReport: {
        siteId: siteId ?? undefined,
        reportType: firstText(proposal.payload.reportType) || "CONSTRUCTION_DAILY_SUPERVISION_LOG",
        title,
        templateId: templateId ?? undefined
      }
    };
  }
  if (proposal.actionType === "UPDATE_REPORT_STEP") {
    const nestedPayload = recordValue(proposal.payload.payload);
    const stepPayload = nestedPayload && Object.keys(nestedPayload).length > 0
      ? nestedPayload
      : workerStepPayload(context.selectedStep, firstText(proposal.userMessage) || "");
    if (Object.keys(stepPayload).length === 0) {
      return null;
    }
    return {
      content: firstText(proposal.userMessage) || "리포트 단계 저장",
      updateReportStep: {
        reportId: numberValue(proposal.payload.reportId) ?? undefined,
        stepCode: firstText(proposal.payload.stepCode, context.selectedStep?.code) || undefined,
        payload: stepPayload
      }
    };
  }
  if (proposal.actionType === "SUBMIT_REPORT") {
    return {
      content: firstText(proposal.userMessage) || "리포트 제출",
      submitReport: {
        reportId: numberValue(proposal.payload.reportId) ?? undefined
      }
    };
  }
  if (proposal.actionType === "RUN_PREFLIGHT_REVIEW") {
    return {
      content: firstText(proposal.userMessage) || "문서 생성 전 검토 실행",
      runPreflightReview: {
        reportId: numberValue(proposal.payload.reportId) ?? undefined
      }
    };
  }
  if (proposal.actionType === "REQUEST_DOCUMENT_GENERATION") {
    return {
      content: firstText(proposal.userMessage) || "문서 생성 요청",
      requestDocumentGeneration: {
        reportId: numberValue(proposal.payload.reportId) ?? undefined,
        outputFormat: firstText(proposal.payload.outputFormat) || "DOCX",
        workerType: firstText(proposal.payload.workerType) || undefined
      }
    };
  }
  return null;
}

function proposalTitle(proposal: WorkerChatPlannerProposal) {
  if (proposal.decision === "ASK_CLARIFICATION") {
    return proposal.userMessage || "추가 확인이 필요합니다.";
  }
  if (proposal.decision === "NO_ACTION") {
    return proposal.userMessage || "실행할 작업이 없습니다.";
  }
  return `${actionLabel(proposal.actionType) || proposal.actionType} 제안`;
}

function proposalPayloadSummary(proposal: WorkerChatPlannerProposal) {
  if (proposal.actionType === "CREATE_SITE") {
    const name = firstText(proposal.payload.name);
    const address = firstText(proposal.payload.address);
    return [name && `현장명: ${name}`, address && `주소: ${address}`].filter(Boolean).join(" · ");
  }
  if (proposal.actionType === "CREATE_REPORT") {
    const title = firstText(proposal.payload.title);
    return title ? `리포트 제목: ${title}` : "";
  }
  if (proposal.actionType === "UPDATE_REPORT_STEP") {
    const stepCode = firstText(proposal.payload.stepCode);
    return stepCode ? `작성 단계: ${stepCode}` : "선택된 작성 단계에 저장합니다.";
  }
  if (proposal.actionType === "SUBMIT_REPORT") {
    return "현재 리포트를 제출 가능한 상태로 전환합니다.";
  }
  if (proposal.actionType === "RUN_PREFLIGHT_REVIEW") {
    return "문서 생성 전 검토를 실행합니다.";
  }
  if (proposal.actionType === "REQUEST_DOCUMENT_GENERATION") {
    return `출력 형식: ${firstText(proposal.payload.outputFormat) || "DOCX"}`;
  }
  return "";
}

function plannerDecisionLabel(decision: WorkerChatPlannerProposal["decision"]) {
  switch (decision) {
    case "ASK_CLARIFICATION":
      return "추가 확인";
    case "NO_ACTION":
      return "실행 없음";
    default:
      return "작업 제안";
  }
}

function actionLabel(actionType: string) {
  switch (actionType) {
    case "CREATE_SITE":
      return "현장 생성";
    case "CREATE_REPORT":
      return "리포트 생성";
    case "UPDATE_REPORT_STEP":
      return "리포트 단계 저장";
    case "SUBMIT_REPORT":
      return "리포트 제출";
    case "RUN_PREFLIGHT_REVIEW":
      return "문서 검토";
    case "REQUEST_DOCUMENT_GENERATION":
      return "문서 생성";
    default:
      return "";
  }
}

function recordValue(value: unknown): Record<string, unknown> | null {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    return null;
  }
  return value as Record<string, unknown>;
}

function firstText(...values: unknown[]) {
  for (const value of values) {
    if (typeof value === "string" && value.trim()) {
      return value.trim();
    }
  }
  return "";
}

function numberValue(value: unknown) {
  if (typeof value === "number" && Number.isFinite(value)) {
    return value;
  }
  if (typeof value === "string" && value.trim()) {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : null;
  }
  return null;
}

function workerStepPayload(step: WorkerChatWorkflowStep | null, content: string): Record<string, unknown> {
  const firstWritableField = step?.fields?.find((field) => field.type === "textarea")
    ?? step?.fields?.find((field) => field.type === "text")
    ?? step?.fields?.[0];
  if (!firstWritableField) {
    return { workerNote: content, source: "WORKER_CHAT" };
  }
  return {
    [firstWritableField.key]: content,
    workerNote: content,
    source: "WORKER_CHAT"
  };
}
