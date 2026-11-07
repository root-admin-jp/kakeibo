import { describe, it, expect, beforeEach } from 'vitest'
import { useAuth } from '../composables/useAuth'

describe('useAuth', () => {
  beforeEach(() => {
    localStorage.clear()
    const { token, user } = useAuth()
    token.value = null
    user.value = null
  })

  it('setSession で state と localStorage の両方に保存される', () => {
    const { token, user, setSession } = useAuth()
    setSession('tok-1', { userId: 1, householdId: 2, email: 'a@example.com', displayName: '太郎' })
    expect(token.value).toBe('tok-1')
    expect(user.value?.displayName).toBe('太郎')
    expect(localStorage.getItem('kakeibo-token')).toBe('tok-1')
    expect(JSON.parse(localStorage.getItem('kakeibo-user')!).email).toBe('a@example.com')
  })

  it('restore で localStorage からセッションを復元する', () => {
    localStorage.setItem('kakeibo-token', 'tok-2')
    localStorage.setItem('kakeibo-user', JSON.stringify({ userId: 5, householdId: 6, email: 'c@example.com', displayName: '次郎' }))
    const { token, user, restore } = useAuth()
    expect(token.value).toBeNull()
    restore()
    expect(token.value).toBe('tok-2')
    expect(user.value?.userId).toBe(5)
  })

  it('壊れた保存ユーザー情報は無視される', () => {
    localStorage.setItem('kakeibo-token', 'tok-3')
    localStorage.setItem('kakeibo-user', '{broken json')
    const { token, user, restore } = useAuth()
    restore()
    expect(token.value).toBe('tok-3')
    expect(user.value).toBeNull()
  })
})
