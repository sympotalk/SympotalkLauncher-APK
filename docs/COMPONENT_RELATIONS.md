# 컴포넌트 관계 문서

세 컴포넌트(런처, DPC, adb-deployer)가 어떻게 연결되는지 기록합니다.
변경 시 영향 범위를 파악하고 동시 수정이 필요한 지점을 빠르게 찾기 위한 문서입니다.

---

## 컴포넌트 개요

| 컴포넌트 | 위치 | 현재 버전 | 역할 |
|---|---|---|---|
| **런처** | `android-app/` | v1.1.9 (versionCode 49) | 태블릿에서 실행되는 행사장 키오스크 앱 |
| **DPC** | `dpc-app/` | v0.2.0 (versionCode 2) | Device Owner 앱 — 런처 자동 업데이트 담당 |
| **adb-deployer** | `adb-deployer/` | v0.3.0 | PC에서 실행하는 초기 세팅 도구 (Electron) |

---

## 관계도

```
┌──────────────────────────────────────────────────┐
│  PC (Windows)                                    │
│                                                  │
│  adb-deployer                                    │
│  ┌─────────────────────────────────────────────┐ │
│  │ Setup Wizard                                │ │
│  │  Step 1. DPC APK 설치 (adb install)         │ │
│  │  Step 2. DO 등록 (dpm set-device-owner)     │ │──── USB/ADB ────►  태블릿
│  │          + PACKAGE_USAGE_STATS 자동 부여    │ │
│  │  Step 3. 런처 APK 설치 (adb install)        │ │
│  └─────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────┐
│  태블릿 (Android 9, LG G Pad 5)                  │
│                                                  │
│  ┌──────────────┐   Broadcast    ┌─────────────┐ │
│  │    런처      │ ─────────────► │     DPC     │ │
│  │  (launcher)  │  ACTION_CHECK  │   (dpc-app) │ │
│  │              │  _UPDATE       │             │ │
│  │  업데이트    │                │ GitHub API  │ │
│  │  버튼 클릭   │                │ 체크        │ │
│  │              │                │ APK 다운로드│ │
│  │              │                │ Silent      │ │
│  │  ◄── 재시작 ─┼────────────────┤ Install     │ │
│  └──────────────┘                └─────────────┘ │
│         ▲                               │        │
│         │         GitHub Releases       │        │
│         └───── APK 자동 업데이트 ◄──────┘        │
└──────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────┐
│  GitHub (sympotalk/SympotalkLauncher-APK)         │
│                                                  │
│  Releases                                        │
│  ├─ 태그: v1.1.9                                 │
│  │   ├─ SympotalkLauncher.apk  (런처용 고정명)   │
│  │   └─ SympotalkLauncher-v1.1.9.apk (버전명)   │
│  └─ 태그: dpc-latest                            │
│      └─ SympotalkDPC.apk                        │
└──────────────────────────────────────────────────┘
```

---

## 하드코딩된 연결 지점 (동시 수정 필요)

### 패키지명

| 값 | 선언 위치 | 참조 위치 |
|---|---|---|
| `com.sympotalk.launcher` | `android-app/app/build.gradle` applicationId | `dpc-app/app/build.gradle` LAUNCHER_PACKAGE<br>`adb-deployer/src/main/adb/DeviceInspector.ts:17`<br>`adb-deployer/src/renderer/views/CommandView.tsx:21,23` |
| `com.sympotalk.dpc` | `dpc-app/app/build.gradle` applicationId | `android-app/.../MainActivity.java:748`<br>`adb-deployer/src/main/adb/DeviceInspector.ts:16,48`<br>`adb-deployer/src/main/security/CommandGuard.ts:44`<br>`adb-deployer/src/renderer/views/CommandView.tsx:15,19`<br>`adb-deployer/src/renderer/views/SetupWizardView.tsx:51,76` |
| `com.sympotalk.dpc/.DpcAdminReceiver` | `dpc-app/.../DpcAdminReceiver.java` | `adb-deployer/src/renderer/views/CommandView.tsx:15`<br>`adb-deployer/src/renderer/views/SetupWizardView.tsx:76` |

> **패키지명 변경 시**: 위 표의 모든 위치를 동시에 수정해야 합니다.

### Broadcast 액션

| 값 | 정의 위치 | 송신 위치 | 수신 위치 |
|---|---|---|---|
| `com.sympotalk.dpc.ACTION_CHECK_UPDATE` | `dpc-app/.../DpcUpdateService.java:17` | `android-app/.../MainActivity.java:749` | `dpc-app/.../UpdateTriggerReceiver.java`<br>`dpc-app/AndroidManifest.xml:57` |

### APK 파일명 (GitHub Releases asset)

| 값 | 생성 위치 | 참조 위치 |
|---|---|---|
| `SympotalkLauncher.apk` | `.github/workflows/build-and-deploy.yml` | `android-app/.../AppUpdateManager.java:195`<br>`dpc-app/.../DpcUpdateService.java:198` |

> **파일명 변경 시**: CI 워크플로 + AppUpdateManager + DpcUpdateService 동시 수정.

### GitHub 저장소

| 값 | 위치 |
|---|---|
| `sympotalk/SympotalkLauncher-APK` | `android-app/app/build.gradle` GITHUB_REPO<br>`dpc-app/app/build.gradle` GITHUB_REPO |

---

## 버전 관리 규칙

### 런처 (android-app)

세 파일을 **항상 동시에** 수정해야 CI 버전 일치 검증을 통과합니다.

```
version.txt                          ← 웹 콘텐츠 업데이트 트리거
android-app/app/build.gradle         ← versionName + versionCode
sw.js                                ← CACHE_NAME 버전 (index.html 변경 시)
```

