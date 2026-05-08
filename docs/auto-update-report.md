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

#### Android 버전별 필요 조건 ⚠️

| Android 버전 | 필요 조건 |
|-------------|----------|
| Android 9 (G Pad 5, minSdk 28) | Device Owner + `PackageInstaller.Session` 기반 설치 |
| Android 10~11 (갤럭시탭 A) | 동일 + 백그라운드 `startActivity()` 제한 → Foreground Service 필수 |
| Android 12+ | `PendingIntent.FLAG_MUTABLE` 명시 필수 |
| Android 13+ | `POST_NOTIFICATIONS` 권한 선언 필요 (설치 알림용) |
| Android 14+ | 백그라운드 설치 제한 강화 → Foreground Service 필수 |

> **현재 기기(G Pad 5 = Android 9, 갤럭시탭 A = Android 10)는 Android 12+ 조건 없이
> 동작하지만, 향후 기기 추가 시 버전 분기 코드 필요.**

#### 필요한 것

| 항목 | 설명 |
|------|------|
| **DPC 앱 (별도 개발)** | Device Owner로 등록되는 관리 앱. `PackageInstaller.Session` 기반 무음 설치 |
| **기기 초기 등록 (1회)** | ADB 명령 1회 실행 또는 공장 초기화 후 QR 코드 스캔 |
| **Update Manifest 서버** | Cloudflare Worker로 버전·sha256·APK URL 제공 |
| **DPC ↔ 런처 통신** | Bound Service 기반 단방향 상태 공유 (DPC → Launcher 읽기 전용) |
| **공통 signing keystore** | **DPC + Launcher 모두 동일 `release.keystore` 사용** (하단 §서명 정책 참조) |

#### 전체 동작 흐름

```
DPC UpdateService (Foreground Service)
  ① 상태: IDLE
  ② Maintenance Window + 배터리/충전/화면 조건 확인
  ③ 상태: CHECKING → Update Manifest 조회
  ④ 새 버전 없음: IDLE로 복귀
  ⑤ 새 버전 있음: 상태: DOWNLOADING
     → HttpURLConnection으로 .tmp 파일 다운로드 (원자적 rename, 하단 §참조)
  ⑥ 상태: VERIFYING
     → SHA-256 checksum 검증
     → 서명 인증서 검증 (PackageManager.getPackageArchiveInfo)
     → 실패: FAILED + 오류 코드 저장 + 지수 백오프 재시도 스케줄
  ⑦ 상태: INSTALLING (install mutex 획득 후)
     → PackageInstaller.Session 실행
     → 실패 코드별 대응 (하단 §INSTALL_FAILED 참조)
  ⑧ 설치 성공: 상태 SUCCESS
     → Foreground Service에서 Launcher 재시작
     → health check 대기 (30초)
  ⑨ Launcher → reportHealthOk(): last_known_good_version 갱신
  ⑩ Launcher → reportCrashEvent(): 임계값 초과 시
     → 상태: ROLLBACK_PENDING
     → stable APK + stable web content 재다운로드
     → ROLLBACK 완료
```

#### 기기 등록 방법 (택1)

```bash
# 옵션 A: ADB 명령 1회 실행 (기존 기기, 구글 계정 미연동 상태 필수)
adb shell dpm set-device-owner com.sympotalk.dpc/.AdminReceiver

# 옵션 B: 공장 초기화 후 QR 코드 스캔 (신규 기기 셋업 시)
```

> ⚠️ `set-device-owner`는 기기에 **구글 계정이 등록되어 있으면 실패**한다.
> 이 프로젝트의 기기는 구글 계정 미연동 상태이므로 조건 충족.

#### DPC + Launcher 서명 정책 ⚠️

**DPC와 Launcher는 반드시 동일한 `release.keystore`로 서명해야 한다.**

```
android-app/release.keystore  ←  Launcher 서명 (현재)
dpc-app/release.keystore       ←  동일 keystore 사용 (DPC 신규)
```

이유:
- 향후 `sharedUserId` 또는 `signature` 기반 권한 공유 가능성 확보
- Bound Service IPC에서 상호 패키지 신뢰 확인 가능
- CI 파이프라인 단일화 (keystore 관리 포인트 1개)

> 현재 `release.keystore`는 의도적으로 저장소에 포함되어 있으며 CI Secrets로 관리됨.
> DPC 앱 빌드 시 동일 keystore + 동일 CI Secrets 사용.

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

