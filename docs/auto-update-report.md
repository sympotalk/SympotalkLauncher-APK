## Final Logic Check (6차 — 2026-05-09 코덱스 5차 정적 QA 본문 반영)

5차 정적 QA가 식별한 12개 항목을 본문에 반영했다. 본 섹션은 **본문 어디가 어떻게 바뀌었는지**와
**코덱스 제안 중 그대로 채택 불가능한 부분에 대한 설계 판단**을 함께 정리한다.

### 본문에 반영한 항목 (12개 전건)

| # | 코덱스 5차 지적 | 본문 반영 위치 |
|---|---------------|---------------|
| 1 | `renameTo()` 비-atomic | §다운로드 원자성 — `Files.move(ATOMIC_MOVE, REPLACE_EXISTING)` 패턴으로 교체 + AtomicMoveNotSupportedException 분기 |
| 2 | 웹 콘텐츠 per-file hash + version-pinned URL 부재 | §APK 롤백 + Manifest JSON 구조에 `webFiles[]` / `lastKnownGoodWebFiles[]` 신설. CI에서 `/v/<version>/` immutable 스냅샷 배포 |
| 3 | PackageInstaller 고수준 status 단정 매핑 | §INSTALL_FAILED 코드 매핑 — `EXTRA_STATUS_MESSAGE` + `EXTRA_LEGACY_STATUS` + PreInstallChecks 결합 분기로 재작성. CONFLICT/INCOMPATIBLE 단정 제거 |
| 4 | DPC + Launcher 동일 keystore "필수" 단정 | §DPC + Launcher 서명 정책 — **별도 keystore + signing certificate pinning** 으로 전환. Launcher의 release.keystore는 변경 금지 |
| 5 | IPC read-only/report 신뢰 경계 모호 | §IPC 전략 — caller UID/cert pinning + signature permission + session/version/ts 토큰 + 거부 카운터 추가 |
| 6 | crash rollback이 Launcher report에만 의존 | §롤백 트리거 — DPC HealthWatchdog 추가. startup_pending marker + ANR 폴링 + process death 폴링 + BOOT_COMPLETED 90초 timeout |
| 7 | Manifest 서버 P0/수백 대 검토 우선순위 충돌 | §Update Manifest 서버 — DPC 자동 업데이트의 P0 필수로 확정. 기존 `AppUpdateManager.java` 60분 캐시는 수동 업데이트 보완책으로만 유지 |
| 8 | Worker가 release JSON만으로 sha256/versionCode 생성 불가 | §Manifest 서버 책임 분리 — Worker는 KV 단순 서빙. CI에서 sha256sum + versionCode + manifest artifact 생성 후 KV PUT |
| 9 | Android 버전별 FGS/notification/startActivity 제약 혼재 | §Android 버전별 필요 조건 — 4개 항목(PackageInstaller 권한/FGS 생존성/POST_NOTIFICATIONS/PendingIntent flag)으로 분리 |
| 10 | `UpdateManager.java`를 DPC 직접 모델로 보기 부족 | §참고 코드베이스 — "네트워크 골격만 참고. `Files.move(ATOMIC_MOVE)` + per-file sha256 + version-pinned URL은 DPC에서 재구현"으로 명시 |
| 11 | Device Owner 등록 절차 축약 | §기기 등록 방법 — 사전 조건 체크리스트, ADB/QR 옵션, QR provisioning JSON payload, 실패 디버깅 포인트 추가 |
| 12 | APK rollback 후 web rollback 순서가 crash loop 유발 | §롤백 전략 — `rollback_in_progress` marker 도입. web staging 우선 → APK 설치 → ATOMIC_MOVE pointer 전환 → marker 해제 → Launcher 재시작 순서로 변경. Launcher 측 `RollbackMarkerWatcher`가 marker true이면 web 로드 보류 |

### 코덱스 제안 vs 기존 설계 — 논리적 충돌 검토 결과

**① "동일 keystore 필수" → "별도 keystore + pinning" — 코덱스 제안 채택 (기존 설계 수정)**

이전 3차 검토에서 "DPC + Launcher 동일 keystore 필수"라고 명시한 것은 다음 중 어느 것도 실제로 요구하지 않는다:
- DPC의 PackageInstaller 무음 설치는 동일 서명 불필요 (packageName 기반)
- Bound Service `signature` permission은 동일 서명 시에만 사용 가능 — 그러나 caller UID + cert pinning으로 동일 효과를 더 안전하게 달성 가능
- `sharedUserId`는 Android 10+에서 deprecated, 신규 도입 권장 안 됨

→ "필수" 단정은 잘못된 결합도. **별도 keystore가 보안상 우월**하며 DPC 코드의 cert pinning이 이를 대체.

**② "GitHub 60분 캐시면 수십 대 충분" vs "Manifest 서버 P0" — 우선순위 충돌, 코덱스 정리 채택**

기존 문서가 §Update Manifest 서버에서는 "수십 대는 문제 없음 → 수백 대 이상에서 검토"라고 P2로 표기하면서 §최종 추가 개발 항목에서는 P0으로 표기한 것은 자가-모순.

→ **DPC 자동 업데이트 흐름**(sha256/versionCode/rollback URL/per-file hash 필요)에서 manifest 서버는 P0 필수.
→ **수동 업데이트** 흐름(`AppUpdateManager.java`)은 GitHub Releases API + 60분 캐시로 충분 — 그대로 유지.
→ 두 흐름이 별개 데이터 출처를 사용하도록 명시적으로 분리.

**③ "INSTALL_FAILED_UPDATE_INCOMPATIBLE → 즉시 rollback" (3차 GPT) vs "VERIFYING 통과 시 재부팅 후 재시도" (3차 자체 조정) vs "사전 검증 결과별 분기" (5차 코덱스) — 5차 안 채택**

3차에서 "VERIFYING 통과 후 발생이면 재부팅 후 재시도"라고 했으나, 실제로는 그것 외에도 minSdk/ABI 미달 같은 케이스가 모두 `STATUS_FAILURE_INCOMPATIBLE`로 묶인다.

→ **VERIFYING 단계에서 미리 4종 사전검증(signature/versionCode/minSdk/ABI)을 수행해 결과를 PreInstallChecks로 저장**하고, 실패 콜백에서 이 결과를 함께 보고 분기하면 단정 없이 정확한 대응이 가능.

**④ "AtomicMoveNotSupportedException 처리" — 코덱스 제안에 명시되진 않았으나 본문 반영 시 추가 발견**

`Files.move(ATOMIC_MOVE)`는 **다른 파티션 간 이동 시 무조건 실패**한다. tmp/live가 동일 cacheDir이면 안전하지만, 향후 외부 저장소 옵션 등이 추가되면 깨질 수 있어 명시적 IOException 분기 추가.

**⑤ rollback_in_progress marker 도입 — 코덱스 제안 그대로 채택, Launcher 측 변경 발생**

기존 Launcher는 `MainActivity.onCreate()`에서 무조건 `loadApp()` 호출. 이제 marker를 먼저 검사하고 true이면 maintenance UI만 표시.
→ Launcher 측 `RollbackMarkerWatcher` 추가 작업 항목 신설.

### 본문 변경 요약

| 섹션 | 변경 |
|------|------|
| §방법1 필요한 것 | "공통 keystore" → "분리된 keystore + pinning" |
| §DPC + Launcher 서명 정책 | 전면 개정 — 별도 keystore + cert pinning + signature permission + session token |
| §Android 버전별 필요 조건 | 4개 항목 분리 (PackageInstaller / FGS / POST_NOTIFICATIONS / PendingIntent) |
| §기기 등록 방법 | 사전 조건 체크리스트 + QR payload JSON + 실패 디버깅 포인트 추가 |
| §전체 동작 흐름 | staging/marker/watchdog 반영하여 ⑤~⑬ 단계로 재구성 |
| §다운로드 원자성 | `Files.move(ATOMIC_MOVE, REPLACE_EXISTING)` + AtomicMoveNotSupportedException 분기 |
| §INSTALL_FAILED 코드 매핑 | status × 부가 신호 × PreInstallChecks 결합 표로 재작성. CONFLICT/INCOMPATIBLE 단정 제거 |
| §롤백 트리거 | DPC HealthWatchdog 추가, rollback loop 방지 규칙 |
| §APK 롤백 + web content | rollback_in_progress marker + staging + ATOMIC_MOVE pointer 전환 + Launcher RollbackMarkerWatcher |
| §IPC 전략 | caller 검증 + signature permission + session token + 거부 카운터 |
| §Update Manifest 서버 | 책임 분리 (CI vs Worker) + per-file hash JSON + CI 단계 + stale fallback |
| §최종 권장 아키텍처 | CI Pipeline + Pages/R2 version-pinned + DPC HealthWatchdog/SignatureVerifier 신설 |
| §최종 추가 개발 항목 | P0 항목을 코덱스 우선순위와 정합되도록 재정리 |
| §상태 머신 | `HEALTH_WAIT` 신설, SharedPreferences 키 목록 추가 |
| §참고 코드베이스 | DPC 별도 keystore 명시, `UpdateManager.java` 재사용 범위 축소, `scripts/build-manifest.js` 신규 |

### 다음 단계 권장

1. **DPC 프로토타입 1차**: Downloader + Verifier(사전 4종 검증) + PackageInstaller + StateMachine + HealthWatchdog 골격까지 — G Pad 5 1대에서 무음 설치 + cold-start crash 시뮬레이션.
2. **CI manifest publish 파이프라인**: `scripts/build-manifest.js` + `/v/<version>/` Pages 배포 + Cloudflare KV PUT.
3. **Launcher 측 변경 최소 단위**: `RollbackMarkerWatcher` + `HealthReporter`만 먼저 추가하고, IPC session token은 후속 단계에서.

---

