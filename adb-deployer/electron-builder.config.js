// electron-builder 배포 설정
/** @type {import('electron-builder').Configuration} */
const config = {
  appId: 'com.sympotalk.adb-deployer',
  productName: 'Sympotalk ADB Deployer',
  directories: {
    buildResources: 'resources',
    output: 'dist'
  },
  files: ['out/**/*'],
  extraResources: [
    { from: 'platform-tools', to: 'platform-tools' },
    { from: 'usb_driver',     to: 'usb_driver' }
  ],
  win: {
    target: [
      { target: 'portable', arch: ['x64'] },
      { target: 'nsis',     arch: ['x64'] }
    ]
  },
  nsis: {
    oneClick: false,
    allowToChangeInstallationDirectory: true,
    installerLanguages: ['ko_KR'],
    language: '1042'
  }
}

module.exports = config
