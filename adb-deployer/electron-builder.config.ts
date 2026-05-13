// electron-builder 배포 설정
import type { Configuration } from 'electron-builder'

const config: Configuration = {
  appId: 'com.sympotalk.adb-deployer',
  productName: 'Sympotalk ADB Deployer',
  directories: {
    buildResources: 'resources',
    output: 'dist'
  },
  files: ['out/**/*'],
  extraResources: [
    { from: 'platform-tools', to: 'platform-tools' }
  ],
  win: {
    target: [
      { target: 'portable', arch: ['x64'] },
      { target: 'nsis',     arch: ['x64'] }
    ]
    // TODO: icon — build/icon.ico 추가 후 활성화
  },
  nsis: {
    oneClick: false,
    allowToChangeInstallationDirectory: true,
    installerLanguages: ['ko_KR'],
    language: '1042'
  }
}

export default config