## Final Logic Check (5차 — 2026-05-08 정적 QA 재검토 및 재업로드)

클로드코드와 GPT 피드백을 반영한 기존 리포트를 코드베이스와 대조해 다시 검토했다. 이번 검토는 구현을 바로 수정한 것이 아니라, **문서 최상단에 최종 설계 보완점과 작업 우선순위를 남기는 정적 QA 정리**다.

> 재업로드 확인: 이 섹션은 이전 4차 정적 QA 요약을 기준으로, GitHub 업로드 누락이 없도록 검토 범위·핵심 결론·우선순위·최종 판단을 한 번 더 명시적으로 정리한 최종본이다.

### 검토 범위

| 범위 | 확인 내용 |
|------|----------|
| `docs/auto-update-report.md` | 자동 APK 업데이트 설계, DPC 구조, rollback, manifest, retry/backoff 문구 재검토 |
| `AppUpdateManager.java` | GitHub Releases 조회, 60분 캐시, DownloadManager 기반 APK 수동 설치 흐름 확인 |
| `UpdateManager.java` | HttpURLConnection 기반 웹 콘텐츠 다운로드, `.tmp` 파일 사용, version.txt 비교 로직 확인 |
| `MainActivity.java` | WebView 로드 순서, JS bridge, 앱/웹 업데이트 트리거 위치 확인 |
| `.github/workflows/build-and-deploy.yml` | 현재 APK 빌드·Release 업로드 절차와 sha256/manifest 생성 누락 여부 확인 |

### 핵심 결론

| # | 발견 항목 | 영향 | 최종 조치 방향 |
|---|----------|------|----------------|
| 1 | `.tmp → delete → renameTo()` 패턴은 실제 atomic 교체가 아님 | rename 실패 시 정상 파일 손실 가능 | `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)` + 실패 검증으로 변경 필요 |
| 2 | 웹 콘텐츠 rollback에 파일별 hash·버전 고정 URL이 없음 | APK와 web content 혼합 버전 로드 가능 | `webFiles[]`/`lastKnownGoodWebFiles[]`와 staging 검증 절차 추가 필요 |
| 3 | `PackageInstaller` 실패 코드 매핑이 고수준 status에 과의존 | downgrade/signature/SDK/ABI 원인 오진 가능 | `EXTRA_STATUS_MESSAGE`, legacy status, 사전 검증 결과를 함께 기록 |
| 4 | DPC와 Launcher 동일 keystore를 “필수”로 단정 | 보안 키 blast radius 증가, 실제 DO 설치 조건과 혼동 | DPC 별도 keystore + Launcher signing certificate pinning 권장 |
| 5 | IPC 설명이 read-only와 report API 사이에서 충돌 | Launcher report 위조/오동작 시 rollback 판단 왜곡 | query API와 report API 분리, caller UID/package 검증 추가 |
| 6 | crash rollback이 Launcher report에 의존 | cold-start crash/OOM/ANR 감지 누락 가능 | DPC 주도 health watchdog + startup marker 추가 |
| 7 | Manifest 서버 필요성이 문서 내에서 P0와 “수백 대 이상 검토”로 충돌 | DPC 자동 업데이트 필수 데이터 출처가 모호 | DPC 자동 업데이트에서는 Manifest 서버를 P0 필수로 정리 |
| 8 | Worker 예시가 sha256/versionCode를 release JSON만으로 만든다고 가정 | 부정확한 manifest 생성 가능 | CI에서 sha256/versionCode/update-manifest artifact 생성 |
| 9 | Android 버전별 FGS/notification/startActivity 제약 설명이 혼재 | 불필요한 Activity launch 또는 권한 요구로 설계 혼선 | PackageInstaller 권한, FGS 생존성, notification 권한을 분리 설명 |
| 10 | `UpdateManager.java`를 DPC Downloader 직접 모델로 보기에는 검증 부족 | partial file, hash 누락, rename 실패 패턴 전파 위험 | 네트워크 골격만 참고하고 DPC 필수 검증 조건 별도 정의 |
| 11 | Device Owner 등록 절차가 축약됨 | 현장 등록 실패 원인 추적 어려움 | DPC 선설치, DeviceAdminReceiver, QR provisioning payload, 실패 조건 추가 |
| 12 | APK rollback 후 web rollback 순서가 crash loop를 만들 수 있음 | 구버전 APK가 신버전 web을 먼저 로드 가능 | `rollback_in_progress` marker와 web-first/staging rollback 순서 추가 |

### 우선순위별 작업 정리

#### P0 — 설계 오류 또는 현장 장애로 직결되는 항목

1. **Atomic file 교체 재정의**
   - `renameTo()` 단독 사용 금지.
   - 같은 디렉터리 staging 후 `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)` 사용.
   - move 실패 시 기존 live 파일을 보존하고 `.tmp`를 정리.

2. **웹 콘텐츠 transaction/rollback 보강**
   - `index.html`, `sw.js`, `manifest.json`, `version.txt`를 개별 즉시 교체하지 않음.
   - staging 디렉터리에 전체 다운로드 → 파일별 sha256 검증 → live pointer/marker 전환.
   - rollback source는 현재 Cloudflare root가 아니라 버전 고정 URL 또는 파일별 manifest로 관리.

3. **PackageInstaller 오류 분류 고도화**
   - `EXTRA_STATUS`, `EXTRA_STATUS_MESSAGE`, 가능하면 legacy install code를 모두 저장.
   - `versionCode`, packageName, signing certificate, minSdk/ABI/feature 호환성은 설치 전 VERIFYING에서 선검증.
   - `STATUS_FAILURE_CONFLICT == version downgrade` 같은 단정은 제거하고 legacy/message 기반으로 분기.

4. **Manifest 서버를 DPC 자동 업데이트 필수 구성으로 고정**
   - GitHub Releases 직접 조회 + 60분 캐시는 기존 수동 업데이트 보완책으로만 유지.
   - DPC는 `sha256`, `versionCode`, `minNativeVersion`, rollback source, channel 정보를 Manifest 서버에서 받음.
   - CI가 `sha256sum`, Gradle `versionCode`, `versionName`, rollback 후보 정보를 manifest artifact/KV로 배포.

5. **Crash rollback watchdog 추가**
   - Launcher가 정상 report를 못 보내는 cold-start crash를 DPC가 감지해야 함.
   - `startup_pending(version, timestamp)` marker와 health timeout을 도입.
   - rollback 판단은 Launcher report 단독이 아니라 DPC 관측값과 결합.

#### P1 — 보안·운영 안정성 보강 항목

1. **DPC/Launcher 서명 정책 재정리**
   - Launcher APK는 기존 `android-app/release.keystore`로 계속 서명해야 업데이트 가능.
   - DPC는 별도 keystore 사용을 기본 권장.
   - DPC는 설치 대상 Launcher APK의 packageName과 signing certificate digest를 pinning.

2. **IPC 신뢰 경계 명확화**
   - DPC Bound Service는 explicit binding, caller UID/package 검증, signature permission을 사용.
   - Launcher는 명령을 내리지 않고 검증된 report만 제출.
   - report API는 stale report 방지를 위해 version/session/timestamp를 포함.

3. **Android 버전별 제약 분리**
   - `PackageInstaller.Session` 설치 권한 조건, Foreground Service 생존성, notification runtime permission을 별도 항목으로 설명.
   - Android 13+ 알림 권한 거부 시에도 DPC 상태 머신은 동작해야 함.

4. **Device Owner provisioning 절차 상세화**
   - DPC APK 선설치, `DeviceAdminReceiver`, admin XML, `dpm set-device-owner` 실패 조건을 문서화.
   - QR provisioning payload에는 package name, download URL, checksum, admin component를 포함.

#### P2 — 향후 확장 항목

1. `webDataSchema` 기반 IndexedDB/localStorage migration 정책 추가.
2. stable/beta channel 분리와 staged rollout 정책 추가.
3. 수백 대 이상 fleet 운영 시 manifest stale fallback, rollout percentage, device cohort 정책 추가.

### 기존 리포트에 대한 최종 판단

- **큰 방향성**: Device Owner + DPC + Manifest + PackageInstaller 기반 자동 업데이트 방향은 타당하다.
- **가장 위험한 문구**: “동일 keystore 필수”, “renameTo 원자적 교체”, “GitHub 캐시로 수십 대 충분”, “PackageInstaller status 단순 매핑”은 구현 전에 반드시 수정해야 한다.
- **다음 문서 개정 권장 순서**: 다운로드/rollback 원자성 → manifest/CI 생성 책임 → PackageInstaller 오류 분류 → DPC/Launcher 보안 경계 → Device Owner provisioning 상세화.

---

## Final Logic Check (3차 — 2026-05-09)

3차 GPT 피드백 5개 항목을 반영하여 아래와 같이 수정했다.

### 반영한 항목

| # | GPT 지적 | 조치 |
|---|----------|------|
| 1 | INSTALL_FAILED_* 코드별 대응 미정의 | §INSTALL_FAILED 코드 매핑 섹션 신설 |
| 2 | Retry backoff 전략 없음 | §재시도 전략 섹션 신설 (지수 백오프 + 회복 불가 코드 분리) |
| 3 | DownloadManager vs OkHttp 혼용 모호 | §다운로드 구현 섹션 신설 + 재사용 테이블 수정 |
| 4 | WebView 데이터 migration 위험 | §롤백 전략에 web content 다운그레이드 연동 추가 + 미래 고려사항 명시 |
| 5 | DPC + Launcher 동일 keystore 사용 명시 | §방법1 필요한 것 + §최종 아키텍처에 추가 |

### 논리적 충돌 3곳 — GPT 제안 조정

**① `INSTALL_FAILED_UPDATE_INCOMPATIBLE → rollback` 조건부 수정**

