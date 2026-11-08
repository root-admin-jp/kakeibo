<script setup lang="ts">
const { api } = useApi()
const month = ref(currentMonth())
const summary = ref<any>(null)
const accounts = ref<any[]>([])
const error = ref('')

async function load() {
  error.value = ''
  try {
    summary.value = await api(`/api/summary/monthly?month=${month.value}`)
    accounts.value = await api('/api/accounts')
  } catch (e: any) { error.value = e?.message || '読み込みに失敗しました' }
}
onMounted(load)
watch(month, load)
</script>
<template>
  <div class="vstack gap-4">
    <div class="d-flex align-items-center gap-3">
      <h2 class="fs-5 fw-bold mb-0">ダッシュボード</h2>
      <input v-model="month" type="month" class="form-control w-auto">
    </div>
    <p v-if="error" class="text-danger small mb-0">{{ error }}</p>
    <div v-if="summary" class="row g-3">
      <div class="col-md-4"><div class="card shadow-sm h-100"><div class="card-body"><div class="small text-body-secondary">収入</div><div class="fs-4 fw-bold text-success">{{ yen(summary.totalIncome) }}</div></div></div></div>
      <div class="col-md-4"><div class="card shadow-sm h-100"><div class="card-body"><div class="small text-body-secondary">支出</div><div class="fs-4 fw-bold text-danger">{{ yen(summary.totalExpense) }}</div></div></div></div>
      <div class="col-md-4"><div class="card shadow-sm h-100"><div class="card-body"><div class="small text-body-secondary">収支</div><div class="fs-4 fw-bold">{{ yen(summary.balance) }}</div></div></div></div>
    </div>
    <div v-if="summary" class="row g-3">
      <div class="col-md-6">
        <div class="card shadow-sm h-100"><div class="card-body">
          <h3 class="fs-6 fw-bold mb-2">カテゴリ別支出</h3>
          <ul class="list-unstyled small mb-0 vstack gap-1">
            <li v-for="c in summary.expenseByCategory" :key="c.categoryName" class="d-flex justify-content-between"><span>{{ c.categoryName }}</span><span>{{ yen(c.total) }}</span></li>
            <li v-if="!summary.expenseByCategory.length" class="text-body-tertiary">データがありません</li>
          </ul>
        </div></div>
      </div>
      <div class="col-md-6">
        <div class="card shadow-sm h-100"><div class="card-body">
          <h3 class="fs-6 fw-bold mb-2">口座残高</h3>
          <ul class="list-unstyled small mb-0 vstack gap-1">
            <li v-for="a in accounts" :key="a.id" class="d-flex justify-content-between"><span>{{ a.name }}</span><span>{{ yen(a.balance) }}</span></li>
            <li v-if="!accounts.length" class="text-body-tertiary">口座がありません。<NuxtLink to="/accounts">口座を作る</NuxtLink></li>
          </ul>
        </div></div>
      </div>
    </div>
  </div>
</template>
