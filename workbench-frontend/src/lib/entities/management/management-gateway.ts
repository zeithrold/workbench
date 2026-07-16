import type {
  AccessGrant,
  AdminUser,
  GroupMember,
  InstanceCapabilities,
  InstanceOperations,
  ManagedInvitation,
  OutboxDelivery,
  OutboxMessage,
  PermissionAction,
  PermissionBinding,
  PermissionGroup,
  PermissionPolicy,
  ProjectSummary,
  TenantCapabilities,
  TenantMember,
  TenantPolicySimulation,
  TenantResource,
} from './model.js'
import { apiFetch } from '$lib/api/http.js'
import { problemFromResponse } from '$lib/api/problem.js'

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await apiFetch(path, init)
  if (!response.ok)
    throw await problemFromResponse(response)
  if (response.status === 204)
    return undefined as T
  return (await response.json()) as T
}

function json(method: string, body: unknown): RequestInit {
  return {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  }
}

export const managementGateway = {
  instanceCapabilities: () =>
    request<InstanceCapabilities>('/api/admin/capabilities'),
  tenantCapabilities: () =>
    request<TenantCapabilities>('/api/manage/capabilities'),
  listTenants: () => request<TenantResource[]>('/api/admin/tenants'),
  createTenant: (body: unknown) =>
    request<TenantResource>('/api/admin/tenants', json('POST', body)),
  updateTenant: (id: string, body: unknown) =>
    request<TenantResource>(`/api/admin/tenants/${id}`, json('PATCH', body)),
  destroyTenant: (id: string, deleteReason?: string) =>
    request<void>(`/api/admin/tenants/${id}`, json('DELETE', { deleteReason })),
  currentTenant: () => request<TenantResource>('/api/manage/tenant'),
  updateCurrentTenant: (body: unknown) =>
    request<TenantResource>('/api/manage/tenant', json('PATCH', body)),
  instanceAdmins: () => request<AdminUser[]>('/api/admin/instance-admins'),
  instanceGrants: () => request<AccessGrant[]>('/api/admin/grants'),
  grantInstanceAdmin: (userId: string) =>
    request<AdminUser>('/api/admin/instance-admins', json('POST', { userId })),
  revokeInstanceAdmin: (id: string) =>
    request<void>(`/api/admin/instance-admins/${id}`, { method: 'DELETE' }),
  tenantAdmins: () => request<AdminUser[]>('/api/manage/tenant-admins'),
  grantTenantAdmin: (userId: string) =>
    request<AdminUser>('/api/manage/tenant-admins', json('POST', { userId })),
  revokeTenantAdmin: (id: string) =>
    request<void>(`/api/manage/tenant-admins/${id}`, { method: 'DELETE' }),
  members: () => request<TenantMember[]>('/api/manage/members'),
  suspendMember: (id: string) =>
    request<TenantMember>(`/api/manage/members/${id}/suspend`, {
      method: 'POST',
    }),
  restoreMember: (id: string) =>
    request<TenantMember>(`/api/manage/members/${id}/restore`, {
      method: 'POST',
    }),
  removeMember: (id: string) =>
    request<TenantMember>(`/api/manage/members/${id}`, { method: 'DELETE' }),
  invitations: () => request<ManagedInvitation[]>('/api/manage/invitations'),
  invite: (email: string, displayName?: string) =>
    request('/api/manage/invitations', json('POST', { email, displayName })),
  cancelInvitation: (id: string) =>
    request<void>(`/api/manage/invitations/${id}`, { method: 'DELETE' }),
  operations: () => request<InstanceOperations>('/api/admin/operations'),
  outboxMessages: () =>
    request<OutboxMessage[]>('/api/admin/outbox/messages?limit=100'),
  outboxDeliveries: () =>
    request<OutboxDelivery[]>('/api/admin/outbox/deliveries?limit=100'),
  replayDelivery: (outboxId: string, consumerName: string) =>
    request<OutboxDelivery>(
      `/api/admin/outbox/deliveries/${outboxId}/consumers/${encodeURIComponent(consumerName)}/replay`,
      { method: 'POST' },
    ),
  groups: () => request<PermissionGroup[]>('/api/manage/groups'),
  createGroup: (body: { code: string, name: string, description?: string }) =>
    request<PermissionGroup>('/api/manage/groups', json('POST', body)),
  updateGroup: (id: string, body: { name?: string, description?: string }) =>
    request<PermissionGroup>(`/api/manage/groups/${id}`, json('PATCH', body)),
  deleteGroup: (id: string) =>
    request<void>(`/api/manage/groups/${id}`, { method: 'DELETE' }),
  groupMembers: (id: string) =>
    request<GroupMember[]>(`/api/manage/groups/${id}/members`),
  addGroupMember: (id: string, userId: string) =>
    request<GroupMember>(
      `/api/manage/groups/${id}/members`,
      json('POST', { userId }),
    ),
  removeGroupMember: (id: string, userId: string) =>
    request<void>(`/api/manage/groups/${id}/members/${userId}`, {
      method: 'DELETE',
    }),
  actions: () => request<PermissionAction[]>('/api/manage/actions'),
  projects: () => request<ProjectSummary[]>('/api/projects'),
  policies: () =>
    request<PermissionPolicy[]>('/api/manage/permission-policies'),
  createPolicy: (body: unknown) =>
    request<PermissionPolicy>(
      '/api/manage/permission-policies',
      json('POST', body),
    ),
  updatePolicy: (id: string, body: { name?: string, description?: string }) =>
    request<PermissionPolicy>(
      `/api/manage/permission-policies/${id}`,
      json('PATCH', body),
    ),
  replacePolicy: (id: string, body: unknown) =>
    request<PermissionPolicy>(
      `/api/manage/permission-policies/${id}`,
      json('PUT', body),
    ),
  deletePolicy: (id: string) =>
    request<void>(`/api/manage/permission-policies/${id}`, {
      method: 'DELETE',
    }),
  simulatePolicy: (body: {
    schemaVersion: 1
    rules: Array<{
      id?: string
      action: string
      resourcePattern: string
      effect: 'ALLOW' | 'DENY'
      condition?: Record<string, unknown> | null
    }>
    action: string
  }) =>
    request<TenantPolicySimulation>(
      '/api/manage/permission-policies/simulate',
      json('POST', body),
    ),
  addPolicyRule: (
    id: string,
    body: { action: string, resourcePattern: string, effect: 'allow' | 'deny' },
  ) =>
    request<PermissionPolicy>(
      `/api/manage/permission-policies/${id}/rules`,
      json('POST', body),
    ),
  bindings: () =>
    request<PermissionBinding[]>('/api/manage/permission-bindings'),
  createBinding: (body: {
    principalType: 'USER' | 'GROUP' | 'TENANT_MEMBER'
    userId?: string
    groupId?: string
    policyId: string
    validTo?: string
  }) =>
    request<PermissionBinding>(
      '/api/manage/permission-bindings',
      json('POST', body),
    ),
  deleteBinding: (id: string) =>
    request<void>(`/api/manage/permission-bindings/${id}`, {
      method: 'DELETE',
    }),
}
