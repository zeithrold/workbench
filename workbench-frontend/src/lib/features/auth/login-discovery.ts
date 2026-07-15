import { apiFetch } from '$lib/api/http.js'
import { problemFromResponse } from '$lib/api/problem.js'

interface LoginMethodSummary {
  id: string
  code: string
  kind: string
  name: string
}

export interface LoginDiscovery {
  identifierRecognized: boolean
  flow: 'INSTANCE_ONLY' | 'TENANT'
  instancePasswordMethod: LoginMethodSummary | null
}

export async function discoverLogin(email: string): Promise<LoginDiscovery> {
  const params = new URLSearchParams({ identifier: email })
  const response = await apiFetch(`/api/auth/login-discovery?${params}`)
  if (!response.ok)
    throw await problemFromResponse(response)
  return await response.json() as LoginDiscovery
}
