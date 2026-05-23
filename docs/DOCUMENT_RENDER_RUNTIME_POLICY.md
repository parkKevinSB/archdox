# Document Render Runtime Policy

## 목적

ArchDox의 문서 생성은 특정 파일 포맷이나 특정 서버 환경에 묶이지 않는다. 데이터 원본은 항상 `report snapshot + template config + output layout`이고, DOCX/PDF/HWPX/HTML은 이 중립 데이터 모델에서 만들어지는 산출물이다.

## LibreOffice의 역할

- LibreOffice는 문서 작성 엔진이 아니라 DOCX를 PDF로 변환하거나 렌더링 검증할 때 쓰는 외부 런타임이다.
- DOCX만 생성하는 단계에서는 LibreOffice가 없어도 된다.
- `PDF`, `DOCX_AND_PDF`, `HTML_AND_PDF`처럼 PDF 산출물이 필요한 실행 위치에는 LibreOffice headless 런타임이 필요하다.
- Windows 전용이 아니다. Linux 서버와 Docker 컨테이너에서 `soffice --headless` 방식으로 운영할 수 있다.

## 배포 원칙

- API 서버 본체에는 LibreOffice를 직접 설치하지 않는 것을 기본 원칙으로 한다.
- 문서 렌더링을 수행하는 `archdox-agent` 또는 cloud document agent Docker 이미지에 LibreOffice를 포함한다.
- Docker 이미지는 `libreoffice-writer`, `fonts-noto-cjk`, `fonts-noto-cjk-extra`, `fontconfig`를 포함해야 한다.
- 실행 경로는 `DOCUMENT_EXPORT_LIBREOFFICE_PATH` 설정으로 분리한다.

## 안정성 규칙

- 변환 프로세스는 작업별 임시 `UserInstallation` 프로필을 사용한다.
- 공유 LibreOffice 사용자 프로필을 쓰지 않는다. 서버 동시 변환, 파일 락, 첫 실행 마법사 문제를 줄이기 위해서다.
- 변환 timeout은 `DOCUMENT_EXPORT_LIBREOFFICE_TIMEOUT_MS`로 설정한다.
- LibreOffice가 없거나 실행되지 않으면 해당 agent는 PDF capability를 광고하지 않는다.

## HWP/HWPX 정책

- MVP의 기본 편집 포맷은 DOCX다.
- PDF는 DOCX에서 LibreOffice로 변환한다.
- HWP/HWPX는 후속 출력 어댑터로 분리한다. Hancom 계열 converter, HWPX XML writer, 또는 외부 변환 서비스를 붙일 수 있지만 document-engine의 중립 데이터 모델은 유지한다.
