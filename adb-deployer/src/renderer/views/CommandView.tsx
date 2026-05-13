// 명령 실행 뷰 — 프리셋 버튼 + 입력창 + 병렬 실행 + 결과 패널
import { useEffect, useRef, useState } from 'react'
import { useDeviceStore } from '../store/deviceStore'
import { useExecutionStore } from '../store/executionStore'
import { useSettingsStore } from '../store/settingsStore'
import { ResultPanel } from '../components/ResultPanel'
import { ConfirmModal } from '../components/ConfirmModal'
import type { CommandPreset, DispatchResult } from '../types/Command'

const PRESET_LIST: CommandPreset[] = [
  { id: 'refresh',       label: 'ADB 새로고침', description: 'ADB 서버 재시작',     args: ['kill-server'] },
  { id: 'dpc-install',   label: 'DPC 설치',     description: 'DPC APK 설치',         args: [],              requiresApkPath: 'dpc' },
  { id: 'launcher-install', label: 'Launcher 설치', description: 'Launcher APK 설치', args: [],            requiresApkPath: 'launcher' },
  { id: 'device-owner-register', label: 'DO 등록', description: 'Device Owner 등록',
    args: ['shell', 'dpm', 'set-device-owner', 'com.sympotalk.dpc/.DpcAdminReceiver'],
    requiresConfirm: true },
  { id: 'device-owner-check', label: 'DO 확인',  description: 'Device Owner 확인',   args: ['shell', 'dpm', 'get-active-admins'] },
  { id: 'launcher-start', label: '런처 실행',    description: 'Launcher 강제 시작',
    args: ['shell', 'am', 'start', '-n', 'com.sympotalk.launcher/.MainActivity'] },
  { id: 'launcher-force-stop', label: '강제 종료', description: 'Launcher 강제 종료',
    args: ['shell', 'am', 'force-stop', 'com.sympotalk.launcher'],
    requiresConfirm: true, dangerLevel: 'warn' },
  { id: 'reboot',        label: '재부팅',        description: '태블릿 재부팅',        args: ['reboot'],      requiresConfirm: true, dangerLevel: 'warn' },
  { id: 'device-info',   label: '기기 정보',     description: '모델·버전·배터리 조회', args: ['shell', 'getprop', 'ro.product.model'] },
]

