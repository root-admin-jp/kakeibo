<script setup lang="ts">
const { api } = useApi()
const categories = ref<any[]>([])
const form = ref({ name: '', parentId: null as number | null })
const error = ref('')
const topCategories = computed(() => categories.value.filter(c => !c.parentId))
const childrenOf = (id: number) => categories.value.filter(c => c.parentId === id)
async function load() { categories.value = await api('/api/categories') }
onMounted(load)
async function save() {
  error.value = ''
  try { await api('/api/categories', { method: 'POST', body: form.value }); form.value = { name: '', parentId: null }; await load() }
  catch (e: any) { error.value = e?.data?.message || '保存に失敗しました' }
}
async function remove(c: any) {
  if (!confirm(`カテゴリ「${c.name}」を削除しますか？`)) return
  try { await api(`/api/categories/${c.id}`, { method: 'DELETE' }); await load() }
  catch (e: any) { error.value = '削除に失敗しました' }
}
</script>
<template>
  <div class="space-y-6">
    <h2 class="text-lg font-bold">カテゴリ</h2>
    <p v-if="error" class="text-red-600 text-sm">{{ error }}</p>
    <form class="bg-white rounded shadow p-4 flex gap-3 items-end" @submit.prevent="save">
      <label class="text-sm">名前<input v-model="form.name" required class="block border rounded px-2 py-1 mt-1"></label>
      <label class="text-sm">大項目
        <select v-model="form.parentId" class="block border rounded px-2 py-1 mt-1">
          <option :value="null">（大項目として作る）</option>
          <option v-for="c in topCategories" :key="c.id" :value="c.id">{{ c.name }}</option>
        </select>
      </label>
      <button class="bg-blue-600 text-white px-4 py-1.5 rounded">追加</button>
    </form>
    <div class="bg-white rounded shadow p-4 space-y-3">
      <div v-for="top in topCategories" :key="top.id">
        <div class="flex gap-2 items-center"><span class="font-medium">{{ top.name }}</span><button class="text-red-600 text-xs" @click="remove(top)">削除</button></div>
        <div class="ml-6 text-sm text-gray-600 flex flex-wrap gap-x-4 gap-y-1 mt-1">
          <span v-for="ch in childrenOf(top.id)" :key="ch.id">{{ ch.name }} <button class="text-red-600 text-xs" @click="remove(ch)">×</button></span>
        </div>
      </div>
    </div>
  </div>
</template>
