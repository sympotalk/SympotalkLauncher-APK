// 앱 설정 상태 관리 — APK 경로·동시실행수·timeout
import { create } from 'zustand'
import type { AppSettings } from '../types/Command'

interface SettingsStore extends AppSettings {
  set: (patch: Partial<AppSettings>) => void
}

export const useSettingsStore = create<SettingsStore>((set) => ({
  concurrency: 5,
  timeoutMs: 30_000,
  dpcApkPath: null,
  launcherApkPath: null,
  set: (patch) => set(patch)
}))
