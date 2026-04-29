# Sympotalk 행사 런처 (sympopad_web)

LG G Pad 5 (Android 9) 행사장 전용 태블릿 PWA 런처.

[![Latest Release](https://img.shields.io/github/v/release/sympotalk/SympotalkLauncher-APK?label=APK)](https://github.com/sympotalk/SympotalkLauncher-APK/releases/latest)
[![Pages](https://img.shields.io/badge/Cloudflare_Pages-active-green)](https://sympotalklauncher-apk.pages.dev)

---

## 개요

행사장에서 태블릿을 키오스크처럼 운영하기 위한 단일 페이지 앱입니다. 다음을 제공:

- 행사 목록 / 상세
- 콘솔·의장·진행자 링크 진입
- WiFi QR 스캐너
- 자동 업데이트 (앱 내 OTA)
- 비정상 종료 감지 + 사용자 친화적 로그 뷰어

배포 채널:
- **APK** — GitHub Releases (자동 빌드)
- **PWA** — https://sympotalklauncher-apk.pages.dev (Cloudflare Pages)

## 기술 스택

| 영역 | 기술 |
|---|---|
| 프론트엔드 | Vanilla HTML/CSS/JS (ES5 — Android 9 / Chrome ~71 호환) |
| Android 래퍼 | Java + WebView (Gradle 8.6, JDK 17, minSdk 28) |
| 엣지 함수 | Cloudflare Pages Functions (Node) |
| DB | Supabase (sympotalkv2) |
| CI/CD | GitHub Actions + Cloudflare Pages 자동 배포 |

## 디렉토리

```
.
├── index.html              # 메인 SPA (130KB+)
├── manifest.json sw.js     # PWA
├── version.txt             # 릴리스 버전 (build.gradle과 동기화 필수)
├── _headers                # Cloudflare Pages 헤더
├── android-app/            # Android WebView 래퍼
├── functions/api/          # Cloudflare Pages Functions
├── supabase/migrations/    # Supabase SQL 마이그레이션
├── .github/workflows/      # CI/CD
├── docs/                   # CHANGELOG / ISSUES / sessions / deployments / incidents
├── .claude/                # Claude Code 자동화 (settings.json + commands/)
└── CLAUDE.md               # Claude Code 항상-읽힘 컨텍스트
```

상세 구조는 [`CLAUDE.md`](./CLAUDE.md) 참고.

## 로컬 실행

```bash
# 정적 서버 (Service Worker 위해 https 또는 localhost 권장)
python -m http.server 8080
# → http://localhost:8080
```

Android APK 로컬 빌드 (보통 불필요, CI가 자동):
```bash
cd android-app && ./gradlew assembleDebug
```

## 배포

`main`에 push하면 자동:
1. GitHub Actions가 APK 빌드
2. GitHub Release 생성 (`v<X.Y.Z>`)
3. Cloudflare Pages가 정적 사이트 배포

**버전 동기화 필수**: `version.txt` ↔ `android-app/app/build.gradle` (versionName + versionCode bump). CI가 일치 검증.

상세 절차는 `/deploy` 슬래시 커맨드 또는 `.claude/commands/deploy.md` 참고.

## 컨벤션

- **JS**: ES5 (`var`, `function`만). `?.`, `??` 등 ES2020+ 금지 — Chrome 71 미지원.
- **커밋**: Conventional Commits + 한국어 + 버전 suffix.
  - 예: `feat: 비정상 종료 감지 + 태블릿 레이아웃 꽉참 (v1.0.43)`
- **자동 감사**: 기능 추가/리팩터링 후 커밋 전 `general-purpose` 서브에이전트로 코드 감사.

## Claude Code 사용 가이드

이 레포는 [Claude Code](https://docs.claude.com/claude-code) 자동화가 구성되어 있습니다.

### 새 세션 시작

```
/init-context
```

CLAUDE.md, 최근 세션 로그 3개, 미해결 이슈, 최근 커밋, CI 상태를 자동 수집해 "이어서 할 작업"을 요약합니다.

### 사용 가능한 슬래시 커맨드

| 커맨드 | 용도 |
|---|---|
| `/init-context` | 새 세션 시작 시 컨텍스트 복원 |
| `/save-session` | 작업 종료 시 세션 로그 + CHANGELOG 갱신 |
| `/sync-github` | 의미있는 단위로 커밋 + push + 이슈 동기화 |
| `/quick-save` | save-session + sync-github 한 번에 |
| `/deploy` | 사전검증 → push → CI 모니터 → health check → 로그 |
| `/deploy-preview` | 현재 브랜치로 프리뷰 배포 (PR 코멘트 자동) |
| `/rollback` | 직전 안정 버전으로 긴급 롤백 + incident 기록 (사용자 승인 필수) |

각 커맨드의 상세 절차는 `.claude/commands/<name>.md` 참고.

### 권한 정책

- **자동 허용**: git/gh 읽기, ls/cat/grep, gradle 빌드, docs/ .claude/ 쓰기
- **자동 거부**: `.env*` 읽기/쓰기, `*.keystore`/`*.jks` 접근, `sudo`, `curl | sh`
- **사용자 확인**: `git push --force`, `git reset --hard`, `rm`, `gh release delete`

세부 설정: [`.claude/settings.json`](./.claude/settings.json)

### 문서

- [`CLAUDE.md`](./CLAUDE.md) — 항상-읽힘 프로젝트 컨텍스트
- [`docs/CHANGELOG.md`](./docs/CHANGELOG.md) — 릴리스 변경 내역
- [`docs/ISSUES.md`](./docs/ISSUES.md) — 미해결 이슈 트래커
- [`docs/sessions/`](./docs/sessions/) — Claude Code 작업 세션 로그
- [`docs/deployments/`](./docs/deployments/) — 배포 기록
- [`docs/incidents/`](./docs/incidents/) — 장애·롤백 사후 보고

## 라이선스

(별도 명시 없음 — 사내 사용)
