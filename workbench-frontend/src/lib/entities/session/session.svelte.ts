import type { DemoCredentials, Session } from './model.js'
import type { SessionGateway } from './session-gateway.js'
import { DemoSessionGateway } from './session-gateway.js'

export class SessionStore {
  current = $state<Session | null>(null)
  pending = $state(false)

  constructor(private readonly gateway: SessionGateway) {}

  async signIn(credentials: DemoCredentials) {
    this.pending = true
    try {
      this.current = await this.gateway.signIn(credentials)
    }
    finally {
      this.pending = false
    }
  }

  async signOut() {
    this.pending = true
    try {
      await this.gateway.signOut()
      this.current = null
    }
    finally {
      this.pending = false
    }
  }

  async switchTenant(tenantId: string) {
    this.pending = true
    try {
      this.current = await this.gateway.switchTenant(tenantId)
    }
    finally {
      this.pending = false
    }
  }
}

export const session = new SessionStore(new DemoSessionGateway())
