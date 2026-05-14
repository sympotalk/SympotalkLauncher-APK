// 기기 상세 정보 병렬 조회 — 모델명·배터리·DO·패키지 상태
import type { AdbEngine } from './AdbEngine'
import type { Device } from '../../renderer/types/Device'

export class DeviceInspector {
  constructor(private engine: AdbEngine) {}

  // 6개 정보를 병렬 조회하여 Device 완성
  async inspect(serial: string): Promise<Partial<Device>> {
    const s = serial
    const [model, version, battery, isDO, dpcOk, launcherOk] = await Promise.all([
      this.getModelName(s),
      this.getAndroidVersion(s),
      this.getBatteryLevel(s),
      this.isDeviceOwner(s),
      this.isPackageInstalled(s, 'com.sympotalk.dpc'),
      this.isPackageInstalled(s, 'com.sympotalk.launcher')
    ])
    return {
      modelName: model,
      androidVersion: version,
      batteryLevel: battery,
      isDeviceOwner: isDO,
      dpcInstalled: dpcOk,
      launcherInstalled: launcherOk
    }
  }

  async getModelName(serial: string): Promise<string> {
    const r = await this.engine.exec(['-s', serial, 'shell', 'getprop', 'ro.product.model'])
    return r.stdout || '알 수 없음'
  }

  async getAndroidVersion(serial: string): Promise<string> {
    const r = await this.engine.exec(['-s', serial, 'shell', 'getprop', 'ro.build.version.release'])
    return r.stdout || '-'
  }

  async getBatteryLevel(serial: string): Promise<number> {
    const r = await this.engine.exec(['-s', serial, 'shell', 'dumpsys', 'battery'])
    const m = r.stdout.match(/level:\s*(\d+)/)
    return m ? parseInt(m[1]) : -1
  }

  // dumpsys device_policy — Android 5+ 지원, dpm get-active-admins는 Android 10+에서만 동작
  async isDeviceOwner(serial: string): Promise<boolean> {
    const r = await this.engine.exec(['-s', serial, 'shell', 'dumpsys', 'device_policy'])
    return r.stdout.includes('com.sympotalk.dpc')
  }

  async isPackageInstalled(serial: string, packageName: string): Promise<boolean> {
    const r = await this.engine.exec(['-s', serial, 'shell', 'pm', 'list', 'packages', packageName])
    return r.stdout.includes(`package:${packageName}`)
  }

  // Google 계정 없음 여부 (DO 등록 사전 조건)
  // RegisteredServicesCache의 "AuthenticatorDescription {type=com.google}"는 시스템 서비스라 제외
  // 실제 사용자 계정은 "Account {name=..., type=com.google}" 형식으로만 등장
  async hasNoGoogleAccount(serial: string): Promise<boolean> {
    const r = await this.engine.exec(['-s', serial, 'shell', 'dumpsys', 'account'])
    return !/Account \{[^}]*type=com\.google/.test(r.stdout)
  }
}
