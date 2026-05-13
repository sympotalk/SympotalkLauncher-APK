// 명령 실행 결과 — 기기별 아코디언
import { useState } from 'react'
import type { DispatchResult } from '../types/Command'

interface Props {
  results: DispatchResult[]
}

export function ResultPanel({ results }: Props) {
  const success = results.filter(r => r.status === 'success').length
  const failure = results.filter(r => r.status !== 'success').length

  return (
    <div className="result-panel">
      {results.length > 0 && (
        <div className="result-panel__summary">
          <span className="text-green">성공: {success}</span>
          <span className="text-red">실패: {failure}</span>
        </div>
      )}
      {results.map(r => (
        <ResultRow key={r.serial} result={r} />
      ))}
    </div>
  )
}

function ResultRow({ result: r }: { result: DispatchResult }) {
  const [open, setOpen] = useState(false)
  const ok = r.status === 'success'

  return (
    <div className={`result-row ${ok ? 'result-row--ok' : 'result-row--fail'}`}>
      <button className="result-row__header" onClick={() => setOpen(v => !v)}>
        <span className="result-row__icon">{ok ? '✓' : '✗'}</span>
        <span className="result-row__serial">{r.serial}</span>
        <span className="result-row__duration">{r.durationMs}ms</span>
        <span className="result-row__toggle">{open ? '▲' : '▼'}</span>
      </button>
      {open && (
        <div className="result-row__body">
          {r.errorMessage && <p className="result-row__error">{r.errorMessage}</p>}
          {r.stdout && <pre className="result-row__pre">{r.stdout}</pre>}
          {r.stderr && <pre className="result-row__pre result-row__pre--err">{r.stderr}</pre>}
        </div>
      )}
    </div>
  )
}
