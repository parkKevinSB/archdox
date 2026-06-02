# Korean Default DOCX Templates

이 디렉터리는 ArchDox document-engine에 기본 포함되는 한국 감리/해체감리 DOCX 템플릿을 보관한다.

이 파일들은 `document-engine/build/archdox-smoke`에 생성되는 테스트 산출물이 아니다. PDF 참고 양식을 바탕으로 만든 편집 가능한 기본 템플릿이며, 실제 운영에서는 사무소별 템플릿 업로드/리비전/게시 기능으로 교체하거나 오버라이드할 수 있다.

## Template Policy

- 데이터 원본은 PDF/DOCX 자체가 아니라 `report snapshot + template config + output layout`이다.
- DOCX는 기본 편집 포맷이며, PDF/HWP/HWPX/HTML은 출력 아티팩트다.
- LibreOffice는 DOCX 작성 엔진이 아니다. DOCX를 PDF로 변환하거나 렌더링 검증에 사용하는 외부 도구다.
- PDF 산출물이 필요한 실행 위치에는 LibreOffice headless 도구가 있어야 한다. 기본 구조에서는 API 서버가 아니라 `archdox-agent` 또는 별도 cloud document agent Docker 이미지에 포함한다.
- 템플릿 안에는 `${fieldName}` 단순 필드와 `${sectionName}` 리치 섹션 플레이스홀더를 둔다.
- 리치 섹션은 `layoutSections` 설정으로 사진표, 체크리스트표, 사진 설명 같은 반복 구조를 바인딩한다.

## Official And Internal Forms

ArchDox 템플릿은 크게 두 종류로 관리한다.

1. `OFFICIAL_SUBMISSION`
   - 공공기관 제출 양식에 가깝게 맞춘 기본 번들 서식이다.
   - 사무소가 그대로 쓰거나 복사해서 오버라이드할 수 있다.
   - 법정/공공기관 양식에 가까운 구조를 유지하는 것이 우선이다.

2. `OFFICE_INTERNAL`
   - 사무소 운영, 내부 회의, 고객 공유, 자체 품질관리용으로 자유롭게 편집하는 서식이다.
   - 공식 제출 양식과 같은 report snapshot을 쓰지만, 문구/사진 배치/표 섹션을 사무소 목적에 맞게 바꿀 수 있다.
   - 기본적으로 DOCX 템플릿 바인딩 경로를 사용한다.

## Construction Daily Log Special Case

`korean-construction-daily-supervision-log-appendix-2.docx` 기본 번들 서식은 document-engine의 공식 공사감리일지 렌더러가 우선 적용된다. 이 렌더러는 제목, 굵은 구분선, 공사감리자/감리원 서명란, 작업사항 표, 사진 및 설명, 작성방법 블록을 공공기관 참고 양식에 가깝게 생성한다.

이 공식 렌더러는 기본 번들 `storageRef`에만 적용한다. 사무소가 별도 템플릿 리비전을 업로드하면 기존 템플릿 바인딩/리치 섹션 경로를 사용해 커스텀 템플릿을 존중한다.

장기적으로는 공식 렌더러의 범위를 줄이고, 반복 표/사진 섹션 같은 필요한 부분만 리치 섹션 헬퍼로 남기는 방향을 선호한다. 그래야 문서 모양 변경이 코드 수정이 아니라 템플릿/설정 변경으로 처리된다.

## Bundled Files

- `korean-construction-supervision-report-appendix-1.docx`
- `korean-construction-daily-supervision-log-appendix-2.docx`
- `korean-demolition-safety-checklist-appendix-1.docx`
- `korean-demolition-daily-supervision-log-appendix-2.docx`
- `korean-demolition-completion-report-appendix-3.docx`

## Regeneration

템플릿 구조를 수정할 때는 아래 스크립트를 실행해 DOCX 자산을 다시 만든다.

```powershell
python scripts/document_templates/generate_korean_default_templates.py
```

생성 후에는 document-engine 테스트를 실행해 모든 기본 템플릿에서 미치환 `${...}`가 남지 않는지 확인한다.
