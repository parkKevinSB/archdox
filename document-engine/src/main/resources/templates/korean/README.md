# Korean Default DOCX Templates

이 디렉터리는 ArchDox `document-engine`에 기본 포함되는 한국 문서
템플릿을 보관한다.

## Active MVP Scope

현재 ArchDox의 활성 MVP 업무 범위는 **공사감리**다.

활성 기본 템플릿은 다음 두 가지다.

- `korean-construction-daily-supervision-log-appendix-2.docx`
- `korean-construction-supervision-report-appendix-1.docx`

Cloud API의 문서유형 등록, 리포트 생성, 템플릿 필드 카탈로그, AI 검토
가이드, 사용자 UI는 위 공사감리 업무 범위만 노출해야 한다.

## Deferred / Archived Templates

다음 해체감리 템플릿은 참고 자료와 향후 별도 업무 페이즈를 위한 보관
자산이다.

- `korean-demolition-safety-checklist-appendix-1.docx`
- `korean-demolition-daily-supervision-log-appendix-2.docx`
- `korean-demolition-completion-report-appendix-3.docx`

이 파일들은 삭제하지 않는다. 다만 공사감리 안정화 전까지 Cloud API,
사용자 UI, 템플릿 필드 카탈로그, 문서유형 목록에서 노출하거나 기본
선택지로 사용하지 않는다.

해체감리는 공사감리와 다른 업무 도메인이다. 해체감리 문서유형,
체크리스트, 작성 흐름, 검증 정책을 열려면 별도 phase에서 도메인
카탈로그와 workflow를 분리해서 구현한다.

## Template Policy

- 데이터 원본은 DOCX/PDF 파일 자체가 아니라
  `report snapshot + template config + output layout`이다.
- DOCX는 기본 편집 포맷이며, PDF/HWP/HWPX/HTML은 출력 아티팩트다.
- LibreOffice는 DOCX 작성 엔진이 아니라 DOCX를 PDF로 변환하거나 렌더링
  검증에 사용하는 외부 도구다.
- 공식 공사감리일지는 별지 제2호서식에 가까운 bundled renderer를 우선
  사용한다.
- 사무소별 양식 변경은 공식본을 복사한 template revision 또는 별도
  office override로 처리한다.

## Construction Daily Log Special Case

`korean-construction-daily-supervision-log-appendix-2.docx` 기본 번들
서식은 ArchDox 공사감리일지 공식 표준이다.

문서 엔진은 다음 구조를 기준으로 렌더링한다.

- 일련번호
- 총괄감리책임자 / 건축사보 서명란
- 공사명, 일자, 요일, 날씨
- `공종 및 세부공정 / 감리 항목 / 감리내용`
- 특기사항
- 지적사항 및 처리결과
- 사진 및 설명
- 작성방법

문서 레이아웃은 나중에 개정될 수 있다. 따라서 구조화된 감리 데이터는
문서 레이아웃에 종속시키지 않고, 각 문서 생성 시점의 template/layout
revision과 함께 artifact history에 남긴다.

## Regeneration

템플릿 구조를 수정할 때는 아래 스크립트로 DOCX 자산을 다시 만들 수 있다.

```powershell
python scripts/document_templates/generate_korean_default_templates.py
```

생성 후에는 document-engine 테스트를 실행해 기본 템플릿에 미치환
`${...}` 값이 남지 않는지 확인한다.
