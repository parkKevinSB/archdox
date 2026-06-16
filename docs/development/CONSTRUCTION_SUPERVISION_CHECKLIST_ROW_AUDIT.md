# Construction Supervision Checklist Row Audit

Level: L2
Status: working-audit
Last reviewed: 2026-06-16

## Purpose

This document audits the current ArchDox construction supervision catalog
against the row-level structure of the official checklist reference:

```text
docs/reference-forms/korean/[별표 1] 단계별 감리 체크리스트 대장(건축공사 감리세부기준).pdf
```

The important modeling change is this:

```text
공종
-> 구분: 기본 업무 / 기본 외 업무
   -> 세부공정
      -> 검사항목
         -> 감리내용 세부 행
            -> 적합/부적합
            -> 기준/참고사항
            -> 조치사항
```

The daily supervision log must not flatten the official checklist rows into one
free-text `supervisionContent` field. The row-level answers are the canonical
business data. Generated daily-log prose can be derived from those row answers.

## Current Finding

The current catalog already covers all 46 trade records and the reference page
range 22-73 as broad selection data. That means ArchDox has enough data to show
trade/detail-process/check-item choices.

It does not yet have full row-level checklist data.

Current row-level coverage:

- Reinforced concrete has partial `checklistRows`.
- Most other trades still have only broad seeded items.
- Many existing seeded items are actually checklist-row candidates and should be
  moved under a parent inspection item.
- `workCategory` is only partially represented. It must be preserved because
  the official checklist separates `기본 업무` and `기본 외 업무`.

## Canonical Shape

Use this shape for the next catalog revision:

```json
{
  "tradeCode": "REINFORCED_CONCRETE",
  "tradeName": "철근 콘크리트 공사",
  "processGroups": [
    {
      "code": "REBAR_ASSEMBLY",
      "name": "철근 조립·배근",
      "workCategory": "BASIC",
      "workCategoryName": "기본 업무",
      "items": [
        {
          "code": "RC_REBAR_CONFIRMATION",
          "name": "철근배근의 확인사항",
          "checklistRows": [
            {
              "code": "RC_REBAR_COUNT_DIAMETER_PITCH",
              "label": "개수, 철근지름, 피치 확인",
              "basis": "개수, 철근지름, 피치 확인"
            }
          ]
        }
      ]
    }
  ]
}
```

The report payload should store the user's row answers:

```json
{
  "inspectionItemCode": "RC_REBAR_CONFIRMATION",
  "inspectionItemName": "철근배근의 확인사항",
  "checklistRows": [
    {
      "code": "RC_REBAR_COUNT_DIAMETER_PITCH",
      "label": "개수, 철근지름, 피치 확인",
      "result": "COMPLIANT",
      "referenceNote": "도면 및 현장 배근 상태 확인",
      "actionNote": ""
    }
  ],
  "supervisionContent": "개수, 철근지름, 피치 확인 등 철근배근의 확인사항을 점검했습니다."
}
```

`supervisionContent` is a generated/rendering convenience. The row answers are
the source data that later produces the formal checklist ledger.

## Trade-By-Trade Audit