### 다운로드 원자성

다운로드 도중 전원 차단, 네트워크 단절이 발생하면 **손상된 .apk 파일이 남는다**.
반드시 임시 파일로 받고 검증 완료 후 원자적으로 교체한다.

```java
File tmpFile = new File(cacheDir, "launcher.apk.tmp");
File apkFile = new File(cacheDir, "launcher.apk");

// 1. tmp로 다운로드
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
    // 2. 다운로드 중 checksum 계산 완료 → 별도 검증 단계 불필요
    if (!bytesToHex(digest.digest()).equalsIgnoreCase(manifest.sha256)) {
        tmpFile.delete();
        throw new VerificationException(FAIL_CHECKSUM, "다운로드 중 checksum 불일치");
    }
}
// 3. 서명 검증
verifySignature(tmpFile);
// 4. 원자적 교체 (같은 파티션이면 atomic)
if (apkFile.exists()) apkFile.delete();
tmpFile.renameTo(apkFile);
```

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

## INSTALL_FAILED 코드 매핑

`PackageInstaller.Session` 설치 완료 콜백에서 받는 오류 코드별 대응표.
이 매핑이 없으면 FAILED 상태에서 원인을 구분할 수 없어 현장 디버깅이 어렵다.

| 오류 코드 | 의미 | 대응 | retry 여부 |
|----------|------|------|-----------|
| `STATUS_FAILURE_INCOMPATIBLE` | 서명 불일치 또는 시스템 캐시 불일치 | **VERIFYING 통과 후 발생이면**: 재부팅 후 재시도. **VERIFYING 미실시/실패 후 발생이면**: rollback | 재부팅 1회 후 재시도 |
| `STATUS_FAILURE_CONFLICT` | versionCode 다운그레이드 시도 | Manifest 오류 — **retry 금지**, 관리자 알림 | ❌ 금지 |
| `STATUS_FAILURE_STORAGE` | 저장 공간 부족 | 캐시 정리 후 재시도 | 정리 후 1회 |
| `STATUS_FAILURE_ABORTED` | 설치 세션 강제 종료 (재부팅 등) | 재시도 | ✅ 지수 백오프 |
| `STATUS_FAILURE_INVALID` | APK 파일 손상 | .tmp 삭제 + 재다운로드 | ✅ 즉시 |
| `STATUS_FAILURE` (기타) | 시스템 내부 오류 | 지수 백오프 재시도 | ✅ 지수 백오프 |

```java
void handleInstallResult(int status, String message) {
    switch (status) {
        case PackageInstaller.STATUS_SUCCESS:
            setState(SUCCESS); break;
        case PackageInstaller.STATUS_FAILURE_CONFLICT:
            // versionCode 다운그레이드 — 재시도 불가
            setState(FAILED);
            setFailReason(FAIL_VERSION_DOWNGRADE);
            notifyAdmin("Manifest versionCode 오류: " + message);
            break;  // backoff 스케줄 하지 않음
        case PackageInstaller.STATUS_FAILURE_INCOMPATIBLE:
            // VERIFYING 통과 후 발생 → 시스템 이슈, 재부팅 후 재시도
            setState(FAILED);
            setFailReason(FAIL_INCOMPATIBLE);
            scheduleRetryAfterReboot();
            break;
        case PackageInstaller.STATUS_FAILURE_STORAGE:
            cleanCache();
            scheduleRetry(BACKOFF_IMMEDIATE);
            break;
        default:
            setState(FAILED);
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

### 롤백 트리거 임계값

모호한 "crash 감지"로는 **rollback loop**가 생긴다.
아래 조건 중 하나 이상 충족 시 롤백 트리거:

| 조건 | 임계값 |
|------|--------|
| cold start crash 반복 | 30초 내 3회 (WebView init 실패 포함) |
| Launcher init timeout | 15초 초과 (JS bridge 응답 없음) |
| health ping 무응답 | 기동 후 30초 내 무응답 |
| process death 반복 | 5분 내 3회 (OOM, ANR 포함) |

### ⚠️ APK 롤백 시 web content도 반드시 함께 다운그레이드

APK만 롤백하면 디스크의 web content는 신버전이 잔류한다.
구버전 APK가 신버전 web을 로드하면 `AndroidBridge.newMethod() undefined` 오류 발생.

```
ROLLBACK_PENDING 진입 시:
  1. lastKnownGoodApkUrl → APK 재다운로드 + 설치
  2. lastKnownGoodWebUrl → index.html + sw.js + manifest.json 재다운로드
  3. version.txt 갱신 → lastKnownGoodVersion으로 덮어쓰기
  4. Launcher 재시작
