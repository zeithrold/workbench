import type { LoginCredentials, Session, Tenant } from './model.js'

export interface SessionGateway {
  current: () => Promise<Session | null>
  signIn: (credentials: LoginCredentials) => Promise<Session>
  signOut: () => Promise<void>
  switchTenant: (tenantId: string) => Promise<Session>
}

const tenants: Tenant[] = [
  { id: 'northstar', name: 'Northstar Studio', slug: 'northstar' },
  { id: 'workbench', name: 'Workbench Labs', slug: 'workbench' },
]

export class DemoSessionGateway implements SessionGateway {
  #session: Session | null = null

  async current(): Promise<Session | null> {
    return this.#session
  }

  async signIn({ email }: LoginCredentials): Promise<Session> {
    this.#session = {
      user: {
        name: email.split('@')[0] || 'Demo user',
        email,
      },
      activeTenant: tenants[0],
      tenants,
    }
    return this.#session
  }

  async signOut(): Promise<void> {
    this.#session = null
  }

  async switchTenant(tenantId: string): Promise<Session> {
    if (!this.#session)
      throw new Error('An active session is required to switch tenant.')
    const activeTenant = tenants.find(tenant => tenant.id === tenantId)
    if (!activeTenant)
      throw new Error(`Unknown tenant: ${tenantId}`)
    this.#session = { ...this.#session, activeTenant }
    return this.#session
  }
}
