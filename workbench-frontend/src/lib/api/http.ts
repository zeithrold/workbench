import { apiBaseUrl, apiVersion, apiVersionHeader } from './client.js'

export function resolveApiBaseUrl(): string {
  return import.meta.env.PUBLIC_API_BASE_URL || apiBaseUrl
}

export async function apiFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const url = path.startsWith('http') ? path : `${resolveApiBaseUrl()}${path}`
  const headers = new Headers(init.headers)
  headers.set(apiVersionHeader, apiVersion)
  return fetch(url, {
    ...init,
    headers,
    credentials: 'include',
  })
}
