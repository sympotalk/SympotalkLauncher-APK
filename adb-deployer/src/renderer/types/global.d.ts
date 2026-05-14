// window.api 전역 타입 선언 — contextBridge 노출 API
import type { Device } from './Device'
import type { DispatchResult, LogEntry, AppSettings, PrerequisiteCheckResult } from './Command'

declare global {
  interface Window {
    api: {
      // ─── 기기 이벤트 (cleanup 함수 반환) ──────────────────
      onDeviceListUpdated:  (cb: (devices: Device[]) => void) => (() => void)
      onExecutionProgress:  (cb: (result: DispatchResult, remaining: number) => void) => (() => void)
      onExecutionComplete:  (cb: (results: DispatchResult[]) => void) => (() => void)

      // ─── 기기 ───────────────────────────────────────────
      inspectDevice:  (serial: string) => Promise<Partial<Device>>
      startPoller:    () => Promise<{ ok: boolean; error?: string }>
      stopPoller:     () => Promise<void>

      // ─── 명령 실행 ──────────────────────────────────────
      executeCommand: (serial: string, args: string[]) => Promise<DispatchResult>
      executeBatch:   (serials: string[], args: string[], timeoutMs: number) => Promise<DispatchResult[]>
      cancelExecution: () => Promise<void>

      // ─── APK ────────────────────────────────────────────
      selectApk:   (type: 'dpc' | 'launcher') => Promise<string | null>
      getApkPaths: () => Promise<{ dpcApkPath: string | null; launcherApkPath: string | null }>

      // ─── 설정 ───────────────────────────────────────────
      getSettings: () => Promise<AppSettings>
      setSettings: (patch: Partial<AppSettings>) => Promise<void>

      // ─── 로그 ───────────────────────────────────────────
      queryLogs:  (filter?: { serial?: string; status?: string }) => Promise<LogEntry[]>
      exportLogs: (format: 'csv' | 'txt', filter?: { status?: string }) => Promise<string | null>

      // ─── 보안 ───────────────────────────────────────────
      checkCommand:         (args: string[]) => Promise<string | null>
      checkDoPrerequisites: (serial: string) => Promise<PrerequisiteCheckResult>

      // ─── USB 드라이버 ────────────────────────────────────
      checkDriver:   () => Promise<boolean>
      installDriver: () => Promise<{ ok: boolean; message: string }>
    }
  }
}

declare const __APP_VERSION__: string

export {}
