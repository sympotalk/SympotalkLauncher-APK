// 기기 목록 주기적 폴링 — 연결/해제 이벤트 발송
import { EventEmitter } from 'events'
import type { AdbEngine } from './AdbEngine'
import type { Device, DeviceStatus } from '../../renderer/types/Device'

// adb devices 파싱 — "serial\tstatus" 형식
const DEVICE_LINE_RE = /^(\S+)\s+(device|unauthorized|offline|no permissions|disconnected)/

export class DevicePoller extends EventEmitter {
  private intervalId: ReturnType<typeof setInterval> | null = null
  private previous = new Map<string, DeviceStatus>()

  constructor(private engine: AdbEngine) {
    super()
  }

  start(intervalMs = 2000): void {
    if (this.intervalId) return
    this.poll()
    this.intervalId = setInterval(() => this.poll(), intervalMs)
  }

  stop(): void {
    if (this.intervalId) {
      clearInterval(this.intervalId)
      this.intervalId = null
    }
  }

  private async poll(): Promise<void> {
    try {
      const result = await this.engine.exec(['devices'])
      const current = this.parseDevices(result.stdout)
      this.diffAndEmit(current)
    } catch {
      // 폴링 오류는 무시 (adb server 미실행 등)
    }
  }

  parseDevices(output: string): Array<Pick<Device, 'serial' | 'status'>> {
    return output
      .split(/\r?\n/)
      .filter(line => DEVICE_LINE_RE.test(line))
      .map(line => {
        const m = line.match(DEVICE_LINE_RE)!
        return { serial: m[1], status: m[2] as DeviceStatus }
      })
  }

  private diffAndEmit(current: Array<Pick<Device, 'serial' | 'status'>>): void {
    const currentMap = new Map(current.map(d => [d.serial, d.status]))

    // 새로 연결된 기기
    for (const [serial, status] of currentMap) {
      if (!this.previous.has(serial)) {
        this.emit('device-connected', { serial, status })
      } else if (this.previous.get(serial) !== status) {
        this.emit('device-status-changed', { serial, status })
      }
    }

    // 해제된 기기
    for (const serial of this.previous.keys()) {
      if (!currentMap.has(serial)) {
        this.emit('device-disconnected', { serial, status: 'disconnected' as DeviceStatus })
      }
    }

    this.previous = currentMap
    this.emit('device-list-updated', current)
  }
}