export function CommandView() {
  const [targetMode, setTargetMode] = useState<'selected' | 'all'>('selected')
  const [inputArgs, setInputArgs] = useState('')
  const [confirm, setConfirm] = useState<{ preset: CommandPreset } | null>(null)
  const [history, setHistory] = useState<string[]>([])
  const inputRef = useRef<HTMLInputElement>(null)

  const { devices, selectedSerials } = useDeviceStore()
  const { isRunning, total, completed, results, setRunning, addResult } = useExecutionStore()
  const { concurrency, timeoutMs, dpcApkPath, launcherApkPath } = useSettingsStore()

  useEffect(() => {
    const unsub1 = window.api.onExecutionProgress((result: unknown, _remaining: number) => {
      addResult(result as DispatchResult)
    })
    const unsub2 = window.api.onExecutionComplete((_all: unknown) => {
      setRunning(false)
    })
    return () => { unsub1?.(); unsub2?.() }
  }, [addResult, setRunning])

  const getTargetSerials = () =>
    targetMode === 'all'
      ? devices.filter(d => d.status === 'device').map(d => d.serial)
      : [...selectedSerials]

  const runArgs = async (args: string[], presetId?: string) => {
    const serials = getTargetSerials()
    if (serials.length === 0) return alert('실행할 기기를 선택하세요.')

    const blocked: string | null = await window.api.checkCommand(args)
    if (blocked) return alert(blocked)

    setRunning(true, serials.length)
    await window.api.executeBatch(serials, args, timeoutMs)
    void presetId
  }

  const handleCustomRun = () => {
    const parts = inputArgs.trim().split(/\s+/).filter(Boolean)
    if (!parts.length) return
    setHistory(h => [inputArgs, ...h].slice(0, 20))
    runArgs(parts)
  }

  const handlePreset = async (preset: CommandPreset) => {
    let args = [...preset.args]

    if (preset.requiresApkPath) {
      const path = preset.requiresApkPath === 'dpc' ? dpcApkPath : launcherApkPath
      if (!path) return alert(`${preset.requiresApkPath === 'dpc' ? 'DPC' : 'Launcher'} APK를 먼저 선택하세요.`)
      args = ['install', '-r', path]
    }

    if (preset.id === 'device-owner-register') {
      const serials = getTargetSerials()
      for (const serial of serials) {
        const check = await window.api.checkDoPrerequisites(serial)
        if (!check.passed) {
          const failed = check.items.filter((i: { passed: boolean }) => !i.passed).map((i: { label: string }) => i.label).join(', ')
          return alert(`${serial}: 사전 조건 미충족 — ${failed}`)
        }
      }
    }

    if (preset.requiresConfirm) {
      setConfirm({ preset: { ...preset, args } })
      return
    }

    runArgs(args, preset.id)
  }

  const progress = total > 0 ? Math.round((completed / total) * 100) : 0

  return (
    <div className="view">
      {/* 대상 선택 */}
      <div className="view__toolbar">
        <label className="radio-label">
          <input type="radio" value="selected" checked={targetMode === 'selected'}
                 onChange={() => setTargetMode('selected')} />
          선택된 기기 ({selectedSerials.size}대)
        </label>
        <label className="radio-label">
          <input type="radio" value="all" checked={targetMode === 'all'}
                 onChange={() => setTargetMode('all')} />
          전체 기기 ({devices.filter(d => d.status === 'device').length}대)
        </label>
      </div>

      {/* 명령 입력 */}
      <div className="cmd-input-row">
        <input
          ref={inputRef}
          className="cmd-input"
          placeholder="adb 명령 인수 입력 (예: shell getprop ro.product.model)"
          value={inputArgs}
          onChange={e => setInputArgs(e.target.value)}
          onKeyDown={e => e.key === 'Enter' && handleCustomRun()}
          list="cmd-history"
        />
        <datalist id="cmd-history">
          {history.map((h, i) => <option key={i} value={h} />)}
        </datalist>
        <button className="btn btn-primary" onClick={handleCustomRun} disabled={isRunning}>
          실행 ▶
        </button>
        {isRunning && (
          <button className="btn btn-secondary" onClick={() => window.api.cancelExecution()}>
            중단
          </button>
        )}
      </div>

      {/* 동시 실행 + timeout */}
      <div className="settings-row">
        <label>동시 실행: <strong>{concurrency}대</strong></label>
        <label>timeout: <strong>{timeoutMs / 1000}s</strong></label>
      </div>

      {/* 프리셋 버튼 */}
      <div className="preset-grid">
        {PRESET_LIST.map(p => (
          <button
            key={p.id}
            className={`btn btn-preset ${p.dangerLevel === 'warn' ? 'btn-preset--warn' : ''}`}
            onClick={() => handlePreset(p)}
            disabled={isRunning}
            title={p.description}
          >
            {p.label}
          </button>
        ))}
      </div>

      {/* 진행률 */}
      {isRunning && (
        <div className="progress-bar">
          <div className="progress-bar__fill" style={{ width: `${progress}%` }} />
          <span className="progress-bar__text">{completed}/{total}</span>
        </div>
      )}

      {/* 결과 */}
      <ResultPanel results={results} />

      {/* 확인 모달 */}
      {confirm && (
        <ConfirmModal
          title={`${confirm.preset.label} 실행`}
          message={`선택된 ${getTargetSerials().length}대에 "${confirm.preset.label}"을 실행합니다.`}
          onConfirm={() => {
            runArgs(confirm.preset.args, confirm.preset.id)
            setConfirm(null)
          }}
          onCancel={() => setConfirm(null)}
        />
      )}
    </div>
  )
}
