// 명령 실행 전 안전성 검사 — blocklist + DO 사전 조건
import { DANGEROUS_PATTERNS } from './blocklist'
import type { DeviceInspector } from '../adb/DeviceInspector'
import type { PrerequisiteCheckResult } from '../../renderer/types/Command'

// stderr 패턴 → 사람이 읽을 수 있는 한국어 메시지
const ERROR_MAP: Array<[RegExp, string]> = [
  [/unauthorized/, 'USB 디버깅을 허가해주세요. 태블릿 화면에서 "허용"을 누르세요.'],
  [/device not found/, '기기가 분리되었습니다. USB 연결을 확인하세요.'],
  [/not have a frp lock/, 'Google 계정이 있습니다. 기기에서 Google 계정을 먼저 제거하세요.'],
  [/Already set/, 'Device Owner가 이미 등록되어 있습니다.'],
  [/INSTALL_FAILED_UPDATE_INCOMPATIBLE/, '서명이 다른 APK가 설치되어 있습니다. 기존 앱을 삭제 후 재설치하세요.'],
  [/INSTALL_FAILED_VERSION_DOWNGRADE/, '현재 설치된 버전보다 낮은 버전입니다.'],
  [/Permission denied/, '권한이 없습니다. Device Owner가 등록되어 있는지 확인하세요.'],
  [/SecurityException/, 'Device Owner 권한이 없습니다. DPC 설치 후 DO 등록을 먼저 실행하세요.'],
]

export class CommandGuard {
  // blocklist 검사 — 통과 시 null, 차단 시 이유 문자열
  checkCommand(args: string[]): string | null {
    const cmd = args.join(' ')
    for (const pattern of DANGEROUS_PATTERNS) {
      if (pattern.test(cmd)) {
        return `위험 명령이 감지되어 차단되었습니다.`
      }
    }
    return null
  }

  // stderr를 한국어 사용자 메시지로 변환
  translateError(stderr: string): string {
    for (const [pattern, msg] of ERROR_MAP) {
      if (pattern.test(stderr)) return msg
    }
    return stderr
  }

  // Device Owner 등록 사전 조건 체크 (기기별)
  async checkDoPrerequisites(
    serial: string,
    inspector: DeviceInspector
  ): Promise<PrerequisiteCheckResult> {
    const [dpcInstalled, noGoogleAccount, noDO] = await Promise.all([
      inspector.isPackageInstalled(serial, 'com.sympotalk.dpc'),
      inspector.hasNoGoogleAccount(serial),
      inspector.isDeviceOwner(serial).then(isDO => !isDO)
    ])
    return {
      passed: dpcInstalled && noGoogleAccount && noDO,
      items: [
        { label: 'DPC APK 설치됨', passed: dpcInstalled },
        { label: 'Google 계정 없음', passed: noGoogleAccount },
        { label: '기존 Device Owner 없음', passed: noDO },
      ]
    }
  }
}
