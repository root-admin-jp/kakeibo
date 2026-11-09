<script setup lang="ts">
import { browserSupportsWebAuthn, startAuthentication } from '@simplewebauthn/browser'
definePageMeta({ layout: false })
const { api } = useApi()
const { setSession } = useAuth()
// パスワードレス認証 (ADR-0014): メール入力 → コード入力の2ステップ。登録とログインは同じフロー。
const step = ref<'email' | 'code'>('email')
const email = ref(''); const displayName = ref(''); const code = ref('')
const error = ref(''); const busy = ref(false)

async function requestCode() {
  busy.value = true; error.value = ''
  try {
    await api('/api/auth/request-code', {
      method: 'POST',
      body: { email: email.value, displayName: displayName.value || undefined },
    })
    step.value = 'code'
  } catch (e: any) {
    error.value = e?.data?.message || e?.message || 'コードの送信に失敗しました'
  } finally { busy.value = false }
}

// パスキー (WebAuthn) ログイン。対応ブラウザでのみボタンを出す。
const passkeySupported = ref(false)
const passkeyError = ref('')
const passkeyBusy = ref(false)
onMounted(() => { passkeySupported.value = browserSupportsWebAuthn() })

async function loginWithPasskey() {
  passkeyBusy.value = true; passkeyError.value = ''
  try {
    const options: any = await api('/api/auth/passkey/login/options', { method: 'POST' })
    const assertion = await startAuthentication({ optionsJSON: options })
    const res: any = await api('/api/auth/passkey/login/verify', {
      method: 'POST',
      body: { challenge: options.challenge, response: assertion },
    })
    setSession(res.token, res)
    navigateTo('/')
  } catch (e: any) {
    passkeyError.value = e?.data?.message || e?.message || 'パスキーでのログインに失敗しました'
  } finally { passkeyBusy.value = false }
}

async function verifyCode() {
  busy.value = true; error.value = ''
  try {
    const res: any = await api('/api/auth/verify-code', {
      method: 'POST',
      body: { email: email.value, code: code.value },
    })
    setSession(res.token, res)
    navigateTo('/')
  } catch (e: any) {
    error.value = e?.data?.message || e?.message || 'コードが正しくありません'
  } finally { busy.value = false }
}
</script>
<template>
  <div class="min-vh-100 d-flex flex-column align-items-center justify-content-center gap-3">
    <form v-if="step === 'email'" class="card shadow w-100 login-card card-body vstack gap-3 p-4" @submit.prevent="requestCode">
      <h1 class="fs-5 fw-bold text-center mb-0">Kakeibo</h1>
      <p class="small text-body-secondary mb-0">メールアドレスに6桁のログインコードを送ります。新規登録も同じ手順です。</p>
      <input v-model="email" type="email" required placeholder="メールアドレス" class="form-control">
      <input v-model="displayName" placeholder="表示名（新規登録のときだけ）" class="form-control">
      <p v-if="error" class="text-danger small mb-0">{{ error }}</p>
      <button :disabled="busy" class="btn btn-primary w-100">コードを送信</button>
    </form>
    <form v-else class="card shadow w-100 login-card card-body vstack gap-3 p-4" @submit.prevent="verifyCode">
      <h1 class="fs-5 fw-bold text-center mb-0">Kakeibo</h1>
      <p class="small text-body-secondary mb-0">{{ email }} にコードを送信しました。有効期限は10分です。</p>
      <input v-model="code" inputmode="numeric" autocomplete="one-time-code" required pattern="\d{6}" placeholder="6桁のコード" class="form-control">
      <p v-if="error" class="text-danger small mb-0">{{ error }}</p>
      <button :disabled="busy" class="btn btn-primary w-100">ログイン</button>
      <button type="button" class="btn btn-link btn-sm" @click="step = 'email'">メールアドレスを変える</button>
    </form>
    <div v-if="step === 'email' && passkeySupported" class="card shadow w-100 login-card card-body vstack gap-2 p-4">
      <button type="button" :disabled="passkeyBusy" class="btn btn-outline-secondary w-100" @click="loginWithPasskey">パスキーでログイン</button>
      <p v-if="passkeyError" class="text-danger small mb-0">{{ passkeyError }}</p>
    </div>
  </div>
</template>
