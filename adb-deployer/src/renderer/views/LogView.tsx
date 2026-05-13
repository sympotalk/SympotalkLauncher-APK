// 로그 뷰 — 전체 실행 이력 + 필터 + CSV/TXT export
import { useCallback, useEffect, useState } from 'react'
import type { LogEntry } from '../types/Command'

export function LogView() {
  const [filterStatus, setFilterStatus] = useState<'all' | 'failure'>('all')
  const [logs, setLogs] = useState<LogEntry[]>([])

  const refresh = useCallback(() => {
    window.api.queryLogs().then((entries: unknown) => setLogs(entries as LogEntry[]))
  }, [])

  useEffect(() => { refresh() }, [refresh])

  const filtered = filterStatus === 'failure'
    ? logs.filter(l => l.status !== 'success')
    : logs

  const handleExport = async (format: 'csv' | 'txt') => {
    const filter = filterStatus === 'failure' ? { status: 'failure' } : undefined
    const path: string | null = await window.api.exportLogs(format, filter)
    if (path) alert(`저장 완료: ${path}`)
  }

  return (
    <div className="view">
      <div className="view__toolbar">
        <label className="radio-label">
          <input type="radio" value="all" checked={filterStatus === 'all'}
                 onChange={() => setFilterStatus('all')} />
          전체
        </label>
        <label className="radio-label">
          <input type="radio" value="failure" checked={filterStatus === 'failure'}
                 onChange={() => setFilterStatus('failure')} />
          실패만
        </label>
        <span className="text-muted">{filtered.length}건</span>
        <button className="btn btn-sm" onClick={refresh}>새로고침</button>
        <button className="btn btn-sm" onClick={() => handleExport('csv')}>CSV 내보내기</button>
        <button className="btn btn-sm" onClick={() => handleExport('txt')}>TXT 내보내기</button>
      </div>

      <div className="log-table-wrapper">
        <table className="log-table">
          <thead>
            <tr>
              <th>시각</th>
              <th>Serial</th>
              <th>명령</th>
              <th>결과</th>
              <th>시간</th>
            </tr>
          </thead>
          <tbody>
            {filtered.length === 0 ? (
              <tr><td colSpan={5} className="text-center text-muted">실행 기록이 없습니다.</td></tr>
            ) : (
              filtered.map(e => (
                <tr key={e.id} className={e.status === 'success' ? 'log-row--ok' : 'log-row--fail'}>
                  <td className="log-time">{new Date(e.timestamp).toLocaleTimeString()}</td>
                  <td className="log-serial">{e.serial}</td>
                  <td className="log-cmd" title={e.command}>{truncate(e.command, 40)}</td>
                  <td>
                    <span className={e.status === 'success' ? 'text-green' : 'text-red'}>
                      {e.status === 'success' ? '✓' : '✗'} {e.exitCode}
                    </span>
                    {e.stderr && <span className="log-err" title={e.stderr}> — {truncate(e.stderr, 30)}</span>}
                  </td>
                  <td className="log-dur">{e.durationMs}ms</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  )
}

function truncate(s: string, n: number) {
  return s.length > n ? s.slice(0, n) + '…' : s
}
