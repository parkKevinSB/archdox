# ArchDox — 상세 설계 문서 인덱스

작성일: 2026-05-21

`건축자동화_개발구현계획.md`(1차 결론)을 바탕으로 사용자가 추가 요청한 항목들을 반영하여 작성한 상세 설계 문서 집합.

## 사용자 요청 매핑

| 요청 | 답변 위치 |
|---|---|
| 웹 UI / 어플 (iOS/Android) | `05_상세설계_클라이언트.md` — PWA + Capacitor 결정 |
| AWS API 서버 | `01` §3, `02`, `03` — 단일 Spring Boot, 멀티테넌트 |
| API 서버에서 문서 생성? (개인 대상) | `04` §2~3, `08` Phase 4 — 개인은 Cloud, 사무소는 ArchDox Agent |
| 건축사무소 로컬 서버 Spring Boot? | `06` §2 — **YES, Spring Boot** |
| 로컬에서 문서 생성 | `03` §8.2, `06` §3.6 — `DocumentRenderFlow` |
| API 서버는 연결 통로 + DB 관리 | `01` §3, `02` — 그대로 반영 |
| NAS 연결 | `06` §3.4 — SMB/NFS/Local |
| 사내 로컬 관리 UI: C# WinForms/WebForms? | `06` §4 — **ArchDox Agent 내장 React (localhost)**, C# 미채택 |
| 카메라 촬영 + 체크리스트 + 임시 저장 | `05` §3~4 |
| 이미지 S3 비용 폭주 우려 | `04` 전체 — 계층 저장 + NAS 우선 + 썸네일만 CloudFront |
| 이미지 id/경로만 저장, 별도 보관 | `02` §3.3 `photos.storage_ref`, `04` §3 |
| Admin 화면/서버 별도 | `07` §6 — 별도 SPA + 별도 인증 |
| 모든 사무소 동일 API + 개인도 가능 | `02` §2, `07` §2~3 — 단일 SaaS + 가상 personal office |
| 놓친 상세 설계 (시간 동기화, 오프라인, EXIF, 백업 등) | `01` §7 + 각 문서 |
| 문서 자동화 생성 방식 (AI 미정, plugin 구조) | `09` 전체 — 4단계 파이프라인 + TextGenerator interface |
| 문장 생성, 문서 포맷, 다양한 문서 종류 커버 | `09` §6 — 문서 종류 카탈로그, schema 기반 |
| AI 계층 구조 (어떤 AI든 붙임) + AI 없이 raw 저장 | `09` §3~5 — Generator plug-in (noop/template/openai/anthropic/ollama) |
| 문서 포맷을 쉽게 만드는 로컬/웹 도구 | `10` 전체 — Cloud Template Studio + Local Editor |
| 포맷 필드와 UI 입력 연결 | `10` §11, `09` §8 — schema-driven wizard 자동 생성 |
| 건축법 자동 검토 (체크/특이사항/제출 시점) | `11` 전체 — Regulation Engine + Review Flow |
| 법령 항목별로 UI 박스/체크박스에 매핑 | `11` §4 — schema의 `regulationRefs` |
| 법령 표시 방식 ((i) 아이콘 팝업, 사이드 패널 등) | `11` §6 — 4-layer UX |
| 생성 문서 전달/이메일/다운로드 정책 | `12` 전체 — 상태 조회와 실제 파일 전달 분리 |

## 문서 목록

