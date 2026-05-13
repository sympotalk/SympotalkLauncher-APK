// 실행 로그 인메모리 버퍼 + JSONL 파일 영속화
import type { App } from 'electron'
import { createWriteStream, mkdirSync, WriteStream } from 'fs'
import { join } from 'path'
import { v4 as uuidv4 } from 'uuid'
import type { LogEntry } from '../../renderer/types/Command'

export class LogStore {
  private buffer: LogEntry[] = []
  private fileStream: WriteStream | null = null
  readonly sessionId: string
  private _app: App | null = null

  constructor() {
    this.sessionId = uuidv4()
  }

  setApp(app: App): void {
    this._app = app
  }

  private ensureStream(): WriteStream {
    if (this.fileStream) return this.fileStream
    if (!this._app) throw new Error('LogStore.setApp() must be called before appending logs')
    const logsDir = join(this._app.getPath('userData'), 'logs')
    mkdirSync(logsDir, { recursive: true })
    const filename = `session-${new Date().toISOString().replace(/[:.]/g, '-')}.jsonl`
    this.fileStream = createWriteStream(join(logsDir, filename), { flags: 'a' })
    return this.fileStream
  }

  append(entry: Omit<LogEntry, 'id' | 'sessionId'>): LogEntry {
    const full: LogEntry = { ...entry, id: uuidv4(), sessionId: this.sessionId }
    this.buffer.push(full)
    this.ensureStream().write(JSON.stringify(full) + '\n')
    return full
  }

  query(filter?: Partial<Pick<LogEntry, 'serial' | 'status'>>): LogEntry[] {
    if (!filter) return [...this.buffer]
    return this.buffer.filter(e =>
      (!filter.serial || e.serial === filter.serial) &&
      (!filter.status || e.status === filter.status)
    )
  }

  close(): void {
    this.fileStream?.end()
  }
}