```

Manifest에 rollback 소스 필드 추가:

```json
{
  "lastKnownGoodVersion":    "1.1.7",
  "lastKnownGoodApkUrl":     "https://github.com/.../v1.1.7/SympotalkLauncher.apk",
  "lastKnownGoodWebBaseUrl": "https://sympotalklauncher-apk.pages.dev",
  "lastKnownGoodSha256":     "..."
}
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

## 상태 머신

```
IDLE             → 업데이트 없음 / 대기
CHECKING         → Manifest 서버 조회 중
DOWNLOADING      → APK 다운로드 중 (.tmp 파일, 진행률 포함)
VERIFYING        → SHA-256 + 서명 검증 중 (다운로드 중 실시간 계산)
INSTALLING       → PackageInstaller.Session 실행 중 (mutex 획득)
SUCCESS          → 설치 완료, health check 대기 (30초)
FAILED           → 오류 발생 (원인 코드 + retryCount + nextRetryTimestamp 저장)
ROLLBACK_PENDING → crash 임계값 초과, stable APK + web content 재다운로드 중
ROLLBACK         → stable 버전 설치 완료, web content 복원 완료
```

> ~~WAITING_REBOOT~~: `PackageInstaller.Session` 설치는 재부팅 불필요. 제거.

**재부팅 후 복구 로직:**

```java
switch (savedState) {
    case "DOWNLOADING":
    case "VERIFYING":
        cleanTmpFiles(); setState("IDLE"); break;  // 재시도
    case "INSTALLING":
        setState("FAILED");
        setFailReason(FAIL_INTERRUPTED);
        scheduleRetry(nextBackoff()); break;  // 설치 중 재부팅 = 실패
    case "ROLLBACK_PENDING":
        startRollback(); break;  // 롤백 재개
}
```

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

| 메서드 | 설명 |
|--------|------|
| `reportCrashEvent(count, reason)` | crash 사실 보고. 롤백 여부는 DPC 판단 |
| `reportHealthOk()` | 정상 기동 확인 보고 |
| `reportIdleState(bool)` | 행사 진행 여부 보고 (Maintenance Window 연동) |

**DPC → Launcher: 읽기 전용 제공**

| 메서드 | 설명 |
|--------|------|
| `getUpdateState()` | 현재 상태 머신 값 |
| `getDownloadProgress()` | 다운로드 진행률 (0~100) |
| `getPendingVersion()` | 설치 예정 버전 |
| `getFailReason()` | 실패 원인 코드 (관리자 화면용) |

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

## Update Manifest 서버 구조

> **현재 코드(`AppUpdateManager.java`)에 60분 캐싱이 이미 구현되어 있어**
> 수십 대 규모에서는 GitHub Rate Limit이 실질적 문제가 되지 않는다.
> **기기 수가 수백 대 이상으로 증가할 경우, Cloudflare Worker Manifest 서버 도입을 검토할 것.**

### Manifest JSON 전체 구조

```json
{
  "version":               "1.1.8",
  "versionCode":           48,
  "minNativeVersion":      47,
  "sha256":                "a3f8c2d1e4b5f6a7...",
  "apkUrl":                "https://github.com/.../v1.1.8/SympotalkLauncher.apk",
  "webVersion":            "1.1.8",
  "lastKnownGoodVersion":  "1.1.7",
  "lastKnownGoodApkUrl":   "https://github.com/.../v1.1.7/SympotalkLauncher.apk",
  "lastKnownGoodWebBaseUrl": "https://sympotalklauncher-apk.pages.dev",
  "lastKnownGoodSha256":   "b4c9d2e3f5a6...",
  "maintenanceOnly":       false
}
```

### Cloudflare Worker (최소 구현)

```javascript
// functions/api/update-manifest.js
export async function onRequest(context) {
  const cached = await context.env.KV.get("latest_manifest", "json");
  if (cached) return Response.json(cached);

  const res = await fetch(
    "https://api.github.com/repos/sympotalk/SympotalkLauncher-APK/releases/latest",
    { headers: { "User-Agent": "SympotalkManifestWorker" } }
  );
  const release = await res.json();
  const manifest = buildManifest(release); // sha256, versionCode, rollback URL 포함
  await context.env.KV.put("latest_manifest", JSON.stringify(manifest), { expirationTtl: 3600 });
  return Response.json(manifest);
}
```

