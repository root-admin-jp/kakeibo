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
  <div class="space-y-6">
    <div class="flex items-center gap-3">
      <h2 class="text-lg font-bold">ダッシュボード</h2>
      <input v-model="month" type="month" class="border rounded px-2 py-1">
    </div>
    <p v-if="error" class="text-red-600 text-sm">{{ error }}</p>
    <div v-if="summary" class="grid grid-cols-3 gap-4">
      <div class="bg-white rounded shadow p-4"><div class="text-sm text-gray-500">収入</div><div class="text-xl font-bold text-green-600">{{ yen(summary.totalIncome) }}</div></div>
      <div class="bg-white rounded shadow p-4"><div class="text-sm text-gray-500">支出</div><div class="text-xl font-bold text-red-600">{{ yen(summary.totalExpense) }}</div></div>
      <div class="bg-white rounded shadow p-4"><div class="text-sm text-gray-500">収支</div><div class="text-xl font-bold">{{ yen(summary.balance) }}</div></div>
    </div>
    <div v-if="summary" class="grid md:grid-cols-2 gap-4">
      <div class="bg-white rounded shadow p-4">
        <h3 class="font-bold mb-2">カテゴリ別支出</h3>
        <ul class="space-y-1 text-sm">
          <li v-for="c in summary.expenseByCategory" :key="c.categoryName" class="flex justify-between"><span>{{ c.categoryName }}</span><span>{{ yen(c.total) }}</span></li>
          <li v-if="!summary.expenseByCategory.length" class="text-gray-400">データがありません</li>
        </ul>
      </div>
      <div class="bg-white rounded shadow p-4">
        <h3 class="font-bold mb-2">口座残高</h3>
        <ul class="space-y-1 text-sm">
          <li v-for="a in accounts" :key="a.id" class="flex justify-between"><span>{{ a.name }}</span><span>{{ yen(a.balance) }}</span></li>
          <li v-if="!accounts.length" class="text-gray-400">口座がありません。<NuxtLink to="/accounts" class="text-blue-600 underline">口座を作る</NuxtLink></li>
        </ul>
      </div>
    </div>
  </div>
</template>
