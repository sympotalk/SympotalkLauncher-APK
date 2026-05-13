// APK 경로 선택 + electron-store 영속화
import type { Dialog } from 'electron'
import { existsSync } from 'fs'
import Store from 'electron-store'

interface StoreSchema {
  dpcApkPath: string | null
  launcherApkPath: string | null
}

const store = new Store<StoreSchema>({
  defaults: { dpcApkPath: null, launcherApkPath: null }
})

export class ApkManager {
  private _dialog: Dialog | null = null

  setDialog(dialog: Dialog): void {
    this._dialog = dialog
  }

  // APK 파일 선택 다이얼로그 → 경로 저장 후 반환
  async selectApk(type: 'dpc' | 'launcher'): Promise<string | null> {
    if (!this._dialog) throw new Error('ApkManager.setDialog() must be called before selectApk')
    const result = await this._dialog.showOpenDialog({
      title: type === 'dpc' ? 'DPC APK 선택' : 'Launcher APK 선택',
      filters: [{ name: 'APK 파일', extensions: ['apk'] }],
      properties: ['openFile']
    })
    if (result.canceled || result.filePaths.length === 0) return null
    const path = result.filePaths[0]
    store.set(type === 'dpc' ? 'dpcApkPath' : 'launcherApkPath', path)
    return path
  }

  getPaths(): { dpcApkPath: string | null; launcherApkPath: string | null } {
    return {
      dpcApkPath: store.get('dpcApkPath'),
      launcherApkPath: store.get('launcherApkPath')
    }
  }

  // 저장된 경로의 파일이 실제로 존재하는지 검증
  validatePaths(): { dpcValid: boolean; launcherValid: boolean } {
    const { dpcApkPath, launcherApkPath } = this.getPaths()
    return {
      dpcValid: !!dpcApkPath && existsSync(dpcApkPath),
      launcherValid: !!launcherApkPath && existsSync(launcherApkPath)
    }
  }
}