| # | 공종 | 원본 페이지 | 현재 상태 | 보강 필요 |
| --- | --- | --- | --- | --- |
| 1 | 가설공사 | 22 | broad seed only | 기본 업무/기본 외 업무 분리와 부지상황확인, 줄쳐보기, 벤치마크, 규준틀, 지내력의 행 구조 필요 |
| 2 | 토공사 | 23 | broad seed only | 터파기, 터파기 계획, 흙막이, 터파기 바닥, 잔토처리의 행 구조 필요 |
| 3 | 지정 및 기초공사 | 24-25 | broad seed only | 말뚝공사와 지정공사를 분리하고 말뚝박기 계획, 말뚝박기, 시험말뚝, 자갈·쇄석지정, 밑창콘크리트 행 구조 필요 |
| 4 | 거푸집 공사 | 26 | broad seed only | 먹매김을 parent item으로 두고 밑창콘크리트, 조립, 자재, 동바리, 존치기간, 해체 행 구조 필요 |
| 5 | 철근 콘크리트 공사 | 27-29 | partial row model | 철근조립·배근, 철근규격증명서, 콘크리트 배합은 시작됨. 압접, 콘크리트 타설, 양생, 상태확인은 row model로 추가 정리 필요 |
| 6 | 철골 공사 | 30-31 | broad seed only | 강재 규격·치수, 설계도서 대조, 앵커볼트, 기둥밑 고르기, 제작, 세우기, 접합 등 다중 행 구조 필요 |
| 7 | 벽돌·블록·ALC 패널 공사 | 32-34 | broad seed only | 자재, 시공, 도서, 공사완료를 기본 업무/기본 외 업무로 분리하고 KS/규격/마감/공법 행 구조 필요 |
| 8 | 석공사 | 35 | broad seed only | 자재성능, 도서 확인, 시공상태, 고정/이음/마감 행 구조 필요 |
| 9 | 타일 및 테라코타 공사 | 36 | broad seed only | 자재성능, 도서, 바탕면, 붙임, 줄눈, 마감상태 행 구조 필요 |
| 10 | 목공사 | 37 | broad seed only | 자재, 도서, 시공 위치, 접합, 방부/방충, 최종 마감 행 구조 필요 |
| 11 | 단열공사 | 38 | broad seed only | 자재성능을 parent item으로 두고 설계도서, 시방서, KS 서류, 부위별 단열재 시공, 최종 마감 행 구조 필요 |
| 12 | 방수공사 | 39 | broad seed only | 자재, 바탕처리, 방수층 시공, 취약부, 담수/누수 확인, 마감 행 구조 필요 |
| 13 | 지붕 및 홈통공사 | 40 | broad seed only | 자재, 도서, 지붕 시공, 홈통/배수, 마감 행 구조 필요 |
| 14 | 금속공사 | 41 | broad seed only | 자재성능, 제작물 반입, 설치 위치, 고정, 방청, 마감 행 구조 필요 |
| 15 | 미장공사 | 42 | broad seed only | 바탕면, 배합, 두께, 균열, 평활도, 마감 행 구조 필요 |
| 16 | 창호공사 | 43 | broad seed only | 자재성능 parent 아래 단열/기밀/수밀/내풍압 등 성능 확인, 방화문·방화셔터, 철물, 반입제품, 설치·마감 행 구조 필요 |
| 17 | 유리공사 | 44 | broad seed only | 유리 자재성능, 규격, 설치, 실링, 파손/흠, 마감 행 구조 필요 |
| 18 | 커튼월공사 | 45 | broad seed only | 성능자료, 구조/고정, 수밀·기밀, 유리/패널, 실링, 마감 행 구조 필요 |
| 19 | 도장공사 | 46 | broad seed only | 바탕처리, 자재, 도막, 색상, 횟수, 마감 행 구조 필요 |
| 20 | 수장공사 | 47 | broad seed only | 자재, 바탕, 설치, 이음, 평활도, 마감 행 구조 필요 |
| 21 | 조경공사 | 48 | broad seed only | 식재, 토양, 배수, 시설물, 마감 행 구조 필요 |
| 22 | 잡공사 | 49 | broad seed only | 항목별 시공 위치, 자재, 설치, 마감 행 구조 필요 |
| 23 | 건물주위 공사 | 50 | broad seed only | 배수, 포장, 경계, 주변 시설, 마감 행 구조 필요 |
| 24 | 승강설비 및 기계식주차 설비공사 | 51 | broad seed only | 기기·자재, 설치 위치, 안전 관련 검사필증, 최종 설치마감 행 구조 필요 |
| 25 | 급배수위생 설비 공사 | 52 | broad seed only | 배관·기기 자재, 위치, 기울기, 수압/누수, 검사, 마감 행 구조 필요 |
| 26 | 공기 조화설비공사 | 53 | broad seed only | 기기·자재, 위치, 덕트/배관 연계, 시험운전, 마감 행 구조 필요 |
| 27 | 배관설비공사 | 54 | broad seed only | 배관 자재, 경로, 지지, 보온, 누수/압력, 마감 행 구조 필요 |
| 28 | 덕트설비공사 | 55 | broad seed only | 덕트 자재, 경로, 이음, 보온, 풍량/기밀, 마감 행 구조 필요 |
| 29 | 자동제어 설비공사 | 56 | broad seed only | 기기·자재, 배선, 센서/제어 위치, 연동, 시험, 마감 행 구조 필요 |
| 30 | 신재생에너지 설비공사 | 57 | broad seed only | 기기·자재, 설치 위치, 연결, 성능/시운전, 마감 행 구조 필요 |
| 31 | 냉동냉장 설비공사 | 58 | broad seed only | 기기·자재, 배관, 보온, 냉매, 시운전, 마감 행 구조 필요 |
| 32 | 클린룸 설비공사 | 59 | broad seed only | 자재, 기밀, 필터, 차압, 청정도, 시험/마감 행 구조 필요 |
| 33 | 가스설비공사 | 60 | broad seed only | 자재, 배관, 누설, 안전장치, 검사필증, 마감 행 구조 필요 |
| 34 | 방음방진 및 내진 설비공사 | 61 | broad seed only | 자재, 설치 위치, 방음/방진/내진 상세, 고정, 마감 행 구조 필요 |
| 35 | 옥외공사 | 62 | broad seed only | 옥외 전기 기기·자재, 설치계획, 시공, 최종 설치마감 행 구조 필요 |
| 36 | 수변전·인입공사 설비공사 | 63 | broad seed only | 기기·자재, 고효율 기자재, 설치 위치, 이격거리·접지, 설치계획/시공 행 구조 필요 |
| 37 | 예비전원 설비공사 | 64 | broad seed only | 기기·자재, 설치 위치, 이격거리·접지, 시험/마감 행 구조 필요 |
| 38 | 옥내배선공사 | 65 | broad seed only | 기기·자재, 설치 위치, 전기안전검사필증, 도서, 시공, 최종 설치마감 행 구조 필요 |
| 39 | 조명설비공사 | 66 | broad seed only | 조명 기기·자재, 고효율 기자재, 설치 위치, 시공, 마감 행 구조 필요 |
| 40 | 동력설비공사 | 67 | broad seed only | 동력 기기·자재, 고효율 기자재, 설치 위치, 시공, 마감 행 구조 필요 |
| 41 | 감시제어설비공사 | 68 | broad seed only | 감시제어 기기·자재, 고효율 기자재, 설치 위치, 시공, 마감 행 구조 필요 |
| 42 | 피뢰 및 접지 설비공사 | 69 | broad seed only | 피뢰·접지 기기·자재, 설치 위치, 설치계획/시공, 접지 확인, 마감 행 구조 필요 |
| 43 | 통신설비공사 | 70 | broad seed only | 통신 기기·자재, 고효율 기자재, 검사필증, 기기·기구 설치, 마감 행 구조 필요 |
| 44 | 약전설비공사 | 71 | broad seed only | 약전 기기·자재, 고효율 기자재, 기기·기구 설치, 마감 행 구조 필요 |
| 45 | 기계소방설비공사 | 72 | broad seed only | 기기·자재, 고효율 기자재, 기기·기구 설치, 소방 검사필증, 마감 행 구조 필요 |
| 46 | 전기소방설비공사 | 73 | broad seed only | 전기소방 기기·자재, 고효율 기자재, 기기·기구 설치, 마감 행 구조 필요 |