1. [01_상세설계_전체아키텍처.md](./01_상세설계_전체아키텍처.md) — 시스템 컨텍스트, ADR, 컴포넌트, 비기능 요구사항
2. [02_상세설계_도메인및데이터.md](./02_상세설계_도메인및데이터.md) — 패키지 구조, 멀티테넌시, 전체 ERD, 보존 정책
3. [03_상세설계_API및이벤트.md](./03_상세설계_API및이벤트.md) — REST API 전체, Bloom 이벤트, Flower workflow
4. [04_상세설계_이미지및스토리지.md](./04_상세설계_이미지및스토리지.md) — 비용 시뮬레이션, 계층 저장, 업로드 파이프라인
5. [05_상세설계_클라이언트.md](./05_상세설계_클라이언트.md) — PWA + Capacitor 전략, 화면, 오프라인
6. [06_상세설계_로컬서버_및_관리UI.md](./06_상세설계_로컬서버_및_관리UI.md) — ArchDox Agent (Spring Boot) + 내장 관리 UI
7. [07_상세설계_멀티테넌트_admin.md](./07_상세설계_멀티테넌트_admin.md) — 테넌시 구조, Admin Console, 라이선스
8. [08_구현순서_및_기능목록.md](./08_구현순서_및_기능목록.md) — Phase별 작업, 전체 기능 체크리스트
9. [09_상세설계_문서생성및AI계층.md](./09_상세설계_문서생성및AI계층.md) — Compose 파이프라인, TextGenerator plug-in, BYOK
10. [10_상세설계_템플릿편집기.md](./10_상세설계_템플릿편집기.md) — Word 업로드 → schema 매핑 → 미리보기 → 게시
11. [11_상세설계_건축법검토.md](./11_상세설계_건축법검토.md) — 법령 코퍼스 + 필드 매핑 + 룰/AI 검토 + 4-layer UX
12. [12_상세설계_문서전달정책.md](./12_상세설계_문서전달정책.md) — 이메일/로컬 보관함/출력/임시 링크 정책

## 핵심 결정 한눈에

- **단일 SaaS Cloud API** (Spring Boot 3.5 / Java 21) + **사무소별 ArchDox Agent** (Spring Boot, 옵션) + **PWA 클라이언트** (React + Capacitor) + **Admin Console** (별도 SPA)
- abyss-runner의 **Flower/Bloom 패턴** 그대로 채택. Cloud/Local 모두 동일한 이벤트 모델 (다른 bus 인스턴스).
- Draft 입력 데이터는 **Cloud 암호화 저장을 기본값**으로 하고, 보안 민감 사무소만 **Local-only draft mode**를 선택한다.
- 이미지: **NAS 우선, 썸네일만 S3 STANDARD + CloudFront**. 개인 플랜은 S3 IA→Glacier lifecycle.
- 사무소 사진 업로드는 **Cloud-mediated를 기본값**으로 한다. ArchDox Agent 직접 업로드는 터널/enrollment 완료 사무소의 선택 옵션이며, Client↔Agent는 짧은 만료 signed upload token을 쓴다.
- 멀티테넌시: **단일 DB + `office_id` 3중 격리** (JPA filter + Repository + RLS optional). 개인은 가상 `personal-*` office.
- 로컬 관리 UI: **C# 미채택**. ArchDox Agent에 내장된 localhost React 페이지.
- **문서 생성은 4단계 파이프라인** (Input → Compose → Bind → Render), AI는 `TextGenerator` interface 뒤로 추상화. AI 없이 noop/template만으로도 모든 문서 생성 가능.
- **새 문서 종류는 schema + DOCX 등록**만으로 추가. 클라이언트 코드 변경 0. 편집기는 Cloud Web + ArchDox Agent 내장 두 종.
- **건축법 검토는 별도 코퍼스 + 필드 매핑 + 룰/AI 엔진**. UI는 (i) 아이콘 + 사이드 패널 + 인라인 + 제출 전 검토 보고서 4-layer.
- 생성 문서는 일반 다운로드 API로 노출하지 않고, `document_delivery_requests`를 통해 **로컬 보관함/이메일/출력/임시 링크**로 전달한다.
- MVP는 운영 단계 분리(Gateway/Supervisor/Redis/SSO)를 미루고 단일 인스턴스로 시작.

## 다음 액션

1. 본 설계 문서들을 한번 훑고, 결정에 이견이 있는 항목을 표시 (특히 ADR 표)
2. Phase 0 + Phase 1.1~1.6 을 첫 PR 단위로 클로드에게 지시
3. 인프라(AWS account, RDS, S3 bucket 분리, CloudFront)는 Phase 2 종료 시점에 준비
