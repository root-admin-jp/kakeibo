import { describe, it, expect, vi, afterEach } from 'vitest'
import { yen, currentMonth } from '../composables/useApi'

describe('yen (金額フォーマット)', () => {
  it('カンマ区切りで ¥ を付ける', () => {
    expect(yen(1200)).toBe('¥1,200')
    expect(yen(1_000_000)).toBe('¥1,000,000')
    expect(yen(999)).toBe('¥999')
  })
  it('0円', () => {
    expect(yen(0)).toBe('¥0')
  })
  it('マイナス金額', () => {
    expect(yen(-500)).toBe('¥-500')
  })
})

describe('currentMonth (当月 YYYY-MM)', () => {
  afterEach(() => { vi.useRealTimers() })

  it('月をゼロ埋めする', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date(2026, 0, 5)) // 2026年1月
    expect(currentMonth()).toBe('2026-01')
  })
  it('2桁月はそのまま', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date(2026, 10, 15)) // 2026年11月
    expect(currentMonth()).toBe('2026-11')
  })
})
