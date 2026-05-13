// 기기 목록 뷰 — 폴링 시작, 전체 선택, DeviceCard 목록
import { useEffect, useState } from 'react'
import { useDeviceStore } from '../store/deviceStore'
import { DeviceCard } from '../components/DeviceCard'
import type { Device } from '../types/Device'

export function DeviceListView() {
  const { devices, selectedSerials, setDevices, toggleSelect, selectAll, deselectAll } =
    useDeviceStore()
  const [polling, setPolling] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    // 폴러 시작
    window.api.startPoller().then((res: { ok: boolean; error?: string }) => {
      if (res.ok) {
        setPolling(true)
      } else {
        setError(res.error ?? 'ADB 초기화 실패')
      }
    })

    // 기기 목록 업데이트 구독
    const unsub = window.api.onDeviceListUpdated((list: unknown) => {
      setDevices(list as Device[])
    })

    return () => {
      unsub?.()
      window.api.stopPoller()
    }
  }, [setDevices])

  const connectedCount = devices.filter(d => d.status === 'device').length

  return (
    <div className="view">
      <div className="view__toolbar">
        <label className="checkbox-label">
          <input
            type="checkbox"
            checked={selectedSerials.size > 0 && selectedSerials.size === connectedCount}
            onChange={e => e.target.checked ? selectAll() : deselectAll()}
          />
          전체 선택
        </label>
        <span className="text-muted">감지된 기기: {devices.length}대</span>
        <button className="btn btn-sm" onClick={() => window.api.startPoller()}>
          새로고침
        </button>
        <span className={`poll-indicator ${polling ? 'poll-indicator--active' : ''}`}>
          {polling ? '● 폴링 중' : '○ 중지'}
        </span>
        {error && <span className="text-red">{error}</span>}
      </div>

      <div className="device-list">
        {devices.length === 0 ? (
          <div className="empty-state">
            USB로 태블릿을 연결하면 여기에 표시됩니다.
          </div>
        ) : (
          devices.map(d => (
            <DeviceCard
              key={d.serial}
              device={d}
              selected={selectedSerials.has(d.serial)}
              onToggle={() => toggleSelect(d.serial)}
            />
          ))
        )}
      </div>
    </div>
  )
}
