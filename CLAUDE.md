# CLAUDE.md — sympopad_web

이 파일은 Claude Code가 새 세션에서 프로젝트를 빠르게 이해하기 위한 항상-읽힘 컨텍스트입니다.
사람도 같이 봅니다. 사실만 적고, 임시 메모/세션 결과는 `docs/sessions/`에 보관하세요.

---

## 1. 프로젝트 개요

**sympopad_web** — LG G Pad 5 (Android 9) 행사장 전용 태블릿 PWA 런처.

행사장에서 태블릿을 키오스크처럼 운영하기 위한 단일 페이지 앱. 행사 목록, 행사 상세, 콘솔/의장/진행자 링크 진입, WiFi QR 스캐너, 자동 업데이트, 로그 뷰어를 제공.

- **메인 타겟**: LG G Pad 5 (1920×1200, Android 9, Chrome ~71)
- **추가 지원**: Galaxy Tab A (Android 10+)
- **배포 형태**: APK (GitHub Releases) + PWA (Cloudflare Pages)
- **GitHub**: https://github.com/sympotalk/SympotalkLauncher-APK

---

## 2. 기술 스택 및 아키텍처

| 영역 | 기술 |
|---|---|
| 프론트엔드 | 순수 HTML / CSS / Vanilla JS (ES5 스타일) — 단일 `index.html` 130KB+ SPA |
| 외부 라이브러리 | jsQR (QR 스캐너, CDN 로드) |
| Service Worker | `sw.js` — 캐싱 + offline 폴백 (cache-first) |
| Android 래퍼 | Java + WebView (`android-app/`, Gradle 8.6, JDK 17, minSdk 28) |
| 엣지 함수 | Cloudflare Pages Functions (`functions/api/*.js` — events, rooms, health) |
| DB | Supabase (sympotalkv2 프로젝트) — REST + 마이그레이션은 `supabase/migrations/` |
| CI/CD | GitHub Actions — APK 빌드 + GitHub Release + Cloudflare Pages 자동 배포 |

> **중요 제약**: Android 9 / Chrome ~71 호환. ES2020+ 문법(`?.`, `??`, `??=`, `Array.at`, `String.replaceAll`, 사설 class field 등) **사용 금지**.

---

## 3. 외부 서비스

| 서비스 | 용도 | 위치 |
|---|---|---|
| **Supabase** (sympotalkv2) | 행사/룸 데이터, REST | `bpxqnfwuvhlaottcnpie.supabase.co` |
| **Cloudflare Pages** | 정적 호스팅 + Functions | `https://sympotalklauncher-apk.pages.dev` |
| **GitHub Releases** | APK 배포 | `sympotalk/SympotalkLauncher-APK/releases` |
| **GitHub Actions** | APK 빌드 자동화 | `.github/workflows/` |

Supabase 프로젝트 ID와 Anon key는 클라이언트 키이며 메모리(MEMORY.md)에 보관.

---

## 4. 환경변수 / 시크릿

이 레포는 `.env` 파일을 사용하지 않습니다. 모든 클라이언트-안전 값은 `index.html` 내부 또는 Cloudflare Pages 빌드 환경에서 결정됩니다.

CI(GitHub Actions)에서 사용하는 시크릿:

| 이름 | 용도 | 위치 |
|---|---|---|
| `KEYSTORE_PASSWORD` | Android APK 서명 keystore 비밀번호 | GitHub Actions secrets |
| `KEY_ALIAS` | keystore alias | GitHub Actions secrets |
| `KEY_PASSWORD` | key 비밀번호 | GitHub Actions secrets |

> 이 파일에는 **절대로 시크릿 값 자체를 적지 마세요.** 키 이름만 기록.

---

## 5. 디렉토리 구조

```
sympopad_web/
├── index.html              # 메인 SPA (130KB+, 모든 뷰가 한 파일에)
├── manifest.json           # PWA 매니페스트
├── sw.js                   # Service Worker
├── version.txt             # 현재 릴리스 버전 (build.gradle versionName과 동기화 필수)
├── _headers                # Cloudflare Pages 헤더
├── generate-icons.html     # 아이콘 생성용 임시 도구
├── android-app/            # Android WebView 래퍼
│   ├── app/build.gradle    # versionCode/versionName (CI가 version.txt와 일치 검증)
│   ├── app/src/main/java/  # Java 소스 (MainActivity, WifiHelper, AppUpdateManager)
│   └── release.keystore    # 공용 서명 keystore (의도적으로 repo 포함)
├── functions/
│   └── api/                # Cloudflare Pages Functions
│       ├── _shared.js
│       ├── events.js
│       ├── rooms.js
│       └── health.js
├── supabase/
│   └── migrations/         # SQL 마이그레이션
├── .github/workflows/
│   ├── build-and-deploy.yml  # main 푸시 → APK 빌드 + GitHub Release
│   ├── release.yml
│   └── setup-keystore.yml
├── .claude/
│   ├── settings.json       # 팀 공유 권한 (커밋 대상)
│   ├── settings.local.json # 개인 권한 (gitignore)
│   ├── commands/           # 슬래시 커맨드 정의
│   └── worktrees/          # 자동 생성 워크트리 (gitignore)
├── docs/
│   ├── CHANGELOG.md        # Keep a Changelog 형식
│   ├── ISSUES.md           # 미해결 이슈 트래커
│   ├── sessions/           # /save-session 출력
│   ├── deployments/        # /deploy 로그
│   └── incidents/          # /rollback 사후 보고
├── HANDOFF.md              # 세션 간 인수인계 (선택, 현재 untracked)
└── CLAUDE.md               # 이 파일
```

