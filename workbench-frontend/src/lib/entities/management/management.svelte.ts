import type {
  InstanceCapabilities,
  ManagementScope,
  TenantCapabilities,
} from './model.js'
import { session } from '$lib/entities/session/session.svelte.js'
import { managementGateway } from './management-gateway.js'

export class ManagementStore {
  instance = $state<InstanceCapabilities | null>(null)
  tenant = $state<TenantCapabilities | null>(null)
  loading = $state(false)
  error = $state<Error | null>(null)
  private tenantId: string | null = null

  async load(force = false) {
    const activeTenantId = session.current?.activeTenant?.id ?? null
    if (!force && this.instance && this.tenantId === activeTenantId)
return
    this.loading = true
    this.error = null
    try {
      const [instance, tenant] = await Promise.all([
        session.current?.adminScopes.includes('INSTANCE')
          ? managementGateway.instanceCapabilities().catch(() => null)
          : Promise.resolve(null),
        activeTenantId
          ? managementGateway.tenantCapabilities().catch(() => null)
          : Promise.resolve(null),
      ])
      this.instance = instance
      this.tenant = tenant
      this.tenantId = activeTenantId
    }
 catch (error) {
      this.error = error as Error
    }
 finally {
      this.loading = false
    }
  }

  has(scope: ManagementScope, action: string) {
    return (
      (scope === 'INSTANCE' ? this.instance : this.tenant)?.actions.includes(
        action,
      ) ?? false
    )
  }

  invalidateTenant() {
    this.tenant = null
    this.tenantId = null
  }
}

export const management = new ManagementStore()