---

## 최종 권장 아키텍처

```
[Cloudflare Worker — update-manifest.js]
  ├─ version, versionCode
  ├─ minNativeVersion          (web/native 호환 최솟값)
  ├─ sha256                    (APK 무결성)
  ├─ apkUrl
  ├─ lastKnownGoodVersion      (rollback APK 소스)
  ├─ lastKnownGoodApkUrl
  ├─ lastKnownGoodWebBaseUrl   (rollback web content 소스)
  ├─ lastKnownGoodSha256
  └─ maintenanceOnly

[DPC App — com.sympotalk.dpc  ← release.keystore 동일 사용]
  ├─ UpdateScheduler           (Maintenance Window + 배터리/화면/idle 조건)
  ├─ Downloader                (HttpURLConnection, atomic .tmp→rename, 실시간 checksum)
  ├─ Verifier                  (SHA-256 + 서명 인증서 검증)
  ├─ PackageInstaller          (Session 기반 무음 설치, install mutex)
  ├─ InstallErrorHandler       (INSTALL_FAILED 코드 매핑, 지수 백오프)
  ├─ StateMachine              (SharedPreferences 영속화, 재부팅 복구)
  ├─ RollbackMonitor           (crash 임계값 평가 → ROLLBACK_PENDING)
  ├─ WebContentRollback        (APK rollback 시 web content 동반 다운그레이드)
  └─ UpdateService             (Bound Service — Launcher에 read-only 노출)

[SympotalkLauncher App  ← 동일 release.keystore]
  ├─ HealthReporter            (reportHealthOk, reportCrashEvent, reportIdleState)
  ├─ NativeWebProtocolSync     (minNativeVersion 체크, 웹 업데이트 보류)
  └─ MaintenanceScreen         (DPC 상태 조회 → 업데이트 중 화면 표시, failReason 표시)
```

### 최종 추가 개발 항목

| 항목 | 규모 | 우선순위 |
|------|------|---------|
| DPC 앱 신규 개발 | 중 (~800줄 Java) | P0 |
| Cloudflare Worker Manifest 서버 | 소 (~60줄 JS) | P0 |
| CI: sha256 자동 생성 + Manifest KV 갱신 | 소 (Actions 1단계 추가) | P0 |
| Launcher 수정 (HealthReporter + web rollback 연동) | 소 (~120줄) | P0 |
| INSTALL_FAILED 코드 매핑 + 지수 백오프 | 소 (~60줄) | P0 |
| protocol versioning (minNativeVersion) | 소 (~30줄) | P1 |
| Maintenance Window UI (관리자 토글, failReason 표시) | 소 | P1 |
| webDataSchema 기반 IndexedDB 마이그레이션 | 중 | P2 (오프라인 강화 시) |
| stable/beta 채널 분리 | 중 | P2 |

---

## 델타(패치) 업데이트 — 이 프로젝트에서 불필요

현재 APK ~8MB, 행사장 WiFi 환경 → 전체 다운로드도 수초 완료.
**델타 패치 구현 대비 효익이 매우 낮다. 권장하지 않음.**

---

## 참고: 현재 코드베이스 관련 파일

| 파일 | 역할 | DPC 재사용 범위 |
|------|------|--------------|
| `android-app/.../AppUpdateManager.java` | GitHub API 조회 + 60분 캐시 + DownloadManager | **버전 비교 로직만** 재사용. 다운로드 로직(DownloadManager)은 재사용 금지. |
| `android-app/.../UpdateManager.java` | 웹 콘텐츠 업데이트 (HttpURLConnection + `.tmp→rename`) | **다운로드 패턴 전체 참조.** DPC Downloader의 직접 모델. |
| `android-app/.../MainActivity.java` | JS 브릿지 + 업데이트 트리거 | HealthReporter / CrashDetector 추가 대상 |
| `android-app/release.keystore` | Launcher 서명 keystore | DPC 앱도 동일 keystore 사용 |
| `.github/workflows/build-and-deploy.yml` | APK 빌드 + GitHub Release | sha256 생성 + Manifest KV 갱신 단계 추가 예정 |
| `functions/api/` (Cloudflare) | events, rooms, health | `update-manifest.js` 추가 예정 |
