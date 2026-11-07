// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
  ssr: true,
  modules: ['@nuxtjs/tailwindcss'],
  runtimeConfig: {
    public: {
      // APIのURLは環境変数 NUXT_PUBLIC_API_BASE で上書きする。秘密は置かない。
      apiBase: process.env.NUXT_PUBLIC_API_BASE || 'http://localhost:8080',
    },
  },
  compatibilityDate: '2025-07-15',
})