GPT는 이 오류 시 즉시 rollback을 권장했다.
그러나 VERIFYING 단계에서 이미 서명 검증을 통과한 상태에서 이 오류가 발생하면,
**같은 keystore로 서명된 rollback APK도 동일하게 실패한다.**

원인은 서명 불일치가 아니라 Android 시스템 캐시 불일치(드문 케이스)이므로:
→ rollback이 아닌 **시스템 재부팅 후 재시도** 가 올바른 대응.
→ 단, VERIFYING 단계를 건너뛰었거나 실패한 경우(서명 검증 미실시)라면 rollback 적합.
→ 코드 매핑 테이블에 조건 분기로 명시.

**② AppUpdateManager.java 재사용 범위 모호 → 명확히 분리**

기존 문서에 "AppUpdateManager.java — DPC 버전 체크 로직 재사용"이라 적혀 있었으나,
AppUpdateManager는 다운로드에 `DownloadManager`(시스템 서비스)를 쓰기 때문에
DPC에서 그대로 재사용하면 상태 제어, checksum, retry/backoff 구현이 어렵다.

→ **재사용 범위를 버전 비교 로직(isNewerVersion + 60분 캐시)으로 한정.**
→ **다운로드는 UpdateManager.java의 HttpURLConnection 패턴을 따름.**

**③ WebView 데이터 rollback 미연동 — GPT 제안보다 더 긴급**

GPT는 "향후 오프라인 캐시 강해지면 중요하다"고 했으나, 현재 설계에서도 발생 가능하다:

```
APK v1.1.8 설치 + web content v1.1.8 다운로드 완료
→ APK rollback to v1.1.7 (crash 감지)
→ 디스크에는 web content v1.1.8 잔류
→ APK v1.1.7이 web v1.1.8 로드
→ AndroidBridge.newMethod() undefined → JS 오류
```

→ **APK rollback 시 web content도 lastKnownGoodVersion으로 다운그레이드 연동 필수.**
→ `webDataSchema` 개념은 미래 고려사항으로 추가.

---

# 자동 패치 업데이트 구현 리포트

> 작성일: 2026-05-09 / 최종 수정: 2026-05-09 (3차 GPT 리뷰 반영)
> 목적: 현재 수동 APK 설치 방식을 자동 패치 방식으로 전환하기 위해 필요한 것 분석

---

## 현재 방식의 문제

현재 APK 업데이트 흐름:

```
사용자가 직접 "업데이트 확인" 버튼 클릭
  → GitHub Releases API 조회
  → APK 다운로드 (DownloadManager)
  → 시스템 설치 다이얼로그 표시
  → 사용자가 "설치" 버튼 클릭 (필수)
  → 설치 완료
```

**문제: 마지막 "사용자 설치 버튼 클릭"이 항상 필요하다.**
행사장 키오스크 용도상 참석자가 접근하면 안 되므로, 관리자 없이는 APK 업데이트가 불가능하다.

---

## 핵심 제약: Android 보안 모델

Android는 **일반 앱이 다른 앱을 사용자 동의 없이 설치하는 것을 원천 차단**한다.

Google Play가 가능한 이유:
- 시스템 앱으로 빌드됨 (`/system/priv-app/`)
- `INSTALL_PACKAGES` 권한 보유 (시스템 레벨 전용, `signature|privileged`)
- 제조사/Google이 사전 서명

일반 APK 배포 방식으로는 이 권한을 획득할 수 없다.
→ **구조적 변경 없이 "완전 자동 설치"는 불가능하다.**

---

## 구현 가능한 방법 3가지

---

### 방법 1. Android Enterprise — Device Owner 모드 ✅ 권장

#### 개념
Android 기업 관리 기능. 기기를 **"관리 기기"로 등록**하면
Device Policy Controller(DPC) 앱이 `PackageInstaller.Session` API를 호출할 수 있어
**사용자 동의 없이 APK 설치 가능**. Google Play 없이도 동작하며 추가 비용 없음.

#### Android 버전별 필요 조건 — 항목별 분리 (코덱스 5차 반영)

이전 표가 PackageInstaller 권한 / Foreground Service 생존성 / notification runtime permission을 한 줄에 섞어서
설계 혼선이 있었다. 세 항목은 **독립적**이며 각각 다른 OS 버전에 영향을 받는다.

##### A. PackageInstaller 권한 (Device Owner 필수)

| Android | PackageInstaller.Session으로 무음 설치 |
|---------|---------------------------------|
| 8.0+ | Device Owner 등록 시 무음 설치 가능 (현재 모든 대상 기기 포함) |
| 13+ | 추가 변경 없음 (`INSTALL_PACKAGES`는 여전히 시스템 전용) |

##### B. Foreground Service 생존성

| Android | DPC 백그라운드 동작 요건 |
|---------|------------------------|
| 8.0~9 | 일반 Service 가능, 단 system-killed 가능성 → `START_STICKY` 권장 |
| 10~11 | 백그라운드 `startActivity()` 제한 시작 → 설치 진행 중에는 FGS로 보호 |
| 12+ | FGS 시작 제한(`startForegroundService` 시점 제약) → DPC owner이므로 면제 |
| 14+ | FGS type 명시 필수 (`dataSync` 또는 `specialUse`) |

##### C. Notification Runtime Permission

| Android | `POST_NOTIFICATIONS` 권한 |
|---------|--------------------------|
| 12 이하 | 자동 부여 |
| 13+ | runtime permission. **거부되어도 DPC 상태 머신/설치 흐름은 영향 없음** — 알림만 표시되지 않음 |

##### D. PendingIntent flags

| Android | 변경 |
|---------|------|
| 12+ | `PendingIntent.FLAG_MUTABLE` 또는 `FLAG_IMMUTABLE` 명시 필수 |

> **현재 기기 매트릭스**: G Pad 5 (Android 9), 갤럭시탭 A (Android 10).
> Android 12+ 제약은 즉시 영향 없으나 신규 기기(13+, 14+) 추가 시 위 표대로 분기.

#### 필요한 것

| 항목 | 설명 |
|------|------|
| **DPC 앱 (별도 개발)** | Device Owner로 등록되는 관리 앱. `PackageInstaller.Session` 기반 무음 설치 |
| **기기 초기 등록 (1회)** | ADB 명령 1회 실행 또는 공장 초기화 후 QR 코드 스캔 |
| **Update Manifest 서버** | Cloudflare Worker로 버전·sha256·APK URL 제공 |
| **DPC ↔ 런처 통신** | Bound Service 기반 단방향 상태 공유 + caller UID/signature permission 검증 |
| **분리된 signing keystore** | **DPC는 Launcher와 별도 keystore 사용** (blast radius 축소). DPC가 Launcher의 packageName + signing certificate SHA-256을 pinning. (하단 §서명 정책 참조) |

#### 전체 동작 흐름 (코덱스 5차 — staging/marker/watchdog 반영)

```
DPC UpdateService (Foreground Service)
  ① 상태: IDLE
  ② Maintenance Window + 배터리/충전/화면 조건 확인
  ③ 상태: CHECKING → Manifest 서버 조회 (실패 시 stale fallback)
  ④ 새 버전 없음: IDLE로 복귀
  ⑤ 새 버전 있음: 상태: DOWNLOADING
     → HttpURLConnection으로 .tmp staging 다운로드 (실시간 sha256 계산)
     → Files.move(ATOMIC_MOVE)는 VERIFYING 통과 후 실행 (live는 손대지 않음)
  ⑥ 상태: VERIFYING — 사전 검증 4종
     → sha256 (다운로드 중 계산값과 manifest 비교)
     → Launcher signing certificate SHA-256 pinning 검증
     → versionCode (downgrade 차단)
     → minSdk + ABI 호환성
     → 통과: PreInstallChecks를 SharedPreferences에 저장 (실패 시 분기에 사용)
     → 실패: FAILED + 오류 코드 저장 + 지수 백오프 재시도 스케줄
  ⑦ Files.move(ATOMIC_MOVE)로 launcher.apk.tmp → launcher.apk 교체
  ⑧ 상태: INSTALLING (install mutex 획득)
     → DPC가 startup_pending(version, ts) marker 기록
     → PackageInstaller.Session 실행
     → 실패 콜백: status + EXTRA_STATUS_MESSAGE + legacy + PreInstallChecks로 분기
  ⑨ 설치 성공: 상태 HEALTH_WAIT
     → Launcher 자동 재시작
     → 60초 내 reportHealthOk(session, version, ts) 대기
  ⑩ HealthWatchdog 평가 (1분 폴링):
     → reportHealthOk 도착 + session/version/ts 검증 통과 → SUCCESS, last_known_good 갱신
     → 60초 초과 + Launcher 프로세스 부재 → cold-start crash → ROLLBACK_PENDING
     → ANR 감지 / process death 5분 내 3회 → ROLLBACK_PENDING
  ⑪ Launcher → reportCrashEvent(): UncaughtException 잡힌 crash 보고 (보조 신호)
     → 5분 내 3회 + watchdog 신호 결합 → ROLLBACK_PENDING
  ⑫ ROLLBACK_PENDING 흐름:
     → rollback_in_progress = true marker 기록
     → web staging 다운로드 (lastKnownGoodWebFiles[] per-file sha256)
     → APK 다운로드 + 검증 + 설치 (PackageInstaller)
     → 설치 SUCCESS 콜백에서 web staging → live ATOMIC_MOVE
     → version.txt 갱신, rollback_in_progress = false
     → Launcher 재시작 (marker false 확인 후 정상 web 로드)
     → 상태: ROLLBACK
  ⑬ rollback loop 방지: 동일 manifest version 재설치 시도 차단
```

#### 기기 등록 방법 — Device Owner provisioning 상세 (코덱스 5차 반영)

##### 사전 조건 체크리스트

