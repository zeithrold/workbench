import { apiFetch } from '$lib/api/http.js'
import { problemFromResponse } from '$lib/api/problem.js'

export interface LoginMethodSummary {
  id: string
  code: string
  kind: string
  name: string
}

export interface TenantLoginMethod {
  loginMethod: LoginMethodSummary
  supportedTenants: Array<{ id: string, name: string, slug: string }>
}

export interface LoginDiscovery {
  identifierRecognized: boolean
  flow: 'INSTANCE_ONLY' | 'TENANT'
  instancePasswordMethod: LoginMethodSummary | null
  tenantMethods: TenantLoginMethod[]
}

export async function discoverLogin(email: string): Promise<LoginDiscovery> {
  const params = new URLSearchParams({ identifier: email })
  const response = await apiFetch(`/api/auth/login-discovery?${params}`)
  if (!response.ok)
    throw await problemFromResponse(response)
  return await response.json() as LoginDiscovery
}
