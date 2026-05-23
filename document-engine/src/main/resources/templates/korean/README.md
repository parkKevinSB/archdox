# Korean Default DOCX Templates

이 디렉터리는 ArchDox document-engine에 기본 포함되는 한국 감리/해체감리 DOCX 템플릿을 보관한다.

이 파일들은 `document-engine/build/archdox-smoke`에 생성되는 테스트 산출물이 아니다. PDF 참고 양식을 바탕으로 만든 편집 가능한 기본 템플릿이며, 실제 운영에서는 사무소별 템플릿 업로드/리비전/게시 기능으로 교체하거나 오버라이드할 수 있다.

## Template Policy

- 데이터 원본은 PDF/DOCX 자체가 아니라 `report snapshot + template config + output layout`이다.
- DOCX는 기본 편집 포맷이며, PDF/HWP/HWPX/HTML은 출력 어댑터가 만드는 산출물이다.
- LibreOffice는 DOCX 작성 엔진이 아니라 DOCX를 PDF로 변환하거나 렌더링 검증할 때 사용하는 외부 런타임이다.
- PDF 산출물이 필요한 실행 위치에는 LibreOffice headless 런타임이 있어야 한다. 기본 구조에서는 API 서버가 아니라 `archdox-agent` 또는 별도 cloud document agent Docker 이미지에 포함한다.
- 템플릿 안에는 `${fieldName}` 단순 필드와 `${sectionName}` 리치 섹션 플레이스홀더를 둔다.
- 리치 섹션은 `layoutSections` 설정으로 사진표, 체크리스트표, 배치표처럼 반복 구조를 바인딩한다.
- 이 템플릿은 법정 서식의 뼈대와 한국어 항목명을 제공하는 기본값이다. 사무소별 세부 문구, 직인/서명란, 출력 여백은 템플릿 리비전으로 조정한다.

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