| 조건 | 미충족 시 결과 |
|------|--------------|
| DPC APK가 기기에 **선설치**되어 있어야 함 | `set-device-owner` 실행 시 ClassNotFound |
| `AndroidManifest.xml`에 `DeviceAdminReceiver` 선언 + admin XML 명시 | provisioning 실패 |
| 기기에 **구글 계정 / 다른 user account 미연동** | `set-device-owner` 실패 (Android 보안 정책) |
| 기기에 다른 Device Owner가 등록되어 있지 않음 | `Already has device owner` 에러 |
| Android 9+ (현재 모든 대상 기기 충족) | API 미지원 |

##### 옵션 A: ADB 명령 (기존 기기, 개발/소규모 배포)

```bash
# 1. DPC APK 선설치
adb install dpc-app/build/outputs/apk/release/dpc-release.apk

# 2. Device Owner 등록 (admin component 정확히 명시)
adb shell dpm set-device-owner com.sympotalk.dpc/.SympotalkDeviceAdmin

# 3. 등록 확인
adb shell dumpsys device_policy | grep "Device Owner"
```

##### 옵션 B: QR Provisioning (대량/신규 기기)

공장 초기화 후 setup wizard에서 화면 6번 탭(또는 NFC) → QR 스캔으로 자동 진행.

QR 페이로드 (JSON):
```json
{
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME":
      "com.sympotalk.dpc/.SympotalkDeviceAdmin",
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION":
      "https://github.com/.../v1.0.0/dpc-release.apk",
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM":
      "BASE64_URL_SAFE_SHA256_OF_DPC_SIGNATURE",
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM":
      "BASE64_URL_SAFE_SHA256_OF_DPC_APK",
  "android.app.extra.PROVISIONING_SKIP_ENCRYPTION": false
}
```

> `SIGNATURE_CHECKSUM` / `PACKAGE_CHECKSUM`은 **base64-urlsafe(no padding)** 형식이어야 함.
> CI 빌드 단계에서 자동 생성하여 QR provisioning page에 동기화.

##### 실패 시 디버깅 포인트

| 증상 | 원인 |
|------|------|
| `set-device-owner` 거부 | 기존 user account 존재, 다른 DO 등록됨, encryption 미완료 |
| QR 스캔 후 "관리자 앱을 설치할 수 없습니다" | SHA-256 checksum 불일치, download URL 도달 불가 |
| Device Owner 등록 후 자동 업데이트 동작 안 함 | DPC가 startForegroundService 미호출, FGS type 누락(Android 14+) |

#### DPC + Launcher 서명 정책 ⚠️ (코덱스 5차 검토 반영)

**DPC와 Launcher는 별도 keystore로 서명한다. 동일 keystore 강제는 보안 blast radius를 키우고
실제 Device Owner 설치 조건과도 무관하다.**

```
android-app/release.keystore  ←  Launcher 서명 (기존 유지, 절대 변경 금지 — 변경 시 OTA 업데이트 불가)
dpc-app/dpc-release.keystore  ←  DPC 전용 별도 keystore (신규)
```

이유:
- DPC가 Launcher를 설치하는 데 **동일 서명은 불필요**(installer는 packageName 기반으로 동작).
- 동일 keystore 공유 시 한쪽 유출 = 양쪽 모두 위험. **두 keystore 분리로 blast radius 축소.**
- 동일 keystore 강제는 향후 `sharedUserId` 같은 deprecated API 사용 여지를 남길 뿐 실익이 없음.
- DPC는 Launcher 설치 전 **signing certificate digest pinning**으로 위변조 방지를 더 강하게 구현.

**대신 다음 안전장치를 도입한다:**

| 안전장치 | 설명 |
|---------|------|
| Launcher 인증서 pinning | DPC 코드에 Launcher의 signing cert SHA-256 fingerprint를 상수로 박음. 설치 전 매번 검증. |
| Bound Service signature permission | DPC가 export한 service에 `android:protectionLevel="signature"` permission 적용 + caller UID/package 검증 |
| IPC session token | report API call에 version + sessionId + timestamp 포함하여 stale/replay 차단 |

> Launcher의 `release.keystore`는 OTA 업데이트 호환을 위해 **절대 변경 금지**.
> DPC keystore는 신규 생성, 별도 CI Secret(`DPC_KEYSTORE_PASSWORD` 등)으로 관리.

---

### 방법 2. Shizuku — ADB 권한 위임

오픈소스 앱 Shizuku가 ADB 권한을 일반 앱에 위임.

**제약:**
- **Android 9(G Pad 5)에서 무선 디버깅 불가** → USB ADB 연결 필요
- **재부팅마다 Shizuku 수동 재시작 필요** → 키오스크 용도에 부적합
- G Pad 5 기준 **사실상 사용 불가**

---

### 방법 3. MDM 솔루션

| 솔루션 | 비용 | 특징 |
|--------|------|------|
| **Samsung Knox Manage** | 유료 (기기당 월정액) | 갤럭시탭 최적화 |
| **Microsoft Intune** | 유료 (M365 포함) | 범용, 모든 Android 지원 |
| **Headwind MDM** | 오픈소스 (자체 서버) | 무료, 기능 제한적 |

제약: 유료(오픈소스 제외), MDM 서버 상시 인터넷 필수, 복잡도 높음.

---

## 방법 비교표

| 방법 | 자동화 수준 | 비용 | G Pad 5 지원 | 구현 난이도 |
|------|------------|------|-------------|------------|
| **Device Owner (DPC)** | ✅ 완전 자동 | 무료 | ✅ | 중 |
| Shizuku | ✅ 완전 자동 | 무료 | ❌ 재부팅 시 재설정 | 중 |
| MDM | ✅ 완전 자동 | 유료 | ✅ | 낮음(서비스 의존) |
| 현재 방식 | ❌ 설치 클릭 필요 | 무료 | ✅ | 없음 |

---

## 웹 콘텐츠 업데이트 — 이미 자동, 단 버전 호환성 주의

`UpdateManager.java`가 구현한 현재 방식:

```
앱 시작 → Cloudflare /version.txt 확인
  → 신버전이면 index.html + sw.js + manifest.json 다운로드
  → WebView 자동 새로고침
```

**사용자 동작 불필요. 완전 자동. ✅**

### ⚠️ Web/Native 버전 호환성 위험

웹이 APK보다 먼저 배포되는 경우:

```
웹 v2 배포 (새 AndroidBridge.newMethod() 호출 포함)
→ 기기 APK는 구버전 → newMethod undefined → JS 오류
```

**Update Manifest에 `minNativeVersion` 필드로 해결:**

```json
{
  "webVersion": "2026.05.09",
  "minNativeVersion": 47,
  "apkVersion": "1.1.7"
}
```

`UpdateManager`가 현재 versionCode < `minNativeVersion`이면 웹 콘텐츠 업데이트를 보류하고
APK 업데이트를 우선 요청한다.

> **현재 프로젝트 관행**: `version.txt` ↔ `build.gradle` 동시 bump 규칙(CLAUDE.md §11)으로
> 이미 웹/APK 동시 배포가 강제됨. 위 설계는 이 규칙이 어겨지는 경우의 안전망.

---

## APK 사전 검증 절차

DPC가 설치를 진행하기 전 반드시 수행하는 2단계 검증:

### 1단계: SHA-256 Checksum 검증

```java
MessageDigest digest = MessageDigest.getInstance("SHA-256");
try (FileInputStream fis = new FileInputStream(apkFile)) {
    byte[] buf = new byte[8192];
    int n;
    while ((n = fis.read(buf)) != -1) digest.update(buf, 0, n);
}
String actual = bytesToHex(digest.digest());

if (!actual.equalsIgnoreCase(manifest.sha256)) {
    apkFile.delete();
    throw new VerificationException(FAIL_CHECKSUM, "APK checksum 불일치");
}
```

> **URL 공개 여부(접근 제어) ≠ APK 무결성(위변조 방지)**
> 저장소가 공개여도 checksum 검증은 반드시 유지. 네트워크 전송 중 손상, CDN 캐시 오염 방어.

### 2단계: 서명 인증서 검증

```java
PackageInfo newPkg = pm.getPackageArchiveInfo(
    apkFile.getAbsolutePath(), PackageManager.GET_SIGNING_CERTIFICATES);
PackageInfo curPkg = pm.getPackageInfo(
    LAUNCHER_PACKAGE, PackageManager.GET_SIGNING_CERTIFICATES);

if (!signaturesMatch(newPkg, curPkg)) {
    throw new VerificationException(FAIL_SIGNATURE,
        "서명 불일치: CI keystore 확인 필요 — 설치 중단");
}
```

---

## 다운로드 구현: HttpURLConnection (DownloadManager 사용 안 함)

### DPC에서 DownloadManager를 쓰지 않는 이유

| 항목 | DownloadManager | HttpURLConnection (권장) |
|------|----------------|------------------------|
| 상태 머신 연동 | 어려움 (시스템 관리) | 직접 제어 가능 |
| checksum 직결 | 불가 | 다운로드 중 실시간 계산 가능 |
| 진행률 콜백 | 제한적 | 직접 구현 |
| retry/backoff | 불가 | 직접 구현 |
| atomic rename | 불가 | 직접 제어 |

> **현재 코드 재사용 범위:**
> - `AppUpdateManager.java` → **버전 비교 로직 + 60분 캐시만 재사용**. 다운로드 로직(DownloadManager) 재사용 금지.
> - `UpdateManager.java` → **HttpURLConnection 기반 다운로드 + `.tmp→rename` 패턴 참조.** DPC 다운로더의 직접 모델.

### 다운로드 원자성 (코덱스 5차 반영 — `renameTo()` 단독 사용 금지)

다운로드 도중 전원 차단, 네트워크 단절이 발생하면 **손상된 .apk 파일이 남는다**.
반드시 임시 파일로 받고 검증 완료 후 **`Files.move(ATOMIC_MOVE, REPLACE_EXISTING)`** 으로 교체한다.

