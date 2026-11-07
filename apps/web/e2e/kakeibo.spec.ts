import { test, expect } from '@playwright/test'

// 実API + 実DB を相手にする E2E。ユーザー登録から日常操作の一巡と、
// 再ログイン後もデータが見えることまでを通しで確認する。
const stamp = Date.now()
const email = `e2e-${stamp}@example.com`
const password = 'password123'
const displayName = 'E2E太郎'
const accountName = `E2E銀行口座${stamp}`
const memo = `E2Eスーパー買い物${stamp}`
const today = new Date()
const todayIso = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`

test.describe.serial('Kakeibo E2E', () => {
  test('未ログインではログイン画面へリダイレクトされる', async ({ page }) => {
    await page.goto('/')
    await expect(page).toHaveURL(/\/login$/)
    await expect(page.getByRole('heading', { name: 'Kakeibo' })).toBeVisible()
  })

  test('新規登録するとダッシュボードが表示される', async ({ page }) => {
    await page.goto('/login')
    await page.getByRole('button', { name: '新規登録' }).click()
    await page.getByPlaceholder('表示名').fill(displayName)
    await page.getByPlaceholder('メールアドレス').fill(email)
    await page.getByPlaceholder('パスワード（8文字以上）').fill(password)
    await page.getByRole('button', { name: '登録する' }).click()
    await expect(page).toHaveURL(/\/$/)
    await expect(page.getByRole('heading', { name: 'ダッシュボード' })).toBeVisible()
    await expect(page.getByText(displayName)).toBeVisible() // ヘッダーのユーザー名
  })

  test('口座を追加できる', async ({ page }) => {
    await login(page)
    await page.goto('/accounts')
    await expect(page.getByRole('heading', { name: '口座' })).toBeVisible()
    await page.getByLabel('名前', { exact: true }).fill(accountName)
    // select は label 内に option テキストも含まれ getByLabel(exact) で特定できないため、フォーム内の順序で特定する（1番目=種別）
    await page.locator('form select').nth(0).selectOption('bank')
    await page.getByRole('button', { name: '追加' }).click()
    await expect(page.getByText(accountName)).toBeVisible()
  })

  test('支出を追加すると取引一覧に出る', async ({ page }) => {
    await login(page)
    await page.goto('/transactions')
    await expect(page.getByRole('heading', { name: '取引' })).toBeVisible()
    await page.getByLabel('日付', { exact: true }).fill(todayIso)
    // フォーム内の select は順に 種別・口座・カテゴリ（支出時は振替先が出ない）
    const form = page.locator('form')
    await form.locator('select').nth(0).selectOption('expense')
    await form.locator('select').nth(1).selectOption({ label: accountName })
    await form.locator('select').nth(2).selectOption({ label: '食費 / 食料品' })
    await page.getByLabel('金額', { exact: true }).fill('1200')
    await page.getByLabel('内容', { exact: true }).fill(memo)
    await page.getByRole('button', { name: '追加' }).click()
    await expect(page.getByText(memo)).toBeVisible()
    await expect(page.getByText('¥1,200')).toBeVisible()
  })

  test('ダッシュボードに支出が集計される', async ({ page }) => {
    await login(page)
    await page.goto('/')
    await expect(page.getByRole('heading', { name: 'ダッシュボード' })).toBeVisible()
    await expect(page.getByText('¥1,200').first()).toBeVisible()
    await expect(page.getByText('食費').first()).toBeVisible()
    await expect(page.getByText(accountName)).toBeVisible()
  })

  test('ログアウトして再ログインしてもデータが残っている', async ({ page }) => {
    await login(page)
    await page.getByRole('button', { name: 'ログアウト' }).click()
    await expect(page).toHaveURL(/\/login$/)
    await page.getByPlaceholder('メールアドレス').fill(email)
    await page.getByPlaceholder('パスワード（8文字以上）').fill(password)
    await page.locator('form > button').click()
    await expect(page.getByRole('heading', { name: 'ダッシュボード' })).toBeVisible()
    await page.goto('/transactions')
    await expect(page.getByText(memo)).toBeVisible()
  })
})

/** 登録済みユーザーでログインし、ダッシュボード到達まで待つ（各テストは新規ページなので毎回必要）。 */
async function login(page: import('@playwright/test').Page) {
  await page.goto('/login')
  await page.getByPlaceholder('メールアドレス').fill(email)
  await page.getByPlaceholder('パスワード（8文字以上）').fill(password)
  await page.locator('form > button').click()
  await expect(page.getByRole('heading', { name: 'ダッシュボード' })).toBeVisible()
}
