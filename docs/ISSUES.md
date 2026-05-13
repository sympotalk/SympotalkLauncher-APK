# 미해결 이슈 (Open Issues)

이 프로젝트의 보류·후속·관찰 항목을 추적합니다. 닫힌 이슈는 `docs/CHANGELOG.md` 또는 git history에서 추적하세요.

---

## 형식

각 이슈는 아래 형식으로 작성:

```
### [P0|P1|P2] 짧은 제목
- **상태**: open / blocked / in-progress
- **발견일**: YYYY-MM-DD
- **관련 파일**: path:line
- **설명**: 무엇이 문제인지
- **재현**: 어떻게 재현하는지
- **해결안 후보**: 가능한 접근법
- **링크**: 관련 커밋/PR/세션
```

우선순위:
- **P0**: 릴리스 차단, 사용자 데이터 손실, 보안
- **P1**: 사용성 저하, 다음 릴리스에서 처리 권장
- **P2**: 기능 향상, 유지보수성, 시간 날 때

---

## Open


### [P1] pagehide 정상 종료 시 INFO 로그 누적
- **상태**: open
- **발견일**: 2026-04-21
- **관련 파일**: `index.html:1185-1194`
- **설명**: `pagehide` 마다 `SympotalkLog.push('info', ...)`가 1건 추가되어 100건 rolling 버퍼가 빠르게 소진. 행사장 sleep/wake 반복 환경에서 진단 정보가 가려질 위험.
- **해결안 후보**: `e.persisted === false`인 경우만 기록 / 로그 레벨 'debug' 분리 / 버퍼 크기 확대.
- **링크**: v1.0.43 감사 리포트 P1-2

### [P2] sw.js fetch handler가 stale-while-revalidate가 아닌 cache-first
- **상태**: open
- **발견일**: 2026-04-21
- **관련 파일**: `sw.js:55-70`
- **설명**: 캐시 히트 시 네트워크를 보지 않음. v1.0.43에서 CACHE_NAME bump으로 일회성 해결했지만, 매 릴리스마다 bump를 강제하므로 장기적으로는 stale-while-revalidate 패턴이 안전.
- **해결안 후보**: `event.respondWith(cached || network)` → `event.respondWith(network ? .then(put cache) : cached)` 형태로 전환.
- **링크**: v1.0.43 감사 리포트 P2-5

---

## Closed (참고용 — 최근 5개만 유지)

| 일자 | 이슈 | 해결 커밋 |
|---|---|---|
| 2026-04-21 | 넓은 태블릿(1920px)에서 좌우 레터박스 | 170446e |
| 2026-04-21 | PWA 사용자가 구 index.html 영구 캐시 | 170446e (sw.js v1→v2) |
| 2026-04-16 | 비정상 종료 시 사용자에게 설명 부재 | 170446e (세션 하트비트) |
| 2026-05-14 | visibilitychange 리스너 3중 등록 | a4f3679 (단일 dispatcher 통합) |
| 2026-05-14 | pagehide bfcache 로그 버퍼 소진 | abe3827 (persisted=false 조건부 기록) |
| 2026-05-14 | sw.js cache-first 패턴 | abe3827 (stale-while-revalidate 전환) |
