// 초기 세팅 마법사 — DPC 설치 → DO 등록 → Launcher 설치 3단계
import { useDeviceStore } from '../store/deviceStore'
import { useSettingsStore } from '../store/settingsStore'
import { useWizardStore } from '../store/wizardStore'
import { ApkSelector } from '../components/ApkSelector'
import { ResultPanel } from '../components/ResultPanel'
import type { DispatchResult, PrerequisiteCheckResult } from '../types/Command'

const STEPS = ['1. DPC 설치', '2. DO 등록', '3. Launcher 설치'] as const
type StepIndex = 0 | 1 | 2

export function SetupWizardView() {
  const { step, running, results, prereqChecks, setStep, setRunning, setResults, setPrereqChecks } = useWizardStore()

  const { devices, selectedSerials } = useDeviceStore()
  const { dpcApkPath, launcherApkPath, timeoutMs } = useSettingsStore()

  const targetSerials = devices
    .filter(d => d.status === 'device' && selectedSerials.has(d.serial))
    .map(d => d.serial)

  const runStep = async () => {
    if (targetSerials.length === 0) return alert('기기 목록에서 대상 기기를 먼저 선택하세요.')

    // DO 등록 전 사전 조건 체크
    if (step === 1) {
      const checks: Record<string, PrerequisiteCheckResult> = {}
      for (const serial of targetSerials) {
        checks[serial] = await window.api.checkDoPrerequisites(serial)
      }
      setPrereqChecks(checks)
      const failed = Object.entries(checks).filter(([, c]) => !c.passed)
      if (failed.length > 0) {
        const msg = failed.map(([s, c]) =>
          `${s}: ${c.items.filter(i => !i.passed).map(i => i.label).join(', ')}`
        ).join('\n')
        return alert(`사전 조건 미충족 기기:\n${msg}`)
      }
    }

    const args = getStepArgs()
    if (!args) return

    setRunning(true)
    setResults([])

    const all: DispatchResult[] = await window.api.executeBatch(targetSerials, args, timeoutMs)

    // DO 등록 성공 후 PACKAGE_USAGE_STATS 권한 자동 부여 (HealthWatchdog 필수)
    if (step === 1 && all.every(r => r.status === 'success')) {
      const permArgs = ['shell', 'appops', 'set', 'com.sympotalk.dpc', 'PACKAGE_USAGE_STATS', 'allow']
      const permResults = await window.api.executeBatch(targetSerials, permArgs, timeoutMs)
      setResults([...all, ...permResults])
      setRunning(false)
      if (permResults.every(r => r.status === 'success')) {
        setTimeout(() => setStep(2 as StepIndex), 500)
      }
      return
    }

    setResults(all)
    setRunning(false)

    // 전부 성공하면 다음 단계로
    if (all.every(r => r.status === 'success') && step < 2) {
      setTimeout(() => setStep((step + 1) as StepIndex), 500)
    }
  }

  const getStepArgs = (): string[] | null => {
    if (step === 0) {
      if (!dpcApkPath) return (alert('DPC APK를 선택하세요.'), null)
      return ['install', '-r', dpcApkPath]
    }
    if (step === 1) {
      return ['shell', 'dpm', 'set-device-owner', 'com.sympotalk.dpc/.DpcAdminReceiver']
    }
    if (!launcherApkPath) return (alert('Launcher APK를 선택하세요.'), null)
    return ['install', '-r', launcherApkPath]
  }

  const stepLabel = ['DPC 설치 실행 →', 'Device Owner 등록 →', 'Launcher 설치 실행 →'][step]

  return (
    <div className="view">
      {/* 단계 표시 */}
      <div className="wizard-steps">
        {STEPS.map((label, i) => (
          <div key={i} className={`wizard-step ${i === step ? 'wizard-step--active' : i < step ? 'wizard-step--done' : ''}`}>
            {label}
          </div>
        ))}
      </div>

      {/* APK 선택 */}
      {step === 0 && <ApkSelector type="dpc"      label="DPC APK" />}
      {step === 2 && <ApkSelector type="launcher" label="Launcher APK" />}

      {/* DO 등록 단계 사전 조건 */}
      {step === 1 && (
        <div className="prereq-box">
          <h3>사전 조건 확인</h3>
          {targetSerials.length === 0
            ? <p className="text-muted">기기를 선택하면 자동으로 확인합니다.</p>
            : Object.entries(prereqChecks).map(([serial, check]) => (
              <div key={serial} className="prereq-device">
                <strong>{serial}</strong>
                {check.items.map(item => (
                  <div key={item.label} className={item.passed ? 'prereq-item prereq-item--ok' : 'prereq-item prereq-item--fail'}>
                    {item.passed ? '✓' : '✗'} {item.label}
                  </div>
                ))}
              </div>
            ))
          }
        </div>
      )}

      <p className="wizard-info">
        대상: {targetSerials.length}대 (기기 탭에서 선택)
      </p>

      <div className="wizard-actions">
        {step > 0 && (
          <button className="btn btn-secondary" onClick={() => setStep((step - 1) as StepIndex)} disabled={running}>
            ← 이전
          </button>
        )}
        <button className="btn btn-primary" onClick={runStep} disabled={running || targetSerials.length === 0}>
          {running ? '실행 중...' : stepLabel}
        </button>
      </div>

      <ResultPanel results={results} />
    </div>
  )
}
