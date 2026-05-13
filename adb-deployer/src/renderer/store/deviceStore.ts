// 기기 목록 + 선택 상태 관리
import { create } from 'zustand'
import type { Device } from '../types/Device'

interface DeviceStore {
  devices: Device[]
  selectedSerials: Set<string>
  setDevices: (devices: Device[]) => void
  updateDevice: (serial: string, patch: Partial<Device>) => void
  toggleSelect: (serial: string) => void
  selectAll: () => void
  deselectAll: () => void
  getSelected: () => Device[]
}

export const useDeviceStore = create<DeviceStore>((set, get) => ({
  devices: [],
  selectedSerials: new Set(),

  setDevices: (devices) => set({ devices }),

  updateDevice: (serial, patch) =>
    set(s => ({
      devices: s.devices.map(d => d.serial === serial ? { ...d, ...patch } : d)
    })),

  toggleSelect: (serial) =>
    set(s => {
      const next = new Set(s.selectedSerials)
      next.has(serial) ? next.delete(serial) : next.add(serial)
      return { selectedSerials: next }
    }),

  selectAll: () =>
    set(s => ({
      selectedSerials: new Set(s.devices.filter(d => d.status === 'device').map(d => d.serial))
    })),

  deselectAll: () => set({ selectedSerials: new Set() }),

  getSelected: () => {
    const { devices, selectedSerials } = get()
    return devices.filter(d => selectedSerials.has(d.serial))
  }
}))
