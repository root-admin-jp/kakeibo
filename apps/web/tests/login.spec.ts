import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mockNuxtImport, mountSuspended } from '@nuxt/test-utils/runtime'
import LoginPage from '../app/pages/login.vue'

// useApi だけモックし、useAuth / ルーティングは実物を使う
const { apiMock } = vi.hoisted(() => ({ apiMock: vi.fn() }))
mockNuxtImport('useApi', () => () => ({ api: apiMock }))

// パスキー対応ブラウザとして振る舞わせる
const { startAuthenticationMock } = vi.hoisted(() => ({ startAuthenticationMock: vi.fn() }))
vi.mock('@simplewebauthn/browser', () => ({
  browserSupportsWebAuthn: () => true,
  startAuthentication: startAuthenticationMock,
  startRegistration: vi.fn(),
}))

async function mountLogin() {
  return await mountSuspended(LoginPage)
}

describe('ログイン画面', () => {
  beforeEach(() => {
    apiMock.mockReset()
    localStorage.clear()
  })

  it('初期表示はメール入力ステップで、コード欄やパスワード欄はない', async () => {
    const wrapper = await mountLogin()
    expect(wrapper.find('h1').text()).toBe('Kakeibo')
    expect(wrapper.find('input[placeholder="メールアドレス"]').exists()).toBe(true)
    expect(wrapper.find('input[placeholder="表示名（新規登録のときだけ）"]').exists()).toBe(true)
    expect(wrapper.find('input[placeholder="6桁のコード"]').exists()).toBe(false)
    expect(wrapper.find('input[type="password"]').exists()).toBe(false)
  })

  it('コード送信で request-code API を叩き、コード入力ステップに進む', async () => {
    apiMock.mockResolvedValue({ sent: true })
    const wrapper = await mountLogin()
    await wrapper.find('input[placeholder="メールアドレス"]').setValue('a@example.com')
    await wrapper.find('form').trigger('submit')
    await vi.waitFor(() => {
      expect(wrapper.find('input[placeholder="6桁のコード"]').exists()).toBe(true)
    })
    expect(apiMock).toHaveBeenCalledWith(
      '/api/auth/request-code',
      expect.objectContaining({ method: 'POST', body: { email: 'a@example.com', displayName: undefined } }),
    )
  })

  it('新規登録のときは表示名も一緒に送る', async () => {
    apiMock.mockResolvedValue({ sent: true })
    const wrapper = await mountLogin()
    await wrapper.find('input[placeholder="表示名（新規登録のときだけ）"]').setValue('花子')
    await wrapper.find('input[placeholder="メールアドレス"]').setValue('b@example.com')
    await wrapper.find('form').trigger('submit')
    await vi.waitFor(() => {
      expect(wrapper.find('input[placeholder="6桁のコード"]').exists()).toBe(true)
    })
    expect(apiMock).toHaveBeenCalledWith(
      '/api/auth/request-code',
      expect.objectContaining({ method: 'POST', body: { email: 'b@example.com', displayName: '花子' } }),
    )
  })

  it('コード検証の成功で verify-code API を叩き、トークンが保存される', async () => {
    apiMock.mockResolvedValueOnce({ sent: true })
    apiMock.mockResolvedValue({ token: 'token-123', userId: 1, householdId: 2, email: 'a@example.com', displayName: '太郎' })
    const wrapper = await mountLogin()
    await wrapper.find('input[placeholder="メールアドレス"]').setValue('a@example.com')
    await wrapper.find('form').trigger('submit')
    await vi.waitFor(() => {
      expect(wrapper.find('input[placeholder="6桁のコード"]').exists()).toBe(true)
    })
    await wrapper.find('input[placeholder="6桁のコード"]').setValue('123456')
    await wrapper.find('form').trigger('submit')
    await vi.waitFor(() => {
      expect(localStorage.getItem('kakeibo-token')).toBe('token-123')
    })
    expect(apiMock).toHaveBeenCalledWith(
      '/api/auth/verify-code',
      expect.objectContaining({ method: 'POST', body: { email: 'a@example.com', code: '123456' } }),
    )
  })

  it('コード送信の失敗でエラーメッセージを表示する', async () => {
    apiMock.mockRejectedValue({ data: { message: 'please wait before requesting a new code' } })
    const wrapper = await mountLogin()
    await wrapper.find('input[placeholder="メールアドレス"]').setValue('a@example.com')
    await wrapper.find('form').trigger('submit')
    await vi.waitFor(() => {
      expect(wrapper.text()).toContain('please wait before requesting a new code')
    })
    expect(localStorage.getItem('kakeibo-token')).toBeNull()
    expect(wrapper.find('input[placeholder="6桁のコード"]').exists()).toBe(false)
  })

  it('パスキー対応ブラウザではパスキーログインボタンが出る', async () => {
    const wrapper = await mountLogin()
    await vi.waitFor(() => {
      expect(wrapper.findAll('button').some(b => b.text() === 'パスキーでログイン')).toBe(true)
    })
  })

  it('パスキーでログインすると options → 認証器 → verify の順に進みセッションが保存される', async () => {
    apiMock
      .mockResolvedValueOnce({ challenge: 'ch-1', rpId: 'localhost' })
      .mockResolvedValueOnce({ token: 'pk-token', userId: 1, householdId: 2, email: 'a@example.com', displayName: '花子' })
    startAuthenticationMock.mockResolvedValue({ id: 'cred-1', type: 'public-key' })
    const wrapper = await mountLogin()
    const btn = await vi.waitFor(() => {
      const b = wrapper.findAll('button').find(x => x.text() === 'パスキーでログイン')
      if (!b) throw new Error('button not yet')
      return b
    })
    await btn.trigger('click')
    await vi.waitFor(() => {
      expect(localStorage.getItem('kakeibo-token')).toBe('pk-token')
    })
    expect(apiMock).toHaveBeenNthCalledWith(1, '/api/auth/passkey/login/options', expect.objectContaining({ method: 'POST' }))
    expect(startAuthenticationMock).toHaveBeenCalledWith(
      expect.objectContaining({ optionsJSON: expect.objectContaining({ challenge: 'ch-1' }) }),
    )
    expect(apiMock).toHaveBeenNthCalledWith(
      2, '/api/auth/passkey/login/verify',
      expect.objectContaining({
        method: 'POST',
        body: { challenge: 'ch-1', response: { id: 'cred-1', type: 'public-key' } },
      }),
    )
  })
})
