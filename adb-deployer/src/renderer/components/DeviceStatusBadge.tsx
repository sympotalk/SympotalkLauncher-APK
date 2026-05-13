// 기기 연결 상태 색상 뱃지
import type { DeviceStatus } from '../types/Device'

const STATUS_CONFIG: Record<DeviceStatus, { label: string; cls: string }> = {
  device:           { label: '연결됨',     cls: 'badge badge-green'  },
  unauthorized:     { label: '승인 필요',  cls: 'badge badge-yellow' },
  offline:          { label: '오프라인',   cls: 'badge badge-gray'   },
  'no permissions': { label: '권한 없음',  cls: 'badge badge-yellow' },
  disconnected:     { label: '해제됨',     cls: 'badge badge-red'    },
}

interface Props { status: DeviceStatus }

export function DeviceStatusBadge({ status }: Props) {
  const cfg = STATUS_CONFIG[status] ?? { label: status, cls: 'badge badge-gray' }
  return <span className={cfg.cls}>{cfg.label}</span>
}
