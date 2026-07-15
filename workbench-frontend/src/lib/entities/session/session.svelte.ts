import type { LoginCredentials, Session } from './model.js'
import type { SessionGateway } from './session-gateway.js'
import { localeState } from '$lib/i18n/locale.svelte.js'
import { createSessionGateway } from './create-session-gateway.js'

export class SessionStore {
  current = $state<Session | null>(null)
  pending = $state(false)
  restored = $state(false)

  constructor(private readonly gateway: SessionGateway) {}

  async restore() {
    this.current = await this.gateway.current()
    localeState.synchronize(this.current?.localeContext)
    this.restored = true
    return this.current
  }

  accept(current: Session) {
    this.current = current
    localeState.synchronize(current.localeContext)
    this.restored = true
  }

  async signIn(credentials: LoginCredentials) {
    this.pending = true
    try {
      this.current = await this.gateway.signIn(credentials)
      localeState.synchronize(this.current.localeContext)
      this.restored = true
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
      localeState.synchronize(null)
      this.restored = true
    }
    finally {
      this.pending = false
    }
  }

  async switchTenant(tenantId: string) {
    this.pending = true
    try {
      this.current = await this.gateway.switchTenant(tenantId)
      localeState.synchronize(this.current.localeContext)
    }
    finally {
      this.pending = false
    }
  }
}

export const session = new SessionStore(createSessionGateway())
