---
description: 작업한 내용을 docs/sessions/에 기록하고 CLAUDE.md, CHANGELOG.md를 갱신합니다.
allowed-tools: Read, Write, Edit, Bash(git log:*), Bash(git status), Bash(git diff:*), Bash(date:*)
---

# /save-session — 세션 종료 시 작업 문서화

## 절차

### 1. 메타데이터 수집

```bash
date +"%Y-%m-%d-%H%M"   # 파일명용 timestamp
git log --oneline -10
git diff --stat HEAD~1
git status
```

### 2. 세션 로그 파일 생성

경로: `docs/sessions/<YYYY-MM-DD-HHMM>-session.md`

템플릿:
```markdown
# Session: <한 줄 요약>

- **일시**: <YYYY-MM-DD HH:MM> ~ <YYYY-MM-DD HH:MM>
- **목표**: <이번 세션의 목표>
- **상태**: completed / partial / blocked

## 변경한 파일
- `path:line` — <무엇을 어떻게 / 왜>

## 만난 에러 / 해결
- 증상 → 원인 → 해결

## 테스트 결과
- 실기기 / CI / 감사 서브에이전트 결과

## TODO (다음 세션)
- [ ] 항목 1
- [ ] 항목 2

## 관련
- 커밋: <hash 목록>
- 이슈: docs/ISSUES.md#…
```

### 3. CLAUDE.md "최근 변경사항" 섹션 갱신

`## 8. 최근 변경사항 (최신 5개)` 표를 업데이트.

- 새 항목을 맨 위에 추가
- 6번째 이상 오래된 행은 삭제 (5개 유지)

### 4. docs/CHANGELOG.md 갱신

새 릴리스 배포가 포함됐다면:
- `## [Unreleased]` 섹션의 변경사항을 새 버전 헤더로 cut
- Keep a Changelog 형식: Added / Changed / Fixed / Removed / Bumped

세션이 릴리스 없이 끝났다면:
- `## [Unreleased]` 섹션에만 변경사항을 누적

### 5. 미해결 이슈는 docs/ISSUES.md에 등록

세션 중 발견하고 못 끝낸 항목을 `## Open` 섹션에 추가:

```markdown
### [P0|P1|P2] 짧은 제목
- **상태**: open
- **발견일**: <YYYY-MM-DD>
- **관련 파일**: path:line
- **설명**: …
- **해결안 후보**: …
- **링크**: 이번 세션 docs/sessions/<file>
```

## 출력

마지막에 다음을 사용자에게 보여줌:

```
✅ 세션 저장 완료
- docs/sessions/<file> (<bytes>)
- CLAUDE.md 최근 변경 갱신
- docs/CHANGELOG.md <Unreleased | vX.Y.Z>
- docs/ISSUES.md +<n>건 추가

다음 세션은 /init-context 로 시작하면 자동 복원됩니다.
```

## 주의

- 자동 git commit 하지 않음 (커밋은 `/sync-github` 책임).
- 시크릿/credential을 세션 로그에 적지 말 것. 변경한 환경변수는 **이름만** 기록.
