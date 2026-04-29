---
description: 현재 브랜치를 프리뷰 환경에 배포합니다 (main에 영향 없음).
allowed-tools: Bash(git push origin:*), Bash(git status), Bash(git branch:*), Bash(gh pr:*)
---

# /deploy-preview — 프리뷰 배포

현재 브랜치를 origin에 push하면 Cloudflare Pages가 자동으로 **프리뷰 URL**을 생성합니다 (main 외 브랜치는 production이 아닌 preview 채널로 배포됨).

## 절차

### 1. 현재 브랜치 확인

```bash
git branch --show-current
git status
```

- `main`이면 중단하고 사용자에게 안내: "프리뷰는 feature 브랜치에서만. `git checkout -b feat/<name>` 후 재시도."

### 2. 푸시

```bash
git push -u origin <current-branch>
```

### 3. 프리뷰 URL 확보

Cloudflare Pages는 빌드 후 코멘트로 URL을 알립니다. PR이 있으면:

```bash
gh pr view --json url,number,headRefName
```

PR이 없으면 Cloudflare Pages 대시보드에서 확인. 일반적인 URL 패턴:
```
https://<commit-hash-prefix>.sympotalklauncher-apk.pages.dev
```

### 4. PR이 있으면 코멘트 추가

```bash
gh pr comment <PR번호> --body "🌐 프리뷰: <URL>"
```

PR이 없으면 사용자에게 URL만 보여주고 종료.

## 출력

```
✅ 프리뷰 배포 트리거됨
- 브랜치: <branch>
- 커밋: <hash>
- 프리뷰 URL (예상): https://<branch>.sympotalklauncher-apk.pages.dev
- 빌드 진행: gh run list --limit 1

PR이 없으면 `gh pr create`로 PR을 만들면 자동으로 프리뷰 URL이 코멘트됩니다.
```

## 주의

- 이 커맨드는 **APK를 빌드하지 않습니다.** APK 빌드는 main push에서만 발생.
- 프리뷰 URL의 보안: 비공개가 아닙니다. 시크릿이 페이지에 노출되지 않게 주의.
