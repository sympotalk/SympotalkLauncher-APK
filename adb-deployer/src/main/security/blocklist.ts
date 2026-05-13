// 위험 명령 패턴 목록 — 매칭 시 즉시 차단
export const DANGEROUS_PATTERNS: RegExp[] = [
  /factory[\s._-]*reset/i,
  /wipe[\s._-]*data/i,
  /rm\s+-[rf]+\s*\//,
  /dpm\s+remove-active-admin/i,
  /pm\s+uninstall.*com\.sympotalk/i,
  /settings\s+put\s+global/i,
  /am\s+force-stop\s+com\.sympotalk/i,
]

// 확인 모달이 필요한 프리셋 ID 목록
export const CONFIRM_REQUIRED_PRESET_IDS = new Set([
  'reboot',
  'device-owner-register',
  'launcher-force-stop',
])
