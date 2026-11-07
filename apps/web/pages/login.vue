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
  <div class="min-h-screen flex items-center justify-center bg-gray-50">
    <form class="bg-white p-8 rounded shadow w-full max-w-sm space-y-4" @submit.prevent="submit">
      <h1 class="text-xl font-bold text-center">Kakeibo</h1>
      <div class="flex gap-2 text-sm">
        <button type="button" class="flex-1 py-1 rounded border" :class="mode==='login' ? 'bg-blue-600 text-white' : ''" @click="mode='login'">ログイン</button>
        <button type="button" class="flex-1 py-1 rounded border" :class="mode==='register' ? 'bg-blue-600 text-white' : ''" @click="mode='register'">新規登録</button>
      </div>
      <input v-if="mode==='register'" v-model="displayName" required placeholder="表示名" class="w-full border rounded px-3 py-2">
      <input v-model="email" type="email" required placeholder="メールアドレス" class="w-full border rounded px-3 py-2">
      <input v-model="password" type="password" required minlength="8" placeholder="パスワード（8文字以上）" class="w-full border rounded px-3 py-2">
      <p v-if="error" class="text-red-600 text-sm">{{ error }}</p>
      <button :disabled="busy" class="w-full bg-blue-600 text-white py-2 rounded disabled:opacity-50">{{ mode==='login' ? 'ログイン' : '登録する' }}</button>
    </form>
  </div>
</template>
