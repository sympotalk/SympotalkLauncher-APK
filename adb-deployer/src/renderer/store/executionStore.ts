// 실행 중 작업 + 결과 상태 관리
import { create } from 'zustand'
import type { DispatchResult } from '../types/Command'

interface ExecutionStore {
  isRunning: boolean
  total: number
  completed: number
  results: DispatchResult[]
  setRunning: (running: boolean, total?: number) => void
  addResult: (result: DispatchResult) => void
  clearResults: () => void
}

export const useExecutionStore = create<ExecutionStore>((set) => ({
  isRunning: false,
  total: 0,
  completed: 0,
  results: [],

  setRunning: (running, total = 0) =>
    set({ isRunning: running, total, completed: 0, ...(running ? { results: [] } : {}) }),

  addResult: (result) =>
    set(s => ({
      results: [...s.results, result],
      completed: s.completed + 1
    })),

  clearResults: () => set({ results: [] })
}))
