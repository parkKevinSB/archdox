export type ApiFieldErrorPayload = {
  field?: string | null;
  code?: string | null;
  message?: string | null;
  params?: Record<string, unknown> | null;
};

export type ApiErrorPayload = {
  code?: string | null;
  messageKey?: string | null;
  message?: string | null;
  params?: Record<string, unknown> | null;
  fieldErrors?: ApiFieldErrorPayload[] | null;
  blockingIssues?: Array<{ code?: string | null; message?: string | null }> | null;
};

type MessageResolver = string | ((error: ApiErrorPayload) => string);

const codeMessagesKo: Record<string, MessageResolver> = {
  BAD_REQUEST: "요청 내용을 확인해주세요.",
  CONFLICT: "현재 상태에서는 요청을 완료할 수 없습니다.",
  FORBIDDEN: "이 작업을 수행할 권한이 없습니다. 계정 소속 또는 담당자 배정을 확인해주세요.",
  NOT_FOUND: "요청한 대상을 찾을 수 없습니다.",
  OFFICE_MEMBERSHIP_REQUIRED: "현재 계정은 이 작업공간에 접근할 수 없습니다. 로그인 계정과 소속을 확인해주세요.",
  PROJECT_ASSIGNMENT_REQUIRED: "이 프로젝트는 배정된 담당자만 리포트를 작성하거나 수정할 수 있습니다.",
  PROJECT_MANAGER_ROLE_REQUIRED: "프로젝트나 현장 구조를 관리할 권한이 없습니다. OWNER, ADMIN 또는 프로젝트 MANAGER 권한이 필요합니다.",
  REPORT_ASSIGNMENT_REQUIRED: "이 리포트는 배정된 작성자만 수정할 수 있습니다. 리포트 WRITER 배정을 확인해주세요.",
  REPORT_CANCEL_NOT_ALLOWED: (error) => `현재 상태(${param(error, "status") ?? "-"})에서는 리포트를 취소할 수 없습니다.`,
  REPORT_NOT_FOUND: "리포트를 찾을 수 없습니다. 선택한 작업공간 또는 리포트 목록을 다시 확인해주세요.",
  REPORT_REOPEN_NOT_ALLOWED: (error) =>
    `현재 상태(${param(error, "status") ?? "-"})에서는 수정본을 만들 수 없습니다. 진행 중인 문서 작업이 끝난 뒤 다시 시도해주세요.`,
  REPORT_STEP_SAVE_NOT_ALLOWED: (error) =>
    `현재 상태(${param(error, "status") ?? "-"})에서는 작성 단계를 저장할 수 없습니다. 수정본 만들기를 먼저 실행해주세요.`,
  REPORT_SUBMIT_NOT_ALLOWED: (error) =>
    `현재 상태(${param(error, "status") ?? "-"})에서는 리포트를 제출할 수 없습니다.`,
  REPORT_WRITE_FORBIDDEN: "이 리포트를 수정할 권한이 없습니다. 계정 소속 또는 담당자 배정을 확인해주세요.",
  UNAUTHORIZED: "로그인이 만료되었습니다. 다시 로그인해주세요.",
  VALIDATION_FAILED: "입력값을 확인해주세요."
};

export function apiErrorMessage(status: number, payload?: ApiErrorPayload | null, textBody?: string | null) {
  const fallback = `요청을 처리하지 못했습니다. (${status})`;
  const rawMessage = validationMessage(payload) ?? payload?.message ?? normalizedText(textBody);
  const codeMessage = payload?.code ? resolveCodeMessage(payload.code, payload) : null;
  return codeMessage ?? friendlyMessage(status, rawMessage) ?? rawMessage ?? fallback;
}

function resolveCodeMessage(code: string, payload: ApiErrorPayload) {
  const resolver = codeMessagesKo[code];
  if (!resolver) {
    return null;
  }
  return typeof resolver === "function" ? resolver(payload) : resolver;
}

