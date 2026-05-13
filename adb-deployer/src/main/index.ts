// Main process 진입점 — BrowserWindow + IPC 핸들러 + 서비스 초기화
// 참고: createRequire로 electron을 IIFE 안에서 지연 로드 (모듈 로드 타임 호이스팅 방지)
import { createRequire } from 'node:module'
import { join } from 'path'
import { AdbEngine } from './adb/AdbEngine'
import { DevicePoller } from './adb/DevicePoller'
import { CommandExecutor } from './adb/CommandExecutor'
import { DeviceInspector } from './adb/DeviceInspector'
import { ApkManager } from './apk/ApkManager'
import { LogStore } from './log/LogStore'
import { LogExporter } from './log/LogExporter'
import { CommandGuard } from './security/CommandGuard'
import { IPC } from '../renderer/types/ipc'
import type { Device } from '../renderer/types/Device'
import Store from 'electron-store'

// rollup이 정적 import로 호이스팅하지 못하도록 createRequire로 electron 지연 로드
const _require = createRequire(import.meta.url)

// 서비스 인스턴스 (electron 의존 없음)
const engine    = new AdbEngine()
const poller    = new DevicePoller(engine)
const executor  = new CommandExecutor(engine)
const inspector = new DeviceInspector(engine)
const apkManager = new ApkManager()
const logStore   = new LogStore()
const exporter   = new LogExporter()
const guard      = new CommandGuard()

const settingsStore = new Store({
  defaults: { concurrency: 5, timeoutMs: 30_000 }
})

let cancelController: AbortController | null = null
let deviceList: Device[] = []

