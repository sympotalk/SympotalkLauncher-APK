// electron-builder 배포 설정
import type { Configuration } from 'electron-builder'

const config: Configuration = {
  appId: 'com.sympotalk.adb-deployer',
  productName: 'Sympotalk ADB Deployer',
  directories: {
    buildResources: 'build',
    output: 'dist'
  },
  files: ['out/**/*'],
  extraResources: [
    // ADB 바이너리 번들 (Phase 2에서 추가)
    // { from: 'resources/adb', to: 'adb', filter: ['**/*'] }
  ],
  win: {
    target: [{ target: 'nsis', arch: ['x64'] }],
    icon: 'build/icon.ico'
  },
  mac: {
    target: [{ target: 'dmg', arch: ['x64', 'arm64'] }],
    icon: 'build/icon.icns',
    category: 'public.app-category.developer-tools'
  },
  nsis: {
    oneClick: false,
    allowToChangeInstallationDirectory: true,
    installerLanguages: ['ko_KR'],
    language: '1042'
  }
}

export default config
