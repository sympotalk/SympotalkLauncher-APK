// 앱 루트 — 탭 기반 레이아웃
import { useState } from 'react'
import { DeviceListView } from './views/DeviceListView'
import { CommandView }    from './views/CommandView'
import { SetupWizardView } from './views/SetupWizardView'
import { LogView }         from './views/LogView'
import { useDeviceStore }  from './store/deviceStore'

const TABS = [
  { id: 'devices', label: '기기 목록' },
  { id: 'command', label: '명령 실행' },
  { id: 'wizard',  label: '초기 세팅' },
  { id: 'logs',    label: '로그' },
] as const

type TabId = typeof TABS[number]['id']

export function App() {
  const [tab, setTab] = useState<TabId>('devices')
  const selectedCount = useDeviceStore(s => s.selectedSerials.size)

  return (
    <div className="app">
      <header className="app-header">
        <h1 className="app-title">Sympotalk ADB Deployer</h1>
        {selectedCount > 0 && (
          <span className="app-selected-badge">{selectedCount}대 선택됨</span>
        )}
      </header>

      <nav className="tab-nav">
        {TABS.map(t => (
          <button
            key={t.id}
            className={`tab-btn ${tab === t.id ? 'tab-btn--active' : ''}`}
            onClick={() => setTab(t.id)}
          >
            {t.label}
          </button>
        ))}
      </nav>

      <main className="app-main">
        {tab === 'devices' && <DeviceListView />}
        {tab === 'command' && <CommandView />}
        {tab === 'wizard'  && <SetupWizardView />}
        {tab === 'logs'    && <LogView />}
      </main>
    </div>
  )
}
