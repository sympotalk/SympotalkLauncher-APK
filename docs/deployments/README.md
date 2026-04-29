# Deployment Logs

배포 기록. 각 릴리스의 배포 시점, 검증 결과, 이슈를 추적합니다.

## 파일명 규칙

```
YYYY-MM-DD-vX.Y.Z-deploy.md
```

예: `2026-04-21-v1.0.43-deploy.md`

## 템플릿

```markdown
# Deployment: vX.Y.Z

- **일시**: 2026-04-21 12:43 KST
- **커밋**: <hash>
- **트리거**: push to main (자동) / workflow_dispatch (수동)
- **결과**: success / partial / rolled-back

## 사전 검증
- [ ] git status clean (작업 폴더 외)
- [ ] version.txt ↔ build.gradle versionName 일치
- [ ] 감사 서브에이전트 P0 없음
- [ ] 실기기 빠른 sanity check (선택)

## 배포 채널
- GitHub Actions: <run URL>
- GitHub Release: vX.Y.Z (APK 첨부)
- Cloudflare Pages: 자동 트리거 (URL: https://sympotalklauncher-apk.pages.dev)

## 검증 (배포 후)
- [ ] APK 다운로드 → 설치 성공
- [ ] 앱 내 "버전 확인" 결과 새 버전 표시
- [ ] 핵심 기능 sanity (행사 목록 / WiFi / 콘솔 링크)

## 이슈
(없으면 "없음")

## 롤백 정보
- 직전 안정 버전: vX.Y.(Z-1)
- 롤백 절차: docs/incidents/ 참조
```

## 자동화

`/deploy` 슬래시 커맨드가 이 디렉토리에 자동 기록합니다.
