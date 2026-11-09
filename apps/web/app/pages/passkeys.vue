<script setup lang="ts">
import { browserSupportsWebAuthn, startRegistration } from '@simplewebauthn/browser'

interface Passkey { id: number; name: string; createdAt: string | null; lastUsedAt: string | null }

const { api } = useApi()
const passkeys = ref<Passkey[]>([])
const name = ref('')
const error = ref('')
const busy = ref(false)
const supported = ref(false)

async function load() {
  passkeys.value = await api<Passkey[]>('/api/passkeys')
}

onMounted(() => {
  supported.value = browserSupportsWebAuthn()
  load()
})

async function add() {
  busy.value = true; error.value = ''
  try {
    const options: any = await api('/api/passkeys/register/options', { method: 'POST' })
    const credential = await startRegistration({ optionsJSON: options })
    await api('/api/passkeys/register/verify', {
      method: 'POST',
      body: { name: name.value || undefined, challenge: options.challenge, response: credential },
    })
    name.value = ''
    await load()
  } catch (e: any) {
    error.value = e?.data?.message || 'パスキーの登録に失敗しました'
  } finally { busy.value = false }
}

async function remove(p: Passkey) {
  if (!confirm(`パスキー「${p.name}」を削除しますか？`)) return
  await api(`/api/passkeys/${p.id}`, { method: 'DELETE' })
  await load()
}
</script>
<template>
  <div>
    <h1 class="fs-4 fw-bold mb-3">パスキー</h1>
    <p class="text-body-secondary small">
      パスキーを登録すると、次回からメールコードなしでログインできます（ADR-0014）。
      秘密鍵はこの端末の認証器から外に出ることはありません。
    </p>

    <div class="card">
      <div class="card-body vstack gap-3">
        <div v-if="supported" class="d-flex gap-2">
          <input v-model="name" placeholder="パスキーの名前" class="form-control" maxlength="100">
          <button type="button" class="btn btn-primary text-nowrap" :disabled="busy" @click="add">パスキーを追加</button>
        </div>
        <p v-else class="text-body-secondary small mb-0">このブラウザはパスキーに対応していません。</p>
        <p v-if="error" class="text-danger small mb-0">{{ error }}</p>
        <ul class="list-group">
          <li v-for="p in passkeys" :key="p.id" class="list-group-item d-flex align-items-center gap-3">
            <div>
              <div>{{ p.name }}</div>
              <div class="small text-body-secondary">
                登録: {{ p.createdAt?.slice(0, 10) || '-' }} / 最終利用: {{ p.lastUsedAt?.slice(0, 10) || '未使用' }}
              </div>
            </div>
            <button type="button" class="btn btn-outline-danger btn-sm ms-auto" @click="remove(p)">削除</button>
          </li>
          <li v-if="passkeys.length === 0" class="list-group-item text-body-secondary">登録済みのパスキーはありません</li>
        </ul>
      </div>
    </div>
  </div>
</template>
