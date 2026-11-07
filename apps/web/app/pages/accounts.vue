<script setup lang="ts">
const { api } = useApi()
const accounts = ref<any[]>([])
const institutions = ref<any[]>([])
const form = ref({ name: '', kind: 'bank', institutionId: null as number | null })
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
    if (editingId.value) await api(`/api/accounts/${editingId.value}`, { method: 'PUT', body: form.value })
    else await api('/api/accounts', { method: 'POST', body: form.value })
    form.value = { name: '', kind: 'bank', institutionId: null }; editingId.value = null
    await load()
  } catch (e: any) { error.value = e?.data?.message || '保存に失敗しました' }
}
function edit(a: any) { editingId.value = a.id; form.value = { name: a.name, kind: a.kind, institutionId: a.institutionId } }
async function remove(a: any) {
  if (!confirm(`口座「${a.name}」を削除しますか？`)) return
  try { await api(`/api/accounts/${a.id}`, { method: 'DELETE' }); await load() }
  catch (e: any) { error.value = e?.data?.message || '削除できませんでした（取引がある口座は削除できません）' }
}
</script>
<template>
  <div class="space-y-6">
    <h2 class="text-lg font-bold">口座</h2>
    <p v-if="error" class="text-red-600 text-sm">{{ error }}</p>
    <form class="bg-white rounded shadow p-4 flex flex-wrap gap-3 items-end" @submit.prevent="save">
      <label class="text-sm">名前<input v-model="form.name" required class="block border rounded px-2 py-1 mt-1"></label>
      <label class="text-sm">種別
        <select v-model="form.kind" class="block border rounded px-2 py-1 mt-1">
          <option v-for="(label, k) in kinds" :key="k" :value="k">{{ label }}</option>
        </select>
      </label>
      <label class="text-sm">金融機関
        <select v-model="form.institutionId" class="block border rounded px-2 py-1 mt-1">
          <option :value="null">なし</option>
          <option v-for="i in institutions" :key="i.id" :value="i.id">{{ i.name }}</option>
        </select>
      </label>
      <button class="bg-blue-600 text-white px-4 py-1.5 rounded">{{ editingId ? '更新' : '追加' }}</button>
    </form>
    <ul class="bg-white rounded shadow divide-y">
      <li v-for="a in accounts" :key="a.id" class="px-4 py-3 flex items-center gap-3">
        <span class="font-medium">{{ a.name }}</span>
        <span class="text-xs bg-gray-100 rounded px-2 py-0.5">{{ kinds[a.kind] }}</span>
        <span class="ml-auto">{{ yen(a.balance) }}</span>
        <button class="text-blue-600 text-sm" @click="edit(a)">編集</button>
        <button class="text-red-600 text-sm" @click="remove(a)">削除</button>
      </li>
      <li v-if="!accounts.length" class="px-4 py-3 text-gray-400 text-sm">口座がありません</li>
    </ul>
  </div>
</template>
