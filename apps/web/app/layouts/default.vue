<script setup lang="ts">
const { user, restore, logout } = useAuth()
onMounted(restore)
</script>
<template>
  <div class="min-vh-100">
    <header class="bg-white border-bottom">
      <div class="container app-container d-flex align-items-center gap-4 py-3">
        <NuxtLink to="/" class="navbar-brand fw-bold fs-5 text-decoration-none">Kakeibo</NuxtLink>
        <nav class="nav small">
          <NuxtLink to="/" class="nav-link px-2">ダッシュボード</NuxtLink>
          <NuxtLink to="/transactions" class="nav-link px-2">取引</NuxtLink>
          <NuxtLink to="/accounts" class="nav-link px-2">口座</NuxtLink>
          <NuxtLink to="/categories" class="nav-link px-2">カテゴリ</NuxtLink>
        </nav>
        <div class="ms-auto d-flex align-items-center gap-3 small">
          <span v-if="user" class="text-body-secondary">{{ user.displayName }}</span>
          <button v-if="user" class="btn btn-link btn-sm text-decoration-none p-0" @click="logout">ログアウト</button>
        </div>
      </div>
    </header>
    <main class="container app-container py-4">
      <slot />
    </main>
  </div>
</template>
