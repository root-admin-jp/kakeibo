<script setup lang="ts">
const { api } = useApi()
const accounts = ref<any[]>([])
const institutions = ref<any[]>([])
const emptyForm = () => ({ name: '', kind: 'bank', institutionId: null as number | null, currentBalance: null as number | null, recordAdjustment: true })
const form = ref(emptyForm())
const editingId = ref<number | null>(null)
const error = ref('')
const kinds: Record<string, string> = { cash: '現金', bank: '銀行', credit_card: 'クレジットカード', e_money: '電子マネー', securities: '証券', other: 'その他' }

async function load() {
  accounts.value = await api('/api/accounts')
  institutions.value = await api('/api/institutions')
}
onMounted(load)
async function save() {
  error.value = ''
  try {
    // 残高欄が空（number入力の空文字含む）なら「残高は変更しない」として送る
    const balance = (form.value.currentBalance as any) === '' || form.value.currentBalance == null ? null : Number(form.value.currentBalance)
    const body = { name: form.value.name, kind: form.value.kind, institutionId: form.value.institutionId, currentBalance: balance, recordAdjustment: form.value.recordAdjustment }
    if (editingId.value) await api(`/api/accounts/${editingId.value}`, { method: 'PUT', body })
    else await api('/api/accounts', { method: 'POST', body })
    form.value = emptyForm(); editingId.value = null
    await load()
  } catch (e: any) { error.value = e?.data?.message || '保存に失敗しました' }
}
function edit(a: any) { editingId.value = a.id; form.value = { name: a.name, kind: a.kind, institutionId: a.institutionId, currentBalance: a.balance, recordAdjustment: true } }
async function remove(a: any) {
  if (!confirm(`口座「${a.name}」を削除しますか？`)) return
  try { await api(`/api/accounts/${a.id}`, { method: 'DELETE' }); await load() }
  catch (e: any) { error.value = e?.data?.message || '削除できませんでした（取引がある口座は削除できません）' }
}
</script>
<template>
  <div class="vstack gap-4">
    <h2 class="fs-5 fw-bold mb-0">口座</h2>
    <p v-if="error" class="text-danger small mb-0">{{ error }}</p>
    <form class="card shadow-sm" @submit.prevent="save">
      <div class="card-body d-flex flex-wrap gap-3 align-items-end">
        <label class="small">名前<input v-model="form.name" required class="form-control mt-1"></label>
        <label class="small">種別
          <select v-model="form.kind" class="form-select mt-1">
            <option v-for="(label, k) in kinds" :key="k" :value="k">{{ label }}</option>
          </select>
        </label>
        <label class="small">金融機関
          <select v-model="form.institutionId" class="form-select mt-1">
            <option :value="null">なし</option>
            <option v-for="i in institutions" :key="i.id" :value="i.id">{{ i.name }}</option>
          </select>
        </label>
        <label class="small">現在の残高（任意）
          <input v-model.number="form.currentBalance" type="number" class="form-control mt-1" placeholder="変更しない場合は空欄">
        </label>
        <label class="small d-flex align-items-center gap-2 pb-1">
          <input v-model="form.recordAdjustment" type="checkbox" class="form-check-input m-0">
          差額を「残高調整」として取引に記録する
        </label>
        <button class="btn btn-primary">{{ editingId ? '更新' : '追加' }}</button>
      </div>
    </form>
    <ul class="list-group shadow-sm">
      <li v-for="a in accounts" :key="a.id" class="list-group-item d-flex align-items-center gap-3">
        <span class="fw-medium">{{ a.name }}</span>
        <span class="badge text-bg-light border">{{ kinds[a.kind] }}</span>
        <span class="ms-auto">{{ yen(a.balance) }}</span>
        <button class="btn btn-link btn-sm text-decoration-none p-0" @click="edit(a)">編集</button>
        <button class="btn btn-link btn-sm text-decoration-none p-0 text-danger" @click="remove(a)">削除</button>
      </li>
      <li v-if="!accounts.length" class="list-group-item text-body-tertiary small">口座がありません</li>
    </ul>
  </div>
</template>
