export interface AuthUser { userId: number; householdId: number; email: string; displayName: string }

export function useAuth() {
  const token = useState<string | null>('auth-token', () => null)
  const user = useState<AuthUser | null>('auth-user', () => null)

  function restore() {
    if (import.meta.client && !token.value) {
      const t = localStorage.getItem('kakeibo-token')
      const u = localStorage.getItem('kakeibo-user')
      if (t) token.value = t
      if (u) { try { user.value = JSON.parse(u) } catch { /* ignore */ } }
    }
  }
  function setSession(t: string, u: AuthUser) {
    token.value = t; user.value = u
    if (import.meta.client) {
      localStorage.setItem('kakeibo-token', t)
      localStorage.setItem('kakeibo-user', JSON.stringify(u))
    }
  }
  function logout() {
    token.value = null; user.value = null
    if (import.meta.client) { localStorage.removeItem('kakeibo-token'); localStorage.removeItem('kakeibo-user') }
    navigateTo('/login')
  }
  return { token, user, restore, setSession, logout }
}
