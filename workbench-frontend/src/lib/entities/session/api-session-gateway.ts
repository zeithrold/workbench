import type { LoginResponse, MembershipResponse, SessionResponse } from './api-model.js'
import type { LoginCredentials, Session } from './model.js'
import type { SessionGateway } from './session-gateway.js'
import { apiFetch } from '$lib/api/http.js'
import { problemFromResponse } from '$lib/api/problem.js'
import { sessionFromCurrent, sessionFromLogin, toTenant, toUser } from './api-model.js'

export class ApiSessionGateway implements SessionGateway {
  async current(): Promise<Session | null> {
    const response = await apiFetch('/api/session')
    if (response.status === 401)
      return null
    if (!response.ok)
      throw await problemFromResponse(response)

    const body = await response.json() as SessionResponse
    const membershipsResponse = await apiFetch('/api/auth/memberships')
    if (!membershipsResponse.ok)
      throw await problemFromResponse(membershipsResponse)
    const memberships = await membershipsResponse.json() as MembershipResponse[]
    return sessionFromCurrent(body, memberships)
  }

  async signIn(credentials: LoginCredentials): Promise<Session> {
    const response = await apiFetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        method: 'PASSWORD',
        loginMethodId: credentials.loginMethodId,
        tenantId: credentials.tenantId,
        subject: credentials.email,
        password: credentials.password,
      }),
    })

    if (!response.ok)
      throw await problemFromResponse(response)

    return sessionFromLogin(await response.json() as LoginResponse)
  }

  async signOut(): Promise<void> {
    const response = await apiFetch('/api/auth/logout', { method: 'POST' })
    if (!response.ok && response.status !== 401)
      throw await problemFromResponse(response)
  }

  async switchTenant(tenantId: string): Promise<Session> {
    const response = await apiFetch('/api/session', {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ tenantId }),
    })

    if (!response.ok)
      throw await problemFromResponse(response)

    const body = await response.json() as SessionResponse
    const current = await this.current()
    if (!current)
      throw new Error('The session ended while switching tenants.')

    return {
      ...current,
      user: toUser(body.user),
      activeTenant: body.activeTenant ? toTenant(body.activeTenant) : null,
      sessionExpiresAt: body.sessionExpiresAt,
      adminScopes: body.adminScopes,
      localeContext: {
        userPreference: body.localeContext?.userPreference ?? null,
        tenantDefault: body.localeContext?.tenantDefault ?? null,
      },
    }
  }
}