> **코덱스 지적**: `renameTo()`는 Android에서 항상 atomic이 아니며, 실패 시 boolean false만 반환해 원인 추적이 어렵다. 또한 `apkFile.delete()` 후 `renameTo()` 사이에 프로세스 종료가 발생하면 **정상 파일을 잃는다**. → `Files.move(ATOMIC_MOVE)`로 변경, 실패 시 IOException으로 명시적 처리.

```java
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;

File tmpFile = new File(cacheDir, "launcher.apk.tmp");  // staging
File apkFile = new File(cacheDir, "launcher.apk");      // live

// 1. tmp로 다운로드 (live 파일은 손대지 않음)
try (HttpURLConnection conn = openConnection(apkUrl);
     InputStream is = conn.getInputStream();
     FileOutputStream fos = new FileOutputStream(tmpFile)) {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    byte[] buf = new byte[8192];
    int n, total = 0;
    while ((n = is.read(buf)) != -1) {
        fos.write(buf, 0, n);
        digest.update(buf, 0, n);
        reportProgress(total += n);
    }
    // 2. 다운로드 중 checksum 계산 완료
    if (!bytesToHex(digest.digest()).equalsIgnoreCase(manifest.sha256)) {
        tmpFile.delete();
        throw new VerificationException(FAIL_CHECKSUM, "다운로드 중 checksum 불일치");
    }
}

// 3. 서명 검증 (tmp 파일 대상)
verifySignature(tmpFile);

// 4. 원자적 교체 — 반드시 같은 파티션(같은 cacheDir)이어야 ATOMIC 가능
try {
    Files.move(
        tmpFile.toPath(),
        apkFile.toPath(),
        StandardCopyOption.ATOMIC_MOVE,
        StandardCopyOption.REPLACE_EXISTING
    );
} catch (AtomicMoveNotSupportedException e) {
    // 다른 파티션 → atomic 불가. live 파일을 보존하고 실패 처리.
    tmpFile.delete();
    throw new InstallException(FAIL_ATOMIC_MOVE,
        "ATOMIC_MOVE 미지원. tmp/live 동일 파티션 확인 필요");
} catch (IOException e) {
    // I/O 오류 → live 파일은 그대로 유지됨 (Files.move는 부분 쓰기 안 함)
    tmpFile.delete();
    throw new InstallException(FAIL_IO, "원자적 교체 실패: " + e.getMessage());
}
```

**`renameTo()` 대비 이점:**
- 실패 시 IOException 발생 → 원인 식별 가능
- live 파일 삭제(`delete()`) 단계 제거 → 교체 실패 시에도 기존 정상 파일 보존
- ATOMIC_MOVE 미지원 환경(다른 파티션) 명시적 검출

---

## 설치 중복 방지

주기적 폴링과 수동 트리거가 동시에 실행될 경우
`PackageInstaller.Session`이 두 개 열리면서 충돌할 수 있다.

```java
private final AtomicBoolean installInProgress = new AtomicBoolean(false);

public void startUpdate() {
    if (!installInProgress.compareAndSet(false, true)) {
        Log.d(TAG, "설치 진행 중 — 중복 요청 무시");
        return;
    }
    try {
        runUpdateFlow();
    } finally {
        installInProgress.set(false);
    }
}
```

> 재부팅 후 `INSTALLING` 상태 = 설치 실패로 간주 → `FAILED`로 전환 후 재시도 스케줄.

---

## INSTALL_FAILED 코드 매핑 (코덱스 5차 — 단정 제거 + EXTRA_STATUS_MESSAGE 함께 기록)

`PackageInstaller.Session` 설치 완료 콜백에서 받는 오류 코드별 대응표.
**고수준 status 코드만으로는 원인을 단정할 수 없다** — 동일 status 코드가 다양한 원인(downgrade, signature, package conflict, ABI 미지원 등)에서 발생한다.

> **코덱스 지적**: `STATUS_FAILURE_CONFLICT == versionCode 다운그레이드` 같은 단정은 제거.
> intent extras (`EXTRA_STATUS_MESSAGE`, legacy `EXTRA_LEGACY_STATUS`)와 **사전 검증 결과(VERIFYING 단계 출력)**를 함께 기록해 분기.

### 콜백에서 수집해야 하는 데이터

```java
int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1);
String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
int legacyStatus = intent.getIntExtra(PackageInstaller.EXTRA_LEGACY_STATUS, 0);
String otherPackage = intent.getStringExtra(PackageInstaller.EXTRA_OTHER_PACKAGE_NAME);

// VERIFYING 단계에서 미리 저장한 호환성 결과
PreInstallChecks pre = state.getPreInstallChecks();
//   → newVersionCode, currentVersionCode, signatureMatched, minSdkOk, abiOk
```

### status × 부가 정보 기반 분기표

| status | 부가 신호 | 판정 | 대응 | retry |
|--------|---------|------|------|-------|
| `STATUS_FAILURE_CONFLICT` | `pre.newVersionCode < pre.currentVersionCode` | versionCode 다운그레이드 | Manifest 오류 — 관리자 알림 | ❌ 금지 |
| `STATUS_FAILURE_CONFLICT` | `otherPackage != null` | 다른 앱과 packageName 충돌 | Manifest 검토 — 관리자 알림 | ❌ 금지 |
| `STATUS_FAILURE_CONFLICT` | message에 "INSTALL_FAILED_DUPLICATE_PACKAGE" | 동일 패키지 충돌 | Manifest 검토 | ❌ 금지 |
| `STATUS_FAILURE_INCOMPATIBLE` | `pre.signatureMatched == true` | VERIFYING 통과 후 발생 → 시스템 캐시 이슈 | 재부팅 후 1회 재시도 | 1회 |
| `STATUS_FAILURE_INCOMPATIBLE` | `pre.signatureMatched == false` 또는 미실시 | 서명 불일치 가능성 | rollback | 0회 |
| `STATUS_FAILURE_INCOMPATIBLE` | `pre.minSdkOk == false` | minSdk 미달 | Manifest 오류 — 관리자 알림 | ❌ 금지 |
| `STATUS_FAILURE_INCOMPATIBLE` | `pre.abiOk == false` | ABI 미지원 | 관리자 알림 | ❌ 금지 |
| `STATUS_FAILURE_STORAGE` | — | 저장 공간 부족 | cache 정리 후 1회 재시도 | 정리 후 1회 |
| `STATUS_FAILURE_ABORTED` | — | 세션 강제 종료(재부팅, 사용자 취소 등) | 지수 백오프 재시도 | ✅ |
| `STATUS_FAILURE_INVALID` | — | APK 손상 | .tmp 삭제 + 재다운로드 | ✅ 즉시 |
| `STATUS_FAILURE` (기타) | message 추가 분석 | 시스템 내부 오류 | 지수 백오프 재시도 | ✅ |

> **VERIFYING 단계에서 사전 검증해야 할 항목:**
> - `versionCode` 비교 (downgrade 차단)
> - `signingCertificate` 일치 여부 (Launcher signing cert pinning)
> - `minSdkVersion ≤ Build.VERSION.SDK_INT`
> - APK ABI ⊂ `Build.SUPPORTED_ABIS`
>
> 이 4개를 사전 검증하면 `STATUS_FAILURE_*` 발생 시 단정 없이 분기 가능.

```java
void handleInstallResult(Intent intent, PreInstallChecks pre) {
    int status = intent.getIntExtra(EXTRA_STATUS, -1);
    String message = intent.getStringExtra(EXTRA_STATUS_MESSAGE);
    int legacy = intent.getIntExtra(EXTRA_LEGACY_STATUS, 0);

    // 모든 정보를 SharedPreferences에 함께 기록 — 현장 디버깅용
    persistFailureContext(status, message, legacy, pre);

    switch (status) {
        case PackageInstaller.STATUS_SUCCESS:
            setState(SUCCESS); break;

        case PackageInstaller.STATUS_FAILURE_CONFLICT:
            if (pre != null && pre.newVersionCode < pre.currentVersionCode) {
                fatalAdminAlert(FAIL_VERSION_DOWNGRADE, message); break;
            }
            if (intent.hasExtra(EXTRA_OTHER_PACKAGE_NAME)) {
                fatalAdminAlert(FAIL_PACKAGE_CONFLICT, message); break;
            }
            fatalAdminAlert(FAIL_CONFLICT_UNKNOWN, message); break;

        case PackageInstaller.STATUS_FAILURE_INCOMPATIBLE:
            if (pre != null && !pre.signatureMatched) {
                triggerRollback(FAIL_SIGNATURE_MISMATCH); break;
            }
            if (pre != null && (!pre.minSdkOk || !pre.abiOk)) {
                fatalAdminAlert(FAIL_PLATFORM_INCOMPATIBLE, message); break;
            }
            // 사전 검증 통과 → 시스템 캐시 이슈
            scheduleRetryAfterReboot(); break;

        case PackageInstaller.STATUS_FAILURE_STORAGE:
            cleanCache(); scheduleRetry(BACKOFF_IMMEDIATE); break;
        case PackageInstaller.STATUS_FAILURE_INVALID:
            deleteTmp(); scheduleRetry(BACKOFF_IMMEDIATE); break;
        default:
            scheduleRetry(nextBackoff());
    }
}
```

---

## 재시도 전략 — 지수 백오프

FAILED 상태에서 무한 즉시 재시도는 네트워크 오류 시 루프를 만든다.

### 기본 지수 백오프 스케줄

| 시도 횟수 | 대기 시간 |
|---------|---------|
| 1회 실패 | 1분 후 재시도 |
| 2회 실패 | 5분 후 재시도 |
| 3회 실패 | 15분 후 재시도 |
| 4회 이상 | 1시간 후 재시도 (상한 고정) |