## High-Value Reconstruction Examples

### 가설공사

The current catalog has broad items such as 부지상황확인, 줄쳐보기,
벤치마크, 규준틀, 지내력. These should become parent inspection items.

Examples of missing checklist rows:

- 대지의 고저차 설계도서 확인
- 대지경계 확인
- 기준점 위치 확인
- BM 위치 변화 확인
- 먹매김 확인
- 설계 지내력의 육안 또는 서류 확인
- 부지 내 기존 건축물, 공작물, 지하매설물 확인
- 배수로와 배수관 확인

### 철근 콘크리트 공사

This is the first trade where the new model has started.

Already started:

- 철근 조립·배근 -> 철근배근의 확인사항 -> 개수, 지름, 피치 등
- 철근 조립·배근 -> 배근검사 후 처리 확인 -> 보강부분 등
- 철근 규격 증명서 -> 철근 규격품 확인
- 콘크리트 배합 -> KS 레디믹스트 콘크리트, 배합보고서/송장 확인

Still needed:

- 압접 rows
- 콘크리트 타설 rows
- 콘크리트 양생 rows
- 콘크리트 상태 확인 rows
- 기본 외 업무와 기본 업무의 정확한 원문 분리

### 단열공사

The current catalog has item-level choices, but the legal/reference checklist is
more useful as row-level questions.

