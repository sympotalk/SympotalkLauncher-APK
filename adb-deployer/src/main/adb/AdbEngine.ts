// ADB 바이너리 관리 + spawn/exec/stream 래퍼
import { spawn, execFile } from 'child_process'
import { existsSync } from 'fs'
import { join } from 'path'
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
    const exeName = process.platform === 'win32' ? 'adb.exe' : 'adb'

    // 탐색 우선순위:
    // 1) 패키징된 앱 — resources/platform-tools/ (extraResources로 번들)
    // 2) 개발 모드  — <project>/platform-tools/ (out/main/ 기준 2단계 위)
    const candidates = [
      join(process.resourcesPath, 'platform-tools', exeName),
      join(__dirname, '..', '..', 'platform-tools', exeName),
    ]

    for (const candidate of candidates) {
      if (existsSync(candidate)) {
        this.adbPath = candidate
        return
      }
    }

    // 시스템 PATH 폴백
    const found = await which('adb')
    if (found) {
      this.adbPath = found
      return
    }

    throw new Error(
      'adb를 찾을 수 없습니다. platform-tools 폴더가 앱과 함께 있어야 합니다.'
    )
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