| 변경 종류 | 올릴 숫자 |
|---|---|
| 버그픽스, UI 소수정 | PATCH (+0.0.1) |
| 기능 추가, Java 변경 | PATCH (+0.0.1) |
| UI 대규모 개편 | MINOR (+0.1.0) |

### DPC (dpc-app)

`dpc-app/app/build.gradle` 만 관리 (version.txt 없음, CI 버전 검증 없음).

| 변경 종류 | 올릴 숫자 |
|---|---|
| 버그픽스 | PATCH |
| 기능 추가 (설치 흐름 변경 등) | MINOR |

### adb-deployer

`adb-deployer/package.json` 의 `"version"` 하나만 관리.
빌드 결과물 파일명(`Sympotalk ADB Deployer X.Y.Z.exe`)에 자동 반영.

| 변경 종류 | 올릴 숫자 |
|---|---|
| 버그픽스, 텍스트 수정 | PATCH |
| 새 기능, UI 개편 | MINOR |
| 아키텍처 변경 | MAJOR |

---

## 변경 시나리오별 동시 수정 체크리스트

### A. 런처 UI/기능 변경

```
[ ] index.html
[ ] sw.js — CACHE_NAME 버전 bump (index.html 변경 시)
[ ] version.txt — 버전 bump
[ ] android-app/app/build.gradle — versionName + versionCode bump
```

### B. 런처 Java 코드 변경 (업데이트 로직 등)

```
[ ] android-app/app/src/main/java/...
[ ] version.txt — 버전 bump
[ ] android-app/app/build.gradle — versionName + versionCode bump
[ ] DPC 연동 변경 시 → dpc-app도 확인 (Broadcast 액션, 패키지명)
```

### C. DPC 업데이트 로직 변경

```
[ ] dpc-app/app/src/main/java/com/sympotalk/dpc/DpcUpdateService.java
[ ] dpc-app/app/src/main/java/com/sympotalk/dpc/SilentInstaller.java
[ ] dpc-app/app/build.gradle — versionCode bump
[ ] APK 파일명 변경 시 → AppUpdateManager.java도 동시 수정
```

### D. DO 등록 절차 변경 (권한 추가 등)

```
[ ] dpc-app/app/src/main/AndroidManifest.xml — 권한 선언
[ ] adb-deployer/src/renderer/views/SetupWizardView.tsx — 마법사 단계
[ ] adb-deployer/src/renderer/views/CommandView.tsx — 수동 프리셋
[ ] adb-deployer/package.json — 버전 bump
```

### E. 패키지명 변경

```
[ ] android-app/app/build.gradle — applicationId
[ ] dpc-app/app/build.gradle — applicationId + LAUNCHER_PACKAGE
[ ] android-app/.../MainActivity.java — "com.sympotalk.dpc" 하드코딩 3곳
[ ] dpc-app/.../UpdateTriggerReceiver.java — action명
[ ] dpc-app/AndroidManifest.xml — intent-filter action
[ ] adb-deployer/src/main/adb/DeviceInspector.ts
[ ] adb-deployer/src/main/security/CommandGuard.ts
[ ] adb-deployer/src/renderer/views/CommandView.tsx
[ ] adb-deployer/src/renderer/views/SetupWizardView.tsx
```

---

## 자동 업데이트 전체 흐름

### 초기 세팅 (1회, adb-deployer 사용)

```
1. adb-deployer 실행 (PC)
2. Setup Wizard Step 1: DPC APK 설치 (adb install)
3. Setup Wizard Step 2: DO 등록 (dpm set-device-owner)
                      + PACKAGE_USAGE_STATS 자동 부여
4. Setup Wizard Step 3: 런처 APK 설치 (adb install)
```

### 이후 자동 업데이트 (adb 불필요)

```
트리거 A — 부팅 시:
  BootReceiver → DpcUpdateService.handleCheckUpdate()

트리거 B — 런처 업데이트 버튼 클릭:
  MainActivity.downloadAndInstallApk()
    → DO 감지 → Broadcast(ACTION_CHECK_UPDATE)
    → UpdateTriggerReceiver → DpcUpdateService.handleCheckUpdate()

공통 흐름:
  IDLE → CHECKING: GitHub API /releases/latest 조회
  → DOWNLOADING:  ApkDownloader (SHA-256 검증 포함)
  → INSTALLING:   SilentInstaller (PackageInstaller 세션, 사용자 승인 불필요)
  → HEALTH_WAIT:  HealthWatchdog 90초 감시
  → IDLE:         성공 (last_known_good 갱신)
  → ROLLING_BACK: 실패 시 저장된 APK 재설치
```

### DO 미등록 기기 (fallback)

```
런처 업데이트 버튼 클릭
  → DO 없음 감지
  → AppUpdateManager.downloadApk() (DownloadManager)
  → installApk() → ACTION_VIEW Intent
  → 시스템 패키지 인스톨러 UI → 사용자 "설치" 버튼 필요
```

---

## CI/CD

| 트리거 | 워크플로 | 결과물 |
|---|---|---|
| `main` 푸시 | `build-and-deploy.yml` | 런처 APK → GitHub Release (`v{버전}` 태그)<br>Cloudflare Pages 배포 |
| `main` 푸시 | `build-dpc.yml` | DPC APK → GitHub Release (`dpc-latest` 태그) |
| 수동 | adb-deployer 로컬 빌드 | `dist/Sympotalk ADB Deployer X.Y.Z.exe` |

> adb-deployer는 CI 자동 빌드 없음 — 로컬에서 `npm run build && npx electron-builder --win portable nsis` 수동 실행.
