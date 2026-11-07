<script setup lang="ts">
const { user, restore, logout } = useAuth()
onMounted(restore)
</script>
<template>
  <div class="min-h-screen bg-gray-50 text-gray-900">
    <header class="bg-white border-b">
      <div class="max-w-5xl mx-auto px-4 py-3 flex items-center gap-6">
        <NuxtLink to="/" class="font-bold text-lg">Kakeibo</NuxtLink>
        <nav class="flex gap-4 text-sm">
          <NuxtLink to="/" class="hover:underline">ダッシュボード</NuxtLink>
          <NuxtLink to="/transactions" class="hover:underline">取引</NuxtLink>
          <NuxtLink to="/accounts" class="hover:underline">口座</NuxtLink>
          <NuxtLink to="/categories" class="hover:underline">カテゴリ</NuxtLink>
        </nav>
        <div class="ml-auto flex items-center gap-3 text-sm">
          <span v-if="user" class="text-gray-500">{{ user.displayName }}</span>
          <button v-if="user" class="text-blue-600 hover:underline" @click="logout">ログアウト</button>
        </div>
      </div>
    </header>
    <main class="max-w-5xl mx-auto px-4 py-6">
      <slot />
    </main>
  </div>
</template>
