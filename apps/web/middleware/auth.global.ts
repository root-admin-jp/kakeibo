export default defineNuxtRouteMiddleware((to) => {
  if (import.meta.server) return
  if (to.path === '/login') return
  const { token, restore } = useAuth()
  restore()
  if (!token.value) return navigateTo('/login')
})
