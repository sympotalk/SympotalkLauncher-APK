// dev 서버 래퍼 — VS Code 터미널에서 상속되는 ELECTRON_RUN_AS_NODE=1 제거 후 electron-vite 실행
'use strict';
const { spawn } = require('child_process');
const path = require('path');

const env = Object.assign({}, process.env);
delete env.ELECTRON_RUN_AS_NODE;

const bin = process.platform === 'win32'
  ? path.resolve(__dirname, '../node_modules/.bin/electron-vite.cmd')
  : path.resolve(__dirname, '../node_modules/.bin/electron-vite');

const ps = spawn(bin, ['dev'], { stdio: 'inherit', env: env, shell: true });
ps.on('close', function(code) { process.exit(code || 0); });
