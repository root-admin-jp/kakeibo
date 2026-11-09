import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mockNuxtImport, mountSuspended } from '@nuxt/test-utils/runtime'
import PasskeysPage from '../app/pages/passkeys.vue'

const { apiMock } = vi.hoisted(() => ({ apiMock: vi.fn() }))
mockNuxtImport('useApi', () => () => ({ api: apiMock }))

const { startRegistrationMock } = vi.hoisted(() => ({ startRegistrationMock: vi.fn() }))
vi.mock('@simplewebauthn/browser', () => ({
  browserSupportsWebAuthn: () => true,
  startRegistration: startRegistrationMock,
  startAuthentication: vi.fn(),
}))

describe('パスキー管理画面', () => {
  beforeEach(() => {
    apiMock.mockReset()
    startRegistrationMock.mockReset()
  })

  it('登録済みパスキーの一覧を表示する', async () => {
    apiMock.mockResolvedValue([
      { id: 1, name: 'MacBook', createdAt: '2026-11-09T10:00:00', lastUsedAt: null },
    ])
    const wrapper = await mountSuspended(PasskeysPage)
    await vi.waitFor(() => {
      expect(wrapper.text()).toContain('MacBook')
    })
    expect(wrapper.text()).toContain('未使用')
    expect(apiMock).toHaveBeenCalledWith('/api/passkeys')
  })

  it('追加は options → 認証器 → verify の順に進む', async () => {
    apiMock
      .mockResolvedValueOnce([]) // 初回一覧
      .mockResolvedValueOnce({ challenge: 'ch-9' }) // register/options
      .mockResolvedValueOnce({ id: 2, name: 'iPhone', createdAt: '2026-11-09T11:00:00', lastUsedAt: null }) // verify
      .mockResolvedValueOnce([{ id: 2, name: 'iPhone', createdAt: '2026-11-09T11:00:00', lastUsedAt: null }]) // 再読込
    startRegistrationMock.mockResolvedValue({ id: 'cred-9', type: 'public-key' })
    const wrapper = await mountSuspended(PasskeysPage)
    await wrapper.find('input[placeholder="パスキーの名前"]').setValue('iPhone')
    await wrapper.findAll('button').find(b => b.text() === 'パスキーを追加')!.trigger('click')
    await vi.waitFor(() => {
      expect(wrapper.text()).toContain('iPhone')
    })
    expect(startRegistrationMock).toHaveBeenCalledWith(
      expect.objectContaining({ optionsJSON: expect.objectContaining({ challenge: 'ch-9' }) }),
    )
    expect(apiMock).toHaveBeenCalledWith(
      '/api/passkeys/register/verify',
      expect.objectContaining({
        method: 'POST',
        body: { name: 'iPhone', challenge: 'ch-9', response: { id: 'cred-9', type: 'public-key' } },
      }),
    )
  })
})
