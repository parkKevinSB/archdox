import { CheckCircle2, Loader2 } from "lucide-react";
import { FormEvent, useEffect, useState } from "react";
import { ApiError, getOfficeInvitationPreview, login, me, signup } from "../../api";
import type { AppState } from "../../appTypes";
import { BrandLogo, InlineAlert } from "../../components/common";
import type { OfficeInvitationPreview } from "../../types";

type AuthScreenProps = {
  invitationToken?: string | null;
  onAuthenticated: (auth: AppState, options?: { invitationAccepted?: boolean }) => void;
};

export function AuthScreen({ invitationToken, onAuthenticated }: AuthScreenProps) {
  const isInvitationFlow = Boolean(invitationToken);
  const [mode, setMode] = useState<"login" | "signup">(isInvitationFlow ? "signup" : "login");
  const [signupAccountType, setSignupAccountType] = useState<"PERSONAL" | "OFFICE">(
    isInvitationFlow ? "OFFICE" : "PERSONAL"
  );
  const [invitationPreview, setInvitationPreview] = useState<OfficeInvitationPreview | null>(null);
  const [email, setEmail] = useState("");
  const [name, setName] = useState("");
  const [password, setPassword] = useState("");
  const [officeCode, setOfficeCode] = useState("");
  const [signupInvitationToken, setSignupInvitationToken] = useState(invitationToken ?? "");
  const [busy, setBusy] = useState(false);
  const [loadingInvitation, setLoadingInvitation] = useState(Boolean(invitationToken));
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!invitationToken) {
      return;
    }
    let cancelled = false;
    setMode("signup");
    setSignupAccountType("OFFICE");
    setSignupInvitationToken(invitationToken);
    setLoadingInvitation(true);
    setError(null);
    getOfficeInvitationPreview(invitationToken)
      .then((preview) => {
        if (cancelled) {
          return;
        }
        setInvitationPreview(preview);
        setEmail(preview.email);
        setOfficeCode(preview.officeCode);
      })
      .catch((err) => {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "초대 정보를 불러오지 못했습니다.");
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoadingInvitation(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [invitationToken]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      if (isInvitationFlow && (!invitationPreview || invitationPreview.status !== "PENDING")) {
        throw new Error("사용할 수 없는 초대입니다. 관리자에게 새 초대를 요청해주세요.");
      }
      const token =
        mode === "login"
          ? await login(email, password)
          : await signup(email, password, trimToNull(name) ?? email, {
              accountType: signupAccountType,
              officeCode: trimToNull(officeCode),
              invitationToken: trimToNull(signupInvitationToken)
            });
      const user = await me(token.accessToken);
      onAuthenticated(
        { accessToken: token.accessToken, refreshToken: token.refreshToken, user },
        {
          invitationAccepted:
            mode === "signup" && signupAccountType === "OFFICE" && Boolean(trimToNull(signupInvitationToken))
        }
      );
    } catch (err) {
      if (mode === "login" && err instanceof ApiError && err.status === 401) {
        setError("이메일 또는 비밀번호를 확인해주세요.");
        return;
      }
      setError(err instanceof Error ? err.message : "인증에 실패했습니다.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="auth-page">
      <section className="auth-panel">
        <BrandLogo large />
        <div className="auth-copy">
          <h1>{isInvitationFlow ? "사무소 초대 가입" : "오늘의 현장 문서를 이어서 작성하세요"}</h1>
          <p>
            {isInvitationFlow
              ? "초대받은 사무소와 이메일을 확인한 뒤 회사 계정을 만듭니다."
              : "프로젝트, 감리일지, 사진, 문서 생성 흐름을 한 화면에서 관리합니다."}
          </p>
        </div>

        {!isInvitationFlow ? (
          <div className="segmented">
            <button className={mode === "login" ? "active" : ""} onClick={() => setMode("login")} type="button">
              로그인
            </button>
            <button className={mode === "signup" ? "active" : ""} onClick={() => setMode("signup")} type="button">
              회원가입
            </button>
          </div>
        ) : null}

        <form className="auth-form" onSubmit={submit}>
          {isInvitationFlow ? (
            <div className="invitation-summary">
              <strong>{invitationPreview?.officeDisplayName ?? "사무소 초대 확인 중"}</strong>
              <span>
                {invitationPreview
                  ? `${invitationPreview.officeCode} · ${invitationPreview.role}`
                  : "초대 정보를 불러오는 중입니다."}
              </span>
            </div>
          ) : null}

          {mode === "signup" ? (
            <>
              {!isInvitationFlow ? (
                <div className="segmented compact">
                  <button
                    className={signupAccountType === "PERSONAL" ? "active" : ""}
                    onClick={() => setSignupAccountType("PERSONAL")}
                    type="button"
                  >
                    개인
                  </button>
                  <button
                    className={signupAccountType === "OFFICE" ? "active" : ""}
                    onClick={() => setSignupAccountType("OFFICE")}
                    type="button"
                  >
                    사무소
                  </button>
                </div>
              ) : null}
              <label>
                이름
                <input
                  autoComplete="name"
                  onChange={(event) => setName(event.target.value)}
                  placeholder="홍길동"
                  required
                  type="text"
                  value={name}
                />
              </label>
              {signupAccountType === "OFFICE" ? (
                <>
                  <label>
                    사무소 코드
                    <input
                      autoComplete="organization"
                      onChange={(event) => setOfficeCode(event.target.value)}
                      placeholder="office-abc123"
                      readOnly={isInvitationFlow}
                      required
                      type="text"
                      value={officeCode}
                    />
                  </label>
                  {!isInvitationFlow ? (
                    <label>
                      초대 토큰
                      <input
                        onChange={(event) => setSignupInvitationToken(event.target.value)}
                        placeholder="초대 링크의 토큰"
                        required
                        type="text"
                        value={signupInvitationToken}
                      />
                    </label>
                  ) : null}
                </>
              ) : null}
            </>
          ) : null}

          <label>
            이메일
            <input
              autoComplete="email"
              onChange={(event) => setEmail(event.target.value)}
              placeholder="name@example.com"
              readOnly={isInvitationFlow}
              required
              type="email"
              value={email}
            />
          </label>
          <label>
            비밀번호
            <input
              autoComplete={mode === "login" ? "current-password" : "new-password"}
              minLength={8}
              onChange={(event) => setPassword(event.target.value)}
              placeholder="8자 이상"
              required
              type="password"
              value={password}
            />
          </label>
          {error ? <InlineAlert message={error} /> : null}
          <button
            className="primary-button"
            disabled={
              busy || loadingInvitation || (isInvitationFlow && (!invitationPreview || invitationPreview.status !== "PENDING"))
            }
            type="submit"
          >
            {busy ? <Loader2 className="spin" size={17} /> : <CheckCircle2 size={17} />}
            {mode === "login" ? "로그인" : "회원가입 후 계속"}
          </button>
        </form>
      </section>
    </div>
  );
}

function trimToNull(value: string) {
  const trimmed = value.trim();
  return trimmed.length === 0 ? null : trimmed;
}
