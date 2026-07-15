export type ManagementScope = 'INSTANCE' | 'TENANT'

export interface InstanceSummary {
  id: string
  name: string
}
export interface TenantSummary {
  id: string
  name: string
  slug: string
}

export interface InstanceCapabilities {
  scope: 'INSTANCE'
  instance: InstanceSummary
  actions: string[]
}

export interface TenantCapabilities {
  scope: 'TENANT'
  tenant: TenantSummary
  actions: string[]
}

export interface TenantResource extends TenantSummary {
  timezone: string
  locale: string
  status: string
  createdAt?: string | null
  updatedAt?: string | null
}

export interface AdminUser {
  id: string
  userId: string
  scope: string
  tenantId?: string | null
  status: string
}

export interface AccessGrant {
  id: string
  scope: string
  userId: string
  action: string
  resourcePattern: string
  effect: string
}

export interface UserSummary {
  id: string
  displayName: string
  primaryEmail?: string | null
}

export interface TenantMember {
  id: string
  user: UserSummary
  status: string
  administrator: boolean
  joinedAt?: string | null
}

export interface ManagedInvitation {
  id: string
  type: string
  email: string
  displayName?: string | null
  expiresAt: string
  createdAt?: string | null
}

export interface InfrastructureComponent {
  code: string
  name: string
  status: string
  connection: string
}

export interface DeliveryTrendPoint {
  bucketAt: string
  succeeded: number
  failed: number
}

export interface InstanceOperations {
  instance: InstanceSummary
  version?: string | null
  apiVersion: string
  uptimeSeconds: number
  messagingTransport: string
  status: string
  components: InfrastructureComponent[]
  metrics: Record<string, number>
  deliveries: Record<string, number>
  deliveryTrend: DeliveryTrendPoint[]
  checkedAt: string
}

export interface OutboxMessage {
  id: string
  eventId: string
  eventType: string
  topic: string
  partitionKey: string
  tenantId?: string | null
  createdAt: string
  retentionUntil: string
}

export interface OutboxDelivery {
  outboxId: string
  consumerName: string
  status: string
  attempts: number
  lastError?: string | null
  updatedAt: string
}

export interface PermissionGroup {
  id: string
  code: string
  name: string
  description?: string | null
  builtin: boolean
}
export interface PermissionPolicy {
  id: string
  code: string
  name: string
  description?: string | null
  builtin: boolean
  rules: unknown[]
}
export interface PermissionBinding {
  id: string
  principalType: string
  user?: UserSummary | null
  group?: PermissionGroup | null
  policy: { id: string, code: string, name: string }
}
