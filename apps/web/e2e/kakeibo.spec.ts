import { test, expect } from '@playwright/test'
import { readFileSync } from 'node:fs'

// 実API + 実DB を相手にする E2E。ユーザー登録から日常操作の一巡と、
// 再ログイン後もデータが見えることまでを通しで確認する。
const stamp = Date.now()
const email = `e2e-${stamp}@example.com`
const displayName = 'E2E太郎'
const accountName = `E2E銀行口座${stamp}`
const memo = `E2Eスーパー買い物${stamp}`
const csvMemo = `E2E CSV取込${stamp}`
const today = new Date()
const todayIso = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`
const todaySlash = `${today.getFullYear()}/${String(today.getMonth() + 1).padStart(2, '0')}/${String(today.getDate()).padStart(2, '0')}`
const thisMonth = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}`

test.describe.serial('Kakeibo E2E', () => {
  test('未ログインではログイン画面へリダイレクトされる', async ({ page }) => {
    await page.goto('/')
    await expect(page).toHaveURL(/\/login$/)
    await expect(page.getByRole('heading', { name: 'Kakeibo' })).toBeVisible()
  })

  test('新規登録するとダッシュボードが表示される', async ({ page }) => {
    await page.goto('/login')
    await page.getByPlaceholder('表示名（新規登録のときだけ）').fill(displayName)
    await page.getByPlaceholder('メールアドレス').fill(email)
    const seen = await countOtpLines(email)
    await page.getByRole('button', { name: 'コードを送信' }).click()
    await page.getByPlaceholder('6桁のコード').fill(await fetchOtpCode(email, seen))
    await page.getByRole('button', { name: 'ログイン' }).click()
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
    const seen = await countOtpLines(email)
    await page.getByRole('button', { name: 'コードを送信' }).click()
    await page.getByPlaceholder('6桁のコード').fill(await fetchOtpCode(email, seen))
    await page.getByRole('button', { name: 'ログイン' }).click()
    await expect(page.getByRole('heading', { name: 'ダッシュボード' })).toBeVisible()
    await page.goto('/transactions')
    await expect(page.getByText(memo)).toBeVisible()
  })

  test('CSV取り込みで取引が増える', async ({ page }) => {
    await login(page)
    await page.goto('/transactions')
    // 取り込み先口座を明示的に選んでから MF形式CSV をアップロードする
    await page.locator('label', { hasText: 'CSVインポート先口座' }).locator('select').selectOption({ label: accountName })
    const csv = ['日付,収支種別,金額,大項目,中項目,内容', `${todaySlash},支出,345,食費,食料品,${csvMemo}`].join('\n')
    await page.locator('input[type="file"]').setInputFiles({ name: 'import.csv', mimeType: 'text/csv', buffer: Buffer.from(csv, 'utf-8') })
    await expect(page.getByText('取り込み完了: 1件、スキップ 0件')).toBeVisible()
    await expect(page.getByText(csvMemo)).toBeVisible()
    await expect(page.getByText('¥345')).toBeVisible()
  })

  test('CSVエクスポートで今月の取引がダウンロードできる', async ({ page }) => {
    await login(page)
    await page.goto('/transactions')
    const [download] = await Promise.all([
      page.waitForEvent('download'),
      page.getByRole('button', { name: 'CSVエクスポート' }).click(),
    ])
    expect(download.suggestedFilename()).toBe(`kakeibo-${thisMonth}.csv`)
    const content = readFileSync(await download.path(), 'utf-8')
    expect(content).toContain('"日付","収支種別","金額","大項目","中項目","内容"') // OpenCSV が全フィールドを引用符付きで出す
    expect(content).toContain(memo) // 画面から追加した支出
    expect(content).toContain(csvMemo) // CSV取り込みした支出
  })

  test('パスキーを登録すると、次回はパスキーだけでログインできる', async ({ page, context }) => {
    await login(page)
    // Chromium の仮想認証器（discoverable credential + ユーザー確認あり）
    const cdp = await context.newCDPSession(page)
    await cdp.send('WebAuthn.enable')
    const { authenticatorId } = await cdp.send('WebAuthn.addVirtualAuthenticator', {
      options: {
        protocol: 'ctap2', transport: 'internal',
        hasResidentKey: true, hasUserVerification: true, isUserVerified: true,
        automaticPresenceSimulation: true,
      },
    })
    try {
      await page.goto('/passkeys')
      await page.getByPlaceholder('パスキーの名前').fill('E2Eパスキー')
      await page.getByRole('button', { name: 'パスキーを追加' }).click()
      await expect(page.getByText('E2Eパスキー')).toBeVisible()

      await page.getByRole('button', { name: 'ログアウト' }).click()
      await expect(page).toHaveURL(/\/login$/)
      await page.getByRole('button', { name: 'パスキーでログイン' }).click()
      await expect(page.getByRole('heading', { name: 'ダッシュボード' })).toBeVisible()
    } finally {
      await cdp.send('WebAuthn.removeVirtualAuthenticator', { authenticatorId }).catch(() => {})
    }
  })
})

/** 登録済みユーザーでログインし、ダッシュボード到達まで待つ（各テストは新規ページなので毎回必要）。 */
async function login(page: import('@playwright/test').Page) {
  await page.goto('/login')
  await page.getByPlaceholder('メールアドレス').fill(email)
  const seen = await countOtpLines(email)
  await page.getByRole('button', { name: 'コードを送信' }).click()
  await page.getByPlaceholder('6桁のコード').fill(await fetchOtpCode(email, seen))
  await page.getByRole('button', { name: 'ログイン' }).click()
  await expect(page.getByRole('heading', { name: 'ダッシュボード' })).toBeVisible()
}

const apiLogPath = () => process.env.KAKEIBO_API_LOG ?? '/tmp/api.log'

/** そのメール宛の [MAIL:LOG] 行（= 発行済みコード）の数を数える。コード取得の起点に使う。 */
async function countOtpLines(forEmail: string): Promise<number> {
  try {
    return readFileSync(apiLogPath(), 'utf-8').split('\n')
      .filter(l => l.includes('[MAIL:LOG]') && l.includes(`to=${forEmail}`)).length
  } catch {
    return 0
  }
}

/**
 * OTP コードを API のログから取得する。開発用メールモード (MAIL_MODE=log) では
 * コードが API ログに [MAIL:LOG] として出力される。
 * 過去のコードと取り違えないよう、送信前に数えた行数 (alreadySeen) より
 * 新しく増えた行のコードだけを対象にする。
 */
async function fetchOtpCode(forEmail: string, alreadySeen: number): Promise<string> {
  for (let i = 0; i < 30; i++) {
    try {
      const mine = readFileSync(apiLogPath(), 'utf-8').split('\n')
        .filter(l => l.includes('[MAIL:LOG]') && l.includes(`to=${forEmail}`))
      if (mine.length > alreadySeen) {
        const m = mine[mine.length - 1].match(/ログインコード: (\d{6})/)
        if (m) return m[1]
      }
    } catch { /* ログ未生成の間は待つ */ }
    await new Promise(r => setTimeout(r, 500))
  }
  throw new Error(`OTP code not found in API log for ${forEmail}`)
}
