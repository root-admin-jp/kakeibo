<script setup lang="ts">
const { api } = useApi()
const config = useRuntimeConfig()
const { token } = useAuth()
const month = ref(currentMonth())
const items = ref<any[]>([])
const accounts = ref<any[]>([])
const categories = ref<any[]>([])
const editingId = ref<number | null>(null)
const error = ref('')
const importMsg = ref('')
const importAccountId = ref<number | null>(null)
const form = ref({ accountId: null as number | null, toAccountId: null as number | null, categoryId: null as number | null, type: 'expense', amount: 0, transactedOn: new Date().toISOString().slice(0, 10), memo: '' })
const catLabel = (c: any) => { const p = categories.value.find(x => x.id === c.parentId); return p ? `${p.name} / ${c.name}` : c.name }
const accountName = (id: number) => accounts.value.find(a => a.id === id)?.name || `#${id}`

async function load() {
  items.value = await api(`/api/transactions?month=${month.value}`)
  accounts.value = await api('/api/accounts')
  categories.value = await api('/api/categories')
  if (!importAccountId.value && accounts.value.length) importAccountId.value = accounts.value[0].id
}
onMounted(load)
watch(month, load)
async function save() {
  error.value = ''
  try {
    if (editingId.value) await api(`/api/transactions/${editingId.value}`, { method: 'PUT', body: form.value })
    else await api('/api/transactions', { method: 'POST', body: form.value })
    editingId.value = null
    form.value = { ...form.value, amount: 0, memo: '', categoryId: null }
    await load()
  } catch (e: any) { error.value = e?.data?.message || '保存に失敗しました' }
}
function edit(t: any) { editingId.value = t.id; form.value = { accountId: t.accountId, toAccountId: t.toAccountId, categoryId: t.categoryId, type: t.type, amount: t.amount, transactedOn: t.transactedOn, memo: t.memo || '' } }
async function remove(t: any) {
  if (!confirm('この取引を削除しますか？')) return
  await api(`/api/transactions/${t.id}`, { method: 'DELETE' }); await load()
}
async function onImport(ev: Event) {
  const file = (ev.target as HTMLInputElement).files?.[0]
  if (!file || !importAccountId.value) return
  const fd = new FormData(); fd.append('file', file)
  importMsg.value = '取り込み中…'
  try {
    const res: any = await $fetch(`${config.public.apiBase}/api/transactions/import?accountId=${importAccountId.value}`, { method: 'POST', body: fd, headers: { Authorization: `Bearer ${token.value}` } })
    importMsg.value = `取り込み完了: ${res.imported}件、スキップ ${res.skipped}件` + (res.errors?.length ? `（${res.errors.slice(0, 3).join(' / ')}）` : '')
    await load()
  } catch (e: any) { importMsg.value = '取り込みに失敗しました: ' + (e?.data?.message || e?.message || '') }
}
const exportUrl = computed(() => `${config.public.apiBase}/api/transactions/export.csv?month=${month.value}`)
async function downloadExport() {
  const blob: Blob = await $fetch(exportUrl.value, { responseType: 'blob', headers: { Authorization: `Bearer ${token.value}` } })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a'); a.href = url; a.download = `kakeibo-${month.value}.csv`; a.click()
  URL.revokeObjectURL(url)
}
</script>
<template>
  <div class="vstack gap-4">
    <div class="d-flex align-items-center gap-3 flex-wrap">
      <h2 class="fs-5 fw-bold mb-0">取引</h2>
      <input v-model="month" type="month" class="form-control w-auto">
      <button class="btn btn-link btn-sm text-decoration-none p-0" @click="downloadExport">CSVエクスポート</button>
    </div>
    <p v-if="error" class="text-danger small mb-0">{{ error }}</p>
    <form class="card shadow-sm" @submit.prevent="save">
      <div class="card-body d-flex flex-wrap gap-3 align-items-end">
        <label class="small">日付<input v-model="form.transactedOn" type="date" required class="form-control mt-1"></label>
        <label class="small">種別
          <select v-model="form.type" class="form-select mt-1">
            <option value="expense">支出</option><option value="income">収入</option><option value="transfer">振替</option>
          </select>
        </label>
        <label class="small">口座
          <select v-model="form.accountId" required class="form-select mt-1">
            <option v-for="a in accounts" :key="a.id" :value="a.id">{{ a.name }}</option>
          </select>
        </label>
        <label v-if="form.type==='transfer'" class="small">振替先
          <select v-model="form.toAccountId" required class="form-select mt-1">
            <option v-for="a in accounts" :key="a.id" :value="a.id">{{ a.name }}</option>
          </select>
        </label>
        <label class="small">カテゴリ
          <select v-model="form.categoryId" class="form-select mt-1">
            <option :value="null">未分類</option>
            <option v-for="c in categories" :key="c.id" :value="c.id">{{ catLabel(c) }}</option>
          </select>
        </label>
        <label class="small">金額<input v-model.number="form.amount" type="number" min="1" required class="form-control mt-1 amount-input"></label>
        <label class="small">内容<input v-model="form.memo" class="form-control mt-1"></label>
        <button class="btn btn-primary">{{ editingId ? '更新' : '追加' }}</button>
      </div>
    </form>
    <div class="card shadow-sm"><div class="card-body d-flex flex-wrap gap-3 align-items-end">
      <label class="small">CSVインポート先口座
        <select v-model="importAccountId" class="form-select mt-1">
          <option v-for="a in accounts" :key="a.id" :value="a.id">{{ a.name }}</option>
        </select>
      </label>
      <label class="small">CSVファイル（日付,収支種別,金額,大項目,中項目,内容）
        <input type="file" accept=".csv" class="form-control mt-1" @change="onImport">
      </label>
      <p v-if="importMsg" class="small text-body-secondary w-100 mb-0">{{ importMsg }}</p>
    </div></div>
    <ul class="list-group shadow-sm">
      <li v-for="t in items" :key="t.id" class="list-group-item d-flex align-items-center gap-3 small">
        <span class="text-body-secondary col-date flex-shrink-0">{{ t.transactedOn }}</span>
        <span class="col-type flex-shrink-0" :class="t.type==='income' ? 'text-success' : t.type==='transfer' ? 'text-body-secondary' : 'text-danger'">{{ t.type==='income' ? '収入' : t.type==='transfer' ? '振替' : '支出' }}</span>
        <span class="flex-grow-1">{{ t.memo || '（内容なし）' }} <span class="text-body-tertiary">/ {{ accountName(t.accountId) }}</span></span>
        <span class="fw-medium">{{ yen(t.amount) }}</span>
        <button class="btn btn-link btn-sm text-decoration-none p-0" @click="edit(t)">編集</button>
        <button class="btn btn-link btn-sm text-decoration-none p-0 text-danger" @click="remove(t)">削除</button>
      </li>
      <li v-if="!items.length" class="list-group-item text-body-tertiary small">この月の取引はありません</li>
    </ul>
  </div>
</template>
