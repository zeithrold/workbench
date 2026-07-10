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

export interface DemoCredentials {
  email: string
}