```java
private static final long[] BACKOFF_MS = {
    60_000L,        // 1분
    5 * 60_000L,    // 5분
    15 * 60_000L,   // 15분
    60 * 60_000L    // 1시간 (상한)
};

private long nextBackoff() {
    int idx = Math.min(retryCount, BACKOFF_MS.length - 1);
    retryCount++;
    return BACKOFF_MS[idx];
}
```

### 회복 불가 코드 — 재시도 금지

| 코드 | 이유 |
|------|------|
| `STATUS_FAILURE_CONFLICT` (versionCode 다운그레이드) | Manifest 자체 오류. 재시도해도 동일 실패. Manifest 수정 전까지 금지. |
| FAIL_SIGNATURE (서명 불일치, VERIFYING 단계) | CI keystore 문제. 재시도해도 동일 실패. 수동 개입 전까지 금지. |

> 회복 불가 코드 발생 시: `setState(FAILED)` + 관리자 토스트/로그 + 재시도 스케줄 없음.
> Manifest 업데이트 또는 기기 재등록 후 수동 트리거로만 재개.

---

## 롤백 전략

설치 자체는 성공했지만 **새 버전 실행 중 crash**하는 경우가 더 위험하다.

### 롤백 소스: 로컬 보관 X, Manifest stable 버전 재다운로드 O

이전 APK를 로컬에 보관하는 방식의 문제:
- ~8MB 추가 스토리지 + orphan 파일 정리 + 서명 mismatch 가능성

**대신: Manifest의 `lastKnownGoodApkUrl`에서 stable APK를 재다운로드.**

### 롤백 트리거 임계값 (코덱스 5차 — DPC watchdog 추가)

> **코덱스 지적**: 기존 트리거는 모두 Launcher의 `reportCrashEvent()` 호출에 의존했다.
> 그러나 cold-start crash, ANR, OOM은 **Launcher가 report 자체를 못 보내는 케이스**다.
> → DPC가 startup marker + health timeout으로 **Launcher report 없이도** rollback 판단 가능해야 한다.

#### Launcher report 기반 트리거 (기존)

| 조건 | 임계값 |
|------|--------|
| `reportCrashEvent()` 반복 | 5분 내 3회 (UncaughtExceptionHandler 잡힌 crash) |
| `reportIdleState(false)` 후 무응답 | 행사 진행 보고 후 60초 내 다른 보고 없음 |

#### DPC watchdog 기반 트리거 (신규)

설치 직후 DPC가 SharedPreferences에 marker 기록:

```java
// 설치 SUCCESS 직후
prefs.edit()
    .putString("startup_pending_version", newVersion)
    .putLong("startup_pending_ts", System.currentTimeMillis())
    .apply();
```

Launcher가 정상 기동되면 `reportHealthOk(version)`로 marker 해제.
**해제되지 않으면 DPC가 다음 조건으로 cold-start crash 판단:**

| 조건 | 감지 방법 | 임계값 |
|------|----------|--------|
| cold-start crash | startup_pending marker가 `health_timeout` 후에도 살아있음 | 60초 |
| ANR | `ActivityManager.getProcessesInErrorState()` 폴링 → ANR state | 1회 감지 즉시 |
| process death 반복 | `ActivityManager.getRunningAppProcesses()`로 Launcher 부재 확인 | 5분 내 3회 |
| 부팅 후 Launcher 미시작 | DPC `BOOT_COMPLETED` 수신 후 Launcher 프로세스 미발견 | 90초 |

```java
// DPC RollbackMonitor (1분마다 폴링)
void evaluateHealth() {
    long ts = prefs.getLong("startup_pending_ts", 0);
    if (ts > 0 && System.currentTimeMillis() - ts > 60_000L) {
        // Launcher가 reportHealthOk를 못 보냈음 → cold-start crash 가능성
        if (!isLauncherProcessAlive()) {
            triggerRollback(FAIL_COLD_START_CRASH);
        }
    }
    // ANR 감지
    for (ProcessErrorStateInfo info : activityManager.getProcessesInErrorState()) {
        if (LAUNCHER_PACKAGE.equals(info.processName)
            && info.condition == ProcessErrorStateInfo.NOT_RESPONDING) {
            recordEvent(EVENT_ANR);
        }
    }
    if (anrCountInWindow(5 * 60_000L) >= 3) triggerRollback(FAIL_ANR_REPEATED);
}
```

> **rollback loop 방지**: 동일 버전에 대해 rollback이 1회 발생하면, 동일 manifest version 재설치 시도 차단(manifest version이 변경되기 전까지 admin alert 상태로 대기).

### ⚠️ APK 롤백 시 web content도 함께 다운그레이드 — staging + marker 순서 (코덱스 5차 반영)

APK만 롤백하면 디스크의 web content는 신버전이 잔류한다.
구버전 APK가 신버전 web을 로드하면 `AndroidBridge.newMethod() undefined` 오류 발생 → crash loop.

> **코덱스 지적 ①** (web rollback 순서): 기존 흐름은 "APK 설치 → web 교체 → 재시작"이었으나, APK 설치 직후 Launcher가 자동 기동하면서 **여전히 잔류한 신버전 web을 로드해 crash loop를 만든다.**
> → web을 staging에 먼저 받아두고, **APK 교체 + web pointer 전환을 동시에** 처리해야 함.
>
> **코덱스 지적 ②** (per-file hash + version-pinned URL): 기존 `lastKnownGoodWebBaseUrl`은 Cloudflare root만 가리키므로, root가 이미 신버전으로 갱신된 상태면 "stable web"을 받을 수 없음.
> → manifest에 **파일별 sha256 + version-pinned URL** 필요.

#### ROLLBACK_PENDING 흐름 (수정)

```
0. rollback_in_progress = true marker 기록 (Launcher 시작 시 web 로드 보류)
   → Launcher가 marker 감지하면 maintenance screen만 표시, web 로드 안 함

1. staging 디렉터리에 lastKnownGoodWebFiles[] 모두 다운로드
   - 파일별 sha256 검증 (개별 실패 시 staging 폐기 후 즉시 재시도)
   - 모든 파일 검증 통과까지 live web은 손대지 않음

2. lastKnownGoodApkUrl → APK 재다운로드 (.tmp) + sha256/서명 검증

3. APK 설치 (PackageInstaller.Session) 시작
   - 설치 중 Launcher 자동 종료됨

4. APK 설치 SUCCESS 콜백:
   ① web staging 디렉터리 → live (Files.move ATOMIC_MOVE 또는 symlink/pointer 전환)
   ② version.txt를 lastKnownGoodVersion으로 덮어쓰기
   ③ rollback_in_progress = false marker 해제

5. DPC가 Launcher 재시작 명령
   - Launcher는 marker가 false임을 확인 후 정상 web 로드
```

#### Manifest에 rollback 소스 필드 추가 — per-file hash + version-pinned URL

```json
{
  "lastKnownGoodVersion": "1.1.7",
  "lastKnownGoodApk": {
    "url":    "https://github.com/.../v1.1.7/SympotalkLauncher.apk",
    "sha256": "...",
    "versionCode": 47
  },
  "lastKnownGoodWebFiles": [
    {
      "path":   "index.html",
      "url":    "https://sympotalklauncher-apk.pages.dev/v/1.1.7/index.html",
      "sha256": "..."
    },
    {
      "path":   "sw.js",
      "url":    "https://sympotalklauncher-apk.pages.dev/v/1.1.7/sw.js",
      "sha256": "..."
    },
    {
      "path":   "manifest.json",
      "url":    "https://sympotalklauncher-apk.pages.dev/v/1.1.7/manifest.json",
      "sha256": "..."
    },
    {
      "path":   "version.txt",
      "url":    "https://sympotalklauncher-apk.pages.dev/v/1.1.7/version.txt",
      "sha256": "..."
    }
  ]
}
```

> **version-pinned URL 요건**: Cloudflare Pages 또는 R2에 `/v/<version>/...` 경로로 **버전별 immutable 스냅샷**을 배포 시점에 고정 업로드. CI에서 자동 처리.

#### Launcher 측 rollback marker 처리

```java
// MainActivity.onCreate
if (UpdateMarker.isRollbackInProgress(this)) {
    setContentView(R.layout.maintenance_screen);
    showRollbackProgress();
    // web 로드 보류 — DPC가 marker 해제 후 재시작할 때까지 대기
    return;
}
loadApp();
```

### 미래 고려사항: WebView 로컬 데이터 schema 버전

현재는 IndexedDB/localStorage 스키마 변경이 거의 없지만,
오프라인 캐시가 강화되면 rollback 후 스키마 불일치 문제가 발생할 수 있다.

```json
// 미래 Manifest 확장 예시
{ "webDataSchema": 3 }
```

앱 시작 시 로컬 schema 버전과 비교, 불일치 시 IndexedDB clear 후 재초기화.
**현재는 불필요. 향후 오프라인 캐시 스키마 변경 시 검토.**

---

## 상태 머신 (코덱스 5차 — rollback_in_progress marker 반영)

```
IDLE             → 업데이트 없음 / 대기
CHECKING         → Manifest 서버 조회 중
DOWNLOADING      → APK 다운로드 중 (.tmp staging, 진행률 포함)
VERIFYING        → 사전 검증 (sha256 + signature pinning + versionCode + minSdk + ABI)
INSTALLING       → PackageInstaller.Session 실행 중 (mutex 획득)
HEALTH_WAIT      → 설치 SUCCESS 후 startup_pending marker 대기 (60초)
SUCCESS          → Launcher reportHealthOk + watchdog 통과 → last_known_good 갱신
FAILED           → 오류 (status + EXTRA_STATUS_MESSAGE + legacy + preChecks 함께 저장)
ROLLBACK_PENDING → crash 임계값 초과 — web staging 다운로드 → APK 설치 → pointer 전환
ROLLBACK         → stable APK + web content 모두 복원, rollback_in_progress 해제
```

