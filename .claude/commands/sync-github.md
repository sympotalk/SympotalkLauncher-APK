---
description: 변경사항을 의미 단위로 커밋하고 origin에 푸시합니다. 미해결 이슈는 GitHub Issues로 동기화.
allowed-tools: Bash(git status), Bash(git diff:*), Bash(git add:*), Bash(git commit:*), Bash(git push origin:*), Bash(git log:*), Bash(gh issue:*), Bash(gh pr:*), Read, Write
---

# /sync-github — GitHub 동기화

## 절차

### 1. 사전 확인

```bash
git status
git diff --stat
git log origin/main..HEAD
```

- staged/unstaged 변경 모두 확인.
- HANDOFF.md, .env 등 커밋 금지 파일이 staging에 들어가려는지 점검.

### 2. 변경을 의미있는 단위로 분할

같은 파일에 여러 관심사가 섞여 있으면 `git add -p`로 hunk 단위 분할.

### 3. Conventional Commits 형식으로 커밋

이 프로젝트의 컨벤션(한국어 + 버전 suffix):

```
<type>: <한국어 한 줄 요약> (vX.Y.Z 인 경우만)

- 변경점 1
- 변경점 2

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
```

타입: `feat:` `fix:` `chore:` `refactor:` `docs:` `style:` `test:`

**버전 bump 동반 시**:
- `version.txt` + `android-app/app/build.gradle` (versionName + versionCode) 동기화 필수.
- 커밋 제목에 `(vX.Y.Z)` suffix 포함.

### 4. 푸시

```bash
git push origin <current-branch>
```

main 푸시는 자동으로 다음을 트리거:
- GitHub Actions: APK 빌드 + Release
- Cloudflare Pages: 정적 사이트 배포

### 5. PR 초안 (브랜치가 main이 아닐 때만)

`docs/sessions/latest-pr-draft.md`에 PR 본문 초안 저장:

```markdown
## Summary
- bullet 1
- bullet 2

## Test plan
- [ ] 실기기 sanity check
- [ ] 감사 서브에이전트 P0 없음 확인
- [ ] CI 통과
```

`gh pr create`는 사용자 승인 후만 실행.

### 6. 미해결 이슈를 GitHub Issues로 동기화

`docs/ISSUES.md` 의 `## Open` 섹션에서 GitHub Issues에 없는 항목을 추가:

```bash
gh issue create \
  --title "[P1] <제목>" \
  --label "priority/p1,bug" \
  --body "<설명 + 관련 파일 + 해결안 후보>"
```

라벨 매핑:
- `[P0]` → `priority/p0`, `bug`
- `[P1]` → `priority/p1`
- `[P2]` → `priority/p2`, `enhancement`

이미 등록된 항목은 중복 생성하지 말 것 (`gh issue list`로 사전 확인).

## 출력

```
✅ 동기화 완료
- 커밋: <hash 목록>
- 푸시: origin/<branch>
- 이슈 등록: #<num> #<num>
- CI: gh run watch <run-id> (자동 트리거됨)
```

## 주의

- `git push --force`는 절대 자동 실행 금지. 필요 시 사용자에게 명시 확인.
- HANDOFF.md, `.claude/session.log`, `.env*`이 `.gitignore`에서 제외되었는지 확인.
- 메인 브랜치 직접 푸시는 신중히 — PR 워크플로우가 있는지 확인.
