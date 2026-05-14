// IPC 채널 상수 — Main ↔ Renderer 계약
export const IPC = {
  // Main → Renderer (push 이벤트)
  DEVICE_LIST_UPDATED:    'device:list-updated',
  DEVICE_STATUS_CHANGED:  'device:status-changed',
  EXECUTION_PROGRESS:     'execution:progress',
  EXECUTION_COMPLETE:     'execution:complete',

  // Renderer → Main (invoke/request)
  EXECUTE_COMMAND:        'adb:execute',
  EXECUTE_BATCH:          'adb:execute-batch',
  EXECUTE_CANCEL:         'adb:cancel',
  DEVICE_INSPECT:         'device:inspect',
  POLLER_START:           'poller:start',
  POLLER_STOP:            'poller:stop',
  APK_SELECT:             'apk:select',
  APK_GET_PATHS:          'apk:get-paths',
  SETTINGS_GET:           'settings:get',
  SETTINGS_SET:           'settings:set',
  LOG_QUERY:              'log:query',
  LOG_EXPORT:             'log:export',
  GUARD_CHECK:            'guard:check',
  GUARD_DO_PREREQ:        'guard:do-prereq',

  // USB 드라이버
  DRIVER_CHECK:           'driver:check',
  DRIVER_INSTALL:         'driver:install',
} as const

export type IpcChannel = typeof IPC[keyof typeof IPC]
