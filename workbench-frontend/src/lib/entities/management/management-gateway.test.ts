import { afterEach, describe, expect, it, vi } from 'vitest'
import { managementGateway } from './management-gateway.js'

afterEach(() => vi.unstubAllGlobals())

describe('managementGateway', () => {
  it('routes every management operation through the shared API boundary', async () => {
    const fetch = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) => new Response('{}', {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    }))
    vi.stubGlobal('fetch', fetch)

    await Promise.all([
      managementGateway.listTenants(),
      managementGateway.createTenant({ name: 'Acme' }),
      managementGateway.updateTenant('ten_1', { name: 'Acme 2' }),
      managementGateway.destroyTenant('ten_1', 'test'),
      managementGateway.currentTenant(),
      managementGateway.updateCurrentTenant({ timezone: 'UTC' }),
      managementGateway.instanceAdmins(),
      managementGateway.instanceGrants(),
      managementGateway.grantInstanceAdmin('usr_1'),
      managementGateway.revokeInstanceAdmin('adm_1'),
      managementGateway.tenantAdmins(),
      managementGateway.grantTenantAdmin('usr_1'),
      managementGateway.revokeTenantAdmin('adm_1'),
      managementGateway.members(),
      managementGateway.suspendMember('tmb_1'),
      managementGateway.restoreMember('tmb_1'),
      managementGateway.removeMember('tmb_1'),
      managementGateway.invitations(),
      managementGateway.invite('ada@example.test', 'Ada'),
      managementGateway.cancelInvitation('inv_1'),
      managementGateway.operations(),
      managementGateway.outboxMessages(),
      managementGateway.outboxDeliveries(),
      managementGateway.replayDelivery('out_1', 'worker/one'),
      managementGateway.groups(),
      managementGateway.createGroup({ code: 'reviewers', name: 'Reviewers' }),
      managementGateway.updateGroup('grp_1', { name: 'Reviewers 2' }),
      managementGateway.deleteGroup('grp_1'),
      managementGateway.policies(),
      managementGateway.createPolicy({ code: 'review', name: 'Review' }),
      managementGateway.updatePolicy('pol_1', { name: 'Review 2' }),
      managementGateway.deletePolicy('pol_1'),
      managementGateway.addPolicyRule('pol_1', {
        action: 'project.read',
        resourcePattern: 'project:*',
        effect: 'allow',
      }),
      managementGateway.bindings(),
      managementGateway.createBinding({
        principalType: 'GROUP',
        groupId: 'grp_1',
        policyId: 'pol_1',
      }),
      managementGateway.deleteBinding('pbd_1'),
    ])

    expect(fetch).toHaveBeenCalledTimes(36)
    expect(fetch.mock.calls.map(([path]) => path)).not.toContainEqual(expect.stringContaining('/capabilities'))
    expect(fetch).toHaveBeenCalledWith(
      '/api/admin/outbox/deliveries/out_1/consumers/worker%2Fone/replay',
      expect.objectContaining({ method: 'POST', credentials: 'include' }),
    )
  })

  it('handles empty success responses and RFC 7807 failures', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response(null, { status: 204 })))
    await expect(managementGateway.deleteBinding('pbd_1')).resolves.toBeUndefined()

    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({
      title: 'Conflict',
      detail: 'The last administrator cannot be removed.',
    }), { status: 409, headers: { 'Content-Type': 'application/problem+json' } })))
    await expect(managementGateway.revokeTenantAdmin('adm_1')).rejects.toThrow(
      'The last administrator cannot be removed.',
    )
  })
})
