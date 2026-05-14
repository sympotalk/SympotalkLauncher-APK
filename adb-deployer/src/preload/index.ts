// contextBridge — 렌더러에 안전한 API 노출 (shell injection 방지)
import { contextBridge, ipcRenderer } from 'electron'
import { IPC } from '../renderer/types/ipc'

contextBridge.exposeInMainWorld('api', {
  // ─── 기기 ───────────────────────────────────────────────
  onDeviceListUpdated: (cb: (devices: unknown[]) => void) => {
    ipcRenderer.on(IPC.DEVICE_LIST_UPDATED, (_, data) => cb(data))
    return () => ipcRenderer.removeAllListeners(IPC.DEVICE_LIST_UPDATED)
  },
  onExecutionProgress: (cb: (result: unknown, remaining: number) => void) => {
    ipcRenderer.on(IPC.EXECUTION_PROGRESS, (_, result, remaining) => cb(result, remaining))
    return () => ipcRenderer.removeAllListeners(IPC.EXECUTION_PROGRESS)
  },
  onExecutionComplete: (cb: (results: unknown[]) => void) => {
    ipcRenderer.on(IPC.EXECUTION_COMPLETE, (_, results) => cb(results))
    return () => ipcRenderer.removeAllListeners(IPC.EXECUTION_COMPLETE)
  },

  inspectDevice: (serial: string) =>
    ipcRenderer.invoke(IPC.DEVICE_INSPECT, serial),

  startPoller: () => ipcRenderer.invoke(IPC.POLLER_START),
  stopPoller:  () => ipcRenderer.invoke(IPC.POLLER_STOP),

  // ─── 명령 실행 ─────────────────────────────────────────
  // args 배열로만 전달 — shell injection 방지
  executeCommand: (serial: string, args: string[]) =>
    ipcRenderer.invoke(IPC.EXECUTE_COMMAND, serial, args),

  executeBatch: (serials: string[], args: string[], timeoutMs: number) =>
    ipcRenderer.invoke(IPC.EXECUTE_BATCH, serials, args, timeoutMs),

  cancelExecution: () => ipcRenderer.invoke(IPC.EXECUTE_CANCEL),

  // ─── APK ───────────────────────────────────────────────
  selectApk: (type: 'dpc' | 'launcher') =>
    ipcRenderer.invoke(IPC.APK_SELECT, type),

  getApkPaths: () =>
    ipcRenderer.invoke(IPC.APK_GET_PATHS),

  // ─── 설정 ──────────────────────────────────────────────
  getSettings: () =>
    ipcRenderer.invoke(IPC.SETTINGS_GET),

  setSettings: (patch: Record<string, unknown>) =>
    ipcRenderer.invoke(IPC.SETTINGS_SET, patch),

  // ─── 로그 ──────────────────────────────────────────────
  queryLogs: (filter?: { serial?: string; status?: string }) =>
    ipcRenderer.invoke(IPC.LOG_QUERY, filter),

  exportLogs: (format: 'csv' | 'txt', filter?: { status?: string }) =>
    ipcRenderer.invoke(IPC.LOG_EXPORT, format, filter),

  // ─── 보안 ──────────────────────────────────────────────
  checkCommand: (args: string[]) =>
    ipcRenderer.invoke(IPC.GUARD_CHECK, args),

  checkDoPrerequisites: (serial: string) =>
    ipcRenderer.invoke(IPC.GUARD_DO_PREREQ, serial),

  // ─── USB 드라이버 ──────────────────────────────────────
  checkDriver: () =>
    ipcRenderer.invoke(IPC.DRIVER_CHECK),

  installDriver: () =>
    ipcRenderer.invoke(IPC.DRIVER_INSTALL),
})