// eslint-disable-next-line @typescript-eslint/no-floating-promises
;(async () => {
  // Electron이 모듈 패치(require 인터셉터)를 설치 완료할 때까지 한 틱 대기
  await new Promise<void>(resolve => setImmediate(resolve))

  // eslint-disable-next-line @typescript-eslint/no-require-imports
  const { app, BrowserWindow, ipcMain, dialog } = _require('electron') as typeof import('electron')

  // 서비스에 Electron API 주입 (의존성 주입 — 모듈 로드 타임에는 electron 미사용)
  logStore.setApp(app)
  apkManager.setDialog(dialog)
  exporter.setDialog(dialog)

  // ─── 윈도우 생성 ─────────────────────────────────────────

  function createWindow(): void {
    const win = new BrowserWindow({
      width: 1280,
      height: 800,
      minWidth: 900,
      minHeight: 600,
      title: 'Sympotalk ADB Deployer',
      webPreferences: {
        preload: join(__dirname, '../preload/index.js'),
        contextIsolation: true,
        nodeIntegration: false
      }
    })

    if (process.env.NODE_ENV === 'development' && process.env['ELECTRON_RENDERER_URL']) {
      win.loadURL(process.env['ELECTRON_RENDERER_URL'])
    } else {
      win.loadFile(join(__dirname, '../renderer/index.html'))
    }

    poller.on('device-list-updated', async (devices: Array<Pick<Device, 'serial' | 'status'>>) => {
      const enriched: Device[] = await Promise.all(
        devices.map(async (d) => {
          const existing = deviceList.find(e => e.serial === d.serial)
          if (existing && existing.status === d.status) return existing
          if (d.status !== 'device') return d as Device
          try {
            const info = await inspector.inspect(d.serial)
            return { ...d, ...info } as Device
          } catch {
            return d as Device
          }
        })
      )
      deviceList = enriched
      win.webContents.send(IPC.DEVICE_LIST_UPDATED, enriched)
    })
  }

  // ─── IPC 핸들러 ───────────────────────────────────────────

  ipcMain.handle(IPC.POLLER_START, async () => {
    try {
      await engine.init()
      poller.start()
      return { ok: true }
    } catch (err: unknown) {
      return { ok: false, error: (err as Error).message }
    }
  })

  ipcMain.handle(IPC.POLLER_STOP, () => {
    poller.stop()
  })

  ipcMain.handle(IPC.DEVICE_INSPECT, async (_, serial: string) => {
    return inspector.inspect(serial)
  })

  ipcMain.handle(IPC.EXECUTE_COMMAND, async (_, serial: string, args: string[]) => {
    const blocked = guard.checkCommand(args)
    if (blocked) return { exitCode: -1, stdout: '', stderr: blocked, durationMs: 0, status: 'failure' }

    const result = await executor.run(serial, args, settingsStore.get('timeoutMs') as number)
    const status = result.exitCode === 0 ? 'success' : 'failure'
    const errorMessage = result.exitCode !== 0 ? guard.translateError(result.stderr) : undefined

    logStore.append({
      timestamp: new Date().toISOString(),
      serial,
      command: args.join(' '),
      ...result,
      status
    })

    return { ...result, status, errorMessage }
  })

  ipcMain.handle(IPC.EXECUTE_BATCH, async (event, serials: string[], args: string[], timeoutMs: number) => {
    const blocked = guard.checkCommand(args)
    if (blocked) return serials.map(serial => ({ serial, exitCode: -1, stdout: '', stderr: blocked, durationMs: 0, status: 'failure' }))

    cancelController = new AbortController()
    const concurrency: number = settingsStore.get('concurrency') as number

    let active = 0
    const queue = [...serials]
    const results: unknown[] = []
    const waiters: Array<() => void> = []

    const acquire = () => new Promise<void>(resolve => {
      if (active < concurrency) { active++; resolve(); return }
      waiters.push(() => { active++; resolve() })
    })
    const release = () => {
      active--
      const next = waiters.shift()
      if (next) next()
    }

    const remaining = { count: serials.length }

    const promises = queue.map(async (serial) => {
      await acquire()
      try {
        const result = await executor.run(serial, args, timeoutMs, cancelController?.signal)
        const status = result.exitCode === 0 ? 'success' : 'failure'
        const errorMessage = result.exitCode !== 0 ? guard.translateError(result.stderr) : undefined
        const dispatched = { serial, ...result, status, errorMessage }

        logStore.append({
          timestamp: new Date().toISOString(),
          serial,
          command: args.join(' '),
          exitCode: result.exitCode,
          stdout:   result.stdout,
          stderr:   result.stderr,
          durationMs: result.durationMs,
          status
        })

        remaining.count--
        event.sender.send(IPC.EXECUTION_PROGRESS, dispatched, remaining.count)
        results.push(dispatched)
        return dispatched
      } finally {
        release()
      }
    })

    const all = await Promise.all(promises)
    event.sender.send(IPC.EXECUTION_COMPLETE, all)
    return all
  })

  ipcMain.handle(IPC.EXECUTE_CANCEL, () => {
    cancelController?.abort()
    cancelController = null
  })

  ipcMain.handle(IPC.APK_SELECT, async (_, type: 'dpc' | 'launcher') => {
    return apkManager.selectApk(type)
  })

  ipcMain.handle(IPC.APK_GET_PATHS, () => {
    return apkManager.getPaths()
  })

  ipcMain.handle(IPC.SETTINGS_GET, () => ({
    concurrency: settingsStore.get('concurrency'),
    timeoutMs: settingsStore.get('timeoutMs'),
    ...apkManager.getPaths()
  }))

  ipcMain.handle(IPC.SETTINGS_SET, (_, patch: Record<string, unknown>) => {
    if (patch.concurrency !== undefined) settingsStore.set('concurrency', patch.concurrency)
    if (patch.timeoutMs !== undefined) settingsStore.set('timeoutMs', patch.timeoutMs)
  })

  ipcMain.handle(IPC.LOG_QUERY, (_, filter?: { serial?: string; status?: string }) => {
    return logStore.query(filter as Parameters<typeof logStore.query>[0])
  })

  ipcMain.handle(IPC.LOG_EXPORT, async (_, format: 'csv' | 'txt', filter?: { status?: string }) => {
    const entries = logStore.query(filter as Parameters<typeof logStore.query>[0])
    return format === 'csv'
      ? exporter.exportCsv(entries)
      : exporter.exportTxt(entries)
  })

  ipcMain.handle(IPC.GUARD_CHECK, (_, args: string[]) => {
    return guard.checkCommand(args)
  })

  ipcMain.handle(IPC.GUARD_DO_PREREQ, async (_, serial: string) => {
    return guard.checkDoPrerequisites(serial, inspector)
  })

  // ─── 앱 수명주기 ─────────────────────────────────────────

  app.whenReady().then(() => {
    createWindow()
    app.on('activate', () => {
      if (BrowserWindow.getAllWindows().length === 0) createWindow()
    })
  })

  app.on('window-all-closed', () => {
    poller.stop()
    logStore.close()
    if (process.platform !== 'darwin') app.quit()
  })
})()
