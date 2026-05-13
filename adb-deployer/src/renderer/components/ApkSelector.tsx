// APK 파일 선택 + 경로 표시
import { useSettingsStore } from '../store/settingsStore'

interface Props {
  type: 'dpc' | 'launcher'
  label: string
}

export function ApkSelector({ type, label }: Props) {
  const path = useSettingsStore(s => type === 'dpc' ? s.dpcApkPath : s.launcherApkPath)
  const setSettings = useSettingsStore(s => s.set)

  const handleSelect = async () => {
    const selected = await window.api.selectApk(type)
    if (selected) {
      setSettings(type === 'dpc' ? { dpcApkPath: selected } : { launcherApkPath: selected })
    }
  }

  const filename = path ? path.split(/[\\/]/).pop() : null

  return (
    <div className="apk-selector">
      <span className="apk-selector__label">{label}</span>
      <span className="apk-selector__path" title={path ?? undefined}>
        {filename ?? '선택되지 않음'}
      </span>
      <button className="btn btn-sm" onClick={handleSelect}>변경</button>
    </div>
  )
}
