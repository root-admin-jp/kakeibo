export function useApi() {
  const config = useRuntimeConfig()
  const { token } = useAuth()
  async function api<T>(path: string, opts: Record<string, unknown> = {}): Promise<T> {
    return await $fetch<T>(config.public.apiBase + path, {
      ...opts,
      headers: { ...(opts.headers as Record<string, string> | undefined), ...(token.value ? { Authorization: `Bearer ${token.value}` } : {}) },
    })
  }
  return { api }
}

export function currentMonth(): string {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}
export function yen(n: number): string { return '¥' + n.toLocaleString('ja-JP') }
