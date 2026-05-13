// 렌더러 진입점
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { App } from './App'
import './assets/global.css'

const root = document.getElementById('root')!
createRoot(root).render(
  <StrictMode>
    <App />
  </StrictMode>
)