Expected structure:

```text
단열공사
-> 기본 업무
   -> 자재
      -> 자재성능 확인
         -> KS 등 자재성능 관련 서류 확인
   -> 공사완료
      -> 최종 마감상태 확인
-> 기본 외 업무
   -> 도서
      -> 단열재 부위별 시공 확인
   -> 자재
      -> 설계도서/시방서/성능자료 확인
```

### 창호공사

The current catalog can say "창호 자재성능", but row-level inspection should make
the expected evidence and field checks explicit.

Expected structure:

- 창호 자재성능
  - KS 등 자재성능 관련 서류 확인
  - 단열 성능 확인
  - 기밀 성능 확인
  - 수밀 성능 확인
  - 내풍압 성능 확인
  - 시방서, 시험성적서, 자재승인서 확인
- 방화문/방화셔터
  - 설계도서 대조 확인
  - 반입 제품 부착 전 확인
  - 개폐장치 작동 확인
- 시공
  - 부착 위치 먹매김 확인
  - 철물 설치 확인
  - 모르타르 채움 확인
  - 방청 및 최종 마감 확인

### 옥내배선공사

The current catalog has the right top-level choices: 기기·자재성능, 기기 설치
위치, 전기안전검사필증, 최종 설치마감상태. These should be converted into
parent inspection items with rows.

Expected structure:

- 기기·자재성능
  - KS 등 기기 및 자재성능 관련 서류 확인
  - 고효율 에너지 기자재 적용 여부 확인
- 기기 설치 위치
  - 도서와 현장 설치 위치 대조 확인
- 전기안전검사필증
  - 검사필증 확인
  - 발급기관, 발급일, 대상 설비 기록 가능
- 시공
  - 배선 경로와 시공상태 확인
  - 최종 설치마감상태 확인

## Implementation Priority

### P0: Model and documentation alignment

- Keep `Trade -> WorkCategory -> ProcessGroup -> InspectionItem -> ChecklistRow`
  as the canonical catalog shape.
- Treat old item-level entries that are really checklist rows as legacy aliases
  during transition.
- Keep `supervisionContent` as derived prose, not the only source data.

### P1: Construction daily log MVP trades

Convert the trades most likely to appear in daily supervision logs first:

- 가설공사
- 토공사
- 지정 및 기초공사
- 거푸집 공사
- 철근 콘크리트 공사 remaining rows
- 철골 공사
- 벽돌·블록·ALC 패널 공사
- 단열공사
- 창호공사

### P2: Remaining architecture and finish trades

Convert pages 35-50 after the core structural trades:

- 석공사
- 타일 및 테라코타 공사
- 목공사
- 방수공사
- 지붕 및 홈통공사
- 금속공사
- 미장공사
- 유리공사
- 커튼월공사
- 도장공사
- 수장공사
- 조경공사
- 잡공사
- 건물주위 공사

### P3: Mechanical, electrical, communication, fire trades

Convert pages 51-73 after the architecture set stabilizes. These trades share
many row patterns:

- 기기 및 자재성능
- 고효율 에너지 기자재
- 도서 및 설치계획
- 시공상태
- 설치 위치
- 검사필증
- 최종 설치마감상태

## Open Decisions

- Should photos attach to the whole daily-log entry or to each checklist row?
  Current UI attaches photos to the entry. Row-level photos may be needed later
  for evidence-heavy inspections.
- Should unchecked rows appear in the generated daily log? Current assumption:
  unchecked rows remain in structured data but do not generate prose unless the
  user marks them or writes a note.
- Should old seeded item codes be kept forever as aliases? Current assumption:
  keep them as `legacy` and hidden from daily-log selection until real data
  migration policy exists.
- Should office overrides edit official rows directly? No. They should create
  office-specific revisions after the base catalog becomes stable.

