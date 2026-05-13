// 기기 상태 및 정보 타입 정의
export type DeviceStatus =
  | 'device'
  | 'unauthorized'
  | 'offline'
  | 'no permissions'
  | 'disconnected'

export interface Device {
  serial: string
  status: DeviceStatus
  modelName?: string
  androidVersion?: string
  batteryLevel?: number
  isDeviceOwner?: boolean
  dpcInstalled?: boolean
  launcherInstalled?: boolean
  lastCommandResult?: CommandResult
}

export interface CommandResult {
  exitCode: number
  stdout: string
  stderr: string
  durationMs: number
  status: 'success' | 'failure' | 'timeout' | 'aborted'
  errorMessage?: string  // stderr를 사람이 읽을 수 있는 메시지로 변환
}
