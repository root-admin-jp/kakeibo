import { defineConfig, devices } from '@playwright/test'

// E2E: 実API (8080) + 実DB + Web (3000) を起動した状態で実行する。
// サーバー起動は CI (.github/workflows/e2e.yml) 側が担当し、ここでは起動済み前提。
// ローカルで試す場合は API と Web を先に起動してから `npm run test:e2e`。
export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: process.env.E2E_BASE_URL || 'http://localhost:3000',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],
})
