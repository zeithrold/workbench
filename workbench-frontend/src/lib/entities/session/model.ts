export interface Tenant {
  id: string
  name: string
  slug: string
}

export interface Session {
  user: {
    name: string
    email: string
  }
  activeTenant: Tenant
  tenants: Tenant[]
}

export interface LoginCredentials {
  email: string
  password?: string
  loginMethodId?: string
  tenantId?: string
}

/** @deprecated Use {@link LoginCredentials} */
export type DemoCredentials = LoginCredentials
