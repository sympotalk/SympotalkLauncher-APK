// 단일 기기 명령 실행 — timeout + abort 지원
import { spawn } from 'child_process'
import type { AdbEngine, ExecResult } from './AdbEngine'

export class CommandExecutor {
  constructor(private engine: AdbEngine) {}

  // 단일 기기에 명령 실행 (serial 자동 포함)
  async run(
    serial: string,
    args: string[],
    timeoutMs = 30_000,
    signal?: AbortSignal
  ): Promise<ExecResult> {
    const fullArgs = ['-s', serial, ...args]
    const start = Date.now()

    return new Promise<ExecResult>((resolve) => {
      const proc = spawn(this.engine.getPath(), fullArgs)
      let stdout = ''
      let stderr = ''
      let settled = false

      const settle = (result: ExecResult) => {
        if (settled) return
        settled = true
        proc.kill()
        resolve(result)
      }

      // abort signal
      signal?.addEventListener('abort', () =>
        settle({ exitCode: -1, stdout, stderr, durationMs: Date.now() - start })
      )

      // timeout
      const timer = setTimeout(
        () => settle({ exitCode: -1, stdout, stderr: 'timeout', durationMs: timeoutMs }),
        timeoutMs
      )

      proc.stdout.on('data', (c: Buffer) => { stdout += c.toString() })
      proc.stderr.on('data', (c: Buffer) => { stderr += c.toString() })

      proc.on('close', (code) => {
        clearTimeout(timer)
        settle({
          exitCode: code ?? 0,
          stdout: stdout.trim(),
          stderr: stderr.trim(),
          durationMs: Date.now() - start
        })
      })

      proc.on('error', (err) => {
        clearTimeout(timer)
        settle({ exitCode: 1, stdout, stderr: err.message, durationMs: Date.now() - start })
      })
    })
  }
}
