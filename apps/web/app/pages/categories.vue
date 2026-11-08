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
  <div class="vstack gap-4">
    <h2 class="fs-5 fw-bold mb-0">カテゴリ</h2>
    <p v-if="error" class="text-danger small mb-0">{{ error }}</p>
    <form class="card shadow-sm" @submit.prevent="save">
      <div class="card-body d-flex gap-3 align-items-end">
        <label class="small">名前<input v-model="form.name" required class="form-control mt-1"></label>
        <label class="small">大項目
          <select v-model="form.parentId" class="form-select mt-1">
            <option :value="null">（大項目として作る）</option>
            <option v-for="c in topCategories" :key="c.id" :value="c.id">{{ c.name }}</option>
          </select>
        </label>
        <button class="btn btn-primary">追加</button>
      </div>
    </form>
    <div class="card shadow-sm"><div class="card-body vstack gap-3">
      <div v-for="top in topCategories" :key="top.id">
        <div class="d-flex gap-2 align-items-center"><span class="fw-medium">{{ top.name }}</span><button class="btn btn-link btn-sm text-decoration-none p-0 text-danger" @click="remove(top)">削除</button></div>
        <div class="ms-4 small text-body-secondary d-flex flex-wrap gap-3 mt-1">
          <span v-for="ch in childrenOf(top.id)" :key="ch.id">{{ ch.name }} <button class="btn btn-link btn-sm text-decoration-none p-0 text-danger" @click="remove(ch)">×</button></span>
        </div>
      </div>
    </div></div>
  </div>
</template>
