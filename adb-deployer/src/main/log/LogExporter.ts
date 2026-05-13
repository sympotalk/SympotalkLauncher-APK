// 로그 CSV/TXT export
import type { Dialog } from 'electron'
import { writeFileSync } from 'fs'
import type { LogEntry } from '../../renderer/types/Command'

export class LogExporter {
  private _dialog: Dialog | null = null

  setDialog(dialog: Dialog): void {
    this._dialog = dialog
  }

  async exportCsv(entries: LogEntry[]): Promise<string | null> {
    if (!this._dialog) throw new Error('LogExporter.setDialog() must be called before export')
    const result = await this._dialog.showSaveDialog({
      title: '로그 CSV 저장',
      defaultPath: `adb-log-${new Date().toISOString().slice(0, 10)}.csv`,
      filters: [{ name: 'CSV', extensions: ['csv'] }]
    })
    if (result.canceled || !result.filePath) return null

    const header = 'timestamp,serial,command,exit_code,status,duration_ms,stdout,stderr\n'
    const rows = entries.map(e =>
      [e.timestamp, e.serial, e.command, e.exitCode, e.status,
       e.durationMs, csvEscape(e.stdout), csvEscape(e.stderr)].join(',')
    ).join('\n')
    writeFileSync(result.filePath, header + rows, 'utf-8')
    return result.filePath
  }

  async exportTxt(entries: LogEntry[]): Promise<string | null> {
    if (!this._dialog) throw new Error('LogExporter.setDialog() must be called before export')
    const result = await this._dialog.showSaveDialog({
      title: '로그 TXT 저장',
      defaultPath: `adb-log-${new Date().toISOString().slice(0, 10)}.txt`,
      filters: [{ name: 'Text', extensions: ['txt'] }]
    })
    if (result.canceled || !result.filePath) return null

    const lines = entries.map(e =>
      `[${e.timestamp}] ${e.serial} | ${e.status.toUpperCase()} | ${e.command}\n  stdout: ${e.stdout}\n  stderr: ${e.stderr}`
    ).join('\n\n')
    writeFileSync(result.filePath, lines, 'utf-8')
    return result.filePath
  }
}

function csvEscape(s: string): string {
  if (s.includes(',') || s.includes('"') || s.includes('\n')) {
    return `"${s.replace(/"/g, '""')}"`
  }
  return s
}