function validationMessage(error?: ApiErrorPayload | null) {
  if (Array.isArray(error?.fieldErrors) && error.fieldErrors.length > 0) {
    const details = error.fieldErrors
      .map((fieldError) => fieldErrorMessage(fieldError))
      .filter((value): value is string => Boolean(value && value.trim()))
      .slice(0, 4);
    if (details.length > 0) {
      return details.join(" / ");
    }
  }

  if (!Array.isArray(error?.blockingIssues) || error.blockingIssues.length === 0) {
    return null;
  }
  const details = error.blockingIssues
    .map((issue) => blockingIssueMessage(issue.code, issue.message))
    .filter((value): value is string => Boolean(value && value.trim()))
    .slice(0, 4);
  if (details.length === 0) {
    return error.message ?? null;
  }
  return `${error.message ?? "리포트를 제출할 준비가 되지 않았습니다."}: ${details.join(" / ")}`;
}

function fieldErrorMessage(error: ApiFieldErrorPayload) {
  const field = error.field ?? "field";
  if (error.code === "NotNull" || error.code === "NotBlank" || error.code === "REQUIRED") {
    return `${field} 항목은 필수입니다.`;
  }
  return error.message ? `${field}: ${error.message}` : `${field} 항목을 확인해주세요.`;
}

function blockingIssueMessage(code?: string | null, message?: string | null) {
  if (code === "MISSING_STEP_BASIC_INFO") {
    return "기본 정보 단계를 저장해주세요.";
  }
  if (code === "MISSING_STEP_CHECKLIST") {
    return "체크리스트 단계를 저장하거나 체크리스트 답변을 입력해주세요.";
  }
  if (code === "MISSING_WORKING_PHOTO") {
    return "문서 생성용 작업본 사진을 최소 1장 업로드해주세요.";
  }
  return message ?? null;
}

function normalizedText(text?: string | null) {
  if (!text) {
    return null;
  }
  const normalized = text.replace(/<[^>]+>/g, " ").replace(/\s+/g, " ").trim();
  return normalized.length > 0 ? normalized : null;
}

function friendlyMessage(status: number, message?: string | null) {
  if (status === 401) {
    return codeMessagesKo.UNAUTHORIZED as string;
  }
  if (status !== 403) {
    return null;
  }
  const normalized = message ?? "";
  if (normalized.includes("Report assignment required")) {
    return codeMessagesKo.REPORT_ASSIGNMENT_REQUIRED as string;
  }
  if (normalized.includes("Project assignment required")) {
    return codeMessagesKo.PROJECT_ASSIGNMENT_REQUIRED as string;
  }
  if (normalized.includes("Report writer role required")) {
    return codeMessagesKo.REPORT_WRITE_FORBIDDEN as string;
  }
  if (normalized.includes("Project manager role required")) {
    return codeMessagesKo.PROJECT_MANAGER_ROLE_REQUIRED as string;
  }
  if (normalized.includes("Office admin role required")) {
    return "사무소 관리 권한이 없습니다. OWNER 또는 ADMIN 권한이 필요합니다.";
  }
  if (normalized.includes("Office membership required")) {
    return codeMessagesKo.OFFICE_MEMBERSHIP_REQUIRED as string;
  }
  if (normalized.includes("Personal workspace cannot")) {
    return "개인 작업공간에서는 사무소 멤버/초대 기능을 사용할 수 없습니다.";
  }
  if (normalized.includes("Invitation email does not match")) {
    return "초대받은 이메일과 현재 계정 이메일이 다릅니다. 초대받은 이메일로 가입하거나 로그인해주세요.";
  }
  return codeMessagesKo.FORBIDDEN as string;
}

function param(error: ApiErrorPayload, key: string) {
  const value = error.params?.[key];
  if (typeof value === "string" || typeof value === "number" || typeof value === "boolean") {
    return String(value);
  }
  return null;
}