> ~~WAITING_REBOOT~~: `PackageInstaller.Session` 설치는 재부팅 불필요. 제거.

**SharedPreferences 영속화 키:**

| 키 | 의미 |
|----|------|
| `state` | 상태 머신 현재 값 |
| `retry_count`, `next_retry_ts` | 백오프 재시도 |
| `last_failure_status` / `_message` / `_legacy` / `_preChecks` | INSTALL_FAILED 컨텍스트 |
| `startup_pending_version` / `_ts` | HealthWatchdog용 marker |
| `rollback_in_progress` | Launcher가 web 로드 보류해야 할지 여부 |
| `last_known_good_version` | 정상 기동 확인된 마지막 버전 |
| `current_session` | DPC가 발급한 IPC session id |

**재부팅 후 복구 로직:**

```java
switch (savedState) {
    case "DOWNLOADING":
    case "VERIFYING":
        cleanTmpFiles(); setState("IDLE"); break;  // 재시도
    case "INSTALLING":
    case "HEALTH_WAIT":
        // 설치 직후 또는 health check 중 재부팅 → 실패로 간주
        setState("FAILED");
        setFailReason(FAIL_INTERRUPTED);
        scheduleRetry(nextBackoff()); break;
    case "ROLLBACK_PENDING":
        // rollback_in_progress marker는 그대로 유지된 상태
        startRollback(); break;  // 롤백 재개
}
```

> 부팅 직후 DPC는 `BOOT_COMPLETED`에서 startup_pending marker를 즉시 검사 — Launcher가 90초 내 미시작이면 cold-start crash로 간주 후 rollback 트리거.

---

## IPC 전략: DPC ↔ Launcher 통신

### 단방향 원칙 — DPC가 상태 owner

```
┌─────────────────────────────────────────────────┐
│  DPC UpdateService (Bound Service)              │
│  - 상태 머신 owner                               │
│  - 모든 상태 변경은 여기서만                       │
└────────────────┬────────────────────────────────┘
                 │ read-only (query)
                 ↓
┌─────────────────────────────────────────────────┐
│  Launcher App                                   │
│  - 상태 조회만 가능 (쓰기 불가)                    │
│  - 사실만 보고 (report), 명령(command) 불가        │
└─────────────────────────────────────────────────┘
```

**Launcher → DPC: 보고 전용 (명령 불가)**

| 메서드 | 설명 | 보강 검증 |
|--------|------|---------|
| `reportCrashEvent(session, version, count, reason)` | crash 사실 보고 | session/version 검증 → stale 보고 무시 |
| `reportHealthOk(session, version, ts)` | 정상 기동 확인 보고 | session 검증 + 60초 이내 ts만 수용 |
| `reportIdleState(session, version, idle)` | 행사 진행 여부 보고 | session 검증 |

**DPC → Launcher: 읽기 전용 제공**

| 메서드 | 설명 |
|--------|------|
| `getUpdateState()` | 현재 상태 머신 값 |
| `getDownloadProgress()` | 다운로드 진행률 (0~100) |
| `getPendingVersion()` | 설치 예정 버전 |
| `getFailReason()` | 실패 원인 코드 (관리자 화면용) |
| `getCurrentSession()` | 현재 active session id (Launcher가 report 호출 시 함께 전송) |

#### 신뢰 경계 보강 (코덱스 5차 반영)

> **코덱스 지적**: "단방향 + read-only" 원칙만으로는 위변조/replay/stale report를 막지 못한다.
> → caller 검증 + permission + session token 3중 보강.

##### A. Bound Service explicit binding + caller 검증

```xml
<!-- DPC AndroidManifest.xml -->
<permission
    android:name="com.sympotalk.dpc.permission.REPORT"
    android:protectionLevel="signature" />

<service
    android:name=".UpdateService"
    android:exported="true"
    android:permission="com.sympotalk.dpc.permission.REPORT" />
```

> `signature` protectionLevel은 동일 서명자만 권한 부여. **DPC와 Launcher가 별도 keystore이면 사용 불가** → DPC 코드에서 `Binder.getCallingUid()` + `PackageManager.getNameForUid()` + signing certificate digest pinning으로 직접 검증.

```java
// DPC UpdateService.onBind / 각 report 메서드 진입부
private void verifyCaller() {
    int uid = Binder.getCallingUid();
    String[] pkgs = pm.getPackagesForUid(uid);
    if (pkgs == null) throw new SecurityException("Unknown caller");

    boolean ok = false;
    for (String pkg : pkgs) {
        if (LAUNCHER_PACKAGE.equals(pkg)
            && verifySigningCertificate(pkg, LAUNCHER_CERT_SHA256)) {
            ok = true; break;
        }
    }
    if (!ok) throw new SecurityException("Unauthorized caller: " + Arrays.toString(pkgs));
}
```

##### B. Session token + version + timestamp

DPC가 Launcher 시작 시 발급한 `sessionId`를 모든 report 호출에 포함.
**stale report (이전 버전, 이전 session)는 거부**.

```java
@Override
public void reportHealthOk(String session, String version, long ts) {
    verifyCaller();
    if (!session.equals(currentSession)) return;          // stale session 거부
    if (!version.equals(installedVersion)) return;        // 잘못된 버전 거부
    if (Math.abs(System.currentTimeMillis() - ts) > 60_000L) return;  // replay 거부
    clearStartupPendingMarker(version);
}
```

##### C. report 거부 카운터

위 검증에서 거부된 호출이 짧은 시간 내 다수 발생하면 **알림** + DPC 진입점 일시 차단(60초).
악성 또는 오작동 Launcher 대비.

---

## 업데이트 타이밍 정책

**행사 진행 중 자동 업데이트는 위험하다.** 아래 조건을 모두 만족할 때만 설치 진행.

| 조건 | 기준 | 비고 |
|------|------|------|
| Maintenance Window | 새벽 02:00~05:00 | 또는 관리자 수동 허용 |
| 배터리 잔량 | **40% 이상** | 설치 중 방전 방지 |
| 충전 연결 | **충전 연결 상태 권장** | 강제 또는 권장 중 선택 |
| 화면 상태 | **화면 꺼짐 (SCREEN_OFF)** | 사용 중 방해 방지 |
| 앱 상태 | idle 모드 (행사 미진행) | Launcher가 `reportIdleState(true)` 보고 |

```java
private boolean canInstallNow() {
    return isMaintenanceWindow()
        && getBatteryLevel() >= 40
        && !isScreenOn()
        && launcherReportedIdle;
}
```

> **관리자 수동 허용**: 조건 미충족 시에도 설정 화면에서 "지금 업데이트" 토글 ON → 즉시 진행.

---

## Update Manifest 서버 구조 (코덱스 5차 — DPC 자동 업데이트의 P0 필수 구성)

> **코덱스 지적 ①** (우선순위 충돌): 기존 문서는 "수십 대는 GitHub Rate Limit 문제 없음"이라고 P2로 표기하면서도 P0 개발 항목에는 manifest 서버를 포함시켰다. 모순.
>
> → **DPC 자동 업데이트에서 manifest 서버는 P0 필수.**
> → GitHub Releases 직접 조회 + 60분 캐시는 **기존 수동 업데이트(`AppUpdateManager.java`)의 보완책**으로만 유지.
>
> **코덱스 지적 ②** (Worker가 release JSON만으로 sha256/versionCode 못 만듦): GitHub Release JSON에는 attached APK의 sha256이나 Gradle versionCode가 직접 노출되지 않음. `buildManifest(release)`가 그걸 만든다는 가정은 틀림.
>
> → **CI 단계에서 sha256/versionCode/rollback URL을 추출해 manifest artifact로 직접 publish**.
> → Worker는 이미 빌드된 manifest를 캐시·서빙하는 단순 역할.

### 책임 분리

| 컴포넌트 | 책임 |
|---------|------|
| **CI (GitHub Actions)** | APK 빌드 → `sha256sum` 추출 → Gradle `versionCode` 추출 → manifest artifact JSON 생성 → Cloudflare KV에 PUT |
| **Cloudflare Worker** | KV에 저장된 manifest JSON을 GET 요청에 응답 (캐시·서빙만) |
| **DPC** | Worker manifest 조회 → 검증 → 다운로드 |
| **AppUpdateManager (Launcher)** | 기존 수동 업데이트 동선만 담당. DPC와 무관. |

### Manifest JSON 전체 구조 (per-file hash 포함)

```json
{
  "version":         "1.1.8",
  "versionCode":     48,
  "minNativeVersion": 47,
  "channel":         "stable",
  "apk": {
    "url":    "https://github.com/.../v1.1.8/SympotalkLauncher.apk",
    "sha256": "a3f8c2d1e4b5f6a7...",
    "size":   8421376
  },
  "webFiles": [
    { "path": "index.html",   "url": "https://.../v/1.1.8/index.html",   "sha256": "..." },
    { "path": "sw.js",        "url": "https://.../v/1.1.8/sw.js",        "sha256": "..." },
    { "path": "manifest.json","url": "https://.../v/1.1.8/manifest.json","sha256": "..." },
    { "path": "version.txt",  "url": "https://.../v/1.1.8/version.txt",  "sha256": "..." }
  ],
  "lastKnownGoodVersion": "1.1.7",
  "lastKnownGoodApk": {
    "url": "https://github.com/.../v1.1.7/SympotalkLauncher.apk",
    "sha256": "b4c9d2e3f5a6...",
    "versionCode": 47
  },
  "lastKnownGoodWebFiles": [
    { "path": "index.html", "url": "https://.../v/1.1.7/index.html", "sha256": "..." },
    { "path": "sw.js",      "url": "https://.../v/1.1.7/sw.js",      "sha256": "..." }
  ],
  "maintenanceOnly": false,
  "publishedAt":     "2026-05-09T02:00:00Z",
  "manifestVersion": 2
}
```

