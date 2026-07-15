import type { Session, Tenant, User } from './model.js'

export interface TenantSummaryResponse {
  id: string
  name: string
  slug: string
}

export interface UserSummaryResponse {
  id: string
  displayName: string
  primaryEmail: string
}

export interface LocaleContextResponse {
  userPreference?: string | null
  tenantDefault?: string | null
}

export interface LoginResponse {
  user: UserSummaryResponse
  sessionExpiresAt: string
  activeTenant: TenantSummaryResponse | null
  eligibleTenants: TenantSummaryResponse[]
  loginContext?: string | null
  localeContext?: LocaleContextResponse
}

export interface SessionResponse {
  user: UserSummaryResponse
  activeTenant: TenantSummaryResponse | null
  sessionExpiresAt: string
  adminScopes: string[]
  localeContext?: LocaleContextResponse
}

export interface MembershipResponse {
  tenant: TenantSummaryResponse
}

export function toTenant(summary: TenantSummaryResponse): Tenant {
  return { id: summary.id, name: summary.name, slug: summary.slug }
}

export function toUser(summary: UserSummaryResponse): User {
  return {
    id: summary.id,
    displayName: summary.displayName,
    primaryEmail: summary.primaryEmail,
  }
}

export function sessionFromLogin(response: LoginResponse): Session {
  const tenants = [
    ...(response.activeTenant ? [response.activeTenant] : []),
    ...response.eligibleTenants,
  ].filter((tenant, index, all) => all.findIndex(item => item.id === tenant.id) === index)

  return {
    user: toUser(response.user),
    activeTenant: response.activeTenant ? toTenant(response.activeTenant) : null,
    tenants: tenants.map(toTenant),
    sessionExpiresAt: response.sessionExpiresAt,
    adminScopes: [],
    localeContext: toLocaleContext(response.localeContext),
  }
}

export function sessionFromCurrent(
  response: SessionResponse,
  memberships: MembershipResponse[],
): Session {
  return {
    user: toUser(response.user),
    activeTenant: response.activeTenant ? toTenant(response.activeTenant) : null,
    tenants: memberships.map(item => toTenant(item.tenant)),
    sessionExpiresAt: response.sessionExpiresAt,
    adminScopes: response.adminScopes,
    localeContext: toLocaleContext(response.localeContext),
  }
}

function toLocaleContext(context: LocaleContextResponse | undefined) {
  return {
    userPreference: context?.userPreference ?? null,
    tenantDefault: context?.tenantDefault ?? null,
  }
}
