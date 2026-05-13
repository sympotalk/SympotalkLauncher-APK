// ADB 바이너리 관리 + spawn/exec/stream 래퍼
import { spawn, execFile } from 'child_process'
import { promisify } from 'util'
import { which } from '../utils/which'

const execFileAsync = promisify(execFile)

export interface ExecResult {
  exitCode: number
  stdout: string
  stderr: string
  durationMs: number
}

export class AdbEngine {
  private adbPath = 'adb'   // 기본: 시스템 PATH

  async init(): Promise<void> {
    // 우선순위: 1) 시스템 PATH  2) 향후 번들 바이너리
    const found = await which('adb')
    if (!found) {
      throw new Error(
        'adb를 찾을 수 없습니다. Android SDK Platform-Tools를 설치하고 PATH에 추가하세요.'
      )
    }
    this.adbPath = found
  }

  getPath(): string {
    return this.adbPath
  }

  // 단발 명령 — 결과를 모아서 반환
  async exec(args: string[], timeoutMs = 30_000): Promise<ExecResult> {
    const start = Date.now()
    try {
      const { stdout, stderr } = await execFileAsync(this.adbPath, args, {
        timeout: timeoutMs,
        maxBuffer: 10 * 1024 * 1024  // 10MB
      })
      return {
        exitCode: 0,
        stdout: stdout.trim(),
        stderr: stderr.trim(),
        durationMs: Date.now() - start
      }
    } catch (err: unknown) {
      const e = err as NodeJS.ErrnoException & { stdout?: string; stderr?: string; code?: number | string }
      return {
        exitCode: typeof e.code === 'number' ? e.code : 1,
        stdout: (e.stdout ?? '').toString().trim(),
        stderr: (e.stderr ?? e.message ?? '').toString().trim(),
        durationMs: Date.now() - start
      }
    }
  }

  // 스트리밍 명령 — logcat 등 장기 실행
  stream(
    args: string[],
    onData: (line: string) => void,
    signal: AbortSignal
  ): Promise<void> {
    return new Promise((resolve, reject) => {
      const proc = spawn(this.adbPath, args)

      signal.addEventListener('abort', () => {
        proc.kill()
        resolve()
      })

      let buffer = ''
      proc.stdout.on('data', (chunk: Buffer) => {
        buffer += chunk.toString()
        const lines = buffer.split('\n')
        buffer = lines.pop() ?? ''
        lines.forEach(line => line && onData(line))
      })

      proc.on('close', () => resolve())
      proc.on('error', reject)
    })
  }

  // adb server 재시작 (연결 이슈 복구용)
  async killServer(): Promise<void> {
    await this.exec(['kill-server'])
  }

  async startServer(): Promise<void> {
    await this.exec(['start-server'])
  }
}