### CI 단계 (GitHub Actions에 추가)

```yaml
- name: Compute APK sha256 and versionCode
  run: |
    APK_PATH=android-app/app/build/outputs/apk/release/app-release.apk
    SHA256=$(sha256sum $APK_PATH | awk '{print $1}')
    VERSION_CODE=$(grep "versionCode" android-app/app/build.gradle | head -1 | grep -oP '\d+')
    VERSION_NAME=$(cat version.txt)
    echo "SHA256=$SHA256" >> $GITHUB_ENV
    echo "VERSION_CODE=$VERSION_CODE" >> $GITHUB_ENV
    echo "VERSION_NAME=$VERSION_NAME" >> $GITHUB_ENV

- name: Build manifest.json
  run: |
    node scripts/build-manifest.js \
      --version "$VERSION_NAME" \
      --versionCode "$VERSION_CODE" \
      --sha256 "$SHA256" \
      --webDir public/ \
      > manifest.json

- name: Publish manifest to Cloudflare KV
  run: |
    curl -X PUT \
      -H "Authorization: Bearer ${{ secrets.CF_API_TOKEN }}" \
      -H "Content-Type: application/json" \
      --data-binary @manifest.json \
      "https://api.cloudflare.com/client/v4/accounts/$CF_ACCOUNT/storage/kv/namespaces/$CF_KV_NAMESPACE/values/latest_manifest"

- name: Publish version-pinned web snapshot
  run: |
    # 신·구 버전이 모두 살아있도록 /v/<version>/ 경로 유지
    wrangler pages deploy public/ --branch "v/$VERSION_NAME"
```

### Cloudflare Worker (단순 캐시·서빙)

```javascript
// functions/api/update-manifest.js
export async function onRequest(context) {
  const manifest = await context.env.KV.get("latest_manifest", "json");
  if (!manifest) {
    return new Response("manifest not published", { status: 503 });
  }
  return Response.json(manifest, {
    headers: { "Cache-Control": "public, max-age=300" }
  });
}
```

> **manifest stale fallback**: DPC는 manifest 조회 실패 시 마지막 성공 manifest를 SharedPreferences에 보관해 재사용.
> 24시간 이상 stale이면 admin alert.

---

## 최종 권장 아키텍처 (코덱스 5차 반영)

```
[CI Pipeline — GitHub Actions]
  ├─ APK 빌드 → sha256sum + versionCode 추출
  ├─ webFiles[] 파일별 sha256 계산
  ├─ manifest.json artifact 생성 (per-file hash + version-pinned URL)
  ├─ Cloudflare KV PUT (latest_manifest)
  └─ /v/<version>/ 경로에 web snapshot immutable 배포

[Cloudflare Worker — update-manifest.js]
  └─ KV → JSON 단순 서빙 (캐시 5분)

[Cloudflare Pages/R2 — version-pinned snapshots]
  ├─ /v/1.1.8/index.html, sw.js, manifest.json, version.txt
  └─ /v/1.1.7/...   (rollback 소스)

[DPC App — com.sympotalk.dpc  ← 별도 keystore 사용]
  ├─ UpdateScheduler           (Maintenance Window + 배터리/화면/idle 조건)
  ├─ Downloader                (HttpURLConnection + Files.move ATOMIC_MOVE + 실시간 checksum)
  ├─ Verifier                  (사전 검증: signature pinning + versionCode + minSdk + ABI)
  ├─ PackageInstaller          (Session 기반 무음 설치, install mutex)
  ├─ InstallErrorHandler       (INSTALL_FAILED status × 부가신호 분기 + 지수 백오프)
  ├─ StateMachine              (SharedPreferences 영속화, 재부팅 복구)
  ├─ RollbackMonitor           (Launcher report + DPC watchdog 결합 평가)
  ├─ HealthWatchdog            (startup_pending marker, ANR/process 폴링, 60초 timeout)
  ├─ WebContentRollback        (staging → ATOMIC_MOVE pointer 전환 + rollback_in_progress marker)
  ├─ SignatureVerifier         (Launcher signing cert SHA-256 pinning)
  ├─ ManifestStore             (manifest 캐시 + stale fallback)
  └─ UpdateService             (Bound Service — caller UID/cert 검증 + session token)

[SympotalkLauncher App  ← 기존 release.keystore 유지 (변경 금지)]
  ├─ HealthReporter            (reportHealthOk/CrashEvent/IdleState — session+version+ts 포함)
  ├─ NativeWebProtocolSync     (minNativeVersion 체크, 웹 업데이트 보류)
  ├─ RollbackMarkerWatcher     (rollback_in_progress true이면 web 로드 보류, maintenance UI 표시)
  └─ MaintenanceScreen         (DPC 상태 조회 → 업데이트 중 화면 표시, failReason 표시)
```

### 최종 추가 개발 항목 (코덱스 5차 — 우선순위 재정리)

| 항목 | 규모 | 우선순위 | 비고 |
|------|------|---------|------|
| **CI: sha256 + versionCode + manifest artifact 생성 + KV publish** | 소 (~80줄 YAML+JS) | **P0** | Worker가 release JSON만으로 manifest 못 만듦 — CI가 책임 |
| **Cloudflare Worker (KV 단순 서빙)** | 소 (~30줄 JS) | **P0** | DPC 자동 업데이트의 데이터 출처 |
| **Version-pinned web snapshot (`/v/<version>/`)** | 소 (Pages 설정) | **P0** | rollback 소스 immutable 보존 |
| DPC 앱 신규 개발 (Downloader/Verifier/Installer/StateMachine) | 중 (~800줄 Java) | P0 | |
| **DPC HealthWatchdog (startup marker + ANR 폴링)** | 소 (~150줄) | **P0** | Launcher report 의존성 제거 |
| **DPC SignatureVerifier (Launcher cert pinning)** | 소 (~60줄) | **P0** | 별도 keystore 운영의 핵심 안전장치 |
| **WebContentRollback (staging + ATOMIC_MOVE + rollback marker)** | 소 (~150줄) | **P0** | crash loop 방지 |
| Launcher HealthReporter + RollbackMarkerWatcher | 소 (~150줄) | P0 | session/version/ts 검증 포함 |
| INSTALL_FAILED status × 부가신호 분기 + 지수 백오프 | 소 (~120줄) | P0 | EXTRA_STATUS_MESSAGE 분석 포함 |
| `Files.move(ATOMIC_MOVE)` 다운로드 마이그레이션 | 소 (~30줄) | P0 | `renameTo()` 단독 사용 금지 |
| Device Owner provisioning 문서 + QR payload 생성기 | 소 | P1 | |
| IPC signature permission + caller UID 검증 + session token | 소 (~80줄) | P1 | |
| Android 13+ POST_NOTIFICATIONS 권한 처리 | 소 (~20줄) | P1 | 알림만 영향, 핵심 흐름 무관 |
| Maintenance Window UI (관리자 토글, failReason 표시) | 소 | P1 | |
| protocol versioning (minNativeVersion 검증) | 소 (~30줄) | P1 | |
| webDataSchema 기반 IndexedDB 마이그레이션 | 중 | P2 | 오프라인 캐시 강화 시 |
| stable/beta 채널 분리 + staged rollout (rollout %) | 중 | P2 | fleet 100대 이상 시 |
| Manifest stale fallback + 24h alert | 소 | P2 | |

---

## 델타(패치) 업데이트 — 이 프로젝트에서 불필요

현재 APK ~8MB, 행사장 WiFi 환경 → 전체 다운로드도 수초 완료.
**델타 패치 구현 대비 효익이 매우 낮다. 권장하지 않음.**

---

## 참고: 현재 코드베이스 관련 파일 (코덱스 5차 반영)

| 파일 | 역할 | DPC 재사용 범위 |
|------|------|--------------|
| `android-app/.../AppUpdateManager.java` | GitHub API 조회 + 60분 캐시 + DownloadManager (수동 업데이트용) | **버전 비교 로직만** 참고. 다운로드 로직(DownloadManager)은 DPC에서 재사용 금지. **DPC와 무관하게 기존 수동 업데이트 동선 유지.** |
| `android-app/.../UpdateManager.java` | 웹 콘텐츠 업데이트 (HttpURLConnection + `.tmp→rename`) | **네트워크 골격(HttpURLConnection, 진행률, .tmp 사용)만 참고.** `renameTo()`/per-file hash 부재/version-pinned URL 부재는 DPC에서 재구현 — `Files.move(ATOMIC_MOVE)` + per-file sha256 + version-pinned URL 필수. |
| `android-app/.../MainActivity.java` | JS 브릿지 + 업데이트 트리거 | HealthReporter / RollbackMarkerWatcher 추가 대상 |
| `android-app/release.keystore` | Launcher 서명 keystore | **변경 금지 (OTA 호환성 보장)**. DPC는 이 keystore의 SHA-256 fingerprint를 pinning 데이터로 추출해서만 사용. |
| `dpc-app/dpc-release.keystore` | DPC 전용 keystore (신규) | DPC 빌드 전용. Launcher와 분리 — blast radius 축소. |
| `.github/workflows/build-and-deploy.yml` | APK 빌드 + GitHub Release | **sha256/versionCode 추출 + manifest artifact 생성 + KV PUT 단계 추가 필수**. version-pinned web snapshot 배포 단계 추가. |
| `functions/api/` (Cloudflare) | events, rooms, health | `update-manifest.js` 추가 (KV 단순 서빙) |
| `scripts/build-manifest.js` | 신규 — CI에서 manifest JSON 생성 | per-file sha256 + version-pinned URL + lastKnownGood 자동 채움 |
