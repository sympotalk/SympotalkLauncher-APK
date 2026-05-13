// 명령 실행 관련 타입 정의
export interface CommandPreset {
  id: string
  label: string
  description: string
  args: string[]            // adb 인수 배열 (serial 제외)
  requiresConfirm?: boolean
  requiresApkPath?: 'dpc' | 'launcher'
  retryCount?: number       // 기본 1 (재시도 없음)
  dangerLevel?: 'safe' | 'warn' | 'danger'
}

export interface DispatchJob {
  serial: string
  args: string[]
  timeoutMs: number
  presetId?: string
}

export interface DispatchResult {
  serial: string
  exitCode: number
  stdout: string
  stderr: string
  durationMs: number
  status: 'success' | 'failure' | 'timeout' | 'aborted'
  errorMessage?: string
}

export interface LogEntry {
  id: string
  sessionId: string
  timestamp: string         // ISO 8601
  serial: string
  command: string           // 실제 실행된 adb 인수 문자열
  exitCode: number
  stdout: string
  stderr: string
  durationMs: number
  status: 'success' | 'failure' | 'timeout' | 'aborted'
  presetId?: string
}

export interface AppSettings {
  concurrency: number       // 동시 실행 수 (기본 5)
  timeoutMs: number         // 기본 30000ms
  dpcApkPath: string | null
  launcherApkPath: string | null
}

export interface PrerequisiteItem {
  label: string
  passed: boolean
}

export interface PrerequisiteCheckResult {
  passed: boolean
  items: PrerequisiteItem[]
}
