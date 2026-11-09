import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mockNuxtImport, mountSuspended } from '@nuxt/test-utils/runtime'
import AccountsPage from '../app/pages/accounts.vue'

const { apiMock } = vi.hoisted(() => ({ apiMock: vi.fn() }))
mockNuxtImport('useApi', () => () => ({ api: apiMock }))

const bankAccount = { id: 1, name: '銀行', kind: 'bank', institutionId: null, balance: 12345 }

describe('口座画面', () => {
  beforeEach(() => {
    apiMock.mockReset()
    apiMock.mockImplementation(async (url: string, opts?: any) => {
      if (url === '/api/institutions') return []
      if (url === '/api/accounts' && opts?.method === 'POST') return { id: 9, name: '新規口座', kind: 'bank', institutionId: null, balance: 0 }
      if (url === '/api/accounts') return [bankAccount]
      return []
    })
  })

  it('口座一覧に残高を表示する', async () => {
    const wrapper = await mountSuspended(AccountsPage)
    await vi.waitFor(() => {
      expect(wrapper.text()).toContain('銀行')
    })
    expect(wrapper.text()).toContain('¥12,345')
  })

  it('残高を入力して追加すると currentBalance と recordAdjustment=true が送られる', async () => {
    const wrapper = await mountSuspended(AccountsPage)
    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('新規口座') // 名前
    await wrapper.find('input[type="number"]').setValue('10000') // 現在の残高
    await wrapper.find('form').trigger('submit')
    await vi.waitFor(() => {
      expect(apiMock).toHaveBeenCalledWith('/api/accounts', expect.objectContaining({ method: 'POST' }))
    })
    const postCall = apiMock.mock.calls.find(c => c[1]?.method === 'POST')
    expect(postCall?.[1]?.body).toEqual(expect.objectContaining({ name: '新規口座', currentBalance: 10000, recordAdjustment: true }))
  })

  it('記録するチェックを外すと recordAdjustment=false が送られる', async () => {
    const wrapper = await mountSuspended(AccountsPage)
    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('新規口座')
    await wrapper.find('input[type="number"]').setValue('5000')
    await wrapper.find('input[type="checkbox"]').setValue(false)
    await wrapper.find('form').trigger('submit')
    await vi.waitFor(() => {
      expect(apiMock).toHaveBeenCalledWith('/api/accounts', expect.objectContaining({ method: 'POST' }))
    })
    const postCall = apiMock.mock.calls.find(c => c[1]?.method === 'POST')
    expect(postCall?.[1]?.body).toEqual(expect.objectContaining({ currentBalance: 5000, recordAdjustment: false }))
  })

  it('編集すると現在の残高がフォームに初期表示される', async () => {
    const wrapper = await mountSuspended(AccountsPage)
    await vi.waitFor(() => {
      expect(wrapper.text()).toContain('銀行')
    })
    await wrapper.findAll('button').find(b => b.text() === '編集')!.trigger('click')
    const balanceInput = wrapper.find('input[type="number"]')
    expect((balanceInput.element as HTMLInputElement).value).toBe('12345')
  })
})
