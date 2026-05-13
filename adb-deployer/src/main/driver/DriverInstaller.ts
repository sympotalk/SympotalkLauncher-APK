// USB ADB 드라이버 감지 및 UAC 상승 자동 설치 (Windows 전용)
import { cpSync, existsSync, mkdirSync, readFileSync } from 'fs'
import { join } from 'path'
import { tmpdir } from 'os'
import { execFile, spawn } from 'child_process'
import { promisify } from 'util'

const execFileAsync = promisify(execFile)

export class DriverInstaller {
  private driverDir: string | null = null

  setDriverDir(dir: string): void {
    this.driverDir = dir
  }

  async isInstalled(): Promise<boolean> {
    try {
      const { stdout } = await execFileAsync('pnputil', ['/enum-drivers'], { timeout: 10_000 })
      return stdout.toLowerCase().includes('android_winusb')
    } catch {
      return false
    }
  }

  async install(): Promise<{ ok: boolean; message: string }> {
    if (!this.driverDir || !existsSync(this.driverDir)) {
      return { ok: false, message: '번들 드라이버 파일을 찾을 수 없습니다.' }
    }

    const tempDir = join(tmpdir(), 'adb-driver-install')
    const resultFile = join(tmpdir(), 'adb-driver-result.txt')

    try {
      mkdirSync(tempDir, { recursive: true })
      cpSync(this.driverDir, tempDir, { recursive: true })
    } catch (err: unknown) {
      return { ok: false, message: `임시 디렉토리 준비 실패: ${(err as Error).message}` }
    }

    const infPath = join(tempDir, 'android_winusb.inf')
    if (!existsSync(infPath)) {
      return { ok: false, message: 'android_winusb.inf 파일이 없습니다.' }
    }

    // PowerShell 단일따옴표 문자열용 이스케이프
    const ps = (s: string) => s.replace(/'/g, "''")

    return new Promise((resolve) => {
      const script = [
        `$p = Start-Process pnputil`,
        `-ArgumentList @('/add-driver', '${ps(infPath)}', '/install')`,
        `-Verb RunAs -Wait -PassThru;`,
        `$p.ExitCode | Out-File '${ps(resultFile)}' -Encoding ascii`,
      ].join(' ')

      const proc = spawn('powershell.exe', ['-NonInteractive', '-Command', script], {
        windowsHide: false,
      })

      proc.on('close', (code) => {
        if (code !== 0) {
          resolve({ ok: false, message: '설치가 취소됐거나 관리자 권한이 거부됐습니다.' })
          return
        }
        try {
          const exitCode = parseInt(readFileSync(resultFile, 'utf8').trim(), 10)
          if (exitCode === 0) {
            resolve({ ok: true, message: '드라이버 설치 완료. 태블릿을 재연결하세요.' })
          } else {
            resolve({ ok: false, message: `pnputil 설치 실패 (오류 코드: ${exitCode})` })
          }
        } catch {
          resolve({ ok: false, message: '설치 완료 여부를 확인할 수 없습니다. 태블릿을 재연결해 보세요.' })
        }
      })

      proc.on('error', (err) => {
        resolve({ ok: false, message: `PowerShell 실행 오류: ${err.message}` })
      })
    })
  }
}
