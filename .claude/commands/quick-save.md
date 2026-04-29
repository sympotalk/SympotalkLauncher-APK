---
description: /save-session + /sync-github을 순차 실행하는 단축 커맨드.
---

# /quick-save — 세션 저장 + GitHub 동기화

다음 두 커맨드를 차례로 실행합니다:

1. **`/save-session`**
   - `docs/sessions/<timestamp>-session.md` 생성
   - `CLAUDE.md` 최근 변경 갱신
   - `docs/CHANGELOG.md` Unreleased 또는 새 버전 항목 추가
   - 미해결 이슈 `docs/ISSUES.md`에 등록

2. **`/sync-github`**
   - 변경사항 의미 단위 커밋 (Conventional Commits, 한국어, 버전 suffix)
   - origin push
   - 미해결 이슈 GitHub Issues로 동기화
   - PR 브랜치면 PR 초안 저장

## 사용 시점

작업 마치고 자리 떠나기 직전 / 하루 끝낼 때 / 큰 마일스톤 도달 직후.

## 주의

- 이 커맨드는 **자동 푸시**합니다. 미완성 변경이 있으면 먼저 stash하거나 별도 브랜치로 옮긴 뒤 실행하세요.
- 시크릿/credential이 staging에 있는지 `/sync-github` 1단계에서 자동 점검합니다.
