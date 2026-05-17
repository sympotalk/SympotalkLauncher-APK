# Changelog

이 파일의 형식은 [Keep a Changelog](https://keepachangelog.com/ko/1.1.0/)를 따르며,
프로젝트는 [SemVer](https://semver.org/lang/ko/)를 사용합니다.

릴리스는 GitHub Releases에서 APK와 함께 발행됩니다: https://github.com/sympotalk/SympotalkLauncher-APK/releases

---

## [Unreleased]

(다음 릴리스에 포함될 변경사항)

---

## [1.2.0] - 2026-05-17

태블릿 부팅 burst 완화 (thermage0516 사고 보고서 RC-8 / RC-9 후속).

### Changed
- **jsQR 동기 로드 제거** — `<head>` 의 `cdn.jsdelivr.net` 동기 `<script>` 삭제. QR 탭 진입시 `ensureJsQR()` 가 self-host `/vendor/jsQR.min.js` 를 동적 로드. 첫 페인트 전 130KB 외부 CDN 다운로드 제거 → 102대 동시 부팅시 jsdelivr 향한 외부 요청 102건 → 0건.
- **proxyFetch 5초 timeout** — `AbortController` 기반. 행사장 WiFi 큐잉으로 인한 무한 펜딩 차단. `abortInFlightProxyFetches()` 헬퍼로 화면 전환시 stale 요청 일괄 취소 가능.
- **/api/rooms batch 호출** — `?event_ids=a,b,c` 신설. 클라이언트는 행사 N개당 1회로 통합 호출 (기존: N병렬). 102대×N → 102×1, AP burst 큰 폭 감소. 50개 chunk 분할 + UUID 검증 유지.
- **Service Worker 강화** — `sw.js v5 → v6`, `/vendor/jsQR.min.js` 를 STATIC_ASSETS 에 추가해 precache. Supabase 패스스루 fetch 에도 5초 timeout 적용.
- **preconnect / dns-prefetch hint** — `sympopad.com`, `live.sympopad.com`, `media.sympopad.com`, Supabase 도메인. 첫 진입 RTT 절감 (보조 최적화).
- **`/vendor/*` 캐시 정책** — `_headers` 에 `max-age=31536000, immutable` 추가. 자체 호스트 벤더 파일은 버전 잠금이라 적극 캐시.

### Bumped
- versionCode 49 → 50, versionName 1.1.9 → 1.2.0

### Migration / Ops notes
- **CACHE_NAME bump 운영 주의**: `v6` 으로 올렸으므로 다음 부팅때 모든 태블릿이 STATIC_ASSETS 를 재다운로드한다. 행사 시작 24h 이내에는 추가 CACHE_NAME bump 금지 (102대 동시 cache-miss = AP burst 재발).
- batch API 는 단일 모드(`?event_id=`) 호환 유지. 외부에서 단일 호출 패턴을 쓰는 코드가 있어도 영향 없음.

---

## [1.0.43] - 2026-04-21

### Added
- 비정상 종료 감지 — 세션 하트비트 (localStorage active 플래그 + pagehide/beforeunload/freeze/visibilitychange 훅)
- `SympotalkLog.translate` 패턴 3종: 비정상 종료 / OOM / freeze+page

### Changed
- `.page-content` `max-width: 900px` 제거 — 1920×1200 LG G Pad 5 등 넓은 태블릿에서 좌우 레터박스 해소
- `sw.js` CACHE_NAME `v1` → `v2` (PWA 구 index.html 영구 캐시 방지)

### Bumped
- versionCode 38 → 39, versionName 1.0.42 → 1.0.43

## [1.0.42] - 2026-04-16

### Added
- 앱 로그 시스템 — 자동 수집 + 사용자 친화적 설명 + 설정 뷰어

## [1.0.41]

### Changed
- QR 스캔 UX 자동 시작 + 인식 성능 개선

## [1.0.40]

### Fixed
- 상단/하단 공백 제거 — 키보드 상태별 플래그 동적 전환

## [1.0.39]

### Fixed
- 가상키보드 입력창 가림 근본 해결

---

이전 버전(v1.0.0 ~ v1.0.38)은 git log를 참조하세요:
```
git log --oneline --grep="v1\.0\."
```
