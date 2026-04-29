---
description: 안전한 배포 파이프라인 — 사전검증 → 빌드 → 배포 → health check → 로그 저장.
allowed-tools: Bash(git status), Bash(git log:*), Bash(git diff:*), Bash(git push origin:*), Bash(git tag:*), Bash(gh run:*), Bash(gh release:*), Bash(curl:*), Read, Write
---

# /deploy — 안전한 배포 파이프라인

이 프로젝트의 배포 채널 (감지 결과):
- **GitHub Actions**: main push 시 APK 빌드 + GitHub Release 자동 생성
- **Cloudflare Pages**: main push 시 정적 사이트 자동 배포
- **(npm publish, vercel, netlify, docker — 감지 안 됨, 사용 안 함)**

## 절차

### 1. 사전 검증

```bash
git status                                # working tree clean? (HANDOFF.md 등 untracked는 OK)
git log origin/main..HEAD --oneline       # push 대기 커밋 목록
```

다음을 수동 검증 (CI에서도 검증되지만 미리 잡는 게 비용 절감):

```bash
# 버전 일관성
cat version.txt
grep -E 'versionName|versionCode' android-app/app/build.gradle
```

`version.txt` ↔ `versionName` 일치 필수. 다르면 중단하고 사용자에게 보고.

ES2020+ 문법 사용 검사 (Android 9 호환성):

```bash
grep -nE '\?\.|\?\?(?!=)' index.html
# 매치 있으면 중단하고 사용자에게 보고 (false positive 가능성도 있음)
```

### 2. 빌드 검증

이 프로젝트는 로컬 빌드 단계 없음 (정적 HTML). Android APK는 CI에서만 빌드.

선택: 로컬 APK 빌드 sanity check (시간 여유 있을 때만):
```bash
cd android-app && ./gradlew assembleDebug --offline
```

### 3. 환경변수 검증

`.env*` 파일이 없으므로 검증 불필요. CI 시크릿(`KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`)은 GitHub Actions에서 관리.

### 4. DB 마이그레이션 (Supabase)

`supabase/migrations/`에 새 SQL이 있는지 확인:

```bash
git diff origin/main..HEAD -- supabase/migrations/
```

새 마이그레이션 파일이 있으면 **사용자 승인 필수**:
1. 파일 내용 보여주기
2. 적용 방법: Supabase Dashboard 또는 `mcp__supabase__apply_migration`
3. 사용자 "yes" 후만 진행

### 5. 배포 실행

```bash
git push origin main
```

이 한 번의 push가 다음을 트리거:
- GitHub Actions: APK 빌드 → GitHub Release 생성/업데이트
- Cloudflare Pages: 정적 사이트 배포

### 6. CI 진행 확인

```bash
gh run list --limit 1            # 가장 최근 실행 ID 확인
gh run watch <run-id> --exit-status
```

빌드 실패 시 즉시 사용자에게 보고하고 중단.

### 7. 배포 후 health check

```bash
# Cloudflare Pages
curl -sI https://sympotalklauncher-apk.pages.dev/version.txt
curl -s  https://sympotalklauncher-apk.pages.dev/version.txt   # 새 버전 표시 확인

# GitHub Release
gh release view v$(cat version.txt) --json name,tagName,assets
```

기대값:
- `version.txt` 응답이 새 버전과 일치
- Release 자산에 `SympotalkLauncher.apk` + `SympotalkLauncher-v<X.Y.Z>.apk` 둘 다 존재

### 8. 로그 저장

`docs/deployments/<YYYY-MM-DD>-v<X.Y.Z>-deploy.md` 생성. 템플릿은 `docs/deployments/README.md` 참조.

git tag 생성은 GitHub Actions가 자동 처리하므로 별도 명령 불필요.

## 출력

```
✅ 배포 완료 v<X.Y.Z>
- 커밋: <hash>
- CI run: <URL>
- Release: https://github.com/sympotalk/SympotalkLauncher-APK/releases/tag/v<X.Y.Z>
- Pages: https://sympotalklauncher-apk.pages.dev (<expected version>)
- 로그: docs/deployments/<file>

다음 권장:
- 실기기(LG G Pad 5)에서 sanity check
- /save-session 으로 세션 마무리
```

## 실패 시 대응

- **CI 실패**: 즉시 중단, 로그 사용자에게 노출. 수정 후 재push.
- **버전 불일치**: 중단. `version.txt`와 `build.gradle` 동기화 후 재시도.
- **health check 실패**: Cloudflare 캐시 전파(보통 30초~수분) 대기 후 재시도. 5분 이상 안 되면 `/rollback` 검토.
