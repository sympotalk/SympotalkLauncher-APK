# adb-deployer CLAUDE.md

Claude Code가 이 디렉토리에서 빌드·커밋·배포 작업 시 반드시 읽는 규칙입니다.

---

## 버전 규칙 ⚠️

버전은 `adb-deployer/package.json`의 `"version"` 필드 **하나만** 관리합니다.
빌드 결과물 파일명(`Sympotalk ADB Deployer 0.2.0.exe`)과 UI 헤더(`v0.2.0`)는 자동으로 반영됩니다.

### 언제 올려야 하나

| 변경 종류 | 올릴 숫자 | 예 |
|---|---|---|
| 버그픽스, 오탐 수정, 텍스트 수정 | **PATCH** (+0.0.1) | `0.2.0 → 0.2.1` |
| 새 기능 추가, UI 개편, 사전조건 추가 | **MINOR** (+0.1.0) | `0.2.0 → 0.3.0` |
| 아키텍처 변경, 대규모 재설계 | **MAJOR** (+1.0.0) | `0.2.0 → 1.0.0` |

### 커밋 전 체크리스트

```bash
# 1. 버전 확인
grep '"version"' adb-deployer/package.json

# 2. 빌드
cd adb-deployer && npm run build

# 3. 패키징
cd adb-deployer && npx electron-builder --win portable nsis --publish never

# 4. 결과물 확인
ls adb-deployer/dist/*.exe
```

### 커밋 메시지 형식

```
feat: <기능 설명> (v0.2.0)
fix:  <버그 설명>  (v0.2.1)
```

버전 변경이 있을 때는 커밋 메시지 끝에 `(vX.Y.Z)` 를 붙입니다.

---

## 로컬 배포 절차

```bash
cd adb-deployer

# 1. 버전 올리기 (package.json "version" 수정)
# 2. 빌드
npm run build

# 3. 패키징 (dist/ 에 exe 생성)
npx electron-builder --win portable nsis --publish never

# 4. 결과물
# dist/Sympotalk ADB Deployer X.Y.Z.exe          ← 포터블 (설치 불필요)
# dist/Sympotalk ADB Deployer Setup X.Y.Z.exe    ← NSIS 설치 프로그램
```

---

## 주요 파일

| 파일 | 역할 |
|---|---|
| `package.json` | 버전 단일 소스 |
| `electron.vite.config.ts` | `__APP_VERSION__`을 렌더러에 빌드타임 주입 |
| `electron-builder.config.js` | 패키징 설정 (외부 리소스 포함) |
| `src/renderer/App.tsx` | 헤더에 `v{__APP_VERSION__}` 표시 |
| `src/main/driver/DriverInstaller.ts` | USB 드라이버 자동 설치 |
| `src/main/adb/AdbEngine.ts` | bundled adb 경로 탐색 |
