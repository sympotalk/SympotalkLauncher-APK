# Session Logs

각 Claude Code 작업 세션의 기록을 저장합니다.

## 파일명 규칙

```
YYYY-MM-DD-HHMM-session.md
```

예: `2026-04-21-1430-session.md` (2026년 4월 21일 14:30 시작)

## 템플릿

```markdown
# Session: <한 줄 요약>

- **일시**: 2026-04-21 14:30 ~ 16:15
- **목표**: <이번 세션에서 달성하려는 것>
- **상태**: completed / partial / blocked

## 변경한 파일
- path:line — 무엇을 어떻게

## 만난 에러 / 해결
- 증상 → 원인 → 해결

## 테스트 결과
- 실행한 검증 (실기기 / CI / 감사 서브에이전트 등)

## TODO (다음 세션)
- [ ] 항목 1
- [ ] 항목 2

## 관련
- 커밋: <hash>
- 이슈: docs/ISSUES.md#…
```

## 생성 자동화

`/save-session` 슬래시 커맨드를 사용하면 자동으로 이 디렉토리에 파일이 생성됩니다.
