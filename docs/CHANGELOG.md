# Changelog

이 파일의 형식은 [Keep a Changelog](https://keepachangelog.com/ko/1.1.0/)를 따르며,
프로젝트는 [SemVer](https://semver.org/lang/ko/)를 사용합니다.

릴리스는 GitHub Releases에서 APK와 함께 발행됩니다: https://github.com/sympotalk/SympotalkLauncher-APK/releases

---

## [Unreleased]

(다음 릴리스에 포함될 변경사항)

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
