import type { ManagementNavigationGateway } from './management-navigation-gateway.js'
import type { ManagementNavigation, ManagementNavigationItemId } from './model.js'
import { ApiManagementNavigationGateway } from './management-navigation-gateway.js'

export class ManagementNavigationStore {
  current = $state<ManagementNavigation | null>(null)
  pending = $state(false)
  error = $state<Error | null>(null)
  private contextKey: string | null | undefined
  private requestGeneration = 0

  constructor(private readonly gateway: ManagementNavigationGateway) {}

  async load(contextKey: string | null, force = false) {
    if (!force && this.current && this.contextKey === contextKey)
      return this.current
    const generation = ++this.requestGeneration
    this.contextKey = contextKey
    this.pending = true
    this.error = null
    try {
      const current = await this.gateway.current()
      if (generation === this.requestGeneration)
        this.current = current
      return current
    }
    catch (error) {
      if (generation === this.requestGeneration)
        this.error = error instanceof Error ? error : new Error(String(error))
      throw error
    }
    finally {
      if (generation === this.requestGeneration)
        this.pending = false
    }
  }

  has(id: ManagementNavigationItemId) {
    return this.current?.items.some(item => item.id === id) ?? false
  }

  accept(current: ManagementNavigation, contextKey: string | null = null) {
    this.current = current
    this.contextKey = contextKey
    this.pending = false
    this.error = null
  }

  reset() {
    this.requestGeneration++
    this.current = null
    this.contextKey = undefined
    this.pending = false
    this.error = null
  }
}

export const managementNavigation = new ManagementNavigationStore(
  new ApiManagementNavigationGateway(),
)
