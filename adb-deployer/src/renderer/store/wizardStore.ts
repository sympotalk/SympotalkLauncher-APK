// 마법사 단계·실행 상태 전역 유지 — 탭 전환 시 unmount돼도 보존
import { create } from 'zustand'
import type { DispatchResult, PrerequisiteCheckResult } from '../types/Command'

type StepIndex = 0 | 1 | 2

interface WizardStore {
  step: StepIndex
  running: boolean
  results: DispatchResult[]
  prereqChecks: Record<string, PrerequisiteCheckResult>
  setStep: (s: StepIndex) => void
  setRunning: (v: boolean) => void
  setResults: (r: DispatchResult[]) => void
  setPrereqChecks: (c: Record<string, PrerequisiteCheckResult>) => void
}

export const useWizardStore = create<WizardStore>((set) => ({
  step: 0,
  running: false,
  results: [],
  prereqChecks: {},
  setStep: (step) => set({ step }),
  setRunning: (running) => set({ running }),
  setResults: (results) => set({ results }),
  setPrereqChecks: (prereqChecks) => set({ prereqChecks }),
}))
