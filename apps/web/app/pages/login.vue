<script setup lang="ts">
definePageMeta({ layout: false })
const { api } = useApi()
const { setSession } = useAuth()
const mode = ref<'login' | 'register'>('login')
const email = ref(''); const password = ref(''); const displayName = ref('')
const error = ref(''); const busy = ref(false)

async function submit() {
  busy.value = true; error.value = ''
  try {
    const path = mode.value === 'login' ? '/api/auth/login' : '/api/auth/register'
    const body = mode.value === 'login' ? { email: email.value, password: password.value } : { email: email.value, password: password.value, displayName: displayName.value }
    const res: any = await api(path, { method: 'POST', body })
    setSession(res.token, res)
    navigateTo('/')
  } catch (e: any) {
    error.value = e?.data?.message || e?.message || 'ログインに失敗しました'
  } finally { busy.value = false }
}
</script>
<template>
  <div class="min-vh-100 d-flex align-items-center justify-content-center">
    <form class="card shadow w-100 login-card card-body vstack gap-3 p-4" @submit.prevent="submit">
      <h1 class="fs-5 fw-bold text-center mb-0">Kakeibo</h1>
      <div class="btn-group w-100" role="group">
        <button type="button" class="btn btn-sm" :class="mode==='login' ? 'btn-primary' : 'btn-outline-secondary'" @click="mode='login'">ログイン</button>
        <button type="button" class="btn btn-sm" :class="mode==='register' ? 'btn-primary' : 'btn-outline-secondary'" @click="mode='register'">新規登録</button>
      </div>
      <input v-if="mode==='register'" v-model="displayName" required placeholder="表示名" class="form-control">
      <input v-model="email" type="email" required placeholder="メールアドレス" class="form-control">
      <input v-model="password" type="password" required minlength="8" placeholder="パスワード（8文字以上）" class="form-control">
      <p v-if="error" class="text-danger small mb-0">{{ error }}</p>
      <button :disabled="busy" class="btn btn-primary w-100">{{ mode==='login' ? 'ログイン' : '登録する' }}</button>
    </form>
  </div>
</template>
