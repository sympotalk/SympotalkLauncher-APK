// 기기 1행 — serial, 상태, 배터리, DO/DPC/Launcher 뱃지, 마지막 결과
import type { Device } from '../types/Device'
import { DeviceStatusBadge } from './DeviceStatusBadge'

interface Props {
  device: Device
  selected: boolean
  onToggle: () => void
}

export function DeviceCard({ device: d, selected, onToggle }: Props) {
  const canSelect = d.status === 'device'

  return (
    <div className={`device-card ${selected ? 'device-card--selected' : ''}`}
         onClick={() => canSelect && onToggle()}>
      <input
        type="checkbox"
        checked={selected}
        disabled={!canSelect}
        onChange={onToggle}
        onClick={e => e.stopPropagation()}
        className="device-card__check"
      />
      <div className="device-card__main">
        <div className="device-card__row1">
          <span className="device-card__serial">{d.serial}</span>
          <DeviceStatusBadge status={d.status} />
          {d.modelName && <span className="device-card__model">{d.modelName}</span>}
          {d.androidVersion && <span className="device-card__version">A{d.androidVersion}</span>}
          {d.batteryLevel !== undefined && d.batteryLevel >= 0 && (
            <span className="device-card__battery">🔋{d.batteryLevel}%</span>
          )}
        </div>
        {d.status === 'device' && (
          <div className="device-card__row2">
            <span className={d.isDeviceOwner ? 'tag tag-ok' : 'tag tag-no'}>
              DO: {d.isDeviceOwner ? '✓' : '✗'}
            </span>
            <span className={d.dpcInstalled ? 'tag tag-ok' : 'tag tag-no'}>
              DPC: {d.dpcInstalled ? '✓' : '✗'}
            </span>
            <span className={d.launcherInstalled ? 'tag tag-ok' : 'tag tag-no'}>
              Launcher: {d.launcherInstalled ? '✓' : '✗'}
            </span>
            {d.lastCommandResult && (
              <span className={d.lastCommandResult.status === 'success' ? 'tag tag-ok' : 'tag tag-no'}>
                {d.lastCommandResult.status === 'success' ? '✓ 성공' : '✗ 실패'}
              </span>
            )}
          </div>
        )}
        {d.status === 'unauthorized' && (
          <p className="device-card__hint">태블릿 화면에서 "USB 디버깅 허용"을 누르세요.</p>
        )}
      </div>
    </div>
  )
}
