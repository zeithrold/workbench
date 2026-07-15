export interface Tenant {
  id: string
  name: string
  slug: string
}

export interface User {
  id: string
  displayName: string
  primaryEmail: string
}

export interface LocaleContext {
  userPreference: string | null
  tenantDefault: string | null
}

export interface Session {
  user: User
  activeTenant: Tenant | null
  tenants: Tenant[]
  sessionExpiresAt: string
  adminScopes: string[]
  localeContext: LocaleContext
}

export interface LoginCredentials {
  email: string
  password: string
  loginMethodId: string
  tenantId?: string
}