---

## 6. 주요 명령어

이 프로젝트에는 npm/pnpm/yarn이 없습니다. 빌드는 GitHub Actions가 담당.

### 로컬 개발 / 검증

```bash
# 로컬 정적 서버 (HTTPS 필요 — file:// 불가, Service Worker 때문)
# 옵션 1: VS Code Live Server (자동 https 옵션)
# 옵션 2: 간단한 https 서버 (python 3)
python -m http.server 8080  # http는 Service Worker만 못 씀, QR 카메라는 localhost 에서 동작

# 버전 일관성 수동 확인
grep -E "versionName|versionCode" android-app/app/build.gradle
cat version.txt
```

### Android APK 로컬 빌드 (CI가 대신 함, 보통 불필요)

```bash
cd android-app
./gradlew assembleDebug
# APK: android-app/app/build/outputs/apk/debug/app-debug.apk
```

### Git / 배포

```bash
git status
git push origin main          # → CI 자동: APK 빌드 + Release + Cloudflare 배포
gh run watch                  # 빌드 진행 상황
gh release view v1.0.43       # 배포된 릴리스 확인
```

### Supabase

```bash
# Cloud (Anthropic Connector / mcp__supabase 활용 권장)
# 마이그레이션 파일은 supabase/migrations/에 SQL로 추가
```

---

## 7. 코딩 컨벤션

- **JS 문법 레벨**: ES5 (Android 9 Chrome ~71). `var`, `function`, `if/else`, `try/catch`만 사용. **`?.`, `??`, `?.()` 금지.**
- **CSS**: 단일 `<style>` 블록(`index.html`). 섹션 주석(`/* ─── 영역 ─── */`)으로 구분.
- **Korean comments**: 코드/주석/커밋 메시지 모두 한국어 우선.
- **커밋 메시지**: Conventional Commits + 한국어 + 버전 suffix.
  - 예: `feat: 비정상 종료 감지 + 태블릿 레이아웃 꽉참 (v1.0.43)`
  - 타입: `feat:` `fix:` `chore:` `refactor:` `docs:`
- **버전 동기화 (필수)**: `version.txt` ↔ `android-app/app/build.gradle` (versionName + versionCode bump). CI가 일치 검증.
- **자동 감사 규칙**: 기능 추가/리팩터링/버전 bump 후 **커밋 전** `general-purpose` 서브에이전트로 코드 감사. 감사는 리포트만, 코드 수정은 본 세션. (`memory/feedback_auto_audit.md`)
- **백업 우선**: 기존 파일 덮어쓸 때는 `.backup` 확장자.

---

## 8. 최근 변경사항 (최신 5개)

| 버전 | 날짜 | 요약 | 커밋 |
|---|---|---|---|
| v1.0.43 | 2026-04-21 | 비정상 종료 감지 + 태블릿 레이아웃 꽉참 + sw.js v2 | 170446e |
| v1.0.42 | 2026-04-16 | 앱 로그 시스템 — 자동 수집 + 친화적 설명 + 설정 뷰어 | 789cbcc |
| v1.0.41 |  | QR 스캔 UX 자동 시작 + 인식 성능 개선 | f3f6ab4 |
| v1.0.40 |  | 상단/하단 공백 제거 — 키보드 상태별 플래그 동적 전환 | 403080d |
| v1.0.39 |  | 가상키보드 입력창 가림 근본 해결 | ed8d388 |

전체 changelog: `docs/CHANGELOG.md` 또는 `git log --oneline`.

---

## 9. 알려진 이슈

상세 트래킹은 `docs/ISSUES.md`. 요약:

- **P1**: `visibilitychange` 리스너 3중 등록 — 디스패처 통합 필요
- **P1**: `pagehide` 정상 종료 시 INFO 로그 누적 — 로그 버퍼 소진 위험
- **P2**: `sw.js` cache-first 패턴 — 매 릴리스 CACHE_NAME bump 강제

P0는 현재 없음.

---

## 10. Claude Code 자동화

이 프로젝트에는 다음 슬래시 커맨드가 정의되어 있습니다 (`.claude/commands/`):

| 커맨드 | 용도 |
|---|---|
| `/init-context` | 새 세션 시작 시 컨텍스트 복원 |
| `/save-session` | 작업 종료 시 세션 로그 저장 + CHANGELOG 갱신 |
| `/sync-github` | 의미있는 단위로 커밋 + 푸시 + 이슈 등록 |
| `/quick-save` | save-session + sync-github 한 번에 |
| `/deploy` | 사전검증 → 빌드 → 배포 → health check (감지된 플랫폼 자동 사용) |
| `/deploy-preview` | 현재 브랜치로 프리뷰 배포 |
| `/rollback` | 직전 안정 버전으로 롤백 + incident 기록 |

**새 세션 시작 시 권장**: `/init-context` 실행하여 최근 세션 로그/이슈/커밋을 자동으로 복원.
