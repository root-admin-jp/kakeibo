import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mockNuxtImport, mountSuspended } from '@nuxt/test-utils/runtime'
import LoginPage from '../pages/login.vue'

// useApi だけモックし、useAuth / ルーティングは実物を使う
const { apiMock } = vi.hoisted(() => ({ apiMock: vi.fn() }))
mockNuxtImport('useApi', () => () => ({ api: apiMock }))

async function mountLogin() {
  return await mountSuspended(LoginPage)
}

describe('ログイン画面', () => {
  beforeEach(() => {
    apiMock.mockReset()
    localStorage.clear()
  })

  it('初期表示はログインモードで、表示名欄は出ない', async () => {
    const wrapper = await mountLogin()
    expect(wrapper.find('h1').text()).toBe('Kakeibo')
    expect(wrapper.find('input[placeholder="メールアドレス"]').exists()).toBe(true)
    expect(wrapper.find('input[placeholder="パスワード（8文字以上）"]').exists()).toBe(true)
    expect(wrapper.find('input[placeholder="表示名"]').exists()).toBe(false)
  })

  it('新規登録モードに切り替えると表示名欄が出る', async () => {
    const wrapper = await mountLogin()
    const toggle = wrapper.findAll('button').find(b => b.text() === '新規登録')!
    await toggle.trigger('click')
    expect(wrapper.find('input[placeholder="表示名"]').exists()).toBe(true)
  })

  it('ログイン成功で API を叩き、トークンが保存される', async () => {
    apiMock.mockResolvedValue({ token: 'token-123', userId: 1, householdId: 2, email: 'a@example.com', displayName: '太郎' })
    const wrapper = await mountLogin()
    await wrapper.find('input[placeholder="メールアドレス"]').setValue('a@example.com')
    await wrapper.find('input[placeholder="パスワード（8文字以上）"]').setValue('password123')
    await wrapper.find('form').trigger('submit')
    await vi.waitFor(() => {
      expect(localStorage.getItem('kakeibo-token')).toBe('token-123')
    })
    expect(apiMock).toHaveBeenCalledWith('/api/auth/login', expect.objectContaining({ method: 'POST' }))
  })

  it('登録成功で register API に表示名付きで送る', async () => {
    apiMock.mockResolvedValue({ token: 'token-999', userId: 3, householdId: 4, email: 'b@example.com', displayName: '花子' })
    const wrapper = await mountLogin()
    const toggle = wrapper.findAll('button').find(b => b.text() === '新規登録')!
    await toggle.trigger('click')
    await wrapper.find('input[placeholder="表示名"]').setValue('花子')
    await wrapper.find('input[placeholder="メールアドレス"]').setValue('b@example.com')
    await wrapper.find('input[placeholder="パスワード（8文字以上）"]').setValue('password123')
    await wrapper.find('form').trigger('submit')
    await vi.waitFor(() => {
      expect(localStorage.getItem('kakeibo-token')).toBe('token-999')
    })
    expect(apiMock).toHaveBeenCalledWith(
      '/api/auth/register',
      expect.objectContaining({ method: 'POST', body: { email: 'b@example.com', password: 'password123', displayName: '花子' } }),
    )
  })

  it('ログイン失敗でエラーメッセージを表示する', async () => {
    apiMock.mockRejectedValue({ data: { message: 'invalid email or password' } })
    const wrapper = await mountLogin()
    await wrapper.find('input[placeholder="メールアドレス"]').setValue('a@example.com')
    await wrapper.find('input[placeholder="パスワード（8文字以上）"]').setValue('wrong-password')
    await wrapper.find('form').trigger('submit')
    await vi.waitFor(() => {
      expect(wrapper.text()).toContain('invalid email or password')
    })
    expect(localStorage.getItem('kakeibo-token')).toBeNull()
  })
})
