---
description: 직전 안정 버전으로 긴급 롤백합니다. 반드시 사용자 승인 후 실행.
allowed-tools: Bash(git log:*), Bash(git revert:*), Bash(git push origin:*), Bash(gh release:*), Bash(gh issue create:*), Read, Write
---

# /rollback — 긴급 롤백

서비스 중단·치명적 버그 시 직전 안정 버전으로 되돌립니다.

## 절차

### 1. 직전 안정 버전 식별

```bash
git log --oneline -10
gh release list --limit 5
```

후보:
- `version.txt`가 가리키는 현재 버전 직전(`v(N-1)`)
- 마지막으로 GitHub Release `make_latest=true`였던 태그
- CI 마지막 success 커밋

### 2. 롤백 계획 출력 (사용자 승인 대기)

```
🚨 롤백 계획

현재: v1.0.43 (commit 170446e) — 문제 발생
목표: v1.0.42 (commit 789cbcc) — 마지막 안정

방식: revert (forward commit, 히스토리 보존)
  ├─ git revert 170446e --no-edit
  ├─ version.txt: 1.0.43 → 1.0.42
  ├─ build.gradle: versionCode 39 → 38, versionName "1.0.43" → "1.0.42"
  └─ git push origin main

영향:
  - APK CI가 v1.0.42를 다시 빌드 (versionCode 동일하면 거부될 수 있음 → 별도 처리)
  - Cloudflare Pages 즉시 배포

⚠️  versionCode 후퇴는 Android에서 거부됨. revert 후 versionCode를 41로 bump하고
    versionName은 "1.0.42-rollback" 처럼 표기 권장.

계속 진행할까요? (yes/no)
```

**`yes` 입력 전엔 절대 실행하지 말 것.**

### 3. 승인 후 실행

```bash
git revert <bad-commit> --no-edit
# 필요 시 version.txt + build.gradle 수정 (versionCode는 forward bump)
git add version.txt android-app/app/build.gradle
git commit --amend --no-edit
git push origin main
```

### 4. 인시던트 기록

`docs/incidents/<YYYY-MM-DD>-<HHMM>-<short-slug>.md` 생성. 템플릿은 `docs/incidents/README.md` 참조.

### 5. GitHub Issue 자동 생성

```bash
gh issue create \
  --title "[INCIDENT] <원인 요약>" \
  --label "priority/p0,incident" \
  --body "$(cat <<EOF
## 발생
<일시 + 영향 범위>

## 즉시 조치
- 롤백 커밋: <hash>
- 롤백 대상: <bad commit hash>

## 근본 원인 (조사 필요)
TODO

## 후속 작업
- [ ] 근본 원인 분석
- [ ] 재발 방지 PR
- [ ] docs/incidents/<file> 작성

상세: docs/incidents/<file>
EOF
)"
```

### 6. CI 모니터링

```bash
gh run watch <new-run-id> --exit-status
```

빌드 실패하면 사용자에게 즉시 보고. 더 이전 버전으로 추가 롤백 검토.

## 출력

```
🔄 롤백 완료
- 롤백 커밋: <hash>
- 새 버전: v<X.Y.Z>-rollback
- CI: <URL>
- 인시던트 보고서: docs/incidents/<file>
- GitHub Issue: #<num>

다음:
- 근본 원인 분석 후 docs/incidents/<file> 의 "근본 원인" 섹션 작성
- 재발 방지 PR 작성
```

## 주의 (절대 자동 실행 금지)

- **`git push --force`** 사용 금지. revert 패턴만 사용.
- **`git reset --hard`** 사용 금지. 커밋 히스토리 보존.
- versionCode를 후퇴시키지 말 것 (Android Play 거부, GitHub Release는 허용해도 사용자 OTA가 거부됨).
- 사용자 명시 승인 없이 진행 금지.
