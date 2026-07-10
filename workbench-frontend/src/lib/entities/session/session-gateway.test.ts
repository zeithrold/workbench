import { describe, expect, it } from 'vitest'
import { DemoSessionGateway } from './session-gateway'

describe('demoSessionGateway', () => {
  it('creates, switches, and clears an in-memory session', async () => {
    const gateway = new DemoSessionGateway()

    expect(await gateway.current()).toBeNull()

    const session = await gateway.signIn({ email: 'alex@example.com' })
    expect(session.user.name).toBe('alex')
    expect(session.activeTenant.id).toBe('northstar')

    const switched = await gateway.switchTenant('workbench')
    expect(switched.activeTenant.name).toBe('Workbench Labs')

    await gateway.signOut()
    expect(await gateway.current()).toBeNull()
  })

  it('rejects a tenant switch without an active session', async () => {
    await expect(new DemoSessionGateway().switchTenant('workbench')).rejects.toThrow('active session')
  })
})
