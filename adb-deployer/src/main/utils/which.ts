// which 유틸 — adb 바이너리 경로 탐색
import { exec } from 'child_process'
import { promisify } from 'util'

const execAsync = promisify(exec)

export async function which(cmd: string): Promise<string | null> {
  const whichCmd = process.platform === 'win32'
    ? `where ${cmd}`
    : `which ${cmd}`
  try {
    const { stdout } = await execAsync(whichCmd)
    return stdout.trim().split('\n')[0].trim() || null
  } catch {
    return null
  }
}
