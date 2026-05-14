// ADB Deployer electron-vite 빌드 설정
import { resolve } from 'path'
import { defineConfig, externalizeDepsPlugin } from 'electron-vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  main: {
    plugins: [externalizeDepsPlugin()]
  },
  preload: {
    plugins: [externalizeDepsPlugin()]
  },
  renderer: {
    define: {
      // npm run build 시 npm이 자동으로 npm_package_version 환경변수를 설정
      __APP_VERSION__: JSON.stringify(process.env['npm_package_version'] ?? '0.0.0')
    },
    resolve: {
      alias: {
        '@renderer': resolve('src/renderer')
      }
    },
    plugins: [react()]
  }
})
