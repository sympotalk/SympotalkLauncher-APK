---
description: 새 세션 시작 시 프로젝트 컨텍스트를 빠르게 복원합니다.
allowed-tools: Read, Bash(git log:*), Bash(git status), Bash(gh run:*), Glob, Grep
---

# /init-context — 컨텍스트 복원

새 Claude Code 세션 시작 시 다음을 자동 수집해 "이어서 할 작업" 요약을 만듭니다.

## 수집 항목

다음을 **병렬로** 수행:

1. **CLAUDE.md 읽기** — 프로젝트 개요, 컨벤션, 최근 변경 5개, 알려진 이슈
2. **최근 세션 로그 3개**:
   ```
   ls -t docs/sessions/*.md | head -3
   ```
   각 파일을 Read로 읽어 TODO 섹션 추출.
3. **미해결 이슈**: `docs/ISSUES.md` 의 "## Open" 섹션 전체 읽기
4. **최근 커밋 20개**:
   ```
   git log --oneline -20
   ```
5. **현재 git 상태**:
   ```
   git status
   git stash list
   ```
6. **CI 최근 런 3개** (gh CLI 인증되어 있으면):
   ```
   gh run list --limit 3
   ```
7. **HANDOFF.md** 가 있으면 읽어 "현재 목표"·"남은 작업" 섹션 추출
8. **메모리 인덱스** (있을 경우):
   ```
   Read C:\Users\이경민\.claude\projects\C--Users-----source-repos-sympopad-web\memory\MEMORY.md
   ```

## 출력 (사용자에게 보여줄 형식)

```
## 프로젝트: sympopad_web
- 현재 브랜치: <branch> (<ahead/behind>)
- 마지막 커밋: <hash> <subject>
- CI 상태: <success / failed / queued>

## 이전 세션에서 이어서 할 작업
1. [P0] <항목>  ← docs/ISSUES.md 또는 최근 세션 TODO에서
2. [P1] <항목>
...

## 미해결 이슈 (요약)
- P1: <개수>건
- P2: <개수>건

## 추천 첫 액션
- "<구체적 다음 단계>"
```

## 주의

- 파일이 없으면(예: 첫 세션) "<없음>"으로 표기하고 진행.
- 메모리 파일은 7일 이상 된 경우 "stale 가능성" 경고 함께 출력.
- 수집된 사실에서 추론하지 말고 **인용**으로만 답변. 모르면 "확인 필요"로 표기.
